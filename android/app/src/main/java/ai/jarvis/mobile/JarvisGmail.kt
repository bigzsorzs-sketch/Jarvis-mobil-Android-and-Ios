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
    data class Read(val sender: String? = null, val unreadOnly: Boolean = true) : GmailOperation
    data class Send(val recipient: String, val subject: String, val body: String,
                    val actuallySend: Boolean) : GmailOperation
    data class Reply(val sender: String?, val body: String) : GmailOperation
    data class Incomplete(val reason: String) : GmailOperation
}

internal data class GmailRequest(val instruction: String, val operation: GmailOperation)
internal data class GmailResult(val status: String, val speak: String? = null)

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
                    .replace(Regex("""\s+"""), " ").trim().ifBlank { null }
                if (body.isBlank()) GmailOperation.Incomplete("Mondd meg a válasz szövegét: válaszolj az utolsó emailre, hogy ...")
                else GmailOperation.Reply(sender, body)
            }
            listOf("olvasd", "olvass", "felolvas", "read ", "tell me").any { n.startsWith(it) } -> {
                val sender = Regex("""(?iu)(?:-t[oő]l|t[oő]l|from)\s+([\p{L} .'-]{2,70})""")
                    .find(command)?.groupValues?.get(1)?.trim()
                GmailOperation.Read(sender, !listOf("utolso", "legutobbi", "last email", "latest email").any { it in n })
            }
            listOf("van uj", "jott uj", "mennyi uj", "hany uj", "check ", "any new", "how many unread").any { n.startsWith(it) } ->
                GmailOperation.Check
            listOf("kuldd", "kuldj", "irj", "keszits", "send", "write", "compose").any { n.startsWith(it) } -> {
                val actuallySend = listOf("kuldd", "kuldj", "send").any { n.startsWith(it) }
                val subject = Regex("""(?iu)\b(?:t[aá]rgy|subject)\s*[: ]\s*(.+?)(?=\s+(?:[uü]zenet|sz[oö]veg|message|body)\b|$)""")
                    .find(command)?.groupValues?.get(1)?.trim().orEmpty()
                val body = Regex("""(?iu)\b(?:[uü]zenet|sz[oö]veg|message|body)\s*[: ]\s*(.+)$""")
                    .find(command)?.groupValues?.get(1)?.trim().orEmpty()
                val head = Regex("""(?iu)\b(?:t[aá]rgy|subject|[uü]zenet|sz[oö]veg|message|body)\b""")
                    .find(command)?.range?.first?.let { command.substring(0, it) } ?: command
                val recipient = head.replace(Regex("""(?iu)^(?:k[uü]ldj|k[uü]ldd|[ií]rj|k[eé]sz[ií]ts|send|write|compose)\s+(?:el\s+)?"""), "")
                    .replace(Regex("""(?iu)^(?:egy\s+)?(?:e-?mailt?|levelet|mail|an?\s+email)\s*(?:a\s+|az\s+|to\s+)?"""), "")
                    .trim().removeSuffix("nek").removeSuffix("nak").trim()
                if (recipient.isEmpty() || body.isEmpty() || subject.isEmpty()) {
                    GmailOperation.Incomplete("Add meg a címzettet, a tárgyat és a szöveget. Példa: küldj emailt anna@example.com tárgy Találkozó üzenet Holnap tízkor.")
                } else GmailOperation.Send(recipient, subject, body, actuallySend)
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
    fun resolve(context: Context, named: String): String {
        val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""").find(named)?.value
        if (email != null && email.equals(named.trim(), true)) return email
        if (email != null) error("Többértelmű címzett. Add meg önállóan az emailcímet.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) error("Engedélyezd a telefon Névjegyek hozzáférését, vagy diktálj pontos emailcímet.")
        val key = named.trim().lowercase(Locale.ROOT)
        if (key.isBlank() || key.length > 100) error("A címzett neve hiányzik.")
        val addresses = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0).orEmpty()
                val label = cursor.getString(1).orEmpty().lowercase(Locale.ROOT)
                if (label == key && address.matches(Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")))
                    addresses.add(address)
            }
        }
        return when (addresses.size) {
            1 -> addresses.single()
            0 -> error("Nem találtam egyértelmű emailcímet ehhez a névjegyhez. Add meg a teljes emailcímet.")
            else -> error("A névjegyhez több emailcím tartozik. Mondd meg a pontos címet.")
        }
    }
}

internal object JarvisGmailApi {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    private val emailPattern = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

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
        val body = plainText(payload) ?: o.optString("snippet")
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

    private fun mime(to: String, subject: String, text: String, previous: GmailMessage? = null): String {
        require(emailPattern.matches(to))
        require(text.isNotBlank() && text.length <= 10000)
        val lines = StringBuilder()
        lines.append("To: ").append(to).append("\r\n")
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
                val recipient = GmailRecipients.resolve(context, operation.recipient)
                if (!operation.actuallySend) {
                    GmailResult("Előkészítve: " + recipient + ", tárgy: " + operation.subject +
                        ". Küldés nem történt. Mondd: küldj emailt ... a teljes szöveggel.")
                } else {
                    val raw = mime(recipient, operation.subject, operation.body)
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
                val raw = mime(recipient, subject, operation.body, previous)
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
