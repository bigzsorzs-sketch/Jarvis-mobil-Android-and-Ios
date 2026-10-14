package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisScreenshotRegressionsTest {
    @Test fun hungarianAndEnglishNavigationRequestTheSameKnownPerson() {
        assertEquals("PERSON:Eszter", VoiceNavigation.destinationFrom("Vigyél Eszterhez"))
        assertEquals("PERSON:Eszter", VoiceNavigation.destinationFrom("Navigálj Eszterhez"))
        assertEquals("Eszter", VoiceNavigation.destinationFrom("Navigate to Eszter"))
        assertEquals("Harrogate", VoiceNavigation.destinationFrom("Navigate to Harrogate"))
        assertEquals("Harrogate", VoiceNavigation.destinationFrom("Vigyél Harrogate-be"))
    }

    @Test fun onlyUniqueSavedAndroidContactPostalAddressIsRoutable() {
        val one = JarvisContactAddresses.Entry(
            7L, "Eszter", "20 Broom Road, Mirfield, UK")
        val other = JarvisContactAddresses.Entry(
            8L, "Eszter Smith", "21 Test Street, Leeds, UK")
        assertEquals("20 Broom Road, Mirfield, UK",
            JarvisContactAddresses.uniqueAddress("Eszter", listOf(one)))
        assertNull(JarvisContactAddresses.uniqueAddress("Eszter",
            listOf(one, one.copy(address = "21 Broom Road, Mirfield, UK"))))
        assertEquals("20 Broom Road, Mirfield, UK",
            JarvisContactAddresses.uniqueAddress("Eszter", listOf(one, other)))
        assertNull(JarvisContactAddresses.uniqueAddress("Eszter",
            listOf(one.copy(displayName = "Eszter Nagy"), other)))
        assertNull(JarvisContactAddresses.uniqueAddress("Eszter", emptyList()))
        assertNull(JarvisContactAddresses.uniqueAddress("John",
            listOf(one)))
    }

    @Test fun hesitantHungarianPrivateGlucoseRequestsRemainLocal() {
        val userRequests = listOf(
            "nézd meg hogy a cukromat meg tudod a nézni hogy hány nyascukrom van",
            "Nézd meg hogy a cukromat meg tudod a nézni",
            "mennyi a vércukrom most",
            "nézd meg hogy mennyi a nyascukrom",
            "hány a cukrom"
        )
        for (request in userRequests) {
            assertTrue(request, JarvisGlucoseVoice.isQuery(request))
            assertFalse(request, JarvisAiRouting.shouldAskAi(request))
        }
        assertFalse(JarvisGlucoseVoice.isQuery("Ne nézd meg a cukromat"))
        assertFalse(JarvisGlucoseVoice.isQuery("Melyik diabetes gyógyszer a jobb?"))
        assertFalse(JarvisGlucoseVoice.isQuery("15 mmol/L"))
    }

    @Test fun capabilitiesDoNotProduceAnOversizedAutoDescription() {
        val response = JarvisCapabilities.replyIfAsked("milyen funkciókat tudsz használni")
        assertNotNull(response)
        assertTrue(response!!.length < 650)
        assertTrue(response.contains("Vércukor"))
        assertTrue(response.contains("Beállítások"))
        assertTrue(JarvisCapabilities.replyIfAsked("ki vagy")!!.length < 300)
    }
}
