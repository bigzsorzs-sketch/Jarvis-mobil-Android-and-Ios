package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    val language = LocalJarvisLanguage.current
    val t = { key: String -> JarvisCommandTexts.text(language, key) }
    fun startVoice() {
        runCatching { ContextCompat.startForegroundService(context,
            Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.START_TAP)) }
            .onFailure { HandsFreeStatus.status = "A mikrofon nem indítható." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice()
    }
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
            title = { Text(t("panel")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, modifier = Modifier.testTag("jarvis-page-command-input"),
                        singleLine = true, label = { Text(t("command")) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }))
                    TextButton(onClick = {
                        if (HandsFreeStatus.running) context.startService(
                            Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.STOP))
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) startVoice()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(t(if (HandsFreeStatus.running) "voiceOff" else "voiceOn")) }
                    if (answer.isNotBlank()) Text(answer, modifier = Modifier.testTag("jarvis-page-command-result"))
                    Text(t("example"),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { send() }, modifier = Modifier.testTag("jarvis-page-command-send")) { Text(t("send")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(t("close")) } })
    }
}
