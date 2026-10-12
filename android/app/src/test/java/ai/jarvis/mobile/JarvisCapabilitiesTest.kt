package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisCapabilitiesTest {
    @Test fun introducesJarvisWithoutInventingAbilities() {
        val answer = JarvisCapabilities.replyIfAsked("Mondj magadról, mire vagy képes")!!
        assertTrue(answer.startsWith("Jarvis vagyok"))
        assertTrue(answer.contains("Vércukor"))
        assertTrue(answer.contains("Ismerősök"))
        assertTrue(answer.contains("nem vezérelnek tetszőleges eszközt"))
    }

    @Test fun canExplainItselfOfflineWithNaturalHungarianRequest() {
        val request = "mit tud elmondani magadról"
        assertTrue(JarvisCapabilities.replyIfAsked(request)!!.startsWith("Jarvis vagyok"))
        assertFalse(JarvisAiRouting.shouldAskAi(request))
        assertNotNull(JarvisCapabilities.replyIfAsked("Mit tudsz elmondani magadról?"))
        assertNull(JarvisCapabilities.replyIfAsked("Mit tudsz elmondani az autókról?"))
    }

    @Test fun listsTheRealMenuRatherThanASecondFakeCatalog() {
        val answer = JarvisCapabilities.replyIfAsked("Listázd az összes eszközöd")!!
        for (item in JarvisToolCatalog.all) assertTrue(item.name, answer.contains(item.name))
    }

    @Test fun statesWhoDevelopedTheAppTruthfully() {
        val answer = JarvisCapabilities.replyIfAsked("Ki fejlesztett?")!!
        assertTrue(answer.contains("nem az OpenAI fejlesztette"))
    }

    @Test fun normalSubjectMatterQuestionsAreNotSelfDiscovery() {
        assertNull(JarvisCapabilities.replyIfAsked("Mit tudsz a vércukorbetegségről?"))
        assertNull(JarvisCapabilities.replyIfAsked("Mikor állítsak be ébresztőt?"))
        assertNull(JarvisCapabilities.replyIfAsked("Milyen számlázási szabályok vannak Angliában?"))
    }

    @Test fun handlesOnlyExplicitAllowlistedPageNavigation() {
        assertEquals("Munka", JarvisCapabilities.localPageToOpen("Nyisd meg a Munka eszköztárat"))
        assertEquals("Ismerősök", JarvisCapabilities.localPageToOpen("Mutasd az ismerősöket"))
        assertEquals("Névjegyek", JarvisCapabilities.localPageToOpen("Nyisd meg a névjegyeket"))
        assertNull(JarvisCapabilities.localPageToOpen("Mit tudsz az ismerősökről?"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a bankszámlámat"))
        assertNull(JarvisCapabilities.localPageToOpen("Listázd az összes eszközöd"))
    }
}
