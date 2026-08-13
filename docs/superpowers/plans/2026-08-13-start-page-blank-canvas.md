# 首页空白画布 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页“添加”改为进入临时空白画布，并让用户从空白画布顶部添加菜单分别载入图片或打开 `.pur` 场景文件。

**Architecture:** 在共享 `PockRefUiState` 增加不持久化的 `blankCanvasOpen`，用它区分首页和无内容但已打开的画布。顶部 picker 由 Compose 负责启动，图片仍走现有图片导入控制器，`.pur` 入口通过新的 ViewModel action 做文件名校验后走现有场景加载控制器；空白画布关闭使用独立清理路径，不调用普通场景关闭流程。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Activity Result APIs, ViewModel + StateFlow, JUnit 4 JVM tests.

---

## 文件边界

### 修改

- `app/src/main/java/com/pockref/app/model/Models.kt`
  - 增加临时 `blankCanvasOpen` 状态字段。
  - 增加只用于 UI 分支的 `isCanvasOpen` 派生属性；保留 `hasSceneContent` 只表示可持久化内容。
- `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
  - 增加 `OpenBlankCanvas`、`CloseBlankCanvas`、`OpenSceneFromPicker` 三个 action。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
  - 接入三个 action 的 reducer 分支。
  - 创建/关闭空白画布时清理 `currentSceneUri`、`isImportedPurFile`、undo/redo 和保存快照状态。
  - 增加 `.pur` picker 入口的显示名校验和错误提示。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`
  - 增加空白画布的打开/关闭状态转换。
  - 成功导入图片或加载场景后退出临时空白画布。
  - `.pur` 加载失败时恢复“空白画布仍可继续操作”的状态。
- `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`
  - 首页添加按钮改为创建空白画布。
  - 空白画布显示完整 `CanvasPanel` 和顶部工具栏。
  - 顶部添加菜单把图片文件 picker 与 `.pur` picker 分开。
  - 更新顶部工具栏、关闭按钮和 `BackHandler` 的显示/关闭条件。
- `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt`
  - 更新 action 数量契约并断言新增 action 存在。

### 新增

- `app/src/main/java/com/pockref/app/ui/BlankCanvasFlow.kt`
  - 保存纯函数式的空白画布状态转换和 `.pur` 文件名校验，避免把可测试逻辑埋在 Compose 或 Android picker 回调中。
- `app/src/test/java/com/pockref/app/ui/BlankCanvasFlowTest.kt`
  - 覆盖首页/空白画布/普通场景三态转换、临时状态清理和 `.pur` 文件名边界。

### 不修改

- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.importMixed()` 和 `shouldOpenSceneFromImport()` 保留，作为已有其它入口的兼容路径；顶部添加菜单不再使用混合 MIME picker。
- `app/src/main/java/com/pockref/app/data/*`、最近文件持久化格式以及 `.pur` 编解码逻辑不在本次范围内。
- 工作区已有未提交文件不加入本功能提交。

## 状态契约

实现后必须满足以下不变量：

```kotlin
// 首页：没有内容，也没有临时画布
!state.blankCanvasOpen && !state.hasSceneContent

// 临时空白画布：没有内容，但必须渲染画布布局
state.blankCanvasOpen && !state.hasSceneContent

// 普通场景：有持久化内容，临时标记必须关闭
!state.blankCanvasOpen && state.hasSceneContent
```

`blankCanvasOpen` 不进入 `PockRefScene`、内容 hash、自动保存、恢复会话或 `recentScenes` 序列化。`hasSceneContent` 不改语义，仍只检查图片和标记内容。

## Task 1: 添加可测试的空白画布状态模型

**Files:**
- Modify: `app/src/main/java/com/pockref/app/model/Models.kt:776-800,1659-1662`
- Create: `app/src/main/java/com/pockref/app/ui/BlankCanvasFlow.kt`
- Create: `app/src/test/java/com/pockref/app/ui/BlankCanvasFlowTest.kt`

- [ ] **Step 1: 写失败测试，锁定三态和 picker 文件名规则**

在 `BlankCanvasFlowTest.kt` 添加以下测试结构：

```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankCanvasFlowTest {
    @Test
    fun openBlankCanvas_preservesRecentScenesAndUserSettings_butClearsStartPageTransientState() {
        val recent = listOf(
            RecentSceneEntry(
                uri = "content://scene/1",
                label = "scene.pur",
                source = "Opened",
            ),
        )
        val before = PockRefUiState(
            recentScenes = recent,
            recentMultiSelectMode = true,
            recentSelection = setOf("content://scene/1"),
            bulkDeleteConfirmOpen = true,
            importSourceMenuOpen = true,
            backgroundColor = Color.Red,
            themeMode = ThemeMode.DARK,
        )

        val after = before.openBlankCanvas()

        assertTrue(after.blankCanvasOpen)
        assertFalse(after.hasSceneContent)
        assertEquals(recent, after.recentScenes)
        assertEquals(Color.Red, after.backgroundColor)
        assertEquals(ThemeMode.DARK, after.themeMode)
        assertFalse(after.recentMultiSelectMode)
        assertTrue(after.recentSelection.isEmpty())
        assertFalse(after.bulkDeleteConfirmOpen)
        assertFalse(after.importSourceMenuOpen)
    }

    @Test
    fun closeBlankCanvas_clearsOnlyTemporaryCanvasState() {
        val before = PockRefUiState(
            blankCanvasOpen = true,
            importSourceMenuOpen = true,
        )

        val after = before.closeBlankCanvas()

        assertFalse(after.blankCanvasOpen)
        assertFalse(after.importSourceMenuOpen)
        assertTrue(after.images.isEmpty())
        assertTrue(after.markupStrokes.isEmpty())
        assertTrue(after.markupTexts.isEmpty())
    }

    @Test
    fun purFileNameValidation_requiresPurExtension() {
        assertTrue(isPurSceneFileName("scene.pur"))
        assertTrue(isPurSceneFileName("SCENE.PUR"))
        assertFalse(isPurSceneFileName("scene"))
        assertFalse(isPurSceneFileName("scene.png"))
        assertFalse(isPurSceneFileName(null))
    }
}
```

Use the existing `RecentSceneEntry` constructor and the project's `ThemeMode` import. Do not assert on a newly invented persisted field.

- [ ] **Step 2: 运行测试，确认 helper 尚不存在**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.BlankCanvasFlowTest"
```

Expected: compilation failure because `blankCanvasOpen`, `openBlankCanvas()`, `closeBlankCanvas()` and `isPurSceneFileName()` are not defined yet.

- [ ] **Step 3: 添加状态字段和纯转换函数**

在 `PockRefUiState` 的 `sceneName` 附近增加：

```kotlin
/** True while the user is viewing a temporary, non-persisted blank canvas. */
val blankCanvasOpen: Boolean = false,
```

在 `Models.kt` 现有 `hasSceneContent` 后增加：

```kotlin
internal val PockRefUiState.isCanvasOpen: Boolean
    get() = blankCanvasOpen || hasSceneContent
```

在新文件 `BlankCanvasFlow.kt` 添加：

```kotlin
package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState

internal fun PockRefUiState.openBlankCanvas(): PockRefUiState = copy(
    blankCanvasOpen = true,
    recentMultiSelectMode = false,
    recentSelection = emptySet(),
    bulkDeleteConfirmOpen = false,
    importSourceMenuOpen = false,
    selectedImageId = null,
    selectedImageIds = emptySet(),
    multiSelectMode = false,
    boxSelectMode = false,
    boxSelectRect = null,
    boxSelectPreviewIds = emptySet(),
    markupModeActive = false,
    draftMarkupStroke = null,
    markupSessionBaseStrokes = null,
    markupSessionBaseTexts = null,
    pendingMarkupTextPosition = null,
    pendingMarkupTextValue = "",
    editingMarkupTextId = null,
    cropImageId = null,
    cropRect = CropRect(),
    cropAspect = CropAspect.FREE,
    cropRotation = 0f,
    canvasScale = 1f,
    canvasOffset = Offset.Zero,
    fittedImageId = null,
)

internal fun PockRefUiState.closeBlankCanvas(): PockRefUiState = copy(
    blankCanvasOpen = false,
    importSourceMenuOpen = false,
    recentMultiSelectMode = false,
    recentSelection = emptySet(),
    bulkDeleteConfirmOpen = false,
    selectedImageId = null,
    selectedImageIds = emptySet(),
    multiSelectMode = false,
    boxSelectMode = false,
    boxSelectRect = null,
    boxSelectPreviewIds = emptySet(),
    markupModeActive = false,
    draftMarkupStroke = null,
    markupSessionBaseStrokes = null,
    markupSessionBaseTexts = null,
    pendingMarkupTextPosition = null,
    pendingMarkupTextValue = "",
    editingMarkupTextId = null,
    cropImageId = null,
    cropRect = CropRect(),
    cropAspect = CropAspect.FREE,
    cropRotation = 0f,
    canvasScale = 1f,
    canvasOffset = Offset.Zero,
    fittedImageId = null,
)

