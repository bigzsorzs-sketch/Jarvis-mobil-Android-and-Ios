package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Only navigation to existing Jarvis pages is available to the AI.
 * The AI never receives a general Android command executor, file access,
 * message sender, settings writer or arbitrary Intent.
 */
internal data class JarvisAiOutcome(
    val message: String,
    val route: String? = null,
    val deviceAction: String? = null
)

internal object JarvisAiTools {
    const val FUNCTION_NAME = "open_jarvis_page"
    const val PHONE_FUNCTION = "open_phone_app"

    // Use the same menu inventory as the actual UI; no fabricated tools.
    val allowedRoutes: List<String>
        get() = JarvisToolCatalog.all.map { it.route }.distinct()

    /** OpenRouter / OpenAI-compatible function specification (read-only plan).
     * A server must not execute returned tool calls: validation is on-device.
     */
    fun toolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", FUNCTION_NAME)
            .put("description", "Open exactly one supported Jarvis Mobile menu page or handoff. " +
                "Only call this when the user clearly asks to OPEN a page or application; " +
                "never claim to have executed an action yourself.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("route", JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(allowedRoutes))))
                .put("required", JSONArray().put("route"))
                .put("additionalProperties", false)))

    fun phoneToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", PHONE_FUNCTION)
            .put("description", "Only open the Android Calendar, Alarms, Email composer, Maps " +
                "or Dialer if the user explicitly commands to open that specific app. " +
                "No sending, calling, editing, deleting or background actions.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("route", JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(JarvisPhoneActions.supported))))
                .put("required", JSONArray().put("route"))
                .put("additionalProperties", false)))

    private fun normal(raw: String): String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim().replace(Regex("\\s+"), " ")

    /** Guard independent of any model instructions: navigation must be requested
     * by the user's own input, not invented by model text or prompt injection.
     */
    fun userAskedToOpen(raw: String): Boolean {
        val input = normal(raw).removePrefix("hey jarvis ")
            .removePrefix("jarvis ").trim()
            .removePrefix("kerlek ").removePrefix("please ").trim()
        return listOf(
            "nyisd ", "nyit ", "nyitnad ", "megnyit ", "megnyitnad ",
            "mutasd ", "mutatnad ", "lepj ", "menj ", "inditsd ",
            "szeretnem megnyitni", "szeretnem latni", "szeretnek belepni",
            "open ", "show ", "launch ", "go to ", "take me to "
        ).any { input.startsWith(it) }
    }

    /**
     * Prevent the model choosing the wrong (yet allowlisted) destination.
     * One explicitly named destination per command; no guessed app.
     */
    private fun routeMatchesUser(route: String, input: String): Boolean {
        val normalized = normal(input)
        val targets = mapOf(
            "Főoldal" to listOf("fooldal", "kezdolap", "home"),
            "Munka" to listOf("munka", "eszkoztar", "work"),
            "Navigáció" to listOf("navig", "terkep", "maps"),
            "Vércukor" to listOf("vercuk", "glucose", "blood sugar"),
            "Számlák" to listOf("szaml", "invoice"),
            "Email" to listOf("email", "gmail", "level", "mail"),
            "Üzenetek" to listOf("uzenet", "whatsapp", "messenger", "messages"),
            "Névjegyek" to listOf("nevjegy", "contact", "hivas"),
            "Naptár" to listOf("naptar", "calendar"),
            "Jegyzetek" to listOf("jegyzet", "notes"),
            "Ébresztő" to listOf("ebreszt", "alarm"),
            "Ismerősök" to listOf("ismeros", "people"),
            "Beállítások" to listOf("beallitas", "settings", "engedely")
        )
        if (Regex("\\b(torol|torold|delete|erase|kuldd|send|fizess|pay)\\b")
                .containsMatchIn(normalized)) return false
        val matched = targets.filterValues { stems ->
            stems.any { stem -> normalized.contains(stem) }
        }.keys
        return matched.size == 1 && route in matched
    }

    fun validate(name: String, route: String, originalInput: String): JarvisAiOutcome {
        if (name == PHONE_FUNCTION) {
            return if (JarvisPhoneActions.authorized(route, originalInput))
                JarvisAiOutcome("A kért Android-alkalmazás megnyitását előkészítettem.",
                    deviceAction = route)
            else JarvisAiOutcome("A telefonos parancs nincs egyértelműen engedélyezve. Nem történt művelet.")
        }
        if (name != FUNCTION_NAME || route !in allowedRoutes) {
            return JarvisAiOutcome("Ezt az AI-eszközparancsot nem támogatom; nem hajtottam végre semmit.")
        }
        if (!userAskedToOpen(originalInput) || !routeMatchesUser(route, originalInput)) {
            return JarvisAiOutcome("Az eszközkérés nem egyezik egyértelműen a parancsoddal. Nem történt művelet.")
        }
        return JarvisAiOutcome("A kért Jarvis-oldalt az Androiddal nyitom meg: $route.", route)
    }

    /** Parse structured provider output. Never interpret assistant text as executable JSON. */
    fun fromProvider(body: JSONObject, originalInput: String): JarvisAiOutcome {
        val message = body.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return JarvisAiOutcome("Az AI nem küldött értelmezhető választ.")
        val calls = message.optJSONArray("tool_calls")
        if (calls != null && calls.length() > 0) {
            if (calls.length() != 1) return JarvisAiOutcome(
                "Több AI-eszközkérés érkezett; biztonsági okból egyiket sem hajtottam végre."
            )
            val call = calls.optJSONObject(0)
                ?: return JarvisAiOutcome("Hibás AI-eszközkérés; nem történt művelet.")
            if (call.optString("type") != "function") {
                return JarvisAiOutcome("Nem támogatott AI-eszközkérés.")
            }
            val function = call.optJSONObject("function")
                ?: return JarvisAiOutcome("Hibás AI-eszközkérés.")
            val args = try {
                JSONObject(function.optString("arguments", ""))
            } catch (_: Exception) {
                return JarvisAiOutcome("Az AI érvénytelen eszközparamétereket adott.")
            }
            if (args.length() != 1 || !args.has("route") || args.opt("route") !is String) {
                return JarvisAiOutcome("Az AI ismeretlen eszközparamétereket adott; nem hajtottam végre semmit.")
            }
            return validate(function.optString("name"), args.optString("route"), originalInput)
        }
        return JarvisAiOutcome(message.optString("content", "").trim()
            .ifEmpty { "Az AI üres választ küldött." })
    }

    /** The private server only proposes an action, it must never execute one. */
    fun fromServer(body: JSONObject, originalInput: String): JarvisAiOutcome {
        val action = body.optJSONObject("action")
        if (action != null) {
            if (action.length() != 2 || !action.has("name") || !action.has("route") ||
                action.opt("name") !is String || action.opt("route") !is String) {
                return JarvisAiOutcome("Érvénytelen szerveres eszközkérés; semmit sem hajtottam végre.")
            }
            return validate(action.optString("name"), action.optString("route"), originalInput)
        }
        // Fail closed on unexpected action types, not a string masquerading as a tool call.
        if (body.has("action")) return JarvisAiOutcome("Hibás szerveres eszközkérés.")
        return JarvisAiOutcome(body.optString("reply", "").trim()
            .ifEmpty { "Az AI üres választ küldött." })
    }
}
