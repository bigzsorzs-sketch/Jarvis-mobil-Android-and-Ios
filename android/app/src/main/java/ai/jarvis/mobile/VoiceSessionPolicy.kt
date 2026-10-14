package ai.jarvis.mobile

import android.speech.SpeechRecognizer

/**
 * Android SpeechRecognizer handles one utterance per session, not a real
 * full-duplex audio stream. Keep the user-started conversation alive by
 * restarting only after results, the answer, or a recoverable provider error.
 */
internal object VoiceSessionPolicy {
    const val AFTER_REPLY_DELAY_MS = 850L
    const val REPLY_TIMEOUT_MS = 45_000L

    fun retryDelay(error: Int, consecutiveFailures: Int = 0): Long {
        val base = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 3_500L
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 2_600L
            SpeechRecognizer.ERROR_NO_MATCH -> 2_000L
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 4_000L
            else -> 2_000L
        }
        // Gradually back off repeated provider failures; never spin or terminate
        // the user-started session merely because the ASR service disconnects.
        val multiplier = when {
            consecutiveFailures >= 6 -> 4
            consecutiveFailures >= 3 -> 2
            else -> 1
        }
        return (base * multiplier).coerceAtMost(20_000L)
    }
}
