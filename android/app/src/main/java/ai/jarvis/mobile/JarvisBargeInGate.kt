package ai.jarvis.mobile

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Conservative voice onset gate for talk-over. Never executes a command: it
 * merely asks the caller to stop audio and start the existing recognizer.
 * Audio samples and level history remain on the device, in memory only.
 */
internal class JarvisBargeInGate {
    private var warmup = 0
    private var ambient = 140.0
    private var consecutive = 0

    fun observe(samples: ShortArray, count: Int): Boolean {
        if (count <= 0) return false
        var power = 0.0
        for (i in 0 until count.coerceAtMost(samples.size)) {
            val value = samples[i].toDouble()
            power += value * value
        }
        val rms = sqrt(power / count.coerceAtMost(samples.size))
        // First 200 ms establish the current (echo-cancelled) acoustic floor.
        if (warmup++ < 10) {
            ambient = max(ambient, rms)
            return false
        }
        val threshold = max(1_000.0, ambient * 2.8)
        if (rms >= threshold) {
            consecutive++
        } else {
            consecutive = 0
            // Adapt only to quieter background; speech never raises the floor.
            ambient = ambient * 0.96 + rms * 0.04
        }
        return consecutive >= 9  // 180 ms of consistently stronger speech
    }
}
