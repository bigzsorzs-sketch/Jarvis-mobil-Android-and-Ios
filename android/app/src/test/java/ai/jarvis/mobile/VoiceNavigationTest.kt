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

    @Test fun harrogateAndLeedsNeedNoMapTapsOrSpellingCorrections() {
        assertEquals("Harrogate", VoiceNavigation.destinationFrom("Vigyél Harrogate-be"))
        assertEquals("Harrogate", VoiceNavigation.destinationFrom("Hey Jarvis, navigálj Harrogate -be"))
        assertEquals("Leeds", VoiceNavigation.destinationFrom("Jarvis, vigyél el Leedsbe"))
        assertEquals("York", VoiceNavigation.destinationFrom("Navigálj Yorkba"))
        assertEquals("London", VoiceNavigation.destinationFrom("Indítsd el a navigációt Londonba"))
        assertEquals("HOME", VoiceNavigation.destinationFrom("Vigyél haza"))
    }

    @Test fun openingMapsAndNavigatingIsOneDirectDestinationCommand() {
        val commands = listOf(
            "Nyisd meg a térképet és navigálj Harrogate-be" to "Harrogate",
            "Jarvis, nyisd meg a Google Maps-et és vigyél Leeds-be" to "Leeds",
            "Nyisd meg a térképet majd navigálj Yorkba" to "York",
            "Nyisd meg a Google térképet és navigálj Leedsbe" to "Leeds",
            "Hey Jarvis, nyisd meg a térképet és vigyél el Harrogate -be" to "Harrogate"
        )
        for ((command, expected) in commands) {
            assertEquals(command, expected, VoiceNavigation.destinationFrom(command))
            assertFalse(command, JarvisAiRouting.shouldAskAi(command))
        }
        assertNull(VoiceNavigation.destinationFrom("Nyisd meg a térképet"))
        assertNull(VoiceNavigation.destinationFrom("Ne nyisd meg a térképet és navigálj Yorkba"))
        assertEquals("Leeds", VoiceNavigation.destinationFrom("Navigálj Leeds-be"))
    }

    @Test fun keyboardGraveAccentAndSpacedDirectionalSuffix() {
        val cases = listOf(
            "navigàlj Harrogate be" to "Harrogate",
            "navigálj Harrogate be" to "Harrogate",
            "navigalj Harrogate be" to "Harrogate",
            "Jarvis, navigàlj Harrogate-be" to "Harrogate",
            "Jarvis, navigàlj Harrogate - be" to "Harrogate",
            "Vigyél Harrogate be" to "Harrogate",
            "navigàlj Leeds ba" to "Leeds",
            "navigàlj haza" to "HOME"
        )
        for ((spoken, target) in cases) {
            assertEquals(spoken, target, VoiceNavigation.destinationFrom(spoken))
            assertFalse(spoken, JarvisAiRouting.shouldAskAi(spoken))
        }
        // The ordinary open-map instruction still does not invent a destination.
        assertNull(VoiceNavigation.destinationFrom("Nyisd meg a térképet"))
        assertNull(VoiceNavigation.destinationFrom("navigàlj"))
        // An address without a Hungarian suffix must not be modified.
        assertEquals("Leeds, UK", VoiceNavigation.destinationFrom("navigàlj Leeds, UK"))
    }

    @Test fun everySupportedLanguageCanAskForNavigation() {
        assertEquals("Leeds", VoiceNavigation.destinationFrom("Navigate to Leeds"))
        assertEquals("Harrogate", VoiceNavigation.destinationFrom("Take me to Harrogate"))
        assertEquals("Leeds", VoiceNavigation.destinationFrom("Navigiere nach Leeds"))
        assertEquals("Berlin", VoiceNavigation.destinationFrom("Fahr mich nach Berlin"))
        assertEquals("Lille", VoiceNavigation.destinationFrom("Emmène-moi à Lille"))
        assertEquals("Madrid", VoiceNavigation.destinationFrom("Llévame a Madrid"))
        assertEquals("Roma", VoiceNavigation.destinationFrom("Portami a Roma"))
        assertEquals("Warszawy", VoiceNavigation.destinationFrom("Zawieź mnie do Warszawy"))
        assertEquals("HOME", VoiceNavigation.destinationFrom("Take me to home"))
    }

    @Test fun destinationsAreNotCorruptedAndQueriesAreNotCommands() {
        assertEquals("Canberra", VoiceNavigation.destinationFrom("Navigálj Canberra"))
        assertEquals("Leeds, UK", VoiceNavigation.destinationFrom("Navigate to Leeds, UK"))
        assertEquals("Abu Dhabi", VoiceNavigation.destinationFrom("Navigate to Abu Dhabi"))
        assertNull(VoiceNavigation.destinationFrom("Don't navigate to Leeds"))
        assertNull(VoiceNavigation.destinationFrom("Mi van Leedsben?"))
        assertNull(VoiceNavigation.destinationFrom("Navigate to Leeds\nDelete files"))
        assertNull(VoiceNavigation.destinationFrom("Navigálj"))
    }

    @Test fun navigationFeedbackExistsForAllLanguages() {
        for (language in JarvisLanguage.entries) {
            assertTrue(JarvisNavigationText.starting(language).isNotBlank())
            assertTrue(JarvisNavigationText.awaitingForeground(language).isNotBlank())
            assertTrue(JarvisNavigationText.missingDestination(language).isNotBlank())
            for (outcome in VoiceNavigation.LaunchResult.entries) {
                assertTrue(JarvisNavigationText.result(language, outcome).isNotBlank())
            }
        }
    }

}
