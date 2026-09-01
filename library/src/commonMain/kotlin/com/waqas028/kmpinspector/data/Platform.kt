package com.waqas028.kmpinspector.data



/**
 * The few host facts the inspector needs. Kept as expect/actual rather than pulling in a date
 * library: formatting is done in common code from the offset below, so every platform renders
 * timestamps identically.
 */
internal expect object InspectorPlatform {
    /** e.g. "Android 14", "iOS 18.2", "Desktop (macOS)" — shown in the session line. */
    val name: String

    /** Background Work is a WorkManager concept; the tab is absent, not disabled, elsewhere. */
    val isAndroid: Boolean

    fun currentTimeMillis(): Long

    fun utcOffsetMillis(): Long
}

/** `12:04:31.882` in local time. */
internal fun formatClock(millis: Long, withMillis: Boolean = true): String {
    val local = millis + InspectorPlatform.utcOffsetMillis()
    val dayMillis = ((local % 86_400_000L) + 86_400_000L) % 86_400_000L
    val h = dayMillis / 3_600_000L
    val m = (dayMillis / 60_000L) % 60
    val s = (dayMillis / 1_000L) % 60
    val ms = dayMillis % 1_000L
    val base = "${pad(h)}:${pad(m)}:${pad(s)}"
    return if (withMillis) "$base.${ms.toString().padStart(3, '0')}" else base
}

/** `04:12` — elapsed capture time for the session line. */
internal fun formatElapsed(millis: Long): String {
    val total = millis / 1000
    return "${pad(total / 60)}:${pad(total % 60)}"
}

private fun pad(v: Long) = v.toString().padStart(2, '0')

/** `142 ms`, `1.2 s` — durations are compared at a glance, so the unit changes with magnitude. */
internal fun formatDuration(millis: Long): String =
    if (millis < 1000) "$millis ms" else "${(millis / 100).toDouble().let { it / 10 }} s"

/** `18.4 kB`, `612 B` — decimal units, matching what browsers and curl report. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1000 -> "$bytes B"
    bytes < 1_000_000 -> "${round1(bytes / 1000.0)} kB"
    else -> "${round1(bytes / 1_000_000.0)} MB"
}

private fun round1(v: Double): String {
    val r = (v * 10).toLong() / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}
