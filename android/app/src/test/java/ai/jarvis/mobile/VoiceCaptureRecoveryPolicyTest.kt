package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceCaptureRecoveryPolicyTest {
    @Test fun oneCloudErrorDoesNotPrematurelyChangeRecognizer() {
        assertFalse(VoiceCaptureRecoveryPolicy.shouldFallback(0, true))
        assertFalse(VoiceCaptureRecoveryPolicy.shouldFallback(1, true))
    }

    @Test fun twoConsecutiveFailuresUseAndroidOnlyWhenAvailable() {
        assertTrue(VoiceCaptureRecoveryPolicy.shouldFallback(2, true))
        assertTrue(VoiceCaptureRecoveryPolicy.shouldFallback(3, true))
        assertFalse(VoiceCaptureRecoveryPolicy.shouldFallback(2, false))
        assertFalse(VoiceCaptureRecoveryPolicy.shouldFallback(20, false))
    }

    @Test fun failedCloudRetryDoesNotBecomeBusyRapidFireLoop() {
        assertEquals(1_100L, VoiceCaptureRecoveryPolicy.retryDelay(1))
        assertEquals(2_500L, VoiceCaptureRecoveryPolicy.retryDelay(2))
        assertEquals(5_000L, VoiceCaptureRecoveryPolicy.retryDelay(4))
    }
}
