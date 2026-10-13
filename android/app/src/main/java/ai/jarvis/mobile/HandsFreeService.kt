package ai.jarvis.mobile

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object HandsFreeStatus {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Kikapcsolva")
    var heard by mutableStateOf("")
    var answer by mutableStateOf("")
    var speaking by mutableStateOf(false)
    var userSpeaking by mutableStateOf(false)
    var micLevel by mutableFloatStateOf(0f)
    var outputLevel by mutableFloatStateOf(0f)
}
internal data class PendingPhoneApp(val action: String, val createdAtMs: Long)
internal data class PendingNativeAction(val action: String, val original: String, val createdAtMs: Long)
internal data class PendingInstalledApp(val original: String, val createdAtMs: Long)
internal data class PendingSystemPanel(val kind: String, val original: String, val createdAtMs: Long)
internal data class PendingSequence(val plan: JarvisSequencePlan, val createdAtMs: Long)
internal data class PendingExternalTask(val task: HandoffTask, val createdAtMs: Long)
internal data class PendingNavigation(val destination: String, val createdAtMs: Long)
internal data class CompletedVoiceTurn(val id: Long, val request: String, val answer: String)

internal object HandsFreeNavigation {
    var appIsForeground by mutableStateOf(false)
    var destination by mutableStateOf<String?>(null)
    var deviceAction by mutableStateOf<PendingPhoneApp?>(null)
    var nativeAction by mutableStateOf<PendingNativeAction?>(null)
    var installedApp by mutableStateOf<PendingInstalledApp?>(null)
    var systemPanel by mutableStateOf<PendingSystemPanel?>(null)
    var sequence by mutableStateOf<PendingSequence?>(null)
    var mapTarget by mutableStateOf<PendingNavigation?>(null)
    var mailDraft by mutableStateOf<EmailDraft?>(null)
    var external by mutableStateOf<PendingExternalTask?>(null)
    var completedVoiceTurn by mutableStateOf<CompletedVoiceTurn?>(null)
}

