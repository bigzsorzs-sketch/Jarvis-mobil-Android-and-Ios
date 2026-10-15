package ai.jarvis.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Opt-in microphone recording for cloud speech-to-text. No audio is saved to disk.
 * Captures ONE utterance, with a short pre-roll, then releases the microphone.
 * Must be called only after a visible user-started microphone foreground service.
 */
internal object JarvisCloudSpeechCapture {
    private const val SAMPLE_RATE = 16_000
    private const val FRAME = 1_600             // 100ms
    private const val MAX_FRAMES = 140          // 14s hard cap
    private const val QUIET_FRAMES = 10         // 1s after speech
    private const val NO_SPEECH_FRAMES = 100    // 10s silence
    private const val MIN_VOICE_FRAMES = 3      // Reject clicks and incidental sounds

    internal fun pcm16Wav(pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= SAMPLE_RATE * 2 * 15)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size + 36)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(1.toShort()) // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    internal fun rms(samples: ShortArray, length: Int): Double {
        if (length <= 0) return 0.0
        var energy = 0.0
        for (i in 0 until length) {
            val s = samples[i].toDouble()
            energy += s * s
        }
        return sqrt(energy / length)
    }

    sealed class Result {
        data class Audio(val wav: ByteArray) : Result()
        data object Silence : Result()
        data object Unavailable : Result()
    }

    @SuppressLint("MissingPermission")
    suspend fun record(context: Context, onLevel: (Float, Boolean) -> Unit = { _, _ -> }): Result =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) return@withContext Result.Unavailable
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return@withContext Result.Unavailable
            val mic = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, FRAME * 4))
            } catch (_: Exception) { return@withContext Result.Unavailable }
            try {
                if (mic.state != AudioRecord.STATE_INITIALIZED) return@withContext Result.Unavailable
                mic.startRecording()
                val scratch = ShortArray(FRAME)
                val bytes = ByteArrayOutputStream()
                val preRoll = ArrayDeque<ByteArray>()
                var floor = 100.0
                var voiced = false
                var consecutiveVoice = 0
                var confirmedFrames = 0
                var quiet = 0
                for (tick in 0 until MAX_FRAMES) {
                    if (!currentCoroutineContext().isActive) return@withContext Result.Silence
                    val count = mic.read(scratch, 0, scratch.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) return@withContext Result.Unavailable
                    val volume = rms(scratch, count)
                    val loud = volume > maxOf(350.0, floor * 2.7)
                    // Only adapt the noise floor when the frame is quiet.
                    if (!loud) floor = floor * 0.97 + volume * 0.03
                    val frameBytes = ByteArray(count * 2)
                    for (i in 0 until count) {
                        val v = scratch[i].toInt()
                        frameBytes[i * 2] = (v and 255).toByte()
                        frameBytes[i * 2 + 1] = ((v ushr 8) and 255).toByte()
                    }
                    if (!voiced) {
                        preRoll.addLast(frameBytes)
                        if (preRoll.size > 4) preRoll.removeFirst()
                        consecutiveVoice = if (loud) consecutiveVoice + 1 else 0
                        if (consecutiveVoice >= MIN_VOICE_FRAMES) {
                            voiced = true
                            preRoll.forEach { bytes.write(it) }
                            preRoll.clear()
                            confirmedFrames = consecutiveVoice
                        }
                        if (tick >= NO_SPEECH_FRAMES && !voiced) return@withContext Result.Silence
                    } else {
                        bytes.write(frameBytes)
                        if (loud) {
                            confirmedFrames++
                            quiet = 0
                        } else quiet++
                        if (quiet >= QUIET_FRAMES && confirmedFrames >= MIN_VOICE_FRAMES)
                            break
                    }
                    // Level is only a visualization; no raw PCM escapes this recorder
                    // except the one explicit transcription request below.
                    onLevel((volume / 3800.0).toFloat().coerceIn(0f, 1f), loud)
                }
                if (!voiced || confirmedFrames < MIN_VOICE_FRAMES) Result.Silence
                else Result.Audio(pcm16Wav(bytes.toByteArray()))
            } catch (_: Exception) { Result.Unavailable }
            finally {
                runCatching { if (mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) mic.stop() }
                mic.release()
            }
        }
}
