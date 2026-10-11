package ai.jarvis.mobile

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import android.speech.tts.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import java.util.Locale

internal object HandsFreeStatus {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Kikapcsolva")
    var heard by mutableStateOf("")
    var answer by mutableStateOf("")
}
internal object HandsFreeNavigation {
    var destination by mutableStateOf<String?>(null)
}

/** User-started, time-limited recognition session. Not a true always-on hotword engine. */
class HandsFreeService : Service(), RecognitionListener {
    companion object {
        const val START = "ai.jarvis.mobile.START_VOICE"
        const val STOP = "ai.jarvis.mobile.STOP_VOICE"
        private const val CHANNEL = "jarvis_voice"
        private const val ID = 4102
        private const val MAX_MS = 15 * 60 * 1000L
    }
    private val handler = Handler(Looper.getMainLooper())
    private val restart = Runnable { listen() }
    private val timeout = Runnable { shutdown("A 15 perces hangos munkamenet lejárt.") }
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var running = false
    private var speaking = false
    private var errors = 0
    private var startedAt = 0L
    private var awaitingCommand = false
    private var replyWindowUntil = 0L
    private var lastMessageKey: String? = null
    private var proposed: Pair<String, String>? = null
    private var proposedCall: VoiceCalling.Target? = null
    private var proposedCallUntil = 0L
    private var confirmUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            shutdown("A hangfigyelés leállt.")
            return START_NOT_STICKY
        }
        if (intent?.action != START || running) return START_NOT_STICKY
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
                .setContentText("Hey Jarvis · 15 perc · koppints a leállításhoz")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leállítás", stopIntent)
                .build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, notice)
        } catch (_: Exception) {
            HandsFreeStatus.status = "Az Android nem engedte a mikrofon-szolgáltatást."
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        HandsFreeStatus.running = true
        startedAt = SystemClock.elapsedRealtime()
        handler.postDelayed(timeout, MAX_MS)
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.forLanguageTag("hu-HU")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            speaking = false
                            if (running) again(450)
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { speaking = false; if (running) again(450) }
                    }
                })
            }
        }
        try {
            recognizer = if (Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(this)
            again(450)
        } catch (_: Exception) { shutdown("Nem indítható a beszédfelismerő.") }
        return START_NOT_STICKY
    }

    private fun again(delay: Long) {
        handler.removeCallbacks(restart)
        if (running && !speaking) handler.postDelayed(restart, delay)
    }
    private fun listen() {
        if (!running || speaking) return
        if (SystemClock.elapsedRealtime() - startedAt >= MAX_MS) {
            shutdown("A hangfigyelés időkorlátja lejárt.")
            return
        }
        try {
            HandsFreeStatus.status = when {
                proposed != null -> "Megerősítést várok: igen vagy nem"
                awaitingCommand -> "Figyelek a parancsodra"
                else -> "Mondd: Hey Jarvis"
            }
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) {
            errors++
            if (errors >= 5) shutdown("A beszédfelismerés hibázott.")
            else again(2000)
        }
    }
    private fun speak(text: String) {
        HandsFreeStatus.answer = text
        HandsFreeStatus.status = "Jarvis válaszol"
        handler.removeCallbacks(restart)
        speaking = true
        recognizer?.cancel()
        val engine = tts
        if (ttsReady && engine != null &&
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "jarvis-" + SystemClock.uptimeMillis()) == TextToSpeech.SUCCESS) return
        handler.postDelayed({ speaking = false; if (running) again(400) }, 1500)
    }
    private fun clearProposal() { proposed = null; confirmUntil = 0L }
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
        if (proposedCall != null) {
            if (now > proposedCallUntil) {
                proposedCall = null
                speak("Lejárt a hívás megerősítési ideje. Nem indítottam hívást.")
            } else if (VoiceIntentParser.isNo(heard)) {
                proposedCall = null
                speak("Rendben, a hívást nem indítottam el.")
            } else if (VoiceIntentParser.isYes(heard)) {
                val target = proposedCall
                proposedCall = null
                speak(if (target != null && VoiceCalling.place(this, target))
                    "Átadtam a híváskérést az Android telefonrendszernek."
                    else "A hívást nem tudtam elindítani. Ellenőrizd a Telefon engedélyét.")
            } else speak("Csak az igen vagy nem választ várom a hívás előtt.")
            return
        }
        if (proposed != null) {
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
        if (VoiceIntentParser.isReadMessage(command)) {
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
        val calledName = VoiceCalling.extractRequest(command)
        if (calledName != null) {
            if (!VoiceCalling.canCall(this)) {
                speak("Hívás indításához engedélyezd a Telefon hozzáférést az Engedélyközpontban.")
                return
            }
            when (val match = VoiceCalling.lookup(this, calledName)) {
                is VoiceCalling.Lookup.Found -> {
                    proposedCall = match.target
                    proposedCallUntil = now + 30_000L
                    speak("Fel szeretnéd hívni " + match.target.label +
                        " névjegyet? A szám: " + match.target.number + ". Mondj igent vagy nemet.")
                }
                VoiceCalling.Lookup.NoContactsAccess -> speak("A névjegy kereséséhez engedélyezd a Névjegyek hozzáférést.")
                VoiceCalling.Lookup.Ambiguous -> speak("Több azonos névjegyet találtam. Pontosítsd a nevet.")
                VoiceCalling.Lookup.NotFound -> speak("Nem találtam ilyen nevű névjegyet.")
            }
            return
        }
        val action = BasicCommandRouter.classify(command)
        when (action) {
            BasicAction.OPEN_GLUCOSE, BasicAction.OPEN_INVOICES,
            BasicAction.OPEN_HOME, BasicAction.OPEN_NOTES, BasicAction.OPEN_PERMISSIONS -> {
                HandsFreeNavigation.destination = when (action) {
                    BasicAction.OPEN_GLUCOSE -> "Vércukor"
                    BasicAction.OPEN_INVOICES -> "Számlák"
                    BasicAction.OPEN_HOME -> "Főoldal"
                    BasicAction.OPEN_NOTES -> "Továbbiak"
                    else -> "Engedélyek"
                }
                speak("Előkészítettem a kért oldalt a Jarvisban.")
            }
            BasicAction.OPEN_MESSAGES -> speak("Mondd: olvasd fel az utolsó WhatsApp vagy Messenger üzenetemet.")
            BasicAction.UNKNOWN -> speak("Ezt a parancsot még nem támogatom.")
            else -> speak("A rendszeralkalmazás megnyitásához nyisd meg Jarvist. Android háttérből nem mindig engedi.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onResults(results: Bundle?) {
        if (running && !speaking)
            process(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
    }
    override fun onError(error: Int) {
        if (!running || speaking) return
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            shutdown("A mikrofonengedély megszűnt.")
            return
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            errors = 0 else errors++
        if (errors >= 5) shutdown("A beszédfelismerés nem működik stabilan.")
        else again(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2200 else 1100)
    }
    private fun shutdown(status: String) {
        running = false
        HandsFreeStatus.running = false
        HandsFreeStatus.status = status
        clearProposal()
        proposedCall = null
        proposedCallUntil = 0L
        awaitingCommand = false
        lastMessageKey = null
        handler.removeCallbacks(restart)
        handler.removeCallbacks(timeout)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (running) shutdown("A hangfigyelés leállt.")
        super.onDestroy()
    }
}
