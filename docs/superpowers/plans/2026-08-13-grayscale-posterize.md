# Grayscale Posterize Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有可保存的灰度功能之后，为当前选中图片增加一个不持久化的 `2/4/8/16` 级色调分离预览，并把级数按钮放到 `SelectionBottomSheet` 顶部左侧。

**Architecture:** 将色调分离作为 `PockRefUiState` 中的临时观察状态，绑定到打开控制区时的选中图片集合；现有灰度继续通过 `SceneImage.colorAdjust` 进入场景、撤销和导出。渲染层新增独立的 posterize 路径：Android 13/API 33 及以上使用 `RuntimeShader` 对 Bitmap、tile 和 GIF 帧逐像素量化，旧系统使用后台生成并缓存的 Bitmap 降级路径；未启用色调分离时完全复用现有颜色矩阵绘制。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, ViewModel + StateFlow, Android Canvas, AGSL `RuntimeShader` on API 33+, JVM unit tests, existing Bitmap/tile/GIF decode pipelines.

---

## 文件结构和职责

### 修改

- `app/src/main/java/com/pockref/app/model/Models.kt`
  - 在 `PockRefUiState` 增加三个临时 posterize 字段。
  - 保持 `toScene()` 只输出持久化场景字段，不序列化 posterize 状态。
- `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
  - 增加 `TogglePosterizeLevel` 和 `ClearPosterizePreview` 两个 action。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
  - 分发临时 posterize action。
  - 提供打开/清理 posterize 上下文的内部 helper，确保不进入场景历史。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt`
  - 保留当前 `convertToGrayscale()` 的场景变更逻辑。
  - 在灰度变更结束后根据结果打开或清理 posterize 控制区。
  - 在 `normalizeSelection()` 中调用 posterize 选中集合校验。
  - 让 `selectImage()` 的单选、取消选择和多选分支都经过同一清理逻辑；不能留下绕过 `normalizeSelection()` 的单选返回路径。
- `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`
  - 将 `SelectionBottomSheet` 顶部左侧的选中数量替换为 posterize 级数按钮。
  - 将临时状态传入 `PockRefCanvas`、普通 Bitmap、tile、GIF 和 hover magnifier 绘制路径。
  - 管理 posterize CPU 缓存的生命周期和失效。
- `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt`
  - 更新 sealed action variant 数量并增加两个新 action 的存在性断言。

### 新增

- `app/src/main/java/com/pockref/app/ui/PosterizeState.kt`
  - 允许的级数集合、状态转换、重复点击关闭和选中集合清理逻辑。
- `app/src/main/java/com/pockref/app/ui/PosterizeMath.kt`
  - 与 Android 无关的亮度计算、色调量化和 ARGB 结果计算。
- `app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt`
  - Android Canvas 渲染器、API 33 AGSL 路径、CPU Bitmap 降级路径及缓存 key。
- `app/src/main/java/com/pockref/app/ui/PosterizeControls.kt`
  - 顶部 sheet 的 `2级 / 4级 / 8级 / 16级` Material 3 控件。
- `app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt`
  - 临时状态转换和选择变化清理测试。
- `app/src/test/java/com/pockref/app/ui/PosterizeMathTest.kt`
  - 亮度量化、级数边界、Alpha 保留和结果等级测试。
- `app/src/test/java/com/pockref/app/ui/PosterizeRendererTest.kt`
  - 缓存 key 隔离和 renderer 支持级数测试。
- `app/src/test/java/com/pockref/app/ui/PosterizePersistenceBoundaryTest.kt`
  - `toScene()`、历史状态和场景 hash 不包含临时 posterize 字段的回归测试。

### 不修改

- `app/src/main/java/com/pockref/app/data/PurSceneCodec.kt`
- `app/src/main/java/com/pockref/app/data/PurFileParser.kt`
- `app/src/main/java/com/pockref/app/data/SceneImageDecoder.kt`
- 现有 `ColorMatrixHelpers.kt` 的灰度、亮度、对比度和饱和度公式。
- 现有原图导出和画布导出的输入契约；导出路径不接收 posterize 参数。

## 任务拆分

### Task 1: 建立 posterize 临时状态模型

**Files:**
- Modify: `app/src/main/java/com/pockref/app/model/Models.kt:776-820,1647-1657`
- Create: `app/src/main/java/com/pockref/app/ui/PosterizeState.kt`
- Test: `app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt`

- [ ] **Step 1: 写临时状态的失败测试**

在 `PosterizeStateTest.kt` 中先写以下测试。测试使用现有 `PockRefUiState` 和最小的 `SceneImage` fixture，不构造 ViewModel：

```kotlin
package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizeStateTest {
    private val imageA = SceneImage(
        id = "a",
        name = "a.png",
        mimeType = "image/png",
        intrinsicWidth = 100,
        intrinsicHeight = 100,
        center = Offset.Zero,
        displaySize = Size2D(100f, 100f),
    )
    private val imageB = imageA.copy(id = "b", name = "b.png")

    @Test
    fun openPosterizeControls_capturesTheCurrentSelection_andStartsInOrdinaryGrayscale() {
        val state = PockRefUiState(
            images = listOf(imageA, imageB),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        )

        val result = state.openPosterizeControls(setOf("a"))

        assertTrue(result.posterizeControlsVisible)
        assertEquals(null, result.posterizeLevels)
        assertEquals(setOf("a"), result.posterizeTargetImageIds)
    }

    @Test
    fun togglePosterizeLevel_repeatedClickReturnsToOrdinaryGrayscale() {
        val opened = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        val enabled = opened.togglePosterizeLevel(2)
        val disabled = enabled.togglePosterizeLevel(2)

        assertEquals(2, enabled.posterizeLevels)
        assertEquals(null, disabled.posterizeLevels)
        assertTrue(disabled.posterizeControlsVisible)
    }

    @Test
    fun togglePosterizeLevel_switchesDirectlyBetweenSupportedLevels() {
        val state = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        val result = state.togglePosterizeLevel(2).togglePosterizeLevel(8)

        assertEquals(8, result.posterizeLevels)
    }

    @Test
    fun togglePosterizeLevel_rejectsUnsupportedLevels_withoutChangingState() {
        val state = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        assertEquals(state, state.togglePosterizeLevel(3))
    }

    @Test
    fun normalizePosterizeSelection_clearsControlsWhenSelectionChanges() {
        val state = PockRefUiState(
            images = listOf(imageA, imageB),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))
            .togglePosterizeLevel(4)

        val result = state.copy(
            selectedImageId = "b",
            selectedImageIds = setOf("b"),
        ).normalizeSelection()

        assertFalse(result.posterizeControlsVisible)
        assertEquals(null, result.posterizeLevels)
        assertTrue(result.posterizeTargetImageIds.isEmpty())
    }
}
```

