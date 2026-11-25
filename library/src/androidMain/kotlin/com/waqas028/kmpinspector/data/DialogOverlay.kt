package com.waqas028.kmpinspector.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.presentation.bubble.BUBBLE_SIZE
import com.waqas028.kmpinspector.presentation.bubble.BubbleFace
import com.waqas028.kmpinspector.presentation.bubble.clampBubble
import com.waqas028.kmpinspector.presentation.bubble.defaultBubblePosition
import com.waqas028.kmpinspector.presentation.bubble.snapBubble
import com.waqas028.kmpinspector.presentation.rememberInspectorState
import com.waqas028.kmpinspector.presentation.shell.InspectorShell
import com.waqas028.kmpinspector.presentation.theme.ProvideInspectorFonts
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Keeps the bubble reachable while the app shows a dialog.
 *
 * Android draws every dialog (AlertDialog, DialogFragment, bottom sheets, Compose `Dialog` and
 * `ModalBottomSheet`) in a window of its own, above the screen's window. The bubble normally lives
 * in the screen's window, so a dialog covers it and the inspector cannot be opened until the
 * dialog is closed. While a dialog is on top, a copy of the bubble is shown in a small window
 * attached to that dialog, which Android always stacks above it. Tapping that copy opens the
 * inspector in a full-screen dialog of its own, above the app's dialog; closing it returns to the
 * app's dialog exactly as it was. The app's screen is never paused, so things like an OTP
 * countdown keep running.
 *
 * Performance: the window list is read only when focus moves between the screen and a dialog,
 * which is exactly when a dialog opens or closes. Nothing polls.
 *
 * Android 10+ only: older versions have no public way to list an app's windows, so there the
 * bubble stays under dialogs as before. Every step is guarded: if anything here fails, the
 * normal bubble is left as it was, and the reason goes to the Logs panel under "Inspector".
 */
internal object DialogOverlay {

    private const val LOG_TAG = "Inspector"

    /**
     * Identity of the Activity whose bubble is currently floating above one of its dialogs, or
     * null. That screen's own overlay draws nothing meanwhile, so there is never a second bubble
     * or a second inspector composing underneath.
     */
    var hostScreenId by mutableStateOf<Int?>(null)
        private set

    /** Weak both ways: a screen's controller lives exactly as long as that screen's views. */
    private val controllers = WeakHashMap<Activity, WeakReference<Controller>>()

