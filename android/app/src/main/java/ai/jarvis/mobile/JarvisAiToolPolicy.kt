package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ordinary questions MUST NOT request model tool-calling capability.
 * OpenRouter can have text endpoints even when it has no tools-capable endpoint.
 * Android still independently validates any returned tool proposal.
 */
internal object JarvisAiToolPolicy {
    fun selectedTools(original: String): JSONArray {
        val text = original.trim()
        val definitions = JSONArray()
        if (text.isBlank() || text.length > 8000) return definitions
        // A question or negated/multi-step request must not become an
        // imperative tool request solely because it mentions an app.
        if ('?' in text || text.matches(Regex("""(?iu)^(?:ne|nem|don't|dont|never)\b.*""")))
            return definitions

        val definition: JSONObject? = when {
            JarvisMessageIntent.parse(text) != null -> JarvisAiTools.messageToolDefinition()
            VoiceCalling.extractAiRequest(text) != null -> JarvisAiTools.callToolDefinition()
            JarvisNativeActions.identify(text) != null -> JarvisAiTools.nativeToolDefinition()
            JarvisPhoneActions.requestedAction(text) != null -> JarvisAiTools.phoneToolDefinition()
            JarvisAiTools.userAskedToOpen(text) &&
                JarvisAiTools.allowedRoutes.any { route ->
                    JarvisAiTools.validate(JarvisAiTools.FUNCTION_NAME, route, text).route == route
                } -> JarvisAiTools.toolDefinition()
            else -> null
        }
        if (definition != null) definitions.put(definition)
        return definitions
    }

    fun providerPayload(question: String, languageTag: String = "hu-HU"): JSONObject {
        val payload = JSONObject()
            .put("model", JarvisAiGateway.TEST_MODEL)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    JarvisIdentity.systemPrompt + if (languageTag.startsWith("en"))
                        " Respond in English by default, unless the user asks for another language."
                    else " Alapértelmezés szerint magyarul válaszolj, kivéve ha a felhasználó más nyelvet kér."))
                .put(JSONObject().put("role", "user").put("content", question)))
            .put("max_tokens", 800)
            .put("stream", false)
        val tools = selectedTools(question)
        if (tools.length() > 0) {
            payload.put("tools", tools)
            payload.put("tool_choice", "auto")
        }
        return payload
    }
}
