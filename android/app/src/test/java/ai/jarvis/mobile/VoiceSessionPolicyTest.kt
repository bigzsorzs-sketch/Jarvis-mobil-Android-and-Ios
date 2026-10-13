package ai.jarvis.mobile

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test

class VoiceSessionPolicyTest {
    @Test fun silenceAndBusyNeverCreateHotRestartLoop() {
        assertTrue(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_NO_MATCH) >= 1_800L)
        assertTrue(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) >= 2_000L)
        assertTrue(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) >= 3_000L)
        assertTrue(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_NETWORK) >
            VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_NO_MATCH))
    }
    @Test fun replyIsSettledBeforeNextRecognitionCycle() {
        assertTrue(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS >= 700L)
        assertTrue(VoiceSessionPolicy.REPLY_TIMEOUT_MS >= 30_000L)
    }
}
