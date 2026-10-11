package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Reading(val value: String, val stamp: String)
data class Invoice(val client: String, val amount: String, val stamp: String)

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        PermissionRefresh.serial += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge. Keep system bars dark while Compose
        // places interactive elements strictly inside safe drawing insets.
        window.statusBarColor = android.graphics.Color.rgb(8, 14, 25)
        window.navigationBarColor = android.graphics.Color.rgb(8, 14, 25)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { JarvisApp(this) }
    }
}

private val Night = Color(0xFF080E19)
private val Panel = Color(0xFF182334)
private val Cyan = Color(0xFF60D5E5)
private val White = Color(0xFFF1F5FA)
private val Soft = Color(0xFFB5C3D8)
private val Pages = listOf("Főoldal", "Chat", "Vércukor", "Számlák", "Továbbiak")

@Composable
fun JarvisApp(context: Context) {
    val securePrefs = remember { SecurePrefs(context) }
    val onboardingPrefs = remember { context.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf(
        if (onboardingPrefs.getBoolean("permissions_reviewed", false)) "Főoldal" else "Engedélyek"
    ) }
    BackHandler(enabled = page != "Főoldal") { page = "Főoldal" }
    val voiceDestination = HandsFreeNavigation.destination
    LaunchedEffect(voiceDestination) {
        if (voiceDestination != null) {
            page = voiceDestination
            HandsFreeNavigation.destination = null
        }
    }
    val readings = remember { mutableStateListOf<Reading>().apply { addAll(readReadings(securePrefs.read("readings", "[]"))) } }
    val invoices = remember { mutableStateListOf<Invoice>().apply { addAll(readInvoices(securePrefs.read("invoices", "[]"))) } }
    val notes = remember { mutableStateListOf<String>().apply { addAll(readNotes(securePrefs.read("notes", "[]"))) } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Night, surface = Panel, onSurface = White,
        onBackground = White, onPrimary = Night
    )) {
        Box(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().background(Night)) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("JARVIS", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("MOBILE  ·  BASIC  ·  ANDROID", color = Soft, fontSize = 10.sp)
                }
                Text("● NATÍV", color = Color(0xFF62E4B8), fontSize = 11.sp)
            }
            HorizontalDivider(color = Color(0xFF243149))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (page) {
                    "Engedélyek" -> PermissionCenter(
                        context,
                        onDone = {
                            onboardingPrefs.edit().putBoolean("permissions_reviewed", true).apply()
                            page = "Főoldal"
                        },
                        onMessages = { page = "Üzenetek" }
                    )
                    "Üzenetek" -> MessageInboxScreen(context, onPermissions = { page = "Engedélyek" })
                    "Névjegyek" -> ContactsScreen(context, onPermissions = { page = "Engedélyek" })
                    "Főoldal" -> HomeScreen(context, readings, invoices, onGo = { page = it })
                    "Chat" -> ChatScreen(context, onNavigate = { page = it })
                    "Vércukor" -> GlucoseScreen(readings) {
                        securePrefs.write("readings", writeReadings(readings))
                    }
                    "Számlák" -> InvoiceScreen(context, invoices) {
                        securePrefs.write("invoices", writeInvoices(invoices))
                    }
                    else -> MoreScreen(context, notes, {
                        securePrefs.write("notes", JSONArray(notes).toString())
                    }, { page = it })
                }
            }
            HorizontalDivider(color = Color(0xFF243149))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Pages.forEach { item ->
                    TextButton(onClick = { page = item }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(item, color = if (page == item) Cyan else Soft, fontSize = 11.sp)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HomeScreen(context: Context, readings: List<Reading>, invoices: List<Invoice>, onGo: (String) -> Unit) {
    Text("Üdv újra!", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = White)
    Text("Az önálló Android Jarvis alapverziója.", color = Soft)
    Spacer(Modifier.height(16.dp))
    SurfaceCard {
        Text("JARVIS BASIC", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("A közös AI-agy és felhőszinkron még nincs bekötve.", color = Soft, fontSize = 13.sp)
        Text("A helyi vércukor- és számlaadatok ezen a telefonon tárolódnak.", fontSize = 12.sp)
    }
    Spacer(Modifier.height(16.dp))
    HandsFreePanel(context)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { ActionTile("🩸 Vércukor", readings.size.toString() + " mérés", onClick = { onGo("Vércukor") }) }
        Box(Modifier.weight(1f)) { ActionTile("🧾 Számlák", invoices.size.toString() + " számla", onClick = { onGo("Számlák") }) }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { ActionTile("💬 Asszisztens", "Chat felület", onClick = { onGo("Chat") }) }
        Box(Modifier.weight(1f)) { ActionTile("🧭 Eszközök", "Email, térkép, jegyzet", onClick = { onGo("Továbbiak") }) }
    }
    Spacer(Modifier.height(16.dp))
    Text("Következő fejlesztési lépések", fontWeight = FontWeight.SemiBold, color = White)
    Text("• Központi AI API és bejelentkezés\n• PC–mobil szinkron\n• Állandó, offline ébresztőszó-motor fejlesztése\n• Libre 2 és OBD2 integráció tesztelése", color = Soft, fontSize = 13.sp)
}

@Composable
private fun ActionTile(title: String, detail: String, onClick: () -> Unit) {
    SurfaceCard {
        Text(title, fontWeight = FontWeight.Bold, color = White)
        Text(detail, color = Soft, fontSize = 12.sp)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Megnyitás") }
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )
}

@Composable
private fun ChatScreen(context: Context, onNavigate: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<Pair<String, String>>() }
    val speaker = remember(context) { TextToSpeech(context) { } }
    DisposableEffect(speaker) { onDispose { speaker.stop(); speaker.shutdown() } }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) { prompt = heard; speechError = "" }
        }
    }
    fun send() {
        val command = prompt.trim()
        if (command.isBlank()) return
        val result = if (AlarmCommands.hasUnsupportedDate(command) && BasicCommandRouter.classify(command) == BasicAction.OPEN_ALARMS) {
            "A holnapra megadott időpontot az alap Óra-API nem támogatja. Nyisd meg az órát, és állítsd be kézzel."
        } else AlarmCommands.userFacingResult(context, command)
            ?: performBasicAction(context, BasicCommandRouter.classify(command), onNavigate)
        log.add(command to result)
        prompt = ""
    }
    Text("Jarvis – helyi parancsok", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("Hang- és szöveges vezérlés", color = Cyan, fontWeight = FontWeight.SemiBold)
        Text("A helyi telefonparancsok használhatók; a felhőalapú AI még nincs bekötve.", color = Soft, fontSize = 12.sp)
        OutlinedTextField(value = prompt, onValueChange = { prompt = it },
            label = { Text("Írj vagy diktálj Jarvisnak") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send() }, enabled = prompt.isNotBlank()) { Text("Végrehajtás") }
            OutlinedButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd a Jarvis parancsot")
                }
                try { voiceLauncher.launch(intent) }
                catch (_: Exception) { speechError = "Nincs kompatibilis beszédfelismerő." }
            }) { Text("🎤 Diktálás") }
        }
        if (speechError.isNotEmpty()) Text(speechError, color = Color(0xFFFFA5A5))
    }
    Spacer(Modifier.height(12.dp))
    log.takeLast(12).asReversed().forEach { (query, reply) ->
        SurfaceCard {
            Text(query, fontWeight = FontWeight.Bold, color = White)
            Text(reply, color = Soft, fontSize = 13.sp)
            TextButton(onClick = { speaker.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply") }) {
                Text("🔊 Felolvasás")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GlucoseScreen(readings: MutableList<Reading>, persist: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Text("Vércukornapló", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)
    Text("mmol/L · helyi mentés · nem orvosi döntéstámogató eszköz", color = Soft, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Mért érték (mmol/L)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val number = input.replace(',', '.').toDoubleOrNull()
            if (number == null || number < 1 || number > 35) {
                error = "Adj meg egy értéket 1 és 35 között."
            } else {
                readings.add(0, Reading(number.toString(), timeNow()))
                persist(); input = ""; error = ""
            }
        }) { Text("Mérés mentése") }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFFA5A5))
    }
    Spacer(Modifier.height(14.dp))
    val values = readings.take(30).mapNotNull { it.value.toDoubleOrNull() }
    if (values.isNotEmpty()) {
        SurfaceCard {
            Text("Mérési áttekintés", color = White, fontWeight = FontWeight.SemiBold)
            Text(
                "Átlag: " + "%.1f".format(Locale.UK, values.average()) +
                    " · Min: " + "%.1f".format(Locale.UK, values.minOrNull() ?: 0.0) +
                    " · Max: " + "%.1f".format(Locale.UK, values.maxOrNull() ?: 0.0) + " mmol/L",
                color = Soft, fontSize = 12.sp
            )
            if (values.size >= 2) {
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    val ordered = values.reversed()
                    val lower = (ordered.minOrNull() ?: 0.0) - 1.0
                    val upper = (ordered.maxOrNull() ?: 1.0) + 1.0
                    val stepX = size.width / (ordered.size - 1)
                    val points = ordered.mapIndexed { i, value ->
                        Offset(
                            i * stepX,
                            (size.height * (1.0 - ((value - lower) / (upper - lower)))).toFloat()
                        )
                    }
                    for (i in 0 until points.lastIndex) {
                        drawLine(Cyan, points[i], points[i + 1], strokeWidth = 4.dp.toPx())
                    }
                    points.forEach { drawCircle(Cyan, 3.dp.toPx(), it) }
                }
            }
            Text("Tájékoztató grafikon. Orvosi döntést nem helyettesít.", color = Soft, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
    if (readings.isEmpty()) Text("Még nincs mentett mérés.", color = Soft)
    readings.forEachIndexed { index, reading ->
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reading.value + " mmol/L", fontWeight = FontWeight.Bold)
                    Text(reading.stamp, color = Soft, fontSize = 12.sp)
                }
                TextButton(onClick = { readings.removeAt(index); persist() }) { Text("Törlés") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InvoiceScreen(context: Context, invoices: MutableList<Invoice>, persist: () -> Unit) {
    var client by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Text("Egyszerű számlázás", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)
    Text("Helyi prototípus: éles adózási/VAT megfelelőség még nincs ellenőrizve.", color = Soft, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        OutlinedTextField(value = client, onValueChange = { client = it }, label = { Text("Ügyfél neve") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Összeg (£)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val value = amount.replace(',', '.').toDoubleOrNull()
            if (client.isBlank() || value == null || value <= 0 || value > 1_000_000) {
                error = "Adj meg egy ügyfélnevet és érvényes összeget."
            } else {
                invoices.add(0, Invoice(client.trim(), "%.2f".format(Locale.UK, value), timeNow()))
                persist(); client = ""; amount = ""; error = ""
            }
        }) { Text("Számlavázlat mentése") }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFFA5A5))
    }
    Spacer(Modifier.height(12.dp))
    invoices.forEachIndexed { index, invoice ->
        SurfaceCard {
            Text(invoice.client, fontWeight = FontWeight.Bold)
            Text("£" + invoice.amount + "  ·  " + invoice.stamp, color = Soft)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = { shareInvoicePdf(context, invoice) }) { Text("PDF megosztás") }
                TextButton(onClick = { invoices.removeAt(index); persist() }) { Text("Törlés") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MoreScreen(context: Context, notes: MutableList<String>, persist: () -> Unit, onGo: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Text("Basic eszközök", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📧 Email", color = White, fontWeight = FontWeight.Bold)
        Text("Valódi emailírás a telefon levelezőalkalmazásával. Gmail-fiók olvasásához külön OAuth-kapcsolat szükséges.", color = Soft, fontSize = 12.sp)
        Button(onClick = { result = if (openEmail(context)) "Email alkalmazás megnyitva." else "Nincs email alkalmazás." }) { Text("Email írása") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🧭 Navigáció", color = White, fontWeight = FontWeight.Bold)
        Button(onClick = { result = if (openMap(context)) "Térkép megnyitva." else "Nincs térkép alkalmazás." }) { Text("Térkép megnyitása") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📅 Naptár és emlékeztetők", color = White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Esemény címe") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            result = if (eventName.isNotBlank() && addCalendarEvent(context, eventName.trim()))
                "A naptárban ellenőrizd és mentsd az eseményt."
            else "Adj meg címet, és legyen telepített naptár."
        }) { Text("Esemény létrehozása") }
        OutlinedButton(onClick = { result = if (openAlarms(context)) "Ébresztők megnyitva." else "Nincs óra alkalmazás." }) { Text("Ébresztők") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📞 Telefon", color = White, fontWeight = FontWeight.Bold)
        Text("A tárcsázót nyitja meg. Hívást nem indít önállóan.", color = Soft, fontSize = 12.sp)
        OutlinedButton(onClick = { result = if (openDialer(context)) "Tárcsázó megnyitva." else "Nincs tárcsázó alkalmazás." }) { Text("Tárcsázó") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🧠 Titkosítva tárolt jegyzetek", color = White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Új jegyzet") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (note.isNotBlank()) { notes.add(0, note.trim()); persist(); note = "" } }) { Text("Mentés") }
        notes.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, Modifier.weight(1f), color = White)
                TextButton(onClick = { notes.removeAt(index); persist() }) { Text("Törlés") }
            }
        }
    }
    if (result.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(result, color = Cyan, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🔐 Engedélyek és üzenetek", color = White, fontWeight = FontWeight.Bold)
        Text("Fotók, dokumentumok, pontos GPS, névjegyek és értesítések: mindegyik külön engedélyezhető.",
            color = Soft, fontSize = 12.sp)
        Button(onClick = { onGo("Engedélyek") }) { Text("Engedélyközpont megnyitása") }
        OutlinedButton(onClick = { onGo("Üzenetek") }) { Text("WhatsApp / Messenger / üzenetek") }
        OutlinedButton(onClick = { onGo("Névjegyek") }) { Text("Névjegyek megtekintése") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🔒 Integrációk folyamatban", color = White, fontWeight = FontWeight.Bold)
        Text("Központi AI, Gmail-fiókszinkron, Libre 2 szinkron, OBD2, 42 nyelv és PC-s adatszinkron további fejlesztést igényel. A Self-Repair nem kerül mobilra.", color = Soft, fontSize = 12.sp)
        TextButton(onClick = { onGo("Chat") }) { Text("Hangos helyi parancsok") }
    }
}

private fun timeNow(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date())
private fun readReadings(raw: String): List<Reading> = try {
    val a = JSONArray(raw); (0 until a.length()).map { i -> a.getJSONObject(i).let { Reading(it.optString("v"), it.optString("t")) } }
} catch (_: Exception) { emptyList() }
private fun writeReadings(a: List<Reading>) = JSONArray().apply {
    a.forEach { put(JSONObject().put("v", it.value).put("t", it.stamp)) }
}.toString()
private fun readInvoices(raw: String): List<Invoice> = try {
    val a = JSONArray(raw); (0 until a.length()).map { i -> a.getJSONObject(i).let { Invoice(it.optString("c"), it.optString("a"), it.optString("t")) } }
} catch (_: Exception) { emptyList() }
private fun writeInvoices(a: List<Invoice>) = JSONArray().apply {
    a.forEach { put(JSONObject().put("c", it.client).put("a", it.amount).put("t", it.stamp)) }
}.toString()
private fun readNotes(raw: String): List<String> = try {
    val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) }
} catch (_: Exception) { emptyList() }

