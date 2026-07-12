# Canvas Pan Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep canvas pan work bounded to one state update per display frame and defer viewport decode work until a pan has paused for 80ms.

**Architecture:** A small UI helper accumulates pointer deltas and flushes them once per Compose frame clock tick. A second helper exposes the 80ms debounce policy as a testable Flow; `PockRefCanvas` uses it to trigger viewport decode and tile scheduling only after canvas offset changes settle. The existing ViewModel reducer remains the canonical owner of `canvasOffset`.

**Tech Stack:** Kotlin, Jetpack Compose frame clock, Kotlin Coroutines Flow, JUnit 4, kotlinx-coroutines-test, Android Macrobenchmark.

---

### Task 1: Add a Testable Per-Frame Pan Coalescer

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/CanvasPanFrameCoalescer.kt`
- Create: `app/src/test/java/com/pockref/app/ui/CanvasPanFrameCoalescerTest.kt`

- [ ] **Step 1: Write the failing coalescer tests**

```kotlin
package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasPanFrameCoalescerTest {

    @Test
    fun flush_combinesAllPointerDeltasSinceThePreviousFrame() {
        val coalescer = CanvasPanFrameCoalescer()

        coalescer.enqueue(Offset(2f, -1f))
        coalescer.enqueue(Offset(3f, 4f))

        assertEquals(Offset(5f, 3f), coalescer.flush())
    }

    @Test
    fun flush_resetsThePendingDeltaForTheNextFrame() {
        val coalescer = CanvasPanFrameCoalescer()
        coalescer.enqueue(Offset(5f, 3f))

        coalescer.flush()

        assertEquals(Offset.Zero, coalescer.flush())
    }

    @Test
    fun enqueue_afterFlush_startsANewFrameBatch() {
        val coalescer = CanvasPanFrameCoalescer()
        coalescer.enqueue(Offset(1f, 1f))
        coalescer.flush()
        coalescer.enqueue(Offset(-4f, 2f))

        assertEquals(Offset(-4f, 2f), coalescer.flush())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasPanFrameCoalescerTest"
```

Expected: compilation failure because `CanvasPanFrameCoalescer` does not exist.

- [ ] **Step 3: Implement the minimal coalescer**

```kotlin
package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset

internal class CanvasPanFrameCoalescer {
    private var pendingDelta = Offset.Zero

    fun enqueue(delta: Offset) {
        pendingDelta += delta
    }

    fun flush(): Offset {
        val delta = pendingDelta
        pendingDelta = Offset.Zero
        return delta
    }
}
```

Do not add locking: all callers run on the Compose main thread.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasPanFrameCoalescerTest"
```

Expected: all three tests pass.

- [ ] **Step 5: Commit the isolated helper**

```bash
git add app/src/main/java/com/pockref/app/ui/CanvasPanFrameCoalescer.kt app/src/test/java/com/pockref/app/ui/CanvasPanFrameCoalescerTest.kt
git commit -m "perf: coalesce canvas pan deltas per frame"
```

### Task 2: Make Viewport Decode Debouncing Explicit and Testable

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/CanvasViewportDecodeFlow.kt`
- Create: `app/src/test/java/com/pockref/app/ui/CanvasViewportDecodeFlowTest.kt`

**Boundary:** This task intentionally supplies and tests the reusable Flow
operator only. Task 4 is responsible for connecting it to the
`PockRefCanvas` viewport decode and tile effects.

- [ ] **Step 1: Write the failing debounce test**

```kotlin
package com.pockref.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasViewportDecodeFlowTest {

    @Test
    fun debounceCanvasPanViewportUpdates_emitsOnlyAfterPanIsIdleFor80ms() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var emissions = 0
        val job = launch {
            source.debounceCanvasPanViewportUpdates().collect { emissions += 1 }
        }

        source.emit(Unit)
        advanceTimeBy(79)
        assertEquals(0, emissions)

        source.emit(Unit)
        advanceTimeBy(79)
        assertEquals(0, emissions)

        advanceTimeBy(1)
        assertEquals(1, emissions)
        job.cancel()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasViewportDecodeFlowTest"
```

Expected: compilation failure because `debounceCanvasPanViewportUpdates` does not exist.

- [ ] **Step 3: Implement the flow extension**

```kotlin
package com.pockref.app.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

internal const val CANVAS_PAN_VIEWPORT_DECODE_DELAY_MS = 80L

internal fun Flow<Unit>.debounceCanvasPanViewportUpdates(): Flow<Unit> =
    debounce(CANVAS_PAN_VIEWPORT_DECODE_DELAY_MS)
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasViewportDecodeFlowTest"
```

Expected: the test passes after exactly one post-idle emission.

- [ ] **Step 5: Commit the isolated debounce policy**

```bash
git add app/src/main/java/com/pockref/app/ui/CanvasViewportDecodeFlow.kt app/src/test/java/com/pockref/app/ui/CanvasViewportDecodeFlowTest.kt
git commit -m "perf: debounce viewport decode after canvas pan"
```

### Task 3: Route Canvas Pointer Pan Through the Frame Clock

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt:784-799`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:7690-7692`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:8207-8218`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:9160-9180`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:9564-9575`

- [ ] **Step 1: Extend the existing canvas gesture test with frame-batching intent**

Add this test to `CanvasPanFrameCoalescerTest.kt`:

```kotlin
@Test
fun multipleSamplesInOneFrame_preserveTheFullFingerTravelDistance() {
    val coalescer = CanvasPanFrameCoalescer()
    coalescer.enqueue(Offset(1.25f, 0.5f))
    coalescer.enqueue(Offset(2.75f, -1.5f))

    assertEquals(Offset(4f, -1f), coalescer.flush())
}

@Test
fun largeFrameBatch_isSplitWithoutDroppingAnyPanDistance() {
    assertEquals(
        listOf(Offset(200f, -200f), Offset(100f, -50f)),
        splitCanvasPanDelta(Offset(300f, -250f)),
    )
}
```

- [ ] **Step 2: Run the focused tests to verify the new assertion is meaningful**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasPanFrameCoalescerTest"
```

Expected: passes only after Task 1's coalescer implementation is present.

- [ ] **Step 3: Centralize the reducer's pan-step limit and add a split helper**

Replace the reducer-local `val maxStep = 200f` with this top-level constant in
`PureRefViewModel.kt`, beside `MIN_CANVAS_SCALE` / `MAX_CANVAS_SCALE`:

```kotlin
internal const val MAX_CANVAS_PAN_STEP_PX: Float = 200f
```

Use it in the reducer:

```kotlin
val dx = action.delta.x.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX)
val dy = action.delta.y.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX)
```

Add this helper beside `CanvasPanFrameCoalescer`:

```kotlin
internal fun splitCanvasPanDelta(delta: Offset): List<Offset> {
    var remaining = delta
    val steps = mutableListOf<Offset>()
    while (remaining != Offset.Zero) {
        val step = Offset(
            x = remaining.x.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX),
            y = remaining.y.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX),
        )
        steps += step
        remaining -= step
    }
    return steps
}
```

- [ ] **Step 4: Add a frame-clock flusher in `PockRefCanvas`**

Immediately after the existing `currentState` / `currentOnAction` declarations, add:

```kotlin
val panCoalescer = remember { CanvasPanFrameCoalescer() }
fun flushQueuedCanvasPan() {
    val delta = panCoalescer.flush()
    splitCanvasPanDelta(delta).forEach { step ->
        currentOnAction(PockRefAction.PanCanvas(step))
    }
}
LaunchedEffect(panCoalescer) {
    while (true) {
        withFrameNanos {
            flushQueuedCanvasPan()
        }
    }
}
```

Use the existing Compose runtime `withFrameNanos` import and add it if it is not already imported.

- [ ] **Step 5: Replace direct pointer-pan dispatches**

At all three current pointer-pan sites, replace:

```kotlin
currentOnAction(PockRefAction.PanCanvas(panDelta))
```

with:

```kotlin
panCoalescer.enqueue(panDelta)
```

The three sites are:

1. markup two-finger canvas transform,
2. normal two-finger transform,
3. enabled single-finger empty-canvas pan.

Do not replace minimap pan or drag-to-edge auto-scroll dispatches; those paths have independent interaction timing.

- [ ] **Step 6: Flush on gesture completion**

Before the normal gesture's tap/double-tap resolution after the pointer loop, call:

```kotlin
flushQueuedCanvasPan()
```

Before each markup transform path returns from `awaitEachGesture`, call the same function after the final pointer event is consumed. This preserves the final sub-frame finger distance before any state-dependent release logic runs.

- [ ] **Step 7: Run the canvas gesture and coalescer tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasPanGestureTest" --tests "com.pockref.app.ui.CanvasPanFrameCoalescerTest"
```

