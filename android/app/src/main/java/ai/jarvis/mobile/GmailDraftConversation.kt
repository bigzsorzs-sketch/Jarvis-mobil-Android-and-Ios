package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Short-lived, foreground-only Gmail composition context. The user supplies
 * the content, NEVER the AI model. Nothing is persisted to chat history.
 */
internal data class GmailDraftConversation(
    val recipient: String,
    val actuallySend: Boolean,
    val subject: String = "",
    val expiresAtMs: Long
)

internal data class GmailRecipientChoice(
    val options: List<String>,
    val actuallySend: Boolean,
    val expiresAtMs: Long
)

internal sealed interface GmailDraftNext {
    data class Updated(val draft: GmailDraftConversation) : GmailDraftNext
    data class Complete(val send: GmailOperation.Send) : GmailDraftNext
    data object Cancelled : GmailDraftNext
    data object NotRelated : GmailDraftNext
}

internal object GmailDraftConversationFlow {
    private fun normalized(raw: String) = Normalizer.normalize(
        raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""\s+"""), " ").trim()

    // A standalone recipient address is meaningful only when a Gmail draft
    // is explicitly waiting for a recipient. Never infer delivery permission.
    private val recipientEmail = Regex("""[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}""")
    fun recipientAddress(raw: String): String? = raw.trim().takeIf {
        it.length <= 254 && recipientEmail.matches(it)
    }

    fun isWakeWord(raw: String): Boolean =
        normalized(raw).trimEnd('.', '!', '?', ',') in
            setOf("jarvis", "hey jarvis", "hej jarvis", "szia jarvis", "ok jarvis")

    fun isCancel(raw: String): Boolean =
        normalized(raw).trimEnd('.', '!') in
            setOf("megse", "torold", "megszakitas", "cancel", "stop", "ne kuldd", "nem")

    fun choice(raw: String, count: Int): Int? {
        val n = normalized(raw).trimEnd('.', '!')
        val index = when (n) {
            "1", "elso", "az elso", "first" -> 0
            "2", "masodik", "a masodik", "second" -> 1
            "3", "harmadik", "a harmadik", "third" -> 2
            "4", "negyedik", "fourth" -> 3
            "5", "otodik", "fifth" -> 4
            else -> return null
        }
        return index.takeIf { it < count }
    }

    fun advance(draft: GmailDraftConversation, input: String): GmailDraftNext {
        val raw = input.trim()
        if (isCancel(raw)) return GmailDraftNext.Cancelled
        if (isWakeWord(raw) || recipientAddress(raw) != null) return GmailDraftNext.NotRelated
        if (raw.length !in 2..4000 || raw.contains('\n') && raw.length > 3500)
            return GmailDraftNext.NotRelated
        // These are new independent commands, not answer text for the pending
        // email. A long free-form email body is accepted unless it begins with
        // a known new command/question.
        val n = normalized(raw)
        if (Regex("""^(?:nyisd|keress|navigalj|hivd|olvasd|mutasd|milyen|mennyi|hany|how|what|open|search|call|navigate|read|kuldd|kuldj|send)\b""")
                .containsMatchIn(n)) return GmailDraftNext.NotRelated

        if (draft.subject.isBlank()) {
            val value = raw.replace(Regex("""(?iu)^(?:t[aá]rgy|subject)\s*[:=-]?\s*"""), "").trim()
            if (value.isEmpty() || value.length > 160 || value.contains('\n'))
                return GmailDraftNext.NotRelated
            return GmailDraftNext.Updated(draft.copy(subject = value))
        }
        val value = raw.replace(Regex("""(?iu)^(?:sz[oö]veg|[uü]zenet|message|body)\s*[:=-]?\s*"""), "").trim()
        if (value.isEmpty() || value.length > 4000) return GmailDraftNext.NotRelated
        return GmailDraftNext.Complete(GmailOperation.Send(
            recipient = draft.recipient, subject = draft.subject,
            body = value, actuallySend = draft.actuallySend
        ))
    }
}
