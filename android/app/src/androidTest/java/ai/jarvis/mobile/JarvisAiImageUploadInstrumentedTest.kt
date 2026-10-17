package ai.jarvis.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class JarvisAiImageUploadInstrumentedTest {
    @Test fun largeSelectedPngIsCompressedButPrivateOriginalRemainsUnchanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File.createTempFile("source-photo-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1500, 1100, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(bitmap.width)
            val random = Random(12345)
            for (y in 0 until bitmap.height) {
                for (x in row.indices) {
                    row[x] = 0xFF000000.toInt() or random.nextInt(0x1000000)
                }
                bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            }
            original.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val oldSize = original.length()
            assertTrue(oldSize > JarvisAiAttachments.MAX_FILE_BYTES)
            val (mime, uploaded) = JarvisAiImageUpload.prepare(
                original, "image/png", JarvisAiImageUpload.MAX_ENCODED_BYTES)
            assertEquals("image/jpeg", mime)
            assertTrue(uploaded.isNotEmpty())
            assertTrue(uploaded.size <= JarvisAiImageUpload.MAX_ENCODED_BYTES)
            assertEquals(oldSize, original.length())
            val result = BitmapFactory.decodeByteArray(uploaded, 0, uploaded.size)
            assertTrue(result != null)
            result?.recycle()
        } finally {
            bitmap.recycle()
            original.delete()
        }
    }
}
