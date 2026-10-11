package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri

internal data class EmailDraft(val recipient: String, val subject: String, val body: String)

/**
 * Email is prepared only. Android's mail application owns recipient review and
 * final SEND; Jarvis never claims that opening the composer delivered a mail.
 */
internal object VoiceEmail {
    fun parse(raw: String): EmailDraft? {
        val input = raw.trim()
        if (!Regex("(?iu)\\b(?:[ií]rj|k[uü]ldj|k[eé]sz[ií]ts)\\s+(?:egy\\s+)?(?:e-?mailt|levelet)\\b")
            .containsMatchIn(input)) return null
        val address = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
            .find(input)?.value.orEmpty()
        val subject = Regex("(?iu)\\bt[aá]rgy\\s*:?\\s*(.+?)(?=\\s+\\b(?:[uü]zenet|sz[oö]veg)\\b|$)")
            .find(input)?.groupValues?.get(1)?.trim().orEmpty()
        val body = Regex("(?iu)\\b(?:[uü]zenet|sz[oö]veg)\\s*:?\\s*(.+)$")
            .find(input)?.groupValues?.get(1)?.trim().orEmpty()
        return EmailDraft(address, subject.take(160), body.take(1500))
    }

    fun openComposer(context: Context, email: EmailDraft): Boolean {
        val target = if (email.recipient.isEmpty()) "mailto:" else "mailto:" + Uri.encode(email.recipient)
        return openSystemIntent(
            context, Intent(Intent.ACTION_SENDTO, Uri.parse(target)).apply {
                if (email.recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email.recipient))
                if (email.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, email.subject)
                if (email.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, email.body)
            }
        )
    }
}
