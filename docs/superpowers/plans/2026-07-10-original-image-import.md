# Original Image Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the exact source bytes, dimensions, and MIME type when importing images from gallery/files, clipboard, and camera.

**Architecture:** Add a small import decoder that reads image bounds without allocating or re-encoding a bitmap and returns the original `ByteArray` unchanged. Route both URI imports and clipboard imports through this decoder while leaving the existing display-size cap and render-time decode ladder unchanged.

**Tech Stack:** Kotlin, Android `BitmapFactory.Options.inJustDecodeBounds`, JUnit 4, Gradle.

---

### Task 1: Original-byte decoder

**Files:**
- Create: `app/src/main/java/com/pockref/app/data/OriginalImageImport.kt`
- Test: `app/src/test/java/com/pockref/app/data/OriginalImageImportTest.kt`

- [ ] **Step 1: Write failing preservation tests**

Create tests that call `decodeOriginalImage(bytes, declaredMime, boundsReader)` with a fake bounds reader and assert:

```kotlin
assertSame(sourceBytes, decoded!!.bytes)
assertEquals(6000, decoded.width)
assertEquals(4000, decoded.height)
assertEquals("image/jpeg", decoded.mimeType)
```

Also assert that non-positive decoded dimensions return `null`, and that detected image MIME replaces `application/octet-stream` without changing bytes.

- [ ] **Step 2: Verify the tests fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.OriginalImageImportTest"
```

Expected: compilation fails because `decodeOriginalImage` and its result/bounds types do not exist.

- [ ] **Step 3: Implement the minimal decoder**

Add an internal bounds DTO and result DTO. The public-to-module function must return the original `ByteArray` reference and use `BitmapFactory` only with `inJustDecodeBounds = true` in its default Android bounds reader.

- [ ] **Step 4: Verify decoder tests pass**

Run the same targeted Gradle command and expect all `OriginalImageImportTest` cases to pass.

### Task 2: Route all import sources through decoder

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt:174`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt:405`

- [ ] **Step 1: Replace clipboard optimization**

Change `decodeClipboardItem` from `imageOptimizer.optimize(rawBytes, mime)` to `decodeOriginalImage(rawBytes, mime)`, mapping the unchanged bytes, original bounds, and resolved MIME into `ClipboardDecodedImage`.

- [ ] **Step 2: Replace URI import optimization**

Change `importImages` to call the same decoder and populate `SceneImage.imageBytes`, `intrinsicWidth`, `intrinsicHeight`, and `mimeType` from its result. Keep the existing 720-unit display-size layout unchanged.

- [ ] **Step 3: Run targeted tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.OriginalImageImportTest"
```

Expected: PASS.

- [ ] **Step 4: Run app unit tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL with no test failures or Kotlin compilation errors.

### Task 3: Review final scope

**Files:**
- Review: `app/src/main/java/com/pockref/app/data/OriginalImageImport.kt`
- Review: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`
- Review: `app/src/test/java/com/pockref/app/data/OriginalImageImportTest.kt`

- [ ] **Step 1: Inspect the focused diff**

Run:

```bash
git diff -- app/src/main/java/com/pockref/app/data/OriginalImageImport.kt app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt app/src/test/java/com/pockref/app/data/OriginalImageImportTest.kt
```

Confirm every production change directly supports original-byte import and does not alter image optimization, display scaling, camera launch, or scene serialization behavior.
