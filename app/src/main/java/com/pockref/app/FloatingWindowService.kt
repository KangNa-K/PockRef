package com.pockref.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pockref.app.ui.PockRefApp
import com.pockref.app.ui.PockRefViewModel
import com.pockref.app.ui.theme.PockRefTheme
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Process-local bridge between the visible Activity and the overlay service.
 *
 * The overlay deliberately renders the same ViewModel instance as MainActivity.
 * This keeps the canvas, undo history, active document, file permissions, and
 * Activity Result registrations intact while the user works in another app.
 */
private object FloatingWindowSession {
    private var activity: MainActivity? = null
    private var viewModel: PockRefViewModel? = null

    fun attach(hostActivity: MainActivity, hostViewModel: PockRefViewModel) {
        activity = hostActivity
        viewModel = hostViewModel
    }

    fun current(): Host? {
        val currentActivity = activity ?: return null
        val currentViewModel = viewModel ?: return null
        return Host(currentActivity, currentViewModel)
    }

    fun clear() {
        activity = null
        viewModel = null
    }

    data class Host(
        val activity: MainActivity,
        val viewModel: PockRefViewModel,
    )
}

/** A lifecycle that remains active while MainActivity is backgrounded. */
private class FloatingWindowLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * A focusable [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] host for
 * PockRef's existing Compose application. Unlike Android PiP, this window
 * receives normal touch input from the canvas and every existing control.
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayOwner: FloatingWindowLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatingWindow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        overlayView = null
        overlayOwner?.destroy()
        overlayOwner = null
        layoutParams = null
        FloatingWindowSession.clear()
        super.onDestroy()
    }

    private fun showFloatingWindow() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val host = FloatingWindowSession.current() ?: run {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val owner = FloatingWindowLifecycleOwner()
        val params = createLayoutParams()
        val view = ComposeView(host.activity).apply {
            // The Activity supplies file pickers and back dispatch; this owner
            // keeps composition and gesture handling alive after it is hidden.
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(host.activity)
            setViewTreeSavedStateRegistryOwner(host.activity)
            setViewTreeOnBackPressedDispatcherOwner(host.activity)
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
        }

        overlayOwner = owner
        overlayView = view
        layoutParams = params
        windowManager.addView(view, params)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        val width = savedDimension(
            key = KEY_WIDTH,
            fallback = (metrics.widthPixels * DEFAULT_WINDOW_WIDTH_FRACTION).roundToInt(),
            min = dp(MIN_WINDOW_WIDTH_DP),
            max = (metrics.widthPixels - dp(WINDOW_MARGIN_DP)).coerceAtLeast(dp(MIN_WINDOW_WIDTH_DP)),
        )
        val height = savedDimension(
            key = KEY_HEIGHT,
            fallback = (metrics.heightPixels * DEFAULT_WINDOW_HEIGHT_FRACTION).roundToInt(),
            min = dp(MIN_WINDOW_HEIGHT_DP),
            max = (metrics.heightPixels - dp(WINDOW_MARGIN_DP)).coerceAtLeast(dp(MIN_WINDOW_HEIGHT_DP)),
        )
        val x = preferences().getInt(KEY_X, (metrics.widthPixels - width) / 2)
            .coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        val y = preferences().getInt(KEY_Y, (metrics.heightPixels - height) / 4)
            .coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))

        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Deliberately do not set FLAG_NOT_FOCUSABLE: a focusable overlay
            // receives normal touch and keyboard input. NOT_TOUCH_MODAL lets
            // the underlying app receive taps outside this window.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun moveWindow(deltaX: Float, deltaY: Float) {
        updateLayout { params, metrics ->
            params.x = (params.x + deltaX.roundToInt())
                .coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
            params.y = (params.y + deltaY.roundToInt())
                .coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
        }
    }

    private fun resizeWindow(deltaX: Float, deltaY: Float) {
        updateLayout { params, metrics ->
            params.width = (params.width + deltaX.roundToInt()).coerceIn(
                dp(MIN_WINDOW_WIDTH_DP),
                (metrics.widthPixels - params.x).coerceAtLeast(dp(MIN_WINDOW_WIDTH_DP)),
            )
            params.height = (params.height + deltaY.roundToInt()).coerceIn(
                dp(MIN_WINDOW_HEIGHT_DP),
                (metrics.heightPixels - params.y).coerceAtLeast(dp(MIN_WINDOW_HEIGHT_DP)),
            )
        }
    }

    private fun updateLayout(update: (WindowManager.LayoutParams, DisplayMetrics) -> Unit) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        update(params, resources.displayMetrics)
        windowManager.updateViewLayout(view, params)
        preferences().edit()
            .putInt(KEY_WIDTH, params.width)
            .putInt(KEY_HEIGHT, params.height)
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    private fun restoreFullScreen() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
        )
        stopSelf()
    }

    private fun closeFloatingWindow() {
        FloatingWindowSession.current()?.activity?.moveTaskToBack(true)
        stopSelf()
    }

    private fun savedDimension(key: String, fallback: Int, min: Int, max: Int): Int {
        return preferences().getInt(key, fallback).coerceIn(min, max)
    }

    private fun preferences() = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PREFERENCES_NAME = "pockref_floating_window"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val MIN_WINDOW_WIDTH_DP = 180
        private const val MIN_WINDOW_HEIGHT_DP = 220
        private const val WINDOW_MARGIN_DP = 16
        private const val DEFAULT_WINDOW_WIDTH_FRACTION = 0.68f
        private const val DEFAULT_WINDOW_HEIGHT_FRACTION = 0.62f

        fun launch(activity: MainActivity, viewModel: PockRefViewModel) {
            FloatingWindowSession.attach(activity, viewModel)
            activity.startService(Intent(activity, FloatingWindowService::class.java))
        }
    }
}

