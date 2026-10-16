package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.UUID

internal data class JarvisAttachment(
    val id: String, val displayName: String, val localFile: File,
    val mimeType: String, val sizeBytes: Long, val isImage: Boolean
)

/** Scoped Android content, copied to app-private cache, not to any cloud. */
internal object JarvisAttachments {
    const val MAX_IMAGES = 10
    const val MAX_TOTAL_BYTES = 1_073_741_824L // 1 GiB, aggregate not per file

    // OpenMultipleDocuments("*/*") offers every available provider file, not
    // just this common subset. These mappings restore useful MIME metadata
    // when providers return application/octet-stream or an empty MIME type.
    private val MIME_BY_EXTENSION = mapOf(
        "pdf" to "application/pdf", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "odt" to "application/vnd.oasis.opendocument.text",
        "rtf" to "application/rtf", "txt" to "text/plain",
        "md" to "text/markdown", "csv" to "text/csv",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "json" to "application/json", "xml" to "application/xml",
        "html" to "text/html", "htm" to "text/html",
        "yaml" to "application/yaml", "yml" to "application/yaml",
        "eml" to "message/rfc822", "ics" to "text/calendar",
        "vcf" to "text/vcard", "epub" to "application/epub+zip",
        "zip" to "application/zip", "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar", "gz" to "application/gzip",
        "exe" to "application/vnd.microsoft.portable-executable",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "gif" to "image/gif",
        "heic" to "image/heic", "heif" to "image/heif",
        "bmp" to "image/bmp", "tif" to "image/tiff",
        "tiff" to "image/tiff", "svg" to "image/svg+xml",
        "mp3" to "audio/mpeg", "wav" to "audio/wav",
        "m4a" to "audio/mp4", "aac" to "audio/aac",
        "ogg" to "audio/ogg", "flac" to "audio/flac",
        "opus" to "audio/opus",
        "mp4" to "video/mp4", "mov" to "video/quicktime",
        "mkv" to "video/x-matroska", "webm" to "video/webm",
        "avi" to "video/x-msvideo", "3gp" to "video/3gpp"
    )
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif",
        "bmp", "tif", "tiff", "svg"
    )

    internal fun determineType(
        displayName: String,
        providerMime: String?,
        forceImage: Boolean = false
    ): Pair<String, Boolean> {
        val extension = displayName.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        val supplied = providerMime?.substringBefore(';')?.trim()
            ?.lowercase(Locale.ROOT)
        val mime = if (supplied.isNullOrEmpty() ||
            supplied == "*/*" || supplied == "application/octet-stream" ||
            supplied == "binary/octet-stream"
        ) MIME_BY_EXTENSION[extension] ?: "application/octet-stream" else supplied
        // Conservative counting: image files selected through Files must count
        // toward the same 20-image cap even if the file provider lies about MIME.
        return mime to (forceImage || mime.startsWith("image/") ||
            extension in IMAGE_EXTENSIONS)
    }

    internal fun categoryFor(mime: String, isImage: Boolean): String = when {
        mime == "application/vnd.microsoft.portable-executable" ||
            mime == "application/x-msdownload" -> "Windows-program"
        isImage -> "Kép"
        mime.startsWith("video/") -> "Videó"
        mime.startsWith("audio/") -> "Hang"
        mime == "application/pdf" -> "PDF"
        mime.startsWith("text/") -> "Szöveg"
        mime == "application/zip" || mime.contains("compressed") ||
            mime == "application/vnd.rar" || mime == "application/x-tar" ||
            mime == "application/gzip" -> "Archívum"
        else -> "Fájl"
    }

    fun withinLimits(imageCount: Int, totalBytes: Long, image: Boolean, newBytes: Long): Boolean =
        newBytes > 0L && newBytes <= MAX_TOTAL_BYTES &&
        totalBytes >= 0L && totalBytes <= MAX_TOTAL_BYTES - newBytes &&
        (!image || imageCount < MAX_IMAGES)

    fun total(items: List<JarvisAttachment>): Long = items.sumOf { it.sizeBytes }
    fun imageCount(items: List<JarvisAttachment>): Int = items.count { it.isImage }

    internal fun localExtension(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension.takeIf { it.matches(Regex("[a-z0-9]{1,16}")) } ?: "bin"
    }

    /** Must run on Dispatchers.IO. Reads streams incrementally and fails closed at the size cap. */
    fun importSelected(
        context: Context, uri: Uri,
        previous: List<JarvisAttachment>,
        forceImage: Boolean = false
    ): Result<JarvisAttachment> = runCatching {
        val resolver = context.contentResolver
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
        val displayName = (name ?: if (forceImage) "Kép.jpg" else "Fájl").take(120)
        val (mime, image) = determineType(displayName, resolver.getType(uri), forceImage)
        if (image && imageCount(previous) >= MAX_IMAGES)
            error("Legfeljebb 10 képet csatolhatsz.")
        val limit = MAX_TOTAL_BYTES - total(previous)
        if (limit <= 0L) error("Elérted az 1 GB összméretű csatolási korlátot.")
        val dir = File(context.cacheDir, "jarvis_attachments").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString() + "." + localExtension(displayName))
        var copied = 0L
        try {
            val source = resolver.openInputStream(uri) ?: error("Nem olvasható a kiválasztott fájl.")
            source.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        if (n == 0) continue
                        if (copied > limit - n) error("Túl nagy fájl: maximum 1 GB összesen.")
                        output.write(buffer, 0, n)
                        copied += n
                    }
                }
            }
            if (!withinLimits(imageCount(previous), total(previous), image, copied))
                error("A csatolmányok mérete vagy darabszáma meghaladja a limitet.")
            JarvisAttachment(UUID.randomUUID().toString(), displayName, dest, mime, copied, image)
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    fun delete(item: JarvisAttachment) {
        item.localFile.delete()
    }
}
