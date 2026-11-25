package com.waqas028.kmpinspector.presentation.bubble

import androidx.compose.ui.geometry.Offset
import com.waqas028.kmpinspector.data.WindowCandidate
import com.waqas028.kmpinspector.data.pickDialogHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubblePlacementTest {

    private val bubble = 100f
    private val maxX = 1000f
    private val maxY = 2000f

    @Test
    fun a_drag_past_the_edge_stops_at_the_edge() {
        assertEquals(Offset(0f, 0f), clampBubble(Offset(-50f, -50f), bubble, maxX, maxY))
        assertEquals(Offset(900f, 1900f), clampBubble(Offset(5000f, 5000f), bubble, maxX, maxY))
    }

    @Test
    fun release_snaps_to_the_nearer_side_and_keeps_the_height() {
        val (left, onRight) = snapBubble(Offset(300f, 700f), bubble, maxX, maxY)
        assertEquals(Offset(0f, 700f), left)
        assertFalse(onRight)

        val (right, toRight) = snapBubble(Offset(600f, 700f), bubble, maxX, maxY)
        assertEquals(Offset(900f, 700f), right)
        assertTrue(toRight)
    }

    @Test
    fun the_first_position_is_the_right_edge_below_the_middle() {
        assertEquals(Offset(900f, 1240f), defaultBubblePosition(bubble, maxX, maxY))
    }

    // ---- Which window the bubble floats above ------------------------------------------------

    private val screen = WindowCandidate(isDialog = false, belongsToScreen = true, visible = true, isInspector = false)
    private val dialog = WindowCandidate(isDialog = true, belongsToScreen = true, visible = true, isInspector = false)

    @Test
    fun no_dialog_means_the_screen_keeps_the_bubble() {
        assertEquals(-1, pickDialogHost(listOf(screen)))
    }

    @Test
    fun the_newest_dialog_wins_when_two_are_stacked() {
        // A country picker opened over the phone-number dialog.
        assertEquals(2, pickDialogHost(listOf(screen, dialog, dialog)))
    }

    @Test
    fun the_inspector_and_other_screens_dialogs_are_never_hosts() {
        val inspector = dialog.copy(isInspector = true)
        val otherScreens = dialog.copy(belongsToScreen = false)
        val hidden = dialog.copy(visible = false)

        assertEquals(1, pickDialogHost(listOf(screen, dialog, inspector, otherScreens, hidden)))
    }
}
