package ai.jarvis.mobile

import org.json.JSONObject

/**
 * OpenAI-compatible OpenRouter text SSE decoder. Not a device-action dispatcher:
 * model tokens only become text and can NEVER trigger Android operations.
 */
internal class JarvisSseDecoder {
    private val full = StringBuilder()
    var done: Boolean = false
        private set
    val text: String get() = full.toString()

    /**
     * @return visible delta or null for keep-alive, metadata or completion events.
     * Only the first candidate's string content is accepted.
     */
    fun accept(line: String): String? {
        if (!line.startsWith("data:") || done) return null
        val body = line.removePrefix("data:").trim()
        if (body == "[DONE]") {
            done = true
            return null
        }
        if (body.isEmpty() || body.length > 65_536) return null
        val event = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (event.has("error")) {
            done = true
            return null
        }
        val choice = event.optJSONArray("choices")?.optJSONObject(0) ?: return null
        val delta = choice.optJSONObject("delta")?.opt("content") as? String
        if (choice.optString("finish_reason") == "stop") done = true
        if (delta.isNullOrEmpty()) return null
        if (full.length + delta.length > 16_000) {
            done = true
            return null
        }
        full.append(delta)
        return delta
    }
}

/** Turns model tokens into speakable units, without waiting for the full response. */
internal class JarvisIncrementalPhrases {
    private val waiting = StringBuilder()
    fun accept(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        waiting.append(delta)
        val ready = mutableListOf<String>()
        while (waiting.length >= 75) {
            // Prefer a completed sentence; never split a word.
            val upper = minOf(waiting.length, 205)
            var cut = -1
            for (i in 70 until upper) {
                if (waiting[i] in ".!?。！？\n" &&
                    (i + 1 == waiting.length || waiting[i + 1].isWhitespace())) {
                    cut = i + 1
                    break
                }
            }
            if (cut < 0 && waiting.length >= 195) {
                cut = (195 downTo 85).firstOrNull { waiting[it].isWhitespace() } ?: 195
            }
            if (cut < 0) break
            val phrase = waiting.substring(0, cut).trim()
            waiting.delete(0, cut)
            if (phrase.isNotBlank()) ready.add(phrase)
        }
        return ready
    }

    fun finish(): String = waiting.toString().trim().also { waiting.clear() }
}