private fun Modifier.floatingWindowHandleGesture(
    onTap: () -> Unit,
    onMove: (Float, Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var accumulatedDelta = Offset.Zero
        var dragging = false

        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            accumulatedDelta += delta
            if (!dragging && hypot(
                    accumulatedDelta.x.toDouble(),
                    accumulatedDelta.y.toDouble(),
                ) > viewConfiguration.touchSlop
            ) {
                dragging = true
            }
            if (dragging && delta != Offset.Zero) {
                change.consume()
                onMove(delta.x, delta.y)
            }
        } while (change.pressed)

        if (!dragging) onTap()
    }
}

@Composable
private fun FloatingWindowFrame(
    viewModel: PockRefViewModel,
    state: com.pockref.app.model.PockRefUiState,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onRestoreFullScreen: () -> Unit,
    onCloseFloatingWindow: () -> Unit,
) {
    var controlsExpanded by remember { mutableStateOf(false) }
    var controlsBounds by remember { mutableStateOf(Rect.Zero) }
    val windowShape = RoundedCornerShape(28.dp)
    val contentShape = RoundedCornerShape(25.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(windowShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = windowShape,
            )
            // Observe presses without consuming them. Canvas gestures retain
            // their original behavior while any press outside the control pill
            // returns its chrome to the compact One UI handle.
            .pointerInput(controlsExpanded, controlsBounds) {
                if (!controlsExpanded) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!controlsBounds.contains(down.position)) {
                        controlsExpanded = false
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(contentShape),
        ) {
            PockRefApp(
                state = state,
                onAction = viewModel::onAction,
                imageLazyLoader = viewModel.imageLazyLoader,
                imageDecoder = viewModel.imageDecoder,
                errorsFlow = viewModel.errors,
                onStartLogExport = viewModel::startLogExport,
                exportInProgressFlow = viewModel.logExportInProgress,
                showFloatingWindowButton = false,
            )
        }

        if (!controlsExpanded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 72.dp, height = 28.dp)
                    .floatingWindowHandleGesture(
                        onTap = { controlsExpanded = true },
                        onMove = onMove,
                    ),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsExpanded,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.CenterHorizontally),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.CenterHorizontally),
        ) {
            Surface(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    controlsBounds = coordinates.boundsInRoot()
                },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    IconButton(
                        modifier = Modifier.size(46.dp),
                        onClick = onRestoreFullScreen,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "恢复全屏",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    VerticalDivider(
                        modifier = Modifier.height(28.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                    IconButton(
                        modifier = Modifier.size(46.dp),
                        onClick = onCloseFloatingWindow,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭悬浮窗",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // The lower-right corner remains a resize hit area only, with no icon.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(32.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x, dragAmount.y)
                    }
                },
        )
    }
}
