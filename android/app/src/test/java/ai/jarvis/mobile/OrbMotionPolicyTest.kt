package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class OrbMotionPolicyTest {
    @Test fun noMovementWhileIdleOrOnlyListening() {
        assertFalse(OrbMotionPolicy.animate(false, false))
    }
    @Test fun actualUserOrJarvisSpeechAnimates() {
        assertTrue(OrbMotionPolicy.animate(true, false))
        assertTrue(OrbMotionPolicy.animate(false, true))
        assertTrue(OrbMotionPolicy.animate(true, true))
    }
}
