package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class PeopleDirectoryTest {
    @Test fun aliasesRequireExactMatchOrHungarianObjectSuffix() {
        assertTrue(PeopleDirectory.matchingName("drágámat", "Drágám"))
        assertTrue(PeopleDirectory.matchingName("Ferenc Várnait", "Ferenc Várnai"))
        assertTrue(PeopleDirectory.matchingName("Suly-t", "Suly"))
        assertTrue(PeopleDirectory.matchingName("Sulynak", "Suly"))
        assertTrue(PeopleDirectory.matchingName("Eszterhez", "Eszter"))
        assertFalse(PeopleDirectory.matchingName("Feri", "Ferenc"))
        assertFalse(PeopleDirectory.matchingName("Eszter", "Eszter Ilona"))
    }
    @Test fun onlyValidatedInternationalNumbers() {
        assertTrue(PeopleDirectory.validPhone("+447700900123"))
        assertFalse(PeopleDirectory.validPhone("+44 hello"))
        assertFalse(PeopleDirectory.validPhone("123"))
    }
}
