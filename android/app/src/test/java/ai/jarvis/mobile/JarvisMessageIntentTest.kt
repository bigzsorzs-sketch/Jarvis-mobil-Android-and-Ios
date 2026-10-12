package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisMessageIntentTest {
    @Test fun parsesHungarianAndEnglishExplicitDrafts() {
        assertEquals(
            OutgoingMessage(MessengerPlatform.WHATSAPP, "Eszternek", "Szia, este hívlak."),
            JarvisMessageIntent.parse("Jarvis, kérlek küldj WhatsApp üzenetet Eszternek, hogy Szia, este hívlak.")
        )
        assertEquals(
            OutgoingMessage(MessengerPlatform.MESSENGER, "Ferinél", "Indulok most"),
            JarvisMessageIntent.parse("Írj Messengeren Ferinél hogy Indulok most")
        )
        assertEquals(
            OutgoingMessage(MessengerPlatform.WHATSAPP, "Eszter", "On my way"),
            JarvisMessageIntent.parse("Send WhatsApp message to Eszter saying On my way")
        )
    }

    @Test fun rejectsNegativeAmbiguousAndIncompleteRequests() {
        assertNull(JarvisMessageIntent.parse("Ne küldj WhatsApp üzenetet Eszternek hogy Indulok"))
        assertNull(JarvisMessageIntent.parse("Mit tudsz a WhatsAppról?"))
        assertNull(JarvisMessageIntent.parse("Küldj WhatsApp üzenetet Eszternek"))
        assertNull(JarvisMessageIntent.parse("Írj WhatsApp-on Eszternek és Marikának hogy Szia"))
        assertNull(JarvisMessageIntent.parse("Írj WhatsApp-on Eszternek hogy "))
        assertNull(JarvisMessageIntent.parse("Írj SMS-t Eszternek hogy Szia"))
        assertNull(JarvisMessageIntent.parse("Írj WhatsAppon Eszternek hogy Szia\nViszlát"))
    }

    @Test fun validatesOnlyOriginalUserPayloadAndSelectedPlatform() {
        val original = "Írj WhatsAppon Eszternek hogy Jó reggelt!"
        val verified = JarvisMessageIntent.validateAiProposal("whatsapp", original)
        assertNotNull(verified)
        assertEquals("Eszternek", verified?.recipient)
        assertEquals("Jó reggelt!", verified?.text)
        assertNull(JarvisMessageIntent.validateAiProposal("messenger", original))
        assertNull(JarvisMessageIntent.validateAiProposal("sms", original))
        assertNull(JarvisMessageIntent.validateAiProposal("whatsapp", "Ne írj neki semmit"))
    }
}
