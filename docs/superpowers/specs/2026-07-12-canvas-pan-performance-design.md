# Canvas Pan Performance Design

## Goal

Make canvas panning feel stable at 60fps on the Galaxy Tab S10+ with a few
ordinary images. The acceptance target is a `FrameTimingMetric` P95 frame
duration at or below 16.7ms during canvas pan.

## Observed Cost

`PanCanvas` currently updates the shared `PockRefUiState` for every pointer
move. Each `canvasOffset` change restarts viewport decode and tile
`LaunchedEffect`s in `PockRefCanvas`, even though a pan should only redraw the
already-cached images.

## Design

### Frame-Coalesced Pan

Keep the latest pointer delta in local canvas state. A frame-clock coroutine
flushes at most one accumulated delta to `PockRefAction.PanCanvas` per display
frame. The reducer remains the source of truth for the committed canvas
offset, so minimap, hit testing, and other consumers retain their existing
contract.

The flush applies the full accumulated delta, rather than dropping intermediate
input samples. Gesture end flushes any remaining delta before normal tap and
selection handling completes.

### Deferred Decode Scheduling

Viewport-based image decode and tile scheduling no longer run on every
`canvasOffset` update. They observe the viewport transform through a debounced
flow and run after the transform has remained unchanged for 80ms. Scene,
viewport-size, focused-image, and scale changes still schedule immediately
when they are not part of an active pan.

The draw loop continues to render cached bitmaps while moving. Existing
viewport culling remains unchanged.

## Alternatives

1. Extract canvas transform from `PockRefUiState` into an independent local
   rendering state. This could reduce recomposition more aggressively, but
   would change minimap, hit-test, and selection synchronization contracts.
2. Only debounce decode scheduling. This is lower risk but leaves high-rate
   full-state updates from touch sampling in place.

The selected approach combines frame-coalescing with deferred decode scheduling
for a focused, lower-risk change.

## Validation

1. Add unit tests for delta accumulation and one-flush-per-frame behavior.
2. Add a focused unit test for the 80ms decode-scheduling policy.
3. Run `:app:testDebugUnitTest` and assemble the debug APK.
4. Run the existing `ScrollFrameTimingBenchmark` on the Galaxy Tab S10+ and
   inspect frame timing. P95 must be at or below 16.7ms for the pan scenario.

## Non-Goals

- Changing image culling, bitmap quality, or decode ladder thresholds.
- Refactoring the shared UI-state architecture.
- Guaranteeing 60fps under device thermal throttling or external system load.
