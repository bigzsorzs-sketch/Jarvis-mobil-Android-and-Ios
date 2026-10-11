package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceEmailTest {
    @Test fun draftAndNothingMore() {
        val draft = VoiceEmail.parse("Írj emailt hello@example.com tárgy Találkozó üzenet Holnap 10 órakor")
        assertNotNull(draft)
        assertEquals("hello@example.com", draft?.recipient)
        assertEquals("Találkozó", draft?.subject)
        assertEquals("Holnap 10 órakor", draft?.body)
        assertNull(VoiceEmail.parse("Nyisd meg az emaileket"))
    }
    @Test fun emptyAddressOpensBlankComposer() {
        val draft = VoiceEmail.parse("Küldj emailt")
        assertNotNull(draft)
        assertEquals("", draft?.recipient)
    }
}
