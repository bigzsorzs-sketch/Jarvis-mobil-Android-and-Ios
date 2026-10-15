package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

/** Regression coverage for real screenshots: a call cannot hijack new commands. */
class JarvisCommandContextTest {
    @Test fun applicationAndJarvisPageCommandsInterruptPreviousCalls() {
        for (command in listOf(
            "nyisd meg a Messengert",
            "nyisd meg a WhatsApp-ot",
            "Nyisd meg a Beállításokat",
            "Nyisd meg az AI-agy oldalt",
            "Nyisd meg az Engedélyek menüt",
            "navigálj Leedsbe",
            "hívd fel Esztert",
            "nézd meg a hívás előzményeimet",
            "akkor milyen funkciókat tudsz használni?",
            "Hogy vagy?",
            "de igen ott van benne"
        )) {
            assertTrue("New request must cancel the old phone call: $command",
                JarvisCommandContext.independentRequest(command))
            assertNull(JarvisCommandContext.possibleContactReply(command))
        }
    }

    @Test fun onlyShortPlausibleContactAndChoicesRemainInCallState() {
        for (candidate in listOf("Vivi", "Vivi Kovács", "Drágám", "Eszter", "07478051290")) {
            assertFalse("Contact should be possible follow-up: $candidate",
                JarvisCommandContext.independentRequest(candidate))
            assertNotNull(candidate, JarvisCommandContext.possibleContactReply(candidate))
        }
        for (confirmation in listOf("igen, hívd", "nem", "első")) {
            assertFalse(JarvisCommandContext.independentRequest(confirmation))
            assertNull(JarvisCommandContext.possibleContactReply(confirmation))
        }
        assertNull(JarvisCommandContext.possibleContactReply("akkor milyen funkciókat tudsz használni?"))
    }

    @Test fun shortSpokenCallApprovalKeepsOnlyTheActiveConfirmedNumber() {
        for (approval in listOf("igen", "igen, hívd", "igen, hívd fel", "yes", "call now")) {
            assertTrue("Explicit approval not recognized: $approval",
                VoiceCalling.callConfirmationYes(approval))
            assertFalse("Confirmation must not lose active contact: $approval",
                JarvisCommandContext.shouldPreemptCallConfirmation(approval, true))
        }
        // No pending contact = a bare call command must request a recipient.
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("hívd", false))
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("hívd fel", false))
        // A new named recipient or a different action invalidates stale approval.
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("Hívd fel Vivit", true))
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("navigálj Leedsbe", true))
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("Hogy vagy?", true))
        // Saying a name is not authorization to place a call.
        assertFalse(VoiceCalling.callConfirmationYes("Drágám"))
        assertFalse(VoiceCalling.callConfirmationYes("nem"))
    }

    @Test fun normalizesUKPhonebookDuplicatesButNeverChoosesDifferentNumbers() {
        assertEquals("+447478051290", VoiceCalling.canonicalPhone("07478 051290"))
        assertEquals("+447478051290", VoiceCalling.canonicalPhone("+44 7478 051290"))
        val exact = listOf(
            VoiceCalling.Target("Drágám", "07478 051290"),
            VoiceCalling.Target("Drágám", "+44 7478 051290"))
        assertTrue(VoiceCalling.selectPhonebookMatch("Drágám", exact) is VoiceCalling.Lookup.Found)
        val multiple = exact + VoiceCalling.Target("Drágám", "07478 000000")
        assertEquals(VoiceCalling.Lookup.Ambiguous,
            VoiceCalling.selectPhonebookMatch("Drágám", multiple))
        val options = VoiceCalling.matchingPhonebookRows("Drágám", multiple)
        assertEquals(2, options.size)
        assertEquals(options[1], VoiceCalling.selectedChoice("második", options))
        assertNull(VoiceCalling.selectedChoice("harmadik", options))
    }

    @Test fun capabilityQuestionsUseLocalCatalogNotAnOldCallOrInventedAiActions() {
        assertTrue(JarvisCapabilities.isDiscoveryRequest(
            "akkor milyen funkciókat tudsz használni?"))
        val answer = JarvisCapabilities.replyIfAsked(
            "akkor milyen funkciókat tudsz használni?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("Jarvis"))
    }
}
