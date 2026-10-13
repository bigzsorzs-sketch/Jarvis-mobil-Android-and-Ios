package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

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
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Reading(val value: String, val stamp: String, val source: String = "manual")
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
    val billingRepo = remember(context) { BillingRepository(context) }
    val narrator = remember(context) { JarvisReadAloud(context) }
    DisposableEffect(narrator) { onDispose { narrator.release() } }
    var language by remember { mutableStateOf(JarvisLanguageSettings.load(context)) }
    val selectLanguage: (JarvisLanguage) -> Unit = { selected ->
        if (language != selected) {
            language = selected
            JarvisLanguageSettings.save(context, selected)
        }
    }
    val detectedSpeechLanguage = JarvisSpeechLanguageEvents.detected
    LaunchedEffect(detectedSpeechLanguage) {
        if (detectedSpeechLanguage != null) {
            selectLanguage(detectedSpeechLanguage)
            JarvisSpeechLanguageEvents.detected = null
        }
    }
    val onboardingPrefs = remember { context.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE) }
    var isDark by remember { mutableStateOf(securePrefs.read("ui_dark_mode", "false") == "true") }
    val toggleTheme = {
        isDark = !isDark
        securePrefs.write("ui_dark_mode", isDark.toString())
    }
    var page by remember { mutableStateOf(
        if (onboardingPrefs.getBoolean("permissions_reviewed", false)) "Főoldal" else "Engedélyek"
    ) }
    val chatStore = remember(context) { JarvisChatStore(context) }
    val conversation = remember(chatStore) { mutableStateListOf<Pair<String, String>>() }
    var chatStorageWarning by remember { mutableStateOf("") }
    var oldestChatId by remember(chatStore) { mutableLongStateOf(0L) }
    var hasOlderChat by remember(chatStore) { mutableStateOf(false) }
    var loadingOlderChat by remember(chatStore) { mutableStateOf(false) }
    // Keystore + database access must never block the first Compose frame.
    // Preserve messages submitted while the earlier history is loading.
    LaunchedEffect(chatStore) {
        val loaded = withContext(Dispatchers.IO) { runCatching { chatStore.recent(50) } }
        if (loaded.isSuccess) {
            val earlier = loaded.getOrDefault(emptyList())
            val newest = conversation.toList()
            conversation.clear()
            conversation.addAll(earlier.map { it.request to it.answer })
            newest.forEach { turn -> conversation.add(turn) }
            oldestChatId = earlier.firstOrNull()?.id ?: 0L
            hasOlderChat = earlier.size == 50
        } else {
            chatStorageWarning =
                "Nem tudtam betölteni a helyi beszélgetésnaplót; az adatokat nem töröltem."
        }
    }
    var commandRevision by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    fun saveChatTurn(turn: Pair<String, String>) {
        drawerScope.launch(Dispatchers.IO) {
            try {
                chatStore.append(turn.first, turn.second)
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    chatStorageWarning = "Hiba: egy beszélgetési bejegyzést nem sikerült titkosítva menteni."
                }
            }
        }
    }
    fun addHistory(turn: Pair<String, String>) {
        conversation.add(turn)
        saveChatTurn(turn)
    }
    // Spoken commands share the same encrypted local timeline as typed messages.
    // Consume exactly once even when the Activity is recomposed.
    val completedVoiceTurn = HandsFreeNavigation.completedVoiceTurn
    LaunchedEffect(completedVoiceTurn) {
        completedVoiceTurn?.let { turn ->
            addHistory(turn.request to turn.answer)
            if (HandsFreeNavigation.completedVoiceTurn == turn)
                HandsFreeNavigation.completedVoiceTurn = null
        }
    }
    suspend fun loadEarlierConversation() {
        if (!hasOlderChat || loadingOlderChat || oldestChatId <= 0L) return
        loadingOlderChat = true
        try {
            val entries = withContext(Dispatchers.IO) {
                chatStore.before(oldestChatId, 50)
            }
            if (entries.isNotEmpty()) {
                conversation.addAll(0, entries.map { it.request to it.answer })
                oldestChatId = entries.first().id
            }
            hasOlderChat = entries.size == 50
        } catch (_: Exception) {
            chatStorageWarning = "Nem tudtam betölteni a régebbi helyi beszélgetéseket."
        } finally {
            loadingOlderChat = false
        }
    }
    var pendingCall by remember { mutableStateOf<VoiceCalling.Target?>(null) }
    var pendingCallUntilMs by remember { mutableLongStateOf(0L) }
    // A multi-turn CALL is a local state machine. It never goes to OpenRouter.
    // Keep only the pending intent within this foreground Activity, not a saved contact.
    var awaitingCallTarget by remember { mutableStateOf<String?>(null) }
    var awaitingCallTargetUntilMs by remember { mutableLongStateOf(0L) }
    var callChoices by remember { mutableStateOf<List<VoiceCalling.Target>>(emptyList()) }
    fun clearCallFlow() {
        pendingCall = null
        pendingCallUntilMs = 0L
        awaitingCallTarget = null
        awaitingCallTargetUntilMs = 0L
        callChoices = emptyList()
    }
    fun prepareCallConfirmation(person: VoiceCalling.Target) {
        callChoices = emptyList()
        awaitingCallTarget = null
        awaitingCallTargetUntilMs = 0L
        pendingCall = person
        pendingCallUntilMs = android.os.SystemClock.elapsedRealtime() + 45_000L
    }
    fun resolveCallTarget(target: String): String {
        return when (val found = VoiceCalling.lookup(context, target)) {
            is VoiceCalling.Lookup.Found -> {
                prepareCallConfirmation(found.target)
                "Ezt a számot hívjam: ${found.target.label}, ${found.target.number}? Mondd: igen, hívd – vagy: nem."
            }
            VoiceCalling.Lookup.NotFound -> {
                awaitingCallTarget = target
                awaitingCallTargetUntilMs = android.os.SystemClock.elapsedRealtime() + 90_000L
                "Nem találtam ezt a nevet a telefon névjegyeiben. Mondd vagy írd be a teljes telefonszámot."
            }
            VoiceCalling.Lookup.NoContactsAccess -> {
                awaitingCallTarget = target
                awaitingCallTargetUntilMs = android.os.SystemClock.elapsedRealtime() + 90_000L
                "A Névjegyek engedély hiányzik. Engedélyezd a Jarvisnak, vagy add meg a teljes telefonszámot."
            }
            VoiceCalling.Lookup.Ambiguous -> {
                val matches = VoiceCalling.candidates(context, target)
                callChoices = matches.take(3)
                awaitingCallTarget = ""
                awaitingCallTargetUntilMs = android.os.SystemClock.elapsedRealtime() + 90_000L
                if (matches.isEmpty()) "Több névjegy van. Mondd a pontos telefonszámot."
                else "Több különböző szám található. " +
                    callChoices.mapIndexed { index, item ->
                        "${index + 1}. ${item.label}: ${item.number}"
                    }.joinToString("; ") +
                    if (matches.size > 3) ". További találatok is vannak, add meg a teljes számot."
                    else ". Mondd: első, második, vagy add meg a teljes számot."
            }
        }
    }
    var pendingMessage by remember { mutableStateOf<OutgoingMessage?>(null) }
    var pendingMessageUntilMs by remember { mutableLongStateOf(0L) }
    fun prepareMessageReview(message: OutgoingMessage) {
        pendingMessage = message
        pendingMessageUntilMs = android.os.SystemClock.elapsedRealtime() + 40_000L
    }
    var pendingSequence by remember { mutableStateOf<JarvisSequencePlan?>(null) }
    var pendingSequenceUntilMs by remember { mutableLongStateOf(0L) }
    fun askSequenceReview(plan: JarvisSequencePlan) {
        pendingSequence = plan
        pendingSequenceUntilMs = android.os.SystemClock.elapsedRealtime() + 40_000L
    }
    fun openTool(destination: String, asPage: Boolean = false): String {
        if (!asPage && (destination == "Navigáció" || destination == "Térkép")) {
            val opened = openMap(context)
            if (!opened) page = "Főoldal"
            return if (opened) "A térképkérést átadtam az Androidnak."
                else "Nem találok telepített térképalkalmazást."
        }
        if (!asPage && destination == "Jegyzetek") {
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
        // Every newly submitted command invalidates older pending approvals and
        // any in-flight AI result; a stale network response cannot act later.
        commandRevision += 1
        val requestRevision = commandRevision
        val now = android.os.SystemClock.elapsedRealtime()
        // Every independent typed/voice command preempts stale call flows.
        // Do NOT execute apps here: normal routing below prioritizes internal
        // Jarvis pages, Android intents and installed apps in that order.
        if (JarvisCommandContext.independentRequest(command)) clearCallFlow()
        if (pendingCall != null && now > pendingCallUntilMs) clearCallFlow()
        if (awaitingCallTarget != null && now > awaitingCallTargetUntilMs) clearCallFlow()
        // BEFORE invalidating earlier approvals, consume explicit follow-ups
        // on the original user-initiated call. A bare "igen, hívd" is allowed
        // only for a fresh, unambiguous, resolved telephone number.
        if (pendingCall != null) {
            val person = pendingCall
            if (now > pendingCallUntilMs) {
                clearCallFlow()
                addHistory(command to "Lejárt a hívás jóváhagyása. Mondd újra, kit hívjak.")
                return
            }
            if (VoiceCalling.callConfirmationYes(command)) {
                clearCallFlow()
                val result = if (person != null && VoiceCalling.place(context, person))
                    "A híváskérést átadtam az Androidnak: ${person.label}. A hívás létrejöttét a telefonon ellenőrizd."
                else "A hívás nem indult el. Ellenőrizd a Telefon engedélyét."
                addHistory(command to result)
                return
            }
            if (VoiceIntentParser.isNo(command)) {
                clearCallFlow()
                addHistory(command to "Rendben, nem indítok hívást.")
                return
            }
            if (VoiceCalling.extractRequest(command) == null && !VoiceCalling.needsTarget(command)) {
                addHistory(command to "A hívás indításához válaszolj: igen, hívd – vagy: nem. Ha másik személyt hívnál, mondd a teljes hívási parancsot.")
                return
            }
            clearCallFlow()
        }
        if (awaitingCallTarget != null) {
            val previousTarget = awaitingCallTarget.orEmpty()
            if (now > awaitingCallTargetUntilMs) {
                clearCallFlow()
                addHistory(command to "A korábbi híváskérés lejárt. Mondd újra a címzettet.")
                return
            }
            if (VoiceIntentParser.isNo(command)) {
                clearCallFlow()
                addHistory(command to "Rendben, megszakítottam a híváskérést.")
                return
            }
            VoiceCalling.selectedChoice(command, callChoices)?.let { choice ->
                prepareCallConfirmation(choice)
                addHistory(command to "Ezt a számot hívjam: ${choice.label}, ${choice.number}? Mondd: igen, hívd – vagy: nem.")
                return
            }
            // A user can state "ez a Vivi száma 07478 ...". Only the exact
            // user-supplied number is accepted; never accept the LLM's guess.
            val supplied = VoiceCalling.statedNumber(command)
            if (supplied != null) {
                if (previousTarget.isNotBlank() && supplied.label != supplied.number &&
                    !VoiceCalling.matchesContactName(previousTarget, supplied.label) &&
                    !VoiceCalling.matchesFirstName(previousTarget, supplied.label)) {
                    addHistory(command to "A megadott név eltér a kért címzettől. Mondd újra a teljes hívási parancsot.")
                    clearCallFlow()
                    return
                }
                val label = previousTarget.ifBlank { supplied.label }.removeSuffix("t")
                prepareCallConfirmation(VoiceCalling.Target(label, supplied.number))
                addHistory(command to "Ezt a számot hívjam: $label, ${supplied.number}? Mondd: igen, hívd – vagy: nem.")
                return
            }
            val candidate = VoiceCalling.extractRequest(command)
                ?: JarvisCommandContext.possibleContactReply(command)
            if (candidate == null || VoiceCalling.callConfirmationYes(command)) {
                // A conversational turn isn't a contact. Do not keep treating
                // arbitrary new questions as replies to an old call request.
                clearCallFlow()
                addHistory(command to "A korábbi híváskérést megszakítottam. Új híváshoz mondd: hívd fel, és a nevet.")
                return
            }
            clearCallFlow()
            addHistory(command to resolveCallTarget(candidate))
            return
        }
        pendingMessage = null
        pendingMessageUntilMs = 0L
        pendingSequence = null
        pendingSequenceUntilMs = 0L
        if (JarvisUpdateVoice.isRequest(command)) {
            JarvisUpdateNavigation.request(JarvisUpdateVoice.shouldInstall(command))
            val message = JarvisUpdateTexts.get(language,
                if (JarvisUpdateVoice.shouldInstall(command)) "voiceInstall" else "voiceCheck")
            addHistory(command to message)
            openTool("Beállítások", asPage = true)
            return
        }
        // A visible, authorized notification preview is local data, not an AI prompt.
        if (VoiceIntentParser.isReadMessage(command)) {
            val source = VoiceIntentParser.messengerHint(command)
            val notice = MessageBuffer.notices.firstOrNull {
                source == null || it.appName.contains(source, ignoreCase = true)
            }
            if (notice == null) {
                addHistory(command to "Nincs olvasható üzenetértesítés. Ellenőrizd az értesítési hozzáférést.")
            } else {
                val reply = notice.sender + ": " + notice.body
                narrator.read(reply, language)
                // Do not persist a third-party private notification into chat history.
                addHistory(command to JarvisReadAloudCommand.response(language, "started"))
            }
            return
        }
        // Read-aloud is a device action. It does not ask a model to synthesize audio.
        // Dedicated local data readers always take precedence over generic TTS.
        val readAloud = if (JarvisGlucoseVoice.isQuery(command) ||
            BillingVoice.isCandidate(command) || VoiceIntentParser.isReadMessage(command))
            null else JarvisReadAloudCommand.parse(command)
        when (val read = readAloud) {
            ReadAloudRequest.LastAnswer -> {
                val previous = conversation.asReversed().firstOrNull {
                    it.second.isNotBlank() && it.second != "AI-válaszra várok…" &&
                        !it.second.startsWith("Felolvasás elindítva.")
                }?.second
                if (previous == null) {
                    addHistory(command to JarvisReadAloudCommand.response(language, "noAnswer"))
                } else {
                    narrator.read(previous, language)
                    addHistory(command to (narrator.status.ifBlank {
                        JarvisReadAloudCommand.response(language, "started")
                    }))
                }
            }
            is ReadAloudRequest.Literal -> {
                narrator.read(read.text, language)
                addHistory(command to narrator.status.ifBlank {
                    JarvisReadAloudCommand.response(language, "started")
                })
            }
            is ReadAloudRequest.Generate -> {
                drawerScope.launch {
                    val prompt = JarvisReadAloudCommand.generationPrompt(read.request, language)
                    val outcome = JarvisAiGateway.askStructured(context, prompt, language.tag)
                    if (requestRevision == commandRevision) {
                        // AI answers cannot grant Android actions. Just read plain content.
                        val actual = outcome.message
                        addHistory(command to actual)
                        if (outcome.route == null && outcome.deviceAction == null &&
                            outcome.nativeAction == null && outcome.messageDraft == null &&
                            outcome.callTarget == null) {
                            narrator.read(actual, language)
                        }
                    }
                }
            }
            null -> Unit
        }
        if (readAloud != null) return
        // The optional Android accessibility bridge can perform only
        // deterministic, user-requested, one-step operations. No AI-selected
        // buttons or automatic security confirmations.
        JarvisScreenControl.parse(command)?.let { action ->
            addHistory(command to JarvisScreenControl.execute(context, action))
            return
        }
        // Navigation commands have priority over generic "open Maps" shortcuts.
        // The recognized city/address must be passed directly to Google's
        // turn-by-turn intent, never dropped in favour of an empty map view.
        VoiceNavigation.destinationFrom(command)?.let { destination ->
            val result = if (VoiceNavigation.resolve(context, destination) == null)
                "Az úti célhoz nem találok mentett címet."
            else JarvisNavigationText.result(
                language, VoiceNavigation.openDetailed(context, destination)
            )
            addHistory(command to result)
            return
        }
        // A bare "hívd fel a telefonszámot" has no safe number to dial:
        // ask for the contact instead of accidentally opening the dialer.
        if (VoiceCalling.needsTarget(command)) {
            awaitingCallTarget = ""
            awaitingCallTargetUntilMs = now + 90_000L
            addHistory(command to "Melyik telefonszámot vagy névjegyet hívjam? Mondd vagy írd le pontosan.")
            return
        }
        // Never ask GPT to interpret an explicit phone call. Resolve via
        // Android Contacts/Telecom and require a separate positive confirmation.
        VoiceCalling.extractRequest(command)?.let { target ->
            addHistory(command to resolveCallTarget(target))
            return
        }
        // Explicit page navigation must take priority over modules that see a
        // keyword inside "Nyisd meg a Vércukor oldalt".
        JarvisCapabilities.localPageToOpen(command)?.let { route ->
            addHistory(command to openTool(route, asPage = true))
            return
        }
        // Private, on-device data query: never send a glucose request to the LLM.
        if (JarvisGlucoseVoice.isQuery(command)) {
            drawerScope.launch {
                val result = JarvisGlucoseVoice.reply(context, language)
                if (requestRevision == commandRevision) addHistory(command to result)
            }
            return
        }
        if (BillingVoice.isCandidate(command)) {
            if (BillingVoice.isOpen(command)) {
                addHistory(command to openTool("Számlák", asPage = true))
            } else {
                drawerScope.launch {
                    val chosenLanguage = language
                    val answer = withContext(Dispatchers.IO) {
                        BillingVoice.execute(context, BillingRepository(context), command, chosenLanguage)
                    }
                    billingRepo.refresh()
                    if (requestRevision == commandRevision) addHistory(command to answer)
                }
            }
            return
        }
        // The same strictly validated local action registry is available from
        // typed commands even when the AI service is offline.
        val sequence = JarvisSequence.plan(command)
        if (sequence != null) {
            askSequenceReview(sequence)
            addHistory(command to "Két lépést azonosítottam. Indítás előtt erősítsd meg a műveletsort.")
            
            return
        }
        val native = JarvisNativeActions.identify(command)
        if (native != null) {
            addHistory(command to JarvisNativeActions.execute(context, native, command))
            
            return
        }
        val systemPanel = JarvisSystemPanels.identify(command)
        if (systemPanel != null) {
            addHistory(command to JarvisSystemPanels.open(context, systemPanel, command))
            return
        }
        // A named installed app opens through a user-originated, exact-label
        // request, never an AI-chosen package/activity name.
        val phoneAction = JarvisPhoneActions.requestedAction(command)
        val appSelection = if (JarvisInstalledApps.requestedLabel(command) != null)
            JarvisInstalledApps.choose(command, JarvisInstalledApps.list(context))
        else AppSelection.NotARequest
        if (appSelection is AppSelection.Found ||
            appSelection is AppSelection.Ambiguous ||
            (appSelection is AppSelection.Missing &&
                phoneAction == null &&
                BasicCommandRouter.classify(command) == BasicAction.UNKNOWN &&
                JarvisCapabilities.localPageToOpen(command) == null)) {
            addHistory(command to JarvisInstalledApps.openRequested(context, command))
            
            return
        }
        if (phoneAction != null) {
            addHistory(command to JarvisPhoneActions.execute(context, phoneAction))
            return
        }
        val localDraft = JarvisMessageIntent.parse(command)
        if (localDraft != null) {
            prepareMessageReview(localDraft)
            addHistory(command to
                "Helyben előkészítettem a piszkozatot. Ellenőrizd a címzettet és az üzenetet.")
            
            return
        }
        if (JarvisAiRouting.shouldAskAi(command)) {
            val pending = command to "AI-válaszra várok…"
            conversation.add(pending)
            
            drawerScope.launch {
                val outcome = JarvisAiGateway.askStructured(context, command)
                if (requestRevision != commandRevision) {
                    val oldIndex = conversation.indexOf(pending)
                    if (oldIndex >= 0) conversation[oldIndex] =
                        command to "Egy újabb kérés miatt a korábbi AI-parancsot megszakítottam."
                    return@launch
                }
                val answer = when {
                    outcome.route != null -> openTool(outcome.route)
                    outcome.deviceAction != null -> JarvisPhoneActions.execute(context, outcome.deviceAction)
                    outcome.nativeAction != null ->
                        JarvisNativeActions.execute(context, outcome.nativeAction, command)
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
                if (index >= 0) {
                    conversation[index] = command to answer
                    saveChatTurn(command to answer)
                }
            }
        } else {
            val answer = JarvisCommandExecutor.run(
                context, command, { destination -> openTool(destination) },
                { contact -> prepareCallConfirmation(contact) }
            )
            addHistory(command to answer)
            
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
            // Consume exactly once, including failures, so returning to Jarvis
            // cannot accidentally relaunch old navigation.
            HandsFreeNavigation.mapTarget = null
            val age = android.os.SystemClock.elapsedRealtime() - requestedMap.createdAtMs
            val chosen = JarvisLanguageSettings.load(context)
            val outcome = if (age in 0..30_000L)
                VoiceNavigation.openDetailed(context, requestedMap.destination)
            else VoiceNavigation.LaunchResult.UNAVAILABLE
            HandsFreeStatus.status = if (age in 0..30_000L)
                JarvisNavigationText.result(chosen, outcome)
            else "A korábbi navigációs kérés lejárt. Mondd el újra az úti célt."
        }
    }
    val pendingVoiceSequence = HandsFreeNavigation.sequence
    LaunchedEffect(pendingVoiceSequence, appIsForeground) {
        if (pendingVoiceSequence != null && appIsForeground) {
            HandsFreeNavigation.sequence = null
            val age = android.os.SystemClock.elapsedRealtime() - pendingVoiceSequence.createdAtMs
            val outcome = if (age in 0..30_000L)
                JarvisSequence.execute(context, pendingVoiceSequence.plan)
            else "A kétlépéses hangparancs lejárt. Semmit nem hajtottam végre."
            addHistory("Hangos műveletsor" to outcome)
            
        }
    }
    val pendingPanel = HandsFreeNavigation.systemPanel
    LaunchedEffect(pendingPanel, appIsForeground) {
        if (pendingPanel != null && appIsForeground) {
            HandsFreeNavigation.systemPanel = null
            val age = android.os.SystemClock.elapsedRealtime() - pendingPanel.createdAtMs
            val response = if (age in 0..30_000L)
                JarvisSystemPanels.open(context, pendingPanel.kind, pendingPanel.original)
            else "Lejárt a rendszerbeállítás kérés. Semmi nem történt."
            addHistory("Hangos beállítások" to response)
        }
    }
    val pendingInstalled = HandsFreeNavigation.installedApp
    LaunchedEffect(pendingInstalled, appIsForeground) {
        if (pendingInstalled != null && appIsForeground) {
            HandsFreeNavigation.installedApp = null
            val age = android.os.SystemClock.elapsedRealtime() - pendingInstalled.createdAtMs
            val response = if (age in 0..30_000L)
                JarvisInstalledApps.openRequested(context, pendingInstalled.original)
            else "Lejárt a korábbi alkalmazásmegnyitási kérés. Nem történt indítás."
            addHistory("Hangos alkalmazásmegnyitás" to response)
            
        }
    }
    val pendingNative = HandsFreeNavigation.nativeAction
    LaunchedEffect(pendingNative, appIsForeground) {
        if (pendingNative != null && appIsForeground) {
            HandsFreeNavigation.nativeAction = null
            val age = android.os.SystemClock.elapsedRealtime() - pendingNative.createdAtMs
            val answer = if (age in 0..30_000L)
                JarvisNativeActions.execute(context, pendingNative.action, pendingNative.original)
            else "Lejárt a korábbi Android-művelet. Nem indítottam el semmit."
            addHistory("Hangos Android-művelet" to answer)
            
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
            addHistory("Jarvis művelet" to answer)
        }
    }
    val voiceDestination = HandsFreeNavigation.destination
    LaunchedEffect(voiceDestination, appIsForeground) {
        if (voiceDestination != null && appIsForeground) {
            openTool(voiceDestination, asPage = true)
            HandsFreeNavigation.destination = null
        }
    }
    val readings = remember { mutableStateListOf<Reading>().apply { addAll(readReadings(securePrefs.read("readings", "[]"))) } }
    val invoices = remember { mutableStateListOf<Invoice>().apply { addAll(readInvoices(securePrefs.read("invoices", "[]"))) } }
    val notes = remember { mutableStateListOf<String>().apply { addAll(readNotes(securePrefs.read("notes", "[]"))) } }

    val onHome = page == "Főoldal"
    CompositionLocalProvider(
        LocalJarvisDark provides isDark,
        LocalJarvisLanguage provides language
    ) {
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
                        openTool(selected, asPage = true)
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
                    if (chatStorageWarning.isNotBlank()) {
                        Text(chatStorageWarning, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(8.dp))
                    }
                    if (onHome) {
                        JarvisHomeScreen(
                            context = context,
                            isDark = isDark,
                            onToggleTheme = toggleTheme,
                            language = language,
                            onSelectLanguage = selectLanguage,
                            onMenu = { drawerScope.launch { drawerState.open() } },
                            onCommand = { command -> executeHome(command) },
                            history = conversation,
                            hasOlderHistory = hasOlderChat,
                            loadingOlderHistory = loadingOlderChat,
                            onLoadOlderHistory = { loadEarlierConversation() },
                            pendingCall = pendingCall,
                            pendingMessage = pendingMessage,
                            pendingSequence = pendingSequence,
                            onSequenceConfirm = {
                                val task = pendingSequence
                                val fresh = android.os.SystemClock.elapsedRealtime() <= pendingSequenceUntilMs
                                pendingSequence = null
                                pendingSequenceUntilMs = 0L
                                val outcome = if (fresh && task != null)
                                    JarvisSequence.execute(context, task)
                                else "Lejárt a műveletsor jóváhagyása. Semmit nem hajtottam végre."
                                addHistory("Műveletsor jóváhagyása" to outcome)
                            },
                            onSequenceDismiss = {
                                pendingSequence = null
                                pendingSequenceUntilMs = 0L
                            },
                            onMessageConfirm = {
                                val draft = pendingMessage
                                val fresh = android.os.SystemClock.elapsedRealtime() <= pendingMessageUntilMs
                                pendingMessage = null
                                pendingMessageUntilMs = 0L
                                val answer = if (!fresh || draft == null)
                                    "Lejárt az üzenet jóváhagyása. Nem történt művelet."
                                else ExternalTasks.dispatch(context, HandoffTask.Message(draft))
                                addHistory("Üzenet ellenőrzése" to answer)
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
                                addHistory("Hívás megerősítve" to answer)
                            },
                            onCallDismiss = { clearCallFlow() }
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(page, color = White, fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).testTag("jarvis-subpage-header")
                                    .clickable { drawerScope.launch { drawerState.open() } })
                            JarvisLanguagePicker(language = language, onSelect = selectLanguage)
                            IconButton(onClick = toggleTheme, modifier = Modifier.testTag("jarvis-subpage-theme-toggle")) {
                                Text(if (isDark) "☀" else "☾", color = Cyan, fontSize = 23.sp)
                            }
                            TextButton(
                                modifier = Modifier.testTag("jarvis-subpage-home-button"),
                                onClick = { page = "Főoldal" }
                            ) {
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
                                    onAi = { page = "AI-agy" },
                                    language = language
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
                                "Alkalmazások" -> JarvisInstalledAppsScreen(context)
                                "Előzmények" -> JarvisChatHistoryScreen(context)
                                "Üzenetek" -> MessageInboxScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Névjegyek" -> ContactsScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Vércukor" -> JarvisGlucoseDashboard(context, readings) {
                                    securePrefs.write("readings", writeReadings(readings))
                                }
                                "Számlák" -> JarvisBillingScreen(context, billingRepo, language)
                                "Jegyzetek" -> LocalNotesScreen(notes) {
                                    securePrefs.write("notes", JSONArray(notes).toString())
                                }
                                "Továbbiak" -> MoreScreen(context, notes, {
                                    securePrefs.write("notes", JSONArray(notes).toString())
                                }, { page = it })
                                else -> JarvisToolLanding(context, page, onOpen = { openTool(it, asPage = true) })
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
private fun LocalNotesScreen(notes: MutableList<String>, persist: () -> Unit) {
    var note by remember { mutableStateOf("") }
    SurfaceCard {
        Text("🧠 Titkosítva tárolt jegyzetek", color = White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = note, onValueChange = { note = it },
            label = { Text("Új jegyzet") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (note.isNotBlank()) { notes.add(0, note.trim()); persist(); note = "" }
        }) { Text("Mentés") }
        notes.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, Modifier.weight(1f), color = White)
                TextButton(onClick = { notes.removeAt(index); persist() }) { Text("Törlés") }
            }
        }
    }
}

@Composable
private fun MoreScreen(context: Context, notes: MutableList<String>, persist: () -> Unit, onGo: (String) -> Unit) {
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
    LocalNotesScreen(notes, persist)
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
        TextButton(onClick = { onGo("Főoldal") }) { Text("Hangos helyi parancsok") }
    }
}

private fun timeNow(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date())
private fun readReadings(raw: String): List<Reading> = try {
    val a = JSONArray(raw); (0 until a.length()).map { i -> a.getJSONObject(i).let {
        Reading(it.optString("v"), it.optString("t"), it.optString("src", "manual"))
    } }
} catch (_: Exception) { emptyList() }
private fun writeReadings(a: List<Reading>) = JSONArray().apply {
    a.forEach { put(JSONObject().put("v", it.value).put("t", it.stamp).put("src", it.source)) }
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

