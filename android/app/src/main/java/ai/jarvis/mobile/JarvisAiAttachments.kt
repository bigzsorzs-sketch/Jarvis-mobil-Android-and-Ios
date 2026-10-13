package ai.jarvis.mobile

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.CodingErrorAction

/**
 * Explicit user-selected attachments for one cloud request.
 * Picking a file only stores it in app-private cache; nothing is uploaded
 * until the user deliberately sends their message.
 */
internal object JarvisAiAttachments {
    const val MAX_ITEMS = 20
    const val MAX_FILE_BYTES = 5L * 1024 * 1024
    const val MAX_SEND_BYTES = 20L * 1024 * 1024

    private val imageMime = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    private val textMime = setOf("text/plain", "text/markdown", "text/csv",
        "application/json", "application/xml", "text/html")

    internal fun acceptedMime(mime: String): Boolean =
        mime in imageMime || mime == "application/pdf" || mime in textMime

    fun buildParts(context: Context, message: String, items: List<JarvisAttachment>): JSONArray {
        require(items.isNotEmpty() && items.size <= MAX_ITEMS) {
            "Egy AI-kéréshez legfeljebb 20 támogatott fájlt csatolhatsz."
        }
        require(items.sumOf { it.sizeBytes } <= MAX_SEND_BYTES) {
            "Az AI-nak egyszerre legfeljebb 20 MB fájl küldhető."
        }
        val cache = context.cacheDir.canonicalFile.path + File.separator
        val parts = JSONArray().put(JSONObject()
            .put("type", "text").put("text", message.take(8000)))
        for (item in items) {
            require(item.sizeBytes in 1..MAX_FILE_BYTES) {
                "Az AI-nak küldött fájl legfeljebb 5 MB lehet: ${item.displayName}"
            }
            val mime = item.mimeType.substringBefore(';').lowercase()
            require(acceptedMime(mime)) {
                "Ehhez a fájltípushoz nincs bekötve AI-elemzés: ${item.displayName} ($mime)"
            }
            val file = item.localFile.canonicalFile
            require(file.path.startsWith(cache) && file.isFile && file.length() == item.sizeBytes) {
                "A csatolmány már nem érhető el biztonságosan: ${item.displayName}"
            }
            val bytes = file.readBytes()
            if (mime in imageMime) {
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                parts.put(JSONObject().put("type", "image_url").put("image_url",
                    JSONObject().put("url", "data:$mime;base64,$encoded")))
            } else if (mime == "application/pdf") {
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                parts.put(JSONObject().put("type", "file").put("file",
                    JSONObject().put("filename", item.displayName)
                        .put("file_data", "data:$mime;base64,$encoded")))
            } else {
                // Plain text is untrusted data; never execute commands from it.
                require(bytes.size <= 256 * 1024) {
                    "Szövegfájl küldésekor maximum 256 KB támogatott: ${item.displayName}"
                }
                val decoded = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                parts.put(JSONObject().put("type", "text").put("text",
                    "Untrusted attachment '${item.displayName}':\n$decoded\nEnd of attachment."))
            }
        }
        return parts
    }
}
