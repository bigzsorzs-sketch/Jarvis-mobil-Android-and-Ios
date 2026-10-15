package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject

/** Only completed, explicitly submitted AI turns from the active chat belong here.
 * Local device/Gmail/glucose results and old unclassified database rows stay local. */
internal object JarvisConversationContext {
    const val MAX_TURNS = 24
    const val MAX_CHARACTERS = 64_000
    fun bounded(turns: List<Pair<String, String>>): List<Pair<String, String>> {
        val kept = mutableListOf<Pair<String, String>>()
        var size = 0
        for ((request, answer) in turns.asReversed()) {
            if (request.isBlank() || answer.isBlank()) continue
            val turn = request.take(8000) to answer.take(16_000)
            val length = turn.first.length + turn.second.length
            if (kept.size >= MAX_TURNS || size + length > MAX_CHARACTERS) break
            kept.add(turn)
            size += length
        }
        return kept.asReversed()
    }

    fun messages(turns: List<Pair<String, String>>): JSONArray = JSONArray().apply {
        bounded(turns).forEach { (request, answer) ->
            put(JSONObject().put("role", "user").put("content", request))
            put(JSONObject().put("role", "assistant").put("content", answer))
        }
    }
}
