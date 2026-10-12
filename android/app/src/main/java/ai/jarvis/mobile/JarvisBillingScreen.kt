package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Real billing UI: drafts, quotes, clients, email sending and settings in all Jarvis languages. */
@Composable
internal fun JarvisBillingScreen(context: Context, repo: BillingRepository, language: JarvisLanguage) {
    val t = { key: String -> BillingStrings.get(language, key) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("invoices") }
    var message by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf("") }
    val state = repo.state
    LaunchedEffect(Unit) { repo.refresh() }

    Text(t("invoices"), fontSize = 25.sp, modifier = Modifier.testTag("billing-heading"))
    Spacer(Modifier.height(10.dp))
    ScrollableTabRow(selectedTabIndex = listOf("invoices", "quotes", "clients", "settings").indexOf(tab),
        edgePadding = 0.dp, modifier = Modifier.testTag("billing-tabs")) {
        listOf("invoices", "quotes", "clients", "settings").forEach { key ->
            Tab(selected = tab == key, onClick = { tab = key; message = "" },
                text = { Text(t(key)) }, modifier = Modifier.testTag("billing-tab-" + key))
        }
    }
    if (message.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(message, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
    when (tab) {
        "clients" -> {
            var name by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var address by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            Text(t("newClient"), fontSize = 19.sp)
            OutlinedTextField(name, { name = it.take(120) }, label = { Text(t("customer")) },
                modifier = Modifier.fillMaxWidth().testTag("billing-client-name"))
            OutlinedTextField(email, { email = it.take(254) }, label = { Text(t("email")) },
                modifier = Modifier.fillMaxWidth().testTag("billing-client-email"))
            OutlinedTextField(address, { address = it.take(500) }, label = { Text(t("address")) },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(100) }, label = { Text(t("phone")) },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                message = try {
                    repo.saveClient(name, email, address, phone)
                    name = ""; email = ""; address = ""; phone = ""
                    t("saved")
                } catch (_: Exception) { t("invalid") }
            }, modifier = Modifier.testTag("billing-save-client")) { Text(t("save")) }
            HorizontalDivider()
            state.clients.sortedBy { it.name }.forEach { client ->
                OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(client.name, style = MaterialTheme.typography.titleMedium)
                        Text(client.email)
                        Text(client.address)
                        TextButton(onClick = {
                            name = client.name; email = client.email
                            address = client.address; phone = client.phone
                        }) { Text(t("newClient")) }
                    }
                }
            }
        }
        "settings" -> {
            var business by remember { mutableStateOf(state.profile.business) }
            var address by remember { mutableStateOf(state.profile.address) }
            var contact by remember { mutableStateOf(state.profile.email) }
            var bank by remember { mutableStateOf(state.profile.bank) }
            var vatNumber by remember { mutableStateOf(state.profile.vatNumber) }
            Text(t("supplier"), fontSize = 19.sp)
            OutlinedTextField(business, { business = it.take(150) },
                label = { Text(t("companyName")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it.take(500) },
                label = { Text(t("address")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(contact, { contact = it.take(254) },
                label = { Text(t("email")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bank, { bank = it.take(180) },
                label = { Text(t("bank")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vatNumber, { vatNumber = it.take(40) },
                label = { Text(t("vatNumber")) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                message = try {
                    repo.saveProfile(BillingProfile(business, address, contact, bank, vatNumber))
                    t("saved")
                } catch (_: Exception) { t("invalid") }
            }) { Text(t("save")) }
            HorizontalDivider()
            Text(t("smtp"), fontSize = 19.sp)
            Text(t("mailHelp"), fontSize = 12.sp)
            var smtpAddress by remember { mutableStateOf(repo.mailAccount().first) }
            var appPassword by remember { mutableStateOf("") }
            OutlinedTextField(smtpAddress, { smtpAddress = it.take(254) },
                label = { Text(t("email")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(appPassword, { appPassword = it.take(200) },
                label = { Text(t("appPassword")) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("billing-app-password"))
            Button(onClick = {
                message = try {
                    repo.saveMailAccount(smtpAddress, appPassword)
                    appPassword = ""
                    t("saved")
                } catch (_: Exception) { t("invalid") }
            }) { Text(t("save")) }
        }
        "invoices", "quotes" -> {
            val kind = if (tab == "quotes") "quote" else "invoice"
            var selectedClient by remember { mutableStateOf("") }
            var dropdown by remember { mutableStateOf(false) }
            var amount by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
            var vat by remember { mutableIntStateOf(0) }
            var vatMenu by remember { mutableStateOf(false) }
            Text(t("newInvoice"), fontSize = 19.sp)
            if (state.clients.isEmpty()) {
                Text(t("firstManual"), fontSize = 13.sp)
                Button(onClick = { tab = "clients" }) { Text(t("clients")) }
            } else {
                Box {
                    OutlinedButton(onClick = { dropdown = true },
                        modifier = Modifier.fillMaxWidth().testTag("billing-select-client")) {
                        Text(state.clients.find { it.id == selectedClient }?.name ?: t("selectClient"))
                    }
                    DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
                        state.clients.sortedBy { it.name }.forEach { client ->
                            DropdownMenuItem(text = { Text(client.name) }, onClick = {
                                selectedClient = client.id; dropdown = false
                            })
                        }
                    }
                }
                OutlinedTextField(description, { description = it.take(700) },
                    label = { Text(t("description")) },
                    modifier = Modifier.fillMaxWidth().testTag("billing-description"))
                OutlinedTextField(amount, { amount = it.take(20) },
                    label = { Text(t("amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("billing-amount"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("vatRate"), modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { vatMenu = true }) { Text(vat.toString() + "%") }
                        DropdownMenu(expanded = vatMenu, onDismissRequest = { vatMenu = false }) {
                            listOf(0, 5, 20).forEach { rate ->
                                DropdownMenuItem(text = { Text(rate.toString() + "%") },
                                    onClick = { vat = rate; vatMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(dueDate, { dueDate = it.take(10) },
                    label = { Text(t("dueDate")) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    message = try {
                        val pence = requireNotNull(BillingValidation.pence(amount))
                        repo.add(selectedClient, description, pence, vat, dueDate, kind)
                        description = ""; amount = ""
                        t("saved")
                    } catch (_: Exception) { t("invalid") }
                }, modifier = Modifier.testTag("billing-save-draft")) {
                    Text(t(if (kind == "quote") "saveQuote" else "saveDraft"))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            var search by remember { mutableStateOf("") }
            OutlinedTextField(search, { search = it },
                label = { Text(t("customer")) }, modifier = Modifier.fillMaxWidth())
            state.invoices.filter { it.kind == kind }.filter { invoice ->
                val name = state.clients.find { it.id == invoice.clientId }?.name.orEmpty()
                invoice.number.contains(search, true) || name.contains(search, true)
            }.forEach { invoice ->
                val client = state.clients.find { it.id == invoice.clientId }
                OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(invoice.number + " · " + (client?.name ?: ""),
                            style = MaterialTheme.typography.titleMedium)
                        Text(BillingValidation.money(invoice.totalPence) + " · " + invoice.issueDate +
                            " · " + t("due") + " " + invoice.dueDate)
                        Text(invoice.description)
                        Text(t(invoice.status))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                scope.launch {
                                    message = try {
                                        val file = withContext(Dispatchers.IO) {
                                            BillingPdf.create(context, repo.state, invoice, language)
                                        }
                                        if (BillingPdf.share(context, file)) t("pdf") else t("invalid")
                                    } catch (_: Exception) { t("invalid") }
                                }
                            }) { Text("PDF") }
                            if (kind == "quote") {
                                TextButton(onClick = {
                                    message = try {
                                        val created = repo.convert(invoice.id)
                                        if (created == null) t("invalid")
                                        else t("created") + " " + created.number
                                    } catch (_: Exception) { t("invalid") }
                                }) { Text(t("convert")) }
                            } else {
                                if (invoice.status == "draft") {
                                    TextButton(enabled = busyId.isEmpty(), onClick = {
                                        busyId = invoice.id
                                        scope.launch {
                                            message = withContext(Dispatchers.IO) {
                                                BillingSend.send(context, BillingRepository(context),
                                                    invoice.id, language)
                                            }
                                            repo.refresh(); busyId = ""
                                        }
                                    }, modifier = Modifier.testTag("billing-send-" + invoice.number)) {
                                        Text(t("sendNow"))
                                    }
                                }
                                if (invoice.status == "unpaid") {
                                    TextButton(onClick = {
                                        message = if (repo.status(invoice.id, setOf("unpaid"), "paid"))
                                            t("paidDone") + " " + invoice.number else t("invalid")
                                    }) { Text(t("markPaid")) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
