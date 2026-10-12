package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisPhoneActionsTest {
    @Test fun opensOnlyExplicitlyNamedAppAndChecksOriginalUserMessage() {
        assertTrue(JarvisPhoneActions.authorized("calendar", "Nyisd meg a naptárat"))
        assertTrue(JarvisPhoneActions.authorized("maps", "Jarvis, kérlek nyisd meg a térképet"))
        assertTrue(JarvisPhoneActions.authorized("alarms", "Mutasd az ébresztőket"))
        assertTrue(JarvisPhoneActions.authorized("email", "Open the email app"))
        assertTrue(JarvisPhoneActions.authorized("dialer", "Nyisd meg a tárcsázót"))
        assertFalse(JarvisPhoneActions.authorized("maps", "Nyisd meg a naptárat"))
        assertFalse(JarvisPhoneActions.authorized("shell", "Nyisd meg a terminált"))
    }

    @Test fun noNegationsQuestionsMultipleOrUnsafeInstructions() {
        assertFalse(JarvisPhoneActions.authorized("maps", "Ne nyisd meg a térképet"))
        assertFalse(JarvisPhoneActions.authorized("maps", "Tudsz térképet nyitni?"))
        assertFalse(JarvisPhoneActions.authorized("maps", "Nyisd meg a térképet és a naptárat"))
        assertFalse(JarvisPhoneActions.authorized("email", "Nyisd meg az emailt és küldd el a levelet"))
        assertFalse(JarvisPhoneActions.authorized("dialer", "Indítsd a telefonhívást"))
    }

    private fun toolCall(name: String, route: String): JSONObject =
        JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name)
                .put("arguments", JSONObject().put("route", route).toString()))

    @Test fun providerPhoneActionIsNotTextOrArbitraryExecution() {
        val tool = toolCall(JarvisAiTools.PHONE_FUNCTION, "calendar")
        val body = JSONObject().put("choices", JSONArray().put(
            JSONObject().put("message", JSONObject().put("tool_calls", JSONArray().put(tool)))))
        assertEquals("calendar", JarvisAiTools.fromProvider(body, "Nyisd meg a naptárat").deviceAction)
        assertNull(JarvisAiTools.fromProvider(body, "Mi a naptár?").deviceAction)
        assertNull(JarvisAiTools.fromProvider(body, "Nyisd meg a térképet").deviceAction)
    }

    @Test fun privateServerMustPassSameOnDeviceValidation() {
        val request = JSONObject().put("action", JSONObject()
            .put("name", JarvisAiTools.PHONE_FUNCTION).put("route", "maps"))
        assertEquals("maps", JarvisAiTools.fromServer(request, "Nyisd meg a térképet").deviceAction)
        assertNull(JarvisAiTools.fromServer(request, "Nyisd meg a naptárat").deviceAction)
        assertNull(JarvisAiTools.fromServer(JSONObject().put("action", JSONObject()
            .put("name", "run_shell").put("route", "maps")), "Nyisd meg a térképet").deviceAction)
    }
}
