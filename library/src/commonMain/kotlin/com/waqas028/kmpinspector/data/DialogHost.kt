package com.waqas028.kmpinspector.data

/**
 * One of the app's windows, as far as choosing where the bubble floats is concerned. Android
 * reads these off its window list; the choice itself is plain logic, kept here so it can be
 * unit-tested.
 */
internal data class WindowCandidate(
    /** A normal app dialog window: AlertDialog, DialogFragment, bottom sheets, Compose Dialog. */
    val isDialog: Boolean,
    /** Opened by the screen currently on show, not by another Activity. */
    val belongsToScreen: Boolean,
    val visible: Boolean,
    /** The inspector's own window, which must never become the bubble's host. */
    val isInspector: Boolean,
)

/**
 * Index of the dialog the bubble should float above, or -1 when the screen itself is on top.
 * [windows] are in the order the app added them, so the last match is the one on top: a country
 * picker opened over a phone-number dialog wins over that dialog.
 */
internal fun pickDialogHost(windows: List<WindowCandidate>): Int =
    windows.indexOfLast { it.isDialog && it.belongsToScreen && it.visible && !it.isInspector }
