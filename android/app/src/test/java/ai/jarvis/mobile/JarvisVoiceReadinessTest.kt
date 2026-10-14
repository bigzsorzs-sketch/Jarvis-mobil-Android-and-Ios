package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisVoiceReadinessTest {
    @Test fun voiceConsentMissingHasActionableStatus() {
        val response = JarvisVoiceReadiness.reasonForState(true, false, true, true)
        assertTrue(response.orEmpty().contains("AI-hang"))
        assertTrue(response.orEmpty().contains("Beállítások"))
    }

    @Test fun expiredOrMissingConnectionCannotSilentlyPretendToSpeak() {
        val response = JarvisVoiceReadiness.reasonForState(true, true, false, false)
        assertTrue(response.orEmpty().contains("AI-kapcsolat"))
    }

    @Test fun cloudTextConsentDoesNotImplicitlyGrantVoiceConsent() {
        assertNotNull(JarvisVoiceReadiness.reasonForState(false, true, true, true))
        assertNotNull(JarvisVoiceReadiness.reasonForState(true, false, true, true))
        assertNull(JarvisVoiceReadiness.reasonForState(true, true, true, true))
    }

    @Test fun credentialReadinessCannotBeAssumedFromVoiceConsent() {
        assertNotNull(JarvisVoiceReadiness.reasonForState(true, true, true, false))
    }
}
