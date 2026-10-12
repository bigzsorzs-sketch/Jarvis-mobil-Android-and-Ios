package ai.jarvis.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/**
 * Foreground chat narrator, independent of the voice-recognition service.
 *
 * Explicit taps/commands only; never reads private local messages automatically.
 * Android's chosen installed TTS voice synthesizes the text. It does not send a
 * request to Jarvis/OpenRouter and does not need microphone permission.
 */
internal class JarvisReadAloud(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var ready = false
    private var closed = false
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private var utteranceId: String? = null
    private var wantedLanguage: JarvisLanguage = JarvisLanguageSettings.load(appContext)
    var isSpeaking by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    init {
        engine = TextToSpeech(appContext) { result ->
            main.post {
                if (closed) return@post
                initialized = true
                ready = result == TextToSpeech.SUCCESS && engine != null
                if (!ready) {
                    chunks = emptyList()
                    isSpeaking = false
                    status = JarvisReadAloudCommand.response(wantedLanguage, "missingTts")
                } else {
                    val voiceEngine = engine
                    voiceEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) {
                            main.post {
                                if (closed || utteranceId != id) return@post
                                chunkIndex++
                                nextChunk()
                            }
                        }
                        @Deprecated("Deprecated Android TTS callback")
                        override fun onError(id: String?) {
                            main.post {
                                if (closed || utteranceId != id) return@post
                                finishWith("failed")
                            }
                        }
                    })
                    if (chunks.isNotEmpty()) nextChunk()
                }
            }
        }
    }

    fun read(text: String, language: JarvisLanguage): Boolean {
        if (closed || text.isBlank()) return false
        val parts = JarvisSpeechChunks.split(text)
        if (parts.isEmpty()) return false
        stopInternal(false)
        wantedLanguage = language
        chunks = parts
        chunkIndex = 0
        status = ""
        isSpeaking = true // includes pending Android TTS initialization
        if (initialized) {
            if (!ready) {
                finishWith("missingTts")
                return false
            }
            nextChunk()
        }
        return true
    }

    private fun nextChunk() {
        if (closed || !ready || chunks.isEmpty()) return
        if (chunkIndex >= chunks.size) {
            finishWith("finished")
            return
        }
        val tts = engine ?: run { finishWith("missingTts"); return }
        val supported = tts.setLanguage(Locale.forLanguageTag(wantedLanguage.tag))
        if (supported < TextToSpeech.LANG_AVAILABLE) {
            finishWith("missingTts")
            return
        }
        val id = "jarvis-read-" + UUID.randomUUID().toString()
        utteranceId = id
        if (tts.speak(chunks[chunkIndex], TextToSpeech.QUEUE_FLUSH, null, id) !=
            TextToSpeech.SUCCESS) finishWith("failed")
    }

    private fun finishWith(key: String) {
        chunks = emptyList()
        chunkIndex = 0
        utteranceId = null
        isSpeaking = false
        status = JarvisReadAloudCommand.response(wantedLanguage, key)
    }

    private fun stopInternal(report: Boolean) {
        utteranceId = null // invalidates stale TTS callbacks
        chunks = emptyList()
        chunkIndex = 0
        engine?.stop()
        isSpeaking = false
        if (report) status = JarvisReadAloudCommand.response(wantedLanguage, "stopped")
    }
    fun stop() = stopInternal(true)

    fun release() {
        if (closed) return
        stopInternal(false)
        closed = true
        engine?.shutdown()
        engine = null
    }
}
