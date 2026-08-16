# Floating-Window S Pen Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Make the existing S Pen side-button selection binding work inside PockRef's own focusable floating window without consuming Compose input.

**Architecture:** Extract raw button-state edge detection into a testable SpenMotionEventBridge that wraps the existing SpenButtonDetector. MainActivity and the floating window each create their own bridge, use the same configured SpenMode, and dispatch detector events through MainActivity's existing selection-action callback. The floating window uses a root FrameLayout that observes touch and generic motion before forwarding them unchanged to its child ComposeView.

**Tech Stack:** Kotlin, Android MotionEvent, FrameLayout, Jetpack Compose, ViewModel/StateFlow, JUnit 4.

---

## File Structure

### Create

- app/src/main/java/com/pockref/app/spen/SpenMotionEventBridge.kt
  - Converts raw Android button-state levels into down/up edges for one SpenButtonDetector.
- app/src/test/java/com/pockref/app/spen/SpenMotionEventBridgeTest.kt
  - Pure JVM coverage for supported button bits, repeated levels, release, reset, and unrelated buttons.

### Modify

- app/src/main/java/com/pockref/app/MainActivity.kt
  - Replace Activity-local button-bit tracking with SpenMotionEventBridge.
  - Pass the current S Pen mode and existing action callback into the floating-window session.
- app/src/main/java/com/pockref/app/FloatingWindowService.kt
  - Store the mode and event callback in FloatingWindowSession.
  - Build a floating-window-local detector and bridge.
  - Host Compose in a root FrameLayout that observes both touch and hover dispatch paths without consuming them.

### Leave Unchanged

- app/src/main/java/com/pockref/app/spen/SpenButtonDetector.kt
  - Its gesture state machine and existing tests remain the source of truth.
- app/src/main/java/com/pockref/app/ui/PureRefApp.kt
  - The existing ComposeView raw-touch diagnostic listener stays intact; the new observer lives one View level above it.

## Task 1: Extract a Testable Button-State Edge Bridge

**Files:**
- Create: app/src/main/java/com/pockref/app/spen/SpenMotionEventBridge.kt
- Create: app/src/test/java/com/pockref/app/spen/SpenMotionEventBridgeTest.kt

- [ ] **Step 1: Write the failing bridge tests**

Create SpenMotionEventBridgeTest.kt. The tests call onButtonState directly, so they do not need Robolectric or constructed MotionEvent objects.

~~~kotlin
package com.pockref.app.spen

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SpenMotionEventBridgeTest {
    private class Harness {
        var now = 0L
        val events = mutableListOf<SpenButtonDetector.ButtonEvent>()
        private val scheduler = FakeSpenButtonScheduler { now }
        private val detector = SpenButtonDetector(
            mode = SpenMode.PRESS_AND_HOLD,
            clock = { now },
            scheduler = scheduler,
            onEvent = events::add,
        )
        val bridge = SpenMotionEventBridge(detector)
    }

    @Test
    fun eachSupportedStylusBit_opensAndClosesBoxSelectionOnce() {
        val supportedBits = listOf(
            MotionEvent.BUTTON_STYLUS_PRIMARY,
            MotionEvent.BUTTON_STYLUS_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
        )

        supportedBits.forEach { bit ->
            val h = Harness()
            h.bridge.onButtonState(bit)
            h.bridge.onButtonState(bit)
            h.bridge.onButtonState(0)

            assertEquals(
                listOf(
                    SpenButtonDetector.ButtonEvent.BoxStart,
                    SpenButtonDetector.ButtonEvent.BoxEnd,
                ),
                h.events,
            )
        }
    }

    @Test
    fun unrelatedButtonState_doesNotReachDetector() {
        val h = Harness()

        h.bridge.onButtonState(MotionEvent.BUTTON_PRIMARY)
        h.bridge.onButtonState(0)

        assertEquals(emptyList<SpenButtonDetector.ButtonEvent>(), h.events)
    }

    @Test
    fun reset_discardsHeldLevel_beforeTheNextPress() {
        val h = Harness()

        h.bridge.onButtonState(MotionEvent.BUTTON_STYLUS_PRIMARY)
        h.bridge.reset()
        h.bridge.onButtonState(0)
        h.bridge.onButtonState(MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxStart,
            ),
            h.events,
        )
    }
}
~~~

