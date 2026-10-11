package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

internal data class JarvisAttachment(
    val id: String, val displayName: String, val localFile: File,
    val mimeType: String, val sizeBytes: Long, val isImage: Boolean
)

/** Scoped Android content, copied to app-private cache, not to any cloud. */
internal object JarvisAttachments {
    const val MAX_IMAGES = 20
    const val MAX_TOTAL_BYTES = 1_073_741_824L // 1 GiB, aggregate not per file

    fun withinLimits(imageCount: Int, totalBytes: Long, image: Boolean, newBytes: Long): Boolean =
        newBytes > 0L && newBytes <= MAX_TOTAL_BYTES &&
        totalBytes >= 0L && totalBytes <= MAX_TOTAL_BYTES - newBytes &&
        (!image || imageCount < MAX_IMAGES)

    fun total(items: List<JarvisAttachment>): Long = items.sumOf { it.sizeBytes }
    fun imageCount(items: List<JarvisAttachment>): Int = items.count { it.isImage }

    /** Must run on Dispatchers.IO. Reads streams incrementally and fails closed at the size cap. */
    fun importSelected(
        context: Context, uri: Uri,
        previous: List<JarvisAttachment>,
        forceImage: Boolean = false
    ): Result<JarvisAttachment> = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: if (forceImage) "image/jpeg" else "application/octet-stream"
        val image = forceImage || mime.startsWith("image/")
        if (image && imageCount(previous) >= MAX_IMAGES) error("Legfeljebb 20 képet választhatsz.")
        val limit = MAX_TOTAL_BYTES - total(previous)
        if (limit <= 0L) error("Elérted az 1 GB összméretű csatolási korlátot.")
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
        val displayName = (name ?: if (image) "Kép" else "Fájl").take(120)
        val dir = File(context.cacheDir, "jarvis_attachments").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString() + ".bin")
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
