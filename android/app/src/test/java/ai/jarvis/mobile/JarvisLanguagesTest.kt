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

    @Test fun remainingPrivacyAndCalendarCopyIsLocalizedInAllSevenLanguages() {
        val notice = "A Jarvis az AI-kérdéseket a korábban engedélyezett kapcsolaton keresztül dolgozza fel. " +
            "A beállítóoldal eltávolítása nem módosítja a mentett hozzáférést vagy hozzájárulásokat. " +
            "Az alkalmazás helyi adatainak végleges törlését az Android Alkalmazásadatok / Tárhely menüjében indíthatod."
        for (lang in JarvisLanguage.entries.filter { it != JarvisLanguage.HUNGARIAN }) {
            org.junit.Assert.assertNotEquals(lang.tag, notice, JarvisTranslations.text(notice, lang))
            for (key in listOf("Adatvédelmi szabályzat",
                    "Jarvis Mobile – Google Play és GitHub", "Átadtam a naptár megnyitását az Androidnak.")) {
                org.junit.Assert.assertNotEquals(lang.tag, key, JarvisTranslations.text(key, lang))
            }
        }
    }

    @Test fun languageTagsAndUnknownValuesAreStable() {
        assertEquals(JarvisLanguage.HUNGARIAN, JarvisLanguage.fromTag(null))
        assertEquals(JarvisLanguage.HUNGARIAN, JarvisLanguage.fromTag("not-a-language"))
        assertEquals(JarvisLanguage.ENGLISH, JarvisLanguage.fromTag("en-GB"))
        assertEquals("en-GB", JarvisLanguage.ENGLISH.tag)
        assertEquals("hu-HU", JarvisLanguage.HUNGARIAN.tag)
    }

    @Test fun sevenLanguageTagsAndRegionalSpeechLocales() {
        assertEquals(7, JarvisLanguage.entries.size)
        val cases = mapOf(
            "hu-RO" to JarvisLanguage.HUNGARIAN,
            "en-US" to JarvisLanguage.ENGLISH,
            "de-AT" to JarvisLanguage.GERMAN,
            "fr-CA" to JarvisLanguage.FRENCH,
            "es-MX" to JarvisLanguage.SPANISH,
            "it-CH" to JarvisLanguage.ITALIAN,
            "pl-PL" to JarvisLanguage.POLISH
        )
        cases.forEach { (tag, expected) ->
            assertEquals("Spoken locale $tag", expected, JarvisLanguage.fromSpeechTag(tag))
        }
        assertEquals(null, JarvisLanguage.fromSpeechTag("tr-TR"))
        for (language in JarvisLanguage.entries) {
            assertEquals(language, JarvisLanguage.fromTag(language.tag))
            assertEquals(language, JarvisLanguage.fromSpeechTag(language.tag))
        }
    }

    @Test fun topNavigationAndBasicActionsHaveRealTranslations() {
        for (language in JarvisLanguage.entries.filter { it != JarvisLanguage.HUNGARIAN }) {
            for (name in listOf("Főoldal", "Beállítások", "Engedélyek", "Jegyzetek", "Vércukor")) {
                assertEquals(
                    "Untranslated in " + language.tag + ": " + name,
                    false, JarvisTranslations.text(name, language) == name
                )
            }
            assertEquals(
                "Ask Jarvis is not localized in " + language.tag,
                false, JarvisTranslations.text("Kérdezd meg Jarvist", language) == "Kérdezd meg Jarvist"
            )
        }
    }

    @Test fun autoLanguageSwitchRequiresConfirmedEngineSwitch() {
        val confidence = android.speech.SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT
        val succeeded = android.speech.SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SUCCEEDED
        val notAttempted = android.speech.SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED
        assertEquals(JarvisLanguage.ENGLISH,
            JarvisSpeechRecognition.acceptedLanguage("en-US", confidence, succeeded, JarvisLanguage.HUNGARIAN))
        assertEquals(null,
            JarvisSpeechRecognition.acceptedLanguage("en-US", confidence, notAttempted, JarvisLanguage.HUNGARIAN))
        assertEquals(null,
            JarvisSpeechRecognition.acceptedLanguage("en-US", 1, succeeded, JarvisLanguage.HUNGARIAN))
        assertEquals(null,
            JarvisSpeechRecognition.acceptedLanguage("xx-XX", confidence, succeeded, JarvisLanguage.HUNGARIAN))
    }

    @Test fun everyExistingEnglishUiKeyIsTranslatedIntoAllNewLocales() {
        for (key in JarvisTranslations.fixedTextKeys()) {
            org.junit.Assert.assertTrue("Untranslated key: " + key, JarvisMoreTranslations.hasFixedText(key))
        }
        for (language in JarvisLanguage.entries.filter {
            it != JarvisLanguage.HUNGARIAN && it != JarvisLanguage.ENGLISH
        }) {
            for (key in JarvisTranslations.fixedTextKeys()) {
                org.junit.Assert.assertTrue("Missing " + language.tag + ": " + key,
                    JarvisTranslations.text(key, language).isNotBlank())
            }
        }
    }

    @Test fun eachAiPromptUsesRequestedReplyLanguage() {
        val expected = mapOf(
            "en-US" to "English",
            "de-DE" to "Deutsch",
            "fr-FR" to "français",
            "es-ES" to "español",
            "it-IT" to "italiano",
            "pl-PL" to "polsku",
            "hu-HU" to "magyarul"
        )
        expected.forEach { (tag, marker) ->
            val payload = JarvisAiToolPolicy.providerPayload("What is 2 plus 2?", tag)
            val prompt = payload.getJSONArray("messages").getJSONObject(0).getString("content")
            org.junit.Assert.assertTrue("Missing reply language " + tag, prompt.contains(marker))
        }
    }

    @Test fun userDataIsNotGuessedOrModified() {
        val note = "A user-created contact: George\n42 £"
        assertEquals(note, JarvisTranslations.text(note, JarvisLanguage.ENGLISH))
    }
}
