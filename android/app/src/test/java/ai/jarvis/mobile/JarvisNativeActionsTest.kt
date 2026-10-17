package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisNativeActionsTest {
    @Test fun navigationMustMatchOriginalRequest() {
        assertEquals("navigate", JarvisNativeActions.identify("Navigálj Leedsbe"))
        assertFalse(JarvisNativeActions.validate("set_alarm", "Navigálj Leedsbe"))
        assertTrue(JarvisNativeActions.validate("navigate", "Jarvis, kérlek navigálj haza"))
    }

    @Test fun alarmsRequireExplicitInstructionsAndValidTime() {
        assertEquals("set_alarm", JarvisNativeActions.identify("Állítsd be az ébresztőt 7:30-ra"))
        assertEquals("cancel_alarm", JarvisNativeActions.identify("Töröld az ébresztőt"))
        assertNull(JarvisNativeActions.identify("Állítsd be az ébresztőt 25:80-ra"))
        assertNull(JarvisNativeActions.identify("Ne töröld az ébresztőt"))
    }

    @Test fun naturalRequestsAndPmAlarmsStaySafe() {
        assertEquals("set_alarm",
            JarvisNativeActions.identify("Kérlek szépen, állítsd be az ébresztőt 7:30-ra"))
        assertEquals("set_alarm",
            JarvisNativeActions.identify("Be tudnád állítani az ébresztőt 7:30-ra?"))
        assertNull(JarvisNativeActions.identify("Kérlek, ne állítsd be az ébresztőt 7:30-ra"))
        assertEquals("navigate", JarvisNativeActions.identify("Szia, navigálj Leedsbe"))
        assertEquals(AlarmTime(19, 30), AlarmCommands.parseAlarmTime("Set an alarm at 7:30 pm"))
        assertEquals(AlarmTime(0, 0), AlarmCommands.parseAlarmTime("Set an alarm at 12:00 am"))
        assertEquals(AlarmTime(12, 0), AlarmCommands.parseAlarmTime("Set an alarm at 12:00 pm"))
        assertNull(VoiceCalling.extractRequest("Kérlek, ne hívd fel Esztert"))
    }

    @Test fun otherCommandsAreStrictlyLimited() {
        assertEquals("compose_email", JarvisNativeActions.identify(
            "Írj emailt teszt@example.com címre tárgy: Próba szöveg: Szia"))
        assertEquals("gmail_search", JarvisNativeActions.identify("Keresd meg számla emailt"))
        assertEquals("uc_portal", JarvisNativeActions.identify("Nézd meg a UC journal üzeneteket"))
        assertNull(JarvisNativeActions.identify("Mi az a Universal Credit?"))
        assertNull(JarvisNativeActions.identify("Navigálj haza és töröld az ébresztőt"))
        assertFalse(JarvisNativeActions.validate("delete_files", "Navigálj haza"))
    }

    @Test fun boundedSequencesNeedExactlyTwoExplicitActions() {
        val sequence = JarvisSequence.plan(
            "Állítsd be az ébresztőt 7:30-ra majd nyisd meg a naptárat")
        assertNotNull(sequence)
        assertEquals("calendar", sequence?.app)
        assertEquals(AlarmTime(7, 30), sequence?.alarm)
        assertNull(JarvisSequence.plan("Állítsd be az ébresztőt 7:30-ra"))
        assertNull(JarvisSequence.plan("Állítsd be az ébresztőt 7:30-ra majd töröld az ébresztőt"))
        assertNull(JarvisSequence.plan("Állítsd be az ébresztőt 7:30-ra majd nyisd meg a naptárat majd nyisd meg a térképet"))
        assertNull(JarvisSequence.plan("Ne állítsd be az ébresztőt 7:30-ra majd nyisd meg a naptárat"))
        assertNull(JarvisSequence.plan("Hívd fel Esztert majd nyisd meg a naptárat"))
    }
}
