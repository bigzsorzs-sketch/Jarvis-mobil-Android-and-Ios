package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisDeparturePolicyTest {
    @Test fun rejectTinyUncertainOrInvalidGeofences() {
        assertTrue(JarvisDeparturePolicy.validZone(53.68, -1.5, 150f))
        assertTrue(JarvisDeparturePolicy.validZone(53.68, -1.5, 100f))
        assertFalse(JarvisDeparturePolicy.validZone(53.68, -1.5, 50f))
        assertFalse(JarvisDeparturePolicy.validZone(Double.NaN, -1.5, 150f))
        assertFalse(JarvisDeparturePolicy.validZone(95.0, -1.5, 150f))
        assertFalse(JarvisDeparturePolicy.validZone(0.0, 0.0, 150f))
        assertTrue(JarvisDeparturePolicy.EXIT_COOLDOWN_MS >= 600_000L)
    }
    @Test fun onlyConfirmedOnLightsRaiseOnAlert() {
        fun bulb(name: String, state: String) =
            JarvisHomeEntity("light." + name.replace(" ", "_"), name,
                state, "light", true)
        val devices = listOf(bulb("Kitchen light", "on"), bulb("Hall light", "off"),
            JarvisHomeEntity("media_player.tv", "TV", "on", "media_player", true),
            JarvisHomeEntity("switch.washer", "Washer", "on", "switch", false))
        val result = JarvisDeparturePolicy.assessment(devices)
        assertTrue(result is JarvisDepartureAssessment.LightsOn)
        assertEquals(listOf("Kitchen light"), (result as JarvisDepartureAssessment.LightsOn).names)
    }
    @Test fun unknownLightStateNeverClaimedAllOff() {
        fun bulb(state: String) = JarvisHomeEntity(
            "light.hall", "Hall", state, "light", true)
        assertEquals(JarvisDepartureAssessment.Unverified,
            JarvisDeparturePolicy.assessment(listOf(bulb("unavailable"))))
        assertEquals(JarvisDepartureAssessment.AllOff,
            JarvisDeparturePolicy.assessment(listOf(bulb("off"))))
        assertEquals(JarvisDepartureAssessment.NoLightsConfigured,
            JarvisDeparturePolicy.assessment(emptyList()))
    }
}
