package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional microphone monitor while Jarvis is speaking. The normal Android
 * SpeechRecognizer is deliberately NOT started over the narrator: it would
 * transcribe Jarvis's own voice into commands. This records no files and sends
 * no microphone data over the network.
 *
 * Only run with an active acoustic echo canceller or headphone output. Some
 * devices cannot safely support acoustic talk-over; on those devices normal
 * stop/listen operation remains available instead of self-triggering commands.
 */
internal class JarvisBargeInMonitor(
    context: Context,
    private val onVoiceDetected: () -> Unit
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var active = false
    @Volatile private var generation = 0
    @Volatile private var recorder: AudioRecord? = null
    private var work: Job? = null

    fun start(): Boolean {
        stop()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return false
        val rate = 16_000
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBytes = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return false
        var record: AudioRecord? = null
        var echo: AcousticEchoCanceler? = null
        var noise: NoiseSuppressor? = null
        try {
            val created = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBytes, 4_096))
                .build()
            record = created
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                return false
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echo = runCatching { AcousticEchoCanceler.create(created.audioSessionId) }.getOrNull()
                runCatching { echo?.enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noise = runCatching { NoiseSuppressor.create(created.audioSessionId) }.getOrNull()
                runCatching { noise?.enabled = true }
            }
            // Speakerphone without active AEC is unsafe: it would listen to
            // its own narration and spuriously interrupt the conversation.
            if (echo?.enabled != true && !headphonesConnected()) {
                noise?.release()
                echo?.release()
                record.release()
                return false
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                noise?.release()
                echo?.release()
                record.release()
                return false
            }
        } catch (_: Exception) {
            runCatching { noise?.release() }
            runCatching { echo?.release() }
            runCatching { record?.release() }
            return false
        }
        val owned = record ?: return false
        val ownedEcho = echo
        val ownedNoise = noise
        val token = ++generation
        recorder = owned
        active = true
        // ATOMIC guarantees the finally/release block runs even if STOP
        // happens before the IO dispatcher schedules its first instruction.
        work = scope.launch(start = CoroutineStart.ATOMIC) {
            var voiceDetected = false
            try {
                val samples = ShortArray(320) // 20 ms at 16 kHz
                val gate = JarvisBargeInGate()
                while (isActive && active && generation == token) {
                    val count = owned.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) break
                    if (gate.observe(samples, count)) {
                        voiceDetected = true
                        break
                    }
                }
            } catch (_: Exception) {
                // Audio routing and OEM changes may invalidate capture mid-speech.
            } finally {
                runCatching { owned.stop() }
                runCatching { ownedNoise?.release() }
                runCatching { ownedEcho?.release() }
                runCatching { owned.release() }
                if (recorder === owned) recorder = null
            }
            // The microphone is released BEFORE the platform recognizer starts.
            if (voiceDetected && active && generation == token) {
                withContext(Dispatchers.Main.immediate) {
                    if (active && generation == token) onVoiceDetected()
                }
            }
        }
        return true
    }

    private fun headphonesConnected(): Boolean {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    fun stop() {
        active = false
        generation++
        // Stop unblocks the AudioRecord.read() call. Its IO worker owns release.
        runCatching { recorder?.stop() }
        work?.cancel()
        work = null
    }

    fun close() {
        stop()
        scope.cancel()
    }
}
