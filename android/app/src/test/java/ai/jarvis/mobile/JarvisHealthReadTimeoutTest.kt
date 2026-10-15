package ai.jarvis.mobile

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/**
 * JUnit4 requires public test methods to return void/Unit.
 * Do not use expression-bodied '= runBlocking { ... }' tests when the
 * final assertion (e.g. assertThrows) returns a Throwable object.
 */
class JarvisHealthReadTimeoutTest {
    @Test fun quickAuthorizedHealthProviderKeepsItsReading() {
        runBlocking {
            assertEquals("verified-reading",
                JarvisHealthReadTimeout.fetch(2_000) { "verified-reading" })
        }
    }

    @Test fun stalledHealthProviderCannotHoldVoiceForever() {
        runBlocking {
            val result = JarvisHealthReadTimeout.fetch(60) {
                delay(4_000)
                "should-never-be-presented"
            }
            assertNull(result)
        }
    }

    @Test fun stopCancelsExternalGlucoseQueryInsteadOfReturningCachedSuccess() {
        runBlocking {
            var reachedAfterLookup = false
            val job = launch {
                JarvisHealthReadTimeout.fetch {
                    delay(60_000)
                    "old-reading"
                }
                reachedAfterLookup = true
            }
            yield()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(reachedAfterLookup)
        }
    }

    @Test fun invalidTimeoutCannotSilentlyDisableTheSafetyBound() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { JarvisHealthReadTimeout.fetch(0) { "value" } }
        }
    }
}