internal fun isPurSceneFileName(displayName: String?): Boolean =
    !displayName.isNullOrBlank() &&
        displayName.substringAfterLast('.', "").equals("pur", ignoreCase = true)
```

Do not reset `backgroundColor`, `backgroundCustomColor`, `themeMode`, gesture settings, `recentScenes`, or scene content in these helpers. The caller only invokes `closeBlankCanvas()` for the empty temporary state; ordinary scenes continue through `onCloseScene()`.

- [ ] **Step 4: 运行测试，确认纯状态转换通过**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.BlankCanvasFlowTest"
```

Expected: PASS.

- [ ] **Step 5: 提交状态模型变更**

```bash
git add app/src/main/java/com/pockref/app/model/Models.kt \
  app/src/main/java/com/pockref/app/ui/BlankCanvasFlow.kt \
  app/src/test/java/com/pockref/app/ui/BlankCanvasFlowTest.kt
git commit -m "feat: model temporary blank canvas state"
```

Only the three listed paths may be staged in this step.

## Task 2: 接入 ViewModel action 和场景生命周期

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt:170-198,326`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt:603-643,2940-2952`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt:539-635,382-474,493-535,1903-1964`
- Modify: `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt:30-176`

- [ ] **Step 1: 增加 action，并先更新 action 数量测试**

在 `PockRefAction` 的场景/导入 action 附近增加：

```kotlin
data object OpenBlankCanvas : PockRefAction
data object CloseBlankCanvas : PockRefAction
data class OpenSceneFromPicker(val uri: Uri) : PockRefAction
```

在 `PureRefActionsTest.expectedVariantCount` 的 changelog 后追加 `178 -> 181` 的说明，并把常量改成 `181`。增加代表性断言：

```kotlin
@Test
fun blankCanvasActions_areActionVariants() {
    val variants = directSubclasses()
    assertTrue(variants.contains(PockRefAction.OpenBlankCanvas::class.java))
    assertTrue(variants.contains(PockRefAction.CloseBlankCanvas::class.java))
    assertTrue(variants.contains(PockRefAction.OpenSceneFromPicker::class.java))
}
```

- [ ] **Step 2: 运行 action 测试，确认 reducer 分支尚未接入**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefActionsTest"
```

Expected: the variant-count/variant-presence assertions pass, while compilation or exhaustive `when` handling identifies the missing reducer branches if the current compiler requires them.

- [ ] **Step 3: 接入 ViewModel reducer 和 picker 场景校验**

在 `PockRefViewModel.onAction()` 的场景 action 分支增加：

```kotlin
PockRefAction.OpenBlankCanvas -> openBlankCanvas()
PockRefAction.CloseBlankCanvas -> closeBlankCanvas()
is PockRefAction.OpenSceneFromPicker -> openSceneFromPicker(action.uri)
```

在 `PureRefViewModel` 增加两个只负责 ViewModel 生命周期的私有方法：

```kotlin
private fun openBlankCanvas() {
    currentSceneUri = null
    isImportedPurFile = false
    lastSaveSnapshotHash = ""
    undoStack.clear()
    redoStack.clear()
    markupHistory.clear()
    sceneGestureActive = false
    gestureUndoRecorded = false
    _uiState.update { it.openBlankCanvas() }
}

private fun closeBlankCanvas() {
    _uiState.update { it.closeBlankCanvas() }
}
```

`closeBlankCanvas()` must not release a codec that belongs to a previously opened scene; `OpenBlankCanvas` is only a valid start-page transition after the normal scene has already been closed.

在 `PockRefViewModel` 增加：

```kotlin
private fun openSceneFromPicker(uri: Uri) {
    val displayName = queryDisplayNameSync(uri)
    if (!isPurSceneFileName(displayName)) {
        reportError("请选择 .pur 场景文件")
        return
    }
    sceneController.loadScene(
        uri = uri,
        preserveBlankCanvasOnFailure = _uiState.value.blankCanvasOpen,
    )
}
```

