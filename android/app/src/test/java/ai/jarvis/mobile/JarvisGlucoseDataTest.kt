package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class JarvisGlucoseDataTest {
    @Test fun offlineLibreViewCsvImportSeparatesHistoricScanAndManualSources() {
        val csv = """
            Device,Serial Number,Device Timestamp,Record Type,Historic Glucose mmol/L,Scan Glucose mmol/L
            My Libre 2,sensor1,2026-10-09 08:00,0,5.6,
            My Libre 2,sensor1,2026-10-09 08:15,0,6.4,
            My Libre 2,sensor1,2026-10-09 08:16,1,,6.5
        """.trimIndent()
        val result = JarvisGlucoseData.importLibreView(csv, emptyList())
        assertEquals(3, result.accepted)
        assertEquals(2, result.readings.count { it.source == "libreview-historic" })
        assertEquals(1, result.readings.count { it.source == "libreview-scan" })
        assertEquals(0, JarvisGlucoseData.importLibreView(csv, result.readings).accepted)
        assertEquals(null, JarvisGlucoseData.estimatedGmi(result.readings))
    }

    @Test fun manualReadingsAreNeverDescribedAsContinuousSensorData() {
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val readings = (0 until 20).map { offset ->
            Reading("6.2", JarvisGlucoseData.normalizeStamp(now - offset * 10 * 60_000L), "manual")
        }
        val sample = JarvisGlucoseData.window(readings, 7, now)
        assertFalse(sample.sufficient)
        assertEquals(null, sample.sensorPercent(3.9, 10.0))
        assertEquals(null, JarvisGlucoseData.estimatedGmi(readings, now))
    }

    @Test fun correctUnitConversions() {
        assertEquals(5.55, JarvisGlucoseData.mmolInput("100", true)!!, 0.02)
        assertEquals(6.7, JarvisGlucoseData.mmolInput("6,7", false)!!, 0.01)
        assertEquals("121", JarvisGlucoseData.shownValue(6.7, true))
        assertNull(JarvisGlucoseData.mmolInput("99999", false))
        assertNull(JarvisGlucoseData.mmolInput("abc", false))
    }

    @Test fun unknownFileDoesNotManufactureReadings() {
        assertThrows(IllegalArgumentException::class.java) {
            JarvisGlucoseData.importLibreView("date,value\n2026-10-10,12", emptyList())
        }
    }

    @Test fun sevenGlucoseDashboardLocalesHaveCompleteLabels() {
        assertEquals(7, JarvisLanguage.entries.size)
        for (key in JarvisGlucoseStrings.keys()) {
            assertTrue(key, JarvisGlucoseStrings.hasAllLanguages(key))
            for (lang in JarvisLanguage.entries) {
                assertNotEquals(key, JarvisGlucoseStrings.text(key, lang))
            }
        }
    }

    @Test fun checkMySugarIsARestrictedLocalHealthRequest() {
        val allowed = listOf(
            "Jarvis nézd meg a cukrom",
            "Mennyi a vércukrom most?",
            "check my blood sugar",
            "prüf meinen blutzucker",
            "vérifie ma glycémie",
            "comprueba mi glucosa",
            "controlla la mia glicemia",
            "sprawdz moja glukoza"
        )
        for (input in allowed) assertTrue("missing intent: " + input,
            JarvisGlucoseVoice.isQuery(input))
        assertFalse(JarvisGlucoseVoice.isQuery("Mire jó a vércukormérő?"))
        assertFalse(JarvisGlucoseVoice.isQuery("How do I treat diabetes?"))
    }

    @Test fun longConversationalHungarianGlucoseCommandsStayOffline() {
        val said = listOf(
            "nézd meg hogy ilyen meg tudod-e oldani hogy megnézed a cukrom",
            "nézd meg hogy a cukromat meg tudod a nézni hogy hány nyascukrom van",
            "Mennyi most a cukrom?",
            "Jarvis megnezed a vercukromat?",
            "Olvasd ki a legutóbbi glukózom értékét"
        )
        for (input in said) assertTrue("Expected local glucose lookup: $input",
            JarvisGlucoseVoice.isQuery(input))
        val notReadings = listOf(
            "Ne nézd meg a cukrom",
            "Mitől magas a cukrom?",
            "Mire jó a vércukormérő?",
            "Melyik cukormentes terméket vegyem?",
            "Hogyan csökkentsem a cukromat?"
        )
        for (input in notReadings) assertFalse("Not a personal glucose read: $input",
            JarvisGlucoseVoice.isQuery(input))
    }
}
