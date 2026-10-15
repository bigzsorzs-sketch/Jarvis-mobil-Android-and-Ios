package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisVoiceSinglePassTest {
    @Test fun ordinaryVoiceQuestionReusesTheAlreadyReturnedAiAnswer() {
        val result = JarvisAiOutcome("Elmondom röviden.", conversationReply = true)
        assertTrue(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", result))
    }

    @Test fun providerFailureIsNotAutomaticallyRepeatedOrChargedTwice() {
        val result = JarvisAiOutcome("A szolgáltató most nem elérhető.")
        assertTrue(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", result))
    }

    @Test fun unvalidatedModelToolProposalNeverSkipsNativeChecks() {
        val proposedCall = JarvisAiOutcome(
            "Hívást javasolok.", callTarget = "Eszter")
        val proposedRoute = JarvisAiOutcome(
            "Megnyitom.", route = "Beállítások")
        assertFalse(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", proposedCall))
        assertFalse(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", proposedRoute))
    }

    @Test fun explicitPhoneCallStillUsesContactsAndPositiveConfirmation() {
        assertFalse(JarvisAiRouting.mayReuseVoiceResponse(
            "Hívd fel Esztert", JarvisAiOutcome("Már hívom.")))
    }

    @Test fun offlineOrUnconfiguredModeCannotReuseCloudAnswer() {
        assertFalse(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", JarvisAiOutcome("Válasz."), false))
        assertFalse(JarvisAiRouting.mayReuseVoiceResponse(
            "Mesélj nekem a Holdról!", JarvisAiOutcome(""), true))
    }
    @Test fun directPlainVoiceChatUsesIncrementalAnswerWhenAvailable() {
        assertTrue(JarvisAiRouting.mayStreamVoiceTurn(
            "Mesélj nekem a Holdról!", incrementalAvailable = true))
        assertFalse(JarvisAiRouting.mayStreamVoiceTurn(
            "Mesélj nekem a Holdról!", incrementalAvailable = false))
    }

    @Test fun speechStreamingDoesNotBypassNativePhoneRouting() {
        assertFalse(JarvisAiRouting.mayStreamVoiceTurn(
            "Hívd fel Esztert", incrementalAvailable = true))
    }

}
