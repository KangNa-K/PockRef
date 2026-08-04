# Retain HD During Global Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep an image's already-enabled original-resolution tile display visible while global image lock or canvas freeze clears the selection.

**Architecture:** Preserve only the single selected HD image ID in a transient `PockRefUiState` field when entering global lock/freeze. The Canvas tile policy will use that retained ID only while lock mode is engaged, so the existing selection-scoped HD behavior remains unchanged in normal mode. The retained ID is cleared when lock mode fully exits or a new scene replaces the current images; the persisted `SceneImage.showsOriginalResolution` flag is not changed.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow/ViewModel, JUnit 4, Gradle.

---

## File Structure

- `app/src/main/java/com/pockref/app/model/Models.kt`: add the transient retained-HD image ID to `PockRefUiState`.
- `app/src/main/java/com/pockref/app/data/ImageTilePolicy.kt`: add pure validation/display helpers for a retained HD image.
- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`: capture the current single HD image before lock/freeze deselects it, preserve it across soft/hard transitions, and clear it on full unlock.
- `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`: clear the transient ID when loading, restoring, or closing a scene.
- `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`: derive the tile target from the retained ID while locked and re-run tile requests when lock state changes.
- `app/src/test/java/com/pockref/app/data/ImageTilePolicyTest.kt`: cover retained HD display eligibility and normal-mode gating.

### Task 1: Add Pure Retained-HD Policy Tests

**Files:**
- Modify: `app/src/test/java/com/pockref/app/data/ImageTilePolicyTest.kt`
- Modify: `app/src/main/java/com/pockref/app/data/ImageTilePolicy.kt`

- [x] **Step 1: Write failing tests**

Add tests that assert an HD image can be returned by the display policy after selection is empty only when lock retention is enabled, and that disabled, missing, and GIF images are rejected.

- [x] **Step 2: Run the focused test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.ImageTilePolicyTest"
```

Expected: the new tests fail to compile until the retained-image policy exists.

- [x] **Step 3: Implement the smallest pure helper**

Add an image-ID validator that reuses the existing non-GIF and `showsOriginalResolution` checks, then add a display policy that prefers the current single selected HD image and falls back to a validated retained ID only when the caller says lock retention is active.

- [ ] **Step 4: Re-run the focused test**

Expected: `ImageTilePolicyTest` passes.

Blocked in this environment: the available Java 17 runtime is a trimmed JRE without `java.management`, so Gradle exits before compiling tests.

### Task 2: Preserve the HD ID Through Lock/Frozen Reducers

**Files:**
- Modify: `app/src/main/java/com/pockref/app/model/Models.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`

- [x] **Step 1: Add transient state**

Add `lockRetainedHdImageId: String? = null` to `PockRefUiState` with a comment stating that it is render-only, not persisted, and not part of scene history.

- [x] **Step 2: Capture before deselection**

In `ToggleGlobalImageLock` and `ToggleLockCanvasFrozen`, compute the current single selected HD image ID before calling `deselect()`. When escalating soft lock to freeze, preserve the already-retained ID if selection is already empty.

- [x] **Step 3: Clear only on full unlock**

Keep the retained ID while either global lock or canvas freeze remains enabled. Clear it when both flags become false.

- [x] **Step 4: Reset on scene replacement**

Set the transient ID to `null` in scene load, autosave restore, and close-scene state replacements.

### Task 3: Keep Canvas Tile Requests Alive While Locked

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`

- [x] **Step 1: Use the retained ID in tile selection**

Include lock flags and `lockRetainedHdImageId` in the `remember` keys. Resolve the tile image through the new policy so a deselected retained HD image remains the active tile target only in lock/freeze mode.

- [x] **Step 2: Re-trigger tile loading on lock transitions**

Include the relevant lock/retained-ID state in the tile `LaunchedEffect` keys so a lock transition requests missing tiles even when the image ID itself is unchanged.

- [x] **Step 3: Preserve existing interaction semantics**

Do not restore image selection or the selection sheet. The retained image should only affect bitmap tile rendering; lock/freeze must continue to block image interaction as before.

### Task 4: Verify the Change

**Files:**
- Test: `app/src/test/java/com/pockref/app/data/ImageTilePolicyTest.kt`

- [ ] **Step 1: Run focused unit tests**

Run the ImageTilePolicy test class and confirm all tests pass.

- [ ] **Step 2: Run the broader JVM unit-test suite**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: the suite passes when a Java Runtime is available.

- [x] **Step 3: Inspect the final diff**

Confirm only the HD retention implementation, its tests, the scene-reset wiring, and this plan document changed. Do not create a commit unless explicitly requested.