- [ ] **Step 2: Run the new test and confirm it fails because the bridge is absent**

Run:

~~~bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.spen.SpenMotionEventBridgeTest"
~~~

Expected: compilation fails with unresolved reference SpenMotionEventBridge.

- [ ] **Step 3: Implement the bridge with no gesture-policy changes**

Create SpenMotionEventBridge.kt:

~~~kotlin
package com.pockref.app.spen

import android.view.MotionEvent

/** Converts raw MotionEvent button levels into detector down/up edges. */
class SpenMotionEventBridge(
    private val detector: SpenButtonDetector,
) {
    val mode: SpenMode
        get() = detector.mode

    private var lastStylusButtonBits = 0

    fun onButtonState(buttonState: Int) {
        val stylusBits = buttonState and STYLUS_BUTTON_MASK
        if (stylusBits == 0 && lastStylusButtonBits == 0) return

        if (stylusBits != 0 && lastStylusButtonBits == 0) {
            detector.onDown()
        } else if (stylusBits == 0 && lastStylusButtonBits != 0) {
            detector.onUp()
        }
        lastStylusButtonBits = stylusBits
    }

    fun reset() {
        lastStylusButtonBits = 0
        detector.reset()
    }

    private companion object {
        val STYLUS_BUTTON_MASK =
            MotionEvent.BUTTON_STYLUS_PRIMARY or
                MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_TERTIARY
    }
}
~~~

- [ ] **Step 4: Run bridge and existing detector tests**

Run:

~~~bash
./gradlew :app:testDebugUnitTest \
  --tests "com.pockref.app.spen.SpenMotionEventBridgeTest" \
  --tests "com.pockref.app.spen.SpenButtonDetectorTest"
~~~

Expected: both test classes pass. The bridge proves only level transitions produce detector edges; the existing suite proves mode semantics are unchanged.

- [ ] **Step 5: Commit the isolated bridge**

~~~bash
git add app/src/main/java/com/pockref/app/spen/SpenMotionEventBridge.kt \
  app/src/test/java/com/pockref/app/spen/SpenMotionEventBridgeTest.kt
git commit -m "feat(spen): extract motion event bridge"
~~~

## Task 2: Reuse the Existing Activity Action Callback in Both Hosts

**Files:**
- Modify: app/src/main/java/com/pockref/app/MainActivity.kt
- Modify: app/src/main/java/com/pockref/app/FloatingWindowService.kt

- [ ] **Step 1: Replace MainActivity's local edge state with the bridge**

Replace the existing detector and lastStylusButtonBits properties with:

~~~kotlin
private lateinit var spenInputBridge: SpenMotionEventBridge
~~~

Replace both dispatch bodies and preserve framework propagation:

~~~kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (::spenInputBridge.isInitialized) {
        spenInputBridge.onButtonState(event.buttonState)
    }
    return super.dispatchTouchEvent(event)
}

override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (::spenInputBridge.isInitialized) {
        spenInputBridge.onButtonState(event.buttonState)
    }
    return super.dispatchGenericMotionEvent(event)
}
~~~

Delete handleMotionEvent(). Update the two S Pen hook comments so they state that an Activity sees events dispatched to its own window, while the floating window has a separate bridge. In onCreate, wrap the existing detector construction:

~~~kotlin
spenInputBridge = SpenMotionEventBridge(
    SpenButtonDetector(
        mode = AppPreferences(this).spenMode,
        onEvent = ::dispatchSpenEvent,
    ),
)
~~~

In onPause, replace both old resets with:

