package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class JarvisStreamCancellationTest {
    private class FakeConnection : HttpURLConnection(URL("https://example.test/chat")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
    }

    @Test fun cancelsAnAlreadyOpenBlockingStream() {
        val token = JarvisStreamCancellation()
        val connection = FakeConnection()
        token.bind(connection)
        assertFalse(token.isCancelled())
        token.cancel()
        assertTrue(connection.disconnected)
        assertTrue(token.isCancelled())
        token.cancel() // idempotent
    }

    @Test fun cancellationBeforeConnectionIsCreatedStillClosesIt() {
        val token = JarvisStreamCancellation()
        token.cancel()
        val late = FakeConnection()
        token.bind(late)
        assertTrue(late.disconnected)
        assertTrue(token.isCancelled())
    }

    @Test fun releaseDoesNotCloseACompletedRequest() {
        val token = JarvisStreamCancellation()
        val completed = FakeConnection()
        token.bind(completed)
        token.release(completed)
        token.cancel()
        assertFalse(completed.disconnected)
    }
}
