# Selection HD Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the iOS-matching on-canvas HD control that persists an image's original-resolution display preference and uses it to request full-resolution decoding.

**Architecture:** Keep the persistent flag on `SceneImage`, and use a pure UI helper to derive the off/mixed/on state and the 52 x 44dp control position. Route the new action through the existing transform controller and `mutateScene`, so undo, redo, dirty state, and autosave notifications remain centralized. Custom `PRFA` writes always emit version 3; readers continue to accept version 1 and 2 with HD disabled.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, StateFlow/ViewModel, JUnit 4, Gradle.

---

## File Structure

- `app/src/main/java/com/pockref/app/model/Models.kt`: stores `showsOriginalResolution` and marks app-generated scenes as custom format version 3.
- `app/src/main/java/com/pockref/app/data/ImageDecodePolicy.kt`: upgrades HD images to `SceneImageDecoder.ORIGINAL_LEVEL`.
- `app/src/main/java/com/pockref/app/data/PurSceneCodec.kt`: writes the V3 per-image HD byte and reads it from in-memory and disk-backed custom files.
- `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`: defines `ToggleOriginalDisplaySelected`.
- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`: dispatches the action.
- `app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt`: toggles the selected images atomically via `mutateScene`.
- `app/src/main/java/com/pockref/app/ui/SelectionHdControl.kt`: pure selection-state/placement policy plus the Compose overlay control.
- `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`: mounts the control above the canvas and exposes rotation-handle drag state to it.
- `app/src/test/java/com/pockref/app/ui/SelectionHdControlPolicyTest.kt`: unit-tests state, toggle target, visibility, and position.
- `app/src/test/java/com/pockref/app/data/ImageDecodePolicyTest.kt`: proves an HD image requests original resolution.
- `app/src/test/java/com/pockref/app/data/PurSceneCodecCustomFormatTest.kt`: proves V3 round-trip preservation and V2 compatibility.

### Task 1: Add the Pure HD Selection Policy

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/SelectionHdControl.kt`
- Create: `app/src/test/java/com/pockref/app/ui/SelectionHdControlPolicyTest.kt`

- [ ] **Step 1: Write the failing policy tests**

```kotlin
class SelectionHdControlPolicyTest {
    @Test
    fun stateFor_returnsOffMixedAndOn() {
        assertEquals(OriginalResolutionSelectionState.OFF, stateFor(emptyList()))
        assertEquals(OriginalResolutionSelectionState.OFF, stateFor(listOf(false, false)))
        assertEquals(OriginalResolutionSelectionState.MIXED, stateFor(listOf(true, false)))
        assertEquals(OriginalResolutionSelectionState.ON, stateFor(listOf(true, true)))
    }

    @Test
    fun targetEnabled_isFalseOnlyWhenEverySelectedImageIsAlreadyHd() {
        assertTrue(targetOriginalResolutionEnabled(listOf(false, true)))
        assertFalse(targetOriginalResolutionEnabled(listOf(true, true)))
    }

    @Test
    fun policy_hidesDuringRotationAndForAnimatedGif() {
        assertFalse(shouldShowOriginalResolutionControl(rotationActive = true, containsAnimatedGif = false))
        assertFalse(shouldShowOriginalResolutionControl(rotationActive = false, containsAnimatedGif = true))
        assertTrue(shouldShowOriginalResolutionControl(rotationActive = false, containsAnimatedGif = false))
    }

    @Test
    fun position_usesBottomRightWhenFrameHasRoomAndCentersOtherwise() {
        assertEquals(Offset(166f, 70f), originalResolutionControlPosition(Rect(0f, 0f, 200f, 100f)))
        assertEquals(Offset(25f, 22f), originalResolutionControlPosition(Rect(0f, 0f, 50f, 44f)))
    }
}
```

- [ ] **Step 2: Run the policy test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.SelectionHdControlPolicyTest"
```

Expected: compilation failure because `SelectionHdControl.kt`, its state enum, and its policy functions do not exist.

- [ ] **Step 3: Implement the minimal policy and control**

```kotlin
enum class OriginalResolutionSelectionState { OFF, MIXED, ON }

