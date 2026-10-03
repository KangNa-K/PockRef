package com.pockref.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.OpenInFull
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pockref.app.ui.PockRefApp
import com.pockref.app.ui.PockRefViewModel
import com.pockref.app.ui.theme.PockRefTheme
import com.pockref.app.spen.SpenButtonDetector
import com.pockref.app.spen.SpenMotionEventBridge
import com.pockref.app.spen.SpenMode
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

/** Observes overlay-window input before forwarding it unchanged to Compose. */
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

/**
 * A non-focusable [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] host
 * for PockRef's existing Compose application. Unlike Android PiP, this window
 * receives normal touch input from the canvas and every existing control while
 * leaving hardware-key focus with the app underneath.
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayOwner: FloatingWindowLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var spenInputBridge: SpenMotionEventBridge? = null
    private var bubbleView: View? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) reattachWindow()
        }
    }
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, foregroundNotification())
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        showFloatingWindow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(unlockReceiver)
            receiverRegistered = false
        }
        spenInputBridge?.reset()
        spenInputBridge = null
        overlayView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        bubbleView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        overlayView = null
        bubbleView = null
        overlayOwner?.destroy()
        overlayOwner = null
        layoutParams = null
        bubbleLayoutParams = null
        FloatingWindowSession.clear()
        super.onDestroy()
    }

    private fun foregroundNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "悬浮参考图板",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮参考图板正在运行")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun reattachWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val view = overlayView ?: bubbleView ?: return
        val wasBubble = view === bubbleView
        val params = if (wasBubble) bubbleLayoutParams else layoutParams
        if (view.isAttachedToWindow && params != null) {
            try {
                windowManager.updateViewLayout(view, params)
                return
            } catch (error: RuntimeException) {
                Log.w(TAG, "Floating window is no longer registered; recreating it", error)
            }
        }
        runCatching { windowManager.removeViewImmediate(view) }
        if (wasBubble) {
            bubbleView = null
            bubbleLayoutParams = null
        } else {
            overlayView = null
            layoutParams = null
            overlayOwner?.destroy()
            overlayOwner = null
            spenInputBridge?.reset()
            spenInputBridge = null
        }
        try {
            if (wasBubble) showBubble() else showFloatingWindow()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to restore floating window after unlock", error)
            stopSelf()
        }
    }

    private fun showFloatingWindow() {
        if (overlayView != null) return
        if (bubbleView != null) removeBubble()
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
        val inputBridge = SpenMotionEventBridge(
            SpenButtonDetector(
                mode = host.spenMode,
                onEvent = host.onSpenEvent,
            ),
        )
        val view = SpenInputFrameLayout(host.activity) { event ->
            inputBridge.onButtonState(event.buttonState)
        }.apply {
            // The Activity supplies file pickers and back dispatch; this owner
            // keeps composition and gesture handling alive after it is hidden.
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
                                onMinimize = ::minimizeToBubble,
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
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        val topInset = statusBarInsetPx()
        val usableHeight = (metrics.heightPixels - topInset - dp(WINDOW_MARGIN_DP))
            .coerceAtLeast(dp(MIN_WINDOW_HEIGHT_DP))
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
            max = usableHeight,
        )
        val x = preferences().getInt(KEY_X, (metrics.widthPixels - width) / 2)
            .coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        val y = preferences().getInt(KEY_Y, topInset + (usableHeight - height) / 4)
            .coerceIn(topInset, (metrics.heightPixels - height).coerceAtLeast(topInset))

        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Keep the overlay touchable for its canvas and chrome, but do not
            // let it become the system input-focus window. Without
            // FLAG_NOT_FOCUSABLE, hardware keyboards and other key devices are
            // routed to this overlay; unhandled keys do not fall through to
            // the app underneath. NOT_TOUCH_MODAL still lets the underlying
            // app receive taps outside this window.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
            val topInset = statusBarInsetPx()
            params.x = (params.x + deltaX.roundToInt())
                .coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
            params.y = (params.y + deltaY.roundToInt())
                .coerceIn(topInset, (metrics.heightPixels - params.height).coerceAtLeast(topInset))
        }
    }

    private fun resizeWindow(deltaX: Float, deltaY: Float) {
        updateLayout { params, metrics ->
            val topInset = statusBarInsetPx()
            params.width = (params.width + deltaX.roundToInt()).coerceIn(
                dp(MIN_WINDOW_WIDTH_DP),
                (metrics.widthPixels - params.x).coerceAtLeast(dp(MIN_WINDOW_WIDTH_DP)),
            )
            params.height = (params.height + deltaY.roundToInt()).coerceIn(
                dp(MIN_WINDOW_HEIGHT_DP),
                (metrics.heightPixels - params.y - dp(WINDOW_MARGIN_DP))
                    .coerceAtLeast(dp(MIN_WINDOW_HEIGHT_DP)),
            )
            params.y = params.y.coerceAtLeast(topInset)
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
        removeBubble()
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

    private fun minimizeToBubble() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        preferences().edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
        windowManager.removeViewImmediate(view)
        overlayView = null
        overlayOwner?.destroy()
        overlayOwner = null
        spenInputBridge?.reset()
        spenInputBridge = null
        layoutParams = null
        showBubble()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val metrics = resources.displayMetrics
        val size = dp(BUBBLE_SIZE_DP)
        val topInset = statusBarInsetPx()
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences().getInt(
                KEY_BUBBLE_X,
                metrics.widthPixels - size - dp(WINDOW_MARGIN_DP),
            ).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
            y = preferences().getInt(KEY_BUBBLE_Y, topInset + dp(WINDOW_MARGIN_DP))
                .coerceIn(topInset, (metrics.heightPixels - size).coerceAtLeast(topInset))
        }
        val bubble = ImageView(this).apply {
            setImageDrawable(applicationInfo.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.app_name)
            setOnTouchListener(BubbleTouchListener(params))
        }
        bubbleView = bubble
        bubbleLayoutParams = params
        windowManager.addView(bubble, params)
    }

    private fun removeBubble() {
        bubbleView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        bubbleView = null
        bubbleLayoutParams = null
    }

    private fun restoreFromBubble() {
        removeBubble()
        showFloatingWindow()
    }

    private inner class BubbleTouchListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragged && hypot(dx.toDouble(), dy.toDouble()) > dp(6)) dragged = true
                    if (dragged) {
                        val metrics = resources.displayMetrics
                        val topInset = statusBarInsetPx()
                        params.x = (startX + dx.roundToInt())
                            .coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
                        params.y = (startY + dy.roundToInt())
                            .coerceIn(topInset, (metrics.heightPixels - params.height).coerceAtLeast(topInset))
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragged && event.actionMasked == MotionEvent.ACTION_UP) {
                        restoreFromBubble()
                    } else {
                        snapBubbleToNearestEdge(view)
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun snapBubbleToNearestEdge(view: View) {
        val params = bubbleLayoutParams ?: return
        val metrics = resources.displayMetrics
        params.x = if (params.x + params.width / 2 < metrics.widthPixels / 2) {
            0
        } else {
            (metrics.widthPixels - params.width).coerceAtLeast(0)
        }
        val topInset = statusBarInsetPx()
        params.y = params.y.coerceIn(topInset, (metrics.heightPixels - params.height).coerceAtLeast(topInset))
        windowManager.updateViewLayout(view, params)
        preferences().edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
    }

    private fun statusBarInsetPx(): Int {
        val metricsInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.statusBars()).top
        } else {
            0
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val resourceInset = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
        return maxOf(metricsInset, resourceInset)
    }

    private fun savedDimension(key: String, fallback: Int, min: Int, max: Int): Int {
        return preferences().getInt(key, fallback).coerceIn(min, max)
    }

    private fun preferences() = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val NOTIFICATION_CHANNEL_ID = "floating_window"
        private const val NOTIFICATION_ID = 1001
        private const val PREFERENCES_NAME = "pockref_floating_window"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val BUBBLE_SIZE_DP = 56
        private const val MIN_WINDOW_WIDTH_DP = 180
        private const val MIN_WINDOW_HEIGHT_DP = 220
        private const val WINDOW_MARGIN_DP = 16
        private const val DEFAULT_WINDOW_WIDTH_FRACTION = 0.68f
        private const val DEFAULT_WINDOW_HEIGHT_FRACTION = 0.62f

        fun launch(
            activity: MainActivity,
            viewModel: PockRefViewModel,
            spenMode: SpenMode,
            onSpenEvent: (SpenButtonDetector.ButtonEvent) -> Unit,
        ) {
            FloatingWindowSession.attach(activity, viewModel, spenMode, onSpenEvent)
            ContextCompat.startForegroundService(
                activity,
                Intent(activity, FloatingWindowService::class.java),
            )
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
    onMinimize: () -> Unit,
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
                shadowElevation = 1.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    IconButton(
                        modifier = Modifier.size(46.dp),
                        onClick = onMinimize,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Minimize,
                            contentDescription = "最小化悬浮窗",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(46.dp),
                        onClick = onRestoreFullScreen,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "恢复全屏",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
