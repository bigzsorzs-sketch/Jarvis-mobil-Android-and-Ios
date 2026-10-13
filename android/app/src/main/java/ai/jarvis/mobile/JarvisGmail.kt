package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Base64
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/**
 * Mail data is fetched on the phone directly from Gmail with a short-lived
 * Google Identity access token. Tokens never leave the phone except directly to Gmail.
 * Only an explicit read-aloud request may pass message text to the separately
 * consented cloud speech provider. Content is never passed to the reasoning model.
 */
internal sealed interface GmailOperation {
    data object Connect : GmailOperation
    data object Disconnect : GmailOperation
    data object Check : GmailOperation
    data object ListUnread : GmailOperation
    data class ResolveRecipient(val name: String, val actuallySend: Boolean) : GmailOperation
    data class Read(val sender: String? = null, val unreadOnly: Boolean = true) : GmailOperation
    data class Send(val recipient: String, val subject: String, val body: String,
                    val actuallySend: Boolean) : GmailOperation
    data class Reply(val sender: String?, val body: String) : GmailOperation
    data class Incomplete(val reason: String) : GmailOperation
}

internal data class GmailRequest(val instruction: String, val operation: GmailOperation)
internal data class GmailResult(
    val status: String,
    val speak: String? = null,
    val recipientCandidates: List<String> = emptyList()
)

