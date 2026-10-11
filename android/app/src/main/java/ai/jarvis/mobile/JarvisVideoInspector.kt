package ai.jarvis.mobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File

/** Local preview only: this is NOT cloud AI understanding or audio transcription. */
internal data class JarvisVideoFrame(val timestampMs: Long, val thumbnail: File)
internal data class JarvisVideoInspection(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudioTrack: Boolean,
    val frames: List<JarvisVideoFrame>
)

internal object JarvisVideoInspector {
    private const val FRAME_EDGE = 640

    /** Invoke from Dispatchers.IO – decoding frames can be slow on some phones. */
    fun inspect(context: Context, attachment: JarvisAttachment): Result<JarvisVideoInspection> =
        runCatching {
            require(attachment.mimeType.startsWith("video/")) {
                "A kiválasztott fájl nem videó."
            }
            require(attachment.localFile.isFile && attachment.localFile.length() > 0L) {
                "A videófájl már nem érhető el."
            }
            val directory = outputDirectory(context, attachment.id)
            directory.mkdirs()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(attachment.localFile.absolutePath)
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0
                val hasAudio = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                )?.equals("yes", ignoreCase = true) == true

                if (duration <= 0L || width <= 0 || height <= 0) {
                    error("Nem sikerült a videó időtartamát vagy felbontását kiolvasni.")
                }

                val frames = mutableListOf<JarvisVideoFrame>()
                val longest = maxOf(width, height)
                val scale = FRAME_EDGE.toDouble() / longest.toDouble()
                val previewWidth = (width * scale).toInt().coerceAtLeast(1)
                val previewHeight = (height * scale).toInt().coerceAtLeast(1)

                JarvisVideoSampling.timestampsMs(duration).forEachIndexed { idx, timestampMs ->
                    val timestampUs = timestampMs * 1_000L
                    val bitmap = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(
                                timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                previewWidth, previewHeight
                            )
                        } else {
                            // Android 8.0 may decode the full frame; avoid huge
                            // allocations on very high resolution videos.
                            if (longest > 1920) null
                            else retriever.getFrameAtTime(
                                timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                        }
                    } catch (_: RuntimeException) { null }

                    if (bitmap != null) {
                        try {
                            val file = File(directory,
                                "frame_" + idx.toString().padStart(2, '0') + ".jpg")
                            file.outputStream().use { out ->
                                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)) {
                                    error("Nem sikerült képkockát menteni.")
                                }
                            }
                            frames += JarvisVideoFrame(timestampMs, file)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                JarvisVideoInspection(duration, width, height, hasAudio, frames)
            } catch (t: Throwable) {
                directory.deleteRecursively()
                throw t
            } finally {
                retriever.release()
            }
        }

    private fun outputDirectory(context: Context, id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return File(context.cacheDir, "jarvis_video_frames/$id")
    }

    fun removeCache(context: Context, attachmentId: String) {
        runCatching { outputDirectory(context, attachmentId).deleteRecursively() }
    }
}
