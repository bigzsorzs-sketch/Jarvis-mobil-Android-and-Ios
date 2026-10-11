package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class ExternalTasksTest {
    @Test fun detectsTargetedMessaging() {
        assertEquals(
            OutgoingMessage(MessengerPlatform.WHATSAPP, "suly-t", ""),
            ExternalTasks.parseMessage("WhatsApp-on keresd meg suly-t és írj neki egy üzit")
        )
        assertEquals(
            OutgoingMessage(MessengerPlatform.MESSENGER, "Ferenc Várnait", ""),
            ExternalTasks.parseMessage("Messengeren keresd meg a Ferenc Várnait és írj neki egy üzenetet")
        )
    }

    @Test fun newWhatsappDraftWithMessage() {
        assertEquals(
            OutgoingMessage(MessengerPlatform.WHATSAPP, "Sulynak", "Szia, később hívlak"),
            ExternalTasks.parseMessage("Írj WhatsApp-on Sulynak hogy Szia, később hívlak")
        )
        assertNull(ExternalTasks.parseGmailSearch("Keresd meg ezt az emailt és írj rá választ"))
        assertEquals("számla", ExternalTasks.parseGmailSearch("Keresd meg számla emailt"))
    }
    @Test fun ucNavigationIsExplicit() {
        assertTrue(ExternalTasks.parseUcJournal("Nézd meg van-e új üzenet a UC journalban"))
        assertFalse(ExternalTasks.parseUcJournal("Hívd fel a drágámat"))
    }
}
