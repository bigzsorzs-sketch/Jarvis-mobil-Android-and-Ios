package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisBillingTest {
    @Test fun everyScreenAndDeliveryStringHasSevenTranslations() {
        assertEquals(7, JarvisLanguage.entries.size)
        assertTrue(BillingStrings.complete())
        for (lang in JarvisLanguage.entries) {
            for (key in BillingStrings.keys()) {
                assertTrue("Empty translation " + lang.tag + "/" + key,
                    BillingStrings.get(lang, key).isNotBlank())
            }
        }
        assertEquals("Invoice", BillingStrings.get(JarvisLanguage.ENGLISH, "invoice"))
        assertEquals("Számla", BillingStrings.get(JarvisLanguage.HUNGARIAN, "invoice"))
    }
    @Test fun parsePoundsPreciselyWithoutFloatingPointRounding() {
        assertEquals(8500L, BillingValidation.pence("85")!!)
        assertEquals(8500L, BillingValidation.pence("85,00")!!)
        assertEquals(1L, BillingValidation.pence("0.01")!!)
        assertNull(BillingValidation.pence("0"))
        assertNull(BillingValidation.pence("-6"))
        assertNull(BillingValidation.pence("12.999"))
        assertNull(BillingValidation.pence("1e5"))
        assertNull(BillingValidation.pence("1000001"))
        assertEquals("£85.00", BillingValidation.money(8500))
    }
    @Test fun sevenLanguageSpeechAmounts() {
        val phrases = listOf(
            "Jarvis, készíts 85 fontos számlát Brandstrom House részére, és küldd el emailben",
            "Create an invoice for Brandstrom House for 85 pounds and send it",
            "Erstelle eine Rechnung für Brandstrom House über 85 Pfund und senden",
            "Créer une facture Brandstrom House de 85 livres et envoyer",
            "Crear una factura de 85 libras para Brandstrom House y enviar",
            "Crea una fattura da 85 sterline per Brandstrom House e invia",
            "Utwórz fakturę dla Brandstrom House 85 funtów i wyślij"
        )
        phrases.forEach {
            assertTrue(it, BillingVoice.isCandidate(it))
            assertEquals(it, 8500L, BillingVoice.amount(it)!!)
        }
    }
    @Test fun sendDoesNotRequireAnExtraConfirmationAndOpenIsNotSend() {
        assertTrue(BillingVoice.isCandidate("Küldd el a Brandstrom House számláját"))
        assertTrue(BillingVoice.isOpen("Nyisd meg a számlázást"))
        assertFalse(BillingVoice.isOpen("Készíts 85 fontos számlát és küldd el"))
        assertFalse(BillingVoice.isCandidate("Send email to a friend"))
    }
    @Test fun sevenLanguageNegatedSendingNeverTriggersActualBillingEmail() {
        val phrases = listOf(
            "Készíts számlát Brandstrom House részére de ne küldd el",
            "Create an invoice for Brandstrom House and don't send",
            "Create an invoice but do not send it",
            "Erstelle Rechnung für Brandstrom House aber nicht senden",
            "Créer une facture mais ne pas envoyer",
            "Crear una factura pero no enviar",
            "Crea fattura ma non inviare",
            "Utwórz fakturę ale nie wysyłaj"
        )
        phrases.forEach { phrase ->
            val normalized = BillingVoice.normalize(phrase)
            assertFalse("Unsafe send: $phrase", BillingVoice.sending(normalized))
        }
    }

    @Test fun actualExplicitSendingIsNotConfusedWithNamesOrNegations() {
        val positive = listOf(
            "Küldd el a számlát",
            "Send this invoice",
            "Senden Rechnung",
            "Envoie la facture",
            "Enviar la factura",
            "Invia fattura",
            "Wyslij fakture"
        )
        positive.forEach { phrase ->
            assertTrue("Expected explicit send: $phrase",
                BillingVoice.sending(BillingVoice.normalize(phrase)))
        }
        for (phrase in listOf("Create invoice for Sendley Cleaners",
            "Create invoice for Senders Ltd", "Customer: Envoiexpress",
            "Don't send an invoice", "Ne küldd el a számlát")) {
            assertFalse(phrase, BillingVoice.sending(BillingVoice.normalize(phrase)))
        }
    }

    @Test fun vatRoundsPerPennyAndDraftNeverClaimsSent() {
        val invoice = BillingInvoice("id", "JAR-000001", "client",
            "Oven cleaning", 101L, 5, "2026-10-12", "2026-10-26")
        assertEquals(5L, invoice.vatPence)
        assertEquals(106L, invoice.totalPence)
        assertEquals("draft", invoice.status)
    }
}
