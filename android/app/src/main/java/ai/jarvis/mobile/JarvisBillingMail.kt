package ai.jarvis.mobile

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal object BillingPdf {
    fun create(context: Context, state: BillingState, invoice: BillingInvoice,
               language: JarvisLanguage): File {
        val client = requireNotNull(state.clients.find { it.id == invoice.clientId })
        val company = state.profile
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 13f
            }
            fun row(value: String, y: Float, size: Float = 13f) {
                pen.textSize = size
                canvas.drawText(value.replace("\n", " ").take(80), 38f, y, pen)
            }
            val word = { key: String -> BillingStrings.get(language, key) }
            row(word(if (invoice.kind == "quote") "quote" else "invoice"), 62f, 26f)
            row(invoice.number, 91f, 17f)
            row(word("supplier") + ": " + company.business, 132f)
            company.address.lines().take(4).forEachIndexed { i, part ->
                row(part, 154f + i * 18f)
            }
            row(word("customer") + ": " + client.name, 258f, 16f)
            client.address.lines().take(4).forEachIndexed { i, part ->
                row(part, 281f + i * 18f)
            }
            row(word("issued") + ": " + invoice.issueDate, 383f)
            row(word("due") + ": " + invoice.dueDate, 404f)
            row(word("description") + ":", 438f, 15f)
            var line = ""
            var y = 461f
            invoice.description.replace("\n", " ").split(" ").forEach { part ->
                if (line.length + part.length > 70) {
                    row(line, y); y += 19f; line = ""
                }
                line = (line + " " + part).trim()
            }
            if (line.isNotBlank()) row(line, y)
            row(word("subtotal") + ": " + BillingValidation.money(invoice.netPence), 592f, 16f)
            if (invoice.vatPercent > 0) {
                row("VAT " + invoice.vatPercent + "%: " + BillingValidation.money(invoice.vatPence), 624f)
                row("VAT No: " + company.vatNumber, 650f)
            }
            row(word("total") + ": " + BillingValidation.money(invoice.totalPence), 692f, 20f)
            if (company.bank.isNotBlank()) {
                row(word("paymentDetails") + ": " + company.bank, 746f, 12f)
            }
            doc.finishPage(page)
            val folder = File(context.cacheDir, "invoices").apply { mkdirs() }
            val file = File(folder, invoice.number.replace(Regex("[^A-Za-z0-9-]"), "") + ".pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            return file
        } finally { doc.close() }
    }

    fun share(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "ai.jarvis.mobile.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "PDF"))
        true
    }.getOrDefault(false)
}

/**
 * Real SMTP transmission, not ACTION_SENDTO. Works with a user configured
 * Gmail App Password (2FA required), over hostname-verified implicit TLS.
 * Never log the credentials or claim inbox delivery from SMTP acceptance.
 */
