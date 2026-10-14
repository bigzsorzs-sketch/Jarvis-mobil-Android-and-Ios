package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSseDecoderTest {
    @Test fun readsTextDeltasAndIgnoresMetadataAndToolLikeEvents() {
        val d = JarvisSseDecoder()
        assertNull(d.accept(": ping"))
        assertNull(d.accept("event: message"))
        assertEquals("Jó", d.accept("""data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Jó"}}]}"""))
        assertEquals(" estét!", d.accept("""data: {"choices":[{"index":0,"delta":{"content":" estét!"}}]}"""))
        assertNull(d.accept("""data: {"choices":[{"delta":{"tool_calls":[{"id":"x"}]}}]}"""))
        assertEquals("Jó estét!", d.text)
        assertNull(d.accept("data: [DONE]"))
        assertTrue(d.done)
        assertNull(d.accept("""data: {"choices":[{"delta":{"content":" extra"}}]}"""))
        assertEquals("Jó estét!", d.text)
    }

    @Test fun completedSentenceCanBeSpokenBeforeWholeAnswerFinishes() {
        val phrases = JarvisIncrementalPhrases()
        assertTrue(phrases.accept("Ez egy gyors ").isEmpty())
        assertTrue(phrases.accept("válasz a kérdésedre, ").isEmpty())
        val early = phrases.accept("amelynek első mondata már elkészült. A második mondat később jön.")
        assertEquals(listOf("Ez egy gyors válasz a kérdésedre, amelynek első mondata már elkészült."), early)
        assertEquals("A második mondat később jön.", phrases.finish())
        assertEquals("", phrases.finish())
    }

    @Test fun largeUnpunctuatedResponseCannotAccumulateForever() {
        val p = JarvisIncrementalPhrases()
        val phrases = p.accept("szöveg ".repeat(85))
        assertTrue(phrases.isNotEmpty())
        assertTrue(phrases.all { it.length <= 205 })
        assertTrue(p.finish().length < 205)
    }

    @Test fun malformedEventsAndNonTextDeltasCannotExecuteAnything() {
        val d = JarvisSseDecoder()
        assertNull(d.accept("data: {not-json}"))
        assertNull(d.accept("""data: {"choices":[{"delta":{"content":null}}]}"""))
        assertNull(d.accept("""data: {"choices":[{"delta":{"content":[{"type":"tool_call"}]}}]}"""))
        assertEquals("", d.text)
        assertFalse(d.done)
    }

    @Test fun streamErrorOrUnexpectedToolCallCannotLookLikeSuccessfulAnswer() {
        val error = JarvisSseDecoder()
        assertNull(error.accept("""data: {"error":{"message":"provider failed"}}"""))
        assertTrue(error.done)
        assertNotNull(error.failure)
        assertEquals("", error.text)

        val tool = JarvisSseDecoder()
        assertNull(tool.accept("""data: {"choices":[{"delta":{"tool_calls":[{"id":"123"}]},"finish_reason":"tool_calls"}]}"""))
        assertTrue(tool.done)
        assertNotNull(tool.failure)

        val limited = JarvisSseDecoder()
        assertEquals("Első rész", limited.accept(
            """data: {"choices":[{"delta":{"content":"Első rész"}}]}"""))
        assertNull(limited.accept(
            """data: {"choices":[{"delta":{},"finish_reason":"length"}]}"""))
        assertTrue(limited.done)
        assertNotNull(limited.failure)
        assertEquals("Első rész", limited.text)
    }

}