~~~kotlin
if (::spenInputBridge.isInitialized) {
    spenInputBridge.reset()
}
~~~

- [ ] **Step 2: Carry the canonical mode and action callback through FloatingWindowSession**

Replace the stored values and session methods with the following complete implementation. This intentionally passes the existing MainActivity callback, instead of duplicating dispatchSpenEvent() in the service.

~~~kotlin
private var activity: MainActivity? = null
private var viewModel: PockRefViewModel? = null
private var spenMode: SpenMode? = null
private var onSpenEvent: ((SpenButtonDetector.ButtonEvent) -> Unit)? = null

fun attach(
    hostActivity: MainActivity,
    hostViewModel: PockRefViewModel,
    hostSpenMode: SpenMode,
    hostOnSpenEvent: (SpenButtonDetector.ButtonEvent) -> Unit,
) {
    activity = hostActivity
    viewModel = hostViewModel
    spenMode = hostSpenMode
    onSpenEvent = hostOnSpenEvent
}

fun current(): Host? {
    val currentActivity = activity ?: return null
    val currentViewModel = viewModel ?: return null
    val currentSpenMode = spenMode ?: return null
    val currentOnSpenEvent = onSpenEvent ?: return null
    return Host(
        activity = currentActivity,
        viewModel = currentViewModel,
        spenMode = currentSpenMode,
        onSpenEvent = currentOnSpenEvent,
    )
}

fun clear() {
    activity = null
    viewModel = null
    spenMode = null
    onSpenEvent = null
}

data class Host(
    val activity: MainActivity,
    val viewModel: PockRefViewModel,
    val spenMode: SpenMode,
    val onSpenEvent: (SpenButtonDetector.ButtonEvent) -> Unit,
)
~~~

Change FloatingWindowService.launch() to this signature and implementation:

~~~kotlin
fun launch(
    activity: MainActivity,
    viewModel: PockRefViewModel,
    spenMode: SpenMode,
    onSpenEvent: (SpenButtonDetector.ButtonEvent) -> Unit,
) {
    FloatingWindowSession.attach(activity, viewModel, spenMode, onSpenEvent)
    activity.startService(Intent(activity, FloatingWindowService::class.java))
}
~~~

Update both MainActivity call sites, including the overlay-permission onResume path:

~~~kotlin
FloatingWindowService.launch(
    activity = this,
    viewModel = viewModel,
    spenMode = spenInputBridge.mode,
    onSpenEvent = ::dispatchSpenEvent,
)
~~~

- [ ] **Step 3: Compile the host/session boundary before wiring overlay input**

Run:

~~~bash
./gradlew :app:compileDebugKotlin
~~~

Expected: BUILD SUCCESSFUL. MainActivity remains the one canonical place for mapping detector events into PockRef actions.

- [ ] **Step 4: Commit the shared host contract**

~~~bash
git add app/src/main/java/com/pockref/app/MainActivity.kt \
  app/src/main/java/com/pockref/app/FloatingWindowService.kt
git commit -m "refactor(spen): share input mode with floating host"
~~~

## Task 3: Observe Floating-Window Touch and Hover Before Compose

**Files:**
- Modify: app/src/main/java/com/pockref/app/FloatingWindowService.kt

- [ ] **Step 1: Add a non-consuming root dispatch container**

Add imports for Context, MotionEvent, View, ViewGroup, FrameLayout, SpenMotionEventBridge, and SpenButtonDetector. Before FloatingWindowService, add:

~~~kotlin
private class SpenInputFrameLayout(
    context: Context,
    private val onMotionEvent: (MotionEvent) -> Unit,
) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onMotionEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        onMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }
}
~~~

- [ ] **Step 2: Use that container as the WindowManager root**

Change service fields:

~~~kotlin
private var overlayView: View? = null
private var overlayOwner: FloatingWindowLifecycleOwner? = null
private var layoutParams: WindowManager.LayoutParams? = null
private var spenInputBridge: SpenMotionEventBridge? = null
~~~

