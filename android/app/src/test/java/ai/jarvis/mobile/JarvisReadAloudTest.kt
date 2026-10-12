package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisReadAloudTest {
    @Test fun recognizesReadAloudInAllSevenLanguages() {
        val commands = listOf(
            "Olvass fel egy mesét",
            "Read aloud a short story",
            "Lies mir eine Geschichte vor",
            "Lis moi une histoire",
            "Lee en voz alta un cuento",
            "Leggi ad alta voce una storia",
            "Przeczytaj krótką historię"
        )
        commands.forEach {
            assertTrue(it, JarvisReadAloudCommand.parse(it) is ReadAloudRequest.Generate)
        }
    }

    @Test fun literalTextIsPrivateAndPreserved() {
        val request = JarvisReadAloudCommand.parse(
            "Jarvis, olvasd fel ezt a szöveget: Holnap 8:30-kor találkozunk. £85.")
        assertTrue(request is ReadAloudRequest.Literal)
        assertEquals("Holnap 8:30-kor találkozunk. £85.", (request as ReadAloudRequest.Literal).text)
        assertEquals("Ez a címzett számára magánüzenet.", (JarvisReadAloudCommand.parse(
            "Read aloud this: Ez a címzett számára magánüzenet.") as ReadAloudRequest.Literal).text)
    }

    @Test fun previousAnswerIsLocalAndQuestionsAreNotMisclassified() {
        val last = listOf(
            "Olvasd fel az előző válaszodat",
            "Read aloud the last answer",
            "Lies mir die letzte Antwort vor",
            "Lis moi la dernière réponse",
            "Lee en voz alta la última respuesta",
            "Leggi ad alta voce l'ultima risposta",
            "Przeczytaj poprzednią odpowiedź"
        )
        last.forEach { assertEquals(it, ReadAloudRequest.LastAnswer,
            JarvisReadAloudCommand.parse(it)) }
        assertNull(JarvisReadAloudCommand.parse("Miért nem tudsz felolvasni?"))
        assertNull(JarvisReadAloudCommand.parse("Is there a TTS engine installed?"))
        assertEquals(ReadAloudRequest.LastAnswer, JarvisReadAloudCommand.parse("Olvasd fel"))
    }

    @Test fun longTextCanBePlayedWithoutLosingAnyWord() {
        val text = (1..4000).joinToString(" ") { "word$it" }
        val parts = JarvisSpeechChunks.split(text)
        assertTrue(parts.size > 5)
        assertTrue(parts.all { it.length <= 2600 })
        assertEquals(text, parts.joinToString("").replace(Regex("\\s+"), " ").trim())
    }

    @Test fun extremelyLongWordAndUnicodeRemainValidChunks() {
        val noSpaces = "á🌍" .repeat(2200)
        val chunks = JarvisSpeechChunks.split(noSpaces, 60)
        assertTrue(chunks.all { it.length <= 60 })
        assertEquals(noSpaces, chunks.joinToString(""))
    }

    @Test fun translatedLabelsAndLanguagePromptsExistForAllLanguages() {
        for (lang in JarvisLanguage.entries) {
            for (key in listOf("started", "stopped", "stop", "read", "finished",
                "failed", "missingTts", "noAnswer")) {
                assertTrue(JarvisReadAloudCommand.response(lang, key).isNotBlank())
            }
            assertTrue(JarvisReadAloudCommand.generationPrompt(
                "Read aloud a story", lang).endsWith("Read aloud a story"))
        }
    }
}
