package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSavedPlacesTest {
    @Test fun takeoutCsvImportsNamesWithQuotedCommasAndNewlines() {
        val export = "Title,Note,URL,Comment\r\n" +
            "\"Home, Sweet Home\",\"note, with comma\",https://maps.google.com/?q=1,test\r\n" +
            "Manchester Airport,travel,https://maps.google.com/?q=2,test\r\n" +
            "\"Liverpool Central\",\"multi\nline\",https://maps.google.com/?q=3,test\r\n"
        val entries = JarvisSavedPlaces.parseCsv(export)
        assertEquals(3, entries.size)
        assertEquals("Home, Sweet Home", entries[0].name)
        assertEquals("Manchester Airport", entries[1].destination)
        assertEquals("Liverpool Central", entries[2].name)
    }

    @Test fun geoJsonUsesValidCoordinatesAndFallsBackToAddress() {
        val json = """
          {"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"location":{"name":"Leeds station","address":"Leeds, UK"}},"geometry":{"type":"Point","coordinates":[-1.548,53.795]}},
            {"type":"Feature","properties":{"location":{"name":"Liverpool library","address":"William Brown Street"}},"geometry":{"type":"Point","coordinates":[0,0]}},
            {"type":"Feature","properties":{"location":{"name":"Bad coords"}},"geometry":{"type":"Point","coordinates":[300,90]}},
            {"type":"Feature","properties":{"location":{"name":"Unknown place"}},"geometry":{"type":"Point","coordinates":[0,0]}}
          ]}
        """.trimIndent()
        val entries = JarvisSavedPlaces.parseGeoJson(json)
        assertEquals(2, entries.size)
        assertEquals("53.795,-1.548", entries[0].destination)
        assertEquals("William Brown Street", entries[1].destination)
        // Unknown or invalid coordinates must never be mistaken for a real route.
    }

    @Test fun locationMetadataMayBeExportedAsAnArray() {
        val json = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"location":[{"name":"Saved station","address":"Leeds, UK"}]},
              "geometry":{"type":"Point","coordinates":[0,0]}}
        ]}"""
        assertEquals(listOf(JarvisSavedPlace("Saved station", "Leeds, UK")),
            JarvisSavedPlaces.parseGeoJson(json))
    }

    @Test fun explicitlyAskForSavedPlacesInsteadOfAiOrEmptyMaps() {
        assertTrue(JarvisSavedPlaces.isListRequest("Mutasd a Google Térképben mentett helyeimet"))
        assertTrue(JarvisSavedPlaces.isListRequest("Nézd meg a csillagozott helyeimet"))
        assertTrue(JarvisSavedPlaces.isListRequest("Listázd a kedvenc helyeket"))
        assertTrue(JarvisSavedPlaces.isListRequest("Show my saved places"))
        assertFalse(JarvisSavedPlaces.isListRequest("Ne mutasd a mentett helyeket"))
        assertFalse(JarvisSavedPlaces.isListRequest("Navigálj Leedsbe"))
        assertFalse(JarvisSavedPlaces.isListRequest("Mit tudsz a Google Mapsről?"))
    }

    @Test fun savedPlaceReferenceIsNotCorruptedByCaseStripping() {
        assertEquals("Tesco", JarvisSavedPlaces.importedName("a mentett Tesco helyhez"))
        assertEquals("Tesco", JarvisSavedPlaces.importedName("a mentett Tesco helyre"))
        assertEquals("a mentett Tesco helyhez",
            VoiceNavigation.destinationFrom("navigálj a mentett Tesco helyhez"))
        assertNull(JarvisSavedPlaces.importedName("Liverpool"))
    }
}
