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
import androidx.lifecycle.ViewModelProvider
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
    private var assistantRequested = false
    private var launcherRequest by mutableStateOf<String?>(null)
    private val assistantMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAssistantVoice()
    }
    private fun startAssistantVoice() {
        val lock = getSystemService(android.app.KeyguardManager::class.java)
        if (lock.isDeviceLocked) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            assistantMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(this,
                Intent(this, HandsFreeService::class.java).setAction(HandsFreeService.START_TAP))
        }.onFailure { HandsFreeStatus.status = "A mikrofon nem indítható. Nyomd meg a Jarvis mikrofonját." }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER))
            launcherRequest = java.util.UUID.randomUUID().toString()
        assistantRequested = JarvisAssistantLaunch.consume(intent)
    }

    override fun onResume() {
        super.onResume()
        PermissionRefresh.serial += 1
        HandsFreeNavigation.appIsForeground = true
        if (assistantRequested) {
            assistantRequested = false
            window.decorView.post { startAssistantVoice() }
        }
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
        assistantRequested = JarvisAssistantLaunch.consume(intent)
        // Quiet daily research scheduling; does not change speech or navigation.
        runCatching { JarvisBackgroundBrain.schedule(applicationContext) }
        setContent { JarvisApp(this, launcherRequest) }
    }
}

private val Night: Color @Composable get() = JarvisPalette.background
private val Panel: Color @Composable get() = JarvisPalette.panel
private val Cyan: Color @Composable get() = JarvisPalette.accent
private val White: Color @Composable get() = JarvisPalette.mainText
private val Soft: Color @Composable get() = JarvisPalette.subtleText

