package ai.jarvis.mobile

/**
 * Immutable request ids for foreground AI handoff. Two identical speech
 * transcripts are still separate turns: the old provider result may NOT act
 * on behalf of the newer microphone request.
 *
 * Owned and advanced only on the main thread by the foreground Activity.
 */
internal class VoiceTurnGeneration {
    private var current = 0L

    fun next(): Long {
        current += 1L
        return current
    }

    fun accepts(id: Long): Boolean = id == current
}
