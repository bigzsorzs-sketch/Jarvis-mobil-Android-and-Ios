package ai.jarvis.mobile

/**
 * Avoid keeping an Android HTTP thread or hands-free microphone session
 * waiting up to 45 minutes for a dead provider. A longer, finite deadline
 * is granted only to explicit medium/high depth requests. This does not
 * reduce Jarvis' separate maximum response-token budget.
 */
internal object JarvisResponseDeadline {
    fun upstreamMs(question: String, hasTools: Boolean = false): Int {
        if (hasTools) return 45_000
        return when (JarvisReasoningPolicy.forQuestion(question, false).effort) {
            "none" -> 45_000
            "low" -> 100_000
            "medium" -> 240_000
            "high" -> 600_000
            else -> 100_000
        }
    }

    // Allow an upstream gateway to return its own timeout/failure response,
    // instead of dropping the network connection first.
    fun socketReadMs(question: String): Int = upstreamMs(question) + 15_000

    // Long user-requested explanations must not be silently cancelled by
    // the old fixed 100-second hands-free timeout before GPT finishes.
    fun foregroundVoiceMs(question: String): Long = upstreamMs(question).toLong() + 30_000L
}
