package com.waqas028.kmpinspector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.presentation.bubble.InspectorBubble
import com.waqas028.kmpinspector.presentation.rememberInspectorState
import com.waqas028.kmpinspector.presentation.shell.InspectorShell
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * Wraps [content] and floats a draggable inspector bubble over it. Callers get the bubble and the
 * full inspector for free — they only wrap their root composable once:
 *
 * ```
 * setContent {
 *     KmpInspector {
 *         MyApp()
 *     }
 * }
 * ```
 *
 * Set [enabled] too false to keep the overlay out of the composition entirely — pass your own debug
 * flag so the inspector never reaches a release build.
 *
 * Feed it through [Inspector] and [InspectorLog].
 */
@Composable
fun KmpInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    var open by remember { mutableStateOf(false) }
    val state = rememberInspectorState()

    Box(Modifier.fillMaxSize()) {
        content()

        if (!open) {
            InspectorBubble(
                unreadCount = InspectorStore.unreadCount,
                hasCrash = InspectorStore.hasCrash,
                onClick = {
                    // Tapping clears the unread count and opens the inspector.
                    InspectorStore.markRead()
                    open = true
                },
            )
        } else {
            InspectorShell(state = state, onClose = { open = false })
        }
    }
}
