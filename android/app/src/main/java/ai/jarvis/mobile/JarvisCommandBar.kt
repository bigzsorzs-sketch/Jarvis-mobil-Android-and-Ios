package ai.jarvis.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Every subpage uses the Home executor; commands never disappear with navigation. */
@Composable
internal fun JarvisCommandBar(context: Context, onCommand: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit(raw: String) {
        val command = VoiceIntentParser.afterWakeWord(raw)?.takeIf { it.isNotBlank() }
            ?: raw.trim()
        if (command.isBlank()) return
        text = ""
        issue = ""
        keyboard?.hide()
        onCommand(command)
    }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { submit(it) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (HandsFreeStatus.running) Text(HandsFreeStatus.status,
            style = MaterialTheme.typography.bodySmall)
        if (issue.isNotBlank()) Text(issue, color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("Kérdezd meg Jarvist") },
                modifier = Modifier.weight(1f).testTag("jarvis-subpage-input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(text) })
            )
            IconButton(modifier = Modifier.testTag("jarvis-subpage-send"), onClick = {
                if (text.isNotBlank()) submit(text)
                else try {
                    if (HandsFreeStatus.running) context.startService(
                        Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.STOP))
                    dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd a Jarvis parancsot")
                    })
                } catch (_: Exception) { issue = "Nincs kompatibilis beszédfelismerő." }
            }) { Text(if (text.isBlank()) "🎙" else "➤") }
        }
    }
}
