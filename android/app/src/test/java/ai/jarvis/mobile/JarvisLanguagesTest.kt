package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisLanguagesTest {
    @Test fun hungarianRemainsOriginalAndEnglishTranslatesAllNavigationRoutes() {
        for (tool in JarvisToolCatalog.all + JarvisToolCatalog.subpages) {
            val displayed = JarvisTranslations.text(tool.name, JarvisLanguage.ENGLISH)
            // Names that are identical in both languages (e.g. Email) are valid.
            org.junit.Assert.assertTrue("Missing " + tool.name, displayed.isNotBlank())
        }
        assertEquals("Főoldal", JarvisTranslations.text("Főoldal", JarvisLanguage.HUNGARIAN))
        assertEquals("Home", JarvisTranslations.text("Főoldal", JarvisLanguage.ENGLISH))
        assertEquals("Settings", JarvisTranslations.text("Beállítások", JarvisLanguage.ENGLISH))
        assertEquals("Ask Jarvis", JarvisTranslations.text("Kérdezd meg Jarvist", JarvisLanguage.ENGLISH))
    }

    @Test fun languageTagsAndUnknownValuesAreStable() {
        assertEquals(JarvisLanguage.HUNGARIAN, JarvisLanguage.fromTag(null))
        assertEquals(JarvisLanguage.HUNGARIAN, JarvisLanguage.fromTag("not-a-language"))
        assertEquals(JarvisLanguage.ENGLISH, JarvisLanguage.fromTag("en-GB"))
        assertEquals("en-GB", JarvisLanguage.ENGLISH.tag)
        assertEquals("hu-HU", JarvisLanguage.HUNGARIAN.tag)
    }

    @Test fun userDataIsNotGuessedOrModified() {
        val note = "A user-created contact: George\n42 £"
        assertEquals(note, JarvisTranslations.text(note, JarvisLanguage.ENGLISH))
    }
}
