package com.waqas028.kmpinspector.data

/**
 * Recognises an `application/x-www-form-urlencoded` body and splits it into pairs.
 *
 * Detected from the text rather than the Content-Type header, because plenty of apps post a form
 * body while declaring `application/json` — that mismatch is exactly the case where a readable
 * table helps most. Returns null when the text is not a form, so the caller can fall back to
 * showing it raw.
 */
internal fun parseFormEncodedOrNull(text: String): List<Pair<String, String>>? {
    val body = text.trim()
    if (body.isEmpty() || '=' !in body) return null
    // A real form body has no raw whitespace; spaces arrive as '+' or %20.
    if (body.any { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) return null

    val pairs = body.split('&').map { segment ->
        val eq = segment.indexOf('=')
        if (eq <= 0) return null
        val key = segment.substring(0, eq)
        if (!key.all { it.isLetterOrDigit() || it in "_-.[]%~" }) return null
        formUrlDecode(key) to formUrlDecode(segment.substring(eq + 1))
    }
    return pairs.ifEmpty { null }
}

/** Percent-decoding with `+` as a space, the encoding a form body uses. */
internal fun formUrlDecode(text: String): String {
    if ('%' !in text && '+' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '+' -> {
                out.append(' ')
                i++
            }

            c.isEscapeAt(text, i) -> {
                // Consume the whole run of %XX at once, so a multi-byte UTF-8 character decodes
                // as one character instead of several replacement marks.
                val bytes = ArrayList<Byte>()
                while (text[i].isEscapeAt(text, i)) {
                    bytes.add(text.substring(i + 1, i + 3).toInt(16).toByte())
                    i += 3
                    if (i >= text.length) break
                }
                out.append(bytes.toByteArray().decodeToString())
            }

            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

private fun Char.isEscapeAt(text: String, index: Int): Boolean =
    this == '%' && index + 3 <= text.length && text[index + 1].isHex() && text[index + 2].isHex()

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
