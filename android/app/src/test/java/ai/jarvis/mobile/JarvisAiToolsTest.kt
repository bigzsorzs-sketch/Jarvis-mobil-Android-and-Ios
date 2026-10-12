package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisAiToolsTest {
    @Test fun allowedRoutesAreOnlyRealMenuDestinations() {
        assertEquals(JarvisToolCatalog.all.map { it.route }.distinct(), JarvisAiTools.allowedRoutes)
        assertFalse(JarvisAiTools.allowedRoutes.contains("Fájl törlése"))
        assertTrue(JarvisAiTools.toolDefinition().toString().contains("additionalProperties"))
    }

    @Test fun nativeGuardNeedsOriginalUserRequest() {
        assertTrue(JarvisAiTools.userAskedToOpen("Jarvis, nyisd meg az ismerőseimet"))
        assertTrue(JarvisAiTools.userAskedToOpen("Show me the invoices page"))
        assertFalse(JarvisAiTools.userAskedToOpen("Mi az a számlázás?"))
        assertFalse(JarvisAiTools.userAskedToOpen("Ne nyisd meg az üzeneteket"))
        assertNull(JarvisAiTools.validate("delete_files", "Üzenetek", "Nyisd meg az üzeneteket").route)
        assertNull(JarvisAiTools.validate(JarvisAiTools.FUNCTION_NAME,
            "Android beállítások törlése", "Nyisd meg a beállításokat").route)
    }

    @Test fun providerValidToolCallBecomesAppRoute() {
        val call = JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", JarvisAiTools.FUNCTION_NAME)
                .put("arguments", "{\\\"route\\\":\\\"Ismerősök\\\"}"))
        val body = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("tool_calls", JSONArray().put(call)))))
        val answer = JarvisAiTools.fromProvider(body, "Nyisd meg az ismerősöket")
        assertEquals("Ismerősök", answer.route)
        assertNull(JarvisAiTools.fromProvider(body, "Mit tudsz az ismerősökről?").route)
    }

    @Test fun noMultiActionUnknownFunctionOrExtraParameters() {
        val good = JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", JarvisAiTools.FUNCTION_NAME)
                .put("arguments", "{\\\"route\\\":\\\"Naptár\\\"}"))
        val multiple = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("tool_calls", JSONArray().put(good).put(good)))))
        assertNull(JarvisAiTools.fromProvider(multiple, "Nyisd meg a naptárat").route)
        val malformed = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("type", "function")
                .put("function", JSONObject().put("name", JarvisAiTools.FUNCTION_NAME)
                    .put("arguments", "{\\\"route\\\":\\\"Naptár\\\",\\\"shell\\\":\\\"rm -rf /\\\"}")))))))
        assertNull(JarvisAiTools.fromProvider(malformed, "Nyisd meg a naptárat").route)
    }

    @Test fun plainTextAndInvalidServerActionDoNotExecute() {
        val text = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", "Nyisd meg a törlés menüt"))))
        val answer = JarvisAiTools.fromProvider(text, "Hogy vagy?")
        assertNull(answer.route)
        assertEquals("Nyisd meg a törlés menüt", answer.message)
        val action = JSONObject().put("action", JSONObject()
            .put("name", JarvisAiTools.FUNCTION_NAME).put("route", "Munka"))
        assertEquals("Munka", JarvisAiTools.fromServer(action, "Nyisd meg a Munka menüt").route)
        assertNull(JarvisAiTools.fromServer(action, "Mi az a Munka menü?").route)
        assertNull(JarvisAiTools.fromServer(JSONObject().put("action", "open_shell"),
            "Nyisd meg a Munka menüt").route)
    }
}
