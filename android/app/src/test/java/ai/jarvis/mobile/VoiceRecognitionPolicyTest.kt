package ai.jarvis.mobile

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test

class VoiceRecognitionPolicyTest {
    @Test fun missingHungarianOfflineModelFallsBackOnce() {
        assertTrue(VoiceRecognitionPolicy.needsSystemFallback(true,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertTrue(VoiceRecognitionPolicy.needsSystemFallback(true,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertFalse(VoiceRecognitionPolicy.needsSystemFallback(false,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    @Test fun permissionAndSilenceErrorsDoNotSwitchRecognitionProviders() {
        assertFalse(VoiceRecognitionPolicy.needsSystemFallback(true,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertFalse(VoiceRecognitionPolicy.needsSystemFallback(true,
            SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(VoiceRecognitionPolicy.needsSystemFallback(true,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }
}
