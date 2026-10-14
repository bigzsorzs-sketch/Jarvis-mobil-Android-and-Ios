package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAiToolPolicyTest {
    @Test fun plainQuestionsNeverRequireExtraProviderTools() {
        for (question in listOf(
            "Mennyit fogyaszt egy Ford Focus?",
            "Mit tudsz elmondani magadról?",
            "Magyarázd el a cukorbetegséget",
            "Hogyan működik a WhatsApp?",
            "Szia, mesélj a naptárakról!",
            "Válaszolj röviden magyarul: működik a Jarvis AI-kapcsolat?"
        )) {
            val body = JarvisAiToolPolicy.providerPayload(question)
            assertFalse("Simple AI question must not require web tools: $question", body.has("tools"))
            assertFalse(body.has("tool_choice"))
            assertEquals(JarvisAiGateway.TEST_MODEL, body.optString("model"))
            assertEquals(question,
                body.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertFalse(JarvisAiToolPolicy.needsLiveWeb(question))
        }
    }

    @Test fun webSearchOnlyForExplicitLiveRequestsInMultipleLanguages() {
        for (question in listOf(
            "Jarvis, nézz utána az aktuális információknak",
            "Keress a neten friss híreket",
            "What is the latest news today?",
            "Search the web for Harrogate traffic",
            "Suche im Internet nach Verkehr",
            "Buscar en internet noticias",
            "Recherche sur internet des nouvelles",
            "Cerca sul web le ultime notizie",
            "Szukaj w internecie informacji"
        )) {
            val body = JarvisAiToolPolicy.providerPayload(question)
            assertTrue(question, JarvisAiToolPolicy.needsLiveWeb(question))
            assertEquals(1, body.getJSONArray("tools").length())
            assertEquals("openrouter:web_search",
                body.getJSONArray("tools").getJSONObject(0).getString("type"))
            assertEquals("auto", body.getString("tool_choice"))
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

    @Test fun simpleChatGetsCompactPromptButCapabilitiesAndActionsKeepFullRules() {
        val greeting = JarvisAiToolPolicy.providerPayload("Halló, jó estét")
        val brief = greeting.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(brief.contains("1-3 mondatban"))
        assertFalse(brief.contains("Az aktuális Jarvis-menüpontok"))
        assertEquals(JarvisAiGateway.TEST_MODEL, greeting.getString("model"))
        assertFalse(greeting.getBoolean("stream"))

        val about = JarvisAiToolPolicy.providerPayload("Mit tudsz Jarvis?")
            .getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(about.contains("Az aktuális Jarvis-menüpontok"))

        val command = JarvisAiToolPolicy.providerPayload("Hívd fel Esztert")
            .getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(command.contains("Az aktuális Jarvis-menüpontok"))

        val live = JarvisAiToolPolicy.providerPayload("Keress a neten friss híreket")
            .getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(live.contains("Az aktuális Jarvis-menüpontok"))
    }
}
