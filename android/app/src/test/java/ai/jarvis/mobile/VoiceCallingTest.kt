package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceCallingTest {
    @Test fun aiCallsMustComeFromExplicitUserIntentAndMatchTarget() {
        assertEquals("Esztert", VoiceCalling.extractAiRequest("Jarvis, kérlek hívd fel Esztert"))
        assertEquals("Esztert", VoiceCalling.extractAiRequest("Fel tudnád hívni Esztert?"))
        assertEquals("+441234567890", VoiceCalling.extractAiRequest("Call +441234567890"))
        assertNull(VoiceCalling.extractAiRequest("Ne hívd fel Esztert"))
        assertNull(VoiceCalling.extractAiRequest("Mit tudsz Eszterről?"))
        assertNull(VoiceCalling.extractAiRequest("Hívd fel Esztert és Marit"))
        assertTrue(VoiceCalling.matchesAiTarget("Esztert", "Eszter"))
        assertFalse(VoiceCalling.matchesAiTarget("Esztert", "Mari"))
        assertFalse(VoiceCalling.matchesAiTarget("+441234567890", "+441234567891"))
    }

    @Test fun hungarianDialCommands() {
        assertEquals("Esztert", VoiceCalling.extractRequest("Hívd fel Esztert"))
        assertEquals("George", VoiceCalling.extractRequest("Tárcsázd George"))
        assertEquals("+4412345678", VoiceCalling.extractRequest("Call +4412345678"))
        assertNull(VoiceCalling.extractRequest("Ki hívott fel?"))
        assertNull(VoiceCalling.extractRequest("Hívjuk fel valamikor Esztert"))
        assertNull(VoiceCalling.extractRequest(""))
    }
}
