package ai.jarvis.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisSmartHomeTest {
    @Test fun chatAndVoiceShareExactlyNamedHomeAssistantActions() {
        val devices = listOf(
            JarvisHomeEntity("light.hall", "Előszobai lámpa", "off", "light", true),
            JarvisHomeEntity("sensor.washer", "Mosógép", "washing", "sensor", false)
        )
        var actions = 0
        val turnedOn = JarvisHomeCommandRunner.evaluate(
            "Kapcsold be az előszobai lámpát", devices
        ) { entity, on ->
            actions++
            assertEquals("light.hall", entity.id)
            assertTrue(on)
            "Home Assistant visszaigazolta."
        }
        assertEquals("Home Assistant visszaigazolta.", turnedOn)
        assertEquals(1, actions)
        assertTrue(JarvisHomeCommandRunner.handles("Kapcsold be az előszobai lámpát"))
        assertTrue(JarvisHomeCommandRunner.handles("Mi a mosógép állapota?"))
        assertFalse(JarvisHomeCommandRunner.handles("Milyen idő van ma?"))
    }

    @Test fun backgroundHomeVoiceNeverTogglesAppliancesOrAmbiguousLights() {
        val devices = listOf(
            JarvisHomeEntity("light.room_a", "Nappali lámpa", "off", "light", true),
            JarvisHomeEntity("light.room_b", "Nappali lámpa", "off", "light", true),
            JarvisHomeEntity("switch.washer_plug", "Mosógép", "off", "switch", true)
        )
        var calls = 0
        val operation: (JarvisHomeEntity, Boolean) -> String = { _, _ ->
            calls++; "DO NOT RUN"
        }
        assertEquals("Nem találtam egyetlen egyértelműen egyező Smart Home eszközt.",
            JarvisHomeCommandRunner.evaluate("Kapcsold be a nappali lámpát", devices, operation))
        assertEquals("Ez az eszköz csak állapotlekérdezésre használható.",
            JarvisHomeCommandRunner.evaluate("Kapcsold be a mosógépet", devices, operation))
        assertEquals(0, calls)
        assertEquals("Home Assistant szerint: Mosógép – off.",
            JarvisHomeCommandRunner.evaluate("Mi a mosógép állapota?", devices, operation))
        assertEquals(0, calls)
    }

    @Test fun onlyVerifiedHttpsOriginsCanCarryTokens() {
        assertEquals("https://myhome.example.org:8123",
            JarvisHomePolicy.origin("https://myhome.example.org:8123/"))
        assertEquals("https://home.example.org",
            JarvisHomePolicy.origin("https://home.example.org"))
        listOf("http://192.168.1.5:8123",
            "https://name:secret@home.example.org",
            "https://home.example.org/api",
            "https://home.example.org?token=secret",
            "https://home.example.org#fragment",
            "file:///tmp/a", "https://").forEach {
            assertNull(it, JarvisHomePolicy.origin(it))
        }
    }

    @Test fun appliancesLocksAndPowerPlugsRemainReadOnly() {
        listOf("light.living_room", "media_player.tv", "fan.ceiling").forEach {
            assertTrue(it, JarvisHomePolicy.toggleAllowed(it))
        }
        listOf("sensor.washer", "binary_sensor.microwave", "switch.washer_plug",
            "climate.thermostat", "lock.front_door", "cover.garage",
            "washing_machine.main", "microwave_oven.kitchen").forEach {
            assertFalse(it, JarvisHomePolicy.toggleAllowed(it))
        }
        assertFalse(JarvisHomePolicy.visible("unknown.admin"))
        assertFalse(JarvisHomePolicy.visible("light.name/../../admin"))
    }

    @Test fun entityDiscoveryExposesOnlyAllowlistedTypesAndState() {
        val light = JarvisHomePolicy.asEntity(JSONObject()
            .put("entity_id", "light.hall")
            .put("state", "off")
            .put("attributes", JSONObject().put("friendly_name", "Hall light")))
        assertEquals("Hall light", light?.name)
        assertEquals("off", light?.state)
        assertTrue(light!!.canToggle)

        val washer = JarvisHomePolicy.asEntity(JSONObject()
            .put("entity_id", "sensor.washer_cycle")
            .put("state", "washing")
            .put("attributes", JSONObject().put("friendly_name", "Washer")))
        assertEquals("washing", washer?.state)
        assertFalse(washer!!.canToggle)
        assertNull(JarvisHomePolicy.asEntity(JSONObject()
            .put("entity_id", "automation.open_all")
            .put("state", "on")))
    }

    @Test fun languageCommandsOnlyMatchExactUniqueDeviceNames() {
        val devices = listOf(
            JarvisHomeEntity("light.nappali_lampa", "Nappali lámpa", "off", "light", true),
            JarvisHomeEntity("media_player.tv", "Living TV", "off", "media_player", true),
            JarvisHomeEntity("sensor.mikro", "Mikró", "off", "sensor", false)
        )
        val hu = JarvisHomeCommands.parse("Kapcsold be a nappali lámpát")
        assertEquals("nappali lampat", hu?.target)
        assertEquals("light.nappali_lampa", JarvisHomeCommands.match(hu!!, devices)?.id)
        assertEquals("media_player.tv", JarvisHomeCommands.match(
            JarvisHomeCommands.parse("Turn off Living TV")!!, devices)?.id)
        assertNull(JarvisHomeCommands.parse("Don't turn on the TV"))
        assertNull(JarvisHomeCommands.parse("Kapcsold be az összes lámpát"))
        assertNull(JarvisHomeCommands.parse("Kapcsold ki a mikrofont"))
        assertNull(JarvisHomeCommands.parse("miért nincs bekapcsolva a lámpa?"))
        assertEquals("mosogep", JarvisHomeCommands.statusTarget(
            "Milyen állapotban van a mosógép?"))
        assertEquals("mikro", JarvisHomeCommands.statusTarget(
            "Mi a mikró állapota?"))
        assertEquals("living tv", JarvisHomeCommands.statusTarget(
            "What is the status of Living TV?"))
        assertNull(JarvisHomeCommands.statusTarget("Kapcsold be a mikrót"))
        val dupe = devices + devices.first().copy(id = "light.nappali_lampa_2")
        assertNull(JarvisHomeCommands.match(hu, dupe))
    }
}
