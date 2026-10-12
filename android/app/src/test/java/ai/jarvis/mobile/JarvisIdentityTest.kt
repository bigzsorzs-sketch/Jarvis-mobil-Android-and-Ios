package ai.jarvis.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisIdentityTest {
    @Test fun introducesIndependentJarvisIdentity() {
        val prompt = JarvisIdentity.systemPrompt
        assertTrue(prompt.contains("Jarvis Mobile"))
        assertTrue(prompt.contains("NEM az OpenAI ChatGPT alkalmazása"))
        assertTrue(prompt.contains("külső nyelvi modellt"))
    }

    @Test fun describesActualCapabilitiesAndBoundaries() {
        val prompt = JarvisIdentity.systemPrompt
        assertTrue(prompt.contains("vércukornapló"))
        assertTrue(prompt.contains("Navigáció"))
        assertTrue(prompt.contains("nem futtathat automatikusan Android-eszközműveleteket"))
        assertTrue(prompt.contains("mobilos önjavítást"))
        assertFalse(prompt.isBlank())
    }
}
