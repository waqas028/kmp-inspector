package com.waqas028.kmpinspector.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.StackFrame
import kotlinx.coroutines.launch

/**
 * Buttons that produce, on demand, each of the things the inspector is meant to show. Handy for
 * exercising a tab without waiting for the condition to occur naturally.
 */
@Composable
internal fun DeveloperScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Developer settings",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onBack) { Text("Done") }
            }
            HorizontalDivider()

            Section("Crashes & exceptions")

            Action(
                label = "Crash the app (fatal)",
                detail = "Throws an uncaught exception. The app will close immediately. " +
                    "Reopen it to see whether the crash survived — today it will not.",
            ) {
                throw IllegalStateException(
                    "Deliberate crash from developer settings: cart total was null after " +
                        "applying promotion PROMO_DENIM20",
                )
            }

            Action(
                label = "Record a fatal crash (without dying)",
                detail = "Writes a FATAL record straight to the inspector. The bubble turns red " +
                    "and pulses. Useful for checking the crash state without losing the app.",
            ) {
                Inspector.recordCrash(
                    CrashRecord(
                        id = nowMillis(),
                        fatal = true,
                        exceptionType = "IllegalStateException",
                        message = "Cart total was null after applying promotion PROMO_DENIM20.",
                        origin = "DeveloperScreen.kt",
                        causedBy = "Caused by: NumberFormatException: For input string: \"12,900\"",
                        timestampMillis = nowMillis(),
                        frames = listOf(
                            StackFrame("com.waqas028.kmpinspector.sample.DeveloperScreen.crash(DeveloperScreen.kt:62)", true),
                            StackFrame("androidx.compose.foundation.ClickableKt.invoke(Clickable.kt:154)", false),
                            StackFrame("kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)", false),
                        ),
                    ),
                )
            }

            Action(
                label = "Record a non-fatal exception",
                detail = "A handled exception, the kind you report yourself. Appears as CAUGHT.",
            ) {
                Inspector.recordNonFatal(
                    exceptionType = "JsonDecodingException",
                    message = "Unexpected null for field 'discount' at offset 214; defaulted to 0.",
                    origin = "ProductMapper.kt:41",
                    frames = listOf(
                        StackFrame("com.waqas028.kmpinspector.sample.ProductMapper.map(ProductMapper.kt:41)", true),
                        StackFrame("kotlinx.serialization.json.internal.decode(StreamingJsonDecoder.kt:96)", false),
                    ),
                )
            }

            Section("Network")

            Action(
                label = "Request that returns 404",
                detail = "Shows an error row with its status mark, and counts towards the Errors chip.",
            ) {
                scope.launch { SampleApp.api.probe("https://api.spaceflightnewsapi.net/v4/no-such-endpoint/") }
            }

            Action(
                label = "Request to a host that does not resolve",
                detail = "A transport failure: a row with ERR and no status code, plus a non-fatal.",
            ) {
                scope.launch { SampleApp.api.probe("https://this-host-does-not-exist.invalid/v4/articles/") }
            }

            Section("Logs")

            Action("Write one log at every level", "V, D, I, W and E, so the level filter has something to filter.") {
                InspectorLog.v("DevTools", "Verbose: recomposed DeveloperScreen")
                InspectorLog.d("DevTools", "Debug: cache lookup took 3ms")
                InspectorLog.i("DevTools", "Info: developer settings opened")
                InspectorLog.w("DevTools", "Warn: price formatting fell back to locale default")
                InspectorLog.e("DevTools", "Error: failed to parse price \"12,900\"")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Action(label: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}
