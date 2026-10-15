package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisQuietActionsTest {
    @Test fun successfulAmazonExecutionIsSilentInChat() {
        assertTrue(JarvisQuietActions.hideSuccessfulTurn(
            "keress az amazonon Costa kávé babot",
            "Megnyitottam az Amazon UK találatait: Costa kávé babot. " +
                "Jarvis még nem kapott ezekhez hivatalos termék-API hozzáférést."
        ))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "keress az amazonon Costa kávé babot",
            "Nem sikerült megnyitni az Amazont."
        ))
    }

    @Test fun explicitVoiceCallConfirmationAndFailuresRemainVisible() {
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "hívd drágám", "Ezt a számot hívjam? Mondd: igen vagy nem."))
        assertTrue(JarvisQuietActions.hideSuccessfulTurn(
            "igen, hívd", "A híváskérést átadtam az Androidnak."))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "küld emailt Várnai Ferencnek", "Nem találtam a címzettet."))
    }

    @Test fun gmailSendIsSilentButUnknownDeliveryIsNot() {
        assertTrue(JarvisQuietActions.hideSuccessfulTurn(
            "küldj emailt", "A Gmail elfogadta az emailt. Azonosító: abc123"))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "küldj emailt", "A küldés eredménye bizonytalan. Ellenőrizd a Gmailt."))
    }

    @Test fun nativeOpeningsAreSilentButUnverifiedNavigationIsNot() {
        assertTrue(JarvisQuietActions.hideSuccessfulTurn(
            "nyisd meg a jegyzeteket", "Megnyitottam a telefon jegyzetalkalmazását."))
        assertTrue(JarvisQuietActions.hideSuccessfulTurn(
            "nyisd meg a Beállítások oldalt", "Megnyitottam a Jarvis Beállítások oldalát."))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "vigyél Eszterhez",
            "Megnyitottam a név keresését a Google Mapsben. Válaszd ki a megfelelő találatot."))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "vigyél Leedsbe",
            "Megnyitottam az útvonalat. Az Indítás gombot még meg kell nyomni."))
        assertFalse(JarvisQuietActions.hideSuccessfulTurn(
            "nyisd meg a Maps alkalmazást", "Nem sikerült megnyitni a Google Mapset."))
    }

    @Test fun accountAndMailPromptsAreNotSwallowed() {
        assertTrue(JarvisGmailFeedback.needsAttention("Nem sikerült csatlakozni a Gmailhez."))
        assertTrue(JarvisGmailFeedback.needsAttention("Hiányzó Gmail-jogosultság."))
        assertFalse(JarvisGmailFeedback.needsAttention("A Gmail elfogadta az emailt."))
        assertFalse(JarvisGmailFeedback.needsAttention("Megnyitottam az Amazon oldalt."))
    }
}
