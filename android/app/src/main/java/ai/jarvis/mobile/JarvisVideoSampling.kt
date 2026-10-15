package ai.jarvis.mobile

import kotlin.math.max
import kotlin.math.min

/**
 * Bounded, representative frame timestamps for local video preview and future
 * user-approved visual analysis. Audio transcription / AI inference is separate.
 */
internal object JarvisVideoSampling {
    const val MAX_FRAMES = 12
    private const val TARGET_INTERVAL_MS = 10_000L

    fun timestampsMs(durationMs: Long, maxFrames: Int = MAX_FRAMES): List<Long> {
        require(maxFrames in 1..MAX_FRAMES)
        if (durationMs <= 0L) return emptyList()
        val count = min(
            maxFrames,
            max(1, (durationMs / TARGET_INTERVAL_MS).coerceAtMost(maxFrames.toLong()).toInt() + 1)
        )
        return (0 until count).map { index ->
            // Middle of each segment, never seek to/after a potentially invalid end timestamp.
            ((index + 0.5) * durationMs.toDouble() / count).toLong()
                .coerceIn(0L, durationMs - 1L)
        }
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "ismeretlen"
        val seconds = durationMs / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest)
        else "%d:%02d".format(minutes, rest)
    }
}
