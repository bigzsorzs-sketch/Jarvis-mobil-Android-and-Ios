package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSpeechVoiceConfigTest {
    @Test fun everySupportedLanguageGetsAnExplicitMatchingLocale() {
        for (language in JarvisLanguage.entries) {
            val voice = JarvisSpeechVoiceConfig.voice(language)
            assertTrue(voice.startsWith(language.tag + "-"))
            assertTrue(voice.endsWith(":MAI-Voice-2.1-Flash"))
        }
    }
    @Test fun speechModelMatchesVoiceSuffix() {
        assertEquals("microsoft/mai-voice-2.1-flash", JarvisSpeechVoiceConfig.MODEL)
    }
}