internal fun stateFor(values: List<Boolean>): OriginalResolutionSelectionState = when {
    values.isEmpty() || values.none { it } -> OriginalResolutionSelectionState.OFF
    values.all { it } -> OriginalResolutionSelectionState.ON
    else -> OriginalResolutionSelectionState.MIXED
}

internal fun targetOriginalResolutionEnabled(values: List<Boolean>): Boolean =
    values.isNotEmpty() && !values.all { it }

internal fun shouldShowOriginalResolutionControl(
    rotationActive: Boolean,
    containsAnimatedGif: Boolean,
): Boolean = !rotationActive && !containsAnimatedGif

internal fun originalResolutionControlPosition(frame: Rect): Offset {
    val x = if (frame.width >= 68f) frame.right - 8f - 26f else frame.center.x
    val y = if (frame.height >= 60f) frame.bottom - 8f - 22f else frame.center.y
    return Offset(x, y)
}
```

Add `SelectionHdControl` in the same file. It must:

```kotlin
Box(
    modifier = Modifier
        .size(width = 52.dp, height = 44.dp)
        .offset { IntOffset((center.x - 26.dp.toPx()).roundToInt(), (center.y - 22.dp.toPx()).roundToInt()) }
        .clip(CircleShape)
        .background(containerColor)
        .border(1.dp, Color.White.copy(alpha = 0.48f), CircleShape)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("HD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
        if (selectionState == OriginalResolutionSelectionState.MIXED) {
            Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
        }
    }
}
```

Use neutral translucent surface for `OFF`, and the existing canvas selection blue for `MIXED`/`ON`. Add an accessibility label of `原图显示` and state-specific accessibility value.

- [ ] **Step 4: Run the policy test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.SelectionHdControlPolicyTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the policy**

```bash
git add app/src/main/java/com/pockref/app/ui/SelectionHdControl.kt app/src/test/java/com/pockref/app/ui/SelectionHdControlPolicyTest.kt
git commit -m "feat: add selection HD control policy"
```

### Task 2: Persist and Decode the HD Preference

**Files:**
- Modify: `app/src/main/java/com/pockref/app/model/Models.kt`
- Modify: `app/src/main/java/com/pockref/app/data/ImageDecodePolicy.kt`
- Modify: `app/src/main/java/com/pockref/app/data/PurSceneCodec.kt`
- Modify: `app/src/test/java/com/pockref/app/data/ImageDecodePolicyTest.kt`
- Modify: `app/src/test/java/com/pockref/app/data/PurSceneCodecCustomFormatTest.kt`

- [ ] **Step 1: Write the failing decode and codec tests**

```kotlin
@Test
fun hdImage_requestsOriginalQualityWithoutFocus() {
    val request = ImageDecodePolicy.requestFor(
        image = testImage(
            displayWidth = 900f,
            displayHeight = 600f,
            imageScale = 1f,
            intrinsicWidth = 10881,
            intrinsicHeight = 14073,
        ).copy(showsOriginalResolution = true),
        canvasScale = 0.1f,
        viewportWidth = 2800f,
        viewportHeight = 1752f,
        isFocusedImage = false,
    )

    assertEquals(10881, request.displayWidth)
    assertEquals(14073, request.displayHeight)
    assertEquals(SceneImageDecoder.ORIGINAL_LEVEL, request.maxLevel)
}

@Test
fun customFormat_roundTripsOriginalResolutionFlag() {
    val scene = PockRefScene(
        version = 3,
        images = listOf(testImage().copy(showsOriginalResolution = true)),
    )
    val output = ByteArrayOutputStream()

    PurSceneCodec().write(scene, output)

    assertTrue(PurSceneCodec().read(ByteArrayInputStream(output.toByteArray()))
        .images.single().showsOriginalResolution)
}
```

Add a V2 byte-fixture test whose image includes the existing `locked` and `cropRect` fields but no HD byte, then assert `showsOriginalResolution == false`.

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.ImageDecodePolicyTest" --tests "com.pockref.app.data.PurSceneCodecCustomFormatTest"
```

Expected: the HD decode assertion fails and the codec test does not compile because `showsOriginalResolution` is absent.

- [ ] **Step 3: Implement the V3 field and original-resolution request**

Add the model field and canonical custom version:

```kotlin
data class SceneImage(
    // existing fields
    val cropRect: CropRect? = null,
    val showsOriginalResolution: Boolean = false,
)

fun PockRefUiState.toScene(): PockRefScene = PockRefScene(
    version = 3,
    // existing fields
)
```

