package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JarvisCloudSpeechCaptureTest {
    @Test fun wavHeaderHasCorrectFormatAndBoundedPayload() {
        val pcm = ByteArray(3200) { 0 }
        val wav = JarvisCloudSpeechCapture.pcm16Wav(pcm)
        assertEquals(3244, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12), Charsets.US_ASCII))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(3236, header.getInt(4))
        assertEquals(16000, header.getInt(24))
        assertEquals(32000, header.getInt(28))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(3200, header.getInt(40))
    }

    @Test fun audioRmsDistinguishesSilenceFromSpeechAndInvalidLengths() {
        assertEquals(0.0, JarvisCloudSpeechCapture.rms(shortArrayOf(), 0), 0.001)
        assertEquals(0.0, JarvisCloudSpeechCapture.rms(shortArrayOf(0, 0), 2), 0.001)
        assertEquals(1000.0, JarvisCloudSpeechCapture.rms(shortArrayOf(1000, -1000), 2), 0.001)
    }

    @Test fun invalidPcmLengthIsRejectedBeforeUpload() {
        assertThrows(IllegalArgumentException::class.java) {
            JarvisCloudSpeechCapture.pcm16Wav(ByteArray(3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JarvisCloudSpeechCapture.pcm16Wav(ByteArray(16_000 * 2 * 16))
        }
    }

    @Test fun incompleteCallIsNotAuthorizationToDial() {
        assertFalse(VoiceCalling.callConfirmationYes("hívd"))
        assertFalse(VoiceCalling.callConfirmationYes("hívd fel"))
        assertTrue(VoiceCalling.callConfirmationYes("igen, hívd fel"))
        assertTrue(VoiceCalling.needsTarget("hívd"))
        assertFalse(JarvisCommandContext.shouldPreemptCallConfirmation("hívd", true))
        assertTrue(JarvisCommandContext.shouldPreemptCallConfirmation("hívd", false))
    }
}
