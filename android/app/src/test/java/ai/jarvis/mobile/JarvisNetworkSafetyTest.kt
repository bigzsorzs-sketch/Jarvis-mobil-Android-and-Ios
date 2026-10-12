package ai.jarvis.mobile

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class JarvisNetworkSafetyTest {
    @Test fun readsSmallUtf8Response() {
        val text = """{"reply":"Szia, működik"}"""
        assertEquals(text, JarvisNetworkSafety.readUtf8Limited(
            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))))
    }

    @Test fun exactLimitAcceptedAndOverLimitRejected() {
        assertEquals("éé", JarvisNetworkSafety.readUtf8Limited(
            ByteArrayInputStream("éé".toByteArray(Charsets.UTF_8)), 4))
        try {
            JarvisNetworkSafety.readUtf8Limited(ByteArrayInputStream(ByteArray(5)), 4)
            fail("Must refuse oversized responses")
        } catch (expected: IllegalArgumentException) {
            assertEquals("AI_RESPONSE_TOO_LARGE", expected.message)
        }
    }

    @Test fun neverAcceptsInvalidLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            JarvisNetworkSafety.readUtf8Limited(ByteArrayInputStream(byteArrayOf(1)), 0)
        }
    }
}
