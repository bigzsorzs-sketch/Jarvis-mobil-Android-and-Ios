package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class AlarmCommandsTest {
    @Test fun parseClockTimes() {
        assertEquals(AlarmTime(6, 0), AlarmCommands.parseAlarmTime("Állítsd be az ébresztőt reggel 6:00-ra"))
        assertEquals(AlarmTime(7, 30), AlarmCommands.parseAlarmTime("Állítsd be az ébresztőt 7:30-ra"))
        assertEquals(AlarmTime(19, 0), AlarmCommands.parseAlarmTime("Ébresztő este 7 órára"))
        assertEquals(AlarmTime(6, 30), AlarmCommands.parseAlarmTime("Ébresztő fél 7-re"))
        assertEquals(AlarmTime(6, 30), AlarmCommands.parseAlarmTime("Ébresztő 6 óra 30 percre"))
    }
    @Test fun doNotClaimUnsupportedOps() {
        assertNull(AlarmCommands.parseAlarmTime("Töröld a 6:00 ébresztőt"))
        assertNull(AlarmCommands.parseAlarmTime("Nyisd meg az ébresztőket"))
        assertNull(AlarmCommands.parseAlarmTime("Állíts ébresztőt holnap 7:30-ra"))
        assertNull(AlarmCommands.parseAlarmTime("Ébresztő 28:99"))
        assertNull(AlarmCommands.parseAlarmTime("Útvonal 6:00-ra"))
        assertNull(AlarmCommands.parseAlarmTime("Ne állítsd be az ébresztőt 7:30-ra"))
        assertNull(AlarmCommands.parseAlarmTime("Állítsd be az ébresztőt 7:30-ra majd nyisd meg a naptárat"))
        assertNull(AlarmCommands.parseAlarmTime("Állítsd be az ébresztőt 7:30-ra és nyisd meg a naptárat"))
    }
}
