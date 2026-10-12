package ai.jarvis.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class BillingClient(val id: String, val name: String, val email: String,
                                  val address: String, val phone: String = "")
internal data class BillingProfile(val business: String = "", val address: String = "",
                                   val email: String = "", val bank: String = "", val vatNumber: String = "")
internal data class BillingInvoice(
    val id: String, val number: String, val clientId: String, val description: String,
    val netPence: Long, val vatPercent: Int, val issueDate: String, val dueDate: String,
    val kind: String = "invoice", val status: String = "draft", val sentAt: String = ""
) {
    val vatPence: Long get() = (netPence * vatPercent + 50) / 100
    val totalPence: Long get() = netPence + vatPence
}
internal data class BillingState(val clients: List<BillingClient> = emptyList(),
    val invoices: List<BillingInvoice> = emptyList(), val profile: BillingProfile = BillingProfile(),
    val nextInvoice: Int = 1, val nextQuote: Int = 1)

internal object BillingValidation {
    private val emailPattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    fun email(value: String) = value.length <= 254 && emailPattern.matches(value.trim())
    fun pence(value: String): Long? = try {
        val cleaned = value.trim().replace("£", "").replace(",", ".")
        if (!cleaned.matches(Regex("^[0-9]+(?:\\.[0-9]{1,2})?$"))) null
        else {
            val amount = BigDecimal(cleaned).setScale(2, RoundingMode.UNNECESSARY)
            if (amount <= BigDecimal.ZERO || amount > BigDecimal("1000000")) null
            else amount.movePointRight(2).longValueExact()
        }
    } catch (_: Exception) { null }
    fun money(pence: Long): String = "£" + String.format(Locale.UK, "%,.2f", pence / 100.0)
    fun date(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess
}

/** Encrypted on-device business data. Never stores credentials in GitHub or an APK. */
internal class BillingRepository(context: Context) {
    private val prefs = SecurePrefs(context.applicationContext)
    var state by mutableStateOf(readState())
        private set
    companion object { private val lock = Any() }

    fun refresh() { state = readState() }
    private fun readState(): BillingState {
        val raw = prefs.read("billing_v1", "")
        if (raw.isBlank()) {
            // Existing 0.1.x draft data is retained during the upgrade.
            val old = runCatching { JSONArray(prefs.read("invoices", "[]")) }.getOrNull()
            if (old == null || old.length() == 0) return BillingState()
            val clients = mutableListOf<BillingClient>()
            val invoices = mutableListOf<BillingInvoice>()
            for (i in 0 until old.length()) {
                val item = old.optJSONObject(i) ?: continue
                val name = item.optString("c").trim().take(120)
                val amount = BillingValidation.pence(item.optString("a"))
                if (name.isEmpty() || amount == null) continue
                val client = clients.find { it.name.equals(name, true) } ?: BillingClient(
                    "legacy-" + i, name, "", ""
                ).also { clients.add(it) }
                invoices.add(BillingInvoice("legacy-invoice-" + i, "LEGACY-" + (i + 1),
                    client.id, "Imported draft", amount, 0, LocalDate.now().toString(),
                    LocalDate.now().plusDays(14).toString()))
            }
            val migrated = BillingState(clients, invoices, nextInvoice = invoices.size + 1)
            if (invoices.isNotEmpty()) prefs.write("billing_v1", encode(migrated))
            return migrated
        }
        return decode(raw)
    }