    fun watch(activity: Activity, contentRoot: ViewGroup) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (controllers[activity]?.get() != null) return
        safely("watch") {
            val controller = Controller(activity, contentRoot)
            controllers[activity] = WeakReference(controller)
            controller.start()
        }
    }

    fun release(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        controllers.remove(activity)?.get()?.let { safely("release") { it.release() } }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        runCatching(block).onFailure { InspectorLog.w(LOG_TAG, "Bubble over dialog: $what failed: $it") }
    }

    /**
     * One per screen. Referenced only from that screen's own view tree (the focus listeners) and
     * weakly from [controllers], so nothing static keeps a screen alive.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private class Controller(private val activity: Activity, private val contentRoot: ViewGroup) {

        private val decor: View = activity.window.decorView
        private val screenId = System.identityHashCode(activity)

        /** The dialog the bubble floats above, and the bubble window itself. */
        private var host: View? = null
        private var bubble: BubbleWindow? = null
        private var inspector: InspectorDialog? = null

        /** Dialog windows already given focus and detach listeners. */
        private val watched: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
        private val onFocusChange = ViewTreeObserver.OnWindowFocusChangeListener { scheduleRescan() }
        private var rescanPosted = false

        fun start() {
            decor.viewTreeObserver.addOnWindowFocusChangeListener(onFocusChange)
            // A dialog can already be up before the screen ever gets focus, e.g. an OTP sheet
            // restored after a rotation, so look once now as well.
            scheduleRescan()
        }

        /** Posted, so the window that just took focus is fully added before the list is read. */
        private fun scheduleRescan() {
            if (rescanPosted) return
            rescanPosted = true
            decor.post {
                rescanPosted = false
                safely("rescan") { rescan() }
            }
        }

        private fun rescan() {
            if (activity.isFinishing || activity.isDestroyed) return release()
            val roots = WindowList.all()
            val appToken = activity.window.attributes.token
            val inspectorRoot = inspector?.window?.peekDecorView()
            val candidates = roots.map { root ->
                val params = root.layoutParams as? WindowManager.LayoutParams
                WindowCandidate(
                    isDialog = params?.type == WindowManager.LayoutParams.TYPE_APPLICATION,
                    belongsToScreen = params != null &&
                        ((appToken != null && params.token == appToken) || root.context.findActivity() === activity),
                    visible = root.isAttachedToWindow && root.visibility == View.VISIBLE,
                    isInspector = root === inspectorRoot,
                )
            }
            val target = roots.getOrNull(pickDialogHost(candidates))

            if (target == null) {
                if (host != null) {
                    InspectorLog.d(LOG_TAG, "Dialog closed: bubble back on the screen")
                } else if (!decor.hasWindowFocus() && candidates.any { it.isDialog }) {
                    // Something took focus but did not qualify: say why, so one phone run explains it.
                    InspectorLog.d(LOG_TAG, "No dialog to float above among ${describe(candidates)}")
                }
                removeBubble()
                updateHost()
                return
            }

            watchDialog(target)
            if (target === host && bubble?.isShowing == true) return
            removeBubble()
            val window = BubbleWindow(activity, contentRoot, target, onTap = ::openInspector)
            if (window.show()) {
                host = target
                bubble = window
                InspectorLog.d(LOG_TAG, "Dialog on top: bubble floating above it")
                // An inspector open on the screen is covered by the dialog now; the bubble above
                // the dialog is the way back in.
                if (inspector == null) InspectorStore.inspectorOpen = false
            }
            updateHost()
        }

        /** Focus moves between stacked dialogs too, and a closing dialog takes its bubble with it. */
        private fun watchDialog(root: View) {
            if (!watched.add(root)) return
            root.viewTreeObserver.addOnWindowFocusChangeListener(onFocusChange)
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    if (v === host) {
                        removeBubble()
                        updateHost()
                    }
                    scheduleRescan()
                }
            })
        }

        private fun openInspector() {
            if (inspector != null) return
            safely("open") {
                val dialog = InspectorDialog(activity)
                dialog.setOnDismissListener {
                    inspector = null
                    InspectorStore.inspectorOpen = false
                    updateHost()
                    scheduleRescan()
                }
                dialog.show()
                inspector = dialog
                InspectorStore.openInspector()
                updateHost()
            }
        }

        private fun removeBubble() {
            bubble?.remove()
            bubble = null
            host = null
        }

        private fun updateHost() {
            val hosting = bubble != null || inspector != null
            if (hosting) {
                hostScreenId = screenId
            } else if (hostScreenId == screenId) {
                hostScreenId = null
            }
        }

        fun release() {
            removeBubble()
            // Dismissing runs the listener above, which clears inspectorOpen.
            inspector?.let { runCatching { it.dismiss() } }
            inspector = null
            runCatching { decor.viewTreeObserver.removeOnWindowFocusChangeListener(onFocusChange) }
            updateHost()
        }

        private fun describe(candidates: List<WindowCandidate>) = candidates.joinToString(prefix = "[", postfix = "]") {
            buildString {
                append(if (it.isDialog) "dialog" else "other")
                if (!it.belongsToScreen) append(" of another screen")
                if (!it.visible) append(" hidden")
                if (it.isInspector) append(" (inspector)")
            }
        }
    }

    /** Kept apart so the Android 10 API is only touched behind the version check in [watch]. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private object WindowList {
        fun all(): List<View> = WindowInspector.getGlobalWindowViews()
    }

    /**
     * The bubble above a dialog: a small window the size of the bubble, attached to the dialog's
     * window. Attached windows always stack above their parent, and Android removes them with it.
     *
     * Not focusable, so the dialog keeps the keyboard; touch-modal off, so every touch outside the
     * bubble still reaches the dialog. It is dragged by moving the window itself, with raw screen
     * coordinates: Compose's drag deltas are relative to the window, which moves under the finger.
     */
    private class BubbleWindow(
        private val activity: Activity,
        private val contentRoot: View,
        private val parent: View,
        private val onTap: () -> Unit,
    ) {
        private val windowManager = activity.windowManager
        private val density = activity.resources.displayMetrics.density
        private val bubblePx = BUBBLE_SIZE.value * density

        /** Room around the circle for the unread badge and the crash halo, which overhang it. */
        private val padPx = (PAD_DP * density).roundToInt()

        private var pressed by mutableStateOf(false)
        private var onRight by mutableStateOf(InspectorStore.bubbleOnRight)

        /** Where the screen's content area sits on screen, and its size: the space positions live in. */
        private val origin = IntArray(2).also { contentRoot.getLocationOnScreen(it) }
        private val maxX = contentRoot.width.toFloat()
        private val maxY = contentRoot.height.toFloat()

        /**
         * Screen position minus window position. Attached windows are placed relative to their
         * parent, so this starts as the dialog's own screen position and is then corrected once
         * from where the window really landed, rather than trusting either assumption.
         */
        private var biasX = 0
        private var biasY = 0

        private val view = TouchFrame(activity)
        private val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            token = parent.windowToken
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            title = "KmpInspector"
        }

        val isShowing: Boolean get() = view.isAttachedToWindow

        /** False when the dialog or the system refused the window; the screen's bubble then stays. */
        fun show(): Boolean = runCatching {
            if (parent.windowToken == null || maxX <= 0f || maxY <= 0f) return false
            // A view added straight to the window manager has no parent to inherit these from;
            // Compose needs both on the root it is handed, as its own Popup sets them.
            view.setViewTreeLifecycleOwner(contentRoot.findViewTreeLifecycleOwner() ?: return false)
            view.setViewTreeSavedStateRegistryOwner(contentRoot.findViewTreeSavedStateRegistryOwner() ?: return false)
            view.addView(
                ComposeView(activity).apply {
                    setContent {
                        ProvideInspectorFonts()
                        Box(Modifier.padding(PAD_DP.dp)) {
                            BubbleFace(
                                unreadCount = InspectorStore.unreadCount,
                                hasCrash = InspectorStore.hasCrash,
                                onRight = onRight,
                                active = pressed,
                            )
                        }
                    }
                },
            )

            val parentOnScreen = IntArray(2).also { parent.getLocationOnScreen(it) }
            biasX = -parentOnScreen[0]
            biasY = -parentOnScreen[1]
            val start = InspectorStore.bubblePosition?.let { clampBubble(it, bubblePx, maxX, maxY) }
                ?: defaultBubblePosition(bubblePx, maxX, maxY)
            applyPosition(start)
            windowManager.addView(view, params)
            calibrateOnceLaidOut(start)
            true
        }.getOrElse {
            InspectorLog.w("Inspector", "Bubble over dialog: could not show: $it")
            runCatching { if (view.isAttachedToWindow) windowManager.removeViewImmediate(view) }
            false
        }

        fun remove() {
            runCatching { if (view.isAttachedToWindow) windowManager.removeViewImmediate(view) }
        }

        /** [position] is the circle's top-left in the screen's content area, as stored for the bubble. */
        private fun applyPosition(position: Offset) {
            params.x = (origin[0] + position.x).roundToInt() - padPx + biasX
            params.y = (origin[1] + position.y).roundToInt() - padPx + biasY
        }

        private fun currentPosition(): Offset = Offset(
            (params.x - biasX + padPx - origin[0]).toFloat(),
            (params.y - biasY + padPx - origin[1]).toFloat(),
        )

        private fun moveTo(position: Offset) {
            applyPosition(position)
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        private fun calibrateOnceLaidOut(intended: Offset) {
            view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val actual = IntArray(2).also { view.getLocationOnScreen(it) }
                    val errorX = actual[0] + padPx - (origin[0] + intended.x).roundToInt()
                    val errorY = actual[1] + padPx - (origin[1] + intended.y).roundToInt()
                    if (errorX != 0 || errorY != 0) {
                        biasX -= errorX
                        biasY -= errorY
                        moveTo(intended)
                    }
                }
            })
        }

        private fun snap() {
            val (snapped, toRight) = snapBubble(currentPosition(), bubblePx, maxX, maxY)
            onRight = toRight
            moveTo(snapped)
            // Shared with the screen's bubble, which picks up here once the dialog closes.
            InspectorStore.bubblePosition = snapped
            InspectorStore.bubbleOnRight = toRight
        }

        @SuppressLint("ViewConstructor")
        private inner class TouchFrame(context: Context) : FrameLayout(context) {
            private val slop = ViewConfiguration.get(context).scaledTouchSlop
            private var downX = 0f
            private var downY = 0f
            private var start = Offset.Zero
            private var dragging = false

            /** The Compose content only draws; every touch is handled here. */
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        start = currentPosition()
                        dragging = false
                        pressed = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        if (!dragging && dx * dx + dy * dy > slop * slop) dragging = true
                        if (dragging) moveTo(clampBubble(start + Offset(dx, dy), bubblePx, maxX, maxY))
                    }
                    MotionEvent.ACTION_UP -> {
                        pressed = false
                        if (dragging) snap() else performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        if (dragging) snap()
                    }
                }
                return true
            }

            override fun performClick(): Boolean {
                super.performClick()
                onTap()
                return true
            }
        }

        private companion object {
            const val PAD_DP = 14
        }
    }

    /**
     * The inspector above an app dialog: a full-screen dialog, so it stacks above the app's own
     * and takes the keyboard for search. Back and the close button dismiss it, which leaves the
     * app's dialog exactly as it was.
     */
    private class InspectorDialog(activity: Activity) :
        ComponentDialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar) {
        init {
            setContentView(
                ComposeView(context).apply {
                    setContent {
                        ProvideInspectorFonts()
                        InspectorShell(state = rememberInspectorState(), onClose = { dismiss() })
                    }
                },
            )
            window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // The shell pads itself for the system bars, like it does on the screen.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
