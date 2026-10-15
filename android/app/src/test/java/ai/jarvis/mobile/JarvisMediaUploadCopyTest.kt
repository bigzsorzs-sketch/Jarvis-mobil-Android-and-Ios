package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisMediaUploadCopyTest {
    @Test fun sevenLanguagesExplainVideoPrivacyAndLimits() {
        assertEquals(7, JarvisLanguage.entries.size)
        for (language in JarvisLanguage.entries) {
            val staging = JarvisMediaUploadCopy.staged(language)
            val preview = JarvisMediaUploadCopy.preview(language)
            assertTrue(language.name, staging.isNotBlank() && preview.isNotBlank())
            assertTrue(language.name, staging.contains("128"))
            assertTrue(language.name, staging.contains("4"))
            assertTrue(language.name, preview.contains("4"))
        }
        assertTrue(JarvisMediaUploadCopy.preview(JarvisLanguage.ENGLISH).contains("not uploaded"))
        assertTrue(JarvisMediaUploadCopy.preview(JarvisLanguage.HUNGARIAN).contains("nem küldi"))
    }
}