Keep `LoadScene` unchanged for external intents and recent-file opens. Keep `ImportMixed` and `shouldOpenSceneFromImport()` unchanged for compatibility.

- [ ] **Step 4: 让场景控制器正确处理临时状态和失败恢复**

Change the `loadScene` signature to:

```kotlin
fun loadScene(
    uri: Uri,
    dropRecentOnFailure: Boolean = false,
    preserveBlankCanvasOnFailure: Boolean = false,
)
```

In the failure branch, after the existing error reporting path for a picker-originated load, clear the provisional scene handle without touching recent files:

```kotlin
if (preserveBlankCanvasOnFailure) {
    mainVm.currentSceneUri = null
    mainVm.isImportedPurFile = false
    mainVm.syncDirectSaveState()
}
```

In the successful `loadScene` state copy add `blankCanvasOpen = false`. In the existing successful image-import state copies (`importImages`, `dropImages`, and `importFromClipboard`) add `blankCanvasOpen = false`. In `closeScene()` add `blankCanvasOpen = false` as a defensive invariant, but do not use `closeScene()` for an untouched temporary blank canvas.

The three image-import paths must also clear the provisional ViewModel scene handle before their existing content-update logic when the import starts from a blank canvas:

```kotlin
if (mainVm._uiState.value.blankCanvasOpen) {
    mainVm.currentSceneUri = null
    mainVm.isImportedPurFile = false
    mainVm.lastSaveSnapshotHash = ""
}
```

Keep the clear operation idempotent and do not change the existing recent-scene or save behavior for imports into a normal scene.

Add a dedicated `closeBlankCanvas()` reducer path through `PockRefAction.CloseBlankCanvas`; it must only apply `PockRefUiState.closeBlankCanvas()` and must not call `codec.release()`, clear recovery backups, increment `untitledCounter`, reset the user background, or write recent-file preferences.

- [ ] **Step 5: 运行生命周期和现有导入测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.BlankCanvasFlowTest" \
  --tests "com.pockref.app.ui.PureRefActionsTest" \
  --tests "com.pockref.app.ui.MixedImportRoutingTest"
```

Expected: PASS. Existing `MixedImportRoutingTest` must continue to pass because its compatibility helper is intentionally unchanged.

- [ ] **Step 6: 提交 ViewModel 生命周期变更**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefActions.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt \
  app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt
git commit -m "feat: route blank canvas lifecycle through view model"
```

Do not stage the pre-existing untracked `MixedImportRoutingTest.kt` in this commit.

## Task 3: 把首页添加改为空白画布入口

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:642-678,1028-1044,1220-1249,1760-1880,2295-2425,6143-6200,6880-7310`

- [ ] **Step 1: 将图片 picker 与场景 picker 分离**

Keep the existing `importLauncher` (`OpenMultipleDocuments`) callback routed to `ImportImages`, and change the callback wiring so the image-file path only launches:

```kotlin
importLauncher.launch(arrayOf("image/*"))
```

Do not launch the old mixed MIME array from the top toolbar. Keep Photo Picker, clipboard, and camera callbacks as image-producing paths.

Remove the now-unused `mixedImportLauncher` declaration and its old union-MIME comments from `PureRefApp.kt`; retain `PockRefAction.ImportMixed`, `PureRefViewModel.importMixed()`, and `shouldOpenSceneFromImport()` for compatibility with existing non-toolbar callers.

Change the existing `openSceneLauncher` callback from:

```kotlin
uri?.let { onAction(PockRefAction.LoadScene(it)) }
```

to:

```kotlin
uri?.let { onAction(PockRefAction.OpenSceneFromPicker(it)) }
```

The scene launcher continues to use:

```kotlin
arrayOf(
    com.pockref.app.MainActivity.PUR_SCENE_MIME,
    "application/octet-stream",
)
```

- [ ] **Step 2: 修改顶部工具栏回调和菜单文案**

Extend `TopToolbar` with a separate callback:

```kotlin
onOpenSceneFile: () -> Unit = {},
```

Keep `onPickFiles` for image files, and wire the call site as:

```kotlin
onPickFiles = { importLauncher.launch(arrayOf("image/*")) },
onOpenSceneFile = {
    openSceneLauncher.launch(
        arrayOf(
            com.pockref.app.MainActivity.PUR_SCENE_MIME,
            "application/octet-stream",
        ),
    )
},
```

Split the current menu row that says `打开文件` into two rows:

```kotlin
// image file row
Text("载入图片")
onPickFiles()

