package ai.jarvis.mobile

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Enforces an upper response-body limit on all supported Android API levels. */
internal object JarvisNetworkSafety {
    const val MAX_AI_RESPONSE_BYTES = 256 * 1024

    fun readUtf8Limited(stream: InputStream, maxBytes: Int = MAX_AI_RESPONSE_BYTES): String {
        require(maxBytes in 1..MAX_AI_RESPONSE_BYTES)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(total <= maxBytes - count) { "AI_RESPONSE_TOO_LARGE" }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }
}
