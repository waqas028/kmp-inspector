package com.waqas028.kmpinspector.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.KmpInspector

@Composable
fun App() {
    // A real host app feeds Inspector from its own HTTP client, logger and scheduler; this seeds
    // the handoff's fixtures so every inspector section has something to show.
    LaunchedEffect(Unit) { seedDemoData(nowMillis()) }

    MaterialTheme {
        KmpInspector {
            SampleContent()
        }
    }
}

@Composable
private fun SampleContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = "KmpInspector Sample",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Tap the bubble to open the inspector. Drag it to move it out of the way.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            // Feeding the inspector at runtime: each tap bumps the bubble's unread badge.
            Button(onClick = { simulateActivity(nowMillis()) }) {
                Text("Simulate activity")
            }
        }
    }
}

/** One request and one log line, so the badge and the live tail can be seen updating. */
private fun simulateActivity(nowMillis: Long) {
    Inspector.recordRequest(
        method = "GET",
        url = "https://api.example.com/v2/products/8821",
        statusCode = 200,
        durationMillis = 118,
        requestBytes = 92,
        responseBytes = 2_140,
    )
    InspectorLog.i("Sample", "Simulated a product fetch at $nowMillis")
}
