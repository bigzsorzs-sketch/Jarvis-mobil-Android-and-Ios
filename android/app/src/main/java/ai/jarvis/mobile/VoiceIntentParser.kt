package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Rule-based commands only. "Hey Jarvis" is recognized *after* Android speech
 * recognition has provided text. This is not a dedicated low-power wake-word
 * engine and cannot guarantee always-on / lock-screen recognition.
 */
internal object VoiceIntentParser {
    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").trim()

    fun afterWakeWord(raw: String): String? {
        val normal = normalize(raw)
        // ASR may insert punctuation between the greeting and the name.
        // Bare "Jarvis" is accepted only at the start, never a passing mention.
        val match = Regex("""\b(?:hey|hej|he|szia|ok|okay)[,\s]+jarvis\b|^jarvis\b""")
            .find(normal) ?: return null
        return raw.substring((match.range.last + 1).coerceAtMost(raw.length))
            .trim().trimStart(',', '.', '!', ':', ' ')
    }

    /**
     * Explicit command to interrupt speech output, without necessarily
     * switching off hands-free listening. Exact normalized utterances only:
     * story text mentioning "stop" must not cancel playback.
     */
    fun isPlaybackStop(raw: String): Boolean {
        val command = afterWakeWord(raw) ?: raw
        val normalized = normalize(command).trim()
            .trimEnd('.', '!', '?', ',')
        return normalized in setOf(
            "allj", "allj meg", "ne olvass tovabb", "ne olvasd tovabb",
            "hagyd abba a felolvasast", "fejezd be a felolvasast",
            "stop", "stop reading", "stop speaking", "be quiet"
        )
    }

    /**
     * Stop the user-started microphone session ONLY on an explicit utterance.
     * A substring check would treat "Ne állj le" / "Don't stop listening"
     * and quoted instructions as permission to shut down the session.
     * Keep voice session stop distinct from stopping just the read-aloud player.
     */
    fun isStop(raw: String): Boolean {
        val spoken = afterWakeWord(raw) ?: raw
        val normalized = normalize(spoken).trim().trimEnd('.', '!', '?', ',')
            .replace(Regex("""^(?:kerlek|legy szives|figyelj|please|bitte|por favor|per favore|prosze)[,\\s]+"""), "")
            .trim()
        return normalized in setOf(
            "allj le", "leallas", "hagyd abba", "kapcsold ki a figyelest",
            "stop listening", "stop the microphone",
            "hor auf zuzuhoren", "hor auf mit dem zuhoren",
            "arrete d'ecouter", "arretez d'ecouter",
            "deja de escuchar", "deten la escucha",
            "smetti di ascoltare", "interrompi l'ascolto",
            "przestan sluchac", "wylacz nasluchiwanie"
        )
    }

    fun isYes(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("igen", "igen kuldd", "kuldd el", "elkuldheted", "mehet", "yes", "send")
    }

    /** Call confirmation must not mistake 'send' or 'küldd el' for consent to call. */
    fun isCallYes(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("igen", "mehet", "yes", "call now", "ja", "ruf an", "oui", "appelle", "sí", "si", "llama", "sì", "chiama", "tak", "zadzwon")
    }

    /** A multi-step workflow must not accept message-send or call words as consent. */
    fun isSequenceYes(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("igen", "mehet", "yes", "start", "hajtsd vegre", "execute")
    }

    fun isNo(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("nem", "megse", "ne kuldd", "torold", "cancel", "no", "nein", "abbrechen", "non", "annuler", "não", "anuluj", "nie", "anulare", "annulla", "cancelar")
    }

    fun isReadMessage(raw: String): Boolean {
        val n = normalize(raw)
        if ("ki irt nekem" in n || "utolso uzenet" in n) return true
        val read = listOf(
            "olvasd fel", "olvas fel", "felolvas", "ki irt",
            "read ", "lies ", "vorlesen", "lis ", "lisez ", "lee ",
            "leggi ", "przeczytaj ", "odczytaj "
        ).any { it in n }
        val messenger = listOf(
            "uzenet", "whatsapp", "messenger", "telegram", "signal", "sms",
            "message", "nachricht", "mensaje", "messaggio", "wiadomosc"
        ).any { it in n }
        return read && messenger
    }

    fun messengerHint(raw: String): String? {
        val n = normalize(raw)
        return when {
            "whatsapp" in n -> "WhatsApp"
            "messenger" in n -> "Messenger"
            "telegram" in n -> "Telegram"
            "signal" in n -> "Signal"
            "sms" in n -> "Messages"
            else -> null
        }
    }

    fun replyDraft(raw: String): String? {
        val n = normalize(raw)
        if (!listOf("valaszolj", "irj neki", "uzenj neki", "reply").any { it in n }) return null
        val match = Regex("\\b(?:hogy|saying)\\s+", RegexOption.IGNORE_CASE).find(n)
            ?: return null
        // All earlier NFD transformations preserve length for ordinary Hungarian
        // letters; to avoid offset errors, take the tail from the *original*
        // string using a direct matching regex.
        val actual = Regex("\\b(?:hogy|saying)\\s+", RegexOption.IGNORE_CASE).find(raw)
            ?: return null
        val text = raw.substring(actual.range.last + 1).trim().trimEnd('.')
        return text.takeIf { it.isNotBlank() && it.length <= 500 }
    }
}

