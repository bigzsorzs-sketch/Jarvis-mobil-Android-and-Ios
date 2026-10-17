package ai.jarvis.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** Decode selected photos in bounded memory and shrink only when necessary.
 * The original private cached photo is never modified.
 */
internal object JarvisAiImageUpload {
    const val MAX_SOURCE_BYTES = 128L * 1024 * 1024
    const val MAX_ENCODED_BYTES = 2 * 1024 * 1024
    private const val MAX_EDGE = 2560

    internal fun allowance(imageCount: Int, reservedNonImageBytes: Long): Int {
        require(imageCount in 1..JarvisAttachments.MAX_IMAGES)
        val remaining = JarvisAiAttachments.MAX_SEND_BYTES - reservedNonImageBytes
        require(remaining >= imageCount * 32_000L) {
            "Nincs elég hely a képeknek a 20 MB-os AI-feltöltési keretben."
        }
        return minOf(MAX_ENCODED_BYTES.toLong(), remaining / imageCount).toInt()
    }

    /** Returns normalized MIME and bytes. Caller verifies cache ownership first. */
    fun prepare(file: File, mime: String, maximum: Int): Pair<String, ByteArray> {
        require(maximum in 32_000..MAX_ENCODED_BYTES)
        require(file.isFile && file.length() in 1..MAX_SOURCE_BYTES) {
            "A kép túl nagy: AI-feldolgozáshoz max. 128 MB-os eredeti képet válassz."
        }
        if (mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif") &&
            file.length() <= maximum) {
            return mime to file.readBytes()
        }
        // Animated GIF cannot be faithfully shrunk to a single JPEG frame.
        require(mime != "image/gif") {
            "Az animált GIF túl nagy. Válassz egy állóképet vagy kisebb GIF-et."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "A kép nem olvasható vagy a telefon nem támogatja a formátumot."
        }
        val decode = BitmapFactory.Options()
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2)
            sample *= 2
        decode.inSampleSize = sample
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, decode)
            ?: throw IllegalArgumentException("A képet nem sikerült feldolgozni.")
        try {
            repeat(4) { attempt ->
                val longest = maxOf(bitmap.width, bitmap.height)
                val targetEdge = if (attempt == 0) MAX_EDGE else maxOf(1000, (MAX_EDGE * Math.pow(0.8, attempt.toDouble())).toInt())
                if (longest > targetEdge) {
                    val factor = targetEdge.toFloat() / longest
                    val scaled = Bitmap.createScaledBitmap(bitmap,
                        maxOf(1, (bitmap.width * factor).toInt()),
                        maxOf(1, (bitmap.height * factor).toInt()), true)
                    if (scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
                }
                // Composite transparent pixels over white for readable screenshots.
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(opaque)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    for (quality in listOf(90, 82, 74, 64, 52, 40)) {
                        val output = ByteArrayOutputStream()
                        if (opaque.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                            val bytes = output.toByteArray()
                            if (bytes.size in 1..maximum) return "image/jpeg" to bytes
                        }
                    }
                } finally {
                    opaque.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
        throw IllegalArgumentException(
            "A kép túl részletes a biztonságos AI-feltöltési kerethez. Próbálj kisebb képet.")
    }
}
