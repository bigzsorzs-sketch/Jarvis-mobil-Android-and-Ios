package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisCommandSafetyTest {
    @Test fun salutationsAndPolitenessDoNotEraseRequestedTargets() {
        assertEquals("nyisd meg a naptárat",
            JarvisCommandSafety.stripPrefixes("Szia, Jarvis, kérlek szépen, nyisd meg a naptárat"))
        assertEquals("mutasd a beállításokat",
            JarvisCommandSafety.stripPrefixes("Jó reggelt Jarvis, mutasd a beállításokat"))
        assertTrue(JarvisAiTools.userAskedToOpen("Szia, nyisd meg a beállításokat"))
        assertTrue(JarvisPhoneActions.authorized("calendar", "Szia, nyisd meg a naptárat"))
        assertTrue(JarvisPhoneActions.authorized("calendar", "Kérlek szépen, nyisd meg a naptárat?"))
    }

    @Test fun negationAndChainingCannotAuthorizeDeviceActions() {
        assertTrue(JarvisCommandSafety.isSafeDeviceRequest("Nyisd meg a naptárat, jó?"))
        assertTrue(JarvisCommandSafety.isSafeDeviceRequest("Be tudnád állítani az ébresztőt 7:30-ra?"))
        assertFalse(JarvisCommandSafety.isSafeDeviceRequest("Meg tudod nyitni a naptárat?"))
        assertFalse(JarvisCommandSafety.isSafeDeviceRequest("Kérlek, ne nyisd meg a naptárat"))
        assertFalse(JarvisCommandSafety.isSafeDeviceRequest("Nem tudom, de nyisd meg a naptárat"))
        assertFalse(JarvisCommandSafety.isSafeDeviceRequest("Nyisd meg a naptárat majd hívd fel Esztert"))
        assertFalse(JarvisPhoneActions.authorized("calendar", "Kérlek, ne nyisd meg a naptárat"))
        assertNull(JarvisNativeActions.identify("Kérlek, ne navigálj Leedsbe"))
        assertNull(VoiceCalling.extractRequest("Kérlek, ne hívd fel Esztert"))
    }
}
