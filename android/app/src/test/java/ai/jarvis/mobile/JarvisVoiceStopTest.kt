package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

/** Stop playback without mistakenly stopping on narrative words or questions. */
class JarvisVoiceStopTest {
    @Test fun naturalHungarianAndEnglishStopsAreImmediateCommands() {
        for (text in listOf("állj", "Állj!", "Jarvis, állj", "hey Jarvis állj",
            "állj meg", "ne olvasd tovább", "Stop reading", "Stop speaking",
            "stop", "hagyd abba a felolvasást")) {
            assertTrue(text, VoiceIntentParser.isPlaybackStop(text))
        }
    }

    @Test fun mentioningStoppingIsNotAPlaybackCommand() {
        for (text in listOf("Hol állj meg az autónkkal?",
            "Mesélj a Stop című könyvről",
            "Mit jelent az, hogy állj?",
            "ne állj le", "állj majd meg", "állj le a hangfigyeléssel",
            "Olvasd tovább a mesét")) {
            assertFalse(text, VoiceIntentParser.isPlaybackStop(text))
        }
    }

    @Test fun disablingListeningKeepsExistingExplicitVoiceStop() {
        assertTrue(VoiceIntentParser.isStop("állj le"))
        assertTrue(VoiceIntentParser.isStop("stop listening"))
        assertFalse(VoiceIntentParser.isStop("állj"))
    }

    @Test fun aHungarianGoogleMapsNameQueryIsOnlySearchNotVerifiedNavigation() {
        assertEquals("PERSON:Eszter", VoiceNavigation.destinationFrom("Vigyél Eszterhez"))
        assertTrue(VoiceNavigation.maySearchNamedPlace("PERSON:Eszter"))
        assertFalse(VoiceNavigation.maySearchNamedPlace("HOME"))
        assertFalse(VoiceNavigation.maySearchNamedPlace("Harrogate"))
        assertFalse(VoiceNavigation.maySearchNamedPlace("PERSON:"))
        for (language in JarvisLanguage.entries) {
            assertTrue(JarvisNavigationText.result(
                language, VoiceNavigation.LaunchResult.SAVED_NAME_SEARCH_OPENED
            ).isNotBlank())
        }
    }
}
