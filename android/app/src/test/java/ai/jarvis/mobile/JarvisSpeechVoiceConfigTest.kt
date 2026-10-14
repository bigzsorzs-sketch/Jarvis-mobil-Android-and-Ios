package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSpeechVoiceConfigTest {
    @Test fun everySupportedLanguageGetsAnExplicitMatchingLocale() {
        for (language in JarvisLanguage.entries) {
            val voice = JarvisSpeechVoiceConfig.voice(language)
            assertTrue(voice.startsWith(language.tag + "-"))
            assertTrue(voice.endsWith(":MAI-Voice-2.1-Flash"))
            assertTrue(JarvisSpeechVoiceConfig.fallbackVoice(language)
                .startsWith(language.tag + "-"))
            assertTrue(JarvisSpeechVoiceConfig.fallbackVoice(language)
                .endsWith(":MAI-Voice-2.1"))
        }
    }
    @Test fun speechModelMatchesVoiceSuffix() {
        assertEquals("microsoft/mai-voice-2.1-flash", JarvisSpeechVoiceConfig.MODEL)
        assertEquals("microsoft/mai-voice-2.1", JarvisSpeechVoiceConfig.FALLBACK_MODEL)
    }
}
