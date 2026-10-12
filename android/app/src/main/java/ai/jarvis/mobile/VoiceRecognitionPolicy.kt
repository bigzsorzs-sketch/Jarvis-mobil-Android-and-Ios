package ai.jarvis.mobile

import android.speech.SpeechRecognizer

/** A device recognizer can be installed without the requested language model. */
internal object VoiceRecognitionPolicy {
    fun needsSystemFallback(onDevice: Boolean, error: Int): Boolean =
        onDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
}
