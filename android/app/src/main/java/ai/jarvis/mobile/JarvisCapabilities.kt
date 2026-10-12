package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Truthful, offline discovery of Jarvis' *actual menu* and strictly allowlisted
 * navigation commands. No LLM text is interpreted as an Android action here.
 */
internal object JarvisCapabilities {
    private fun normalized(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun isCapabilityQuestion(n: String): Boolean =
        n.contains("mire vagy kepes") ||
        n.contains("mire kepes a jarvis") ||
        n.contains("milyen funkcioid") ||
        n.contains("milyen funkcioi") ||
        n.contains("milyen eszkozeid") ||
        n.contains("milyen eszkozei") ||
        n.contains("osszes eszkozt") ||
        n.contains("osszes eszkozod") ||
        n.contains("eszkozeid listaja") ||
        n.contains("listazd az eszkoz") ||
        n.contains("sorold fel az eszkoz") ||
        n.contains("mit tud a jarvis") ||
        n == "mit tudsz" ||
        n == "mit tudsz jarvis"

    private fun isIdentityQuestion(n: String): Boolean =
        n.contains("mondj magadrol") ||
        n.contains("meselj magadrol") ||
        n.contains("mutatkozz be") ||
        n == "ki vagy" ||
        n == "ki vagy te" ||
        n == "ki a jarvis" ||
        n.contains("ki fejlesztett") ||
        n.contains("ki keszitett") ||
        n.contains("who are you") ||
        n.contains("what are you")

    fun isDiscoveryRequest(raw: String): Boolean {
        val n = normalized(raw)
        return n.isNotBlank() && (isCapabilityQuestion(n) || isIdentityQuestion(n))
    }

    fun replyIfAsked(raw: String): String? {
        if (!isDiscoveryRequest(raw)) return null
        val n = normalized(raw)
        if (n.contains("ki fejlesztett") || n.contains("ki keszitett")) {
            return "A Jarvis Mobile önálló fejlesztésű alkalmazás. A válaszaimhoz " +
                "külső AI-modellt használok, de a Jarvis alkalmazást nem az OpenAI fejlesztette."
        }
        val menu = JarvisToolCatalog.all.joinToString(", ") { it.name }
        return "Jarvis vagyok, a Jarvis Mobile Android-asszisztense. " +
            "A saját alkalmazásom menüjében ezek érhetők el: $menu. " +
            "Szöveges kérdésekre válaszolhatok és felolvashatom a választ. " +
            "A támogatott helyi parancsokkal oldalakat nyithatok meg, illetve " +
            "telefonos műveleteket készíthetek elő. Hívás, üzenet és külső alkalmazás " +
            "esetén engedély vagy jóváhagyás kellhet. " +
            "Az AI-válaszaim önmagukban nem vezérelnek tetszőleges eszközt, " +
            "és nem látom automatikusan a telefon teljes tartalmát."
    }

    /**
     * Only clearly imperative requests, and only routes implemented by Jarvis.
     * Existing alarm, map, email, glucose etc. commands keep their own handlers.
     */
    fun localPageToOpen(raw: String): String? {
        val n = normalized(raw)
        val openRequest = listOf("nyisd meg", "nyitnad meg", "mutasd", "lepj a",
            "lepj be", "open ", "show ", "go to ").any { it in n }
        if (!openRequest || isDiscoveryRequest(raw)) return null
        val route = when {
            Regex("\\bismeros").containsMatchIn(n) -> "Ismerősök"
            Regex("\\bnevjegy").containsMatchIn(n) -> "Névjegyek"
            Regex("\\belozmeny").containsMatchIn(n) ||
                n.contains("chat history") -> "Előzmények"
            Regex("\\balkalmazasok").containsMatchIn(n) ||
                Regex("\\bappok").containsMatchIn(n) -> "Alkalmazások"
            Regex("\\bmunka\\b").containsMatchIn(n) ||
                n.contains("eszkoztar") -> "Munka"
            else -> null
        }
        return route?.takeIf { selected ->
            JarvisToolCatalog.all.any { it.route == selected }
        }
    }
}
