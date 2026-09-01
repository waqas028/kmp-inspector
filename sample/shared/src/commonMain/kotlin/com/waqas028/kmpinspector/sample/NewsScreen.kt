package com.waqas028.kmpinspector.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waqas028.kmpinspector.sample.data.NewsUiState

@Composable
internal fun NewsScreen(state: NewsUiState, onRefresh: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        // The sample owns its own insets; the inspector handles its own when open.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Spaceflight News", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = state.user?.let { "Signed in as ${it.displayName}" } ?: "Not signed in",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onRefresh, enabled = !state.loading) {
                    Text(if (state.loading) "Loading…" else "Refresh")
                }
            }

            // Says plainly when the network failed and the fixtures took over, so the inspector's
            // contents are never mistaken for real traffic.
            state.message?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            if (state.loading && state.articles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.articles, key = { it.id }) { article ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            article.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${article.newsSite} · ${formatDate(article.publishedAtMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (article.summary.isNotBlank()) {
                            Text(
                                article.summary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** `2026-09-01 12:04` in UTC. Enough to order a news list without a date library. */
private fun formatDate(millis: Long): String {
    val days = millis / 86_400_000L
    var year = 1970
    var remaining = days
    while (true) {
        val len = if (isLeapYear(year)) 366 else 365
        if (remaining < len) break
        remaining -= len
        year++
    }
    val lengths = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 0
    while (remaining >= lengths[month]) {
        remaining -= lengths[month]
        month++
    }
    val dayOfMonth = remaining + 1
    val minutesOfDay = (millis % 86_400_000L) / 60_000L
    return "$year-${pad(month + 1L)}-${pad(dayOfMonth)} ${pad(minutesOfDay / 60)}:${pad(minutesOfDay % 60)}"
}

private fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
private fun pad(v: Long) = v.toString().padStart(2, '0')
