package com.waqas028.kmpinspector

import androidx.compose.runtime.Composable

/** No-op: renders [content] with no overlay. */
@Composable
fun KmpInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = content()
