package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * One deterministic precedence contract shared by typed chat and hands-free voice.
 *
 * A stale phone call must never hijack a new question, page navigation or app
 * command. A bare name/ordinal/number is a follow-up only while a call flow is
 * explicitly waiting for a target. All final calls still need confirmation.
 */
internal object JarvisCommandContext {
    private fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9+? ]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim()

    /**
     * A valid, short-lived call approval takes priority over an incomplete new
     * call command such as "hívd" or "hívd fel". Without this exception,
     * independentRequest() clears the resolved phone number before "yes" can
     * reach the confirmation handler.
     */
    fun shouldPreemptCallConfirmation(raw: String, activePendingCall: Boolean): Boolean =
        independentRequest(raw) &&
            !(activePendingCall && (VoiceCalling.callConfirmationYes(raw) || VoiceCalling.needsTarget(raw)))

    fun independentRequest(raw: String): Boolean {
        val s = normalize(raw)
        if (s.isBlank()) return false
        if (VoiceCalling.extractRequest(raw) != null || VoiceCalling.needsTarget(raw) ||
            JarvisScreenControl.parse(raw) != null ||
            JarvisControlCommand.parse(raw) != null ||
            JarvisLocalPreferences.parse(raw) != null ||
            JarvisHomeCommands.parse(raw) != null ||
            JarvisHomeCommands.statusTarget(raw) != null) return true
        if (JarvisInstalledApps.requestedLabel(raw) != null ||
            JarvisCapabilities.localPageToOpen(raw) != null ||
            JarvisCapabilities.isDiscoveryRequest(raw) ||
            JarvisPhoneActions.requestedAction(raw) != null ||
            JarvisSystemPanels.identify(raw) != null ||
            VoiceNavigation.destinationFrom(raw) != null ||
            JarvisUpdateVoice.isRequest(raw) ||
            JarvisMessageIntent.parse(raw) != null ||
            VoiceIntentParser.isReadMessage(raw)) return true

        // A phone number or a bare contact can be an answer to "Whom to call?".
        if (VoiceCalling.statedNumber(raw) != null) return false
        if (s.matches(Regex("""\+?[0-9]{5,16}"""))) return false
        if (s in setOf("elso", "az elso", "masodik", "a masodik", "harmadik",
                "igen", "igen hivd", "igen hivd fel", "nem", "no", "yes")) return false

        // Everything else that is an ordinary sentence belongs to the newest
        // user command, not the previous short-lived call state.
        if ('?' in raw || s.split(' ').size > 3) return true
        if (listOf("de ", "akkor ", "mit ", "mi ", "milyen ", "hogyan ",
                "hogy ", "ki ", "hol ", "mikor ", "mennyi ", "mire ",
                "tudsz ", "keress ", "nezd ", "nyisd ", "nyiss ",
                "mondj ", "mutasd ", "sorold ", "magyarazd ",
                "open ", "show ", "what ", "how ", "where ", "when ")
                .any { s.startsWith(it) }) return true
        return false
    }

    /** Caller can use this only while waiting for an explicitly requested
     * contact. Reject free-form conversation masquerading as a contact name.
     */
    fun possibleContactReply(raw: String): String? {
        if (independentRequest(raw)) return null
        if (VoiceCalling.callConfirmationYes(raw) || VoiceIntentParser.isNo(raw)) return null
        if (normalize(raw) in setOf("elso", "az elso", "elsot", "masodik",
                "a masodik", "harmadik", "a harmadik", "first", "second", "third",
                "1", "2", "3")) return null
        val candidate = VoiceCalling.followUpTarget(raw) ?: return null
        val simple = normalize(candidate)
        if (simple.split(' ').size > 3) return null
        if (simple.matches(Regex("""[a-z ]{2,70}""")) ||
            simple.matches(Regex("""\+?[0-9 ]{5,22}"""))) return candidate
        return null
    }
}
