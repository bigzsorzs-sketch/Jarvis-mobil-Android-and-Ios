package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class LocalConversationTest {
    @Test fun greetingsHandleAccentsCaseWakeWordAndPunctuation() {
        val expected = "Jó reggelt! Miben segíthetek?"
        listOf("jó reggelt", "JÓ REGGELT!!!", "Jarvis, jó reggelt!",
            "Hey Jarvis jó reggelt", "jo reggelt", "good morning")
            .forEach { assertEquals(it, expected, LocalConversation.reply(it)) }
        assertEquals("Szia! Miben segíthetek?", LocalConversation.reply("Helló 👋"))
        assertEquals("Szívesen!", LocalConversation.reply("köszönöm!"))
    }

    @Test fun greetingsDoNotSwallowActionsInTheSameSentence() {
        val command = "Jó reggelt, nyisd meg a jegyzeteket"
        assertNull(LocalConversation.reply(command))
        assertEquals(BasicAction.OPEN_NOTES, BasicCommandRouter.classify(command))
    }

    @Test fun generalQuestionsDoNotGetInventedOfflineAnswers() {
        assertNull(LocalConversation.reply("Mennyi a mai bitcoin árfolyam?"))
        assertNull(LocalConversation.reply("jó reggelt holnap hány órakor kelsz"))
        assertNull(LocalConversation.reply(" "))
    }
}