- [ ] **Step 2: 运行状态测试，确认 helper 尚不存在**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeStateTest"
```

Expected: compilation failure for the missing `posterizeControlsVisible`, `posterizeLevels`, `posterizeTargetImageIds`, `openPosterizeControls()`, `togglePosterizeLevel()` and `normalizeSelection()` integration.

- [ ] **Step 3: 增加 `PockRefUiState` 临时字段**

在 `PockRefUiState` 的选择字段附近增加，不要放进 `SceneImage`：

```kotlin
/** True while the current grayscale selection exposes posterize controls. */
val posterizeControlsVisible: Boolean = false,

/** Null means ordinary grayscale; otherwise one of 2, 4, 8, or 16. */
val posterizeLevels: Int? = null,

/** The selected image ids captured when the controls were opened. */
val posterizeTargetImageIds: Set<String> = emptySet(),
```

Do not add any of these fields to `PockRefUiState.toScene()`. The existing `toScene()` implementation must remain structurally identical after the new fields are added.

- [ ] **Step 4: 实现纯状态转换和不变量校验**

在 `PosterizeState.kt` 中添加：

```kotlin
package com.pockref.app.ui

import com.pockref.app.model.PockRefUiState

internal val SUPPORTED_POSTERIZE_LEVELS: Set<Int> = setOf(2, 4, 8, 16)

internal fun PockRefUiState.openPosterizeControls(targetIds: Set<String>): PockRefUiState {
    val validTargets = targetIds.filterTo(linkedSetOf()) { id ->
        images.any { it.id == id }
    }
    if (validTargets.isEmpty()) return clearPosterizePreview()
    return copy(
        posterizeControlsVisible = true,
        posterizeLevels = null,
        posterizeTargetImageIds = validTargets,
    )
}

internal fun PockRefUiState.togglePosterizeLevel(levels: Int): PockRefUiState {
    if (!posterizeControlsVisible || levels !in SUPPORTED_POSTERIZE_LEVELS) return this
    return copy(
        posterizeLevels = if (posterizeLevels == levels) null else levels,
    )
}

internal fun PockRefUiState.clearPosterizePreview(): PockRefUiState = copy(
    posterizeControlsVisible = false,
    posterizeLevels = null,
    posterizeTargetImageIds = emptySet(),
)

internal fun PockRefUiState.clearPosterizeIfSelectionChanged(
    normalizedSelection: Set<String>,
): PockRefUiState {
    if (!posterizeControlsVisible) return clearPosterizePreview()
    return if (posterizeTargetImageIds == normalizedSelection) {
        copy(posterizeTargetImageIds = normalizedSelection)
    } else {
        clearPosterizePreview()
    }
}
```

Update `normalizeSelection()` in `PureRefViewModelTransform.kt` so it computes the normalized selection first, then applies `clearPosterizeIfSelectionChanged(normalizedIds)` to the returned state. The selection normalization must still preserve the current `selectedImageId` behavior.

Also update `PockRefViewModelTransform.selectImage()` so its `imageId == null` and single-select `return@update state.copy(...)` branches call `.normalizeSelection()` before returning. The multi-select branch already calls it. This is required because a single-select tap is the common path that changes the selected id without going through `mutateScene()`.

- [ ] **Step 5: 运行状态测试并检查 scene boundary**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeStateTest" --tests "com.pockref.app.ui.PureRefTransformTest"
```

Expected: all selected state and existing transform tests pass. In particular, `PockRefUiState.toScene()` must not change when only posterize fields change.

- [ ] **Step 6: 提交状态模型**

```bash
git add app/src/main/java/com/pockref/app/model/Models.kt \
  app/src/main/java/com/pockref/app/ui/PosterizeState.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt \
  app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt
git commit -m "feat: add transient posterize preview state"
```

### Task 2: 接入 Action、ViewModel 和灰度生命周期

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt:895-905,1216-1225`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt:2079-2094`
- Modify: `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt`
- Test: `app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt`

- [ ] **Step 1: 为 Action surface 写失败断言**

在 `PureRefActionsTest.kt` 增加：

```kotlin
@Test
fun posterizeActions_areDirectSealedSubclasses() {
    val subclasses = directSubclasses()
    assertTrue(subclasses.contains(PockRefAction.TogglePosterizeLevel::class.java))
    assertTrue(subclasses.contains(PockRefAction.ClearPosterizePreview::class.java))
}
```

把 `expectedVariantCount` 从当前 `178` 暂时改为 `180`，然后运行 action 测试，确认在 action 尚未添加前失败。

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefActionsTest"
```

Expected: compilation failure because the two action types do not exist.

- [ ] **Step 2: 增加两个 Action 类型**

在 `PockRefAction` 的图片变换 action 附近增加：

```kotlin
/** Toggle a transient posterize level for the current grayscale selection. */
data class TogglePosterizeLevel(val levels: Int) : PockRefAction

/** Clear the transient posterize controls and preview without touching images. */
data object ClearPosterizePreview : PockRefAction
```

这两个 action 不能调用 `mutateScene()`；它们只改变 `PockRefUiState` 的临时字段。

- [ ] **Step 3: 接入 ViewModel reducer**

在 `PureRefViewModel.onAction()` 的变换分支加入：

```kotlin
is PockRefAction.TogglePosterizeLevel -> {
    _uiState.update { state -> state.togglePosterizeLevel(action.levels) }
}
PockRefAction.ClearPosterizePreview -> {
    _uiState.update { state -> state.clearPosterizePreview() }
}
```

在 `PureRefViewModel` 增加内部 helper，供灰度转换和场景边界使用：

```kotlin
internal fun openPosterizeControlsForSelection(selectedIds: Set<String>) {
    _uiState.update { state -> state.openPosterizeControls(selectedIds) }
}

