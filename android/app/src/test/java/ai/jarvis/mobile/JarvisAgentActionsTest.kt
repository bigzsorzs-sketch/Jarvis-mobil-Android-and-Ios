package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAgentActionsTest {
    @Test fun multiStepUserCommandsAreParsedFromActualFragments() {
        val steps = JarvisAgentActions.eligible(
            "Nyisd meg a Beállítások oldalát, majd kapcsold be a sötét módot")
        assertNotNull(steps)
        assertEquals(2, steps!!.size)
        assertEquals(AgentKind.PAGE, steps[0].kind)
        assertEquals("Beállítások", steps[0].target)
        assertEquals(AgentKind.THEME, steps[1].kind)
        assertEquals("dark", steps[1].target)
        assertTrue(JarvisAgentActions.authorized(
            "Nyisd meg a Beállítások oldalát, majd kapcsold be a sötét módot",
            steps[1]))
    }

    @Test fun calendarHandoffMustBeTheLastStep() {
        val after = JarvisAgentActions.eligible(
            "Kapcsold be a sötét módot, majd nyisd meg a Naptár oldalt")
        assertNotNull(after)
        assertEquals(AgentKind.PHONE_APP, after!!.last().kind)
        assertEquals("calendar", after.last().target)
        assertNull(JarvisAgentActions.eligible(
            "Nyisd meg a Naptár oldalt, majd kapcsold be a sötét módot"))
    }

    @Test fun navigationIsOnlyAllowedAtEndOfPlan() {
        val safe = JarvisAgentActions.eligible(
            "Kapcsold be a sötét módot, majd navigálj Leedsbe")
        assertNotNull(safe)
        assertEquals(AgentKind.NAVIGATION, safe!!.last().kind)
        assertNull(JarvisAgentActions.eligible(
            "Navigálj Leedsbe, majd kapcsold be a sötét módot"))
    }

    @Test fun unsafeOrAmbiguousCommandsNeverEnterAgent() {
        for (request in listOf(
            "Hívd fel Esztert, majd küldj neki üzenetet",
            "Nyisd meg a bankot, majd fizess 100 fontot",
            "Nyisd meg a térképet és állítsd be Eszter címét",
            "Ne kapcsold be a sötét módot, majd nyisd meg a Beállításokat",
            "Nyisd meg a Beállítások oldalát, majd töröld a fájlokat",
            "Nyisd meg a Beállítások oldalát, majd telepíts új appot",
            "Nyisd meg az Email oldalát, majd küldj levelet"
        )) assertNull(request, JarvisAgentActions.eligible(request))
    }

    @Test fun modelCannotSwapAddSkipOrEditOriginalActions() {
        val original = "Nyisd meg a Beállítások oldalát, majd kapcsold be a sötét módot"
        val allowed = JarvisAgentActions.eligible(original)!!
        val valid = """{"steps":[{"index":1,"action":"PAGE"},{"index":2,"action":"THEME"}]}"""
        assertTrue(JarvisAgentActions.validModelPlan(valid, allowed))
        for (invalid in listOf(
            """{"steps":[{"index":2,"action":"THEME"},{"index":1,"action":"PAGE"}]}""",
            """{"steps":[{"index":1,"action":"PAGE"}]}""",
            """{"steps":[{"index":1,"action":"PAGE"},{"index":2,"action":"THEME"}],"execute":true}""",
            """{"steps":[{"index":1,"action":"PAGE","target":"Számlák"},{"index":2,"action":"THEME"}]}""",
            """{"steps":[{"index":1,"action":"CALL"},{"index":2,"action":"THEME"}]}""",
            "CONTINUE",
            "INVALID"
        )) assertFalse(invalid, JarvisAgentActions.validModelPlan(invalid, allowed))
        assertFalse(JarvisAgentActions.authorized(original,
            allowed[1].copy(target = "light")))
    }
}
