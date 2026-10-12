package ai.jarvis.mobile

import android.content.Context
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Deterministic offline billing commands. No LLM can invent a recipient,
 * decide an amount, or silently choose between multiple matching clients.
 */
internal object BillingVoice {
    internal fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    fun isCandidate(raw: String): Boolean {
        val v = normalize(raw)
        return listOf("szaml", "invoice", "billing", "rechnung", "factur", "fattur",
            "faktur", "presupuesto", "devis", "arajanlat", "wycena", "preventivo",
            "offene rechnungen").any { it in v }
    }
    private fun sending(v: String) = listOf("kuldd", "kuldj", "send", "envoi", "enviar",
        "envia", "invia", "verschick", "senden", "wyslij", "envoye").any { it in v }
    private fun creating(v: String) = listOf("keszits", "kesziten", "create", "make",
        "erstell", "creer", "cree", "crear", "crea", "tworz", "utworz", "prepara",
        "przygotuj").any { it in v }
    private fun markingPaid(v: String) = listOf("fizetettnek", "mark paid",
        "mark as paid", "als bezahlt", "marquer payee", "marcar pagada",
        "segna pagata", "oznacz oplacona", "oznacz jako oplacona").any { it in v }
    private fun opening(v: String) = listOf("nyisd", "mutasd", "open", "show",
        "offne", "ouvrir", "abre", "apri", "otworz").any { it in v }
    fun isOpen(raw: String) = isCandidate(raw) && opening(normalize(raw)) &&
        !sending(normalize(raw)) && !creating(normalize(raw))

    internal fun amount(raw: String): Long? {
        val pattern = Regex(
            "(?iu)(?:£\\s*([0-9]+(?:[.,][0-9]{1,2})?)|" +
                "([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:font(?:os|ot|ert)?|" +
                "pounds?|gbp|pfund|livres?|libras?|sterline|funtow|funtow))")
        val match = pattern.find(normalize(raw)) ?: return null
        return BillingValidation.pence(match.groupValues[1].ifEmpty { match.groupValues[2] })
    }
    private fun exactClient(raw: String, customers: List<BillingClient>): BillingClient? {
        val command = normalize(raw)
        val matches = customers.filter { normalize(it.name).isNotEmpty() &&
            normalize(it.name) in command }
        return matches.singleOrNull()
    }
    private fun descriptionFrom(raw: String, name: String): String? {
        val pos = raw.indexOf(name, ignoreCase = true)
        if (pos < 0) return null
        val remaining = raw.substring(pos + name.length)
            .replace(Regex("(?iu)^\\s*(?:részére|reszere|for|pour|für|fur|para|per|dla)[,:\\s]*"), "")
            .replace(Regex("(?iu)[,\\s]*(?:és|es|and|und|et|y|e|i)\\s+(?:küldd|kuldd|send|envoi|envia|invia|wyslij|sende).*"), "")
            .trim(' ', ',', '.')
        if (remaining.isEmpty() || remaining.length > 120 ||
            listOf("and send", "és küldd", "kuldd", "send", "senden").any {
                normalize(remaining).startsWith(normalize(it))
            }) return null
        return remaining.replace(Regex("(?iu)(ról|ről)$"), "").trim().ifBlank { null }
    }

    /**
     * Must run off the main/UI thread because direct SMTP may block.
     * The very first invoice/service and recipient are entered manually.
     */
    fun execute(context: Context, repo: BillingRepository, raw: String,
                language: JarvisLanguage): String {
        val t = { key: String -> BillingStrings.get(language, key) }
        if (!isCandidate(raw)) return t("openPage")
        if (isOpen(raw)) return t("openPage")
        repo.refresh()
        val state = repo.state
        val normalized = normalize(raw)
        if (!creating(normalized) && !sending(normalized) && !markingPaid(normalized)) {
            val outstanding = state.invoices.filter { it.kind == "invoice" && it.status == "unpaid" }
            return if (outstanding.isEmpty()) t("noUnpaid")
            else t("unpaid") + ": " + outstanding.size + " · " +
                BillingValidation.money(outstanding.sumOf { it.totalPence })
        }
        val customer = exactClient(raw, state.clients) ?: return t("noClientMatch")
        if (markingPaid(normalized)) {
            val eligible = state.invoices.filter {
                it.kind == "invoice" && it.clientId == customer.id && it.status == "unpaid"
            }
            val target = eligible.singleOrNull() ?: return t("missingInvoice")
            return if (repo.status(target.id, setOf("unpaid"), "paid"))
                t("paidDone") + " " + target.number else t("alreadyProcessed")
        }
        if (creating(normalized)) {
            val money = amount(raw) ?: return t("needAmount")
            val previous = state.invoices.firstOrNull { it.clientId == customer.id &&
                it.description.isNotBlank() && !it.id.startsWith("legacy-") }
                ?: return t("needService")
            val description = descriptionFrom(raw, customer.name) ?: previous.description
            val newInvoice = try {
                repo.add(customer.id, description, money, previous.vatPercent,
                    LocalDate.now().plusDays(14).toString())
            } catch (_: Exception) { return t("invalid") }
            if (sending(normalized)) return BillingSend.send(context, repo, newInvoice.id, language)
            return t("created") + " " + newInvoice.number
        }
        if (sending(normalized)) {
            val drafts = state.invoices.filter { it.kind == "invoice" &&
                it.clientId == customer.id && it.status == "draft" }
            val pending = drafts.singleOrNull() ?: return t("missingInvoice")
            return BillingSend.send(context, repo, pending.id, language)
        }
        return t("openPage")
    }
}
