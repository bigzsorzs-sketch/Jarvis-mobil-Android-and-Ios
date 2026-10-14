package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAgentResearchTest {
    @Test fun explicitDeepTechnicalRequestsCanUseBoundedAgent() {
        assertTrue(JarvisAgentResearch.shouldHandle(
            "Elemezd alaposan a Jarvis Android mikrofon hibáját"))
        assertTrue(JarvisAgentResearch.shouldHandle(
            "Research in depth the Kotlin coroutine crash"))
        assertFalse(JarvisAgentResearch.shouldHandle("Szia Jarvis"))
        assertFalse(JarvisAgentResearch.shouldHandle(
            "Nyisd meg a térképet és navigálj Manchesterbe"))
        assertFalse(JarvisAgentResearch.shouldHandle(
            "Kutass: az API kulcs sk-or-secret javításához"))
    }

    @Test fun onlyBoundedResearchPlansAreAccepted() {
        val valid = """{"steps":["Kotlin coroutines cancellation behaviour","Android speech recognizer lifecycle"]}"""
        assertEquals(2, JarvisAgentResearch.parsePlan(valid)?.size)
        assertEquals(1, JarvisAgentResearch.parsePlan(
            """{"steps":["Android lifecycle documentation"]}""")?.size)
        assertNull(JarvisAgentResearch.parsePlan(
            """{"steps":["Android lifecycle documentation"],"execute":true}"""))
        assertNull(JarvisAgentResearch.parsePlan(
            """{"steps":["first","second","third"]}"""))
        assertNull(JarvisAgentResearch.parsePlan(
            """{"steps":["Run code from file://private/key"]}"""))
        assertNull(JarvisAgentResearch.parsePlan(
            """{"steps":["Kotlin lifecycle","Kotlin lifecycle"]}"""))
        assertNull(JarvisAgentResearch.parsePlan("Open the phone camera"))
    }

    @Test fun sequenceConfirmsExactStoredAlarmBeforeOpeningSecondApp() {
        val now = System.currentTimeMillis()
        val future = java.util.Calendar.getInstance().apply {
            timeInMillis = now + 60L * 60 * 1000
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val whenAlarm = java.util.Calendar.getInstance().apply { timeInMillis = future }
        val time = AlarmTime(whenAlarm.get(java.util.Calendar.HOUR_OF_DAY),
            whenAlarm.get(java.util.Calendar.MINUTE))
        assertTrue(JarvisSequence.alarmMatches(future, time, now))
        assertFalse(JarvisSequence.alarmMatches(0L, time, now))
        assertFalse(JarvisSequence.alarmMatches(future, AlarmTime(
            (time.hour + 1) % 24, time.minute), now))
        assertFalse(JarvisSequence.alarmMatches(future - 48L * 60 * 60 * 1000, time, now))
    }
}
