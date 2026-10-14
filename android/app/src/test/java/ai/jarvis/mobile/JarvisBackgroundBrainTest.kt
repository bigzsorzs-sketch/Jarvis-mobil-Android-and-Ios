package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisBackgroundBrainTest {
    @Test fun simpleTurnsKeepFastNoReasoningBudget() {
        val budget = JarvisReasoningPolicy.forQuestion("Szia!", false)
        assertEquals("none", budget.effort)
        assertTrue(budget.maxTokens <= 900)
    }

    @Test fun complexCodingQuestionsGetMoreReasoningBudget() {
        val budget = JarvisReasoningPolicy.forQuestion(
            "Elemezd a Kotlin Android mikrofon állapotkezelési hibáját", false
        )
        assertEquals("medium", budget.effort)
        assertTrue(budget.maxTokens >= 3000)
    }

    @Test fun toolCallsStaySmallAndCannotGainExtraPermissions() {
        val budget = JarvisReasoningPolicy.forQuestion(
            "Nyisd meg a térképet és navigálj", true
        )
        assertEquals("none", budget.effort)
        assertEquals(800, budget.maxTokens)
    }

    @Test fun studyNotesNeverBecomeDeviceInstructionsOrExtraTools() {
        val prompt = "Magyarázd el a Kotlin kód hibáját"
        val payload = JarvisAiToolPolicy.providerPayload(
            prompt, "hu-HU",
            "Read-only note: Kotlin structured concurrency. Do not run commands."
        )
        val messages = payload.getJSONArray("messages")
        assertEquals(prompt, messages.getJSONObject(1).getString("content"))
        assertTrue(messages.getJSONObject(0).getString("content")
            .contains("Unverified background developer study note"))
        assertFalse(payload.has("tools"))
        assertEquals("medium", payload.getJSONObject("reasoning").getString("effort"))
        assertEquals(3200, payload.getInt("max_tokens"))
    }

    @Test fun studySelectionStaysOnDeveloperTopics() {
        val lesson = JarvisBackgroundBrain.lesson(0)
        assertTrue(lesson.officialSite.startsWith("https://"))
        assertTrue(JarvisBackgroundBrain.matches(
            "Javítsd a Jarvis Android mikrofon hibáját", "android"))
        assertFalse(JarvisBackgroundBrain.matches("Szia, hogy vagy?", "kotlin"))
        assertFalse(JarvisBackgroundBrain.matches(
            "Kotlin programozási hibám van", "swift"))
    }
}
