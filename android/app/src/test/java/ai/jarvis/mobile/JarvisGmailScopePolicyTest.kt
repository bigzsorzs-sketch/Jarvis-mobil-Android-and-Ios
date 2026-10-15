package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisGmailScopePolicyTest {
    @Test fun viewingInboxNeverRequestsMailSendAccess() {
        val requests = listOf(
            GmailOperation.Connect,
            GmailOperation.Check,
            GmailOperation.ListUnread,
            GmailOperation.Read(),
            GmailOperation.ResolveRecipient("Anna", false),
            GmailOperation.Send("Anna", "Hello", "Draft only", false)
        )
        requests.forEach { op ->
            assertEquals(op.toString(), setOf(JarvisGmailScopePolicy.READ),
                JarvisGmailScopePolicy.required(op))
        }
    }

    @Test fun actualSendAndReplyNeedProfileReadPlusSendPermissions() {
        // Gmail users.getProfile does not accept gmail.send alone; Jarvis
        // reads the authenticated sender address before constructing RFC822.
        assertEquals(JarvisGmailScopePolicy.ALL,
            JarvisGmailScopePolicy.required(
                GmailOperation.Send("someone@example.org", "Subject", "Body", true)))
        assertEquals(JarvisGmailScopePolicy.ALL,
            JarvisGmailScopePolicy.required(GmailOperation.Reply(null, "Thanks")))
    }

    @Test fun incompleteAndDisconnectNeverTriggerFreshAuthorization() {
        assertTrue(JarvisGmailScopePolicy.required(GmailOperation.Disconnect).isEmpty())
        assertTrue(JarvisGmailScopePolicy.required(
            GmailOperation.Incomplete("Missing recipient")).isEmpty())
    }

    @Test fun authDoesNotAssumeUnapprovedSendOrReadScopes() {
        assertTrue(JarvisGmailScopePolicy.granted(setOf(JarvisGmailScopePolicy.READ),
            listOf(JarvisGmailScopePolicy.READ)))
        assertFalse(JarvisGmailScopePolicy.granted(JarvisGmailScopePolicy.ALL,
            listOf(JarvisGmailScopePolicy.READ)))
        assertFalse(JarvisGmailScopePolicy.granted(setOf(JarvisGmailScopePolicy.SEND),
            listOf(JarvisGmailScopePolicy.READ)))
    }
}
