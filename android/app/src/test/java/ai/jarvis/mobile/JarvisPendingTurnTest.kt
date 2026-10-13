package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisPendingTurnTest {
    @Test fun identicalRequestsKeepDistinctSlotsEvenWhenAnswersArriveOutOfOrder() {
        val first = "same question" to "AI-válaszra várok…"
        val second = "same question" to "AI-válaszra várok…"
        val turns = mutableListOf(first, second)
        assertEquals(first, second)
        assertEquals(1, JarvisPendingTurn.findIndex(turns, second))
        // The second request returns first; the first must remain pending.
        turns[JarvisPendingTurn.findIndex(turns, second)] = second.first to "second answer"
        assertEquals(0, JarvisPendingTurn.findIndex(turns, first))
        turns[JarvisPendingTurn.findIndex(turns, first)] = first.first to "first answer"
        assertEquals("first answer", turns[0].second)
        assertEquals("second answer", turns[1].second)
        assertEquals(-1, JarvisPendingTurn.findIndex(turns, first))
    }

    @Test fun historyPrependPreservesPendingReferenceAndCompletedPairIsNotPending() {
        val pending = "file question" to "Checking attachments…"
        val turns = mutableListOf(pending)
        turns.add(0, "history" to "old response")
        assertEquals(1, JarvisPendingTurn.findIndex(turns, pending))
        turns[1] = pending.first to "done"
        assertEquals(-1, JarvisPendingTurn.findIndex(turns, pending))
    }
}
