package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceNavigationTest {
    @Test fun hungarianDestinations() {
        assertEquals("Leeds", VoiceNavigation.destinationFrom("Navigálj Leedsbe"))
        assertEquals("Bradford", VoiceNavigation.destinationFrom("Navigálj Bradfordba"))
        assertEquals("HOME", VoiceNavigation.destinationFrom("Navigálj haza"))
        assertEquals("PERSON:Esztert", VoiceNavigation.destinationFrom(
            "Nyisd meg a térképet és állítsd be az Esztert címnek"))
        assertEquals("PERSON:Eszter", VoiceNavigation.destinationFrom(
            "Navigálj Eszter címére"))
        assertEquals("London", VoiceNavigation.destinationFrom("Mutasd meg Londont".replace("Londont", "London")))
        assertNull(VoiceNavigation.destinationFrom("Nyisd meg a számlázást"))
        assertNull(VoiceNavigation.destinationFrom("Navigálj"))
    }
}
