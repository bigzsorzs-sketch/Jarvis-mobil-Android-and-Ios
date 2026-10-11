package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoicePrivacyPolicyTest {
    @Test fun lockedDeviceIsPrivateByDefault() {
        assertFalse(VoicePrivacyPolicy.mayUsePrivateData(deviceLocked = true, explicitOptIn = false))
        assertTrue(VoicePrivacyPolicy.mayUsePrivateData(deviceLocked = false, explicitOptIn = false))
    }
    @Test fun explicitOptInIsRequiredForLockedPrivateActions() {
        assertTrue(VoicePrivacyPolicy.mayUsePrivateData(deviceLocked = true, explicitOptIn = true))
    }
}
