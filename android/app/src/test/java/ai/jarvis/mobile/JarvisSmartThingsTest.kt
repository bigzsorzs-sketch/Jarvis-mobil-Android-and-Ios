package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisSmartThingsTest {
    private fun item(name: String, category: String, switch: Boolean): JSONObject {
        val caps = JSONArray()
        if (switch) caps.put(JSONObject().put("id", "switch"))
        return JSONObject()
            .put("deviceId", "aaaabbbb-1111-2222-3333-123456789abc")
            .put("label", name)
            .put("components", JSONArray().put(JSONObject()
                .put("id", "main")
                .put("categories", JSONArray().put(JSONObject().put("name", category)))
                .put("capabilities", caps)))
    }

    @Test fun onlyDeclaredLightCategoryMaySendSwitchCommands() {
        val light = JarvisSmartThingsPolicy.classify(item("Hall light", "Light", true))
        assertNotNull(light)
        assertTrue(light!!.canSwitchLight)
        val request = JarvisSmartThingsPolicy.command(light, false)
        val command = request.getJSONArray("commands").getJSONObject(0)
        assertEquals("main", command.getString("component"))
        assertEquals("switch", command.getString("capability"))
        assertEquals("off", command.getString("command"))
        assertEquals(0, command.getJSONArray("arguments").length())
        for (category in listOf("Washer", "Refrigerator", "Television",
                "Switch", "Outlet", "Microwave")) {
            val other = JarvisSmartThingsPolicy.classify(item("Unknown", category, true))
            assertNotNull(other)
            assertFalse(other!!.canSwitchLight)
            assertThrows(IllegalArgumentException::class.java) {
                JarvisSmartThingsPolicy.command(other, true)
            }
        }
        assertFalse(JarvisSmartThingsPolicy.classify(item("Light without switch", "Light", false))!!.canSwitchLight)
    }

    @Test fun onlyCanonicalUuidDevicesCanBeUsed() {
        assertTrue(JarvisSmartThingsPolicy.validId("aaaabbbb-1111-2222-3333-123456789abc"))
        assertFalse(JarvisSmartThingsPolicy.validId("../users/test"))
        assertFalse(JarvisSmartThingsPolicy.validId("all"))
        assertNull(JarvisSmartThingsPolicy.classify(
            JSONObject().put("deviceId", "malicious/path")))
        assertTrue(JarvisSmartThingsPolicy.MAX_TOKEN_AGE <= 24L * 60 * 60 * 1000)
        assertTrue(JarvisSmartThingsPolicy.MAX_DEVICES <= 100)
    }

    @Test fun applianceStatusIsReadOnlyAndUsesOnlyKnownCapabilities() {
        val washer = JSONObject().put("components", JSONObject()
            .put("main", JSONObject().put("washerOperatingState", JSONObject()
                .put("machineState", JSONObject().put("value", "running")))))
        assertEquals("washerOperatingState: running",
            JarvisSmartThingsPolicy.statusSummary(washer))
        val tv = JSONObject().put("components", JSONObject()
            .put("main", JSONObject().put("mediaPlayback", JSONObject()
                .put("playbackStatus", JSONObject().put("value", "paused")))))
        assertEquals("mediaPlayback: paused",
            JarvisSmartThingsPolicy.statusSummary(tv))
        val unsafe = JSONObject().put("components", JSONObject()
            .put("main", JSONObject().put("ovenOperatingState", JSONObject()
                .put("ovenOperatingState", JSONObject().put("value", "heat")))))
        assertEquals("unknown", JarvisSmartThingsPolicy.statusSummary(unsafe))
    }

    @Test fun statusMustBeExplicitlyReportedNotInferredFromCommandReceipt() {
        val status = JSONObject().put("components", JSONObject()
            .put("main", JSONObject().put("switch", JSONObject()
                .put("switch", JSONObject().put("value", "on")))))
        assertEquals("on", JarvisSmartThingsPolicy.switchValue(status))
        assertNull(JarvisSmartThingsPolicy.switchValue(JSONObject()))
        assertNull(JarvisSmartThingsPolicy.switchValue(
            JSONObject().put("components", JSONObject().put("main", JSONObject()
                .put("switch", JSONObject().put("switch", JSONObject()
                    .put("value", "unavailable"))))))
    }
}