internal object JarvisGmailCommands {
    private fun normal(s: String): String = Normalizer.normalize(
        s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ").trim()

    fun parse(original: String): GmailRequest? {
        val command = original.trim().replace(
            Regex("""(?iu)^(?:(?:hey|szia)\s+)?jarvis[\s,:!]*"""), "").trim()
        val n = normal(command)
        if (command.isBlank() || command.length > 6000) return null
        val mentionsMail = Regex("""\b(emails?|e mail|gmail|mail|level|levelek|levelet|levelem|leveleim|levelre|leveled|level[eé]re)\b""")
            .containsMatchIn(n) || listOf("emailt", "emailem", "emailje", "emailre", "emailja", "emailjeim", "gmail").any { it in n }
        if (!mentionsMail) return null
        val operation: GmailOperation = when {
            listOf("levalaszt", "disconnect gmail", "gmail kijelentkez", "gmail fiok eltavolit").any { it in n } ->
                GmailOperation.Disconnect
            listOf("csatlakozt", "kapcsolod", "connect gmail", "gmail bejelentkez").any { it in n } ->
                GmailOperation.Connect
            (listOf("valaszolj", "valaszoljal", "reply", "answer").any { n.startsWith(it) }) -> {
                val body = Regex("""(?iu)\b(?:hogy|[uü]zenet|sz[oö]veg|saying|message)\s*[: ,]?\s*(.+)$""")
                    .find(command)?.groupValues?.get(1)?.trim().orEmpty()
                val head = Regex("""(?iu)\b(?:hogy|[uü]zenet|sz[oö]veg|saying|message)\b""")
                    .find(command)?.range?.first?.let { command.substring(0, it) } ?: command
                val sender = head.replace(Regex("""(?iu)^(?:v[aá]laszolj|v[aá]laszolj[aá]l|reply|answer)\s*"""), "")
                    .replace(Regex("""(?iu)\b(?:az?|the|utols[oó]|legut[oó]bbi|last|latest|emailj[eé]re|emailre|emailj[aá]ra|emailre|level[eé]re|level[eé]nek|mail|email|gmail|from|to)\b"""), " ")
                    .replace(Regex("""[,.:;!?]"""), " ")
                    .replace(Regex("""\s+"""), " ").trim().ifBlank { null }
                if (body.isBlank()) GmailOperation.Incomplete("Mondd meg a válasz szövegét: válaszolj az utolsó emailre, hogy ...")
                else GmailOperation.Reply(sender, body)
            }
            listOf("olvasd", "olvass", "felolvas", "read ", "tell me").any { n.startsWith(it) } -> {
                val sender = Regex("""(?iu)(?:-t[oő]l|t[oő]l|from)\s+([\p{L} .'-]{2,70})""")
                    .find(command)?.groupValues?.get(1)?.trim()
                GmailOperation.Read(sender, !listOf("utolso", "legutobbi", "last email", "latest email").any { it in n })
            }
            // Describe unread emails using Gmail metadata rather than forwarding
            // the question to a generic AI model that cannot access the inbox.
            (
                (listOf("milyen uj", "mik az uj", "mik jottek", "sorold fel az uj",
                    "mutasd az uj", "list unread", "which new", "what new",
                    "show unread", "show new").any { n.startsWith(it) } &&
                    listOf("email", "gmail", "level", "leve").any { it in n }) ||
                n == "uj emailek" || n == "uj levelek"
            ) -> GmailOperation.ListUnread
            listOf("van uj", "jott uj", "mennyi uj", "hany uj", "check ",
                "any new", "how many unread", "hany olvasatlan").any { n.startsWith(it) } ->
                GmailOperation.Check
            listOf("kuld ", "kuldd ", "kuldj ", "irj ", "keszits ", "send ", "write ", "compose ").any { n.startsWith(it) } -> {
                val actuallySend = listOf("kuld ", "kuldd ", "kuldj ", "send ").any { n.startsWith(it) }
                val subject = Regex("""(?iu)\b(?:t[aá]rgy|subject)\s*[: ]\s*(.+?)(?=\s+(?:[uü]zenet|sz[oö]veg|message|body)\b|$)""")
                    .find(command)?.groupValues?.get(1)?.trim().orEmpty()
                val body = Regex("""(?iu)\b(?:[uü]zenet|sz[oö]veg|message|body)\s*[: ]\s*(.+)$""")
                    .find(command)?.groupValues?.get(1)?.trim().orEmpty()
                val head = Regex("""(?iu)\b(?:t[aá]rgy|subject|[uü]zenet|sz[oö]veg|message|body)\b""")
                    .find(command)?.range?.first?.let { command.substring(0, it) } ?: command
                val recipient = head.replace(Regex("""(?iu)^(?:k[uü]ldj|k[uü]ldd|k[uü]ld|[ií]rj|k[eé]sz[ií]ts|send|write|compose)\s+(?:el\s+)?"""), "")
                    .replace(Regex("""(?iu)^(?:egy\s+)?(?:e-?mailt?|levelet|mail|an?\s+email)\s*(?:a\s+|az\s+|to\s+)?"""), "")
                    .trim().removeSuffix("nek").removeSuffix("nak").trim()
                when {
                    recipient.isBlank() -> GmailOperation.Incomplete(
                        "Mondd meg a címzett nevét vagy emailcímét.")
                    subject.isBlank() && body.isBlank() -> GmailOperation.ResolveRecipient(
                        recipient, actuallySend)
                    subject.isBlank() || body.isBlank() -> GmailOperation.Incomplete(
                        "Add meg a tárgyat és a levél szövegét, vagy csak a címzettet, és sorban megkérdezem.")
                    else -> GmailOperation.Send(recipient, subject, body, actuallySend)
                }
            }
            else -> return null
        }
        return GmailRequest(original, operation)
    }
}

internal data class GmailMessage(
    val id: String, val threadId: String, val from: String,
    val replyTo: String, val subject: String, val body: String,
    val messageId: String, val references: String
)

/** Restricted recipients: addresses from the user command or unique Android contact. */
internal object GmailRecipients {
    private val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /** A named address is resolved from past Gmail headers, NEVER Android Contacts. */
    fun resolve(accessToken: String, named: String): String {
        if (email.matches(named.trim())) return named.trim()
        val candidates = JarvisGmailApi.addressesForName(accessToken, named)
        return when (candidates.size) {
            1 -> candidates.single()
            0 -> error("Nem találtam egyértelmű emailcímet a korábbi Gmail-leveleidben. Add meg a címet.")
            else -> error("Több Gmail-cím egyezik a névvel. Add meg a pontos emailcímet, nehogy rossz címzettnek küldjek.")
        }
    }
}

internal object JarvisGmailApi {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    private val emailPattern = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /** Search real Gmail message headers from both received AND sent messages. */
    fun addressesForName(token: String, name: String): List<String> {
        val clean = name.trim().removeSuffix("nek").removeSuffix("nak").trim()
        require(clean.length in 2..70 &&
            clean.matches(Regex("""[\p{L}\p{M} .'-]+"""))) {
            "Ellenőrizd a címzett nevét, vagy add meg a pontos emailcímet."
        }
        val safe = clean.replace(Regex("""[\r\n"]"""), "").trim()
        val query = "{from:\"" + safe + "\" to:\"" + safe + "\"}"
        val messageIds = ids(token, query, 80)
        val candidates = linkedSetOf<String>()
        for (id in messageIds) {
            require(id.matches(Regex("""[A-Za-z0-9_\-]{1,120}""")))
            val payload = request(token, "/messages/" + id +
                "?format=metadata&metadataHeaders=From&metadataHeaders=To")
                .optJSONObject("payload") ?: continue
            val headers = payload.optJSONArray("headers") ?: continue
            for (index in 0 until headers.length()) {
                val header = headers.getJSONObject(index)
                if (header.optString("name").equals("From", true) ||
                    header.optString("name").equals("To", true)) {
                    candidates.addAll(GmailMailboxHeader.matchAddress(
                        safe, header.optString("value")))
                }
            }
        }
        return candidates.toList().sorted()
    }

