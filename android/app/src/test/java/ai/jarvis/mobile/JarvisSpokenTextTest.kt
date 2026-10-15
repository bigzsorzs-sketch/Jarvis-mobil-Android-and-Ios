package ai.jarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisSpokenTextTest {
    @Test fun removesFormattingWithoutDroppingMeaning() {
        assertEquals("Fontos különbség például email.",
            JarvisSpokenText.clean("Fontos **különbség** (pl. email)."))
        assertEquals("A B csillag", JarvisSpokenText.clean("A / B *csillag*"))
        assertEquals("Cím\nelső\nmásodik",
            JarvisSpokenText.clean("# Cím\n- első\n- második"))
        assertEquals("A Google Maps itt nyitható.",
            JarvisSpokenText.clean("[A Google Maps](https://maps.google.com) itt nyitható."))
    }

    @Test fun preservesConfirmationMeaningAndHumanSpeech() {
        assertEquals("Hívd fel Esztert?", JarvisSpokenText.clean("Hívd fel Esztert?"))
        assertEquals("Igen, hívd fel.", JarvisSpokenText.clean("Igen, hívd fel."))
        assertEquals("Fontos pont", JarvisSpokenText.clean("**Fontos** pont"))
    }
}