Expected: all canvas pan and coalescer tests pass.

- [ ] **Step 8: Commit the frame-paced integration**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt app/src/main/java/com/pockref/app/ui/CanvasPanFrameCoalescer.kt app/src/test/java/com/pockref/app/ui/CanvasPanFrameCoalescerTest.kt
git commit -m "perf: pace canvas pan updates to display frames"
```

### Task 4: Debounce Decode and Tile Work While the Viewport Moves

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt:7832-7911`

- [ ] **Step 1: Add the pan-request flow beside the existing canvas state**

After `currentState` / `currentOnAction`, create a one-slot stream:

```kotlin
val panViewportUpdates = remember {
    MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
```

Add imports for `BufferOverflow`, `MutableSharedFlow`, and `collectLatest`.

- [ ] **Step 2: Extract the existing viewport decode body into a local suspend function**

Move the body currently inside the `LaunchedEffect` at lines 7832-7863 into:

```kotlin
suspend fun requestViewportDecode() {
    val s = currentState
    val viewport = worldViewportRect(
        canvasOffset = s.canvasOffset,
        canvasScale = s.canvasScale,
        viewportWidth = s.canvasViewportSize.width,
        viewportHeight = s.canvasViewportSize.height,
    )
    if (viewport.width <= 0f || viewport.height <= 0f) return
    pipeline.onViewportChanged(
        images = s.images,
        viewportRect = android.graphics.RectF(viewport.left, viewport.top, viewport.right, viewport.bottom),
        cachedImageIds = bitmapCache.keys.toSet(),
        cachedLevels = bitmapCache.mapValues { it.value.first },
    ) { image ->
        ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = s.canvasScale,
            viewportWidth = s.canvasViewportSize.width,
            viewportHeight = s.canvasViewportSize.height,
            isFocusedImage = image.id == focusedHighResImageId,
        )
    }
}
```

