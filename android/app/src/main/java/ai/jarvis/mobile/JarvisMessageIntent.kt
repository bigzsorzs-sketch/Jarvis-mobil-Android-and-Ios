package ai.jarvis.mobile

/**
 * Reconstruct every message field from the user's own explicit instruction,
 * never from model-supplied recipient/text. Messages are only prepared for
 * review in the target app; this parser never sends a message.
 */
internal object JarvisMessageIntent {
    private val hu = Regex(
        """(?iu)^(?:írj|küldj|üzenj)\s+(?:egy\s+)?(whats\s*app(?:[- ]?on)?|messenger(?:en|ben)?)\s+(?:üzenetet\s+)?(?:a\s+|az\s+)?(.+?)\s*,?\s+(?:hogy|szöveggel)\s+(.+)$"""
    )
    private val en = Regex(
        """(?iu)^(?:send|write)\s+(?:a\s+)?(whatsapp|messenger)\s+message\s+to\s+(.+?)\s+(?:saying|that\s+says)\s+(.+)$"""
    )

    fun parse(original: String): OutgoingMessage? {
        val command = original.trim()
            .replace(Regex("""(?iu)^(?:(?:hey|hej|szia|ok|okay)\s+)?jarvis\s*[,!:]?\s*"""), "")
            .replace(Regex("""(?iu)^k[eé]rlek\s+"""), "")
        val match = hu.matchEntire(command) ?: en.matchEntire(command) ?: return null
        val platform = when {
            match.groupValues[1].replace(" ", "").lowercase().startsWith("whats") ->
                MessengerPlatform.WHATSAPP
            match.groupValues[1].lowercase().startsWith("messenger") -> MessengerPlatform.MESSENGER
            else -> return null
        }
        val recipient = match.groupValues[2].trim().trimEnd(',', ' ', '.')
        val body = match.groupValues[3].trim()
        if (recipient.isBlank() || recipient.length > 70 || body.isBlank() || body.length > 1500) return null
        if (listOf(recipient, body).any { text ->
                text.any { ch -> ch == '\n' || ch == '\r' || ch == '\u0000' }
            }) return null
        // Reject multi-recipient ambiguity, not conjunctions inside a message.
        if (Regex("""(?iu)\s+(?:és|and|meg)\s+""").containsMatchIn(recipient)) return null
        return OutgoingMessage(platform, recipient, body)
    }

    /** The AI can choose only an app already expressly named by the user. */
    fun validateAiProposal(platform: String, original: String): OutgoingMessage? {
        val requested = parse(original) ?: return null
        val selected = when (platform) {
            "whatsapp" -> MessengerPlatform.WHATSAPP
            "messenger" -> MessengerPlatform.MESSENGER
            else -> return null
        }
        return requested.takeIf { it.platform == selected }
    }
}
