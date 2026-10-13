package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

/** No AI model is allowed to plan/trigger arbitrary screen taps. */
class JarvisScreenControlTest {
    @Test fun explicitUserSystemCommandsMapToExactlyOneAction() {
        val samples = mapOf(
            "vissza" to JarvisScreenControl.Action.Back,
            "menj vissza" to JarvisScreenControl.Action.Back,
            "go back" to JarvisScreenControl.Action.Back,
            "menj a kezdőképernyőre" to JarvisScreenControl.Action.Home,
            "nyisd meg az értesítéseket" to JarvisScreenControl.Action.Notifications,
            "gyorsbeállítások" to JarvisScreenControl.Action.QuickSettings,
            "görgess le" to JarvisScreenControl.Action.ScrollDown,
            "scroll up" to JarvisScreenControl.Action.ScrollUp)
        for ((spoken, expected) in samples) {
            assertEquals(spoken, expected, JarvisScreenControl.parse(spoken))
            assertTrue(JarvisCommandContext.independentRequest(spoken))
        }
    }

    @Test fun exactVisibleLabelIsOneStepAndNeverSensitiveApproval() {
        val safe = JarvisScreenControl.parse("Koppints a Keresés gombra")
        assertTrue(safe is JarvisScreenControl.Action.TapLabel)
        assertEquals("kereses", (safe as JarvisScreenControl.Action.TapLabel).value)
        for (input in listOf(
            "nyomd meg a fizetés gombot", "koppints a törlés gombra",
            "nyomd meg a delete button", "tap send", "click approve",
            "nyomd meg az engedélyezés gombot", "nem koppints a keresésre",
            "koppints a keresés és a küldés gombra",
            "Hogy vagy?", "Hívd fel Vivit"
        )) {
            assertNull(input, JarvisScreenControl.parse(input))
        }
    }

    @Test fun externalAppsRemainIndependentOfGenericScreenTapActions() {
        assertNull(JarvisScreenControl.parse("Nyisd meg a Messengert"))
        assertNull(JarvisScreenControl.parse("Nyisd meg a WhatsAppot"))
        assertTrue(JarvisCommandContext.independentRequest("Nyisd meg a Messengert"))
    }
}
