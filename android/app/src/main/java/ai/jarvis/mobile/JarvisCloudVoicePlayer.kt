package ai.jarvis.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * One foreground cloud-AI narrator shared by chat and hands-free replies.
 * Audio is generated remotely, never with the Android TTS engine.
 * Only one temporary MP3 exists at a time and is removed after playback.
 * Playback is never considered a verified network/device action.
 */
internal class JarvisCloudVoicePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var serial = 0
    private var work: Job? = null
    private var player: MediaPlayer? = null
    private var activeFile: File? = null
    private var closed = false
    var lastFailure: String = ""
        private set

    /** Returns false immediately when speech lacks explicit separate cloud consent. */
    fun speak(text: String, language: JarvisLanguage, onStart: () -> Unit = {}, completed: (Boolean) -> Unit): Boolean {
        lastFailure = JarvisVoiceReadiness.reason(appContext).orEmpty()
        if (closed) {
            lastFailure = "A Jarvis AI-hanglejátszó bezárult. Indítsd újra a beszélgetést."
            return false
        }
        if (text.isBlank() || text.length > 30_000) {
            lastFailure = "A felolvasandó szöveg hiányzik vagy túl hosszú."
            return false
        }
        if (lastFailure.isNotBlank()) return false
        val chunks = JarvisSpeechChunks.splitForConversation(text)
        if (chunks.isEmpty()) return false
        stop()
        lastFailure = ""
        val request = serial
        work = scope.launch {
            // Prefetch at most one following MP3 while the current chunk is
            // playing. This reduces dead air without increasing memory use.
            var upcoming = async(Dispatchers.IO) {
                JarvisAiGateway.speechBytes(appContext, chunks.first(), language)
            }
            for (index in chunks.indices) {
                val audio = upcoming.await()
                if (request != serial) return@launch
                if (audio == null) {
                    lastFailure = JarvisAiGateway.lastSpeechError
                        .ifBlank { "A felhős AI-hang nem adott lejátszható választ." }
                    completed(false)
                    return@launch
                }
                val file = try {
                    withContext(Dispatchers.IO) {
                        File.createTempFile("jarvis-voice-", ".mp3", appContext.cacheDir)
                            .also { it.writeBytes(audio) }
                    }
                } catch (_: Exception) {
                    if (request == serial) completed(false)
                    return@launch
                }
                if (request != serial) {
                    file.delete()
                    return@launch
                }
                upcoming = if (index + 1 < chunks.size) async(Dispatchers.IO) {
                    JarvisAiGateway.speechBytes(appContext, chunks[index + 1], language)
                } else upcoming
                activeFile = file
                val played = try {
                    playFile(file, request, onStart)
                } catch (_: Exception) {
                    false
                } finally {
                    // An obsolete coroutine must never release a newer turn's
                    // player/file after a barge-in or fast user interruption.
                    if (request == serial) {
                        player?.release()
                        player = null
                    }
                    file.delete()
                    if (activeFile === file) activeFile = null
                }
                if (request != serial) return@launch
                if (!played) {
                    lastFailure = "Az Android nem tudta lejátszani a kapott MP3-hangot."
                    completed(false)
                    return@launch
                }
            }
            if (request == serial) completed(true)
        }
        return true
    }

    private suspend fun playFile(file: File, request: Int, onStart: () -> Unit): Boolean =
        suspendCancellableCoroutine { continuation ->
            val media = MediaPlayer()
            player = media
            try {
                // Explicitly route AI voice through the Android MEDIA stream,
                // not call/ringtone volume. Bluetooth output stays user-selected.
                media.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener {
                    if (request == serial && continuation.isActive) {
                        try {
                            it.start()
                            onStart()
                        }
                        catch (_: Exception) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                }
                media.setOnCompletionListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                media.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                    true
                }
                media.prepareAsync()
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
        }

    fun stop() {
        serial++
        work?.cancel()
        work = null
        player?.run {
            try { stop() } catch (_: Exception) { /* not yet prepared */ }
            release()
        }
        player = null
        activeFile?.delete()
        activeFile = null
    }

    fun release() {
        if (closed) return
        closed = true
        stop()
        scope.cancel()
    }
}
