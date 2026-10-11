package ai.jarvis.mobile

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.speech.tts.TextToSpeech

/** Ephemeral message information: never persisted or transmitted to a server. */
internal data class MessageNotice(
    val key: String,
    val appName: String,
    val sender: String,
    val body: String,
    val canReply: Boolean
)

internal object MessageBuffer {
    private val main = Handler(Looper.getMainLooper())
    val notices = mutableStateListOf<MessageNotice>()
    private val quickReplies = mutableMapOf<String, Pair<PendingIntent, Array<RemoteInput>>>()

    fun add(notice: MessageNotice, pending: PendingIntent?, inputs: Array<RemoteInput>?) {
        main.post {
            notices.removeAll { it.key == notice.key }
            notices.add(0, notice)
            if (pending != null && inputs != null && inputs.isNotEmpty()) {
                quickReplies[notice.key] = pending to inputs
            } else quickReplies.remove(notice.key)
            while (notices.size > 30) {
                quickReplies.remove(notices.last().key)
                notices.removeAt(notices.lastIndex)
            }
        }
    }

    fun remove(key: String) {
        main.post {
            notices.removeAll { it.key == key }
            quickReplies.remove(key)
        }
    }

    fun clear() {
        main.post {
            notices.clear()
            quickReplies.clear()
        }
    }

    /** Only explicitly user-triggered quick reply, and only if the source app exposes RemoteInput. */
    fun reply(context: Context, key: String, message: String): Boolean {
        val draft = message.trim()
        if (draft.isBlank()) return false
        val (pending, remoteInputs) = quickReplies[key] ?: return false
        return try {
            val results = Bundle()
            results.putCharSequence(remoteInputs.first().resultKey, draft)
            val intent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, intent, results)
            pending.send(context, 0, intent)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

internal object SupportedMessengers {
    val applications = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.facebook.orca" to "Messenger",
        "com.google.android.apps.messaging" to "Google Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "org.telegram.messenger" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal"
    )
}

/**
 * Android grants notification access in a separate Settings screen.
 * Access technically covers *all* notifications; this listener only processes
 * allowlisted messaging apps and keeps their previews temporarily in memory.
 * Lock-screen redaction and missing quick-reply actions are respected.
 */
class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val source = SupportedMessengers.applications[sbn.packageName] ?: return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (body.isBlank()) return
        val action = sbn.notification.actions
            ?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        MessageBuffer.add(
            MessageNotice(sbn.key, source, title.ifBlank { source }, body.take(2000), action != null),
            action?.actionIntent,
            action?.remoteInputs
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) MessageBuffer.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        MessageBuffer.clear()
    }
}

@Composable
internal fun MessageInboxScreen(context: Context, onPermissions: () -> Unit) {
    val notices = MessageBuffer.notices
    val speaker = remember(context) { TextToSpeech(context) { } }
    DisposableEffect(speaker) { onDispose { speaker.stop(); speaker.shutdown() } }
    var info by remember { mutableStateOf("") }
    Text("Üzenetek", color = Color.White, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "WhatsApp, Messenger, SMS-alkalmazások és más támogatott appok friss értesítései. " +
            "A teljes beszélgetéselőzményt Jarvis nem látja. Az üzeneteket nem tölti fel és nem menti tartósan.",
        color = Color(0xFFB5C3D8)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onPermissions) { Text("Értesítési engedélyek") }
    OutlinedButton(onClick = { MessageBuffer.clear(); info = "Az ideiglenes üzenetlista kiürítve." }) {
        Text("Üzenetlista ürítése")
    }
    if (info.isNotEmpty()) Text(info, color = Color(0xFF60D5E5))
    if (notices.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Nincs új, elérhető üzenetértesítés. Engedélyezd az értesítések olvasását, majd várj egy új üzenetre.",
            color = Color(0xFFB5C3D8))
    }
    notices.forEach { notice ->
        key(notice.key) {
            var replyDraft by remember { mutableStateOf("") }
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182334))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.appName + " · " + notice.sender,
                        color = Color(0xFF60D5E5), fontWeight = FontWeight.Bold)
                    Text(notice.body, color = Color.White)
                    OutlinedButton(onClick = {
                        speaker.speak(notice.sender + ": " + notice.body, TextToSpeech.QUEUE_FLUSH, null, notice.key)
                    }) { Text("🔊 Felolvasás") }
                    if (notice.canReply) {
                        OutlinedTextField(replyDraft, onValueChange = { replyDraft = it },
                            label = { Text("Válasz") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            info = if (MessageBuffer.reply(context, notice.key, replyDraft)) {
                                replyDraft = ""
                                "A gyorsválaszt átadtam az üzenetalkalmazásnak. A kézbesítést ott ellenőrizd."
                            } else "A gyorsválasz nem sikerült. Nyisd meg az eredeti alkalmazást."
                        }, enabled = replyDraft.isNotBlank()) {
                            Text("Válasz elküldése")
                        }
                    } else {
                        Text("Ehhez az értesítéshez az alkalmazás nem adott gyorsválasz-lehetőséget.",
                            color = Color(0xFFB5C3D8))
                    }
                }
            }
        }
    }
}