internal fun clearPosterizePreview() {
    _uiState.update { state -> state.clearPosterizePreview() }
}
```

这两个 helper 不调用 `notifyContentChanged()`、`syncDirectSaveState()` 或历史记录逻辑。

- [ ] **Step 4: 让灰度动作建立或清理观察上下文**

保留 `convertToGrayscale()` 当前对 `SceneImage.colorAdjust.saturation` 的切换算法。场景变更完成后，根据变更后的选中图片决定控制区：

```kotlin
fun convertToGrayscale() {
    val selectedIds = currentSelectedIds()
    if (selectedIds.isEmpty()) return

    mainVm.mutateScene {
        copy(
            images = images.map { img ->
                if (img.id !in selectedIds) img
                else {
                    val currentSat = img.colorAdjust.saturation
                    val newSat = if (currentSat <= -0.9f) 0f else -1f
                    img.copy(colorAdjust = img.colorAdjust.copy(saturation = newSat))
                }
            },
        )
    }

    val after = mainVm._uiState.value
    val allSelectedGrayscale = selectedIds.all { id ->
        after.images.firstOrNull { it.id == id }
            ?.colorAdjust?.saturation?.let { it <= -0.9f } == true
    }
    if (allSelectedGrayscale && selectedIds == after.selectedImageIds) {
        mainVm.openPosterizeControlsForSelection(selectedIds)
    } else {
        mainVm.clearPosterizePreview()
    }
}
```

This preserves the existing saved gray behavior while making the second click close the controls when the selected images become non-gray. A mixed multi-selection must not expose posterize controls unless all selected images are grayscale after the action.

- [ ] **Step 5: 测试灰度边界和非持久化行为**

Add tests to `PosterizeStateTest.kt` or a focused ViewModel-free helper test that assert:

```kotlin
val before = state.openPosterizeControls(setOf("a")).togglePosterizeLevel(4)
val afterSelectionChange = before.copy(
    selectedImageId = "b",
    selectedImageIds = setOf("b"),
).normalizeSelection()

assertFalse(afterSelectionChange.posterizeControlsVisible)
assertEquals(before.images, afterSelectionChange.images)
assertEquals(before.toScene(), afterSelectionChange.toScene())
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefActionsTest" \
  --tests "com.pockref.app.ui.PosterizeStateTest" \
  --tests "com.pockref.app.ui.PureRefTransformTest"
```

Expected: PASS, with `PureRefActionsTest` reporting exactly `180` direct variants.

- [ ] **Step 6: 提交 Action 和生命周期接入**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefActions.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt \
  app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt \
  app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt
git commit -m "feat: bind posterize preview to grayscale action"
```

### Task 3: 实现纯 posterize 数学和 Android 33 GPU shader

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/PosterizeMath.kt`
- Create: `app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt`
- Create: `app/src/test/java/com/pockref/app/ui/PosterizeMathTest.kt`

- [ ] **Step 1: 写亮度量化失败测试**

在 `PosterizeMathTest.kt` 中添加：

```kotlin
package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizeMathTest {
    private val epsilon = 1e-4f

    @Test
    fun quantizeLuminance_twoLevels_hasOnlyBlackAndWhite() {
        assertEquals(0f, quantizeLuminance(0.24f, 2), epsilon)
        assertEquals(1f, quantizeLuminance(0.75f, 2), epsilon)
    }

    @Test
    fun quantizeLuminance_usesNearestLevel() {
        assertEquals(1f / 3f, quantizeLuminance(0.34f, 4), epsilon)
        assertEquals(2f / 3f, quantizeLuminance(0.66f, 4), epsilon)
    }

    @Test
    fun quantizeLuminance_rejectsUnsupportedLevels() {
        assertEquals(0.5f, quantizeLuminance(0.5f, 3), epsilon)
    }

    @Test
    fun posterizeArgb_preservesAlpha() {
        val result = posterizeArgb(0x80404040.toInt(), 2)
        assertEquals(0x80, (result ushr 24) and 0xFF)
    }

    @Test
    fun posterizeArgb_twoLevels_producesOnlyBlackOrWhiteRgb() {
        val dark = posterizeArgb(0xFF303030.toInt(), 2)
        val light = posterizeArgb(0xFFDDDDDD.toInt(), 2)
        assertEquals(0xFF000000.toInt(), dark)
        assertEquals(0xFFFFFFFF.toInt(), light)
    }
}
```

- [ ] **Step 2: 运行数学测试确认缺少实现**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeMathTest"
```

Expected: compilation failure for the missing math functions.

- [ ] **Step 3: 实现纯量化函数**

在 `PosterizeMath.kt` 中添加：

```kotlin
package com.pockref.app.ui

import kotlin.math.round

internal const val POSTERIZE_RED_WEIGHT = 0.213f
internal const val POSTERIZE_GREEN_WEIGHT = 0.715f
internal const val POSTERIZE_BLUE_WEIGHT = 0.072f

internal fun quantizeLuminance(luminance: Float, levels: Int): Float {
    if (levels !in SUPPORTED_POSTERIZE_LEVELS) return luminance.coerceIn(0f, 1f)
    val scale = (levels - 1).toFloat()
    return (round(luminance.coerceIn(0f, 1f) * scale) / scale)
        .coerceIn(0f, 1f)
}

internal fun posterizeArgb(argb: Int, levels: Int): Int {
    val alpha = (argb ushr 24) and 0xFF
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val luminance =
        red * POSTERIZE_RED_WEIGHT +
            green * POSTERIZE_GREEN_WEIGHT +
            blue * POSTERIZE_BLUE_WEIGHT
    val gray = (quantizeLuminance(luminance, levels) * 255f).toInt()
    return (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
}

internal fun posterizeArgbPixels(pixels: IntArray, levels: Int) {
    if (levels !in SUPPORTED_POSTERIZE_LEVELS) return
    pixels.indices.forEach { index ->
        pixels[index] = posterizeArgb(pixels[index], levels)
    }
}
```

