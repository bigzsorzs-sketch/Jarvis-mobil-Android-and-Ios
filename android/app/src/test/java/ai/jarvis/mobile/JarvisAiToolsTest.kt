package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisAiToolsTest {
    private fun providerWith(vararg calls: JSONObject): JSONObject {
        val result = JSONArray()
        calls.forEach { result.put(it) }
        val message = JSONObject().put("tool_calls", result)
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message)))
    }

    private fun toolCall(name: String, params: JSONObject): JSONObject =
        JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name)
                .put("arguments", params.toString()))

    @Test fun routesAreRealMenuDestinationsOnly() {
        assertEquals(JarvisToolCatalog.all.map { it.route }.distinct(), JarvisAiTools.allowedRoutes)
        assertFalse(JarvisAiTools.allowedRoutes.contains("Delete files"))
        assertTrue(JarvisAiTools.toolDefinition().toString().contains("additionalProperties"))
    }

    @Test fun userIntentGateIsIndependentOfAiInstructions() {
        assertTrue(JarvisAiTools.userAskedToOpen("Jarvis, nyisd meg az ismerőseimet"))
        assertTrue(JarvisAiTools.userAskedToOpen("Show me the invoices page"))
        assertFalse(JarvisAiTools.userAskedToOpen("Mi az a számlázás?"))
        assertFalse(JarvisAiTools.userAskedToOpen("Ne nyisd meg az üzeneteket"))
        assertNull(JarvisAiTools.validate("delete_files", "Üzenetek",
            "Nyisd meg az üzeneteket").route)
        assertNull(JarvisAiTools.validate(JarvisAiTools.FUNCTION_NAME, "Delete files",
            "Nyisd meg a fájlokat").route)
    }

    @Test fun oneValidProviderToolCallProposesNavigation() {
        val body = providerWith(toolCall(JarvisAiTools.FUNCTION_NAME,
            JSONObject().put("route", "Ismerősök")))
        assertEquals("Ismerősök", JarvisAiTools.fromProvider(
            body, "Nyisd meg az ismerősöket").route)
        assertNull(JarvisAiTools.fromProvider(body, "Mit tudsz az ismerősökről?").route)
    }

    @Test fun multipleCallsExtraParametersAndWrongTypesAreRejected() {
        val valid = toolCall(JarvisAiTools.FUNCTION_NAME, JSONObject().put("route", "Naptár"))
        assertNull(JarvisAiTools.fromProvider(providerWith(valid, valid),
            "Nyisd meg a naptárat").route)
        assertNull(JarvisAiTools.fromProvider(providerWith(toolCall(
            JarvisAiTools.FUNCTION_NAME,
            JSONObject().put("route", "Naptár").put("shell", "shutdown"))),
            "Nyisd meg a naptárat").route)
        assertNull(JarvisAiTools.fromProvider(providerWith(toolCall(
            JarvisAiTools.FUNCTION_NAME, JSONObject().put("route", 42))),
            "Nyisd meg a naptárat").route)
    }

    @Test fun aiCannotOpenWrongYetExistingJarvisPage() {
        val wrong = providerWith(toolCall(JarvisAiTools.FUNCTION_NAME,
            JSONObject().put("route", "Email")))
        assertNull(JarvisAiTools.fromProvider(wrong, "Nyisd meg a térképet").route)
        assertNull(JarvisAiTools.fromProvider(wrong, "Nyisd meg az emailt és a naptárat").route)
        val correct = providerWith(toolCall(JarvisAiTools.FUNCTION_NAME,
            JSONObject().put("route", "Ismerősök")))
        assertEquals("Ismerősök", JarvisAiTools.fromProvider(correct,
            "Nyisd meg az ismerősöket").route)
    }

    @Test fun ordinaryTextNeverBecomesAnExecutableToolCall() {
        val text = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", "Nyisd meg a törlés menüt"))))
        val result = JarvisAiTools.fromProvider(text, "Hogy vagy?")
        assertNull(result.route)
        assertEquals("Nyisd meg a törlés menüt", result.message)
    }

    @Test fun serverResponsesAlsoRequireValidation() {
        val data = JSONObject().put("action", JSONObject().put("name", JarvisAiTools.FUNCTION_NAME)
            .put("route", "Munka"))
        assertEquals("Munka", JarvisAiTools.fromServer(data, "Nyisd meg a Munka oldalt").route)
        assertNull(JarvisAiTools.fromServer(data, "Mi az a Munka?").route)
        assertNull(JarvisAiTools.fromServer(JSONObject().put("action", "run shell"),
            "Nyisd meg a Munka oldalt").route)
    }
    @Test fun aiCanOnlyPrepareOneMatchingCallAndNeverExecutesIt() {
        val tool = toolCall(JarvisAiTools.CALL_FUNCTION, JSONObject().put("target", "Eszter"))
        val accepted = JarvisAiTools.fromProvider(providerWith(tool), "Fel tudnád hívni Esztert?")
        assertEquals("Esztert", accepted.callTarget)
        assertNull(accepted.route)
        assertNull(accepted.deviceAction)
        assertNull(JarvisAiTools.fromProvider(providerWith(tool), "Mit tudsz Eszterről?").callTarget)
        assertNull(JarvisAiTools.fromProvider(providerWith(tool), "Ne hívd fel Esztert").callTarget)
        assertNull(JarvisAiTools.fromProvider(providerWith(tool,tool), "Hívd fel Esztert").callTarget)
        assertNull(JarvisAiTools.fromProvider(providerWith(toolCall(JarvisAiTools.CALL_FUNCTION,
            JSONObject().put("target", "Mari"))), "Hívd fel Esztert").callTarget)
        assertNull(JarvisAiTools.fromProvider(providerWith(toolCall(JarvisAiTools.CALL_FUNCTION,
            JSONObject().put("target", "Eszter").put("execute", true))),
            "Hívd fel Esztert").callTarget)
    }

    @Test fun serverCallProposalRequiresExactUserRequest() {
        val proposal = JSONObject().put("action", JSONObject()
            .put("name", JarvisAiTools.CALL_FUNCTION).put("target", "+441234567890"))
        assertEquals("+441234567890", JarvisAiTools.fromServer(proposal,
            "Call +441234567890").callTarget)
        assertNull(JarvisAiTools.fromServer(proposal, "Call +441234567899").callTarget)
        assertNull(JarvisAiTools.fromServer(JSONObject().put("action", JSONObject()
            .put("name", JarvisAiTools.CALL_FUNCTION).put("route", "dialer")),
            "Call +441234567890").callTarget)
    }

}