// scene file row
Text("打开文件")
onOpenSceneFile()
```

The gallery, clipboard, and camera rows remain image sources. Every row first dispatches `onImportSourceMenuOpenChange(false)` and only then launches its picker.

- [ ] **Step 3: 修改首页按钮，只创建临时空白画布**

Change `EmptyCanvasState`'s callback from `onImportMixed: () -> Unit` to `onOpenBlankCanvas: () -> Unit`, and change the button body to:

```kotlin
ExtendedFloatingActionButton(
    onClick = onOpenBlankCanvas,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
) {
    Icon(Icons.Rounded.Add, contentDescription = "添加", modifier = Modifier.size(24.dp))
}
```

At the `CanvasPanel` call site remove the obsolete `onImport` and `onOpenScene` parameters and pass:

```kotlin
onOpenBlankCanvas = { onAction(PockRefAction.OpenBlankCanvas) }
```

Update `CanvasPanel`'s signature and its `EmptyCanvasState` call accordingly:

```kotlin
private fun CanvasPanel(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onOpenBlankCanvas: () -> Unit,
    onSaveScene: () -> Unit,
    // existing loader/cache parameters remain unchanged
)
```

```kotlin
EmptyCanvasState(
    state = state,
    onAction = onAction,
    onOpenBlankCanvas = onOpenBlankCanvas,
    // existing log-export parameters remain unchanged
)
```

Do not add any new center-of-canvas import or file buttons; the top toolbar is the only picker entry after this action.

- [ ] **Step 4: 运行编译，确认 picker callback 类型和 Compose 参数完整**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS after the obsolete `mixedImportLauncher` declaration and its comments have been removed; leave the `ImportMixed` action and ViewModel compatibility path intact.

- [ ] **Step 5: 提交 picker/UI entry changes**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt
git commit -m "feat: enter blank canvas from start page"
```

## Task 4: 让空白画布显示完整布局并正确关闭

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:854-900,1614-1623,1759-1776,2304-2423,6995-7005`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt:1834-1841,1894-1964`

- [ ] **Step 1: 更新所有画布/首页分支条件**

Define the UI-only condition once in `PockRefApp`:

```kotlin
val canvasIsOpen = state.isCanvasOpen
```

Use it for the top-level canvas/back behavior, but keep `state.hasSceneContent` for content-only chrome:

```kotlin
val sceneIsOpen = state.isCanvasOpen || state.canDirectSave
val recentMultiSelectActive = state.recentMultiSelectMode && !sceneIsOpen
```

Inside `CanvasPanel`, change the start-page branch from:

```kotlin
if (!state.hasSceneContent) {
```

to:

```kotlin
if (!state.hasSceneContent && !state.blankCanvasOpen) {
```

This leaves `PockRefCanvas` visible while suppressing the homepage card, recent grid, recent multi-select sheet, and log-export FAB in the temporary blank canvas.

Keep `BottomRightActionPanel` gated by `state.hasSceneContent`; it is scene-content chrome and must not appear on a blank canvas.

- [ ] **Step 2: 显示顶部工具栏和 minimap 兼容 chrome**

Change the top toolbar gate from the current image-only check:

```kotlin
state.practiceState == null && state.images.isNotEmpty() && !isLockEngaged
```

to:

```kotlin
state.practiceState == null && state.isCanvasOpen && !isLockEngaged
```

Leave the existing lock/practice gates unchanged. The blank canvas then gets the same toolbar surface, recent-file chevron, add button, undo/redo controls, and close button as a normal canvas.

Change the close button to be enabled whenever the canvas is open and dispatch the dedicated action for an untouched blank canvas:

```kotlin
ToolbarIconButton(
    imageVector = Icons.Rounded.Close,
    contentDescription = "关闭",
    enabled = state.isCanvasOpen,
    onClick = {
        onAction(
            if (state.blankCanvasOpen && !state.hasSceneContent) {
                PockRefAction.CloseBlankCanvas
            } else {
                PockRefAction.CloseScene
            },
        )
    },
)
```

Other canvas chrome that already returns early for an empty image list, such as the minimap, may remain unchanged. Do not force empty minimap/FAB content into the blank canvas.

- [ ] **Step 3: 更新系统返回优先级**

Keep the existing transient overlay priority, then route the final close action as follows:

