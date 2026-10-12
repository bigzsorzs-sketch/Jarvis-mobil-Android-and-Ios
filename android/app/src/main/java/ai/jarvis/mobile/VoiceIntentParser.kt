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

    private val wakeWords = listOf("hey jarvis", "hej jarvis", "he jarvis", "szia jarvis", "ok jarvis", "okay jarvis")
    fun afterWakeWord(raw: String): String? {
        val normal = normalize(raw)
        val match = wakeWords.mapNotNull { phrase ->
            val index = normal.indexOf(phrase)
            if (index >= 0) index + phrase.length else null
        }.minOrNull() ?: return null
        return raw.substring(match.coerceAtMost(raw.length)).trim().trimStart(',', '.', '!', ':', ' ')
    }

    fun isStop(raw: String): Boolean {
        val n = normalize(raw)
        return listOf("allj le", "leallas", "hagyd abba", "kapcsold ki a figyelest", "stop listening")
            .any { n.contains(it) }
    }

    fun isYes(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("igen", "igen kuldd", "kuldd el", "elkuldheted", "mehet", "yes", "send")
    }

    /** Call confirmation must not mistake 'send' or 'küldd el' for consent to call. */
    fun isCallYes(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("igen", "mehet", "yes", "hivd fel", "call now")
    }

    fun isNo(raw: String): Boolean {
        val n = normalize(raw).trimEnd('.', '!', ',')
        return n in setOf("nem", "megse", "ne kuldd", "torold", "cancel", "no")
    }

    fun isReadMessage(raw: String): Boolean {
        val n = normalize(raw)
        if ("ki irt nekem" in n || "utolso uzenet" in n) return true
        val read = listOf("olvasd fel", "olvas fel", "felolvas", "ki irt").any { it in n }
        val messenger = listOf("uzenet", "whatsapp", "messenger", "telegram", "signal", "sms")
            .any { it in n }
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
