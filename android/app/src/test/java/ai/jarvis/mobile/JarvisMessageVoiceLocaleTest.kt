package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisMessageVoiceLocaleTest {
    @Test fun notificationDictationTracksAllSevenJarvisLanguages() {
        val expected = setOf("hu-HU", "en-GB", "de-DE", "fr-FR", "es-ES", "it-IT", "pl-PL")
        assertEquals(7, JarvisLanguage.entries.size)
        assertEquals(expected,
            JarvisLanguage.entries.map(JarvisMessageVoiceLocale::speechTag).toSet())
        for (selected in JarvisLanguage.entries) {
            val tag = JarvisMessageVoiceLocale.speechTag(selected)
            assertEquals(selected.tag, tag)
            assertEquals(tag, JarvisMessageVoiceLocale.ttsLocale(selected).toLanguageTag())
            assertTrue(JarvisMessageVoiceLocale.dictationPrompt(selected).isNotBlank())
        }
    }

    @Test fun changingLanguageChangesDictationAndReadAloudLocale() {
        assertEquals("hu-HU", JarvisMessageVoiceLocale.speechTag(JarvisLanguage.HUNGARIAN))
        assertEquals("en-GB", JarvisMessageVoiceLocale.speechTag(JarvisLanguage.ENGLISH))
        assertNotEquals(
            JarvisMessageVoiceLocale.dictationPrompt(JarvisLanguage.ENGLISH),
            JarvisMessageVoiceLocale.dictationPrompt(JarvisLanguage.HUNGARIAN))
    }
}
