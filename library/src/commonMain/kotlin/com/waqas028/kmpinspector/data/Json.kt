package com.waqas028.kmpinspector.data



/**
 * Just enough JSON to render a collapsible tree. A dependency-free parser keeps the published
 * artifact to Compose only; the viewer needs child counts and ordering, which this preserves.
 */
internal sealed interface JsonNode {
    data class Str(val value: String) : JsonNode
    data class Num(val raw: String) : JsonNode
    data class Bool(val value: Boolean) : JsonNode
    data object Null : JsonNode
    data class Arr(val items: List<JsonNode>) : JsonNode
    data class Obj(val entries: List<Pair<String, JsonNode>>) : JsonNode
}

internal fun parseJsonOrNull(text: String): JsonNode? = try {
    val parser = JsonParser(text)
    val node = parser.value()
    parser.skipWs()
    if (parser.atEnd()) node else null
} catch (_: Exception) {
    null
}

private class JsonParser(private val s: String) {
    private var i = 0

    fun atEnd() = i >= s.length

    fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    fun value(): JsonNode {
        skipWs()
        val c = s[i]
        return when {
            c == '{' -> obj()
            c == '[' -> arr()
            c == '"' -> JsonNode.Str(string())
            c == 't' -> { expect("true"); JsonNode.Bool(true) }
            c == 'f' -> { expect("false"); JsonNode.Bool(false) }
            c == 'n' -> { expect("null"); JsonNode.Null }
            c == '-' || c.isDigit() -> number()
            else -> error("unexpected char at $i")
        }
    }

    private fun expect(word: String) {
        require(s.startsWith(word, i)) { "expected $word at $i" }
        i += word.length
    }

    private fun obj(): JsonNode {
        i++
        val entries = mutableListOf<Pair<String, JsonNode>>()
        skipWs()
        if (s[i] == '}') {
            i++
            return JsonNode.Obj(entries)
        }
        while (true) {
            skipWs()
            val k = string()
            skipWs()
            require(s[i] == ':') { "expected : at $i" }
            i++
            entries += k to value()
            skipWs()
            when (s[i]) {
                ',' -> i++
                '}' -> { i++; return JsonNode.Obj(entries) }
                else -> error("bad object at $i")
            }
        }
    }

    private fun arr(): JsonNode {
        i++
        val items = mutableListOf<JsonNode>()
        skipWs()
        if (s[i] == ']') {
            i++
            return JsonNode.Arr(items)
        }
        while (true) {
            items += value()
            skipWs()
            when (s[i]) {
                ',' -> i++
                ']' -> { i++; return JsonNode.Arr(items) }
                else -> error("bad array at $i")
            }
        }
    }

    private fun string(): String {
        require(s[i] == '"') { "expected string at $i" }
        i++
        val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') {
                i++
                when (val e = s[i]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                        i += 4
                    }
                    else -> sb.append(e)
                }
            } else {
                sb.append(s[i])
            }
            i++
        }
        i++
        return sb.toString()
    }

    private fun number(): JsonNode {
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        return JsonNode.Num(s.substring(start, i))
    }
}

/** A collapsed branch keeps its child count - often all you needed. */
internal fun JsonNode.collapsedLabel(): String = when (this) {
    is JsonNode.Obj -> "{ … ${entries.size} ${if (entries.size == 1) "key" else "keys"} }"
    is JsonNode.Arr -> "[ … ${items.size} ${if (items.size == 1) "item" else "items"} ]"
    else -> ""
}

internal fun JsonNode.isBranch() = this is JsonNode.Obj || this is JsonNode.Arr
