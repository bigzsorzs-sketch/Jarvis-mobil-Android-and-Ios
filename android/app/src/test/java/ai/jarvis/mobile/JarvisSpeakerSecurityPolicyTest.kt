package ai.jarvis.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No transcript, remembered opt-in or model response can replace device unlock. */
class JarvisSpeakerSecurityPolicyTest {
    @Test fun lockedDeviceNeverAuthorizesSensitiveOrSmartHomeCommands() {
        assertFalse(JarvisSpeakerSecurityPolicy.permitted(true, JarvisVoiceRisk.PERSONAL))
        assertFalse(JarvisSpeakerSecurityPolicy.permitted(true, JarvisVoiceRisk.LIGHTING))
        assertFalse(JarvisSpeakerSecurityPolicy.permitted(
            true, JarvisVoiceRisk.LIGHTING, lightingOptIn = true))
    }

    @Test fun unlockedDeviceMayContinueToNormalPerActionPermissionChecks() {
        assertTrue(JarvisSpeakerSecurityPolicy.permitted(false, JarvisVoiceRisk.PERSONAL))
        assertTrue(JarvisSpeakerSecurityPolicy.permitted(false, JarvisVoiceRisk.LIGHTING))
    }
}
