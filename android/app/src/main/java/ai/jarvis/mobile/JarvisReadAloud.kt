package ai.jarvis.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Foreground reader: cloud-generated AI speech, NEVER Android TTS.
 * Requires separate opt-in because spoken text may contain private information.
 */
internal class JarvisReadAloud(context: Context) {
    private val engine = JarvisCloudVoicePlayer(context)
    private var closed = false
    private var language = JarvisLanguageSettings.load(context)
    var isSpeaking by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    fun read(text: String, selected: JarvisLanguage): Boolean {
        if (closed || text.isBlank()) return false
        stopInternal(false)
        language = selected
        val started = engine.speak(text, selected) { success ->
            if (!closed) {
                isSpeaking = false
                status = JarvisReadAloudCommand.response(language,
                    if (success) "finished" else "failed")
            }
        }
        isSpeaking = started
        status = JarvisReadAloudCommand.response(language,
            if (started) "started" else "missingTts")
        return started
    }

    private fun stopInternal(report: Boolean) {
        engine.stop()
        isSpeaking = false
        if (report) status = JarvisReadAloudCommand.response(language, "stopped")
    }

    fun stop() = stopInternal(true)

    fun release() {
        if (closed) return
        closed = true
        stopInternal(false)
        engine.release()
    }
}
