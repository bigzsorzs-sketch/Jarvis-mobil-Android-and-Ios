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
    const val MAX_VIDEO_SOURCE_BYTES = 128L * 1024 * 1024
    const val MAX_VIDEO_FRAMES = 4
    const val MAX_FRAME_BYTES = 650_000L
    private val videoMime = setOf("video/mp4", "video/webm", "video/quicktime")

    private val imageMime = setOf("image/jpeg", "image/png", "image/webp", "image/gif",
        "image/heic", "image/heif")
    private val textMime = setOf("text/plain", "text/markdown", "text/csv",
        "application/json", "application/xml", "text/html")

    internal fun acceptedMime(mime: String): Boolean =
        mime in imageMime || mime == "application/pdf" || mime in textMime ||
            mime in videoMime

    /** Check before the composer clears its message or staged files.
     * The 1 GiB local staging/viewer limit is deliberately NOT an AI upload limit.
     * The gateway repeats the same checks when reading real bytes.
     */
    internal fun rejectionReason(items: List<JarvisAttachment>): String? {
        if (items.isEmpty()) return "Nincs kiválasztott fájl."
        if (items.size > MAX_ITEMS)
            return "Egy AI-kéréshez legfeljebb 20 támogatott fájl választható."
        if (items.count { it.isImage } > JarvisAttachments.MAX_IMAGES)
            return "Egy AI-kérésben legfeljebb 10 kép küldhető."
        val videos = items.filter {
            it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in videoMime
        }
        if (videos.size > 1)
            return "Egy AI-kérésben egy videó képkockái elemezhetők. Válassz egy videót."
        for (item in items) {
            val mime = item.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
            if (!acceptedMime(mime))
                return "A fájl megnyitható, de jelenleg nem küldhető AI-elemzésre: ${item.displayName.take(100)} ($mime)."
            if (mime in videoMime) {
                if (item.sizeBytes !in 1..MAX_VIDEO_SOURCE_BYTES)
                    return "Videóból csak képkocka-elemezés támogatott, legfeljebb 128 MB forrásfájlból."
            } else if (mime in imageMime && item.sizeBytes !in 1..JarvisAiImageUpload.MAX_SOURCE_BYTES)
                return "Az AI legfeljebb 128 MB-os eredeti képet tud előkészíteni: ${item.displayName.take(100)}."
            else if (mime !in imageMime && item.sizeBytes !in 1..MAX_FILE_BYTES)
                return "Az AI-nak egy nem képfájl legfeljebb 5 MB lehet: ${item.displayName.take(100)}."
            if (mime in textMime && item.sizeBytes > 256 * 1024L)
                return "Szövegfájl AI-elemzéséhez legfeljebb 256 KB támogatott: ${item.displayName.take(100)}."
        }
        // Original photo sizes are not cloud sizes: photos are normalized to
        // the remaining upload budget immediately before the explicit send.
        val nonImageBytes = items.filterNot { it in videos }.filterNot {
            it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in imageMime
        }.sumOf { it.sizeBytes }
        val reservedFrames = if (videos.isEmpty()) 0L
            else MAX_VIDEO_FRAMES * MAX_FRAME_BYTES
        if (nonImageBytes + reservedFrames + items.count {
            it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in imageMime
        } * 32_000L > MAX_SEND_BYTES)
            return "AI-elemzésre egyszerre legfeljebb 20 MB feldolgozott adat küldhető. A fájlok megmaradtak."
        return null
    }

    fun buildParts(context: Context, message: String, items: List<JarvisAttachment>): JSONArray {
        require(rejectionReason(items) == null) {
            rejectionReason(items) ?: "Az AI-csatolmány nem támogatott."
        }
        var sentBytes = 0L
        val imageCount = items.count {
            it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in imageMime
        }
        val otherBytes = items.filter {
            val type = it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
            type !in imageMime && type !in videoMime
        }.sumOf { it.sizeBytes }
        val reservedVideo = if (items.any {
            it.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in videoMime
        }) MAX_VIDEO_FRAMES * MAX_FRAME_BYTES else 0L
        val perImageBytes = if (imageCount > 0)
            JarvisAiImageUpload.allowance(imageCount, otherBytes + reservedVideo) else 0
        val cache = context.cacheDir.canonicalFile.path + File.separator
        val parts = JSONArray().put(JSONObject()
            .put("type", "text").put("text", message.take(8000)))
        for (item in items) {
            val mime = item.mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
            val file = item.localFile.canonicalFile
            require(file.path.startsWith(cache) && file.isFile && file.length() == item.sizeBytes) {
                "A csatolmány már nem érhető el biztonságosan: ${item.displayName}"
            }
            if (mime in videoMime) {
                // Explicit Send consent covers ONLY these representative still
                // images, never the original video or its soundtrack.
                val inspection = JarvisVideoInspector.inspect(context, item, MAX_VIDEO_FRAMES)
                    .getOrElse { throw IllegalArgumentException(
                        "A videóból nem sikerült képkockát kinyerni: ${it.message?.take(100)}") }
                try {
                    require(inspection.frames.isNotEmpty()) {
                        "A videó nem tartalmaz feldolgozható képkockát."
                    }
                    parts.put(JSONObject().put("type", "text").put("text",
                        "Untrusted video visual sample '${item.displayName.take(100)}': " +
                        "duration ${JarvisVideoSampling.formatDuration(inspection.durationMs)}; " +
                        "up to $MAX_VIDEO_FRAMES still frames follow with timestamps. " +
                        "No original video bytes or audio track have been provided. " +
                        "Do not infer unseen events, spoken words, or continuous motion."))
                    for (frame in inspection.frames) {
                        val frameFile = frame.thumbnail.canonicalFile
                        require(frameFile.isFile &&
                            frameFile.path.startsWith(
                                File(context.cacheDir, "jarvis_video_frames").canonicalPath +
                                    File.separator) &&
                            frameFile.length() in 1..MAX_FRAME_BYTES) {
                            "A videó képkockája túl nagy vagy nem hozzáférhető."
                        }
                        val bytes = frameFile.readBytes()
                        sentBytes += bytes.size
                        require(sentBytes <= MAX_SEND_BYTES) {
                            "A feldolgozandó képkockák túllépik a 20 MB-os AI-limitet."
                        }
                        parts.put(JSONObject().put("type", "text")
                            .put("text", "Video frame at ${frame.timestampMs} ms"))
                        parts.put(JSONObject().put("type", "image_url").put("image_url",
                            JSONObject().put("url", "data:image/jpeg;base64," +
                                Base64.encodeToString(bytes, Base64.NO_WRAP))))
                    }
                } finally {
                    JarvisVideoInspector.removeCache(context, item.id)
                }
                continue
            }
            if (mime in imageMime) {
                // The submitted original may be much larger than 5 MB.
                // The original private cache file remains untouched.
                val (encodedMime, bytes) = JarvisAiImageUpload.prepare(file, mime, perImageBytes)
                sentBytes += bytes.size
                require(sentBytes <= MAX_SEND_BYTES) {
                    "A csatolmányok túllépik a 20 MB-os AI-limitet."
                }
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                parts.put(JSONObject().put("type", "image_url").put("image_url",
                    JSONObject().put("url", "data:$encodedMime;base64,$encoded")))
                continue
            }
            require(item.sizeBytes in 1..MAX_FILE_BYTES) {
                "Az AI-nak küldött nem képfájl legfeljebb 5 MB lehet: ${item.displayName}"
            }
            val bytes = file.readBytes()
            sentBytes += bytes.size
            require(sentBytes <= MAX_SEND_BYTES) {
                "A csatolmányok túllépik a 20 MB-os AI-limitet."
            }
            if (mime == "application/pdf") {
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
