package ai.jarvis.mobile

/**
 * Two requests with identical user text and progress text still represent
 * different chat entries. Locate the exact in-memory placeholder object,
 * not the first Pair that compares equal by value.
 *
 * Pair references survive Compose SnapshotStateList additions and chat-history
 * prepends. Replaced/completed entries correctly return -1.
 */
internal object JarvisPendingTurn {
    fun findIndex(turns: List<Pair<String, String>>, placeholder: Pair<String, String>): Int =
        turns.indexOfFirst { it === placeholder }

    /** A waiting turn stays in memory so streaming and completion can update its slot,
     * but it should never be displayed as an assistant reply or speech status. */
    fun isWaitingPlaceholder(text: String): Boolean =
        text == "AI-válaszra várok…" || text == "AI-válaszra várok"
}
