package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceCallingTest {
    @Test fun hungarianDialCommands() {
        assertEquals("Esztert", VoiceCalling.extractRequest("Hívd fel Esztert"))
        assertEquals("George", VoiceCalling.extractRequest("Tárcsázd George"))
        assertEquals("+4412345678", VoiceCalling.extractRequest("Call +4412345678"))
        assertNull(VoiceCalling.extractRequest("Ki hívott fel?"))
        assertNull(VoiceCalling.extractRequest("Hívjuk fel valamikor Esztert"))
        assertNull(VoiceCalling.extractRequest(""))
    }
}