- [ ] **Step 4: 实现 API 33 AGSL shader 源码和 request 类型**

在 `PosterizeRenderer.kt` 中定义稳定的 renderer input：

```kotlin
package com.pockref.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.pockref.app.model.ColorAdjust

internal data class PosterizeRequest(
    val levels: Int,
    val colorAdjust: ColorAdjust,
)

internal object PosterizeRenderer {
    private const val AGSL = """
        uniform shader inputImage;
        uniform float levels;
        uniform float brightness;
        uniform float contrast;
        uniform float saturation;

        vec4 main(vec2 canvasCoordinates) {
            vec4 source = inputImage.eval(canvasCoordinates);
            float saturationScale = clamp(1.0 + saturation, 0.0, 1.0);
            float sourceLuminance = dot(source.rgb, vec3(0.213, 0.715, 0.072));
            vec3 saturated = mix(vec3(sourceLuminance), source.rgb, saturationScale);
            float contrastScale = max(0.0, 1.0 + contrast);
            float contrastTranslate = 0.5 * (1.0 - contrastScale);
            vec3 adjusted = saturated * contrastScale +
                vec3(contrastTranslate + brightness);
            float adjustedLuminance = dot(adjusted, vec3(0.213, 0.715, 0.072));
            float levelScale = max(1.0, levels - 1.0);
            float quantized = floor(clamp(adjustedLuminance, 0.0, 1.0) * levelScale + 0.5) /
                levelScale;
            return vec4(vec3(quantized), source.a);
        }
    """

    internal fun isSupported(levels: Int): Boolean =
        levels in SUPPORTED_POSTERIZE_LEVELS

    internal fun drawBitmap(
        canvas: Canvas,
        source: Bitmap,
        sourceRect: Rect,
        destinationRect: RectF,
        request: PosterizeRequest,
        cpuFallback: Bitmap?,
    ): Boolean {
        if (!isSupported(request.levels)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawWithRuntimeShader(canvas, source, sourceRect, destinationRect, request)
            return true
        }
        if (cpuFallback == null) return false
        canvas.drawBitmap(
            cpuFallback,
            sourceRect,
            destinationRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return true
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawWithRuntimeShader(
        canvas: Canvas,
        source: Bitmap,
        sourceRect: Rect,
        destinationRect: RectF,
        request: PosterizeRequest,
    ) {
        // The implementation must map the destination rectangle back to the
        // selected source rectangle through the BitmapShader local matrix;
        // do not crop or rescale the source Bitmap itself.
        val bitmapShader = android.graphics.BitmapShader(
            source,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        )
        bitmapShader.setLocalMatrix(sourceToCanvasMatrix(sourceRect, destinationRect))
        val shader = android.graphics.RuntimeShader(AGSL).apply {
            setInputShader("inputImage", bitmapShader)
            setFloatUniform("levels", request.levels.toFloat())
            setFloatUniform("brightness", request.colorAdjust.brightness.coerceIn(-1f, 1f))
            setFloatUniform("contrast", request.colorAdjust.contrast.coerceIn(-1f, 1f))
            setFloatUniform("saturation", request.colorAdjust.saturation.coerceIn(-1f, 1f))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(destinationRect, paint)
    }

    private fun sourceToCanvasMatrix(sourceRect: Rect, destinationRect: RectF): Matrix {
        val sx = destinationRect.width() / sourceRect.width().coerceAtLeast(1)
        val sy = destinationRect.height() / sourceRect.height().coerceAtLeast(1)
        return Matrix().apply {
            setScale(1f / sx, 1f / sy)
            postTranslate(
                sourceRect.left - destinationRect.left / sx,
                sourceRect.top - destinationRect.top / sy,
            )
        }
    }
}
```

The shader must apply the existing `ColorAdjust` order (saturation first, then contrast and brightness) before calculating luminance. The GPU path must retain source alpha and must never mutate or recycle the source Bitmap.

- [ ] **Step 5: Implement the API <33 CPU Bitmap fallback**

Add an internal function in `PosterizeRenderer.kt` that creates a mutable `ARGB_8888` output, applies the existing color matrix first, then quantizes pixels:

```kotlin
internal fun createPosterizedBitmap(
    source: Bitmap,
    request: PosterizeRequest,
): Bitmap {
    require(PosterizeRenderer.isSupported(request.levels))
    val output = Bitmap.createBitmap(
        source.width.coerceAtLeast(1),
        source.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val colorCanvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = buildColorMatrix(request.colorAdjust)?.let {
            android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(it))
        }
    }
    colorCanvas.drawBitmap(source, 0f, 0f, paint)
    val pixels = IntArray(output.width * output.height)
    output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
    posterizeArgbPixels(pixels, request.levels)
    output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
    return output
}
```

Do not run this function from the Compose draw lambda. The next task will call it from a background cache worker and draw the original grayscale image until the generated output is ready.

- [ ] **Step 6: 运行 renderer/math 单元测试和编译检查**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeMathTest"
./gradlew :app:compileDebugKotlin
```

Expected: all pure math tests pass and the Android renderer compiles with `minSdk = 24`; API 33 code must remain guarded by `Build.VERSION.SDK_INT` and `@RequiresApi`.

- [ ] **Step 7: 提交 renderer 基础实现**

```bash
git add app/src/main/java/com/pockref/app/ui/PosterizeMath.kt \
  app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt \
  app/src/test/java/com/pockref/app/ui/PosterizeMathTest.kt
git commit -m "feat: add posterize math and renderer"
```

### Task 4: 增加 CPU 缓存和 Canvas 渲染接入

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:900-1030,2213-2295,7560-8060,11409-11720`
- Modify: `app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt`
- Create: `app/src/test/java/com/pockref/app/ui/PosterizeRendererTest.kt`

- [ ] **Step 1: 定义可失效的 CPU cache key 和 cache owner**

