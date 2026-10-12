package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
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
        HandsFreeNavigation.appIsForeground = true
    }

    override fun onPause() {
        HandsFreeNavigation.appIsForeground = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge. Keep system bars dark while Compose
        // places interactive elements strictly inside safe drawing insets.
        window.statusBarColor = android.graphics.Color.rgb(8, 14, 25)
        window.navigationBarColor = android.graphics.Color.rgb(8, 14, 25)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // Resize instead of panning the entire screen (which hides the composer
        // and pushes the orb behind the system status bar on some OEM phones).
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { JarvisApp(this) }
    }
}

private val Night: Color @Composable get() = JarvisPalette.background
private val Panel: Color @Composable get() = JarvisPalette.panel
private val Cyan: Color @Composable get() = JarvisPalette.accent
private val White: Color @Composable get() = JarvisPalette.mainText
private val Soft: Color @Composable get() = JarvisPalette.subtleText

@Composable
fun JarvisApp(context: Context) {
    val securePrefs = remember { SecurePrefs(context) }
    val onboardingPrefs = remember { context.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE) }
    var isDark by remember { mutableStateOf(securePrefs.read("ui_dark_mode", "false") == "true") }
    val toggleTheme = {
        isDark = !isDark
        securePrefs.write("ui_dark_mode", isDark.toString())
    }
    var page by remember { mutableStateOf(
        if (onboardingPrefs.getBoolean("permissions_reviewed", false)) "Főoldal" else "Engedélyek"
    ) }
    val conversation = remember { mutableStateListOf<Pair<String, String>>() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var pendingCall by remember { mutableStateOf<VoiceCalling.Target?>(null) }
    var pendingCallUntilMs by remember { mutableLongStateOf(0L) }
    fun prepareCallConfirmation(person: VoiceCalling.Target) {
        pendingCall = person
        pendingCallUntilMs = android.os.SystemClock.elapsedRealtime() + 30_000L
    }
    var pendingMessage by remember { mutableStateOf<OutgoingMessage?>(null) }
    var pendingMessageUntilMs by remember { mutableLongStateOf(0L) }
    fun prepareMessageReview(message: OutgoingMessage) {
        pendingMessage = message
        pendingMessageUntilMs = android.os.SystemClock.elapsedRealtime() + 40_000L
    }
    fun openTool(destination: String): String {
        if (destination == "Navigáció" || destination == "Térkép") {
            val opened = openMap(context)
            if (!opened) page = "Főoldal"
            return if (opened) "A térképkérést átadtam az Androidnak."
                else "Nem találok telepített térképalkalmazást."
        }
        if (destination == "Jegyzetek") {
            val result = JarvisExternalApps.openNotes(context)
            if (result != AppHandoffResult.OPENED) page = "Főoldal"
            return when (result) {
                AppHandoffResult.OPENED -> "Megnyitottam a telefon jegyzetalkalmazását."
                AppHandoffResult.SEARCH_OPENED -> "Jegyzetalkalmazást keresek a Play Áruházban."
                AppHandoffResult.UNAVAILABLE -> "Nem sikerült megnyitni a jegyzetalkalmazást."
            }
        }
        page = destination
        return "Megnyitottam a Jarvis $destination oldalát."
    }
    fun executeHome(command: String) {
        if (command.isBlank()) return
        if (JarvisAiRouting.shouldAskAi(command)) {
            val pending = command to "AI-válaszra várok…"
            conversation.add(pending)
            if (conversation.size > 50) conversation.removeAt(0)
            drawerScope.launch {
                val outcome = JarvisAiGateway.askStructured(context, command)
                val answer = when {
                    outcome.route != null -> openTool(outcome.route)
                    outcome.deviceAction != null -> JarvisPhoneActions.execute(context, outcome.deviceAction)
                    outcome.messageDraft != null -> {
                        prepareMessageReview(outcome.messageDraft)
                        "A címzett és az üzenet ellenőrzésre vár. Semmit nem küldtem el."
                    }
                    outcome.callTarget != null -> {
                        when (val match = VoiceCalling.lookup(context, outcome.callTarget)) {
                            is VoiceCalling.Lookup.Found -> {
                                prepareCallConfirmation(match.target)
                                "A hívás még nem indult el. Ellenőrizd a címzettet és erősítsd meg."
                            }
                            VoiceCalling.Lookup.NoContactsAccess ->
                                "A névjegyek kereséséhez engedélyezd a Névjegyek hozzáférést."
                            VoiceCalling.Lookup.Ambiguous ->
                                "Több azonos névjegyet találtam. Pontosítsd a nevet."
                            VoiceCalling.Lookup.NotFound ->
                                "Nem találtam a címzettet; nem indítottam hívást."
                        }
                    }
                    else -> outcome.message
                }
                val index = conversation.indexOf(pending)
                if (index >= 0) conversation[index] = command to answer
            }
        } else {
            val answer = JarvisCommandExecutor.run(
                context, command, { destination -> openTool(destination) },
                { contact -> prepareCallConfirmation(contact) }
            )
            conversation.add(command to answer)
            if (conversation.size > 50) conversation.removeAt(0)
        }
    }
    BackHandler(enabled = drawerState.isOpen || page != "Főoldal") {
        if (drawerState.isOpen) drawerScope.launch { drawerState.close() }
        else page = "Főoldal"
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            val light = !isDark
            window.statusBarColor = if (light) android.graphics.Color.WHITE
                else android.graphics.Color.rgb(8, 14, 25)
            window.navigationBarColor = if (light) android.graphics.Color.rgb(246, 250, 255)
                else android.graphics.Color.rgb(8, 14, 25)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    val appIsForeground = HandsFreeNavigation.appIsForeground
    val externalRequest = HandsFreeNavigation.external
    var handoffResult by remember { mutableStateOf("") }
    LaunchedEffect(externalRequest, appIsForeground) {
        if (externalRequest != null && appIsForeground) {
            // A queued voice handoff cannot silently execute after a long delay.
            HandsFreeNavigation.external = null
            val ageMs = android.os.SystemClock.elapsedRealtime() - externalRequest.createdAtMs
            handoffResult = if (ageMs in 0..30_000L)
                ExternalTasks.dispatch(context, externalRequest.task)
            else "A korábbi hangos kérés lejárt. Nem indítottam műveletet."
        }
    }
    val requestedEmail = HandsFreeNavigation.mailDraft
    LaunchedEffect(requestedEmail, appIsForeground) {
        if (requestedEmail != null && appIsForeground) {
            if (VoiceEmail.openComposer(context, requestedEmail)) {
                HandsFreeNavigation.mailDraft = null
            }
        }
    }
    val requestedMap = HandsFreeNavigation.mapTarget
    LaunchedEffect(requestedMap, appIsForeground) {
        if (requestedMap != null && appIsForeground) {
            if (VoiceNavigation.open(context, requestedMap)) {
                HandsFreeNavigation.mapTarget = null
            }
        }
    }
    val requestedPhoneApp = HandsFreeNavigation.deviceAction
    LaunchedEffect(requestedPhoneApp, appIsForeground) {
        if (requestedPhoneApp != null && appIsForeground) {
            // One-time, short-lived voice handoff: never launch something hours later.
            HandsFreeNavigation.deviceAction = null
            val fresh = android.os.SystemClock.elapsedRealtime() - requestedPhoneApp.createdAtMs < 30_000
            val answer = if (fresh) JarvisPhoneActions.execute(context, requestedPhoneApp.action)
                else "A korábbi hangparancs lejárt. Nem indítottam el alkalmazást."
            conversation.add("Jarvis művelet" to answer)
        }
    }
    val voiceDestination = HandsFreeNavigation.destination
    LaunchedEffect(voiceDestination, appIsForeground) {
        if (voiceDestination != null && appIsForeground) {
            openTool(voiceDestination)
            HandsFreeNavigation.destination = null
        }
    }
    val readings = remember { mutableStateListOf<Reading>().apply { addAll(readReadings(securePrefs.read("readings", "[]"))) } }
    val invoices = remember { mutableStateListOf<Invoice>().apply { addAll(readInvoices(securePrefs.read("invoices", "[]"))) } }
    val notes = remember { mutableStateListOf<String>().apply { addAll(readNotes(securePrefs.read("notes", "[]"))) } }

    val onHome = page == "Főoldal"
    CompositionLocalProvider(LocalJarvisDark provides isDark) {
        MaterialTheme(colorScheme = if (!isDark) lightColorScheme(
            primary = Color(0xFF075FF1), background = Color.White,
            surface = Color.White, onSurface = Color(0xFF08142E)
        ) else darkColorScheme(
            primary = Cyan, background = Night, surface = Panel,
            onSurface = White, onBackground = White, onPrimary = Night
        )) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    JarvisDrawer(page) { selected ->
                        openTool(selected)
                        drawerScope.launch { drawerState.close() }
                    }
                }
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding()
                        .background(Night)
                ) {
                    if (onHome) {
                        JarvisHomeScreen(
                            context = context,
                            isDark = isDark,
                            onToggleTheme = toggleTheme,
                            onMenu = { drawerScope.launch { drawerState.open() } },
                            onCommand = { command -> executeHome(command) },
                            history = conversation,
                            pendingCall = pendingCall,
                            pendingMessage = pendingMessage,
                            onMessageConfirm = {
                                val draft = pendingMessage
                                val fresh = android.os.SystemClock.elapsedRealtime() <= pendingMessageUntilMs
                                pendingMessage = null
                                pendingMessageUntilMs = 0L
                                val answer = if (!fresh || draft == null)
                                    "Lejárt az üzenet jóváhagyása. Nem történt művelet."
                                else ExternalTasks.dispatch(context, HandoffTask.Message(draft))
                                conversation.add("Üzenet ellenőrzése" to answer)
                            },
                            onMessageDismiss = {
                                pendingMessage = null
                                pendingMessageUntilMs = 0L
                            },
                            onCallConfirm = {
                                val person = pendingCall
                                val fresh = android.os.SystemClock.elapsedRealtime() <= pendingCallUntilMs
                                pendingCall = null
                                pendingCallUntilMs = 0L
                                val answer = if (!fresh || person == null)
                                    "Lejárt a hívás megerősítési ideje. Nem indítottam hívást."
                                else if (VoiceCalling.place(context, person))
                                    "A híváskérést átadtam az Androidnak."
                                else "A hívást nem sikerült elindítani. Ellenőrizd a telefonengedélyt."
                                conversation.add("Hívás megerősítve" to answer)
                            },
                            onCallDismiss = {
                                pendingCall = null
                                pendingCallUntilMs = 0L
                            }
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(page, color = White, fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                                    .clickable { drawerScope.launch { drawerState.open() } })
                            TextButton(onClick = toggleTheme) {
                                Text(if (isDark) "☀ Világos" else "☾ Sötét", color = Cyan)
                            }
                            TextButton(onClick = { page = "Főoldal" }) {
                                Text("Főoldal", color = Cyan)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF243149))
                        if (handoffResult.isNotBlank()) {
                            Text(handoffResult, color = Cyan, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            when (page) {
                                "Beállítások" -> JarvisSettingsScreen(
                                    context, isDark, toggleTheme,
                                    onVoice = { page = "Hangbeállítások" },
                                    onAi = { page = "AI-agy" }
                                )
                                "Hangbeállítások" -> HandsFreePanel(context)
                                "AI-agy" -> JarvisAiSettings(context)
                                "Engedélyek" -> PermissionCenter(
                                    context,
                                    onDone = {
                                        onboardingPrefs.edit()
                                            .putBoolean("permissions_reviewed", true).apply()
                                        page = "Főoldal"
                                    }
                                )
                                "Üzenetek" -> MessageInboxScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Névjegyek" -> ContactsScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Ismerősök" -> PeopleDirectoryScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Vércukor" -> GlucoseScreen(readings) {
                                    securePrefs.write("readings", writeReadings(readings))
                                }
                                "Számlák" -> InvoiceScreen(context, invoices) {
                                    securePrefs.write("invoices", writeInvoices(invoices))
                                }
                                "Továbbiak" -> MoreScreen(context, notes, {
                                    securePrefs.write("notes", JSONArray(notes).toString())
                                }, { page = it })
                                else -> JarvisToolLanding(context, page, onOpen = { openTool(it) })
                            }
                        }
                    }
                }
            }
        }
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
                val graphColor = Cyan
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
                        drawLine(graphColor, points[i], points[i + 1], strokeWidth = 4.dp.toPx())
                    }
                    points.forEach { drawCircle(graphColor, 3.dp.toPx(), it) }
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
    var homeAddress by remember(context) { mutableStateOf(VoiceNavigation.homeAddress(context)) }
    var result by remember { mutableStateOf("") }
    Text("Basic eszközök", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🌐 Biztonságos fiókmegnyitás", color = White, fontWeight = FontWeight.Bold)
        Text("Az UC Journal csak a hivatalos GOV.UK bejelentkezés után olvasható. " +
            "Jarvis nem ismeri a jelszavadat, és nem tudja ellenőrizni az új üzeneteket.",
            color = Soft, fontSize = 12.sp)
        Button(onClick = {
            result = ExternalTasks.dispatch(context, HandoffTask.UcJournal)
        }) { Text("UC – hivatalos bejelentkezés") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📧 Email", color = White, fontWeight = FontWeight.Bold)
        Text("Valódi emailírás a telefon levelezőalkalmazásával. Gmail-fiók olvasásához külön OAuth-kapcsolat szükséges.", color = Soft, fontSize = 12.sp)
        Button(onClick = { result = if (openEmail(context)) "Email alkalmazás megnyitva." else "Nincs email alkalmazás." }) { Text("Email írása") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🧭 Navigáció", color = White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = homeAddress, onValueChange = { homeAddress = it },
            label = { Text("Otthoni cím (csak ezen a telefonon)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (homeAddress.isBlank()) {
                result = "Adj meg egy teljes címet a hazanavigációhoz."
            } else {
                VoiceNavigation.saveHome(context, homeAddress)
                result = "Az otthoni címed titkosított helyi tárhelyre került."
            }
        }) { Text("Otthoni cím mentése") }
        OutlinedButton(onClick = { result = if (openMap(context)) "Térkép megnyitva."
            else "Nincs térképalkalmazás." }) { Text("Térkép megnyitása") }
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
        Text("⏰ Jarvis ébresztő", color = White, fontWeight = FontWeight.Bold)
        val activeAlarm = JarvisAlarms.current(context)
        Text(
            if (activeAlarm > 0L) "Következő ébresztő: " +
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date(activeAlarm))
            else "Nincs aktív Jarvis-ébresztő.",
            color = Soft, fontSize = 12.sp
        )
        Text("Hangos értesítéssel jelez. Az ébresztés pontos időzítéséhez külön Android-engedély kell.",
            color = Soft, fontSize = 12.sp)
        OutlinedButton(onClick = { onGo("Engedélyek") }) { Text("Ébresztési engedélyek") }
        Button(onClick = { result = if (JarvisAlarms.cancel(context)) "A Jarvis-ébresztőt töröltem."
            else "Nincs törölhető Jarvis-ébresztő." }) { Text("Saját ébresztő törlése") }
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
        Button(onClick = { onGo("Ismerősök") }) { Text("Ismerősök, becenevek és címek") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🔒 Integrációk folyamatban", color = White, fontWeight = FontWeight.Bold)
        Text("Központi AI, Gmail-fiókszinkron, Libre 2 szinkron, OBD2, 42 nyelv és PC-s adatszinkron további fejlesztést igényel. A Self-Repair nem kerül mobilra.", color = Soft, fontSize = 12.sp)
        TextButton(onClick = { onGo("Főoldal") }) { Text("Hangos helyi parancsok") }
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
