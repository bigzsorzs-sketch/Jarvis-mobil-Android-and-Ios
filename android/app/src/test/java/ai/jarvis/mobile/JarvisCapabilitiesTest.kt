package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisCapabilitiesTest {
    @Test fun everyMenuAndNestedSettingsPageIsAvailableWithoutAi() {
        for (tool in JarvisToolCatalog.all + JarvisToolCatalog.subpages) {
            val request = "Nyisd meg a " + tool.name + " oldalt"
            assertEquals(request, tool.route, JarvisCapabilities.localPageToOpen(request))
            assertFalse(request, JarvisAiRouting.shouldAskAi(request))
        }
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a beállítások AI-agy oldalát"))
        assertNull(JarvisCapabilities.localPageToOpen("Open the AI brain page"))
        assertFalse(JarvisToolCatalog.subpages.any { it.route == "AI-agy" })
        assertEquals("Hangbeállítások", JarvisCapabilities.localPageToOpen("Nyisd meg a hangvezérlést"))
        assertEquals("Továbbiak", JarvisCapabilities.localPageToOpen("Nyisd meg az otthoni cím beállítását"))
        assertEquals("Továbbiak", JarvisCapabilities.localPageToOpen("Nyisd meg a mentett helyek importálását"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a Navigáció oldalt"))
        assertEquals("Továbbiak", JarvisCapabilities.localPageToOpen("Nyisd meg a Jarvis-ébresztő kezelését"))
        assertEquals("Jegyzetek", JarvisCapabilities.localPageToOpen("Nyisd meg a helyi jegyzeteket"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a wifi beállításokat"))
    }

    @Test fun pageRoutingNeverExecutesNegatedOrMultipleRequests() {
        assertNull(JarvisCapabilities.localPageToOpen("Ne nyisd meg az ismerősöket"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a munkát és az ismerősöket"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a naptárat és az emailt"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a naptárat"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg az emailt"))
    }

    @Test fun introducesJarvisWithoutInventingAbilities() {
        val answer = JarvisCapabilities.replyIfAsked("Mondj magadról, mire vagy képes")!!
        assertTrue(answer.startsWith("Jarvis vagyok"))
        assertTrue(answer.contains("Vércukor"))
        assertTrue(answer.contains("Hívás"))
        assertFalse(answer.contains("Ismerősök"))
        assertTrue(answer.contains("nem vezérelnek tetszőleges eszközt"))
    }

    @Test fun canExplainItselfOfflineWithNaturalHungarianRequest() {
        val request = "mit tud elmondani magadról"
        assertTrue(JarvisCapabilities.replyIfAsked(request)!!.startsWith("Jarvis vagyok"))
        assertFalse(JarvisAiRouting.shouldAskAi(request, cloudConversation = false))
        assertTrue(JarvisAiRouting.shouldAskAi(request))
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

    @Test fun redundantCallContactsUiIsGoneButNativeCallCommandsRemain() {
        assertFalse(JarvisToolCatalog.all.any { it.name == "Hívás" || it.route == "Névjegyek" })
        assertFalse(JarvisToolCatalog.subpages.any { it.route == "Névjegyek" })
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a Hívás oldalt"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a Névjegyek oldalt"))
        assertEquals("Esztert", VoiceCalling.extractRequest("Hívd fel Esztert"))
        assertEquals("Vivit", VoiceCalling.extractRequest("Jarvis, hívd fel Vivit"))
        assertTrue(VoiceCalling.callConfirmationYes("igen, hívd fel"))
        // Approval and recipient resolution are native, not a page's click action.
        assertFalse(VoiceCalling.callConfirmationYes("nem"))
    }

    @Test fun handlesOnlyExplicitAllowlistedPageNavigation() {
        assertEquals("Munka", JarvisCapabilities.localPageToOpen("Nyisd meg a Munka eszköztárat"))
        assertNull(JarvisCapabilities.localPageToOpen("Mutasd az ismerősöket"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a névjegyeket"))
        assertNull(JarvisCapabilities.localPageToOpen("Mit tudsz az ismerősökről?"))
        assertNull(JarvisCapabilities.localPageToOpen("Nyisd meg a bankszámlámat"))
        assertNull(JarvisCapabilities.localPageToOpen("Listázd az összes eszközöd"))
    }
}