先在 `PosterizeRendererTest.kt` 写缓存隔离测试，锁定同一图片的不同级数、颜色调整和 tile 不能复用同一个结果：

```kotlin
package com.pockref.app.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizeRendererTest {
    @Test
    fun cacheKey_changesWhenPosterizeInputsChange() {
        val base = PosterizeCacheKey(
            imageId = "image",
            sourceToken = "source-v1",
            decodeLevel = 2,
            tileToken = null,
            levels = 2,
            brightnessBits = 0f.toBits(),
            contrastBits = 0f.toBits(),
            saturationBits = (-1f).toBits(),
        )

        assertNotEquals(base, base.copy(levels = 4))
        assertNotEquals(base, base.copy(tileToken = "0:1"))
        assertNotEquals(base, base.copy(sourceToken = "source-v2"))
    }

    @Test
    fun rendererAcceptsOnlySupportedLevels() {
        assertTrue(PosterizeRenderer.isSupported(2))
        assertTrue(PosterizeRenderer.isSupported(4))
        assertTrue(PosterizeRenderer.isSupported(8))
        assertTrue(PosterizeRenderer.isSupported(16))
    }
}
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeRendererTest"
```

Expected: compilation failure because `PosterizeCacheKey` and `PosterizeRenderer.isSupported()` are not defined yet.

- [ ] **Step 2: 实现 cache key 和 cache owner**

在 `PosterizeRenderer.kt` 添加：

```kotlin
internal data class PosterizeCacheKey(
    val imageId: String,
    val sourceToken: String,
    val decodeLevel: Int,
    val tileToken: String?,
    val levels: Int,
    val brightnessBits: Int,
    val contrastBits: Int,
    val saturationBits: Int,
)
```

`sourceToken` 必须区分当前图片源：对内存图片使用 `imageBytes.contentHashCode()`，对 disk-backed 图片使用 `imageByteOffset`, `imageByteLength`, `intrinsicWidth` 和 `intrinsicHeight` 的组合；不要只使用图片名称或文件扩展名。

添加 `PosterizeBitmapCache`，要求：

- `request(key, source, request, scope)` 只提交一次相同 key 的后台任务；
- 任务在 `Dispatchers.Default` 调用 `createPosterizedBitmap()`；
- 结果回到主线程写入 Compose 可观察的结果 map；
- `get(key)` 返回已完成的 Bitmap，未完成时返回 `null`；
- `removeImage(imageId)` 回收该图片生成的 Bitmap；
- `clear()` 在 Canvas 退出时回收全部生成 Bitmap；
- 永远不回收 `bitmapCache` 或 `tileCache` 里的源 Bitmap。

- [ ] **Step 3: 在 `PockRefApp` / `CanvasPanel` 创建并传递 cache**

在现有 `canvasBitmapCache` 和 `canvasTileCache` 附近增加：

```kotlin
val posterizeBitmapCache = remember { PosterizeBitmapCache() }

DisposableEffect(posterizeBitmapCache) {
    onDispose { posterizeBitmapCache.clear() }
}
```

把 cache 通过 `CanvasPanel` 和 `PockRefCanvas` 参数传下去。`PosterizeBitmapCache` 不能放进 `PockRefUiState`，因为它是渲染资源，不是业务状态。

- [ ] **Step 4: 让 posterize 状态只作用于目标图片**

在 `PockRefCanvas` 中添加纯派生值：

```kotlin
fun posterizeLevelsFor(imageId: String): Int? =
    state.posterizeLevels.takeIf { levels ->
        levels != null &&
            state.posterizeControlsVisible &&
            imageId in state.posterizeTargetImageIds
    }
```

将 `posterizeLevelsFor(image.id)` 传给 `drawSceneImage()`、hover magnifier 和 tile 绘制。未命中目标时必须传 `null`，以保证其它图片继续使用现有 `buildColorFilter(image.colorAdjust)` 路径。

- [ ] **Step 5: 接入普通 Bitmap 和 crop Bitmap**

扩展 `drawSceneImage()` 参数：

```kotlin
posterizeLevels: Int? = null,
posterizeCache: PosterizeBitmapCache? = null,
posterizeSourceToken: String = image.id,
```

在 `bitmap != null && crop != null` 与 `bitmap != null` 两个分支中使用以下规则：

```kotlin
val request = posterizeLevels?.let { levels ->
    PosterizeRequest(levels = levels, colorAdjust = image.colorAdjust)
}
if (request == null) {
    drawImage(
        image = bitmap,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstSize = destinationSize,
        colorFilter = buildColorFilter(image.colorAdjust),
    )
} else {
    drawIntoCanvas { drawCanvas ->
        val source = bitmap.asAndroidBitmap()
        val sourceRect = android.graphics.Rect(
            srcOffset.x,
            srcOffset.y,
            srcOffset.x + srcSize.width,
            srcOffset.y + srcSize.height,
        )
        val destination = android.graphics.RectF(
            0f,
            0f,
            width,
            height,
        )
        val key = posterizeCache?.keyForImage(
            image = image,
            sourceToken = posterizeSourceToken,
            levels = request.levels,
            tileToken = null,
        )
        val cpuFallback = if (Build.VERSION.SDK_INT < 33 && key != null && posterizeCache != null) {
            posterizeCache.getOrRequest(key, source, request)
        } else {
            null
        }
        val drawn = PosterizeRenderer.drawBitmap(
            canvas = drawCanvas.nativeCanvas,
            source = source,
            sourceRect = sourceRect,
            destinationRect = destination,
            request = request,
            cpuFallback = cpuFallback,
        )
        if (!drawn) {
            drawImage(
                image = bitmap,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstSize = destinationSize,
                colorFilter = buildColorFilter(image.colorAdjust),
            )
        }
    }
}
```

Keep the existing crop source rectangle math and the existing image transform scope (`translate`, `rotate`, `scale`) unchanged. Posterize must be applied inside that scope so rotation, flipping and selection geometry do not change.

