package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * User-recoverable PRIVATE server access without resurrecting the old API-key
 * editor. OpenRouter credentials must always stay on the authenticated server.
 * No secrets are displayed, exported, logged or silently submitted.
 */
@Composable
internal fun JarvisAiRecoverySettings(context: Context, language: JarvisLanguage) {
    val en = language != JarvisLanguage.HUNGARIAN
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val connected = remember(revision) { JarvisAiGateway.hasSavedConnection(context) }
    val textConsent = remember(revision) { JarvisAiGateway.cloudConsent(context) }
    val voiceConsent = remember(revision) { JarvisAiGateway.voiceConsent(context) }
    val audioConsent = remember(revision) { JarvisAiGateway.transcriptionConsent(context) }
    val server = JarvisAiGateway.baseUrl(context)
    val serverAvailable = JarvisAiGateway.normalizedBaseUrl(server) != null

    SettingsSectionTitle(if (en) "AI connection and permissions" else "AI-kapcsolat és engedélyek")
    JarvisSettingsGroup {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (connected) {
                    if (en) "Jarvis AI access is saved on this phone."
                    else "A Jarvis AI-kapcsolat mentve van ezen a telefonon."
                } else {
                    if (en) "Jarvis AI access is not connected. Restore access with a private invitation."
                    else "Nincs aktív Jarvis AI-kapcsolat. Privát meghívóval újra csatlakozhatsz."
                }, modifier = Modifier.testTag("jarvis-ai-connection-state")
            )
            if (!connected) {
                if (serverAvailable) {
                    OutlinedTextField(
                        value = invite,
                        onValueChange = { invite = it.trim().take(48) },
                        singleLine = true,
                        enabled = !busy,
                        label = { Text(if (en) "Private invitation code" else "Privát meghívókód") },
                        modifier = Modifier.fillMaxWidth().testTag("jarvis-ai-invitation")
                    )
                    Button(
                        enabled = !busy && invite.matches(Regex("[0-9a-fA-F]{48}")),
                        onClick = {
                            val code = invite
                            invite = ""
                            busy = true
                            scope.launch {
                                try {
                                    val error = JarvisAiGateway.activateInvitation(context, server, code)
                                    status = error ?: if (en) "AI access activated. Review permissions below."
                                        else "AI-kapcsolat aktiválva. Ellenőrizd az engedélyeket."
                                } catch (_: Exception) {
                                    status = if (en) "Activation failed. No access was changed."
                                        else "Az aktiválás sikertelen. A hozzáférés nem változott."
                                } finally {
                                    busy = false
                                    revision++
                                }
                            }
                        }, modifier = Modifier.testTag("jarvis-ai-reconnect")
                    ) { Text(if (en) "Restore AI access" else "AI-kapcsolat helyreállítása") }
                } else {
                    Text(if (en) "The private Jarvis server is not configured in this build. Contact the app owner."
                        else "A privát Jarvis-szerver nincs beállítva ebben a kiadásban. Fordulj az üzemeltetőhöz.")
                }
            }
            if (connected) {
                Text(if (en) "Cloud processing is optional; revoke it here at any time."
                    else "A felhőfeldolgozás külön engedélyezhető, és itt bármikor visszavonható.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (en) "Send text to Jarvis AI" else "Szöveg küldése a Jarvis AI-nak",
                        modifier = Modifier.weight(1f))
                    Switch(checked = textConsent, onCheckedChange = {
                        JarvisAiGateway.setCloudConsent(context, it)
                        revision++
                    }, modifier = Modifier.testTag("jarvis-ai-consent-text"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (en) "Cloud voice replies" else "Felhős hangválasz",
                        modifier = Modifier.weight(1f))
                    Switch(checked = voiceConsent, onCheckedChange = {
                        JarvisAiGateway.setVoiceConsent(context, it)
                        revision++
                    }, modifier = Modifier.testTag("jarvis-ai-consent-voice"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (en) "Upload microphone audio for speech recognition"
                        else "Mikrofonhang feltöltése beszédfelismeréshez",
                        modifier = Modifier.weight(1f))
                    Switch(checked = audioConsent, onCheckedChange = {
                        JarvisAiGateway.setTranscriptionConsent(context, it)
                        revision++
                    }, modifier = Modifier.testTag("jarvis-ai-consent-audio"))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