Make `PurSceneCodec.write` always write `CUSTOM_SCENE_VERSION` (`3`), then append:

```kotlin
output.writeBooleanByte(image.showsOriginalResolution)
```

after the V2 crop payload. Both `readCustomScene` and `readCustomSceneFromFile` must read it only for `version >= 3`:

```kotlin
val showsOriginalResolution = if (version >= 3) data.readBooleanByte() else false
base.copy(
    locked = locked,
    cropRect = cropRect,
    showsOriginalResolution = showsOriginalResolution,
)
```

Use the equivalent `RandomAccessFile` branch for disk-backed custom scenes. Do not alter desktop-compatible writer/parser layout.

Change the decode condition:

```kotlin
if (isFocusedImage || image.showsOriginalResolution) {
    return ImageDecodeRequest(
        displayWidth = image.intrinsicWidth.coerceAtLeast(1),
        displayHeight = image.intrinsicHeight.coerceAtLeast(1),
        maxLevel = SceneImageDecoder.ORIGINAL_LEVEL,
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.ImageDecodePolicyTest" --tests "com.pockref.app.data.PurSceneCodecCustomFormatTest"
```

Expected: PASS.

- [ ] **Step 5: Commit persistence and decode behavior**

```bash
git add app/src/main/java/com/pockref/app/model/Models.kt app/src/main/java/com/pockref/app/data/ImageDecodePolicy.kt app/src/main/java/com/pockref/app/data/PurSceneCodec.kt app/src/test/java/com/pockref/app/data/ImageDecodePolicyTest.kt app/src/test/java/com/pockref/app/data/PurSceneCodecCustomFormatTest.kt
git commit -m "feat: persist original-resolution image display"
```

### Task 3: Add the Action and Atomic Selection Toggle

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt`
- Modify: `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt`

- [ ] **Step 1: Write the failing action contract test**

Update `expectedVariantCount` by one and add:

```kotlin
@Test
fun toggleOriginalDisplaySelected_isAnActionVariant() {
    assertTrue(
        directSubclasses().contains(PockRefAction.ToggleOriginalDisplaySelected::class.java),
    )
}
```

- [ ] **Step 2: Run the action test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefActionsTest"
```

Expected: failure because the new direct sealed subclass is absent; keep the existing unrelated count failure documented if it remains present before this task.

- [ ] **Step 3: Define and route the action**

Add the action adjacent to `ToggleLockSelected`:

```kotlin
data object ToggleOriginalDisplaySelected : PockRefAction
```

Route it in `PockRefViewModel.onAction`:

```kotlin
PockRefAction.ToggleOriginalDisplaySelected -> transformController.toggleOriginalDisplaySelected()
```

Implement the controller method using the existing undo-aware mutation path:

```kotlin
internal fun toggleOriginalDisplaySelected() {
    val selectedIds = currentSelectedIds()
    if (selectedIds.isEmpty()) return
    val selected = mainVm._uiState.value.images.filter { it.id in selectedIds }
    if (selected.isEmpty()) return
    val shouldEnable = !selected.all { it.showsOriginalResolution }

    mainVm.mutateScene {
        copy(images = images.map { image ->
            if (image.id in selectedIds) image.copy(showsOriginalResolution = shouldEnable) else image
        })
    }
}
```

Do not apply the lock guard: iOS allows HD state to be toggled for every selected image, and the operation changes display preference rather than geometry.

- [ ] **Step 4: Run the action test to verify the new variant is recognized**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PureRefActionsTest"
```

Expected: the new action-variant assertion passes. If the pre-existing expected-count assertion still fails, update its expected count to the actual intentional count and record the change in its changelog comment.

- [ ] **Step 5: Commit the action plumbing**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefActions.kt app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt
git commit -m "feat: toggle selected images in original resolution"
```

### Task 4: Mount the iOS-Matching Canvas Overlay

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/SelectionHdControl.kt`

- [ ] **Step 1: Write the failing compile-time integration contract**

Add this internal function to the policy test:

```kotlin
@Test
fun selectionFrameOnCanvas_projectsWorldAabbToCanvasPixels() {
    val frame = selectionFrameOnCanvas(
        selectionAabb = Rect(-40f, 0f, 60f, 40f),
        canvasScale = 2f,
        canvasOffset = Offset(30f, 40f),
        canvasSize = Size(400f, 300f),
    )

    assertEquals(Rect(150f, 190f, 350f, 270f), frame)
}
```

- [ ] **Step 2: Run the integration policy test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.SelectionHdControlPolicyTest"
```