    private fun encode(value: BillingState): String = JSONObject().apply {
        put("version", 1); put("nextInvoice", value.nextInvoice); put("nextQuote", value.nextQuote)
        put("profile", JSONObject().apply {
            put("business", value.profile.business); put("address", value.profile.address)
            put("email", value.profile.email); put("bank", value.profile.bank)
            put("vat", value.profile.vatNumber)
        })
        put("clients", JSONArray().apply { value.clients.forEach { c -> put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("email", c.email)
            put("address", c.address); put("phone", c.phone)
        }) } })
        put("invoices", JSONArray().apply { value.invoices.forEach { i -> put(JSONObject().apply {
            put("id", i.id); put("number", i.number); put("clientId", i.clientId)
            put("description", i.description); put("netPence", i.netPence)
            put("vatPercent", i.vatPercent); put("issueDate", i.issueDate)
            put("dueDate", i.dueDate); put("kind", i.kind); put("status", i.status)
            put("sentAt", i.sentAt)
        }) } })
    }.toString()

    private fun decode(raw: String): BillingState {
        val root = JSONObject(raw)
        require(root.optInt("version") == 1) { "Unknown billing storage version" }
        val profile = root.getJSONObject("profile")
        val customers = root.getJSONArray("clients")
        val bills = root.getJSONArray("invoices")
        require(customers.length() <= 5000 && bills.length() <= 20000) { "Billing storage too large" }
        return BillingState(
            clients = (0 until customers.length()).map { customers.getJSONObject(it).let { c ->
                BillingClient(c.getString("id"), c.getString("name"), c.optString("email"),
                    c.optString("address"), c.optString("phone"))
            } },
            invoices = (0 until bills.length()).map { bills.getJSONObject(it).let { i ->
                BillingInvoice(i.getString("id"), i.getString("number"), i.getString("clientId"),
                    i.getString("description"), i.getLong("netPence"), i.optInt("vatPercent", 0),
                    i.getString("issueDate"), i.getString("dueDate"),
                    i.optString("kind", "invoice"), i.optString("status", "draft"),
                    i.optString("sentAt"))
            } },
            profile = BillingProfile(profile.optString("business"), profile.optString("address"),
                profile.optString("email"), profile.optString("bank"), profile.optString("vat")),
            nextInvoice = root.optInt("nextInvoice", 1).coerceAtLeast(1),
            nextQuote = root.optInt("nextQuote", 1).coerceAtLeast(1)
        )
    }
    private fun modify(change: (BillingState) -> BillingState) {
        synchronized(lock) {
            val updated = change(readState())
            prefs.write("billing_v1", encode(updated))
            state = updated
        }
    }
    fun saveProfile(profile: BillingProfile) {
        require(profile.business.isNotBlank() && profile.address.isNotBlank())
        require(profile.email.isBlank() || BillingValidation.email(profile.email))
        modify { it.copy(profile = profile) }
    }
    fun saveClient(name: String, email: String, address: String, phone: String = ""): BillingClient {
        require(name.isNotBlank() && name.length <= 120 && address.length <= 500)
        require(email.isBlank() || BillingValidation.email(email))
        var result: BillingClient? = null
        modify { s ->
            val previous = s.clients.find { it.name.equals(name.trim(), true) }
            val updated = BillingClient(previous?.id ?: UUID.randomUUID().toString(),
                name.trim(), email.trim(), address.trim(), phone.trim().take(100))
            result = updated
            s.copy(clients = s.clients.filterNot { it.id == updated.id } + updated)
        }
        return requireNotNull(result)
    }
    fun add(clientId: String, description: String, amount: Long, vat: Int,
            dueDate: String, kind: String = "invoice"): BillingInvoice {
        require(description.isNotBlank() && description.length <= 700)
        require(amount in 1..100_000_000L && vat in listOf(0, 5, 20))
        require(BillingValidation.date(dueDate) && kind in listOf("invoice", "quote"))
        var result: BillingInvoice? = null
        modify { s ->
            require(s.clients.any { it.id == clientId })
            val number = if (kind == "quote") "EST-%06d".format(Locale.ROOT, s.nextQuote)
                         else "JAR-%06d".format(Locale.ROOT, s.nextInvoice)
            val item = BillingInvoice(UUID.randomUUID().toString(), number, clientId,
                description.trim(), amount, vat, LocalDate.now().toString(), dueDate, kind)
            result = item
            s.copy(invoices = listOf(item) + s.invoices,
                nextInvoice = s.nextInvoice + if (kind == "invoice") 1 else 0,
                nextQuote = s.nextQuote + if (kind == "quote") 1 else 0)
        }
        return requireNotNull(result)
    }
    fun status(id: String, allowed: Set<String>, newStatus: String, sentAt: String = ""): Boolean {
        var changed = false
        modify { s -> s.copy(invoices = s.invoices.map { invoice ->
            if (invoice.id == id && invoice.status in allowed) {
                changed = true
                invoice.copy(status = newStatus,
                    sentAt = if (sentAt.isNotEmpty()) sentAt else invoice.sentAt)
            } else invoice
        }) }
        return changed
    }
    fun convert(id: String): BillingInvoice? {
        val item = readState().invoices.find { it.id == id && it.kind == "quote" } ?: return null
        return add(item.clientId, item.description, item.netPence, item.vatPercent,
            item.dueDate)
    }
    fun mailAccount(): Pair<String, String> = prefs.read("billing_mail_address", "") to
        prefs.read("billing_mail_app_password", "")
    fun saveMailAccount(address: String, password: String) {
        require(BillingValidation.email(address) && password.isNotBlank() && password.length <= 200)
        prefs.write("billing_mail_address", address.trim())
        prefs.write("billing_mail_app_password", password.trim())
    }
}
