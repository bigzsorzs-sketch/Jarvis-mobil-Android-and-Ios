package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisBargeInGateTest {
    private fun samples(level: Int) = ShortArray(320) { level.toShort() }

    @Test fun steadyNarratorEchoAloneDoesNotTrigger() {
        val gate = JarvisBargeInGate()
        repeat(80) { assertFalse(gate.observe(samples(2_200), 320)) }
    }

    @Test fun userVoiceInterruptsOnlyAfterSustainedLevelRise() {
        val gate = JarvisBargeInGate()
        repeat(12) { assertFalse(gate.observe(samples(200), 320)) }
        repeat(8) { assertFalse(gate.observe(samples(2_500), 320)) }
        assertTrue(gate.observe(samples(2_500), 320))
    }

    @Test fun isolatedNoiseBurstDoesNotInterrupt() {
        val gate = JarvisBargeInGate()
        repeat(12) { gate.observe(samples(100), 320) }
        repeat(6) { assertFalse(gate.observe(samples(8_000), 320)) }
        repeat(2) { assertFalse(gate.observe(samples(100), 320)) }
        repeat(6) { assertFalse(gate.observe(samples(8_000), 320)) }
    }

    @Test fun smallSampleAndSilenceNeverTrigger() {
        val gate = JarvisBargeInGate()
        repeat(40) { assertFalse(gate.observe(samples(0), 320)) }
        assertFalse(gate.observe(samples(30_000), 0))
    }

    @Test fun conversationChunksStartShortAndLoseNoContent() {
        val long = "Ez egy fontos mondat. " +
            "A beszélgetés közben hanggenerálás zajlik, ezért minden karakter számít. ".repeat(35)
        val parts = JarvisSpeechChunks.splitForConversation(long)
        assertTrue(parts.size > 2)
        assertTrue(parts.first().length <= 261)
        assertEquals(long, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 850 })
    }

    @Test fun shortAnswersAreNotFragmented() {
        assertEquals(listOf("Szia!"), JarvisSpeechChunks.splitForConversation("Szia!"))
        assertTrue(JarvisSpeechChunks.splitForConversation("").isEmpty())
    }
}
