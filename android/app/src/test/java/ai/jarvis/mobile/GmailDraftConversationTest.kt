package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class GmailDraftConversationTest {
    @Test fun findsOnlyExactlyMatchingMailboxNameAndSentRecipients() {
        assertEquals(setOf("ferenc@example.com"), GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "\"Várnai Ferenc\" <ferenc@example.com>"))
        assertEquals(setOf("ferenc@example.com"), GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "Other Person <other@example.com>, Várnai Ferenc <ferenc@example.com>"))
        assertEquals(setOf("ferenc@example.com"), GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "Ferenc Várnai <ferenc@example.com>"))
        assertTrue(GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "Várnai Ferencz <different@example.com>").isEmpty())
        assertTrue(GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "someone@example.com").isEmpty())
        assertTrue(GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "Other Person <ferenc@example.com>").isEmpty())
        assertEquals(setOf("ferenc@example.com"), GmailMailboxHeader.matchAddress(
            "Várnai Ferenc", "=?UTF-8?B?VsOhcm5haSBGZXJlbmM=?= <ferenc@example.com>"))
    }

    @Test fun subjectBodyAreCollectedSeparatelyNeverInvented() {
        val initial = GmailDraftConversation("ferenc@example.com", true, expiresAtMs = 9000L)
        val step = GmailDraftConversationFlow.advance(initial, "Tárgy: Holnapi találkozó")
            as GmailDraftNext.Updated
        assertEquals("Holnapi találkozó", step.draft.subject)
        val result = GmailDraftConversationFlow.advance(step.draft,
            "Szöveg: Holnap délután négykor találkozunk.") as GmailDraftNext.Complete
        assertEquals("ferenc@example.com", result.send.recipient)
        assertEquals("Holnapi találkozó", result.send.subject)
        assertEquals("Holnap délután négykor találkozunk.", result.send.body)
        assertTrue(result.send.actuallySend)
    }

    @Test fun unrelatedQuestionDoesNotStealPendingDraft() {
        val initial = GmailDraftConversation("ferenc@example.com", true, expiresAtMs = 9000L)
        assertEquals(GmailDraftNext.NotRelated,
            GmailDraftConversationFlow.advance(initial, "milyen új emailek vannak?"))
        assertEquals(GmailDraftNext.Cancelled,
            GmailDraftConversationFlow.advance(initial, "mégse"))
        assertNull(GmailDraftConversationFlow.choice("1", 0))
        assertEquals(0, GmailDraftConversationFlow.choice("1", 2))
        assertEquals(1, GmailDraftConversationFlow.choice("második", 2))
    }

    @Test fun writingRequestCannotAutoSend() {
        val draft = GmailDraftConversation("ferenc@example.com", false,
            subject = "Holnapi találkozó", expiresAtMs = 9000L)
        val ready = GmailDraftConversationFlow.advance(draft,
            "Szöveg: Köszönöm a leveled.") as GmailDraftNext.Ready
        assertEquals("Köszönöm a leveled.", ready.draft.body)
        assertEquals(GmailDraftNext.NotRelated,
            GmailDraftConversationFlow.advance(ready.draft, "Hány óra van?"))
        val result = GmailDraftConversationFlow.advance(ready.draft,
            "küld el") as GmailDraftNext.Complete
        assertTrue(result.send.actuallySend)
        assertEquals("ferenc@example.com", result.send.recipient)
        assertEquals("Holnapi találkozó", result.send.subject)
        assertEquals("Köszönöm a leveled.", result.send.body)
        assertEquals(GmailDraftNext.Cancelled,
            GmailDraftConversationFlow.advance(ready.draft, "mégse"))
        assertFalse(GmailDraftConversationFlow.isExplicitSend("ne küldd el"))
        assertFalse(GmailDraftConversationFlow.isExplicitSend("küldd el a többit"))
        assertTrue(GmailDraftConversationFlow.isExplicitSend("Send it"))
    }
}
