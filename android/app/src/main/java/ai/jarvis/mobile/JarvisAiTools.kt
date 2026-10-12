package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** The AI may propose only vetted UI handoffs, an explicitly requested
 * call pending confirmation, or a reviewed external message draft.
 * It never receives arbitrary device execution, file deletion or direct sending.
 */
internal data class JarvisAiOutcome(
    val message: String,
    val route: String? = null,
    val deviceAction: String? = null,
    val callTarget: String? = null,
    val messageDraft: OutgoingMessage? = null,
    val nativeAction: String? = null
)

internal object JarvisAiTools {
    const val FUNCTION_NAME = "open_jarvis_page"
    const val PHONE_FUNCTION = "open_phone_app"
    const val CALL_FUNCTION = "prepare_phone_call"
    const val MESSAGE_FUNCTION = "prepare_message_draft"
    const val NATIVE_FUNCTION = "select_native_tool"

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

    /** A call can only be proposed. Android resolves the original user's target,
     * shows the real phone number, and requires a separate user confirmation.
     */
    fun callToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", CALL_FUNCTION)
            .put("description", "Prepare exactly one telephone call only when the user " +
                "explicitly requests calling a named contact or phone number. " +
                "The Android device checks the target and must obtain confirmation " +
                "before making any call. Never claim a call started.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("target", JSONObject()
                    .put("type", "string").put("minLength", 1).put("maxLength", 70)))
                .put("required", JSONArray().put("target"))
                .put("additionalProperties", false)))

    /** The model chooses only the app. Recipient and content always come from
     * the original user command parsed on the device.
     */
    fun messageToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", MESSAGE_FUNCTION)
            .put("description", "Prepare one WhatsApp or Messenger message draft ONLY if " +
                "the user explicitly specifies the application, recipient and full " +
                "message text. The phone reconstructs recipient and text from the " +
                "original user command. Never send or claim delivery.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("platform", JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(listOf("whatsapp", "messenger")))))
                .put("required", JSONArray().put("platform"))
                .put("additionalProperties", false)))

    /** The AI selects a supported capability, but supplies no device arguments. */
    fun nativeToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", NATIVE_FUNCTION)
            .put("description", "Propose exactly one supported Android action requested explicitly " +
                "by the user: navigate, set/cancel Jarvis alarm, compose email, open Gmail " +
                "search or the UC sign-in page. The phone independently checks the " +
                "original user instruction and handles the real operation.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("action", JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(JarvisNativeActions.supported))))
                .put("required", JSONArray().put("action"))
                .put("additionalProperties", false)))

    fun validateNative(action: String, originalInput: String): JarvisAiOutcome =
        if (JarvisNativeActions.validate(action, originalInput))
            JarvisAiOutcome("A parancsot Androidon ellenőrzöm.", nativeAction = action)
        else
            JarvisAiOutcome("Az eszközkérés nem egyezik a parancsoddal. Nem történt művelet.")

    fun validateMessage(suggestedPlatform: String, originalInput: String): JarvisAiOutcome {
        val message = JarvisMessageIntent.validateAiProposal(suggestedPlatform, originalInput)
            ?: return JarvisAiOutcome(
                "Az AI üzenetkérése nem egyezik a parancsoddal. Nem készítettem piszkozatot.")
        return JarvisAiOutcome("Az üzenetpiszkozat jóváhagyásra vár.", messageDraft = message)
    }

    fun validateCall(suggested: String, originalInput: String): JarvisAiOutcome {
        val requested = VoiceCalling.extractAiRequest(originalInput)
        if (!VoiceCalling.matchesAiTarget(requested, suggested)) {
            return JarvisAiOutcome("A hívás címzettje nem egyezik a parancsoddal. Nem indítottam hívást.")
        }
        return JarvisAiOutcome("A hívás jóváhagyásra vár.", callTarget = requested)
    }

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
            "Alkalmazások" to listOf("alkalmazasok", "appok", "installed apps"),
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
            if (function.optString("name") == NATIVE_FUNCTION) {
                if (args.length() != 1 || args.opt("action") !is String) return JarvisAiOutcome("Érvénytelen AI-művelet.")
                return validateNative(args.optString("action"), originalInput)
            }
            if (function.optString("name") == MESSAGE_FUNCTION) {
                if (args.length() != 1 || args.opt("platform") !is String) {
                    return JarvisAiOutcome("Érvénytelen AI-üzenetparaméterek. Nem történt művelet.")
                }
                return validateMessage(args.optString("platform"), originalInput)
            }
            if (function.optString("name") == CALL_FUNCTION) {
                if (args.length() != 1 || !args.has("target") || args.opt("target") !is String) {
                    return JarvisAiOutcome("Érvénytelen hívási paraméterek; hívás nem indult.")
                }
                return validateCall(args.optString("target"), originalInput)
            }
            if (args.length() != 1 || !args.has("route") || args.opt("route") !is String) {
                return JarvisAiOutcome("Az AI ismeretlen eszközparamétereket adott; nem hajtottam végre semmit.")
            }
            return validate(function.optString("name"), args.optString("route"), originalInput)
        }
        if (VoiceCalling.extractAiRequest(originalInput) != null) {
            return JarvisAiOutcome("Az AI nem adott ellenőrzött hívási parancsot. Nem indítottam hívást.")
        }
        if (JarvisMessageIntent.parse(originalInput) != null) {
            return JarvisAiOutcome("Az AI nem adott ellenőrzött üzenetpiszkozatot. Nem történt küldés.")
        }
        if (JarvisNativeActions.identify(originalInput) != null) {
            return JarvisAiOutcome("Az AI nem adott ellenőrzött Android-parancsot. Nem történt végrehajtás.")
        }
        return JarvisAiOutcome(message.optString("content", "").trim()
            .ifEmpty { "Az AI üres választ küldött." })
    }

    /** The private server only proposes an action, it must never execute one. */
    fun fromServer(body: JSONObject, originalInput: String): JarvisAiOutcome {
        val action = body.optJSONObject("action")
        if (action != null) {
            if (action.length() != 2 || action.opt("name") !is String) {
                return JarvisAiOutcome("Érvénytelen szerveres eszközkérés; semmit sem hajtottam végre.")
            }
            if (action.optString("name") == NATIVE_FUNCTION) {
                if (action.opt("action") !is String) return JarvisAiOutcome("Érvénytelen szerveres Android-művelet.")
                return validateNative(action.optString("action"), originalInput)
            }
            if (action.optString("name") == MESSAGE_FUNCTION) {
                if (action.opt("platform") !is String) {
                    return JarvisAiOutcome("Érvénytelen szerveres üzenetparaméterek.")
                }
                return validateMessage(action.optString("platform"), originalInput)
            }
            if (action.optString("name") == CALL_FUNCTION) {
                if (action.opt("target") !is String) {
                    return JarvisAiOutcome("Érvénytelen hívási paraméterek; hívás nem indult.")
                }
                return validateCall(action.optString("target"), originalInput)
            }
            if (action.opt("route") !is String) {
                return JarvisAiOutcome("Érvénytelen szerveres eszközkérés; semmit sem hajtottam végre.")
            }
            return validate(action.optString("name"), action.optString("route"), originalInput)
        }
        // Fail closed on unexpected action types, not a string masquerading as a tool call.
        if (body.has("action")) return JarvisAiOutcome("Hibás szerveres eszközkérés.")
        if (VoiceCalling.extractAiRequest(originalInput) != null) {
            return JarvisAiOutcome("A szerver nem adott ellenőrzött hívási parancsot. Nem indítottam hívást.")
        }
        if (JarvisMessageIntent.parse(originalInput) != null) {
            return JarvisAiOutcome("A szerver nem adott ellenőrzött üzenetpiszkozatot. Nem történt küldés.")
        }
        if (JarvisNativeActions.identify(originalInput) != null) {
            return JarvisAiOutcome("A szerver nem adott ellenőrzött Android-parancsot. Nem történt végrehajtás.")
        }
        return JarvisAiOutcome(body.optString("reply", "").trim()
            .ifEmpty { "Az AI üres választ küldött." })
    }
}
