package ai.jarvis.mobile

import android.speech.SpeechRecognizer

/**
 * Half-duplex Android speech policy. Android SpeechRecognizer closes capture
 * after each utterance; these delays avoid a tight open/close cycle while
 * idle and give the provider time to release its microphone before relaunch.
 */
internal object VoiceSessionPolicy {
    const val AFTER_REPLY_DELAY_MS = 850L
    const val REPLY_TIMEOUT_MS = 45_000L

    fun retryDelay(error: Int): Long = when (error) {
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 3_500L
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 2_600L
        SpeechRecognizer.ERROR_NO_MATCH -> 2_000L
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 4_000L
        else -> 2_000L
    }
}
