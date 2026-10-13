package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisControlRegistryTest {
    @Test fun editsPreserveActualUserValueAndDoNotClickSave() {
        val registry = JarvisControlRegistry()
        var value = ""
        var saves = 0
        registry.put("field", JarvisControlRegistry.Control("Új jegyzet", edit = { value = it }))
        registry.put("save", JarvisControlRegistry.Control("Mentés", click = { saves++ }))
        val parsed = JarvisControlCommand.parse("Írd az Új jegyzet mezőbe: Árvíztűrő, 12:30!")!!
        registry.execute(parsed)
        assertEquals("Árvíztűrő, 12:30!", value)
        assertEquals(0, saves)
        registry.execute(JarvisControlCommand.parse("Nyomd meg a Mentés gombot")!!)
        assertEquals(1, saves)
    }
    @Test fun duplicateLabelsRequireExplicitOrdinalAndDisabledControlsNeverRun() {
        val registry = JarvisControlRegistry()
        var selected = 0
        registry.put(1, JarvisControlRegistry.Control("Mentés", click = { selected = 1 }))
        registry.put(2, JarvisControlRegistry.Control("Mentés", click = { selected = 2 }))
        registry.execute(JarvisControlCommand.Click("mentes"))
        assertEquals(0, selected)
        registry.execute(JarvisControlCommand.parse("nyomd meg a második Mentés gombot")!!)
        assertEquals(2, selected)
        registry.put(2, JarvisControlRegistry.Control("Mentés", enabled = false, click = { selected = 9 }))
        registry.execute(JarvisControlCommand.Click("Mentés", 2))
        assertEquals(2, selected)
        registry.remove(1); registry.remove(2)
        registry.execute(JarvisControlCommand.Click("Mentés"))
        assertEquals(2, selected)
    }
    @Test fun negativesAndQuestionsAreNeverActions() {
        listOf("Ne nyomd meg a Mentés gombot", "Nem akarom menteni", "How do I click Save?",
            "Mi történik ha nyomd meg a Mentés gombot?").forEach {
            assertNull(it, JarvisControlCommand.parse(it))
        }
    }
    @Test fun voiceAndTextUseIdenticalParsersIncludingSpokenFieldValue() {
        assertEquals(JarvisControlCommand.parse("Nyomd meg a Mentés gombot"),
            JarvisControlCommand.parse("Jarvis, nyomd meg a Mentés gombot"))
        assertEquals(JarvisControlCommand.Fill("Új jegyzet", "tej és kenyér"),
            JarvisControlCommand.parse("írd az Új jegyzet mezőbe tej és kenyér"))
        assertEquals(JarvisControlCommand.Click("Save"), JarvisControlCommand.parse("click the Save button"))
        assertEquals(JarvisControlCommand.Click("Kunden"), JarvisControlCommand.parse("klicke Kunden"))
    }
}