    private fun request(token: String, path: String, body: JSONObject? = null): JSONObject {
        require(token.isNotBlank())
        require(path.startsWith("/") && !path.contains("://"))
        val url = URL(BASE + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(when (code) {
                    401 -> "A Gmail-munkamenet lejárt. Csatlakozz újra a Google-fiókhoz."
                    403 -> "A Gmail hozzáférése tiltott. Ellenőrizd az OAuth-jogosultságot és a Gmail API engedélyezését."
                    429 -> "A Gmail túl sok kérést kapott. Próbáld újra később."
                    else -> "Gmail API hiba (HTTP " + code + "). Nem igazolt a végrehajtás."
                })
            }
            return JSONObject(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun safeHeader(s: String): String = s.replace(Regex("""[\r\n\p{Cntrl}]"""), " ").trim().take(500)
    private fun encodedHeader(s: String): String {
        val v = safeHeader(s)
        return if (v.all { it.code in 32..126 }) v
            else "=?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString(v.toByteArray(StandardCharsets.UTF_8)) + "?="
    }

    private fun decode(s: String): String = runCatching {
        String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
    }.getOrDefault("")

    private fun plainText(part: JSONObject): String? {
        val mime = part.optString("mimeType")
        if (mime == "text/plain") {
            val value = decode(part.optJSONObject("body")?.optString("data").orEmpty())
            if (value.isNotBlank()) return value
        }
        val parts = part.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val candidate = plainText(parts.getJSONObject(i))
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }

    // Many modern mailers send HTML-only messages. Convert locally, never
    // fetch embedded images or follow remote resources.
    private fun htmlText(part: JSONObject): String? {
        if (part.optString("mimeType") == "text/html") {
            val html = decode(part.optJSONObject("body")?.optString("data").orEmpty())
            if (html.isNotBlank()) {
                return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
                    .toString().trim().takeIf { it.isNotBlank() }
            }
        }
        val parts = part.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val candidate = htmlText(parts.getJSONObject(i))
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }

    private fun message(token: String, id: String): GmailMessage {
        require(id.matches(Regex("""[A-Za-z0-9_\-]{1,120}""")))
        val o = request(token, "/messages/" + id + "?format=full")
        val payload = o.optJSONObject("payload") ?: error("Nem olvasható az email.")
        val headers = payload.optJSONArray("headers") ?: JSONArray()
        fun header(name: String): String {
            for (i in 0 until headers.length()) {
                val item = headers.getJSONObject(i)
                if (item.optString("name").equals(name, true)) return item.optString("value")
            }
            return ""
        }
        val body = plainText(payload) ?: htmlText(payload) ?: o.optString("snippet")
        return GmailMessage(
            id, o.optString("threadId"), header("From"), header("Reply-To"),
            header("Subject"), body.take(10000), header("Message-ID"), header("References")
        )
    }

    private fun ids(token: String, query: String, limit: Int = 3): List<String> {
        val o = request(token, "/messages?q=" + encode(query) + "&maxResults=" + limit)
        val arr = o.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
            .filter { it.isNotBlank() }
    }

    private fun mime(from: String, to: String, subject: String, text: String, previous: GmailMessage? = null): String {
        require(emailPattern.matches(from) && emailPattern.matches(to))
        require(text.isNotBlank() && text.length <= 10000)
        val lines = StringBuilder()
        lines.append("From: ").append(from).append("\r\n")
        lines.append("To: ").append(to).append("\r\n")
        lines.append("Date: ").append(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
            java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))).append("\r\n")
        lines.append("Subject: ").append(encodedHeader(subject)).append("\r\n")
        if (previous != null) {
            val originalId = safeHeader(previous.messageId)
            require(originalId.startsWith("<") && originalId.endsWith(">")) {
                "A levél Message-ID adata hiányzik. Nem tudok biztonságosan válaszolni a beszélgetésben."
            }
            lines.append("In-Reply-To: ").append(originalId).append("\r\n")
            lines.append("References: ").append(safeHeader((previous.references + " " + originalId).trim()))
                .append("\r\n")
        }
        lines.append("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n")
        lines.append("Content-Transfer-Encoding: base64\r\n\r\n")
        lines.append(java.util.Base64.getMimeEncoder(76, "\r\n".toByteArray())
            .encodeToString(text.toByteArray(StandardCharsets.UTF_8)))
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(lines.toString().toByteArray(StandardCharsets.UTF_8))
    }

