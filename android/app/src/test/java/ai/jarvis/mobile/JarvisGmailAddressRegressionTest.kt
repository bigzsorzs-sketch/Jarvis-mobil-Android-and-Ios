package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisGmailAddressRegressionTest {
    @Test fun standaloneEmailCanOnlyBeParsedAsRecipientAndIsNotAnAction() {
        assertEquals("example+test@gmail.com",
            GmailDraftConversationFlow.recipientAddress(" example+test@gmail.com "))
        assertNull(GmailDraftConversationFlow.recipientAddress("Jarvis"))
        assertNull(GmailDraftConversationFlow.recipientAddress("Navigate to Leeds"))
        assertNull(GmailDraftConversationFlow.recipientAddress("email@example.com send"))
        assertNull(GmailDraftConversationFlow.recipientAddress("not-an-address"))
        assertNull(JarvisGmailCommands.parse("example+test@gmail.com"))
        assertNull(VoiceEmail.parse("example+test@gmail.com"))
    }

    @Test fun wakeWordCannotAccidentallyBecomeEmailSubjectOrRecipient() {
        assertTrue(GmailDraftConversationFlow.isWakeWord("Jarvis"))
        assertTrue(GmailDraftConversationFlow.isWakeWord("Hey Jarvis!"))
        assertFalse(GmailDraftConversationFlow.isWakeWord("Jarvis írj emailt"))
        val pending = GmailDraftConversation(
            "recipient@example.com", false, expiresAtMs = Long.MAX_VALUE)
        assertEquals(GmailDraftNext.NotRelated,
            GmailDraftConversationFlow.advance(pending, "Jarvis"))
        assertEquals(GmailDraftNext.NotRelated,
            GmailDraftConversationFlow.advance(pending, "other@example.com"))
    }

    @Test fun explicitGmailDraftRemainsTheOnlySendPath() {
        val command = JarvisGmailCommands.parse("Írj emailt Eszternek")
        assertNotNull(command)
        assertTrue(command!!.operation is GmailOperation.ResolveRecipient)
        val draft = GmailDraftConversation(
            "recipient@example.com", false, expiresAtMs = Long.MAX_VALUE)
        val next = GmailDraftConversationFlow.advance(draft, "Heti takarítás")
        assertTrue(next is GmailDraftNext.Updated)
        val ready = GmailDraftConversationFlow.advance(
            (next as GmailDraftNext.Updated).draft, "Holnap délután érkezünk.")
        assertTrue(ready is GmailDraftNext.Ready)
        assertEquals("recipient@example.com", (ready as GmailDraftNext.Ready).draft.recipient)
        assertEquals("Holnap délután érkezünk.", ready.draft.body)
        assertTrue(GmailDraftConversationFlow.advance(ready.draft, "küldd el")
            is GmailDraftNext.Complete)
    }
}