internal object BillingSmtp {
    fun send(sender: String, password: String, recipient: String, subject: String,
             message: String, attachment: File) {
        require(BillingValidation.email(sender) && BillingValidation.email(recipient))
        require(password.isNotEmpty() && attachment.isFile && attachment.length() in 1..3_000_000)
        val socket = Socket()
        socket.connect(InetSocketAddress("smtp.gmail.com", 465), 15_000)
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(socket, "smtp.gmail.com", 465, true) as SSLSocket
        ssl.use {
            ssl.soTimeout = 20_000
            val settings = ssl.sslParameters
            settings.endpointIdentificationAlgorithm = "HTTPS"
            ssl.sslParameters = settings
            ssl.startHandshake()
            val input = BufferedReader(InputStreamReader(ssl.inputStream, StandardCharsets.US_ASCII))
            val output = OutputStreamWriter(ssl.outputStream, StandardCharsets.US_ASCII)
            fun response(expected: Int) {
                var status: Int
                var more: Boolean
                do {
                    val line = input.readLine() ?: error("SMTP closed")
                    require(line.length >= 4 && line.substring(0, 3).toIntOrNull() != null) {
                        "Malformed SMTP response"
                    }
                    status = line.substring(0, 3).toInt()
                    more = line[3] == '-'
                } while (more)
                check(status == expected) { "SMTP rejected request" }
            }
            fun command(line: String, expected: Int) {
                output.write(line + "\r\n"); output.flush(); response(expected)
            }
            response(220)
            command("EHLO jarvis.local", 250)
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(sender.toByteArray(StandardCharsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(StandardCharsets.UTF_8)), 235)
            command("MAIL FROM:<" + sender + ">", 250)
            command("RCPT TO:<" + recipient + ">", 250)
            command("DATA", 354)
            val boundary = "jarvis-" + UUID.randomUUID().toString()
            val base64 = Base64.getMimeEncoder(76, "\r\n".toByteArray(StandardCharsets.US_ASCII))
            val encodedSubject = "=?UTF-8?B?" +
                Base64.getEncoder().encodeToString(subject.toByteArray(StandardCharsets.UTF_8)) + "?="
            val body = base64.encodeToString(message.toByteArray(StandardCharsets.UTF_8))
            val pdf = base64.encodeToString(attachment.readBytes())
            val email = buildString {
                append("From: <" + sender + ">\r\nTo: <" + recipient + ">\r\n")
                append("Subject: " + encodedSubject + "\r\nMIME-Version: 1.0\r\n")
                append("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"\r\n\r\n")
                append("--" + boundary + "\r\nContent-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n\r\n" + body + "\r\n")
                append("--" + boundary + "\r\nContent-Type: application/pdf; name=\"" + attachment.name + "\"\r\n")
                append("Content-Disposition: attachment; filename=\"" + attachment.name + "\"\r\n")
                append("Content-Transfer-Encoding: base64\r\n\r\n" + pdf + "\r\n")
                append("--" + boundary + "--\r\n")
            }
            // Base64-encoded MIME contains no line with a lone period.
            output.write(email); output.write(".\r\n"); output.flush()
            response(250)
            runCatching { command("QUIT", 221) }
        }
    }
}

internal object BillingSend {
    /**
     * Call from a background coroutine. A reserved send is never retried
     * automatically, because a timeout after DATA may still mean accepted mail.
     */
    fun send(context: Context, repo: BillingRepository, id: String,
             language: JarvisLanguage): String {
        repo.refresh()
        val snapshot = repo.state
        val item = snapshot.invoices.find { it.id == id }
            ?: return BillingStrings.get(language, "missingInvoice")
        val client = snapshot.clients.find { it.id == item.clientId }
            ?: return BillingStrings.get(language, "missingClient")
        if (item.kind != "invoice") return BillingStrings.get(language, "quoteCannotSend")
        if (item.status != "draft") return BillingStrings.get(language, "alreadyProcessed")
        if (!BillingValidation.email(client.email) || client.address.isBlank() ||
            snapshot.profile.business.isBlank() || snapshot.profile.address.isBlank())
            return BillingStrings.get(language, "missingDetails")
        if (item.vatPercent > 0 && snapshot.profile.vatNumber.isBlank())
            return BillingStrings.get(language, "missingVat")
        val (sender, password) = repo.mailAccount()
        if (!BillingValidation.email(sender) || password.isEmpty())
            return BillingStrings.get(language, "missingSmtp")
        if (!repo.status(id, setOf("draft"), "sending"))
            return BillingStrings.get(language, "alreadyProcessed")
        return try {
            val pdf = BillingPdf.create(context, snapshot, item, language)
            BillingSmtp.send(sender, password, client.email,
                BillingStrings.get(language, "invoice") + " " + item.number,
                BillingStrings.get(language, "emailBody") + "\n" + item.number, pdf)
            repo.status(id, setOf("sending"), "unpaid", Instant.now().toString())
            BillingStrings.get(language, "smtpAccepted") + " " + item.number
        } catch (_: Exception) {
            repo.status(id, setOf("sending"), "uncertain")
            BillingStrings.get(language, "sendUncertain")
        }
    }
}