Import `androidx.compose.ui.graphics.asAndroidBitmap` for the `ImageBitmap` to `Bitmap` bridge. The current resolved Compose UI Graphics dependency exposes this Android-only extension (`asAndroidBitmap(ImageBitmap): Bitmap`). The CPU fallback must draw with the same `sourceRect` passed to the GPU path; otherwise a cropped image would incorrectly show its full uncropped source.

The project is compiled with `compileSdk = 34`, so `android.graphics.RuntimeShader` is available to the source set while the app still supports `minSdk = 24`. Keep all direct `RuntimeShader` references inside the `@RequiresApi(Build.VERSION_CODES.TIRAMISU)` implementation and select that path only after the runtime SDK check. Do not reference the Android renderer from JVM-only math tests; `:app:compileDebugKotlin` is the compatibility gate for the guarded API surface.

- [ ] **Step 6: 接入 tile 绘制**

Extend `drawImageTiles()` with `posterizeLevels`, `posterizeCache`, and source token. For every tile, preserve the current `srcOffsetX`, `srcOffsetY`, `srcWidth`, `srcHeight`, `dstLeft`, `dstTop`, `dstWidth`, and `dstHeight` calculations. Only replace the final `drawImage()` call:

```kotlin
val request = posterizeLevels?.let { levels ->
    PosterizeRequest(levels = levels, colorAdjust = image.colorAdjust)
}
if (request == null) {
    drawImage(
        image = tile,
        srcOffset = IntOffset(srcOffsetX, srcOffsetY),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(dstWidth, dstHeight),
        colorFilter = buildColorFilter(image.colorAdjust),
    )
} else {
    drawIntoCanvas { drawCanvas ->
        val source = tile.asAndroidBitmap()
        val sourceRect = android.graphics.Rect(
            srcOffsetX,
            srcOffsetY,
            srcOffsetX + srcWidth,
            srcOffsetY + srcHeight,
        )
        val destination = android.graphics.RectF(
            dstLeft.toFloat(),
            dstTop.toFloat(),
            (dstLeft + dstWidth).toFloat(),
            (dstTop + dstHeight).toFloat(),
        )
        val key = posterizeCache.keyForTile(
            image = image,
            tileKey = key,
            levels = request.levels,
        )
        val cpuFallback = if (Build.VERSION.SDK_INT < 33) {
            posterizeCache.getOrRequest(key, source, request)
        } else null
        PosterizeRenderer.drawBitmap(
            canvas = drawCanvas.nativeCanvas,
            source = source,
            sourceRect = sourceRect,
            destinationRect = destination,
            request = request,
            cpuFallback = cpuFallback,
        )
    }
}
```

For the CPU fallback, request each tile independently. Never merge tiles into a full-resolution Bitmap just to apply posterize.

- [ ] **Step 7: 接入 GIF 当前帧**

Extend `drawGifMovieFrame()` with `posterizeLevels` and cache. When posterize is disabled, leave the existing `Movie.draw(nativeCanvas, 0f, 0f, paint)` path untouched. When enabled:

1. Obtain/reuse one bounded ARGB_8888 frame buffer sized to `movie.width()` × `movie.height()` for the current GIF id; if the frame exceeds the existing decode/display budget, use the same sampled frame size used by the current GIF path.
2. Draw the current `Movie` frame into the buffer with the existing `ColorAdjust` color filter.
3. Call `PosterizeRenderer.drawBitmap()` from the buffer into the destination rectangle.
4. For API <33, use the same background cache contract; while the posterized frame is pending, draw the normal grayscale frame.
5. Do not change `GifPlaybackState`, frame timing, or playback controls.

The frame buffer is a reusable render resource and must be cleared/recycled when the GIF id changes or the Canvas leaves composition.

- [ ] **Step 8: 让 hover magnifier 使用相同预览**

Thread `posterizeLevelsFor(hoveredImage.id)` and `PosterizeBitmapCache` into `HoverMagnifierOverlay` / `drawHoverMagnifier()`. If the magnifier has no posterized cache ready on API <33, retain its current grayscale draw rather than blocking hover interaction. On API 33+, use the same GPU renderer and source-to-destination mapping as the main image.

- [ ] **Step 9: 清理缓存并验证未启用路径不变**

Extend the existing `DisposableEffect(state.images)` cleanup:

```kotlin
val currentIds = state.images.map { it.id }.toSet()
bitmapCache.keys.filterNot { it in currentIds }.forEach { bitmapCache.remove(it) }
tileCache.keys.filterNot { it.imageId in currentIds }.forEach { tileCache.remove(it) }
posterizeBitmapCache.removeImagesNotIn(currentIds)
```

Add `LaunchedEffect` keys for `state.posterizeLevels`, `state.posterizeControlsVisible`, `state.posterizeTargetImageIds`, `state.images`, and `state.canvasScale` so missing CPU outputs are requested again when the selected images or source decode level changes. Do not add posterize state to the ordinary image decode pipeline key when posterize is disabled.

Run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeMathTest" \
  --tests "com.pockref.app.ui.PosterizeStateTest" \
  --tests "com.pockref.app.ui.PureRefTransformTest"
```

Expected: compile succeeds; existing image rendering tests pass; no source Bitmap is recycled by posterize cleanup.

- [ ] **Step 10: 提交 Canvas renderer integration**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt \
  app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt
git commit -m "feat: render transient posterize preview on canvas"
```