/** User-started continuous listening until manually stopped or Android ends service. */
class HandsFreeService : Service(), RecognitionListener {
    companion object {
        const val START = "ai.jarvis.mobile.START_VOICE"
        // Explicitly pressing the large microphone means an immediate command:
        // users must not also say a wake word.
        const val START_TAP = "ai.jarvis.mobile.START_VOICE_TAP"
        const val STOP = "ai.jarvis.mobile.STOP_VOICE"
        private const val CHANNEL = "jarvis_voice"
        private const val ID = 4102
    }
    private val handler = Handler(Looper.getMainLooper())
    private val restart = Runnable { listen() }
    private var recognizer: SpeechRecognizer? = null
    private var cloudVoice: JarvisCloudVoicePlayer? = null
    private var activeVoiceCommand: String? = null
    private var turnCounter = 0L
    private var usingOnDeviceRecognizer = false
    private var recognizedUtteranceLanguage: JarvisLanguage? = null
    private var autoLanguageSwitchEnabled = android.os.Build.VERSION.SDK_INT >= 34
    private var running = false
    private var speaking = false
    private var errors = 0
    private var awaitingCommand = false
    private var tapActivated = false
    private var replyWindowUntil = 0L
    private var lastMessageKey: String? = null
    private var proposed: Pair<String, String>? = null
    private var proposedSequence: JarvisSequencePlan? = null
    private var proposedSequenceUntil = 0L
    private var proposedCall: VoiceCalling.Target? = null
    private var proposedCallUntil = 0L
    // Next speech turn can supply the previously omitted contact/number.
    private var awaitingCallTargetUntil = 0L
    private var awaitingExternalDraft: OutgoingMessage? = null
    private var externalProposal: OutgoingMessage? = null
    private var externalDeadline = 0L
    private var confirmUntil = 0L
    private var aiJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            shutdown("A hangfigyelés leállt.")
            return START_NOT_STICKY
        }
        if (intent?.action !in setOf(START, START_TAP) || running) return START_NOT_STICKY
        // Tap-to-talk is distinct from optional hands-free wake-word listening.
        tapActivated = intent?.action == START_TAP
        awaitingCommand = tapActivated
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || !SpeechRecognizer.isRecognitionAvailable(this)) {
            HandsFreeStatus.status = "Mikrofonengedély vagy Android beszédfelismerő hiányzik."
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Jarvis hangfigyelés", NotificationManager.IMPORTANCE_LOW)
            )
            val stopIntent = PendingIntent.getService(
                this, 1, Intent(this, HandsFreeService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val openIntent = PendingIntent.getActivity(
                this, 2, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notice = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Jarvis – mikrofon aktív")
                .setContentText("Folyamatos beszélgetés · érintsd meg a leállításhoz")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leállítás", stopIntent)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                startForeground(ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, notice)
        } catch (_: Exception) {
            HandsFreeStatus.status = "Az Android nem engedte a mikrofon-szolgáltatást."
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        HandsFreeStatus.running = true
        cloudVoice = JarvisCloudVoicePlayer(applicationContext)
        try {
            usingOnDeviceRecognizer = Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            createRecognizer()
            again(450)
        } catch (_: Exception) {
            if (usingOnDeviceRecognizer) {
                usingOnDeviceRecognizer = false
                try {
                    createRecognizer()
                    again(450)
                } catch (_: Exception) { shutdown("Nem indítható a beszédfelismerő.") }
            } else shutdown("Nem indítható a beszédfelismerő.")
        }
        return START_NOT_STICKY
    }

    private fun createRecognizer() {
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = if (usingOnDeviceRecognizer && Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        else SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(this)
    }

    private fun again(delay: Long) {
        handler.removeCallbacks(restart)
        if (running && !speaking) handler.postDelayed(restart, delay)
    }
    private fun listen() {
        if (!running || speaking) return
        try {
            recognizedUtteranceLanguage = null
            HandsFreeStatus.status = when {
                proposed != null -> "Megerősítést várok: igen vagy nem"
                awaitingCommand -> "Figyelek a parancsodra"
                else -> "Mondd: Jarvis vagy Hey Jarvis"
            }
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                JarvisSpeechRecognition.configure(this, JarvisLanguageSettings.load(applicationContext),
                    autoLanguageSwitchEnabled)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOnDeviceRecognizer)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) {
            errors++
            if (errors >= 5) shutdown("A beszédfelismerés hibázott.")
            else again(2000)
        }
    }
    private fun finishSpokenReply() {
        speaking = false
        HandsFreeStatus.speaking = false
        HandsFreeStatus.outputLevel = 0f
        if (running) {
            if (tapActivated) awaitingCommand = true
            again(450)
        }
    }

    private fun speak(text: String) {
        val wantedLanguage = recognizedUtteranceLanguage ?:
            JarvisLanguageSettings.load(applicationContext)
        HandsFreeStatus.answer = text
        HandsFreeStatus.status = "Jarvis válaszol"
        activeVoiceCommand?.let { request ->
            turnCounter += 1
            HandsFreeNavigation.completedVoiceTurn = CompletedVoiceTurn(turnCounter, request, text)
            activeVoiceCommand = null
        }
        handler.removeCallbacks(restart)
        speaking = true
        HandsFreeStatus.speaking = false
        HandsFreeStatus.micLevel = 0f
        HandsFreeStatus.userSpeaking = false
        recognizer?.cancel()
        val started = cloudVoice?.speak(text, wantedLanguage,
            onStart = { if (running) HandsFreeStatus.speaking = true }
        ) { successful ->
            if (running) {
                if (!successful) HandsFreeStatus.status =
                    cloudVoice?.lastFailure.orEmpty().ifBlank {
                        JarvisReadAloudCommand.response(wantedLanguage, "failed")
                    }
                finishSpokenReply()
            }
        } == true
        if (!started) {
            HandsFreeStatus.status = JarvisReadAloudCommand.response(wantedLanguage, "missingTts")
            finishSpokenReply()
        }
    }
    private fun callMessage(key: String, name: String = "", number: String = "") =
        JarvisCallText.get(recognizedUtteranceLanguage ?:
            JarvisLanguageSettings.load(applicationContext), key, name, number)

    private fun privateVoiceAllowed(): Boolean {
        val guard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = guard.isDeviceLocked
        val enabled = if (locked)
            SecurePrefs(this).read("voice_private_on_lock", "false") == "true"
        else false
        return VoicePrivacyPolicy.mayUsePrivateData(locked, enabled)
    }

    private fun clearProposal() { proposed = null; confirmUntil = 0L }

    private fun proposePhoneCall(target: String, now: Long) {
        if (!privateVoiceAllowed()) {
            speak("Oldd fel a telefont a hívás indításához.")
            return
        }
        if (!VoiceCalling.canCall(this)) {
            speak("Hívás indításához engedélyezd a Telefon hozzáférést az Engedélyközpontban.")
            return
        }
        when (val match = VoiceCalling.lookup(this, target)) {
            is VoiceCalling.Lookup.Found -> {
                proposedCall = match.target
                proposedCallUntil = now + 30_000L
                speak(callMessage("confirm", match.target.label, match.target.number))
            }
            VoiceCalling.Lookup.NoContactsAccess -> speak(callMessage("noContacts"))
            VoiceCalling.Lookup.Ambiguous -> speak(callMessage("ambiguous"))
            VoiceCalling.Lookup.NotFound -> speak(callMessage("missing"))
        }
    }
    private fun process(raw: String) {
        val heard = raw.trim()
        if (!running || heard.isBlank()) { again(600); return }
        HandsFreeStatus.heard = heard
        errors = 0
        val now = SystemClock.elapsedRealtime()
        if (VoiceIntentParser.isStop(heard) || VoiceIntentParser.isStop(
                VoiceIntentParser.afterWakeWord(heard).orEmpty())) {
            shutdown("A hangfigyelés leállt.")
            return
        }
        // The same precedence contract as the text command handler.
        // A NEW user request (app, Jarvis page, setting, navigation or question)
        // immediately cancels all stale phone and draft confirmations.
        val freshAppCommand = (VoiceIntentParser.afterWakeWord(heard) ?: heard).trim()
        val isActiveTurn = tapActivated || awaitingCommand ||
            VoiceIntentParser.afterWakeWord(heard) != null
        if (isActiveTurn && JarvisCommandContext.independentRequest(freshAppCommand)) {
            proposedCall = null
            awaitingCallTargetUntil = 0L
            proposedSequence = null
            awaitingExternalDraft = null
            externalProposal = null
            proposed = null
        }
        if (isActiveTurn &&
            JarvisCapabilities.localPageToOpen(freshAppCommand) == null &&
            JarvisPhoneActions.requestedAction(freshAppCommand) == null &&
            JarvisInstalledApps.requestedLabel(freshAppCommand) != null) {
            proposedCall = null
            awaitingCallTargetUntil = 0L
            if (!privateVoiceAllowed()) {
                speak("Oldd fel a telefont az alkalmazás megnyitásához.")
            } else {
                HandsFreeNavigation.installedApp = PendingInstalledApp(
                    freshAppCommand, now
                )
                speak(if (HandsFreeNavigation.appIsForeground)
                    "Megnyitom a kért alkalmazást."
                else "Hozd előtérbe Jarvist az alkalmazás megnyitásához.")
            }
            return
        }
        if (proposedSequence != null) {
            val plan = proposedSequence
            if (!privateVoiceAllowed() || now > proposedSequenceUntil) {
                proposedSequence = null
                speak("A műveletsor jóváhagyása lejárt vagy a telefon zárolva van. Nem indult el.")
            } else if (VoiceIntentParser.isNo(heard)) {
                proposedSequence = null
                speak("Rendben, egyik műveletet sem indítottam.")
            } else if (VoiceIntentParser.isSequenceYes(heard)) {
                proposedSequence = null
                if (plan != null) {
                    HandsFreeNavigation.sequence = PendingSequence(plan, SystemClock.elapsedRealtime())
                    speak("Jóváhagytad a két lépést. A Jarvis előtérben, harminc másodpercen belül indítja.")
                }
            } else speak("Két művelethez csak az igen vagy nem választ várom.")
            return
        }
        if (awaitingExternalDraft != null || externalProposal != null) {
            if (!privateVoiceAllowed()) {
                awaitingExternalDraft = null
                externalProposal = null
                speak("A telefon lezárva. Privát üzenethez oldd fel.")
                return
            }
            if (now > externalDeadline) {
                awaitingExternalDraft = null
                externalProposal = null
                speak("Lejárt az üzenet előkészítési ideje. Semmit nem küldtem.")
                return
            }
            if (VoiceIntentParser.isNo(heard)) {
                awaitingExternalDraft = null
                externalProposal = null
                speak("Rendben, megszakítottam az üzenetet.")
                return
            }
            val proposedMessage = externalProposal
            if (proposedMessage != null) {
                if (VoiceIntentParser.isYes(heard)) {
                    HandsFreeNavigation.external = PendingExternalTask(
                        HandoffTask.Message(proposedMessage), SystemClock.elapsedRealtime())
                    externalProposal = null
                    speak("Átadtam az üzenet-előkészítést a telefonodnak. " +
                        "A tényleges küldést a WhatsAppban vagy Messengerben neked kell jóváhagynod.")
                } else speak("A címzett és a szöveg ellenőrzéséhez mondd: igen vagy nem.")
                return
            }
            val waiting = awaitingExternalDraft
            if (waiting != null) {
                val body = (VoiceIntentParser.afterWakeWord(heard) ?: heard).trim()
                if (body.length > 1500) {
                    speak("Túl hosszú a diktált üzenet. Mondj rövidebb szöveget.")
                    return
                }
                awaitingExternalDraft = null
                externalProposal = waiting.copy(text = body)
                externalDeadline = now + 40_000L
                speak(waiting.recipient + " részére ezt írnád: " + body.take(350) +
                    ". Megnyissam az üzenetalkalmazásban? Igen vagy nem.")
                return
            }
        }
        if (proposedCall != null) {
            if (!privateVoiceAllowed()) {
                proposedCall = null
                speak("Lezárt képernyőn a privát műveletek tiltva vannak. Oldd fel a telefont.")
                return
            }
            if (now > proposedCallUntil) {
                proposedCall = null
                speak(callMessage("timeout"))
            } else if (VoiceIntentParser.isNo(heard)) {
                proposedCall = null
                speak(callMessage("cancel"))
            } else if (VoiceCalling.callConfirmationYes(heard)) {
                val target = proposedCall
                proposedCall = null
                speak(callMessage(if (target != null && VoiceCalling.place(this, target))
                    "submitted" else "failed"))
            } else speak(callMessage("yesNo"))
            return
        }
        if (awaitingCallTargetUntil > 0L) {
            val validUntil = awaitingCallTargetUntil
            awaitingCallTargetUntil = 0L
            if (now > validUntil) {
                speak("Lejárt a telefonszám megadására szánt idő. Mondd újra, kit hívjak.")
            } else if (VoiceIntentParser.isNo(heard)) {
                speak("Rendben, nem indítok hívást.")
            } else {
                val target = VoiceCalling.followUpTarget(heard)
                if (target == null) {
                    speak("Mondj egy konkrét telefonszámot vagy névjegynevet. Hívás még nem indult.")
                    awaitingCallTargetUntil = now + 30_000L
                } else {
                    activeVoiceCommand = heard
                    proposePhoneCall(target, now)
                }
            }
            return
        }
        if (proposed != null) {
            if (!privateVoiceAllowed()) {
                clearProposal()
                speak("Lezárt képernyőn nem küldök privát üzenetet. Oldd fel a telefont.")
                return
            }
            if (now > confirmUntil) {
                clearProposal()
                speak("Lejárt a megerősítési idő. Nem küldtem el semmit.")
                return
            }
            if (VoiceIntentParser.isNo(heard)) {
                clearProposal()
                speak("Rendben, nem küldtem el.")
                return
            }
            if (VoiceIntentParser.isYes(heard)) {
                val current = proposed
                clearProposal()
                val accepted = current != null && MessageBuffer.reply(this, current.first, current.second)
                speak(if (accepted)
                    "A gyorsválaszt átadtam az alkalmazásnak. A kézbesítést ott ellenőrizd."
                    else "Nem sikerült a gyorsválasz. Nem tudom igazolni az elküldést.")
                return
            }
            speak("Mondd, hogy igen vagy nem. Még semmit nem küldtem el.")
            return
        }
        val afterWake = VoiceIntentParser.afterWakeWord(heard)
        val command = when {
            afterWake != null -> {
                awaitingCommand = false
                if (afterWake.isBlank()) {
                    awaitingCommand = true
                    speak("Figyelek.")
                    return
                }
                afterWake
            }
            awaitingCommand -> { awaitingCommand = false; heard }
            now < replyWindowUntil && VoiceIntentParser.replyDraft(heard) != null -> heard
            else -> { again(600); return }
        }
        // A newer local command also invalidates a previous cloud proposal.
        aiJob?.cancel()
        aiJob = null
        activeVoiceCommand = command
        // Same deterministic on-device executor as typed chat.
        // User must have enabled Jarvis Accessibility manually in Android.
        JarvisLocalPreferences.parse(command)?.let { requested ->
            speak(JarvisLocalPreferences.apply(this, requested))
            return
        }
        JarvisScreenControl.parse(command)?.let { action ->
            speak(if (privateVoiceAllowed())
                JarvisScreenControl.execute(this, action)
            else "Oldd fel a telefont a képernyő vezérléséhez.")
            return
        }
        if (JarvisUpdateVoice.isRequest(command)) {
            val selected = recognizedUtteranceLanguage
                ?: JarvisLanguageSettings.load(applicationContext)
            if (!privateVoiceAllowed()) {
                speak("A frissítéshez oldd fel a telefont.")
            } else {
                val install = JarvisUpdateVoice.shouldInstall(command)
                JarvisUpdateNavigation.request(install)
                HandsFreeNavigation.destination = "Beállítások"
                speak(JarvisUpdateTexts.get(selected,
                    if (install) "voiceInstall" else "voiceCheck"))
            }
            return
        }
        // Existing authorized notification/glucose/billing readers take priority.
        val readAloud = if (JarvisGlucoseVoice.isQuery(command) ||
            BillingVoice.isCandidate(command) || VoiceIntentParser.isReadMessage(command))
            null else JarvisReadAloudCommand.parse(command)
        when (val read = readAloud) {
            ReadAloudRequest.LastAnswer -> {
                if (!privateVoiceAllowed()) {
                    speak("Oldd fel a telefont a korábbi válasz felolvasásához.")
                } else {
                    val previous = HandsFreeStatus.answer
                    speak(if (previous.isBlank()) JarvisReadAloudCommand.response(
                        recognizedUtteranceLanguage ?: JarvisLanguageSettings.load(applicationContext),
                        "noAnswer") else previous)
                }
            }
            is ReadAloudRequest.Literal -> {
                if (privateVoiceAllowed()) speak(read.text)
                else speak("Oldd fel a telefont a megadott szöveg felolvasásához.")
            }
            is ReadAloudRequest.Generate -> {
                if (!privateVoiceAllowed()) {
                    speak("Oldd fel a telefont a felolvasáshoz.")
                } else {
                    val selected = recognizedUtteranceLanguage
                        ?: JarvisLanguageSettings.load(applicationContext)
                    aiJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                        val outcome = JarvisAiGateway.askStructured(applicationContext,
                            JarvisReadAloudCommand.generationPrompt(read.request, selected),
                            selected.tag)
                        if (running) speak(outcome.message)
                    }
                }
            }
            null -> Unit
        }
        if (readAloud != null) return
        // Navigation with a destination is a single local operation. Never
        // let "open Google Maps AND navigate to Leeds" turn into just openMap().
        VoiceNavigation.destinationFrom(command)?.let { destination ->
            val chosen = recognizedUtteranceLanguage ?: JarvisLanguageSettings.load(applicationContext)
            when {
                !privateVoiceAllowed() ->
                    speak("Oldd fel a telefont a navigáció indításához.")
                VoiceNavigation.resolve(this, destination) == null ->
                    speak(JarvisNavigationText.missingDestination(chosen))
                else -> {
                    // Android permits launching Maps from our foreground Activity,
                    // not arbitrarily from a microphone foreground service.
                    HandsFreeNavigation.mapTarget = PendingNavigation(
                        destination, SystemClock.elapsedRealtime()
                    )
                    speak(if (HandsFreeNavigation.appIsForeground)
                        JarvisNavigationText.starting(chosen)
                    else JarvisNavigationText.awaitingForeground(chosen))
                }
            }
            return
        }
        // Page-opening intent wins over a glucose keyword inside its page name.
        JarvisCapabilities.localPageToOpen(command)?.let { route ->
            if (!privateVoiceAllowed()) {
                speak("A telefon zárolva van. Az oldal megnyitásához előbb oldd fel.")
            } else {
                HandsFreeNavigation.destination = route
                speak("Megnyitom a Jarvis $route oldalát, amint Jarvis előtérben van.")
            }
            return
        }
        if (JarvisGlucoseVoice.isQuery(command)) {
            if (!privateVoiceAllowed()) {
                speak("A telefon zárolva van. A személyes egészségügyi adatokhoz előbb oldd fel.")
            } else {
                aiJob?.cancel()
                aiJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                    val result = JarvisGlucoseVoice.reply(
                        applicationContext,
                        recognizedUtteranceLanguage ?: JarvisLanguageSettings.load(applicationContext))
                    if (running) speak(result)
                }
            }
            return
        }
        if (BillingVoice.isCandidate(command)) {
            if (!privateVoiceAllowed()) {
                speak("A számlázási műveletekhez oldd fel a telefont.")
            } else if (BillingVoice.isOpen(command)) {
                HandsFreeNavigation.destination = "Számlák"
                speak(BillingStrings.get(recognizedUtteranceLanguage
                    ?: JarvisLanguageSettings.load(applicationContext), "openPage"))
            } else {
                val chosenLanguage = recognizedUtteranceLanguage
                    ?: JarvisLanguageSettings.load(applicationContext)
                aiJob?.cancel()
                aiJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                    val answer = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        BillingVoice.execute(applicationContext,
                            BillingRepository(applicationContext), command, chosenLanguage)
                    }
                    if (running) speak(answer)
                }
            }
            return
        }
        val sequence = JarvisSequence.plan(command)
        if (sequence != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon lezárva. Két művelethez előbb oldd fel.")
            } else {
                proposedSequence = sequence
                proposedSequenceUntil = now + 40_000L
                speak("Két művelet: először beállítom a Jarvis-ébresztőt " +
                    sequence.alarm.hour + " óra " + sequence.alarm.minute +
                    " percre, majd megnyitom a(z) " + sequence.app +
                    " alkalmazást. Jóváhagyod? Igen vagy nem.")
            }
            return
        }
        val recognizedNative = JarvisNativeActions.identify(command)
        if (recognizedNative != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon lezárva. A művelethez előbb oldd fel.")
            } else if (recognizedNative == JarvisNativeActions.SET_ALARM ||
                recognizedNative == JarvisNativeActions.CANCEL_ALARM) {
                // The Jarvis alarm can be scheduled while the app is in the background.
                speak(JarvisNativeActions.execute(this, recognizedNative, command))
            } else {
                HandsFreeNavigation.nativeAction = PendingNativeAction(
                    recognizedNative, command, SystemClock.elapsedRealtime())
                speak("Előkészítettem a kért Android-műveletet. " +
                    "Előtérben lévő Jarvisból, harminc másodpercen belül nyitható meg.")
            }
            return
        }
        val systemPanel = JarvisSystemPanels.identify(command)
        if (systemPanel != null) {
            if (!privateVoiceAllowed()) speak("A telefon zárolva van. Oldd fel a beállítások megnyitásához.")
            else {
                HandsFreeNavigation.systemPanel = PendingSystemPanel(
                    systemPanel, command, SystemClock.elapsedRealtime())
                speak("A beállítási panel megnyitása előkészítve. " +
                    "Hozd előtérbe Jarvist harminc másodpercen belül.")
            }
            return
        }
        // Only the foreground activity may launch third-party apps. Never
        // execute a background or lock-screen activity start.
        val phoneAction = JarvisPhoneActions.requestedAction(command)
        val userApp = if (JarvisInstalledApps.requestedLabel(command) != null)
            JarvisInstalledApps.choose(command, JarvisInstalledApps.list(this))
        else AppSelection.NotARequest
        if (userApp is AppSelection.Found || userApp is AppSelection.Ambiguous ||
            (userApp is AppSelection.Missing &&
                phoneAction == null &&
                BasicCommandRouter.classify(command) == BasicAction.UNKNOWN &&
                JarvisCapabilities.localPageToOpen(command) == null)) {
            if (!privateVoiceAllowed()) speak("A telefon zárolva van. Előbb oldd fel.")
            else if (userApp is AppSelection.Found) {
                HandsFreeNavigation.installedApp = PendingInstalledApp(
                    command, SystemClock.elapsedRealtime())
                speak("A telepített alkalmazás megnyitása előkészítve. " +
                    "Hozd előtérbe Jarvist harminc másodpercen belül.")
            } else speak(if (userApp is AppSelection.Ambiguous)
                "Több ilyen nevű alkalmazás van; válassz az Alkalmazások oldalon."
                else "Nincs ilyen nevű telepített alkalmazás.")
            return
        }
        if (VoiceIntentParser.isReadMessage(command)) {
            if (!privateVoiceAllowed()) {
                lastMessageKey = null
                speak("A telefon lezárva. A privát üzenetek felolvasásához oldd fel, vagy engedélyezd ezt külön a Jarvisban.")
                return
            }
            val app = VoiceIntentParser.messengerHint(command)
            val item = MessageBuffer.notices.firstOrNull {
                app == null || it.appName.contains(app, ignoreCase = true)
            }
            if (item == null) speak("Nem látok elérhető üzenetértesítést. Engedélyezd az üzenetértesítések hozzáférését.")
            else {
                lastMessageKey = item.key
                replyWindowUntil = now + 45_000L
                speak("Üzenet: " + item.appName + ". " + item.sender + " írta: " +
                    item.body.take(450) + ". Válaszolhatsz: válaszolj neki hogy, és a szöveg.")
            }
            return
        }
        val draft = VoiceIntentParser.replyDraft(command)
        if (draft != null) {
            if (!privateVoiceAllowed()) {
                speak("Lezárt képernyőn nem készítek privát üzenetválaszt.")
                return
            }
            val item = MessageBuffer.notices.firstOrNull { it.key == lastMessageKey }
            if (item == null) speak("Előbb olvastass fel egy üzenetet.")
            else if (!item.canReply) speak("Ehhez az értesítéshez nincs támogatott gyorsválasz.")
            else {
                proposed = item.key to draft
                confirmUntil = now + 40_000L
                speak(item.sender + " részére ezt írtad: " + draft + ". Elküldhetem? Mondd, hogy igen vagy nem.")
            }
            return
        }
        val firstSequence = JarvisSequence.plan(command)
        if (firstSequence != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon lezárva. Két művelethez előbb oldd fel.")
            } else {
                proposedSequence = firstSequence
                proposedSequenceUntil = now + 40_000L
                speak("Két lépés: ébresztő " + firstSequence.alarm.hour +
                    " óra " + firstSequence.alarm.minute + " perc, majd " +
                    firstSequence.app + " alkalmazás. Jóváhagyod? Igen vagy nem.")
            }
            return
        }
        if (command.contains("töröld", ignoreCase = true) && command.contains("ébreszt", ignoreCase = true)) {
            speak(if (JarvisAlarms.cancel(this)) "Töröltem a Jarvis-ébresztőt."
                else "Nem találtam törölhető Jarvis-ébresztőt.")
            return
        }
        val alarmTime = AlarmCommands.parseAlarmTime(command)
        if (alarmTime != null) {
            when (val result = JarvisAlarms.schedule(this, alarmTime)) {
                is JarvisAlarms.Result.Created -> speak("Beállítottam a Jarvis-ébresztőt " + result.time + " időpontra.")
                JarvisAlarms.Result.NoExactAlarmPermission ->
                    speak("Engedélyezd a pontos ébresztéseket a Jarvis engedélyközpontjában.")
                JarvisAlarms.Result.NoNotificationPermission ->
                    speak("Engedélyezd az értesítéseket, hogy az ébresztő jelezzen.")
                JarvisAlarms.Result.Failed -> speak("Nem sikerült beállítani az ébresztőt.")
            }
            return
        }
        val localAiDraft = JarvisMessageIntent.parse(command)
        if (localAiDraft != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon lezárva. Üzenethez előbb oldd fel.")
            } else {
                externalProposal = localAiDraft
                externalDeadline = now + 40_000L
                speak("Helyben előkészített üzenet " + localAiDraft.recipient +
                    " részére: " + localAiDraft.text.take(350) +
                    ". Megnyissam az üzenetalkalmazásban? Igen vagy nem.")
            }
            return
        }
        // Structured AI draft requests must not be intercepted by legacy local parsing.
        val outgoing = if (localAiDraft != null) null
            else ExternalTasks.parseMessage(command)
        if (outgoing != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon lezárva. A privát üzenetekhez előbb oldd fel.")
            } else {
                if (outgoing.text.isBlank()) {
                    awaitingExternalDraft = outgoing
                    externalDeadline = now + 45_000L
                    speak("Kinek írjunk: " + outgoing.recipient + ". Mit írjak neki? " +
                        "Diktáld a szöveget, vagy mondd: mégse.")
                } else {
                    externalProposal = outgoing
                    externalDeadline = now + 40_000L
                    speak("Ezt készítem elő " + outgoing.recipient + " részére: " +
                        outgoing.text.take(350) + ". Megnyissam? Igen vagy nem.")
                }
            }
            return
        }
        if (ExternalTasks.parseUcJournal(command)) {
            HandsFreeNavigation.external = PendingExternalTask(
                HandoffTask.UcJournal, SystemClock.elapsedRealtime())
            speak("A hivatalos UC oldal megnyitását előkészítettem. " +
                "Bejelentkezést és Journal-ellenőrzést még nem végzek automatikusan.")
            return
        }
        val gmailQuery = ExternalTasks.parseGmailSearch(command)
        if (gmailQuery != null) {
            if (!privateVoiceAllowed()) {
                speak("A Gmail megnyitásához oldd fel a telefont.")
            } else {
                HandsFreeNavigation.external = PendingExternalTask(
                    HandoffTask.GmailSearch(gmailQuery), SystemClock.elapsedRealtime())
                speak("A Gmail keresést előkészítettem. A levelek tartalmát még nem látom.")
            }
            return
        }
        if (ExternalTasks.resemblesGmailSearch(command)) {
            speak("Nem tudom melyik levelet keresed. Add meg a feladót vagy tárgyat. " +
                "Gmail OAuth nélkül még nem tudom elolvasni és megválaszolni.")
            return
        }
        val email = VoiceEmail.parse(command)
        if (email != null) {
            HandsFreeNavigation.mailDraft = email
            speak("Előkészítettem a levelet. A küldést csak a levelezőalkalmazásban tudod jóváhagyni; háttérből nem indítok automatikus emailt.")
            return
        }
        val nav = VoiceNavigation.destinationFrom(command)
        if (nav != null) {
            val chosen = recognizedUtteranceLanguage
                ?: JarvisLanguageSettings.load(applicationContext)
            if (VoiceNavigation.resolve(this, nav) == null) {
                speak(JarvisNavigationText.missingDestination(chosen))
            } else {
                // A foreground activity immediately launches Google Maps. A service
                // cannot bypass Android's background-activity-launch restrictions.
                HandsFreeNavigation.mapTarget = PendingNavigation(nav, SystemClock.elapsedRealtime())
                speak(if (HandsFreeNavigation.appIsForeground)
                    JarvisNavigationText.starting(chosen)
                else JarvisNavigationText.awaitingForeground(chosen))
            }
            return
        }
        if (VoiceCalling.needsTarget(command)) {
            awaitingCallTargetUntil = now + 30_000L
            speak("Melyik telefonszámot vagy névjegyet hívjam? Mondd el most.")
            return
        }
        val calledName = VoiceCalling.extractRequest(command)
        if (calledName != null) {
            proposePhoneCall(calledName, now)
            return
        }
        LocalConversation.reply(command)?.let {
            speak(it)
            return
        }
        JarvisCapabilities.replyIfAsked(command)?.let {
            speak(it)
            return
        }
        JarvisCapabilities.localPageToOpen(command)?.let { route ->
            HandsFreeNavigation.destination = route
            speak("A Jarvis $route oldalát megnyitom, amint az alkalmazás előtérbe kerül.")
            return
        }
        // Known system app requests must work locally, like typed commands.
        // The older BasicAction fallback refused these even in the foreground.
        if (phoneAction != null) {
            if (!privateVoiceAllowed()) {
                speak("A telefon zárolva van. Az alkalmazás megnyitásához oldd fel.")
            } else {
                HandsFreeNavigation.deviceAction = PendingPhoneApp(
                    phoneAction, SystemClock.elapsedRealtime())
                speak(if (HandsFreeNavigation.appIsForeground)
                    "Átadom a kért alkalmazás megnyitását az Androidnak."
                else "Az alkalmazás megnyitása előkészítve. Hozd előtérbe Jarvist harminc másodpercen belül.")
            }
            return
        }
        val action = if (JarvisMessageIntent.parse(command) != null)
            BasicAction.UNKNOWN else BasicCommandRouter.classify(command)
        when (action) {
            BasicAction.OPEN_GLUCOSE, BasicAction.OPEN_INVOICES,
            BasicAction.OPEN_HOME, BasicAction.OPEN_NOTES, BasicAction.OPEN_PERMISSIONS -> {
                HandsFreeNavigation.destination = when (action) {
                    BasicAction.OPEN_GLUCOSE -> "Vércukor"
                    BasicAction.OPEN_INVOICES -> "Számlák"
                    BasicAction.OPEN_HOME -> "Főoldal"
                    BasicAction.OPEN_NOTES -> "Jegyzetek"
                    else -> "Beállítások"
                }
                speak("Előkészítettem a kért oldalt a Jarvisban.")
            }
            BasicAction.OPEN_MESSAGES -> speak("Mondd: olvasd fel az utolsó WhatsApp vagy Messenger üzenetemet.")
            BasicAction.UNKNOWN -> {
                if (!JarvisAiRouting.shouldAskAi(command)) {
                    speak("Ezt a parancsot még nem tudom végrehajtani.")
                } else if (!privateVoiceAllowed()) {
                    speak("A telefon lezárva. Az AI-kérdéshez előbb oldd fel.")
                } else {
                    // The remote model may propose only a validated allowlisted
                    // Jarvis page. Actual navigation happens in the foreground UI.
                    handler.removeCallbacks(restart)
                    speaking = true
                    HandsFreeStatus.status = "AI-válaszra várok"
                    aiJob?.cancel()
                    aiJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                        val outcome = JarvisAiGateway.askStructured(applicationContext, command,
                            recognizedUtteranceLanguage?.tag)
                        if (running) {
                            speaking = false
                            when {
                                outcome.route != null -> {
                                    HandsFreeNavigation.destination = outcome.route
                                    speak("A Jarvis " + outcome.route + " oldalát megnyitom, " +
                                        "amikor az alkalmazás előtérbe kerül.")
                                }
                                outcome.deviceAction != null -> {
                                    HandsFreeNavigation.deviceAction = PendingPhoneApp(
                                        outcome.deviceAction, SystemClock.elapsedRealtime())
                                    speak("Az Android-alkalmazást csak az előtérbe hozott " +
                                        "Jarvisból indíthatom. A kérés harminc másodpercig érvényes.")
                                }
                                outcome.nativeAction != null -> {
                                    val kind = outcome.nativeAction
                                    if (!privateVoiceAllowed()) {
                                        speak("A telefon zárolva van. A művelethez oldd fel.")
                                    } else if (kind == JarvisNativeActions.SET_ALARM ||
                                        kind == JarvisNativeActions.CANCEL_ALARM) {
                                        speak(JarvisNativeActions.execute(this@HandsFreeService, kind, command))
                                    } else {
                                        HandsFreeNavigation.nativeAction = PendingNativeAction(
                                            kind, command, SystemClock.elapsedRealtime())
                                        speak("Az Android-műveletet előkészítettem. " +
                                            "Előtérben harminc másodpercig érvényes.")
                                    }
                                }
                                outcome.messageDraft != null -> {
                                    if (!privateVoiceAllowed()) {
                                        speak("A telefon lezárva. Üzenethez oldd fel.")
                                    } else {
                                        externalProposal = outcome.messageDraft
                                        externalDeadline = SystemClock.elapsedRealtime() + 40_000L
                                        speak("Üzenetpiszkozat " + outcome.messageDraft.recipient +
                                            " részére: " + outcome.messageDraft.text.take(350) +
                                            ". Megnyissam a külső alkalmazásban? Igen vagy nem.")
                                    }
                                }
                                outcome.callTarget != null -> {
                                    if (!privateVoiceAllowed()) {
                                        speak("A hívás előkészítéséhez előbb oldd fel a telefont.")
                                    } else if (!VoiceCalling.canCall(this@HandsFreeService)) {
                                        speak("A híváshoz engedélyezd a Telefon hozzáférést.")
                                    } else when (val match = VoiceCalling.lookup(
                                            this@HandsFreeService, outcome.callTarget)) {
                                        is VoiceCalling.Lookup.Found -> {
                                            proposedCall = match.target
                                            proposedCallUntil = SystemClock.elapsedRealtime() + 30_000L
                                            speak(callMessage("confirm", match.target.label, match.target.number))
                                        }
                                        VoiceCalling.Lookup.NoContactsAccess ->
                                            speak(callMessage("noContacts"))
                                        VoiceCalling.Lookup.Ambiguous ->
                                            speak(callMessage("ambiguous"))
                                        VoiceCalling.Lookup.NotFound ->
                                            speak(callMessage("missing"))
                                    }
                                }
                                else -> speak(outcome.message)
                            }
                        }
                    }
                }
            }
            else -> speak("A rendszeralkalmazás megnyitásához nyisd meg Jarvist. Android háttérből nem mindig engedi.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (running && !speaking) HandsFreeStatus.status = "Hallgatlak, mondd a kérésed…"
    }
    override fun onBeginningOfSpeech() {
        HandsFreeStatus.userSpeaking = true
    }
    override fun onRmsChanged(rmsdB: Float) {
        if (!running || speaking) return
        val normalized = ((rmsdB + 2f) / 13f).coerceIn(0f, 1f)
        HandsFreeStatus.micLevel = HandsFreeStatus.micLevel * 0.65f + normalized * 0.35f
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onLanguageDetection(results: Bundle) {
        if (!running || speaking || !autoLanguageSwitchEnabled) return
        val detected = JarvisSpeechRecognition.fromPlatformResult(
            results, JarvisLanguageSettings.load(applicationContext)) ?: return
        recognizedUtteranceLanguage = detected
        if (detected != JarvisLanguageSettings.load(applicationContext)) {
            JarvisSpeechLanguageEvents.detected = detected
            JarvisLanguageSettings.save(applicationContext, detected)
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onResults(results: Bundle?) {
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
        if (running && !speaking)
            process(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
    }
    override fun onError(error: Int) {
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
        if (!running || speaking) return
        if (VoiceRecognitionPolicy.needsSystemFallback(usingOnDeviceRecognizer, error)) {
            // Availability of an offline recognizer does not imply a Hungarian model.
            usingOnDeviceRecognizer = false
            errors = 0
            try {
                createRecognizer()
                HandsFreeStatus.status = "A telefon rendszer-beszédfelismerőjére váltok."
                again(450)
            } catch (_: Exception) {
                shutdown("Nem érhető el a beszédfelismerő. Ellenőrizd a telefon hangbeállításait.")
            }
            return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
            if (autoLanguageSwitchEnabled) {
                // Missing extra-language models must not break the user's original
                // single-language voice command flow. Retry without auto switching.
                autoLanguageSwitchEnabled = false
                errors = 0
                HandsFreeStatus.status = "Automatikus nyelvváltás nem elérhető; a kiválasztott nyelvre váltok."
                again(450)
            } else {
                shutdown("A beszédfelismerés a kiválasztott nyelven nem elérhető. Telepítsd a nyelvi modellt a telefon beállításaiban.")
            }
            return
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            shutdown("A mikrofonengedély megszűnt.")
            return
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            HandsFreeStatus.status = "Nem értettem. Próbáld újra, most figyelek."
            errors = 0
            if (tapActivated) awaitingCommand = true
        } else errors++
        if (errors >= 5) shutdown("A beszédfelismerés nem működik stabilan.")
        else again(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2200 else 1100)
    }
    private fun shutdown(status: String) {
        running = false
        HandsFreeStatus.running = false
        HandsFreeStatus.speaking = false
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
        HandsFreeStatus.outputLevel = 0f
        HandsFreeStatus.status = status
        clearProposal()
        proposedCall = null
        proposedCallUntil = 0L
        awaitingCallTargetUntil = 0L
        proposedSequence = null
        proposedSequenceUntil = 0L
        awaitingExternalDraft = null
        externalProposal = null
        externalDeadline = 0L
        awaitingCommand = false
        tapActivated = false
        lastMessageKey = null
        handler.removeCallbacks(restart)
        aiJob?.cancel()
        aiJob = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        cloudVoice?.release()
        cloudVoice = null
        activeVoiceCommand = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (running) shutdown("A hangfigyelés leállt.")
        super.onDestroy()
    }
}

