package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSystemPanelsTest {
    @Test fun supportsExplicitSafeSystemSettingsRequests() {
        assertEquals(JarvisSystemPanels.WIFI,
            JarvisSystemPanels.identify("Nyisd meg a WiFi beállításokat"))
        assertEquals(JarvisSystemPanels.BLUETOOTH,
            JarvisSystemPanels.identify("Jarvis, kérlek nyisd meg a Bluetooth beállításokat"))
        assertEquals(JarvisSystemPanels.DISPLAY,
            JarvisSystemPanels.identify("Nyisd meg a kijelző beállításait"))
        assertEquals(JarvisSystemPanels.LOCATION,
            JarvisSystemPanels.identify("Open GPS settings"))
        assertEquals(JarvisSystemPanels.BATTERY,
            JarvisSystemPanels.identify("Nyisd meg az akkumulátor beállításokat"))
        assertEquals(JarvisSystemPanels.APP_DETAILS,
            JarvisSystemPanels.identify("Nyisd meg a Jarvis engedélyeit"))
    }
    @Test fun neverTogglesSettingsOrExecutesCompoundOrNegatedRequests() {
        assertNull(JarvisSystemPanels.identify("Kapcsold be a WiFi-t"))
        assertNull(JarvisSystemPanels.identify("Ne nyisd meg a WiFi beállításokat"))
        assertNull(JarvisSystemPanels.identify("Nyisd meg a WiFi és a Bluetooth beállításokat"))
        assertNull(JarvisSystemPanels.identify("Tudod bekapcsolni a Bluetooth-t?"))
        assertNull(JarvisSystemPanels.identify("Nyisd meg a WiFi beállításokat majd fizess"))
        assertNull(JarvisSystemPanels.identify("Nyisd meg a banki beállításokat"))
    }
}