### Task 5: 增加 SelectionBottomSheet 顶部级数按钮

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/PosterizeControls.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:2895-2985`
- Test: `app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt`

- [ ] **Step 1: 实现独立的 Material 3 控件**

在 `PosterizeControls.kt` 中添加：

```kotlin
package com.pockref.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PosterizeLevelButtons(
    selectedLevels: Int?,
    onLevelClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(2, 4, 8, 16).forEach { levels ->
            FilterChip(
                selected = selectedLevels == levels,
                onClick = { onLevelClick(levels) },
                label = {
                    Text(
                        text = "${levels}级",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .heightIn(min = 40.dp),
            )
        }
    }
}
```

Use an explicit ordered list (`listOf(2, 4, 8, 16)`) for UI order rather than relying on `Set` iteration order; keep `SUPPORTED_POSTERIZE_LEVELS` for validation only.

- [ ] **Step 2: 写顶部布局的状态测试契约**

Add pure assertions to `PosterizeStateTest.kt` for the UI predicates:

```kotlin
@Test
fun controlsAreVisible_onlyForTheCapturedSelection() {
    val state = PockRefUiState(
        selectedImageId = "a",
        selectedImageIds = setOf("a"),
    ).openPosterizeControls(setOf("a"))

    assertTrue(state.posterizeControlsVisible)
    assertEquals(setOf("a"), state.posterizeTargetImageIds)
}
```

- [ ] **Step 3: 替换 `SelectionBottomSheet` 左上角文本**

At the start of `SelectionBottomSheet`, compute:

```kotlin
val posterizeControlsVisible =
    state.posterizeControlsVisible &&
        state.posterizeTargetImageIds == state.selectedImageIds &&
        state.selectedImageIds.isNotEmpty()
```

Replace the current `Text("已选 $selectedCount 张")` with:

```kotlin
if (posterizeControlsVisible) {
    PosterizeLevelButtons(
        selectedLevels = state.posterizeLevels,
        onLevelClick = { levels ->
            onAction(PockRefAction.TogglePosterizeLevel(levels))
        },
        modifier = Modifier.weight(1f),
    )
} else {
    Text(
        text = "已选 $selectedCount 张",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
```

Keep the existing lock badge immediately after this left-side content. Keep the spacer, “选择模式” label, and `SheetModeToggle` on the right. If the posterize buttons need more room, constrain the left group with `Modifier.weight(1f)` and keep the right group at its existing intrinsic width; do not add a second row.

- [ ] **Step 4: 确认两个灰度入口使用同一控制行为**

The locked panel and normal panel both already dispatch `PockRefAction.ConvertToGrayscale`. Do not add a second gray action. The ViewModel lifecycle from Task 2 must be the only place that opens/clears posterize controls, so both panel variants behave identically.

- [ ] **Step 5: 编译并手动检查 top sheet layout**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Then run the app on the target landscape tablet configuration and verify:

- ordinary selected state shows `已选 N 张`;
- after clicking `灰度`, the same top-left slot shows exactly `2级`, `4级`, `8级`, `16级`;
- the right-side selection mode control does not move to a second row;
- clicking `2级` activates only `2级`;
- clicking `2级` again clears all active states;
- clicking `4级` after `2级` switches directly to `4级`;
- clicking `灰度` again hides the four buttons and restores the ordinary top-left label.

- [ ] **Step 6: 提交 sheet UI**

```bash
git add app/src/main/java/com/pockref/app/ui/PosterizeControls.kt \
  app/src/main/java/com/pockref/app/ui/PureRefApp.kt \
  app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt
git commit -m "feat: add posterize level controls to selection sheet"
```

### Task 6: 锁定持久化、撤销、导出和场景切换边界

**Files:**
- Create: `app/src/test/java/com/pockref/app/ui/PosterizePersistenceBoundaryTest.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt` at scene load/import cleanup sites only if tests expose a missed path
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt` at close/reset cleanup sites only if tests expose a missed path

- [ ] **Step 1: 写 `toScene()` 和 history boundary 测试**

在 `PosterizePersistenceBoundaryTest.kt` 添加：

```kotlin
package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.data.AutoSaveHash
import com.pockref.app.data.PurSceneCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class PosterizePersistenceBoundaryTest {
    @Test
    fun posterizeOnlyChangesAreAbsentFromSceneProjection() {
        val image = SceneImage(
            id = "a",
            name = "a.png",
            mimeType = "image/png",
            intrinsicWidth = 100,
            intrinsicHeight = 100,
            center = Offset.Zero,
            displaySize = Size2D(100f, 100f),
        )
        val ordinary = PockRefUiState(
            images = listOf(image),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        )
        val preview = ordinary.openPosterizeControls(setOf("a"))
            .togglePosterizeLevel(8)

        assertEquals(ordinary.toScene(), preview.toScene())
        assertEquals(
            AutoSaveHash.compute(PurSceneCodec(), ordinary.toScene()),
            AutoSaveHash.compute(PurSceneCodec(), preview.toScene()),
        )
    }
}
```

Because `hasUnsavedChanges` is ViewModel-owned, implement the test with a small internal comparison helper rather than constructing the Android ViewModel. The helper must compare `toScene()` or the same scene hash input used by the ViewModel; it must not compare the full `PockRefUiState`.

The test only compares the scene projection and its persisted hash; it must not compare full UI-state equality because selection and transient posterize fields are intentionally outside the persisted scene. Use the same `toScene()` projection for any export-input regression assertion; do not invent a separate `hasUnsavedChangesForPosterizeOnlyComparison()` API.

- [ ] **Step 2: 验证 scene load/import 自动清理**

Exercise every existing scene replacement path that ends in `.normalizeSelection()`:

- opening a `.pur` scene;
- first image import into an empty canvas;
- importing additional images into a current scene;
- closing a scene and returning to the start page;
- deleting the last selected image.

For each path, assert that the resulting state satisfies:

```kotlin
!state.posterizeControlsVisible
state.posterizeLevels == null
state.posterizeTargetImageIds.isEmpty()
```

If a path updates selection without calling `normalizeSelection()`, add an explicit `.clearPosterizePreview()` to that path. Do not add broad cleanup to unrelated state updates.

- [ ] **Step 3: 验证撤销/重做只记录灰度**

Use the existing transform test fixture to verify this sequence:

1. Start with a selected color image.
2. Dispatch/execute the existing grayscale mutation.
3. Open posterize controls and select `2`.
4. Assert the scene image has `saturation <= -0.9f` and the scene projection is unchanged by step 3.
5. Undo once and assert the existing grayscale mutation is undone; posterize fields are cleared by the resulting selection/state restore.
6. Redo once and assert grayscale is restored; posterize remains off until the user clicks `灰度` again.

Do not add posterize fields to `HistoryEntry`, `SceneDiff`, `buildHistoryEntry()`, or `applyHistoryEntry()`.

- [ ] **Step 4: 验证导出不读取 posterize 状态**

Search and inspect all calls to:

```bash
rg -n "exportCanvas|ConfirmExportCanvas|ConfirmExportOriginals|toScene\(" \
  app/src/main/java/com/pockref/app/ui app/src/main/java/com/pockref/app/data
```

The implementation must satisfy:

- `exportCanvas()` receives the scene/image data only, with no posterize level parameter;
- selected-original export continues copying original bytes;
- canvas export uses the saved `image.colorAdjust` but never invokes `PosterizeRenderer`;
- thumbnail/cover generation uses ordinary `colorAdjust` only.

Add a regression assertion in `PosterizePersistenceBoundaryTest` that a selected image with a transient `posterizeLevels = 2` still produces the same export scene input as the same image without the transient fields.

- [ ] **Step 5: 运行边界测试和全量 JVM 测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizePersistenceBoundaryTest" \
  --tests "com.pockref.app.ui.PosterizeStateTest" \
  --tests "com.pockref.app.ui.PureRefTransformTest" \
  --tests "com.pockref.app.data.PurSceneCodecTest"
./gradlew :app:testDebugUnitTest
```

Expected: the existing grayscale codec round-trip tests continue to pass, and no test observes a posterize field in the serialized scene.

- [ ] **Step 6: 提交 persistence boundary fixes**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt \
  app/src/test/java/com/pockref/app/ui/PosterizePersistenceBoundaryTest.kt
git commit -m "test: lock posterize preview persistence boundaries"
```

### Task 7: 端到端验证和最终回归

**Files:**
- Modify only files that fail the focused verification; do not include unrelated worktree files.
- Optional test additions: `app/src/test/java/com/pockref/app/ui/PosterizeRendererTest.kt`

- [ ] **Step 1: 运行所有 focused tests and compile**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.Posterize*"
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.ColorMatrixTest"
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefTransformTest"
```

Expected: all focused tests pass and the existing `ColorMatrixTest` remains unchanged.

- [ ] **Step 2: 在目标平板上验证交互矩阵**

Use the Galaxy Tab landscape target and verify these cases:

1. Single selected color image: click `灰度`, confirm ordinary grayscale and four inactive level buttons.
2. Click `2级`, confirm pure black/white preview; click `2级` again, confirm ordinary grayscale.
3. Click `4级`, `8级`, and `16级`; confirm only the latest button is active and the number of visible gray bands changes.
4. Multi-select three images and click a level; confirm the same level is applied to all selected images.
5. Change selection while posterize is active; confirm controls disappear and the newly selected image does not inherit the preview.
6. Click `灰度` again; confirm controls disappear and the selected images return to their ordinary color state according to the existing grayscale toggle.
7. Rotate, flip, crop, drag, zoom and lock an image while posterize is active; confirm only pixels change, not geometry or selection chrome.
8. Activate HD/tile rendering on a large image; confirm each visible tile uses the same level and no full-resolution temporary bitmap is allocated.
9. Select a GIF; confirm frame playback continues and every frame uses the active level.
10. Save/reopen the scene and export; confirm posterize is off after reopen and does not appear in either export result.

- [ ] **Step 3: 检查性能和资源释放**

During the large-image and GIF cases, verify:

- level changes do not create a new source decode request;
- API 33+ uses the shader path and does not allocate a full-size CPU result for each level;
- API <33 fallback work does not run on the UI thread;
- switching selection or scene releases posterized output Bitmaps;
- closing the Canvas does not recycle source Bitmap/tile entries still owned by the existing decode cache;
- while a CPU fallback result is pending, the UI remains interactive and displays ordinary grayscale.

- [ ] **Step 4: 运行完整构建并检查工作区**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
git status --short
```

Expected: tests and debug APK build pass. `git status` may still show unrelated pre-existing user changes, but the feature commits must contain only the files listed in this plan.

- [ ] **Step 5: 最终提交（仅当 Task 7 产生修复）**

Stage only the concrete paths modified by Task 7, then run:

```bash
git commit -m "fix: polish posterize preview regression checks"
```

Do not use a wildcard or stage unrelated worktree changes; the staged path list must come from the Task 7 failure fixes only.

Do not create a release tag or modify `versionCode`/`versionName` for this feature-only implementation unless a separate release request is made.

## 计划自检

### Spec coverage

- `2/4/8/16` 级按钮和重复点击关闭：Task 1, Task 2, Task 5。
- 灰度后默认普通灰度、按钮未激活：Task 1, Task 2, Task 5。
- sheet 左上角替换“已选 N 张”：Task 5。
- 多选统一使用同一级数：Task 1, Task 5, Task 7。
- 色调分离不进入保存、撤销、自动保存、hash 和导出：Task 2, Task 6。
- 现有灰度继续保存、撤销和导出：Task 2, Task 6。
- 普通 Bitmap、crop、tile 和 GIF：Task 4, Task 7。
- API 33 GPU 与 API <33 CPU 降级：Task 3, Task 4。
- 选择变化、场景切换、关闭画布清理：Task 1, Task 6。
- Alpha、亮度量化和级数边界：Task 3。
- 目标平板横屏布局和手动验证：Task 5, Task 7。

### Placeholder scan

The plan contains no `TODO`, `TBD`, angle-bracket placeholder, or unspecified implementation step. Every code-changing task names exact files, functions, tests, commands, and expected outcomes. The final optional commit deliberately leaves the staged path list to the actual Task 7 failure fixes, but explicitly forbids wildcard or unrelated staging.

### Type and name consistency

- State fields are consistently named `posterizeControlsVisible`, `posterizeLevels`, and `posterizeTargetImageIds`.
- State helpers are consistently named `openPosterizeControls`, `togglePosterizeLevel`, `clearPosterizePreview`, and `clearPosterizeIfSelectionChanged`.
- Action names are consistently `TogglePosterizeLevel` and `ClearPosterizePreview`.
- Renderer types are consistently `PosterizeRequest`, `PosterizeCacheKey`, `PosterizeBitmapCache`, and `PosterizeRenderer`.
- The UI control is consistently `PosterizeLevelButtons`.
