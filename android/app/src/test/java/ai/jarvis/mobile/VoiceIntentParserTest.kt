package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceIntentParserTest {
    @Test fun wakeWordOnly() {
        assertNull(VoiceIntentParser.afterWakeWord("Csak beszélgetünk Jarvisról"))
        assertEquals("", VoiceIntentParser.afterWakeWord("Hey Jarvis"))
        assertEquals("nyisd meg a térképet", VoiceIntentParser.afterWakeWord("Hé Jarvis, nyisd meg a térképet"))
        assertEquals("nyisd meg a naptárat", VoiceIntentParser.afterWakeWord("Jarvis, nyisd meg a naptárat"))
        assertEquals("nyisd meg a térképet", VoiceIntentParser.afterWakeWord("Hé, Jarvis! nyisd meg a térképet"))
        assertEquals("", VoiceIntentParser.afterWakeWord("Jarvis"))
        assertNull(VoiceIntentParser.afterWakeWord("Beszéljünk a Jarvis alkalmazásról"))
        assertNull(VoiceIntentParser.afterWakeWord("Hey Jarvisnak mondtam"))
    }

    @Test fun confirmRequiresExplicitWord() {
        assertTrue(VoiceIntentParser.isYes("Igen"))
        assertFalse(VoiceIntentParser.isYes("igen, valami mást"))
        assertTrue(VoiceIntentParser.isNo("Mégse"))
        assertTrue(VoiceIntentParser.isStop("Kapcsold ki a figyelést"))
    }

    @Test fun targetedMessageReply() {
        assertTrue(VoiceIntentParser.isReadMessage("Olvasd fel az utolsó WhatsApp üzenetet"))
        assertFalse(VoiceIntentParser.isReadMessage("Olvasd fel a vércukromat"))
        assertFalse(VoiceIntentParser.isReadMessage("Olvasd fel a PIN-kódom"))
        assertEquals("WhatsApp", VoiceIntentParser.messengerHint("Olvasd fel a WhatsApp üzenetet"))
        assertEquals("20 perc múlva hazaérek", VoiceIntentParser.replyDraft("Válaszolj neki hogy 20 perc múlva hazaérek"))
        assertNull(VoiceIntentParser.replyDraft("válaszolj neki"))
    }
    @Test fun readMessageCommandsKeepTheirPrivateNotificationHandlerInSevenLanguages() {
        val phrases = listOf(
            "Olvasd fel a WhatsApp üzenetet",
            "Read aloud my WhatsApp message",
            "Lies die WhatsApp Nachricht vor",
            "Lis moi le message WhatsApp",
            "Lee el mensaje de WhatsApp",
            "Leggi il messaggio WhatsApp",
            "Przeczytaj wiadomość WhatsApp"
        )
        phrases.forEach { assertTrue(it, VoiceIntentParser.isReadMessage(it)) }
        assertFalse(VoiceIntentParser.isReadMessage("Olvass fel egy mesét"))
    }

    @Test fun messageConsentCannotTriggerPhoneCall() {
        org.junit.Assert.assertTrue(VoiceIntentParser.isCallYes("igen"))
        org.junit.Assert.assertTrue(VoiceIntentParser.isCallYes("yes"))
        org.junit.Assert.assertFalse(VoiceIntentParser.isCallYes("küldd el"))
        org.junit.Assert.assertFalse(VoiceIntentParser.isCallYes("send"))
        org.junit.Assert.assertFalse(VoiceIntentParser.isCallYes("nem"))
    }

    @Test fun workflowConsentCannotBeMistakenForSendingOrCalling() {
        assertTrue(VoiceIntentParser.isSequenceYes("Igen"))
        assertTrue(VoiceIntentParser.isSequenceYes("Mehet"))
        assertFalse(VoiceIntentParser.isSequenceYes("Küldd el"))
        assertFalse(VoiceIntentParser.isSequenceYes("Send"))
        assertFalse(VoiceIntentParser.isSequenceYes("Call now"))
        assertFalse(VoiceIntentParser.isSequenceYes("Hívd fel"))
    }

}

