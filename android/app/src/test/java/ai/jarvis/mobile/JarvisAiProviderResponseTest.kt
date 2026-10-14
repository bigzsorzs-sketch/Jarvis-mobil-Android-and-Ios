package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisAiProviderResponseTest {
    @Test fun malformedSuccessIsNotMistakenForAnAiAnswer() {
        val outcome = JarvisAiTools.fromProvider(JSONObject().put("error",
            JSONObject().put("code", "provider_error")), "Hogy vagy?")
        assertTrue(outcome.message.contains("hibát küldött"))
        assertNull(outcome.route)
    }

    @Test fun typedTextPartsAreCombinedWithoutExecutingTools() {
        val blocks = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Első mondat."))
            .put(JSONObject().put("type", "text").put("text", "Második mondat."))
        val response = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("content", blocks))))
        val outcome = JarvisAiTools.fromProvider(response, "Mesélj valamit!")
        assertEquals("Első mondat.\nMásodik mondat.", outcome.message)
        assertNull(outcome.route)
    }
}
