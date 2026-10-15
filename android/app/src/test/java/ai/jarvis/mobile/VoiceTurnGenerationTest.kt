package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceTurnGenerationTest {
    @Test fun duplicateTranscriptDoesNotReviveStaleVoiceResult() {
        val turns = VoiceTurnGeneration()
        val first = turns.next()
        assertTrue(turns.accepts(first))
        val second = turns.next()
        assertFalse(turns.accepts(first))
        assertTrue(turns.accepts(second))
    }

    @Test fun typedOrCancelledTurnInvalidatesDelayedCloudAnswer() {
        val turns = VoiceTurnGeneration()
        val voiceRequest = turns.next()
        turns.next() // typed input, closed microphone or switched chat
        assertFalse(turns.accepts(voiceRequest))
    }
}
