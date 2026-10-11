package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceIntentParserTest {
    @Test fun wakeWordOnly() {
        assertNull(VoiceIntentParser.afterWakeWord("Csak beszélgetünk Jarvisról"))
        assertEquals("", VoiceIntentParser.afterWakeWord("Hey Jarvis"))
        assertEquals("nyisd meg a térképet", VoiceIntentParser.afterWakeWord("Hé Jarvis, nyisd meg a térképet"))
    }

    @Test fun confirmRequiresExplicitWord() {
        assertTrue(VoiceIntentParser.isYes("Igen"))
        assertFalse(VoiceIntentParser.isYes("igen, valami mást"))
        assertTrue(VoiceIntentParser.isNo("Mégse"))
        assertTrue(VoiceIntentParser.isStop("Kapcsold ki a figyelést"))
    }

    @Test fun targetedMessageReply() {
        assertTrue(VoiceIntentParser.isReadMessage("Olvasd fel az utolsó WhatsApp üzenetet"))
        assertEquals("WhatsApp", VoiceIntentParser.messengerHint("Olvasd fel a WhatsApp üzenetet"))
        assertEquals("20 perc múlva hazaérek", VoiceIntentParser.replyDraft("Válaszolj neki hogy 20 perc múlva hazaérek"))
        assertNull(VoiceIntentParser.replyDraft("válaszolj neki"))
    }
}