private fun safeOpen(context: Context, intent: Intent) {
    try {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    } catch (_: Exception) { /* No compatible handler installed */ }
}
private fun shareInvoicePdf(context: Context, invoice: Invoice) {
    val doc = PdfDocument()
    try {
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas
        val ink = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true }
        ink.textSize = 24f
        canvas.drawText("JARVIS - INVOICE DRAFT", 42f, 65f, ink)
        ink.textSize = 16f
        canvas.drawText("Customer: " + invoice.client.take(55), 42f, 130f, ink)
        canvas.drawText("Amount: GBP " + invoice.amount, 42f, 166f, ink)
        canvas.drawText("Recorded: " + invoice.stamp, 42f, 202f, ink)
        ink.textSize = 11f
        canvas.drawText("Draft only - verify legal and tax details before sending.", 42f, 268f, ink)
        doc.finishPage(page)
        val directory = File(context.cacheDir, "invoices").apply { mkdirs() }
        val target = File(directory, "jarvis-draft-" + System.currentTimeMillis() + ".pdf")
        FileOutputStream(target).use { doc.writeTo(it) }
        val uri = FileProvider.getUriForFile(context, "ai.jarvis.mobile.files", target)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "PDF megosztása"))
    } catch (_: Exception) {
        /* PDF export failure is not treated as a completed invoice */
    } finally { doc.close() }
}