@Composable
fun JarvisApp(context: Context, launcherRequest: String? = null) {
    val securePrefs = remember { SecurePrefs(context) }
    val billingRepo = remember(context) { BillingRepository(context) }
    val narrator = remember(context) { JarvisReadAloud(context) }
    DisposableEffect(narrator) {
        val stopNarrator: () -> Unit = { narrator.stop() }
        JarvisVoiceStreamBridge.stopLocalNarration = stopNarrator
        onDispose {
            if (JarvisVoiceStreamBridge.stopLocalNarration === stopNarrator)
                JarvisVoiceStreamBridge.stopLocalNarration = null
            narrator.release()
        }
    }
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
    val commandDark = JarvisLocalPreferences.requestedDark
    LaunchedEffect(commandDark) {
        if (commandDark != null) {
            isDark = commandDark
            JarvisLocalPreferences.requestedDark = null
        }
    }
    val toggleTheme = {
        isDark = !isDark
        securePrefs.write("ui_dark_mode", isDark.toString())
    }
    var page by remember { mutableStateOf(
        if (onboardingPrefs.getBoolean("permissions_reviewed", false)) "Főoldal" else "Engedélyek"
    ) }
    val controls = remember(page) { JarvisControlRegistry() }
    val subpageScroll = remember(page) { androidx.compose.foundation.ScrollState(0) }
    var commandPanel by remember { mutableStateOf(false) }
    var lastCommandReply by remember { mutableStateOf("") }
    // One active response sink for both synchronous and asynchronous execution.
    var voiceReply by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    fun completeCommand(request: String, answer: String) {
        lastCommandReply = answer
        val reply = voiceReply
        if (reply?.first == request) {
            voiceReply = null
            // Completed local actions are intentionally silent to the microphone session.
            reply.second(if (JarvisQuietActions.hideSuccessfulTurn(request, answer)) "" else answer)
        }
    }
    val chatSession = remember(context) {
        ViewModelProvider(context as ComponentActivity)[JarvisChatSession::class.java]
    }
    val currentChat = chatSession.current
    val conversation = currentChat.turns
    val chatStorageWarning = chatSession.warning
    var commandRevision by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    fun saveChatTurn(turn: Pair<String, String>, aiContext: Boolean = false) {
        // Capture the actual chat object; a delayed response belongs to its origin.
        chatSession.save(currentChat, turn, aiContext)
    }
    fun addHistory(turn: Pair<String, String>) {
        // Voice answers are still delivered, but successful local actions
        // no longer fill the visible or encrypted chat conversation.
        completeCommand(turn.first, turn.second)
        if (JarvisQuietActions.hideSuccessfulTurn(turn.first, turn.second)) return
        conversation.add(turn)
        saveChatTurn(turn)
    }
    var lastAmazonSearch by remember { mutableStateOf<AmazonAction.Search?>(null) }
    fun executeAmazon(action: AmazonAction): String = when (action) {
        is AmazonAction.Search -> {
            lastAmazonSearch = action
            JarvisAmazon.open(context, action)
        }
        is AmazonAction.ChangeToBusiness -> {
            val earlier = lastAmazonSearch
            if (earlier == null) "Melyik terméket keressem az Amazon Businessben?"
            else {
                val next = earlier.copy(business = true)
                lastAmazonSearch = next
                JarvisAmazon.open(context, next)
            }
        }
        else -> JarvisAmazon.open(context, action)
    }
    var gmailDraft by remember { mutableStateOf<GmailDraftConversation?>(null) }
    var gmailChoice by remember { mutableStateOf<GmailRecipientChoice?>(null) }
    // Waiting for an address after a user-initiated Gmail draft. No background send.
    var gmailRecipientWait by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    var gmailLookupInstruction by remember { mutableStateOf<String?>(null) }
    var gmailStatus by remember { mutableStateOf("") }
    var gmailReadText by remember { mutableStateOf("") }
    val gmailOrigins = remember { java.util.IdentityHashMap<GmailRequest, Pair<String, Int>>() }
    val runGmailAction = rememberJarvisGmail { request, outcome ->
        val origin = gmailOrigins.remove(request) ?: return@rememberJarvisGmail
        // Never show a delayed Gmail lookup/error as the reply to a later user turn.
        if (origin.first != currentChat.id || origin.second != commandRevision)
            return@rememberJarvisGmail
        if (request.operation is GmailOperation.ResolveRecipient) {
            if (gmailLookupInstruction != request.instruction) return@rememberJarvisGmail
            gmailLookupInstruction = null
            val addresses = outcome.recipientCandidates
            gmailDraft = null
            gmailChoice = null
            gmailRecipientWait = if (addresses.isEmpty()) request.operation.actuallySend to
                (android.os.SystemClock.elapsedRealtime() + 180_000L) else null
            when {
                addresses.size == 1 -> gmailDraft = GmailDraftConversation(
                    addresses.single(), request.operation.actuallySend,
                    expiresAtMs = android.os.SystemClock.elapsedRealtime() + 180_000L)
                addresses.size > 1 -> gmailChoice = GmailRecipientChoice(
                    addresses, request.operation.actuallySend,
                    android.os.SystemClock.elapsedRealtime() + 90_000L)
            }
        }
        if (request.operation is GmailOperation.Incomplete &&
            request.operation.reason.startsWith("Mondd meg a címzett", ignoreCase = true)) {
            gmailRecipientWait = false to
                (android.os.SystemClock.elapsedRealtime() + 180_000L)
        }
        gmailStatus = outcome.status
        gmailReadText = outcome.speak.orEmpty()
        if ((request.operation is GmailOperation.Read || request.operation == GmailOperation.ListUnread) &&
            outcome.speak != null) page = "Email"
        // Keep private Gmail bodies out of the persisted chat history.
        // Still deliver the asynchronous answer to a pending hands-free turn.
        val fromVoice = voiceReply?.first == request.instruction
        completeCommand(request.instruction, if (fromVoice && outcome.speak != null)
            outcome.speak else outcome.status)
        // Never record mailbox-derived addresses, message body, recipient
        // resolution or completed sends as "Gmail művelet" chat entries.
        // Only a genuine next-step prompt or failure is displayed transiently.
        val mailPrompt = when (request.operation) {
            is GmailOperation.ResolveRecipient -> when (outcome.recipientCandidates.size) {
                0 -> outcome.status
                1 -> "Megtaláltam a címzettet a Gmailben. Mi legyen a tárgy?"
                else -> {
                    page = "Email"
                    "Több megfelelő emailcím van. A kiválasztáshoz nézd meg az Email oldalt, és mondd vagy írd be a sorszámot."
                }
            }
            is GmailOperation.Incomplete -> outcome.status
            GmailOperation.Check -> outcome.status
            else -> if (JarvisGmailFeedback.needsAttention(outcome.status))
                outcome.status else null
        }
        if (mailPrompt != null) {
            conversation.add("Jarvis" to mailPrompt)
            // This prompt is not persisted: Gmail content stays in memory.
        }
        // The microphone service speaks its own response. Do not start a
        // second cloud player over the top of it for the same email.
        if (!fromVoice && request.operation is GmailOperation.Read) outcome.speak?.let {
            if (!narrator.read(it, language)) {
                gmailStatus = outcome.status + " A hangos felolvasás nem indult el; a szöveg az Email oldalon olvasható."
            }
        }
    }
    fun gmailAction(request: GmailRequest) {
        gmailOrigins[request] = currentChat.id to commandRevision
        runGmailAction(request)
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
        // Backward-compatible route guard for pending voice/AI requests from
        // before the Navigation page was removed. Real destination navigation
        // still executes earlier through VoiceNavigation, unchanged.
        if (asPage && destination == "Navigáció") {
            page = "Továbbiak"
            return "A mentett helyek importálása és az otthoni cím most a Továbbiak oldalon van."
        }
        // Legacy AI/tool requests must not reopen the removed Contacts screen.
        // Phonebook lookup and confirmed calls still run through VoiceCalling.
        if (destination == "Névjegyek" || destination == "Hívás") {
            return "A külön Hívás és Névjegyek oldal már nincs a menüben. " +
                "Híváshoz mondd vagy írd be: Hívd fel Esztert."
        }
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
    fun submitAttachments(question: String, attachments: List<JarvisAttachment>) {
        if (attachments.isEmpty() || currentChat.loading) return
        // Upload only as a consequence of explicit Send. No contacts or phone
        // state in payload; never execute any tools returned by attachment AI.
        clearCallFlow()
        commandRevision += 1
        val revision = commandRevision
        val selectedLanguage = language.tag
        val aiHistory = currentChat.aiTurns.toList()
        val waiting = question to "A kiválasztott fájlokat ellenőrzöm és elemzésre küldöm…"
        conversation.add(waiting)
        drawerScope.launch {
            val result = try {
                JarvisAiGateway.askStructured(context, question, selectedLanguage, attachments, aiHistory)
            } finally {
                // Selected files are temporary private cache files, not durable
                // chat history. Delete after the attempt, including HTTP errors.
                withContext(Dispatchers.IO) {
                    attachments.forEach { JarvisAttachments.delete(it) }
                }
            }
            val index = JarvisPendingTurn.findIndex(conversation, waiting)
            if (index >= 0) {
                val response = if (revision == commandRevision) result.message
                    else "Újabb kérés érkezett; az előző AI-elemzést nem hajtottam végre eszközműveletként."
                conversation[index] = question to response
                saveChatTurn(question to response, revision == commandRevision && result.conversationReply)
            }
        }
    }
    fun executeHome(command: String) {
        if (command.isBlank()) return
        // Do this BEFORE any loading guard, Gmail follow-up or AI call.
        // Typed or dictated "állj" must immediately cancel BOTH narrators.
        if (VoiceIntentParser.isPlaybackStop(command)) {
            commandRevision += 1
            JarvisForegroundCommands.interruptSpeech?.invoke()
            JarvisVoiceStreamBridge.interruptActive()
            narrator.stop()
            val notice = JarvisReadAloudCommand.response(language, "stopped")
            // No TTS acknowledgement: the user asked for silence.
            completeCommand(command, notice)
            conversation.add(command to notice)
            return
        }
        if (currentChat.loading) {
            completeCommand(command, JarvisTranslations.text("Betöltés…", language))
            return
        }
        // Every newly submitted command invalidates older pending approvals and
        // any in-flight AI result; a stale network response cannot act later.
        commandRevision += 1
        val requestRevision = commandRevision
        val now = android.os.SystemClock.elapsedRealtime()
        if (gmailLookupInstruction != null) gmailLookupInstruction = null
        if (GmailDraftConversationFlow.isWakeWord(command)) {
            addHistory(command to "Igen, figyelek. Miben segíthetek?")
            return
        }
        gmailRecipientWait?.let { waiting ->
            val address = GmailDraftConversationFlow.recipientAddress(command)
            gmailRecipientWait = null
            if (now <= waiting.second && address != null) {
                gmailDraft = GmailDraftConversation(
                    address, waiting.first, expiresAtMs = now + 180_000L)
                gmailChoice = null
                completeCommand(command, "Rögzítettem a címzettet. Mi legyen az email tárgya?")
                conversation.add("Jarvis" to "Rögzítettem a címzettet. Mi legyen az email tárgya?")
                return
            }
        }
        if (gmailChoice != null) {
            val choice = gmailChoice!!
            if (now > choice.expiresAtMs || GmailDraftConversationFlow.isCancel(command)) {
                gmailChoice = null
                completeCommand(command, "Megszakítottam a Gmail-címzett kiválasztását.")
                conversation.add("Jarvis" to "Nem küldtem emailt.")
                return
            }
            val chosen = GmailDraftConversationFlow.choice(command, choice.options.size)
            if (chosen != null) {
                gmailDraft = GmailDraftConversation(
                    choice.options[chosen], choice.actuallySend,
                    expiresAtMs = now + 180_000L)
                gmailChoice = null
                completeCommand(command, "Kiválasztottam a címzettet a Gmailből. Mi legyen a tárgy?")
                conversation.add("Jarvis" to
                    "Kiválasztottam a címzettet a Gmailből. Mi legyen a tárgy?")
                return
            }
            gmailChoice = null
        }
        if (gmailDraft != null) {
            val oldDraft = gmailDraft!!
            if (now > oldDraft.expiresAtMs) {
                gmailDraft = null
            } else if (GmailDraftConversationFlow.recipientAddress(command) != null) {
                gmailDraft = oldDraft.copy(recipient =
                    GmailDraftConversationFlow.recipientAddress(command)!!,
                    expiresAtMs = now + 180_000L)
                completeCommand(command, "Módosítottam az email címzettjét. Mi legyen a tárgy?")
                conversation.add("Jarvis" to "Módosítottam az email címzettjét. Mi legyen a tárgy?")
                return
            } else when (val next = GmailDraftConversationFlow.advance(oldDraft, command)) {
                GmailDraftNext.Cancelled -> {
                    gmailDraft = null
                    completeCommand(command, "Megszakítottam az emailt. Nem küldtem el.")
                    conversation.add("Jarvis" to "Email megszakítva, nem küldtem el.")
                    return
                }
                GmailDraftNext.NotRelated -> gmailDraft = null
                is GmailDraftNext.Updated -> {
                    gmailDraft = next.draft.copy(expiresAtMs = now + 180_000L)
                    val response = "Rögzítettem a tárgyat. Mi legyen a levél szövege?"
                    completeCommand(command, response)
                    conversation.add("Jarvis" to response)
                    return
                }
                is GmailDraftNext.Complete -> {
                    gmailDraft = null
                    gmailAction(GmailRequest(command, next.send))
                    return
                }
            }
        }
        JarvisControlCommand.parse(command)?.let { control ->
            val result = controls.execute(control, language)
            clearCallFlow()
            pendingMessage = null
            pendingSequence = null
            addHistory(command to result)
            return
        }
        when (JarvisScreenControl.parse(command)) {
            JarvisScreenControl.Action.Back -> if (page != "Főoldal") {
                page = "Főoldal"
                addHistory(command to "Visszaléptem a Jarvis főoldalára.")
                return
            }
            JarvisScreenControl.Action.ScrollDown, JarvisScreenControl.Action.ScrollUp -> if (page != "Főoldal") {
                val down = JarvisScreenControl.parse(command) == JarvisScreenControl.Action.ScrollDown
                drawerScope.launch {
                    subpageScroll.animateScrollTo((subpageScroll.value + if (down) 600 else -600)
                        .coerceIn(0, subpageScroll.maxValue))
                    if (requestRevision == commandRevision) addHistory(command to "Görgettem a Jarvis-oldalt.")
                }
                return
            }
            else -> Unit
        }
        // Every independent typed/voice command preempts stale call flows.
        // Do NOT execute apps here: normal routing below prioritizes internal
        // Jarvis pages, Android intents and installed apps in that order.
        // An unexpired confirmed contact must survive "igen, hívd", "hívd",
        // and "hívd fel". Independent NEW calls or other commands still cancel it.
        // The actual dialing remains inside the explicit confirmation branch.
        val activePendingCall = pendingCall != null && now <= pendingCallUntilMs
        if (JarvisCommandContext.shouldPreemptCallConfirmation(command, activePendingCall))
            clearCallFlow()
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
            if (VoiceCalling.needsTarget(command)) {
                // A one-word partial transcript is NOT an explicit yes.
                // Keep the resolved number while we ask the user to confirm.
                addHistory(command to "A számot megtartottam, de ez nem egyértelmű jóváhagyás. Mondd: igen, hívd – vagy: nem.")
                return
            }
            if (VoiceCalling.extractRequest(command) == null) {
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
        if (pendingMessage != null && (VoiceIntentParser.isYes(command) || VoiceIntentParser.isNo(command))) {
            val draft = pendingMessage
            pendingMessage = null
            val fresh = now <= pendingMessageUntilMs
            pendingMessageUntilMs = 0L
            addHistory(command to if (VoiceIntentParser.isNo(command)) "Megszakítottam az üzenetet."
                else if (fresh && draft != null) ExternalTasks.dispatch(context, HandoffTask.Message(draft))
                else "Lejárt az üzenet jóváhagyása.")
            return
        }
        if (pendingSequence != null && (VoiceIntentParser.isSequenceYes(command) || VoiceIntentParser.isNo(command))) {
            val plan = pendingSequence
            pendingSequence = null
            val fresh = now <= pendingSequenceUntilMs
            pendingSequenceUntilMs = 0L
            addHistory(command to if (VoiceIntentParser.isNo(command)) "Megszakítottam a műveletsort."
                else if (fresh && plan != null) JarvisSequence.execute(context, plan)
                else "Lejárt a műveletsor jóváhagyása.")
            return
        }
        pendingMessage = null
        pendingMessageUntilMs = 0L
        pendingSequence = null
        pendingSequenceUntilMs = 0L
        // AI plans 2-3 explicitly named safe operations. Android executes each
        // native action and returns a typed receipt before the next AI decision.
        // Do not route a compound request through a single-step handler.
        if (JarvisAgentActions.eligible(command) != null) {
            val pending = command to "Az Android-műveleteket tervezem és ellenőrzöm…"
            conversation.add(pending)
            val pendingChatId = currentChat.id
            val pendingIndex = conversation.lastIndex
            drawerScope.launch {
                val result = JarvisAgentActions.run(context, command, language.tag,
                    isCurrent = {
                        requestRevision == commandRevision &&
                            currentChat.id == pendingChatId &&
                            HandsFreeNavigation.appIsForeground
                    },
                    execute = { step ->
                        try {
                            when (step.kind) {
                                AgentKind.THEME -> {
                                    val change = JarvisLocalPreferences.parse(step.input)
                                        as? JarvisLocalPreferences.Command.Theme
                                    if (change == null ||
                                        (if (change.dark) "dark" else "light") != step.target)
                                        AgentReceipt(step.index, AgentStatus.FAILED,
                                            "A kért megjelenés nem ellenőrizhető.")
                                    else {
                                        JarvisLocalPreferences.apply(context, change)
                                        isDark = change.dark
                                        val verified = securePrefs.read("ui_dark_mode", "") ==
                                            change.dark.toString()
                                        AgentReceipt(step.index,
                                            if (verified) AgentStatus.VERIFIED else AgentStatus.FAILED,
                                            if (verified) "Megjelenés ellenőrizve."
                                            else "A megjelenés mentése nem igazolható.")
                                    }
                                }
                                AgentKind.LANGUAGE -> {
                                    val change = JarvisLocalPreferences.parse(step.input)
                                        as? JarvisLocalPreferences.Command.Language
                                    if (change == null || change.selected.tag != step.target)
                                        AgentReceipt(step.index, AgentStatus.FAILED,
                                            "A nyelvválasztás nem ellenőrizhető.")
                                    else {
                                        JarvisLocalPreferences.apply(context, change)
                                        selectLanguage(change.selected)
                                        val verified = JarvisLanguageSettings.load(context) ==
                                            change.selected && language == change.selected
                                        AgentReceipt(step.index,
                                            if (verified) AgentStatus.VERIFIED else AgentStatus.FAILED,
                                            if (verified) "A Jarvis nyelve ellenőrizve."
                                            else "A nyelv mentése nem igazolható.")
                                    }
                                }
                                AgentKind.PAGE -> {
                                    val route = JarvisCapabilities.localPageToOpen(step.input)
                                    if (route != step.target)
                                        AgentReceipt(step.index, AgentStatus.FAILED,
                                            "Az oldal nem egyezik a parancsoddal.")
                                    else {
                                        openTool(route, asPage = true)
                                        AgentReceipt(step.index,
                                            if (page == route) AgentStatus.VERIFIED
                                            else AgentStatus.FAILED,
                                            if (page == route) "A Jarvis oldal megnyílt: " + route
                                            else "Nem sikerült igazolni az oldal megnyitását.")
                                    }
                                }
                                AgentKind.PHONE_APP -> {
                                    val requested = JarvisPhoneActions.requestedAction(step.input)
                                    if (requested != step.target)
                                        AgentReceipt(step.index, AgentStatus.FAILED,
                                            "A telefonos alkalmazás neve nem egyezik.")
                                    else {
                                        val response = JarvisPhoneActions.execute(context, requested)
                                        val passed = response.startsWith("Átadtam") ||
                                            response.startsWith("Megnyitottam")
                                        AgentReceipt(step.index,
                                            if (passed) AgentStatus.HANDOFF else AgentStatus.FAILED,
                                            response)
                                    }
                                }
                                AgentKind.NAVIGATION -> {
                                    val destination = VoiceNavigation.destinationFrom(step.input)
                                    if (destination != step.target)
                                        AgentReceipt(step.index, AgentStatus.FAILED,
                                            "A navigációs cél eltér a parancsoddal.")
                                    else when (VoiceNavigation.openDetailed(context, destination)) {
                                        VoiceNavigation.LaunchResult.GUIDANCE_REQUESTED ->
                                            AgentReceipt(step.index, AgentStatus.HANDOFF,
                                                "A navigációt átadtam a Google Mapsnek; " +
                                                    "az indulás nincs visszaigazolva.")
                                        VoiceNavigation.LaunchResult.DIRECTIONS_OPENED ->
                                            AgentReceipt(step.index, AgentStatus.HANDOFF,
                                                "Megnyílt az útvonalnézet; a Start még szükséges lehet.")
                                        VoiceNavigation.LaunchResult.SAVED_NAME_SEARCH_OPENED ->
                                            AgentReceipt(step.index, AgentStatus.HANDOFF,
                                                "Megnyitottam a Google Maps névkeresését. Válaszd ki a helyes mentett helyet.")
                                        VoiceNavigation.LaunchResult.UNAVAILABLE ->
                                            AgentReceipt(step.index, AgentStatus.FAILED,
                                                "Nem sikerült megnyitni a navigációt.")
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            AgentReceipt(step.index, AgentStatus.FAILED,
                                "Android-műveleti hiba. A következő lépés nem indult.")
                        }
                    })
                if (requestRevision == commandRevision && currentChat.id == pendingChatId) {
                    val index = if (pendingIndex in conversation.indices &&
                        conversation[pendingIndex].first == command) pendingIndex
                    else JarvisPendingTurn.findIndex(conversation, pending)
                    if (index >= 0) {
                        conversation[index] = command to result
                        saveChatTurn(command to result)
                        completeCommand(command, result)
                    }
                }
            }
            return
        }
        if (JarvisUpdateVoice.isRequest(command)) {
            JarvisUpdateNavigation.request(JarvisUpdateVoice.shouldInstall(command))
            val message = JarvisUpdateTexts.get(language,
                if (JarvisUpdateVoice.shouldInstall(command)) "voiceInstall" else "voiceCheck")
            addHistory(command to message)
            openTool("Beállítások", asPage = true)
            return
        }
        // The private Google Maps lists are never accessible from a generic Maps API key.
        // Show the on-device imported copy, or the import option, not an invented cloud answer.
        if (JarvisSavedPlaces.isListRequest(command)) {
            val count = JarvisSavedPlaces.list(context).size
            val answer = if (count > 0)
                "Megnyitom a $count importált mentett helyet. Ez helyi másolat, nem élő Google-fiók kapcsolat."
            else "A Google Maps mentett helyeihez nincs élő hozzáférésem. " +
                "Megnyitom a Beállítások → Továbbiak oldalt, ahol Google Takeout CSV vagy GeoJSON fájlt importálhatsz."
            openTool("Továbbiak", asPage = true)
            addHistory(command to answer)
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
        // An email address alone is not an instruction to open an empty composer
        // or disclose the address to the general AI model.
        GmailDraftConversationFlow.recipientAddress(command)?.let {
            completeCommand(command, "Emailcímet adtál meg. Levelet csak kifejezett kérésre készítek elő.")
            conversation.add("Jarvis" to "Emailcímet adtál meg. Levelet csak kifejezett kérésre készítek elő.")
            return
        }
        // Route user-originated Gmail actions before generic narration or AI.
        // Only the real Google authorization flow can grant email access.
        JarvisGmailCommands.parse(command)?.let { request ->
            if (request.operation is GmailOperation.ResolveRecipient) {
                gmailLookupInstruction = request.instruction
                gmailDraft = null
                gmailChoice = null
            }
            gmailAction(request)
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
        JarvisLocalPreferences.parse(command)?.let { change ->
            addHistory(command to JarvisLocalPreferences.apply(context, change))
            return
        }
        JarvisScreenControl.parse(command)?.let { action ->
            addHistory(command to JarvisScreenControl.execute(context, action))
            return
        }
        // Navigation commands have priority over generic "open Maps" shortcuts.
        // The recognized city/address must be passed directly to Google's
        // turn-by-turn intent, never dropped in favour of an empty map view.
        VoiceNavigation.destinationFrom(command)?.let { destination ->
            val result = if (VoiceNavigation.resolve(context, destination) == null &&
                !VoiceNavigation.maySearchNamedPlace(destination))
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
        // Resolve internal Jarvis page requests BEFORE Amazon's external
        // marketplace handoff. "Open the Amazon page" must stay in Jarvis;
        // otherwise UI/voice navigation can send the Activity to background.
        // An actual product search still opens Amazon's retailer results.
        JarvisAmazon.parse(command)?.let { action ->
            addHistory(command to executeAmazon(action))
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
        if (SpokenAlarmTime.isSetIntent(command)) {
            addHistory(command to JarvisAlarmText.invalid(context))
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
            // Already resolved the exact app; do not query the entire launcher list
            // twice again. openSelected still revalidates the Android component.
            addHistory(command to JarvisInstalledApps.openSelected(context, appSelection))
            
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
        if (JarvisAiRouting.shouldAskAi(command, JarvisAiGateway.configured(context))) {
            // Deep technical requests get a bounded plan -> evidence -> review loop.
            // All device commands retain the existing native execution path.
            val researchAgent = JarvisAgentResearch.shouldHandle(command)
            val aiHistory = currentChat.aiTurns.toList()
            val pending = command to "AI-válaszra várok…"
            conversation.add(pending)
            val pendingIndex = conversation.lastIndex
            val pendingChatId = currentChat.id
            val progressive = StringBuilder()
            var lastVisibleUpdateMs = 0L
            drawerScope.launch {
                val spokenStream = !researchAgent &&
                    JarvisAiGateway.canStreamText(context, command) &&
                    voiceReply?.first == command
                val streamCancellation = JarvisStreamCancellation()
                val aiJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                val abortTurn: (() -> Unit)? = if (spokenStream) {
                    {
                        if (requestRevision == commandRevision &&
                            voiceReply?.first == command) {
                            // Clear the old answer on real user interruption.
                            commandRevision += 1
                            voiceReply = null
                            if (pendingIndex in conversation.indices &&
                                conversation[pendingIndex].first == command) {
                                val cancelled = command to "A hangos választ megszakítottad."
                                conversation[pendingIndex] = cancelled
                                saveChatTurn(cancelled)
                            }
                            streamCancellation.cancel()
                            aiJob?.cancel()
                        }
                    }
                } else null
                if (abortTurn != null)
                    JarvisVoiceStreamBridge.interruptCurrent = abortTurn
                try {
                val outcome = if (researchAgent) {
                    JarvisAgentResearch.answer(context, command, language.tag, aiHistory)
                } else if (JarvisAiGateway.canStreamText(context, command)) {
                    JarvisAiGateway.askIncremental(context, command, language.tag,
                        cancellation = streamCancellation,
                        onDelta = { delta ->
                            if (requestRevision == commandRevision &&
                                currentChat.id == pendingChatId &&
                                pendingIndex in conversation.indices &&
                                conversation[pendingIndex].first == command) {
                                progressive.append(delta)
                                // Do not recompose the entire chat for every tiny SSE token.
                                val nowMs = android.os.SystemClock.elapsedRealtime()
                                if (nowMs - lastVisibleUpdateMs >= 85L ||
                                    delta.contains('\n') || progressive.length < 30) {
                                    conversation[pendingIndex] = command to progressive.toString()
                                    lastVisibleUpdateMs = nowMs
                                }
                            }
                        },
                        onPhrase = { phrase ->
                            // Only an already-active hands-free turn receives sound.
                            // Typed chat never starts an unsolicited microphone/TTS.
                            if (requestRevision == commandRevision &&
                                voiceReply?.first == command)
                                JarvisVoiceStreamBridge.deliver(command, phrase)
                        }, history = aiHistory)
                } else JarvisAiGateway.askStructured(context, command, language.tag, history = aiHistory)
                if (requestRevision != commandRevision) {
                    val oldIndex = if (pendingIndex in conversation.indices &&
                        conversation[pendingIndex].first == command) pendingIndex
                        else JarvisPendingTurn.findIndex(conversation, pending)
                    if (oldIndex >= 0) {
                        val cancelled = command to "Egy újabb kérés miatt a korábbi AI-parancsot megszakítottam."
                        conversation[oldIndex] = cancelled
                        saveChatTurn(cancelled)
                    }
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
                val index = if (pendingIndex in conversation.indices &&
                    conversation[pendingIndex].first == command) pendingIndex
                    else JarvisPendingTurn.findIndex(conversation, pending)
                if (index >= 0) {
                    conversation[index] = command to answer
                    saveChatTurn(command to answer, outcome.conversationReply)
                    completeCommand(command, answer)
                }
                } finally {
                    // Do not clear a newer turn's cancellation callback.
                    if (JarvisVoiceStreamBridge.interruptCurrent === abortTurn)
                        JarvisVoiceStreamBridge.interruptCurrent = null
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
    val latestExecutor = rememberUpdatedState<(String) -> Unit> { executeHome(it) }
    DisposableEffect(context) {
        val handler: (String, (String) -> Unit) -> Unit = { command, respond ->
            voiceReply = command to respond
            latestExecutor.value(command)
        }
        JarvisForegroundCommands.handler = handler
        onDispose {
            if (JarvisForegroundCommands.handler === handler) JarvisForegroundCommands.handler = null
            voiceReply = null
        }
    }
    fun executeTyped(command: String) {
        voiceReply = null
        JarvisForegroundCommands.interruptSpeech?.invoke()
        executeHome(command)
    }
    fun selectConversation(id: String? = null) {
        commandRevision += 1
        voiceReply?.second?.invoke(JarvisTranslations.text("Új beszélgetés", language))
        voiceReply = null
        JarvisForegroundCommands.interruptSpeech?.invoke()
        clearCallFlow()
        pendingMessage = null
        pendingSequence = null
        gmailDraft = null
        gmailChoice = null
        gmailRecipientWait = null
        gmailLookupInstruction = null
        lastAmazonSearch = null
        lastCommandReply = ""
        commandPanel = false
        if (id == null) chatSession.newChat() else chatSession.open(id)
        page = "Főoldal"
        drawerScope.launch { drawerState.close() }
    }
    LaunchedEffect(launcherRequest) {
        // Only a fresh launcher intent starts another chat. Returning from Maps,
        // permissions, Gmail authorization or the file picker keeps the session.
        if (launcherRequest != null) selectConversation()
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
        LocalJarvisControls provides controls,
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
            RegisterJarvisControl("Új beszélgetés", click = { selectConversation() })
            if (commandPanel) {
                JarvisCommandPanel(context, lastCommandReply,
                    onCommand = { executeTyped(it) }, onDismiss = { commandPanel = false })
            }
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    JarvisDrawer(
                        selected = page,
                        conversations = chatSession.summaries,
                        currentConversationId = currentChat.id,
                        onNewConversation = { selectConversation() },
                        onOpenConversation = { selectConversation(it) },
                        onSelect = { selected ->
                            openTool(selected, asPage = true)
                            drawerScope.launch { drawerState.close() }
                        }
                    )
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
                        key(currentChat.id) {
                        JarvisHomeScreen(
                            loadingConversation = currentChat.loading,
                            context = context,
                            isDark = isDark,
                            onToggleTheme = toggleTheme,
                            language = language,
                            onSelectLanguage = selectLanguage,
                            onMenu = { drawerScope.launch { drawerState.open() } },
                            onCommand = { command -> executeTyped(command) },
                            onAttachmentsSubmit = { question, selected ->
                                submitAttachments(question, selected)
                            },
                            history = conversation,
                            hasOlderHistory = currentChat.hasOlder,
                            loadingOlderHistory = currentChat.loadingOlder,
                            onLoadOlderHistory = { chatSession.loadEarlier(currentChat) },
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
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(page, color = White, fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).testTag("jarvis-subpage-header")
                                    .clickable { drawerScope.launch { drawerState.open() } })
                            TextButton(onClick = { commandPanel = true }, modifier = Modifier.testTag("jarvis-command-panel")) {
                                Text(JarvisCommandTexts.text(language, "command"))
                            }
                            JarvisLanguagePicker(language = language, onSelect = selectLanguage)
                            IconButton(onClick = toggleTheme, modifier = Modifier.testTag("jarvis-subpage-theme-toggle")) {
                                Text(if (isDark) "☀" else "☾", color = Cyan, fontSize = 23.sp)
                            }
                            ControlledTextButton(
                                modifier = Modifier.testTag("jarvis-subpage-home-button"),
                                onClick = { page = "Főoldal" }
                            , commandLabel = "Főoldal") {
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
                                .verticalScroll(subpageScroll)
                                .padding(16.dp)
                        ) {
                            when (page) {
                                "Beállítások" -> JarvisSettingsScreen(
                                    context, isDark, toggleTheme,
                                    onVoice = { page = "Hangbeállítások" },
                                    onAi = { page = "AI-agy" },
                                    onMore = { page = "Továbbiak" },
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
                                "Előzmények" -> JarvisChatHistoryScreen(context, onOpenConversation = { selectConversation(it) })
                                "Üzenetek" -> MessageInboxScreen(context, onPermissions = {
                                    page = "Engedélyek"
                                })
                                "Vércukor" -> JarvisGlucoseDashboard(context, readings) {
                                    securePrefs.write("readings", writeReadings(readings))
                                }
                                "Számlák" -> JarvisBillingScreen(context, billingRepo, language)
                                "Email" -> JarvisGmailScreen(language, gmailStatus, gmailReadText, ::gmailAction)
                                "Amazon" -> JarvisAmazonScreen(language, ::executeAmazon)
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
        ControlledOutlinedTextField(value = client, onValueChange = { client = it }, label = { Text("Ügyfél neve") }, modifier = Modifier.fillMaxWidth(), commandLabel = "Ügyfél neve")
        ControlledOutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Összeg (£)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), commandLabel = "Összeg (£)")
        ControlledButton(onClick = {
            val value = amount.replace(',', '.').toDoubleOrNull()
            if (client.isBlank() || value == null || value <= 0 || value > 1_000_000) {
                error = "Adj meg egy ügyfélnevet és érvényes összeget."
            } else {
                invoices.add(0, Invoice(client.trim(), "%.2f".format(Locale.UK, value), timeNow()))
                persist(); client = ""; amount = ""; error = ""
            }
        }, commandLabel = "Számlavázlat mentése") { Text("Számlavázlat mentése") }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFFA5A5))
    }
    Spacer(Modifier.height(12.dp))
    invoices.forEachIndexed { index, invoice ->
        SurfaceCard {
            Text(invoice.client, fontWeight = FontWeight.Bold)
            Text("£" + invoice.amount + "  ·  " + invoice.stamp, color = Soft)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ControlledTextButton(onClick = { shareInvoicePdf(context, invoice) }, commandLabel = "PDF megosztás") { Text("PDF megosztás") }
                ControlledTextButton(onClick = { invoices.removeAt(index); persist() }, commandLabel = "Törlés") { Text("Törlés") }
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
        ControlledOutlinedTextField(value = note, onValueChange = { note = it },
            label = { Text("Új jegyzet") }, modifier = Modifier.fillMaxWidth(), commandLabel = "Új jegyzet")
        ControlledButton(onClick = {
            if (note.isNotBlank()) { notes.add(0, note.trim()); persist(); note = "" }
        }, commandLabel = "Mentés") { Text("Mentés") }
        notes.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, Modifier.weight(1f), color = White)
                ControlledTextButton(onClick = { notes.removeAt(index); persist() }, commandLabel = "Törlés") { Text("Törlés") }
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
        ControlledButton(onClick = {
            result = ExternalTasks.dispatch(context, HandoffTask.UcJournal)
        }, commandLabel = "UC – hivatalos bejelentkezés") { Text("UC – hivatalos bejelentkezés") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📧 Email", color = White, fontWeight = FontWeight.Bold)
        Text("Valódi emailírás a telefon levelezőalkalmazásával. Gmail-fiók olvasásához külön OAuth-kapcsolat szükséges.", color = Soft, fontSize = 12.sp)
        ControlledButton(onClick = { result = if (openEmail(context)) "Email alkalmazás megnyitva." else "Nincs email alkalmazás." }, commandLabel = "Email írása") { Text("Email írása") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🧭 Navigáció", color = White, fontWeight = FontWeight.Bold)
        ControlledOutlinedTextField(value = homeAddress, onValueChange = { homeAddress = it },
            label = { Text("Otthoni cím (csak ezen a telefonon)") }, modifier = Modifier.fillMaxWidth(), commandLabel = "Otthoni cím (csak ezen a telefonon)")
        ControlledButton(onClick = {
            if (homeAddress.isBlank()) {
                result = "Adj meg egy teljes címet a hazanavigációhoz."
            } else {
                VoiceNavigation.saveHome(context, homeAddress)
                result = "Az otthoni címed titkosított helyi tárhelyre került."
            }
        }, commandLabel = "Otthoni cím mentése") { Text("Otthoni cím mentése") }
        ControlledButton(onClick = {
            result = if (VoiceNavigation.resolve(context, "HOME") == null)
                "Előbb add meg és mentsd el az otthoni címedet."
            else JarvisNavigationText.result(
                JarvisLanguageSettings.load(context), VoiceNavigation.openDetailed(context, "HOME"))
        }, commandLabel = "Navigálás haza") { Text("Navigálás haza") }
        ControlledOutlinedButton(onClick = { result = if (openMap(context)) "Térkép megnyitva."
            else "Nincs térképalkalmazás." }, commandLabel = "Térkép megnyitása") { Text("Térkép megnyitása") }
        // Keep the local saved-places file picker accessible after removing
        // the standalone Navigation UI. It never requires Google credentials.
        JarvisSavedPlacesPanel(context)
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📅 Naptár és emlékeztetők", color = White, fontWeight = FontWeight.Bold)
        ControlledOutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Esemény címe") }, modifier = Modifier.fillMaxWidth(), commandLabel = "Esemény címe")
        ControlledButton(onClick = {
            result = if (eventName.isNotBlank() && addCalendarEvent(context, eventName.trim()))
                "A naptárban ellenőrizd és mentsd az eseményt."
            else "Adj meg címet, és legyen telepített naptár."
        }, commandLabel = "Esemény létrehozása") { Text("Esemény létrehozása") }
        ControlledOutlinedButton(onClick = { result = if (openAlarms(context)) "Ébresztők megnyitva." else "Nincs óra alkalmazás." }, commandLabel = "Ébresztők") { Text("Ébresztők") }
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
        ControlledOutlinedButton(onClick = { onGo("Engedélyek") }, commandLabel = "Ébresztési engedélyek") { Text("Ébresztési engedélyek") }
        ControlledButton(onClick = { result = if (JarvisAlarms.cancel(context)) "A Jarvis-ébresztőt töröltem."
            else "Nincs törölhető Jarvis-ébresztő." }, commandLabel = "Saját ébresztő törlése") { Text("Saját ébresztő törlése") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("📞 Telefon", color = White, fontWeight = FontWeight.Bold)
        Text("A tárcsázót nyitja meg. Hívást nem indít önállóan.", color = Soft, fontSize = 12.sp)
        ControlledOutlinedButton(onClick = { result = if (openDialer(context)) "Tárcsázó megnyitva." else "Nincs tárcsázó alkalmazás." }, commandLabel = "Tárcsázó") { Text("Tárcsázó") }
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
        ControlledButton(onClick = { onGo("Engedélyek") }, commandLabel = "Engedélyközpont megnyitása") { Text("Engedélyközpont megnyitása") }
        ControlledOutlinedButton(onClick = { onGo("Üzenetek") }, commandLabel = "WhatsApp / Messenger / üzenetek") { Text("WhatsApp / Messenger / üzenetek") }
    }
    Spacer(Modifier.height(12.dp))
    SurfaceCard {
        Text("🔒 Integrációk folyamatban", color = White, fontWeight = FontWeight.Bold)
        Text("Központi AI, Gmail-fiókszinkron, Libre 2 szinkron, OBD2, 42 nyelv és PC-s adatszinkron további fejlesztést igényel. A Self-Repair nem kerül mobilra.", color = Soft, fontSize = 12.sp)
        ControlledTextButton(onClick = { onGo("Főoldal") }, commandLabel = "Hangos helyi parancsok") { Text("Hangos helyi parancsok") }
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