```kotlin
else -> onAction(
    if (state.blankCanvasOpen && !state.hasSceneContent) {
        PockRefAction.CloseBlankCanvas
    } else {
        PockRefAction.CloseScene
    },
)
```

The `BackHandler` must be enabled for `state.isCanvasOpen`, while the unsaved-dialog guard remains unchanged. A blank canvas with no content therefore returns to the homepage without invoking the ordinary unsaved-scene dialog.

Reset the local `menuExpanded` state when `blankCanvasOpen` changes so a recent-file popup from a previous canvas cannot reappear on a newly created blank canvas:

```kotlin
LaunchedEffect(state.blankCanvasOpen) {
    menuExpanded = false
}
```

The reducer already clears `importSourceMenuOpen`; this effect handles the local recent-file menu state.

- [ ] **Step 4: 保证普通关闭流程和空白关闭流程分离**

Keep `PockRefViewModelScene.onCloseScene()` and its existing `closeScene()` behavior for contentful scenes. The new `CloseBlankCanvas` action must never call `onCloseScene()` when:

```kotlin
state.blankCanvasOpen && !state.hasSceneContent
```

The existing `closeScene()` should still set `blankCanvasOpen = false` defensively so successful saves/discards and external scene transitions cannot leave a stale temporary flag.

- [ ] **Step 5: 运行 UI 编译和 focused tests**

Run:

```bash
./gradlew :app:compileDebugKotlin \
  :app:testDebugUnitTest --tests "com.pockref.app.ui.BlankCanvasFlowTest" \
  --tests "com.pockref.app.ui.PureRefActionsTest"
```

Expected: PASS.

- [ ] **Step 6: 提交画布分支和关闭行为**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt \
  app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt
git commit -m "fix: close temporary blank canvas without saving"
```

## Task 5: 回归验证并检查未提交工作区隔离

**Files:**
- No new source files.
- Review only the feature paths listed in Tasks 1-4.

- [ ] **Step 1: 运行完整 JVM 单元测试**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: the task's focused tests and the existing test suite pass. If a pre-existing unrelated test fails, capture the exact test name and failure output; do not revert or stage unrelated files.

- [ ] **Step 2: 构建 debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a generated debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: 检查 feature diff 和工作区隔离**

Run:

```bash
git status --short
git diff HEAD~3..HEAD --stat
git diff --check HEAD~3..HEAD
```

Verify that implementation commits contain only the planned paths, while the pre-existing modifications to image decoding, build metadata, changelogs, and unrelated tests remain unstaged/uncommitted unless they were already committed independently.

- [ ] **Step 4: 手工验证关键交互（设备或 Compose smoke test）**

On the Galaxy Tab target or an equivalent Android emulator, verify in order:

1. 首页点击“添加”进入完整画布，首页卡片和最近文件网格消失。
2. 顶部添加按钮打开菜单；“载入图片”只显示图片 picker，“打开文件”只显示 `.pur`/兼容场景文件 picker。
3. 取消任一 picker 后仍停留在空白画布。
4. 成功载入图片后顶部工具栏仍在，空白临时标记消失，关闭按普通未保存场景处理。
5. 成功打开 `.pur` 后进入正常已打开场景，最近文件记录按原有逻辑更新。
6. 空白画布直接按返回或点顶部关闭回到首页，最近文件列表不增加空条目，也不出现保存对话框。
7. 选择非 `.pur` 文件时显示错误提示，空白画布保持打开。

- [ ] **Step 5: 提交最终实现（仅当所有验证通过）**

```bash
git status --short
git log -5 --oneline
```

Do not create a release version bump or changelog entry for this feature unless the user separately requests a release build.

## Spec coverage self-check

- 首页添加进入真实空白画布：Task 3, Task 4.
- 复用 `PockRefCanvas` 和顶部工具栏：Task 4.
- 图片与 `.pur` 入口分离：Task 3.
- picker 取消保持空白画布：Task 2, Task 3, Task 5.
- 返回/关闭不保留空场景：Task 2, Task 4, Task 5.
- 不写最近文件、不自动保存、不创建空 `.pur`：Task 2, Task 4.
- 导入/打开成功转普通流程：Task 2.
- `.pur` 校验失败保持空白画布：Task 2, Task 5.
- 单元测试、完整测试和 APK 构建：Task 1, Task 2, Task 4, Task 5.
