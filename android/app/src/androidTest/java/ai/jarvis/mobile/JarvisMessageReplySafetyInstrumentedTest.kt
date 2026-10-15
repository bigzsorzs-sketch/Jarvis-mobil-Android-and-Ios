package ai.jarvis.mobile

import android.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android RemoteInput contracts, not mocked JVM stubs. */
@RunWith(AndroidJUnit4::class)
class JarvisMessageReplySafetyInstrumentedTest {
    @Test fun noFreeFormResponseMeansNoAutomatedQuickReply() {
        val choiceOnly = RemoteInput.Builder("choice")
            .setAllowFreeFormInput(false)
            .setChoices(arrayOf("Yes", "No")).build()
        assertNull(MessageBuffer.freeFormInput(null))
        assertNull(MessageBuffer.freeFormInput(emptyArray()))
        assertNull(MessageBuffer.freeFormInput(arrayOf(choiceOnly)))
    }

    @Test fun explicitFreeFormFieldWinsOverUnrelatedChoiceField() {
        val choiceOnly = RemoteInput.Builder("choice")
            .setAllowFreeFormInput(false)
            .setChoices(arrayOf("Yes", "No")).build()
        val reply = RemoteInput.Builder("message_text")
            .setAllowFreeFormInput(true).build()
        assertEquals("message_text",
            MessageBuffer.freeFormInput(arrayOf(choiceOnly, reply))?.resultKey)
    }
}
