package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisVideoSamplingTest {
    @Test fun unknownAndTinyVideos() {
        assertTrue(JarvisVideoSampling.timestampsMs(0).isEmpty())
        assertTrue(JarvisVideoSampling.timestampsMs(-1).isEmpty())
        assertEquals(listOf(1L), JarvisVideoSampling.timestampsMs(2))
        assertEquals(1, JarvisVideoSampling.timestampsMs(8_000).size)
    }

    @Test fun samplesBoundedForLongVideo() {
        val duration = 2L * 60 * 60 * 1000
        val ts = JarvisVideoSampling.timestampsMs(duration)
        assertEquals(JarvisVideoSampling.MAX_FRAMES, ts.size)
        assertTrue(ts.zipWithNext().all { (a, b) -> b > a })
        assertTrue(ts.all { it in 0 until duration })
    }

    @Test fun shortVideoSpacedAndReadableDuration() {
        val ts = JarvisVideoSampling.timestampsMs(30_000)
        assertEquals(4, ts.size)
        assertTrue(ts.first() > 0)
        assertTrue(ts.last() < 30_000)
        assertEquals("1:05", JarvisVideoSampling.formatDuration(65_000))
        assertEquals("1:02:05", JarvisVideoSampling.formatDuration(3_725_000))
        assertEquals("ismeretlen", JarvisVideoSampling.formatDuration(0))
    }
}
