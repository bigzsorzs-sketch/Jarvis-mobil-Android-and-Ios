package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSupabasePkceTest {
    @Test fun standardRfc7636Sha256Challenge() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            JarvisSupabaseAuth.challenge(verifier))
    }

    @Test fun generatedChallengeHasUrlSafeAlphabet() {
        val challenge = JarvisSupabaseAuth.challenge("a".repeat(43))
        assertEquals(43, challenge.length)
        assertTrue(challenge.matches(Regex("[A-Za-z0-9_-]+")))
    }
}