- [ ] **Step 3: Separate immediate non-pan invalidations from debounced pan invalidations**

Replace the old viewport `LaunchedEffect` with these three effects:

```kotlin
LaunchedEffect(state.canvasScale, state.canvasViewportSize, state.images, focusedHighResImageId) {
    requestViewportDecode()
}

LaunchedEffect(state.canvasOffset) {
    panViewportUpdates.tryEmit(Unit)
}

LaunchedEffect(panViewportUpdates) {
    panViewportUpdates
        .debounceCanvasPanViewportUpdates()
        .collectLatest {
            requestViewportDecode()
        }
}
```

This keeps scale, scene, viewport-size, and focused-image changes immediate. Repeated pan updates only emit one request after 80ms without offset changes.

- [ ] **Step 4: Apply the same idle gate to tile scheduling**

Extract the current tile `LaunchedEffect` body into this local suspend function:

```kotlin
suspend fun requestVisibleTiles() {
    if (tileCacheImageId != tileImageId) {
        tileCache.clear()
        tileCacheImageId = tileImageId
    }

    val activeImage = tileImageId?.let { id ->
        currentState.images.firstOrNull { it.id == id }
    }
    if (
        tilePipeline == null ||
        activeImage == null ||
        !ImageTilePolicy.shouldRequestTiles(
            image = activeImage,
            canvasScale = currentState.canvasScale,
            isSingleImageSelected = true,
        )
    ) {
        tileCache.clear()
        return
    }

    val s = currentState
    val viewport = worldViewportRect(
        canvasOffset = s.canvasOffset,
        canvasScale = s.canvasScale,
        viewportWidth = s.canvasViewportSize.width,
        viewportHeight = s.canvasViewportSize.height,
        bufferPx = 0f,
    )
    val requests = ImageTilePlanner.visibleTiles(activeImage, viewport)
    val missing = requests.filterNot { it.key in tileCache }
    tilePipeline.decode(activeImage, missing) { decoded ->
        tileCache[decoded.request.key] = decoded.bitmap.asImageBitmap()
    }

    if (tileCache.size > MAX_TILE_CACHE_ENTRIES) {
        val visibleKeys = requests.mapTo(mutableSetOf()) { it.key }
        tileCache.keys
            .filter { it !in visibleKeys }
            .take(tileCache.size - MAX_TILE_CACHE_ENTRIES)
            .forEach { tileCache.remove(it) }
    }
}
```

