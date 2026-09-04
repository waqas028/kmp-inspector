package com.waqas028.kmpinspector.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * The injected overlay finds the dispatcher through the view tree: ComponentActivity installs it
 * on the decor view, and the overlay's ComposeView is a descendant of that.
 */
@Composable
internal actual fun InspectorBackHandler(enabled: Boolean, onBack: () -> Unit) =
    BackHandler(enabled = enabled, onBack = onBack)
