package com.waqas028.kmpinspector.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import com.waqas028.kmpinspector.KmpInspector

/**
 * Drops a [ComposeView] holding the inspector overlay into every Activity's content frame. That
 * is what lets a mixed XML + Compose app get the bubble everywhere without wrapping any screen.
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

        root.addView(
            ComposeView(activity).apply {
                tag = TAG
                setContent { KmpInspector {} }
            },
        )
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
