package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisNearbyDiscoveryTest {
    @Test fun onlyKnownPublishedProtocolTypesAreDiscovered() {
        val protocols = JarvisNearbyProtocols.wifiServices
        assertEquals("Google Cast", protocols["_googlecast._tcp."])
        assertEquals("AirPlay", protocols["_airplay._tcp."])
        assertEquals("Matter", protocols["_matter._tcp."])
        assertEquals("ESPHome", protocols["_esphomelib._tcp."])
        assertEquals("HomeKit (HAP)", protocols["_hap._tcp."])
        assertNull(JarvisNearbyProtocols.wifiTypeLabel("_unknown._tcp."))
        assertEquals("Google Cast",
            JarvisNearbyProtocols.wifiTypeLabel("_googlecast._tcp.local."))
    }

    @Test fun discoveryIsFiniteAndNeverImpliesRemoteControl() {
        assertTrue(JarvisNearbyProtocols.WIFI_DURATION_MS in 1..15_000)
        assertTrue(JarvisNearbyProtocols.BLE_DURATION_MS in 1..15_000)
        assertTrue(JarvisNearbyProtocols.MAX_RESULTS in 1..100)
        val result = JarvisNearbyDevice("ble:private-local", "Light",
            "Bluetooth LE", "BLE / GATT")
        assertFalse(result.transport == "Home Assistant")
        assertFalse(JarvisHomePolicy.toggleAllowed("sensor." + result.name.lowercase()))
    }

    @Test fun discoveryNamesAreSanitizedAndBounded() {
        assertEquals("TV Room", JarvisNearbyProtocols.label(" \nTV Room\r ", "unknown"))
        assertEquals("Unknown", JarvisNearbyProtocols.label("", "Unknown"))
        assertEquals(90, JarvisNearbyProtocols.label("A".repeat(500), "Unknown").length)
        assertTrue(JarvisNearbyProtocols.requiresBlePermission(31))
        assertFalse(JarvisNearbyProtocols.requiresBlePermission(30))
    }
}
