package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisCallTextTest {
    @Test fun allSevenLanguagesRenderCallReviewWithOriginalPhoneContact() {
        for (language in JarvisLanguage.entries) {
            val prompt = JarvisCallText.get(language, "confirm", "Vivi", "+441234567890")
            assertTrue(prompt.contains("Vivi"))
            assertTrue(prompt.contains("+441234567890"))
            assertTrue(JarvisCallText.get(language, "dialog", "Vivi", "+441234567890")
                .contains("+441234567890"))
            for (key in listOf("timeout", "cancel", "yesNo", "submitted", "failed",
                "noContacts", "ambiguous", "missing")) {
                assertTrue(language.tag + " " + key,
                    JarvisCallText.get(language, key).isNotBlank())
            }
        }
    }
}
