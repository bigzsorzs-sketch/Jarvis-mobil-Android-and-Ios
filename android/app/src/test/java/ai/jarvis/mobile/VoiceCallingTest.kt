package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceCallingTest {
    @Test fun politeHungarianCallsStillNeedRealRecipientAndConfirmation() {
        assertEquals("Esztert", VoiceCalling.extractRequest("Légy szíves, hívd fel Esztert"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Figyelj, légy szíves hívd fel Vivit"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Figyelj, légy szíves, hívd fel Vivit"))
        assertNull(VoiceCalling.extractRequest("Légy szíves, hívd fel Esztert és Vivit"))
        assertNull(VoiceCalling.extractRequest("Légy szíves, ne hívd fel Esztert"))
        assertTrue(VoiceCalling.needsTarget("Légy szíves hívd fel"))
    }

    @Test fun aiCallsMustComeFromExplicitUserIntentAndMatchTarget() {
        assertEquals("Esztert", VoiceCalling.extractAiRequest("Jarvis, kérlek hívd fel Esztert"))
        assertEquals("Esztert", VoiceCalling.extractAiRequest("Fel tudnád hívni Esztert?"))
        assertEquals("+441234567890", VoiceCalling.extractAiRequest("Call +441234567890"))
        assertNull(VoiceCalling.extractAiRequest("Ne hívd fel Esztert"))
        assertNull(VoiceCalling.extractAiRequest("Mit tudsz Eszterről?"))
        assertNull(VoiceCalling.extractAiRequest("Hívd fel Esztert és Marit"))
        assertTrue(VoiceCalling.matchesAiTarget("Esztert", "Eszter"))
        assertFalse(VoiceCalling.matchesAiTarget("Esztert", "Mari"))
        assertFalse(VoiceCalling.matchesAiTarget("+441234567890", "+441234567891"))
    }

    @Test fun systemPhonebookNamesAndSevenCallLanguages() {
        assertTrue(VoiceCalling.matchesFirstName("Vivit", "Vivi Nagy"))
        assertTrue(VoiceCalling.matchesContactName("Esztert", "Eszter"))
        assertFalse(VoiceCalling.matchesFirstName("Viv", "Vivi Nagy"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Jarvis, hívd fel Vivit"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Call Vivi"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Ruf an Vivi"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Appelle Vivi"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Llama a Vivi"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Chiama Vivi"))
        assertEquals("Vivi", VoiceCalling.extractRequest("Zadzwoń do Vivi"))
        assertNull(VoiceCalling.extractRequest("Don't call Vivi"))
    }

    @Test fun naturalHungarianCallOrdersAndMissingTargetAskForNumber() {
        assertEquals("Vivit", VoiceCalling.extractRequest("Vivit hívd fel"))
        assertEquals("Esztert", VoiceCalling.extractRequest("Esztert tárcsázd"))
        assertEquals("+441234567890", VoiceCalling.extractRequest("A +441234567890 számot hívd fel"))
        assertNull(VoiceCalling.extractRequest("az adott telefonszámot hívd fel"))
        assertNull(VoiceCalling.extractRequest("hívd fel az adott telefonszámot"))
        assertNull(VoiceCalling.extractRequest("hívd fel"))
        assertTrue(VoiceCalling.needsTarget("az adott telefonszámot hívd fel"))
        assertTrue(VoiceCalling.needsTarget("Hívd fel a telefonszámot"))
        assertTrue(VoiceCalling.needsTarget("Hívd fel"))
        assertFalse(VoiceCalling.needsTarget("Hívd fel Esztert"))
        assertFalse(VoiceCalling.needsTarget("Ne hívd fel Esztert"))
        assertEquals("+447911123456", VoiceCalling.followUpTarget("+447911123456"))
        assertEquals("Vivit", VoiceCalling.followUpTarget("Vivit"))
        assertNull(VoiceCalling.followUpTarget("az adott telefonszámot"))
        assertNull(VoiceCalling.followUpTarget("Nyisd meg a térképet"))
    }

    @Test fun phonebookLookupUsesUniqueMatchesNotFirstRowsOrModelGuesses() {
        val contacts = listOf(
            VoiceCalling.Target("Vivi Nagy", "07478000001"),
            VoiceCalling.Target("Vivi Nagy", "07478000001"), // duplicate Android provider row
            VoiceCalling.Target("Vivi Kovács", "07478000002")
        )
        assertEquals(VoiceCalling.Lookup.Ambiguous,
            VoiceCalling.selectPhonebookMatch("Vivit", contacts))
        val exact = VoiceCalling.selectPhonebookMatch("Vivi Nagy", contacts)
        assertTrue(exact is VoiceCalling.Lookup.Found)
        assertEquals("07478000001", (exact as VoiceCalling.Lookup.Found).target.number)
        assertEquals(VoiceCalling.Lookup.NotFound,
            VoiceCalling.selectPhonebookMatch("Marit", contacts))
        val repeated = VoiceCalling.selectPhonebookMatch(
            "Vivit", contacts.filter { it.label == "Vivi Nagy" })
        assertTrue(repeated is VoiceCalling.Lookup.Found)
    }

    @Test fun chatCallIntentsAndConfirmationWorkWithPhonebook() {
        assertEquals("vivit", VoiceCalling.extractRequest("hívd vivit"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Jarvis, hívd fel Vivit"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Vivit hívd fel"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Hívj Vivit"))
        assertTrue(VoiceCalling.callConfirmationYes("hívd"))
        assertTrue(VoiceCalling.callConfirmationYes("hívd fel"))
        assertFalse(VoiceCalling.callConfirmationYes("drágám"))
        assertFalse(VoiceCalling.callConfirmationYes("hívd fel Vivit"))
        assertTrue(VoiceCalling.callConfirmationYes("igen hívd"))
        assertTrue(VoiceCalling.callConfirmationYes("igen, hívd fel"))
        assertFalse(VoiceCalling.callConfirmationYes("nem"))
        assertFalse(VoiceCalling.callConfirmationYes("vivi"))
        assertEquals("Vivi", VoiceCalling.statedNumber("ez a Vivi száma 07478 051290")?.label)
        assertEquals("07478051290",
            VoiceCalling.statedNumber("ez a Vivi száma 07478 051290")?.number)
        assertNull(VoiceCalling.statedNumber("Vivi"))
    }

    @Test fun hungarianDialCommands() {
        assertEquals("Esztert", VoiceCalling.extractRequest("Hívd fel Esztert"))
        assertEquals("George", VoiceCalling.extractRequest("Tárcsázd George"))
        assertEquals("+4412345678", VoiceCalling.extractRequest("Call +4412345678"))
        assertNull(VoiceCalling.extractRequest("Ki hívott fel?"))
        assertNull(VoiceCalling.extractRequest("Hívjuk fel valamikor Esztert"))
        assertNull(VoiceCalling.extractRequest(""))
    }
}
