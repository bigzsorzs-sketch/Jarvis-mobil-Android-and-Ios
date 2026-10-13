package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAssistantSelectionTest {
    @Test fun assistantRolePromptRequiresAndroid10AndAvailableUnheldRole() {
        assertFalse(JarvisAssistantSelection.needsRolePrompt(28, true, false))
        assertFalse(JarvisAssistantSelection.needsRolePrompt(29, false, false))
        assertFalse(JarvisAssistantSelection.needsRolePrompt(35, true, true))
        assertTrue(JarvisAssistantSelection.needsRolePrompt(29, true, false))
        assertTrue(JarvisAssistantSelection.needsRolePrompt(36, true, false))
    }
}