At the start of onDestroy(), reset and discard the local bridge:

~~~kotlin
spenInputBridge?.reset()
spenInputBridge = null
~~~

In showFloatingWindow(), replace the direct ComposeView root with the bridge and FrameLayout root below. Keep the existing FloatingWindowFrame arguments unchanged.

~~~kotlin
val inputBridge = SpenMotionEventBridge(
    SpenButtonDetector(
        mode = host.spenMode,
        onEvent = host.onSpenEvent,
    ),
)
val view = SpenInputFrameLayout(host.activity) { event ->
    inputBridge.onButtonState(event.buttonState)
}.apply {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(host.activity)
    setViewTreeSavedStateRegistryOwner(host.activity)
    setViewTreeOnBackPressedDispatcherOwner(host.activity)
    addView(
        ComposeView(host.activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setContent {
                val state by host.viewModel.uiState.collectAsState()
                PockRefTheme(themeMode = state.themeMode) {
                    FloatingWindowFrame(
                        viewModel = host.viewModel,
                        state = state,
                        onMove = ::moveWindow,
                        onResize = ::resizeWindow,
                        onRestoreFullScreen = ::restoreFullScreen,
                        onCloseFloatingWindow = ::closeFloatingWindow,
                    )
                }
            }
        },
    )
}
spenInputBridge = inputBridge
overlayOwner = owner
overlayView = view
layoutParams = params
windowManager.addView(view, params)
~~~

The root forwards untouched events to Compose after observation, so it cannot consume canvas or chrome gestures. Its tree owners remain visible to the child ComposeView through the View hierarchy.

- [ ] **Step 3: Build and run every S Pen unit test**

Run:

~~~bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest \
  --tests "com.pockref.app.spen.SpenMotionEventBridgeTest" \
  --tests "com.pockref.app.spen.SpenButtonDetectorTest"
~~~

Expected: compilation succeeds, both test classes pass, and no PockRefApp source changes are required.

- [ ] **Step 4: Perform the Galaxy Tab S10+ manual acceptance pass**

1. In full screen, press and release the S Pen button over the canvas; verify the configured selection behavior is unchanged.
2. Enter PockRef's floating window and repeat with the pen tip on the floating canvas; verify box selection starts and exits.
3. Hover over the floating canvas and repeat; verify the generic-motion path produces the same state change.
4. Pan, pinch, use the floating-window move handle, resize the window, and click its controls; verify the root container has not consumed any of those gestures.
5. Hold the S Pen button, restore full screen or close the floating window, then press again in full screen; verify the next press starts fresh.
6. If a floating-window press never reaches box selection, inspect PockRefSpen logs. A stylus event with buttonState equal to zero is an OEM overlay limitation; a nonzero value without selection behavior is an application defect that must be fixed before merge.

- [ ] **Step 5: Commit the floating-window input path**

~~~bash
git add app/src/main/java/com/pockref/app/FloatingWindowService.kt
git commit -m "fix(spen): handle side button in floating window"
~~~

## Task 4: Run the Focused Regression Suite

**Files:**
- No source changes expected.

- [ ] **Step 1: Run focused tests and Android lint**

Run:

~~~bash
./gradlew :app:testDebugUnitTest \
  --tests "com.pockref.app.spen.SpenMotionEventBridgeTest" \
  --tests "com.pockref.app.spen.SpenButtonDetectorTest"
./gradlew :app:lintDebug
~~~

Expected: both commands finish with BUILD SUCCESSFUL. Investigate any new lint issue in MainActivity.kt or FloatingWindowService.kt rather than suppressing it.

- [ ] **Step 2: Inspect the final change set**

Run:

~~~bash
git status --short
git log --oneline -3
git diff HEAD~3..HEAD --check
~~~

Expected: the worktree is clean, the three implementation commits are present, and whitespace validation produces no output.
