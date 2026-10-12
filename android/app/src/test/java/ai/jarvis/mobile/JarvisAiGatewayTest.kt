package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAiGatewayTest {
    @Test fun rejectsInsecureEndpoints() {
        assertNull(JarvisAiGateway.normalizedBaseUrl("http://example.com"))
        assertNull(JarvisAiGateway.normalizedBaseUrl("https://user:pass@example.com"))
        assertNull(JarvisAiGateway.normalizedBaseUrl("https://example.com/path"))
        assertNull(JarvisAiGateway.normalizedBaseUrl("https://example.com?q=1"))
        assertNull(JarvisAiGateway.normalizedBaseUrl(""))
    }

    @Test fun acceptsHttpsOrigin() {
        assertEquals("https://example.com",
            JarvisAiGateway.normalizedBaseUrl("https://EXAMPLE.com/"))
        assertEquals("https://example.com:8443",
            JarvisAiGateway.normalizedBaseUrl("https://example.com:8443"))
    }

    @Test fun requiresStrongAccessCode() {
        assertTrue(JarvisAiGateway.isValidTesterToken("a".repeat(64)))
        assertFalse(JarvisAiGateway.isValidTesterToken("weak-password"))
        assertFalse(JarvisAiGateway.isValidTesterToken("f".repeat(63)))
    }

    @Test fun checksOpenRouterKeyWithoutEmbeddingOne() {
        assertTrue(JarvisAiGateway.isValidOpenRouterKey("sk-or-" + "x".repeat(40)))
        assertFalse(JarvisAiGateway.isValidOpenRouterKey("not-an-api-key"))
        assertFalse(JarvisAiGateway.isValidOpenRouterKey("sk-or-" + "a".repeat(201)))
        assertFalse(JarvisAiGateway.isValidOpenRouterKey("sk-or-" + "a".repeat(20) + " "))
    }

    @Test fun http404ExplainsRoutingNotInvalidSecrets() {
        val direct = JarvisAiGateway.http404Message(true)
        assertTrue(direct.contains("OpenRouter HTTP 404"))
        assertTrue(direct.contains("openai/gpt-4o-mini"))
        assertFalse(direct.contains("érvénytelen"))
        val server = JarvisAiGateway.http404Message(false)
        assertTrue(server.contains("/v1/chat"))
        assertTrue(server.contains("Saját"))
        assertFalse(server.contains("sk-or-"))
    }

    @Test fun routesQuestionsButNotLocalCommands() {
        assertTrue(JarvisAiRouting.shouldAskAi("Magyarázd el a napfogyatkozást"))
        assertFalse(JarvisAiRouting.shouldAskAi("Szia"))
        assertFalse(JarvisAiRouting.shouldAskAi("Nyisd meg a jegyzeteket"))
    }
}
