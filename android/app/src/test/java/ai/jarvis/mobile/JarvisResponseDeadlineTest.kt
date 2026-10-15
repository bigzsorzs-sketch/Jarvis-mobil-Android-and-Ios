package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisResponseDeadlineTest {
    @Test fun simpleVoiceCommandsAndSmallTalkHaveFiniteNetworkDeadlines() {
        assertEquals(45_000, JarvisResponseDeadline.upstreamMs("Szia"))
        assertEquals(100_000, JarvisResponseDeadline.upstreamMs("Milyen idő van?"))
        assertEquals(45_000, JarvisResponseDeadline.upstreamMs("Hívd fel Esztert", hasTools = true))
    }

    @Test fun complexAndExplicitDeepTasksCanWaitLongerWithout44MinuteHang() {
        assertEquals(240_000, JarvisResponseDeadline.upstreamMs("Compare these options"))
        assertEquals(600_000, JarvisResponseDeadline.upstreamMs("Gondold át alaposan"))
        assertEquals(615_000, JarvisResponseDeadline.socketReadMs("Gondold át alaposan"))
        assertEquals(630_000L, JarvisResponseDeadline.foregroundVoiceMs("Gondold át alaposan"))
        assertTrue(JarvisResponseDeadline.upstreamMs("Gondold át alaposan") < 2_640_000)
        assertEquals(128_000, JarvisReasoningPolicy.forQuestion("Gondold át alaposan", false).maxTokens)
    }
}
