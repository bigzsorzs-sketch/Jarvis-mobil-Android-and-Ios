package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceTranscriptSelectionTest {
    @Test fun uncertainEnvironmentalNoiseDoesNotTriggerADeviceCommand() {
        assertNull(VoiceTranscriptSelection.select(listOf("hívd fel valakit"), floatArrayOf(0.12f)))
        assertNull(VoiceTranscriptSelection.select(listOf("", "   "), null))
    }

    @Test fun recognizersWithoutConfidenceDoNotSilenceRealSpeech() {
        assertEquals("navigálj Leedsbe",
            VoiceTranscriptSelection.select(listOf(" navigálj Leedsbe "), null))
        assertEquals("hívd fel Esztert",
            VoiceTranscriptSelection.select(listOf("hívd fel Esztert"), floatArrayOf(-1f)))
    }

    @Test fun strongCandidateBeatsWeakOne() {
        assertEquals("navigálj Manchesterbe", VoiceTranscriptSelection.select(
            listOf("navigálj Londonba", "navigálj Manchesterbe"),
            floatArrayOf(0.11f, 0.91f)))
    }

    @Test fun overlyLongOrMissingAsrHypothesesAreIgnored() {
        assertNull(VoiceTranscriptSelection.select(listOf("a".repeat(1600)), null))
        assertEquals("Jarvis, hallasz?",
            VoiceTranscriptSelection.select(listOf(" ", "Jarvis, hallasz?"), null))
    }
}
