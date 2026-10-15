package ai.jarvis.mobile

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Health Connect is a separate Android service and a provider may stall.
 * A fresh reading is optional: after a bounded timeout Jarvis must still
 * report the newest stored reading with its real timestamp and source.
 * Parent coroutine cancellation (STOP/new voice turn) is propagated.
 */
internal object JarvisHealthReadTimeout {
    const val MAX_WAIT_MS = 12_000L

    suspend fun <T> fetch(timeoutMs: Long = MAX_WAIT_MS, lookup: suspend () -> T?): T? {
        require(timeoutMs in 1..MAX_WAIT_MS)
        return withTimeoutOrNull(timeoutMs) { lookup() }
    }
}
