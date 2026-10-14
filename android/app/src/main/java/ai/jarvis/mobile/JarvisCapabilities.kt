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
        n.contains("milyen funkciokat") ||
        n.contains("milyen funkciokat tudsz") ||
        n.contains("milyen funkciok") ||
        n.contains("milyen funkciokkal") ||
        n.contains("milyen funkcioid") ||
        n.contains("milyen funkciod") ||
        n.contains("mik a funkcioid") ||
        n.contains("mit tudsz hasznalni") ||
        n.contains("sorold fel a funkcioid") ||
        n.contains("melyik funkciokat") ||
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
        n.contains("mit tud elmondani magadrol") ||
        n.contains("mit tudsz elmondani magadrol") ||
        n.contains("mit tudnal mondani magadrol") ||
        n.contains("meselj a jarvisrol") ||
        n.contains("mutasd be magad") ||
        n.contains("tell me about yourself") ||
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
        if (isIdentityQuestion(n)) {
            return "Jarvis vagyok, az Android-asszisztensed. Beszélgethetek veled, " +
                "és a telefonod támogatott funkcióit hanggal vagy írásban kezelhetem. " +
                "Kérdezd meg: Milyen funkciókat tudsz használni?"
        }
        val tools = JarvisToolCatalog.all.joinToString(", ") { it.name }
        return "Az elérhető Jarvis-funkciók: $tools. " +
            "Tudok útvonalat kérni a térképen, névjegyet keresni, hívást előkészíteni, " +
            "helyi adatokat olvasni és támogatott alkalmazásokat megnyitni. " +
            "Egyes műveletekhez külön engedély vagy megerősítés kell; " +
            "külső alkalmazások végső eredményét nem mindig látom."
    }

    /** Menu and nested settings share the same offline navigation for voice/text.
     * Explicit page requests take priority; ordinary phone-app requests retain
     * their existing native Android handoffs. */
    fun localPageToOpen(raw: String): String? {
        val command = VoiceIntentParser.afterWakeWord(raw)?.takeIf { it.isNotBlank() } ?: raw
        val n = normalized(command)
        if (!JarvisAiTools.userAskedToOpen(command) || isDiscoveryRequest(command) ||
            JarvisSystemPanels.identify(command) != null ||
            Regex("""\b(ne|nem|dont|never|not|torold|delete|kuldd|send|fizess|pay|es|and|majd|then|aztan)\b""")
                .containsMatchIn(n)) return null
        val aliases = mapOf(
            "Főoldal" to listOf("kezdolap", "home"),
            "Munka" to listOf("eszkoztar", "eszkozok", "work"),
            "Alkalmazások" to listOf("appok", "installed apps"),
            "Előzmények" to listOf("chat history", "beszelgetesnaplo"),
            "Vércukor" to listOf("vercukornaplo", "glucose"),
            "Számlák" to listOf("szamlazas", "invoice"),
            "Üzenetek" to listOf("messages"),
            "Beállítások" to listOf("settings"),
            "Jegyzetek" to listOf("helyi jegyzetek"),
            "Hangbeállítások" to listOf("hangvezerles", "hangfigyeles", "voice settings"),
            "Engedélyek" to listOf("engedelykozpont", "permissions"),
            "Továbbiak" to listOf("otthoni cim", "esemeny hozzaadasa",
                "jarvis ebreszto kezelese", "mentett helyek importalasa", "mentett helyek beallitasai",
                "saved places import")
        )
        val matches = (JarvisToolCatalog.all + JarvisToolCatalog.subpages).filter { tool ->
            (listOf(tool.name, tool.route) + JarvisLanguage.entries.flatMap { lang ->
                listOf(JarvisTranslations.text(tool.name, lang), JarvisTranslations.text(tool.route, lang))
            } + aliases[tool.route].orEmpty()).any { alias ->
                Regex("""\b${Regex.escape(normalized(alias))}[a-z]{0,8}\b""")
                    .containsMatchIn(n)
            }
        }.map { it.route }.toMutableSet()
        // A named nested Settings page is more specific than its parent.
        if (matches.any { it in setOf("AI-agy", "Hangbeállítások", "Engedélyek") }) {
            matches.remove("Beállítások")
        }
        if ("Továbbiak" in matches) matches.remove("Ébresztő")
        val route = matches.singleOrNull() ?: return null
        val explicitPage = Regex("""\b(oldal[a-z]*|menu[a-z]*|kepernyo[a-z]*|page|screen|seite|pagina|ecran|strone)\b""")
            .containsMatchIn(n)
        if (!explicitPage && route in setOf("Email", "Naptár", "Ébresztő")) {
            return null
        }
        return route
    }
}

