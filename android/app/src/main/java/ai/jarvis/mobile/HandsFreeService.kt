package ai.jarvis.mobile

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import android.speech.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    // Set only after the Android provider confirms it is ready to capture.
    var listening by mutableStateOf(false)
    var micLevel by mutableFloatStateOf(0f)
    var outputLevel by mutableFloatStateOf(0f)
    // Persist actionable output errors across recognition restarts.
    var audioError by mutableStateOf("")
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
    private val recognitionWatchdog = Runnable {
        if (running && recognizerListening && !speaking && !foregroundReplyPending) {
            recognizerListening = false
            HandsFreeStatus.listening = false
            HandsFreeStatus.userSpeaking = false
            HandsFreeStatus.micLevel = 0f
            errors++
            HandsFreeStatus.status = "A beszédfelismerő nem válaszol; újraindítom a mikrofont."
            // Replacing the recognizer invalidates all delayed callbacks.
            runCatching { createRecognizer() }
            again(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_CLIENT, errors))
        }
    }
    private fun recognitionDeadline(delayMs: Long) {
        handler.removeCallbacks(recognitionWatchdog)
        if (running && recognizerListening) handler.postDelayed(recognitionWatchdog, delayMs)
    }
    // SpeechRecognizer is a one-utterance API: track the actual session and
    // asynchronous foreground answer separately. Never start two recognizers,
    // or restart recording while the reply is being generated/read aloud.
    private var recognizerListening = false
    private var speechObservedThisTurn = false
    private var foregroundReplyPending = false
    private val foregroundReplyTimeout = Runnable {
        if (running && foregroundReplyPending) {
            foregroundReplyPending = false
            cancelForegroundStream()
            speak("A válasz nem érkezett meg időben. Újra figyelek rád.")
        }
    }
    private var recognizer: SpeechRecognizer? = null
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cloudCaptureJob: Job? = null
    private var cloudCaptureGeneration = 0L
    private var consecutiveCloudFailures = 0
    private var useAndroidRecognizerFallback = false
    private var recognizerGeneration = 0
    private var cloudVoice: JarvisCloudVoicePlayer? = null
    private var foregroundStream: JarvisCloudVoicePlayer.StreamHandle? = null
    private var foregroundTurnId = 0L
    private fun cancelForegroundStream() {
        foregroundTurnId++
        // Barge-in must cancel the old AI HTTP turn, not only its MP3 audio.
        JarvisVoiceStreamBridge.interruptActive()
        JarvisVoiceStreamBridge.receiver = null
        foregroundStream?.cancel()
        foregroundStream = null
    }
    private var bargeInMonitor: JarvisBargeInMonitor? = null
    private var spokenReplySerial = 0L
    // Non-identifying debug latency metric for measurable Basic voice QA.
    private var lastVoiceEndedAtMs = 0L
    private var activeVoiceCommand: String? = null
    private val aiConversation = mutableListOf<Pair<String, String>>()
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
        // Tapped voice uses the language picked on Home, avoiding optional
        // seven-language OEM auto-switching until voice capture is reliable.
        autoLanguageSwitchEnabled = VoiceSessionPolicy.useAutomaticLanguageSwitch(
            userTappedMic = tapActivated,
            supported = Build.VERSION.SDK_INT >= 34
        )
        awaitingCommand = tapActivated
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || (!JarvisAiGateway.canTranscribe(this) &&
                !SpeechRecognizer.isRecognitionAvailable(this))) {
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
        JarvisForegroundCommands.interruptSpeech = {
            cancelForegroundStream()
            spokenReplySerial++
            bargeInMonitor?.stop()
            aiJob?.cancel()
            cloudVoice?.stop()
            activeVoiceCommand = null
            foregroundReplyPending = false
            handler.removeCallbacks(foregroundReplyTimeout)
            finishSpokenReply()
        }
        HandsFreeStatus.running = true
        HandsFreeStatus.heard = ""
        HandsFreeStatus.audioError = ""
        cloudVoice = JarvisCloudVoicePlayer(applicationContext)
        bargeInMonitor = JarvisBargeInMonitor(applicationContext) { onBargeIn() }
        try {
            // Prefer the system recognizer for the selected language: presence of
            // an offline engine does not mean it has a Hungarian model.
            usingOnDeviceRecognizer = false
            if (!tapActivated || !JarvisAiGateway.canTranscribe(this)) createRecognizer()
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
        handler.removeCallbacks(recognitionWatchdog)
        recognizerListening = false
        HandsFreeStatus.listening = false
        val generation = ++recognizerGeneration
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = if (usingOnDeviceRecognizer && Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        else SpeechRecognizer.createSpeechRecognizer(this)
        // Disconnected Android recognizers may send a late onError after a
        // replacement was started. Never let that callback end a new turn.
        recognizer?.setRecognitionListener(object : RecognitionListener {
            private fun active() = generation == recognizerGeneration
            override fun onReadyForSpeech(params: Bundle?) {
                if (active()) this@HandsFreeService.onReadyForSpeech(params)
            }
            override fun onBeginningOfSpeech() {
                if (active()) this@HandsFreeService.onBeginningOfSpeech()
            }
            override fun onRmsChanged(rmsdB: Float) {
                if (active()) this@HandsFreeService.onRmsChanged(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {
                if (active()) this@HandsFreeService.onBufferReceived(buffer)
            }
            override fun onEndOfSpeech() {
                if (active()) this@HandsFreeService.onEndOfSpeech()
            }
            override fun onError(error: Int) {
                if (active()) this@HandsFreeService.onError(error)
            }
            override fun onResults(results: Bundle?) {
                if (active()) this@HandsFreeService.onResults(results)
            }
            override fun onPartialResults(partials: Bundle?) {
                if (active()) this@HandsFreeService.onPartialResults(partials)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {
                if (active()) this@HandsFreeService.onEvent(eventType, params)
            }
            override fun onLanguageDetection(results: Bundle) {
                if (active()) this@HandsFreeService.onLanguageDetection(results)
            }
        })
    }

    private fun again(delay: Long) {
        handler.removeCallbacks(restart)
        if (running && !speaking && !recognizerListening && !foregroundReplyPending)
            handler.postDelayed(restart, delay)
    }
    /**
     * Two consecutive capture/provider failures should not trap a live voice
     * session in the same failing cloud-recorder loop. Prefer the existing
     * Android recognition path if the platform reports it available. The
     * transcript still passes through the SAME foreground AI and native
     * action validation, including phone-call confirmation.
     */
    private fun recoverCloudRecognition(reason: String) {
        consecutiveCloudFailures++
        val platformAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(this)
        }.getOrDefault(false)
        if (VoiceCaptureRecoveryPolicy.shouldFallback(consecutiveCloudFailures, platformAvailable)) {
            try {
                createRecognizer()
                useAndroidRecognizerFallback = true
                HandsFreeStatus.status =
                    "A felhős beszédfelismerés hibázott. Android beszédfelismerőre váltok."
                again(400)
                return
            } catch (_: Exception) {
                // Do not claim the system recognizer works when it failed to initialize.
            }
        }
        HandsFreeStatus.status = reason
        again(VoiceCaptureRecoveryPolicy.retryDelay(consecutiveCloudFailures))
    }

    /** One cloud-recognized utterance, followed by the SAME foreground GPT-5.2
     * conversation and Android validation pathway as typed requests.
     * Only explicitly enabled, user-started Home microphone sessions use this.
     */
    private fun startCloudCapture() {
        if (!running || cloudCaptureJob?.isActive == true) return
        val generation = ++cloudCaptureGeneration
        val language = JarvisLanguageSettings.load(applicationContext)
        HandsFreeStatus.status = "Hallgatlak · GPT beszédfelismerés"
        HandsFreeStatus.listening = true
        HandsFreeStatus.userSpeaking = false
        cloudCaptureJob = captureScope.launch {
            val result = JarvisCloudSpeechCapture.record(applicationContext) { level, speakingNow ->
                handler.post {
                    if (running && generation == cloudCaptureGeneration) {
                        HandsFreeStatus.micLevel = level
                        HandsFreeStatus.userSpeaking = speakingNow
                    }
                }
            }
            if (!running || generation != cloudCaptureGeneration) return@launch
            HandsFreeStatus.listening = false
            HandsFreeStatus.userSpeaking = false
            HandsFreeStatus.micLevel = 0f
            cloudCaptureJob = null
            when (result) {
                JarvisCloudSpeechCapture.Result.Silence -> {
                    HandsFreeStatus.status = "Nem hallottam beszédet. Figyelek tovább."
                    again(350)
                }
                JarvisCloudSpeechCapture.Result.Unavailable -> {
                    recoverCloudRecognition("A felhős mikrofonrögzítés nem indult. Ellenőrizd az engedélyt.")
                }
                is JarvisCloudSpeechCapture.Result.Audio -> {
                    lastVoiceEndedAtMs = SystemClock.elapsedRealtime()
                    HandsFreeStatus.status = "Beszédfelismerés az OpenRouteren…"
                    val transcript = JarvisAiGateway.transcribe(
                        applicationContext, result.wav, language)
                    // Consent revocation or a stopped service invalidates old audio.
                    if (!running || generation != cloudCaptureGeneration) return@launch
                    if (!JarvisAiGateway.canTranscribe(applicationContext)) {
                        HandsFreeStatus.status = "A hangfeltöltés engedélyét visszavontad."
                        again(500)
                    } else if (transcript.isNullOrBlank()) {
                        recoverCloudRecognition(JarvisAiGateway.lastTranscriptionError
                            .ifBlank { "Nem értettem. Ismételd meg a kérést." })
                    } else {
                        consecutiveCloudFailures = 0
                        HandsFreeStatus.heard = transcript
                        recognizedUtteranceLanguage = language
                        process(transcript)
                    }
                }
            }
        }
    }

    private fun listen() {
        if (!running || speaking || recognizerListening || foregroundReplyPending ||
            cloudCaptureJob?.isActive == true) return
        if (tapActivated && !useAndroidRecognizerFallback && JarvisAiGateway.canTranscribe(this)) {
            startCloudCapture()
            return
        }
        try {
            if (recognizer == null) createRecognizer()
            recognizedUtteranceLanguage = null
            speechObservedThisTurn = false
            // Preserve the latest recognized transcript between capture cycles.
            // Otherwise a 250 ms restart hides whether "igen" was heard at all.
            HandsFreeStatus.listening = false
            HandsFreeStatus.status = when {
                proposed != null -> "Megerősítést várok: igen vagy nem"
                awaitingCommand -> "Figyelek a parancsodra"
                else -> "Mondd: Jarvis vagy Hey Jarvis"
            }
            // Set BEFORE startListening: some Android speech providers deliver
            // callbacks synchronously, and starting twice causes BUSY loops.
            recognizerListening = true
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                JarvisSpeechRecognition.configure(this, JarvisLanguageSettings.load(applicationContext),
                    autoLanguageSwitchEnabled)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOnDeviceRecognizer)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Let normal speech pauses pass. These are hints, not guarantees.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            })
            recognitionDeadline(VoiceSessionPolicy.RECOGNITION_TIMEOUT_MS)
        } catch (_: Exception) {
            recognizerListening = false
            errors++
            HandsFreeStatus.status = "A beszédfelismerő nem indult. Újrapróbálom."
            runCatching { createRecognizer() }
            again(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_CLIENT, errors))
        }
    }
    private fun onBargeIn() {
        // A real mic onset interrupts ONLY speech output, never executes a
        // partial transcript. The existing recognizer handles the new turn.
        if (!running || !speaking || !HandsFreeStatus.speaking) return
        cancelForegroundStream()
        foregroundReplyPending = false
        handler.removeCallbacks(foregroundReplyTimeout)
        spokenReplySerial++
        cloudVoice?.stop()
        bargeInMonitor?.stop()
        speaking = false
        HandsFreeStatus.speaking = false
        HandsFreeStatus.outputLevel = 0f
        HandsFreeStatus.status = "Megszakítottad a felolvasást. Mondd újra a kérést, hogy biztosan rögzítsem."
        if (tapActivated) awaitingCommand = true
        again(110L)
    }

    private fun finishSpokenReply() {
        bargeInMonitor?.stop()
        speaking = false
        HandsFreeStatus.speaking = false
        HandsFreeStatus.outputLevel = 0f
        if (running) {
            if (tapActivated) awaitingCommand = true
            again(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS)
        }
    }

    private fun speak(text: String) {
        // Never record or upload Jarvis's own AI audio as a new command.
        cloudCaptureGeneration++
        cloudCaptureJob?.cancel()
        cloudCaptureJob = null
        val replySerial = ++spokenReplySerial
        bargeInMonitor?.stop()
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
        handler.removeCallbacks(recognitionWatchdog)
        handler.removeCallbacks(foregroundReplyTimeout)
        foregroundReplyPending = false
        speaking = true
        HandsFreeStatus.speaking = false
        HandsFreeStatus.micLevel = 0f
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.listening = false
        if (recognizerListening) {
            recognizerListening = false
            recognizer?.cancel()
        }
        val started = cloudVoice?.speak(text, wantedLanguage,
            onStart = {
                if (running && spokenReplySerial == replySerial) {
                    // One monitor for the entire answer, not a restart between
                    // pre-fetched MP3 chunks: keep capture stable through pauses.
                    if (!HandsFreeStatus.speaking) bargeInMonitor?.start()
                    HandsFreeStatus.speaking = true
                    HandsFreeStatus.audioError = ""
                    HandsFreeStatus.status = "Jarvis AI-hang lejátszása…"
                    // Measure first ACTUAL audible playback, not when text arrives.
                    // No transcript, API token, identity or personal data is logged.
                    if (lastVoiceEndedAtMs > 0L) {
                        val elapsedMs = SystemClock.elapsedRealtime() - lastVoiceEndedAtMs
                        if (BuildConfig.DEBUG && elapsedMs in 0L..120_000L) {
                            Log.d("JarvisVoiceLatency",
                                "end_of_speech_to_first_audio_ms=$elapsedMs")
                        }
                        lastVoiceEndedAtMs = 0L
                    }
                }
            }
        ) { successful ->
            if (running && spokenReplySerial == replySerial) {
                if (!successful) {
                    val failure = cloudVoice?.lastFailure.orEmpty().ifBlank {
                        JarvisReadAloudCommand.response(wantedLanguage, "failed")
                    }
                    HandsFreeStatus.audioError = failure + " " +
                        JarvisVoiceReadiness.mediaVolumeMessage(applicationContext)
                    HandsFreeStatus.status = "AI-hang hiba. Nézd meg a hangellenőrzést."
                }
                finishSpokenReply()
            }
        } == true
        if (!started) {
            HandsFreeStatus.audioError = cloudVoice?.lastFailure.orEmpty().ifBlank {
                JarvisVoiceReadiness.reason(applicationContext)
                    ?: "Nem tudtam elindítani az AI-hang lejátszását."
            }
            HandsFreeStatus.status = "AI-hang nem indult; ellenőrizd a Beállítások → AI-agy oldalt."
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
        if (VoiceIntentParser.isPlaybackStop(heard)) {
            // The user requested silence. Stop streamed/cloud service speech AND
            // the separate Activity read-aloud player; do not speak an acknowledgement.
            JarvisForegroundCommands.interruptSpeech?.invoke()
            JarvisVoiceStreamBridge.stopLocalNarration?.invoke()
            foregroundReplyPending = false
            handler.removeCallbacks(foregroundReplyTimeout)
            awaitingCommand = true
            HandsFreeStatus.status = "Felolvasás leállítva. Figyelek."
            again(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS)
            return
        }
        if (VoiceIntentParser.isStop(heard) || VoiceIntentParser.isStop(
                VoiceIntentParser.afterWakeWord(heard).orEmpty())) {
            shutdown("A hangfigyelés leállt.")
            return
        }
        // All foreground speech, including follow-ups on typed calls, uses the
        // SAME Activity session. Only microphone transport belongs to this service.
        val wake = VoiceIntentParser.afterWakeWord(heard)
        if ((tapActivated || awaitingCommand || wake != null) && privateVoiceAllowed()) {
            val sharedCommand = (wake ?: heard).trim()
            if (sharedCommand.isNotBlank()) {
                aiJob?.cancel()
                activeVoiceCommand = null // Activity writes the single shared history entry.
                cancelForegroundStream()
                val turn = foregroundTurnId
                foregroundReplyPending = true
                handler.removeCallbacks(restart)
                JarvisVoiceStreamBridge.receiver = { requested, phrase ->
                    if (!running || turn != foregroundTurnId ||
                        !foregroundReplyPending || requested != sharedCommand) {
                        false
                    } else {
                        if (foregroundStream == null) {
                            val replySerial = ++spokenReplySerial
                            val language = recognizedUtteranceLanguage ?:
                                JarvisLanguageSettings.load(applicationContext)
                            foregroundStream = cloudVoice?.startStream(language,
                                onStart = {
                                    if (running && foregroundTurnId == turn &&
                                        spokenReplySerial == replySerial) {
                                        if (!HandsFreeStatus.speaking) bargeInMonitor?.start()
                                        HandsFreeStatus.speaking = true
                                        HandsFreeStatus.status = "Jarvis AI-hang lejátszása…"
                                        HandsFreeStatus.audioError = ""
                                        if (lastVoiceEndedAtMs > 0L) {
                                            val elapsed = SystemClock.elapsedRealtime() - lastVoiceEndedAtMs
                                            if (BuildConfig.DEBUG && elapsed in 0L..120_000L)
                                                Log.d("JarvisVoiceLatency",
                                                    "end_of_speech_to_first_audio_ms=$elapsed")
                                            lastVoiceEndedAtMs = 0L
                                        }
                                    }
                                },
                                completed = { successful ->
                                    if (running && foregroundTurnId == turn) {
                                        foregroundStream = null
                                        if (!successful) {
                                            // The first cloud MP3 can fail while
                                            // the text SSE is still in progress.
                                            // End this turn rather than leaving
                                            // foregroundReplyPending true forever.
                                            HandsFreeStatus.audioError =
                                                cloudVoice?.lastFailure.orEmpty()
                                                    .ifBlank { "A felhős hanggenerálás megszakadt." }
                                            foregroundReplyPending = false
                                            handler.removeCallbacks(foregroundReplyTimeout)
                                            JarvisVoiceStreamBridge.receiver = null
                                            JarvisVoiceStreamBridge.interruptActive()
                                            HandsFreeStatus.status = "AI-hang hiba. Újra figyelek rád."
                                        }
                                        finishSpokenReply()
                                    }
                                })
                            if (foregroundStream != null) {
                                speaking = true
                                HandsFreeStatus.listening = false
                                handler.removeCallbacks(foregroundReplyTimeout)
                            }
                        }
                        foregroundStream?.offer(phrase) == true
                    }
                }
                val delivered = JarvisForegroundCommands.submit(sharedCommand) { answer ->
                    if (running && foregroundReplyPending && foregroundTurnId == turn) {
                        foregroundReplyPending = false
                        JarvisVoiceStreamBridge.receiver = null
                        handler.removeCallbacks(foregroundReplyTimeout)
                        val queued = foregroundStream
                        if (answer.isBlank()) {
                            // Successful native action: no spoken acknowledgement.
                            // Silence is not a text-to-speech failure.
                            queued?.cancel()
                            finishSpokenReply()
                        } else if (queued != null) {
                            HandsFreeStatus.answer = answer
                            queued.finish()
                        } else {
                            speak(answer)
                        }
                    }
                }
                if (delivered) {
                    aiConversation.clear() // Foreground chat owns its own isolated context.
                    awaitingCommand = true
                    // An asynchronous Gmail/API response must not create
                    // repeated microphone cycles while still pending.
                    if (foregroundReplyPending)
                        handler.postDelayed(foregroundReplyTimeout,
                            JarvisResponseDeadline.foregroundVoiceMs(sharedCommand))
                    return
                }
                foregroundReplyPending = false
                cancelForegroundStream()
                handler.removeCallbacks(foregroundReplyTimeout)
            }
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
        // Gmail needs Activity-owned Google OAuth. If the shared foreground
        // command bridge is unavailable, never pretend to read/send mail and
        // never pass this personal-data command to the reasoning model.
        if (JarvisGmailCommands.parse(command) != null) {
            speak("Gmail-művelethez nyisd meg Jarvist a feloldott telefonon, és ismételd meg a parancsot.")
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
                VoiceNavigation.resolve(this, destination) == null &&
                    !VoiceNavigation.maySearchNamedPlace(destination) ->
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
        if (SpokenAlarmTime.isSetIntent(command)) {
            speak(JarvisAlarmText.invalid(this))
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
            if (VoiceNavigation.resolve(this, nav) == null &&
                !VoiceNavigation.maySearchNamedPlace(nav)) {
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
        if (!JarvisAiGateway.configured(applicationContext)) {
            LocalConversation.reply(command)?.let { speak(it); return }
            JarvisCapabilities.replyIfAsked(command)?.let { speak(it); return }
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
                            recognizedUtteranceLanguage?.tag, history = aiConversation.toList())
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
                                else -> {
                                    if (outcome.conversationReply) {
                                        val next = JarvisConversationContext.bounded(
                                            aiConversation + (command to outcome.message))
                                        aiConversation.clear()
                                        aiConversation.addAll(next)
                                    }
                                    speak(outcome.message)
                                }
                            }
                        }
                    }
                }
            }
            else -> speak("A rendszeralkalmazás megnyitásához nyisd meg Jarvist. Android háttérből nem mindig engedi.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (running && recognizerListening && !speaking) {
            HandsFreeStatus.listening = true
            HandsFreeStatus.status = "Hallgatlak, mondd a kérésed…"
        }
    }
    override fun onBeginningOfSpeech() {
        if (!running || !recognizerListening || speaking) return
        speechObservedThisTurn = true
        HandsFreeStatus.listening = true
        HandsFreeStatus.userSpeaking = true
        recognitionDeadline(VoiceSessionPolicy.RECOGNITION_TIMEOUT_MS)
    }
    override fun onRmsChanged(rmsdB: Float) {
        if (!running || speaking) return
        val normalized = ((rmsdB + 2f) / 13f).coerceIn(0f, 1f)
        HandsFreeStatus.micLevel = HandsFreeStatus.micLevel * 0.65f + normalized * 0.35f
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.listening = false
        HandsFreeStatus.micLevel = 0f
        if (running && recognizerListening) {
            if (speechObservedThisTurn) lastVoiceEndedAtMs = SystemClock.elapsedRealtime()
            HandsFreeStatus.status = "Feldolgozom a hallottakat…"
            recognitionDeadline(VoiceSessionPolicy.RESULT_TIMEOUT_MS)
        }
    }
    override fun onPartialResults(partialResults: Bundle?) {
        if (!recognizerListening || speaking) return
        // The recognizer, not a raw PCM microphone stream, applies its own
        // device-dependent acoustic/noise processing.
        val partial = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.take(120).orEmpty()
        if (partial.isNotBlank()) {
            speechObservedThisTurn = true
            // Show what Android has decoded, but NEVER execute partial words:
            // final recognition and foreground confirmation remain required.
            HandsFreeStatus.heard = partial
            HandsFreeStatus.status = "Beszéd felismerése…"
        }
    }
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
        if (!recognizerListening) return // Stale result after cancellation.
        recognizerListening = false
        handler.removeCallbacks(recognitionWatchdog)
        HandsFreeStatus.listening = false
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
        if (running && !speaking) {
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: arrayListOf()
            val transcript = VoiceTranscriptSelection.select(candidates,
                results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
                userInitiated = tapActivated)
            if (transcript == null) {
                HandsFreeStatus.status = "Zaj vagy bizonytalan beszéd. Mondd újra a kérést."
                again(VoiceSessionPolicy.retryDelay(SpeechRecognizer.ERROR_NO_MATCH))
            } else process(transcript)
        }
    }
    override fun onError(error: Int) {
        if (!recognizerListening) return // Ignore late callbacks from cancelled turns.
        recognizerListening = false
        handler.removeCallbacks(recognitionWatchdog)
        HandsFreeStatus.listening = false
        HandsFreeStatus.userSpeaking = false
        HandsFreeStatus.micLevel = 0f
        if (!running || speaking || foregroundReplyPending) return
        if (VoiceRecognitionPolicy.needsSystemFallback(usingOnDeviceRecognizer, error)) {
            usingOnDeviceRecognizer = false
            errors = 0
            try {
                createRecognizer()
                HandsFreeStatus.status = "A rendszer beszédfelismerőjére váltok."
                again(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS)
            } catch (_: Exception) {
                HandsFreeStatus.status = "Nem sikerült beszédfelismerőt választani."
                again(VoiceSessionPolicy.retryDelay(error, 3))
            }
            return
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            shutdown("A mikrofonengedély megszűnt.")
            return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
            if (autoLanguageSwitchEnabled) {
                // One unavailable language pack must not disable manual voice.
                autoLanguageSwitchEnabled = false
                errors = 0
                HandsFreeStatus.status = "Automatikus nyelvváltás nem elérhető; a kiválasztott nyelven figyelek."
                again(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS)
            } else {
                shutdown("A kiválasztott nyelv beszédfelismerése hiányzik. Ellenőrizd az Android nyelvi modelljeit.")
            }
            return
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            if (autoLanguageSwitchEnabled && speechObservedThisTurn) {
                // Some OEM engines advertise language switching but then fail
                // to decode spoken Hungarian. Retry with the manually selected
                // language alone; keep the Home picker fully functional.
                autoLanguageSwitchEnabled = false
                HandsFreeStatus.status = "Többnyelvű felismerés sikertelen; a kiválasztott nyelvre váltok."
                again(VoiceSessionPolicy.AFTER_REPLY_DELAY_MS)
                return
            }
            // Silence is not a failure of a user-started conversation. Keep
            // listening until explicit STOP rather than ending after 5 errors.
            errors = 0
            HandsFreeStatus.status = "Nem hallottam érthető beszédet. Újra figyelek."
            if (tapActivated) awaitingCommand = true
            again(VoiceSessionPolicy.retryDelay(error))
            return
        }
        errors++
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
            // A provider can leave its speech service in a BUSY/dead state.
            // Discard it before restarting, without losing the user's session.
            runCatching { createRecognizer() }
        }
        HandsFreeStatus.status = when (error) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "A telefon beszédfelismerője hálózati hibát jelzett. Újrapróbálom."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "A mikrofon foglalt volt. Újraindítom a felismerést."
            else -> "A beszédfelismerő hibázott ($error). Újrapróbálom."
        }
        // Temporary provider errors must never look like an intentional STOP.
        again(VoiceSessionPolicy.retryDelay(error, errors))
    }
    private fun shutdown(status: String) {
        spokenReplySerial++
        lastVoiceEndedAtMs = 0L
        bargeInMonitor?.close()
        bargeInMonitor = null
        running = false
        JarvisForegroundCommands.interruptSpeech = null
        HandsFreeStatus.running = false
        HandsFreeStatus.heard = ""
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
        handler.removeCallbacks(recognitionWatchdog)
        handler.removeCallbacks(foregroundReplyTimeout)
        recognizerListening = false
        HandsFreeStatus.listening = false
        ++cloudCaptureGeneration
        cloudCaptureJob?.cancel()
        cloudCaptureJob = null
        captureScope.cancel()
        ++recognizerGeneration
        foregroundReplyPending = false
        cancelForegroundStream()
        aiJob?.cancel()
        aiJob = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        cloudVoice?.release()
        cloudVoice = null
        activeVoiceCommand = null
        aiConversation.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (running) shutdown("A hangfigyelés leállt.")
        super.onDestroy()
    }
}

