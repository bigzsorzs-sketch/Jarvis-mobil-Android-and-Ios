package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** A single, summonable command surface over any form. It does not navigate
 * home or dispose unsaved fields when the user types a page command. */
@Composable
internal fun JarvisCommandPanel(context: Context, answer: String,
    onCommand: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isBlank()) return
        val command = text.trim()
        text = ""
        onCommand(command)
    }
    // Panel buttons must not compete with same-named controls in the form.
    CompositionLocalProvider(LocalJarvisControls provides null) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { LocalizedText("Jarvis-parancs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, modifier = Modifier.testTag("jarvis-page-command-input"),
                        singleLine = true, label = { LocalizedText("Parancs") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }))
                    if (answer.isNotBlank()) Text(answer, modifier = Modifier.testTag("jarvis-page-command-result"))
                    LocalizedText("Példa: nyomd meg a Mentés gombot. Mező: írd az Új jegyzet mezőbe: bevásárlás.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { send() }, modifier = Modifier.testTag("jarvis-page-command-send")) { LocalizedText("Küldés") } },
            dismissButton = { TextButton(onClick = onDismiss) { LocalizedText("Bezárás") } })
    }
}
