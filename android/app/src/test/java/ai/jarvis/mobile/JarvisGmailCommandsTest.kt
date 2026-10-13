package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisGmailCommandsTest {
    @Test fun hungarianSendIsRealActionOnlyWhenRequested() {
        val command = JarvisGmailCommands.parse(
            "küldj emailt anna@example.com tárgy Találkozó üzenet Holnap 10-kor")
        assertNotNull(command)
        val op = command!!.operation as GmailOperation.Send
        assertTrue(op.actuallySend)
        assertEquals("anna@example.com", op.recipient)
        assertEquals("Találkozó", op.subject)
        assertEquals("Holnap 10-kor", op.body)
    }

    @Test fun writeMeansDraftNotSend() {
        val op = JarvisGmailCommands.parse(
            "írj emailt anna@example.com tárgy Szia üzenet Holnap hívlak")!!.operation
        assertFalse((op as GmailOperation.Send).actuallySend)
    }

    @Test fun incompleteMailDoesNotSend() {
        val named = JarvisGmailCommands.parse("írj egy emailt a Várnai Ferencnek")!!
            .operation as GmailOperation.ResolveRecipient
        assertEquals("Várnai Ferenc", named.name)
        assertFalse(named.actuallySend)
        val sendNamed = JarvisGmailCommands.parse("küld email Várnai Ferencnek")!!
            .operation as GmailOperation.ResolveRecipient
        assertEquals("Várnai Ferenc", sendNamed.name)
        assertTrue(sendNamed.actuallySend)
        assertTrue(JarvisGmailCommands.parse("küldj emailt anna@example.com")!!.operation
            is GmailOperation.ResolveRecipient)
    }

    @Test fun canAskUnreadAndReadAloud() {
        assertEquals(GmailOperation.Check,
            JarvisGmailCommands.parse("Van új emailem?")!!.operation)
        assertTrue(JarvisGmailCommands.parse("Olvasd fel az új emailjeimet")!!.operation
            is GmailOperation.Read)
        assertEquals(GmailOperation.Check,
            JarvisGmailCommands.parse("check my emails")!!.operation)
    }

    @Test fun hungarianEmailListingsRouteToGmailNotGenericAi() {
        listOf(
            "milyen új emailek vannak?",
            "Milyen új levelek vannak?",
            "Sorold fel az új emailjeimet",
            "Mutasd az új emaileket",
            "What new emails do I have?",
            "show unread emails"
        ).forEach { input ->
            assertEquals(input, GmailOperation.ListUnread,
                JarvisGmailCommands.parse(input)?.operation)
        }
        assertEquals(GmailOperation.Check,
            JarvisGmailCommands.parse("van új emailem?")?.operation)
        assertNull(JarvisGmailCommands.parse("milyen új filmek vannak?"))
    }

    @Test fun mailboxNameResolutionNotPhoneContactDraft() {
        val new = JarvisGmailCommands.parse("Küldj emailt Várnai Ferencnek")!!.operation
        assertTrue(new is GmailOperation.ResolveRecipient)
        assertTrue((new as GmailOperation.ResolveRecipient).actuallySend)
        assertEquals(GmailOperation.Check,
            JarvisGmailCommands.parse("Van új emailem?")!!.operation)
    }

    @Test fun replyKeepsUserContentAndSender() {
        val op = JarvisGmailCommands.parse(
            "Válaszolj Ferenc emailjére hogy Holnap délután jó")!!.operation
                as GmailOperation.Reply
        assertEquals("Ferenc", op.sender)
        assertEquals("Holnap délután jó", op.body)
        val latest = JarvisGmailCommands.parse(
            "válaszolj az utolsó emailre, hogy köszönöm")!!.operation
                as GmailOperation.Reply
        assertNull(latest.sender)
    }

    @Test fun noMailActionForUnrelatedOrNegatedStatements() {
        assertNull(JarvisGmailCommands.parse("navigálj Harrogate-be"))
        assertNull(JarvisGmailCommands.parse("nyisd meg a térképet"))
        assertNull(JarvisGmailCommands.parse("Olvasd fel a mesét"))
    }

    @Test fun englishComposeAndRead() {
        val op = JarvisGmailCommands.parse(
            "send email to alice@example.org subject Catch up message See you tomorrow")!!.operation
            as GmailOperation.Send
        assertEquals("alice@example.org", op.recipient)
        assertEquals("Catch up", op.subject)
        assertEquals("See you tomorrow", op.body)
        assertTrue(JarvisGmailCommands.parse("read my new emails")!!.operation is GmailOperation.Read)
    }
}
