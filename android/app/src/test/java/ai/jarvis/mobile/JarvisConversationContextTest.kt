package ai.jarvis.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisConversationContextTest {
    @Test fun followUpCarriesPreviousTurnsAsRolesAndCurrentQuestionExactlyOnce() {
        val prior = listOf("Két autó közül választok: Focus vagy Golf." to "Hasonlítsuk össze őket.",
            "A Golf drágább." to "Akkor a fenntartási költséget is nézzük.")
        val body = JarvisAiToolPolicy.providerPayload("És a másik?", history = prior)
        val messages = body.getJSONArray("messages")
        assertEquals(6, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(prior[0].first, messages.getJSONObject(1).getString("content"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals(prior[1].second, messages.getJSONObject(4).getString("content"))
        assertEquals("És a másik?", messages.getJSONObject(5).getString("content"))
        assertFalse(body.has("tools"))
        assertEquals("openai/gpt-5.2", body.getString("model"))
        val server = JarvisAiGateway.serverChatPayload("És a másik?", "hu-HU", history = prior)
        assertEquals(4, server.getJSONArray("history").length())
        assertEquals("És a másik?", server.getString("message"))
    }

    @Test fun priorCommandCannotAuthorizeAToolForAFollowUp() {
        val prior = listOf("Nyisd meg a térképet" to "Megnyitottam.")
        val body = JarvisAiToolPolicy.providerPayload("Miért?", history = prior)
        assertFalse(body.has("tools"))
        assertNull(JarvisAiTools.validate("open_phone_app", "maps", "Miért?").deviceAction)
        val command = JarvisAiToolPolicy.providerPayload("Hívd fel Esztert", history = prior)
        assertEquals(2, command.getJSONArray("messages").length())
        assertFalse(JarvisAiGateway.serverChatPayload("Hívd fel Esztert", "hu-HU", history = prior).has("history"))
    }

    @Test fun historyBoundKeepsRecentCompletePairsInOrder() {
        val turns = (1..40).map { "kérdés $it" to "válasz $it" }
        val kept = JarvisConversationContext.bounded(turns)
        assertEquals(24, kept.size)
        assertEquals(turns.last(), kept.last())
        assertEquals(turns[16], kept.first())
        val huge = JarvisConversationContext.bounded((1..40).map { "a".repeat(8000) to "b".repeat(16000) })
        assertTrue(huge.sumOf { it.first.length + it.second.length } <= 64000)
        assertTrue(JarvisConversationContext.bounded(emptyList()).isEmpty())
    }

    @Test fun errorsAndActionsDoNotBecomeCloudHistory() {
        assertFalse(JarvisAiOutcome("Hálózati hiba").conversationReply)
        assertFalse(JarvisAiTools.validate("open_phone_app", "maps", "Nyisd meg a térképet").conversationReply)
        assertFalse(JarvisAiTools.fromProvider(JSONObject("""{"choices":[{"message":{"content":""}}]}"""), "Szia").conversationReply)
        assertTrue(JarvisAiTools.fromProvider(JSONObject("""{"choices":[{"message":{"content":"Szia George!"}}]}"""), "Szia").conversationReply)
    }

    @Test fun greetingsAndSelfDescriptionUseModelWhenConfigured() {
        listOf("Szia", "Ki vagy?", "mire vagy képes mint Jarvis? hogyan mutatnád be magad?",
            "ha el kellene adnod magad akkor mit mondanál magadról?").forEach {
            assertTrue(it, JarvisAiRouting.shouldAskAi(it))
        }
        assertFalse(JarvisAiRouting.shouldAskAi("Szia", false))
        assertFalse(JarvisAiRouting.shouldAskAi("Ki vagy?", false))
    }

    @Test fun updateQuestionsAndConversationAreNeverUpdateCommands() {
        listOf("milyen új frissítést kaptál", "What update did you receive?",
            "Welche Aktualisierung hast du bekommen?", "Quelle mise à jour as-tu reçue?",
            "¿Qué actualización recibiste?", "Quale aggiornamento hai ricevuto?",
            "Jaką aktualizację dostałeś?", "Ne frissítsd magad", "Don't install the update",
            "Explain how to update Jarvis").forEach {
            assertFalse(it, JarvisUpdateVoice.isRequest(it))
            assertFalse(it, JarvisUpdateVoice.shouldInstall(it))
        }
        assertTrue(JarvisAiRouting.shouldAskAi("milyen új frissítést kaptál"))
        assertTrue(JarvisUpdateVoice.isRequest("Jarvis, ellenőrizd a frissítéseket"))
        assertTrue(JarvisUpdateVoice.shouldInstall("Jarvis, frissítsd magad"))
    }

    @Test fun talkingAboutBillingDoesNotQueryPrivateInvoices() {
        listOf("Hogyan működik a számlázás?", "Explain invoicing", "Szerintem a számlázás bonyolult",
            "Was ist eine Rechnung?", "Explique une facture", "Explica una factura",
            "Spiega la fattura", "Wyjaśnij fakturę").forEach { assertFalse(it, BillingVoice.isCandidate(it)) }
    }

    @Test fun longAnswerSurvivesBeyondOldStreamingLimit() {
        val decoder = JarvisSseDecoder()
        val part = "x".repeat(12000)
        repeat(3) {
            decoder.accept("data: " + JSONObject().put("choices", org.json.JSONArray().put(
                JSONObject().put("delta", JSONObject().put("content", part)))))
        }
        decoder.accept("data: [DONE]")
        assertNull(decoder.failure)
        assertEquals(36000, decoder.text.length)
    }

    @Test fun topicalConversationDoesNotOpenAnApp() {
        listOf("Beszélgessünk a térképekről", "Szerintem a beállítások bonyolultak",
            "Explain why calendar reminders help", "Erkläre mir Gmail permissions",
            "Explique les maps", "Explica el email", "Spiega il calendar", "Wyjaśnij email").forEach {
            assertEquals(it, BasicAction.UNKNOWN, BasicCommandRouter.classify(it))
        }
    }

    @Test fun everyConversationGetsTheRequestedMaximumOutputCeiling() {
        listOf("Szia!", "És a másik?", "Írj egy történetet", "Hasonlítsd össze", "Think deeply").forEach {
            assertEquals(it, 128_000, JarvisAiToolPolicy.providerPayload(it).getInt("max_tokens"))
        }
    }

    @Test fun reasoningDepthWorksInAllSevenLanguagesAndKeepsToolsBounded() {
        listOf("Gondold át alaposan", "Think deeply", "Denke gründlich", "Analyse approfondie",
            "Analiza a fondo", "Analizza a fondo", "Przeanalizuj dokładnie").forEach {
            val budget = JarvisReasoningPolicy.forQuestion(it, false)
            assertEquals(it, "high", budget.effort)
            assertEquals(128_000, budget.maxTokens)
            assertEquals("none", JarvisReasoningPolicy.forQuestion(it, true).effort)
        }
        listOf("Hasonlítsd össze", "Compare these options", "Vergleiche diese Optionen",
            "Comparez ces options", "Compara estas opciones", "Confronta queste opzioni",
            "Porównaj te opcje").forEach {
            assertEquals(it, "medium", JarvisReasoningPolicy.forQuestion(it, false).effort)
        }
    }
}
