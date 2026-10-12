package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAiToolPolicyTest {
    @Test fun plainQuestionsMustHaveNoToolsOrToolChoice() {
        for (question in listOf(
            "Mennyit fogyaszt egy Ford Focus?",
            "Mit tudsz elmondani magadról?",
            "Magyarázd el a cukorbetegséget",
            "Hogyan működik a WhatsApp?",
            "Szia, mesélj a naptárakról!"
        )) {
            val body = JarvisAiToolPolicy.providerPayload(question)
            assertFalse(question, body.has("tools"))
            assertFalse(question, body.has("tool_choice"))
            assertEquals(JarvisAiGateway.TEST_MODEL, body.optString("model"))
            assertEquals(question,
                body.getJSONArray("messages").getJSONObject(1).getString("content"))
        }
    }

    @Test fun explicitAllowedCommandsGetOnlyOneRelevantTool() {
        val open = JarvisAiToolPolicy.providerPayload("Nyisd meg a naptárat")
        assertTrue(open.has("tools"))
        assertEquals(1, open.getJSONArray("tools").length())
        assertEquals("open_phone_app", open.getJSONArray("tools")
            .getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("auto", open.getString("tool_choice"))
        val call = JarvisAiToolPolicy.providerPayload("Hívd fel Esztert")
        assertEquals("prepare_phone_call", call.getJSONArray("tools")
            .getJSONObject(0).getJSONObject("function").getString("name"))
        val draft = JarvisAiToolPolicy.providerPayload(
            "Írj WhatsApp üzenetet Eszternek hogy ma később megyek")
        assertEquals("prepare_message_draft", draft.getJSONArray("tools")
            .getJSONObject(0).getJSONObject("function").getString("name"))
    }

    @Test fun noToolsForNegationsQuestionsOrUnsupportedActions() {
        for (input in listOf(
            "Ne nyisd meg a naptárat",
            "Meg tudod nyitni a térképet?",
            "Mi az a telefonhívás?",
            "Nyisd meg a bankszámlámat",
            "Indítsd az átutalást",
            ""
        )) {
            assertEquals(input, 0, JarvisAiToolPolicy.selectedTools(input).length())
        }
    }
}
