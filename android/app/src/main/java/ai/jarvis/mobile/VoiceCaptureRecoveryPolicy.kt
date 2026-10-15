package ai.jarvis.mobile

/** Fail over only after repeat cloud failures; never treat silence as an error. */
internal object VoiceCaptureRecoveryPolicy {
    private const val MAX_CLOUD_FAILURES = 2

    fun shouldFallback(consecutiveFailures: Int, androidRecognizerAvailable: Boolean): Boolean =
        androidRecognizerAvailable && consecutiveFailures >= MAX_CLOUD_FAILURES

    fun retryDelay(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= 1 -> 1_100L
        consecutiveFailures == 2 -> 2_500L
        else -> 5_000L
    }
}
