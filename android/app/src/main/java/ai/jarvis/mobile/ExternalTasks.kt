package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Targeted personal messages require app-level review; never claim to send in background. */
internal enum class MessengerPlatform { WHATSAPP, MESSENGER }
internal data class OutgoingMessage(val platform: MessengerPlatform, val recipient: String, val text: String)

internal object ExternalTasks {
    fun parseMessage(input: String): OutgoingMessage? {
        val app = Regex("(?iu)whats\\s?app(?:[- ]?on)?|messenger(?:en|ben)?").find(input) ?: return null
        val platform = if (app.value.lowercase().startsWith("whats")) MessengerPlatform.WHATSAPP
                       else MessengerPlatform.MESSENGER
        val tail = input.substring(app.range.last + 1).trim()
        val found = Regex("(?iu)^keresd\\s+meg\\s+(?:a\\s+|az\\s+)?(.+?)(?:\\s+és\\s+(?:írj|küldj|üzenj).*)?$")
            .find(tail)
        val forward = Regex("(?iu)^(?:nak|nek|ra|re)?\\s*(?:a\\s+|az\\s+)?(.+?)\\s+(?:hogy|szöveg|üzenet)\\s+(.+)$")
            .find(tail)
        val target = when {
            found != null -> found.groupValues[1].trim()
            forward != null -> forward.groupValues[1].trim()
            else -> return null
        }.trimEnd('.', '!', '?').trim()
        val body = when {
            forward != null -> forward.groupValues[2].trim()
            found != null -> {
                val full = Regex("(?iu)\\b(?:hogy|szöveg)\\s+(.+)$").find(input)
                full?.groupValues?.get(1)?.trim().orEmpty()
            }
            else -> ""
        }
        if (target.isBlank() || target.length > 70 || body.length > 1500) return null
        return OutgoingMessage(platform, target, body)
    }

    fun openWhatsapp(context: Context, phone: String, body: String): Boolean {
        val digits = phone.removePrefix("+")
        if (!digits.matches(Regex("[1-9][0-9]{7,14}"))) return false
        val url = "https://wa.me/" + digits +
            if (body.isBlank()) "" else "?text=" + Uri.encode(body)
        return openSystemIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun openMessengerShare(context: Context, body: String): Boolean {
        if (body.isBlank()) {
            val intent = context.packageManager.getLaunchIntentForPackage("com.facebook.orca") ?: return false
            return openSystemIntent(context, intent)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            setPackage("com.facebook.orca")
        }
        return openSystemIntent(context, send)
    }

    const val UC_OFFICIAL_SIGNIN = "https://www.gov.uk/sign-in-universal-credit"
    fun openUcInBrowser(context: Context): Boolean {
        val uri = Uri.parse(UC_OFFICIAL_SIGNIN)
        val chrome = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome")
        return openSystemIntent(context, chrome) ||
            openSystemIntent(context, Intent(Intent.ACTION_VIEW, uri))
    }

    fun parseUcJournal(input: String): Boolean {
        val normalized = PeopleDirectory.normalized(input)
        val uc = Regex("\\b(?:uc|universal credit)\\b").containsMatchIn(normalized)
        return uc && listOf("journal", "uzenet", "fiok", "account").any { it in normalized }
    }

    fun parseGmailSearch(input: String): String? {
        val matched = Regex("(?iu)^(?:keresd\\s+meg|keresd|nézd\\s+meg)\\s+(.+?)\\s+(?:emailt|e-mailt|levelet)\\b.*$")
            .find(input.trim()) ?: return null
        return matched.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 180 }
    }

    /** Handoff to Gmail's web search, not an API search or automated reply. */
    fun openGmailSearch(context: Context, query: String): Boolean {
        val url = "https://mail.google.com/mail/u/0/#search/" + Uri.encode(query)
        return openSystemIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
