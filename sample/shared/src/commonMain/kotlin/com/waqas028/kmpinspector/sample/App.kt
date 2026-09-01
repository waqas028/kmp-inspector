package com.waqas028.kmpinspector.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waqas028.kmpinspector.KmpInspector
import com.waqas028.kmpinspector.firstElement
import com.waqas028.kmpinspector.generateFibi
import com.waqas028.kmpinspector.secondElement

@Composable
fun App() {
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

            var report by remember { mutableStateOf<String?>(null) }

            Button(onClick = { report = inspect() }) {
                Text("Debug KMP Inspector")
            }

            report?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Calls into the library. [firstElement] and [secondElement] are `expect val`s with a different
 * `actual` per platform, so the output differs on Android, iOS and desktop — which is exactly what
 * this sample is here to prove.
 */
private fun inspect(): String = buildString {
    appendLine("firstElement  = $firstElement")
    appendLine("secondElement = $secondElement")
    append("generateFibi()  = ${generateFibi().take(8).joinToString()}")
}