Expected: compilation failure because `selectionFrameOnCanvas` is absent.

- [ ] **Step 3: Implement projection and mount the overlay**

Add an internal projection helper alongside the policy:

```kotlin
internal fun selectionFrameOnCanvas(
    selectionAabb: Rect,
    canvasScale: Float,
    canvasOffset: Offset,
    canvasSize: Size,
): Rect {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f) + canvasOffset
    return Rect(
        left = center.x + selectionAabb.left * canvasScale,
        top = center.y + selectionAabb.top * canvasScale,
        right = center.x + selectionAabb.right * canvasScale,
        bottom = center.y + selectionAabb.bottom * canvasScale,
    )
}
```

Make `PockRefCanvas` return a `Box` containing the existing input-enabled `Canvas` and an overlay child. Keep all existing pointer-input modifiers on the `Canvas`; the overlay only intercepts its own 52 x 44dp button.

At composable scope, track `rotationHandleDragInProgress` with `remember { mutableStateOf(false) }`. In the existing rotation-handle hit branch set it to `true`; reset it in the `awaitEachGesture` cleanup path so every pointer-up, cancellation, and multi-touch handoff restores `false`.

Mount:

```kotlin
val selectedImages = state.images.filter { it.id in state.selectedImageIds }
SelectionHdControl(
    frame = selectionFrameOnCanvas(
        selectionAabb = groupSelectionAabb(selectedImages).rect,
        canvasScale = state.canvasScale,
        canvasOffset = state.canvasOffset,
        canvasSize = canvasSize,
    ),
    selectionState = stateFor(selectedImages.map(SceneImage::showsOriginalResolution)),
    visible = selectedImages.isNotEmpty() &&
        shouldShowOriginalResolutionControl(
            rotationActive = rotationHandleDragInProgress,
            containsAnimatedGif = selectedImages.any { isGifMimeType(it.mimeType) && it.cropRect == null },
        ),
    onClick = { onAction(PockRefAction.ToggleOriginalDisplaySelected) },
)
```

Keep the selection-frame calculation based on `groupSelectionAabb`, matching the existing multi-selection chrome.

- [ ] **Step 4: Run policy and build verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.SelectionHdControlPolicyTest"
./gradlew :app:assembleDebug
```

Expected: policy test PASS and Debug APK build SUCCESS.

- [ ] **Step 5: Commit the overlay**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt app/src/main/java/com/pockref/app/ui/SelectionHdControl.kt app/src/test/java/com/pockref/app/ui/SelectionHdControlPolicyTest.kt
git commit -m "feat: show HD control on selected canvas images"
```

### Task 5: Final Regression Verification

**Files:**
- Modify only if a failing targeted test exposes a defect in files listed above.

- [ ] **Step 1: Run all directly affected unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.SelectionHdControlPolicyTest" --tests "com.pockref.app.ui.PureRefActionsTest" --tests "com.pockref.app.data.ImageDecodePolicyTest" --tests "com.pockref.app.data.PurSceneCodecCustomFormatTest"
```

Expected: PASS. If an already-existing unrelated `PureRefActionsTest` count mismatch remains, update it only when its expected count is stale because this task added the one action.

- [ ] **Step 2: Build the Debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect the final diff**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and no staged or committed changes outside the files listed in this plan.

## Plan Self-Review

- Spec coverage: Task 1 implements iOS state, placement, visibility, and visual semantics; Task 2 implements persistent model, codec compatibility, and decoder behavior; Task 3 implements the atomic all-selected action with undo/redo; Task 4 mounts the accessible canvas overlay and rotation/GIF hiding; Task 5 verifies the requested behavior.
- Placeholder scan: no `TODO`, `TBD`, or deferred implementation steps remain.
- Type consistency: the action is named `ToggleOriginalDisplaySelected`; the model field is `showsOriginalResolution`; `OriginalResolutionSelectionState` and the policy functions are defined before their consumers.
