package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisLocalPreferencesTest {
    @Test fun themeChangesAreExplicit() {
        assertEquals(JarvisLocalPreferences.Command.Theme(true),
            JarvisLocalPreferences.parse("Kapcsold be a sötét módot"))
        assertEquals(JarvisLocalPreferences.Command.Theme(false),
            JarvisLocalPreferences.parse("Állítsd világos módra"))
        assertNull(JarvisLocalPreferences.parse("Milyen a sötét mód?"))
        assertNull(JarvisLocalPreferences.parse("Ne kapcsold be a sötét módot"))
        assertTrue(JarvisCommandContext.independentRequest("Válts sötét módra"))
    }

    @Test fun allSevenLanguagesHaveDeterministicSwitchCommands() {
        val cases = mapOf(
            "Válts magyarra" to JarvisLanguage.HUNGARIAN,
            "Válts angolra" to JarvisLanguage.ENGLISH,
            "Válts németre" to JarvisLanguage.GERMAN,
            "Válts franciára" to JarvisLanguage.FRENCH,
            "Válts spanyolra" to JarvisLanguage.SPANISH,
            "Válts olaszra" to JarvisLanguage.ITALIAN,
            "Válts lengyelre" to JarvisLanguage.POLISH
        )
        for ((spoken, expected) in cases) {
            val actual = JarvisLocalPreferences.parse(spoken)
            assertEquals(spoken, JarvisLocalPreferences.Command.Language(expected), actual)
            assertTrue(spoken, JarvisCommandContext.independentRequest(spoken))
        }
        assertNull(JarvisLocalPreferences.parse("Miért jó az angol nyelv?"))
        assertNull(JarvisLocalPreferences.parse("Kérlek, ne válts magyarra"))
    }
}
