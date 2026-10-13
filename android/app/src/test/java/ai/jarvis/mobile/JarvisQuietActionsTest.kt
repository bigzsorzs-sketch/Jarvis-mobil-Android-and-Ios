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

    @Test fun accountAndMailPromptsAreNotSwallowed() {
        assertTrue(JarvisGmailFeedback.needsAttention("Nem sikerült csatlakozni a Gmailhez."))
        assertTrue(JarvisGmailFeedback.needsAttention("Hiányzó Gmail-jogosultság."))
        assertFalse(JarvisGmailFeedback.needsAttention("A Gmail elfogadta az emailt."))
        assertFalse(JarvisGmailFeedback.needsAttention("Megnyitottam az Amazon oldalt."))
    }
}