    /** Called from Dispatchers.IO only; success depends on the actual Gmail send response. */
    fun execute(context: Context, accessToken: String, operation: GmailOperation): GmailResult {
        return when (operation) {
            GmailOperation.Connect -> {
                val account = request(accessToken, "/profile").optString("emailAddress")
                GmailResult("Gmail-fiók csatlakoztatva: " + account + ". A hozzáférés visszavonható.")
            }
            GmailOperation.Check -> {
                val data = request(accessToken, "/messages?q=" + encode("in:inbox is:unread") + "&maxResults=20")
                val estimate = data.optInt("resultSizeEstimate", 0)
                GmailResult("A Gmail becslése szerint " + estimate + " olvasatlan beérkező leveled van.")
            }
            GmailOperation.ListUnread -> {
                // Fetch only permitted sender/subject metadata. The actual
                // message body is neither downloaded nor sent to the LLM.
                val found = ids(accessToken, "in:inbox is:unread", 5)
                if (found.isEmpty()) GmailResult("Nincs új, olvasatlan beérkező emailed.")
                else {
                    val descriptions = found.map { id ->
                        require(id.matches(Regex("""[A-Za-z0-9_\-]{1,120}""")))
                        val payload = request(accessToken, "/messages/" + id +
                            "?format=metadata&metadataHeaders=From&metadataHeaders=Subject")
                            .optJSONObject("payload") ?: error("Nem tudom megjeleníteni az email adatait.")
                        val headers = payload.optJSONArray("headers") ?: JSONArray()
                        fun header(name: String): String {
                            for (index in 0 until headers.length()) {
                                val item = headers.getJSONObject(index)
                                if (item.optString("name").equals(name, ignoreCase = true))
                                    return item.optString("value").replace(
                                        Regex("""[\r\n\p{Cntrl}]"""), " ").take(140)
                            }
                            return "ismeretlen"
                        }
                        "Feladó: " + header("From") + ". Tárgy: " + header("Subject")
                    }
                    GmailResult("Lekértem " + found.size + " olvasatlan email feladóját és tárgyát. " +
                        "Megnyitottam az Email oldalt.",
                        descriptions.joinToString("\n\n"))
                }
            }
            is GmailOperation.ResolveRecipient -> {
                val direct = operation.name.trim()
                val candidates = if (emailPattern.matches(direct)) listOf(direct)
                    else addressesForName(accessToken, direct)
                GmailResult(
                    when (candidates.size) {
                        0 -> "Nem találtam ezt a címzettet a korábbi Gmail-leveleidben. Add meg az emailcímet."
                        1 -> "Megtaláltam a címzettet a Gmail-levelezésedben. Mi legyen a tárgy?"
                        else -> "Több emailcím is megfelel. Melyik legyen? " +
                            candidates.take(5).mapIndexed { index, address ->
                                (index + 1).toString() + ": " + address
                            }.joinToString("; ")
                    },
                    recipientCandidates = candidates.take(5)
                )
            }
            is GmailOperation.Read -> {
                val q = (if (operation.unreadOnly) "in:inbox is:unread" else "in:inbox") +
                    if (operation.sender.isNullOrBlank()) "" else " from:" + operation.sender
                val matches = ids(accessToken, q)
                if (matches.isEmpty()) GmailResult("Nem találok megfelelő beérkező emailt.")
                else {
                    val letters = matches.map { message(accessToken, it) }
                    val spoken = letters.joinToString("\n\n") {
                        "Feladó: " + it.from + ". Tárgy: " + it.subject + ". Levél: " + it.body.take(4000)
                    }
                    GmailResult(letters.size.toString() + " emailt lekértem. Felolvasom.", spoken)
                }
            }
            is GmailOperation.Send -> {
                val recipient = GmailRecipients.resolve(accessToken, operation.recipient)
                if (!operation.actuallySend) {
                    GmailResult("Előkészítve: " + recipient + ", tárgy: " + operation.subject +
                        ". Küldés nem történt. Mondd: küldj emailt ... a teljes szöveggel.")
                } else {
                    val from = request(accessToken, "/profile").optString("emailAddress")
                    val raw = mime(from, recipient, operation.subject, operation.body)
                    val response = request(accessToken, "/messages/send", JSONObject().put("raw", raw))
                    require(response.optString("id").isNotBlank()) { "A Gmail nem igazolta az elküldést." }
                    GmailResult("A Gmail elfogadta az emailt. Címzett: " + recipient +
                        ". Azonosító: " + response.optString("id"))
                }
            }
            is GmailOperation.Reply -> {
                val q = if (operation.sender.isNullOrBlank()) "in:inbox"
                    else "in:inbox from:" + operation.sender
                val found = ids(accessToken, q, 10)
                if (found.isEmpty()) return GmailResult("Nem találok megfelelő beérkező emailt. Nem küldtem választ.")
                val previous = found.map { message(accessToken, it) }.firstOrNull {
                    operation.sender.isNullOrBlank() ||
                        Normalizer.normalize(it.from.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                            .replace(Regex("\\p{M}+"), "").contains(
                                Normalizer.normalize(operation.sender.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                                    .replace(Regex("\\p{M}+"), ""))
                } ?: return GmailResult("Nem találtam egyértelmű feladót. Nem küldtem választ.")
                val recipient = emailPattern.find(previous.replyTo.ifBlank { previous.from })?.value
                    ?: return GmailResult("A levél feladójának nincs biztonságosan azonosítható emailcíme.")
                require(previous.threadId.isNotBlank()) { "Hiányzik az eredeti beszélgetés azonosítója." }
                val subject = if (previous.subject.startsWith("Re:", true)) previous.subject else "Re: " + previous.subject
                val from = request(accessToken, "/profile").optString("emailAddress")
                val raw = mime(from, recipient, subject, operation.body, previous)
                val response = request(accessToken, "/messages/send",
                    JSONObject().put("raw", raw).put("threadId", previous.threadId))
                require(response.optString("id").isNotBlank() &&
                    response.optString("threadId") == previous.threadId) {
                    "A Gmail nem igazolta a beszélgetésben küldött választ."
                }
                GmailResult("A Gmail elfogadta a választ az eredeti beszélgetésben. Címzett: " +
                    recipient + ". Azonosító: " + response.optString("id"))
            }
            is GmailOperation.Incomplete -> GmailResult(operation.reason)
            GmailOperation.Disconnect -> GmailResult("A leválasztást a Google engedélykezelője hajtja végre.")
        }
    }
}
