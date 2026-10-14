package ai.jarvis.mobile

/**
 * Android SpeechRecognizer performs actual acoustic processing/noise reduction.
 * Passive/wake-word mode filters very low confidence hypotheses. For an
 * explicitly user-started microphone session, do not discard recognized words
 * just because an OEM returns poor confidence scores: Android already decoded
 * an utterance, and command authorization still happens in the executor.
 * Jarvis does not secretly capture raw PCM to apply unsupported noise DSP.
 */
internal object VoiceTranscriptSelection {
    private const val MIN_CONFIDENCE = 0.30f

    fun select(
        hypotheses: List<String>,
        confidences: FloatArray?,
        userInitiated: Boolean = false
    ): String? {
        val entries = hypotheses.mapIndexedNotNull { index, phrase ->
            val text = phrase.trim().replace(Regex("""\s+"""), " ")
            if (text.isBlank() || text.length > 1_500) null
            else {
                val score = confidences?.getOrNull(index) ?: -1f
                if (!userInitiated && score in 0f..MIN_CONFIDENCE) null else text to score
            }
        }
        return entries.maxByOrNull {
            // Unknown (-1) still works, but reported strong candidates win.
            it.second.takeIf { score -> score >= 0 } ?: -1f
        }?.first
    }
}
