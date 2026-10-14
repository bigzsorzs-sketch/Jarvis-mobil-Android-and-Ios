package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

/**
 * Exact requests which motivated the Android personal-assistant work.
 * Tests intentionally distinguish a prepared cross-app handoff from
 * verified execution inside WhatsApp/Messenger/Gmail/DWP.
 */
class UserScenariosTest {
    @Test fun callMyPartnerViaConfirmedAlias() {
        assertEquals("drágámat", VoiceCalling.extractRequest("Hívd fel a drágámat"))
        assertTrue(PeopleDirectory.matchingName("drágámat", "Drágám"))
    }
    @Test fun openWhatsappContactWithExplicitSelection() {
        assertEquals(
            OutgoingMessage(MessengerPlatform.WHATSAPP, "suly-t", ""),
            ExternalTasks.parseMessage("WhatsApp-on keresd meg suly-t és írj neki egy üzit")
        )
    }
    @Test fun messengerContactSelectionIsStillRequired() {
        val parsed = ExternalTasks.parseMessage(
            "Messengeren keresd meg a Ferenc Várnait és írj neki egy üzenetet"
        )
        assertEquals(MessengerPlatform.MESSENGER, parsed?.platform)
        assertEquals("Ferenc Várnait", parsed?.recipient)
    }
    @Test fun ucIsOfficialSignInHandoffOnly() {
        assertTrue(ExternalTasks.parseUcJournal(
            "Nyisd meg a Chrome-ot, lépj be az UC accountomba és nézd meg az új journal message-et"
        ))
        assertEquals("https://www.gov.uk/sign-in-universal-credit", ExternalTasks.UC_OFFICIAL_SIGNIN)
    }
    @Test fun mapsRequiresPrivateContactAddressForNamedNavigation() {
        assertEquals("PERSON:Esztert", VoiceNavigation.destinationFrom(
            "Nyisd meg a térképet és állítsd be az Esztert címnek"))
        assertEquals("PERSON:Eszter", VoiceNavigation.destinationFrom("Navigálj Eszterhez"))
    }
    @Test fun vagueEmailCannotBeTruthfullySearched() {
        assertNull(ExternalTasks.parseGmailSearch("Keresd meg ezt az emailt és írj neki választ"))
    }
}
