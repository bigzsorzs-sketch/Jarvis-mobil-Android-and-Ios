package ai.jarvis.mobile

import java.net.HttpURLConnection

/**
 * Closes an interrupted OpenRouter HTTP stream immediately. Coroutine.cancel()
 * alone does not reliably interrupt Java's blocking InputStream.readLine().
 * A cancellation arriving before the connection is opened is also respected.
 */
internal class JarvisStreamCancellation {
    private var cancelled = false
    private var live: HttpURLConnection? = null

    @Synchronized fun bind(connection: HttpURLConnection) {
        if (cancelled) connection.disconnect() else live = connection
    }

    @Synchronized fun cancel() {
        cancelled = true
        live?.disconnect()
        live = null
    }

    @Synchronized fun release(connection: HttpURLConnection?) {
        if (live === connection) live = null
    }

    @Synchronized fun isCancelled(): Boolean = cancelled
}