Then replace the three effects from Step 3 with:

```kotlin
LaunchedEffect(
    state.canvasScale,
    state.canvasViewportSize,
    state.images,
    focusedHighResImageId,
    tileImageId,
    tilePipeline,
) {
    requestViewportDecode()
    requestVisibleTiles()
}

LaunchedEffect(state.canvasOffset) {
    panViewportUpdates.tryEmit(Unit)
}

LaunchedEffect(panViewportUpdates) {
    panViewportUpdates
        .debounceCanvasPanViewportUpdates()
        .collectLatest {
            requestViewportDecode()
            requestVisibleTiles()
        }
}
```

This keeps tile-cache selection invalidation immediate whenever its selected
image changes, while offset-only panning waits for the 80ms idle window. Do
not clear `tileCache` solely because `canvasOffset` changes.

- [ ] **Step 5: Run the debounce test and all unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.CanvasViewportDecodeFlowTest"
./gradlew :app:testDebugUnitTest
```

Expected: the focused Flow test and the full unit suite pass.

- [ ] **Step 6: Commit the viewport scheduling integration**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt app/src/main/java/com/pockref/app/ui/CanvasViewportDecodeFlow.kt app/src/test/java/com/pockref/app/ui/CanvasViewportDecodeFlowTest.kt
git commit -m "perf: defer viewport decode during canvas pan"
```

### Task 5: Build and Measure on the Target Tablet

**Files:**
- Verify: `benchmark/src/main/java/com/pockref/benchmark/ScrollFrameTimingBenchmark.kt`
- Verify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`

- [ ] **Step 1: Build the debug APK and benchmark target**

Run:

```bash
./gradlew :app:assembleDebug :benchmark:assembleBenchmark
```

Expected: both APKs build successfully.

- [ ] **Step 2: Verify that the Galaxy Tab S10+ is connected**

Run:

```bash
adb devices
```

Expected: one device in the `device` state.

- [ ] **Step 3: Run the canvas frame-timing benchmark**

Run:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.pockref.benchmark.ScrollFrameTimingBenchmark
```

Expected: Macrobenchmark completes and writes `FrameTimingMetric` output under `benchmark/build/outputs/connected_android_test_additional_output/`.

- [ ] **Step 4: Inspect the pan result**

Run:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -type f | sort
```

Inspect the JSON/CSV report for the canvas-pan case. Accept the change only when P95 frame duration is at or below 16.7ms. If the device is not connected, record the benchmark as pending rather than claiming the 60fps target is met.

- [ ] **Step 5: Commit the final verified change**

```bash
git status --short
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt app/src/main/java/com/pockref/app/ui/CanvasPanFrameCoalescer.kt app/src/main/java/com/pockref/app/ui/CanvasViewportDecodeFlow.kt app/src/test/java/com/pockref/app/ui/CanvasPanFrameCoalescerTest.kt app/src/test/java/com/pockref/app/ui/CanvasViewportDecodeFlowTest.kt
git commit -m "perf: smooth canvas panning"
```

Do not stage unrelated existing changes.
