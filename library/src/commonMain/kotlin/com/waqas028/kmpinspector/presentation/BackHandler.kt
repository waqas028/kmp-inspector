package com.waqas028.kmpinspector.presentation

import androidx.compose.runtime.Composable

/**
 * Runs [onBack] when the platform's back gesture fires while [enabled]. Android hooks the
 * Activity's back dispatcher; iOS and desktop have no system back, so they do nothing.
 */
@Composable
internal expect fun InspectorBackHandler(enabled: Boolean, onBack: () -> Unit)
