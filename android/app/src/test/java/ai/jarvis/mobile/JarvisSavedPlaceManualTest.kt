package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisSavedPlaceManualTest {
    @Test fun onlyConcreteDestinationsCanBeStoredForPeople() {
        assertTrue(JarvisSavedPlaces.validManualPlace(
            "Eszter", "20 Broom Road, Leeds, LS10 3JK"))
        assertTrue(JarvisSavedPlaces.validManualPlace(
            "Home", "53.8013,-1.5491"))
        assertFalse(JarvisSavedPlaces.validManualPlace("Eszter", "Eszter"))
        assertFalse(JarvisSavedPlaces.validManualPlace("E", "20 Broom Road, Leeds"))
        assertFalse(JarvisSavedPlaces.validManualPlace(
            "Eszter", "20 Broom Road\nHack"))
        assertFalse(JarvisSavedPlaces.validManualPlace("", "Leeds"))
    }

    @Test fun personDestinationRemainsNamedUntilLocallyResolved() {
        assertEquals("PERSON:Eszter",
            VoiceNavigation.destinationFrom("vigyél eszterhez"))
        assertEquals("PERSON:Eszter",
            VoiceNavigation.destinationFrom("Navigálj Eszterhez"))
        assertTrue(VoiceNavigation.maySearchNamedPlace("PERSON:Eszter"))
    }
}
