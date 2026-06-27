package net.notpumpkins.midnightcoreutils.api.config

class JsonConfigSerializer : ConfigSerializer {

    override fun serialize(data: Map<String, Map<String, Any?>>): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        data.entries.forEachIndexed { nsIdx, (namespace, values) ->
            sb.appendLine("  \"$namespace\": {")
            values.entries.forEachIndexed { idx, (key, value) ->
                sb.append("    \"$key\": ${toJsonValue(value)}")
                if (idx < values.size - 1) sb.append(",")
                sb.appendLine()
            }
            sb.append("  }")
            if (nsIdx < data.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("}")
        return sb.toString()
    }

    override fun deserialize(raw: String): Map<String, Map<String, Any?>> {
        val result = linkedMapOf<String, MutableMap<String, Any?>>()
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("Invalid JSON: missing outer braces")
        }

        var pos = 1
        while (pos < trimmed.length - 1) {
            skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
            if (trimmed[pos] == '}') break

            val namespace = parseString(trimmed, pos) ?: break
            pos = namespace.second
            skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
            if (trimmed[pos] != ':') break
            pos++
            skipWhitespace(trimmed, pos)?.let { pos = it } ?: break

            val nsValues = linkedMapOf<String, Any?>()
            if (trimmed[pos] == '{') {
                pos++
                while (pos < trimmed.length && trimmed[pos] != '}') {
                    skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
                    if (trimmed[pos] == '}') break

                    val key = parseString(trimmed, pos) ?: break
                    pos = key.second
                    skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
                    if (trimmed[pos] != ':') break
                    pos++
                    skipWhitespace(trimmed, pos)?.let { pos = it } ?: break

                    val value = parseValue(trimmed, pos) ?: break
                    pos = value.second
                    nsValues[key.first] = value.first

                    skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
                    if (trimmed[pos] == ',') pos++
                }
                if (pos < trimmed.length && trimmed[pos] == '}') pos++
            }

            result[namespace.first] = nsValues
            skipWhitespace(trimmed, pos)?.let { pos = it } ?: break
            if (pos < trimmed.length && trimmed[pos] == ',') pos++
        }

        return result
    }

    private fun toJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escapeJson(value)}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> "[${value.joinToString(", ") { toJsonValue(it) }}]"
            is Map<*, *> -> {
                "{" + value.entries.joinToString(", ") {
                    "\"${it.key}\": ${toJsonValue(it.value)}"
                } + "}"
            }
            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun skipWhitespace(s: String, start: Int): Int? {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return if (i < s.length) i else null
    }

    private fun parseString(s: String, start: Int): Pair<String, Int>? {
        if (start >= s.length || s[start] != '"') return null
        var i = start + 1
        val sb = StringBuilder()
        while (i < s.length) {
            when (s[i]) {
                '\\' -> {
                    i++
                    if (i < s.length) {
                        sb.append(
                            when (s[i]) {
                                '"' -> '"'; '\\' -> '\\'; '/' -> '/'
                                'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                                else -> s[i]
                            }
                        )
                    }
                }
                '"' -> return Pair(sb.toString(), i + 1)
                else -> sb.append(s[i])
            }
            i++
        }
        return null
    }

    private fun parseValue(s: String, start: Int): Pair<Any?, Int>? {
        if (start >= s.length) return null
        return when {
            s[start] == '"' -> {
                val result = parseString(s, start) ?: return null
                Pair(result.first as Any?, result.second)
            }
            s[start] == '{' -> {
                val end = findMatchingBrace(s, start, '{', '}') ?: return null
                val inner = s.substring(start, end + 1)
                Pair(deserialize(inner), end + 1)
            }
            s[start] == '[' -> {
                val end = findMatchingBrace(s, start, '[', ']') ?: return null
                val inner = s.substring(start + 1, end)
                val items = mutableListOf<Any?>()
                var p = 0
                while (p < inner.length) {
                    skipWhitespace(inner, p)?.let { p = it } ?: break
                    if (p >= inner.length) break
                    val value = parseValue(inner, p) ?: break
                    items.add(value.first)
                    p = value.second
                    skipWhitespace(inner, p)?.let { p = it } ?: break
                    if (p < inner.length && inner[p] == ',') p++
                }
                Pair(items, end + 1)
            }
            s.substring(start).startsWith("null") -> Pair(null, start + 4)
            s.substring(start).startsWith("true") -> Pair(true, start + 4)
            s.substring(start).startsWith("false") -> Pair(false, start + 5)
            else -> parseNumber(s, start)
        }
    }

    private fun parseNumber(s: String, start: Int): Pair<Number, Int>? {
        var i = start
        if (i < s.length && s[i] == '-') i++
        var isDouble = false
        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) {
            if (s[i] == '.' || s[i] == 'e' || s[i] == 'E') isDouble = true
            i++
        }
        if (i == start) return null
        if (i > start && (s[i - 1] == '+' || s[i - 1] == '-') && i - 1 == start) return null
        val raw = s.substring(start, i)
        if (raw == "-" || raw.isEmpty()) return null
        return try {
            if (isDouble) Pair(raw.toDouble(), i) else Pair(raw.toLong(), i)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun findMatchingBrace(s: String, start: Int, open: Char, close: Char): Int? {
        var depth = 1
        var i = start + 1
        var inString = false
        while (i < s.length && depth > 0) {
            if (inString) {
                if (s[i] == '\\') i++
                else if (s[i] == '"') inString = false
            } else {
                when (s[i]) {
                    '"' -> inString = true
                    open -> depth++
                    close -> depth--
                }
            }
            i++
        }
        return if (depth == 0) i - 1 else null
    }
}
