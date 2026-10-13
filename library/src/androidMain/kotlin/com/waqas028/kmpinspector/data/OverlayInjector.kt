package com.waqas028.kmpinspector.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import com.waqas028.kmpinspector.KmpInspector

/**
 * Drops a [ComposeView] holding the inspector overlay into every Activity's content frame. That
 * is what lets a mixed XML + Compose app get the bubble everywhere without wrapping any screen.
 *
 * "Every" means every Activity that can host it. Activities from other libraries pass through
 * these callbacks too, and one that cannot hold a Compose surface is skipped rather than crashed.
 *
 * Injection happens on resume rather than create because Activities call `setContentView` after
 * `onCreate` starts, and a second `setContentView` (some base classes do this) replaces the
 * frame's children, which would silently remove the overlay. The tag check makes re-injection
 * idempotent.
 *
 * The overlay view is the size of the frame but, until the inspector is open, contains nothing that
 * handles touches, so Compose reports the events unhandled and they fall through to the app's own
 * views underneath.
 */
internal class OverlayInjector(
    private val excludeActivity: (Activity) -> Boolean,
) : Application.ActivityLifecycleCallbacks {

    fun install(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityResumed(activity: Activity) = inject(activity)

    private fun inject(activity: Activity) {
        if (excludeActivity(activity)) return
        // ComposeView needs view-tree owners; ComponentActivity installs them on the decor view,
        // which the overlay inherits through its parent chain. A plain Activity has none.
        if (activity !is LifecycleOwner) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.findViewWithTag<ComposeView>(TAG) != null) return
        // ComponentActivity only installs those owners inside setContentView. A screen that never
        // sets content has none, and a ComposeView attached there throws "ViewTreeLifecycleOwner
        // not found" and takes the host app down. Library trampolines are the usual case: Firebase
        // Auth's reCAPTCHA screen just hands off to the browser. There is nothing to float over on
        // such a screen anyway, so it goes without the bubble.
        if (root.findViewTreeLifecycleOwner() == null) return
        if (root.findViewTreeSavedStateRegistryOwner() == null) return

        val overlay = ComposeView(activity).apply {
            tag = TAG
            setContent { KmpInspector {} }
        }
        // A debugging aid must never be the reason the host app dies. If a screen we know nothing
        // about still rejects the overlay, that screen simply has no bubble.
        try {
            root.addView(overlay)
        } catch (_: RuntimeException) {
            runCatching { root.removeView(overlay) }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val TAG = "kmp_inspector_overlay"
    }
}
