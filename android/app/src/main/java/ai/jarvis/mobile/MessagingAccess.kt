package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    private val messageOpens = mutableMapOf<String, PendingIntent>()

    fun add(notice: MessageNotice, pending: PendingIntent?, inputs: Array<RemoteInput>?, open: PendingIntent?) {
        main.post {
            notices.removeAll { it.key == notice.key }
            notices.add(0, notice)
            if (open != null) messageOpens[notice.key] = open else messageOpens.remove(notice.key)
            if (pending != null && inputs != null && inputs.isNotEmpty()) {
                quickReplies[notice.key] = pending to inputs
            } else quickReplies.remove(notice.key)
            while (notices.size > 30) {
                quickReplies.remove(notices.last().key)
                messageOpens.remove(notices.last().key)
                notices.removeAt(notices.lastIndex)
            }
        }
    }

    fun remove(key: String) {
        main.post {
            notices.removeAll { it.key == key }
            quickReplies.remove(key)
            messageOpens.remove(key)
        }
    }

    fun clear() {
        main.post {
            notices.clear()
            quickReplies.clear()
            messageOpens.clear()
        }
    }

    fun openOriginal(context: Context, key: String): Boolean {
        val pending = messageOpens[key] ?: return false
        return try { pending.send(); true } catch (_: Exception) { false }
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
            action?.remoteInputs,
            sbn.notification.contentIntent
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try { activeNotifications?.forEach { onNotificationPosted(it) } } catch (_: Exception) {}
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
    var dictatedKey by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    val dictateReply = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            val key = dictatedKey
            if (!heard.isNullOrBlank() && key != null) drafts[key] = heard
        }
        dictatedKey = null
    }
    Text("Üzenetek", color = JarvisPalette.mainText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "WhatsApp, Messenger, SMS-alkalmazások és más támogatott appok friss értesítései. " +
            "A teljes beszélgetéselőzményt Jarvis nem látja. Az üzeneteket nem tölti fel és nem menti tartósan.",
        color = JarvisPalette.subtleText
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onPermissions) { Text("Értesítési engedélyek") }
    OutlinedButton(onClick = { MessageBuffer.clear(); info = "Az ideiglenes üzenetlista kiürítve." }) {
        Text("Üzenetlista ürítése")
    }
    if (info.isNotEmpty()) Text(info, color = JarvisPalette.accent)
    if (notices.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Nincs új, elérhető üzenetértesítés. Engedélyezd az értesítések olvasását, majd várj egy új üzenetre.",
            color = JarvisPalette.subtleText)
    }
    notices.forEach { notice ->
        key(notice.key) {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = JarvisPalette.panel)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.appName + " · " + notice.sender,
                        color = JarvisPalette.accent, fontWeight = FontWeight.Bold)
                    Text(notice.body, color = JarvisPalette.mainText)
                    OutlinedButton(onClick = {
                        speaker.speak(notice.sender + ": " + notice.body, TextToSpeech.QUEUE_FLUSH, null, notice.key)
                    }) { Text("🔊 Felolvasás") }
                    if (notice.canReply) {
                        OutlinedTextField(drafts[notice.key].orEmpty(),
                            onValueChange = { drafts[notice.key] = it },
                            label = { Text("Válasz") }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = {
                            dictatedKey = notice.key
                            try {
                                dictateReply.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Diktáld a választ – küldés előtt ellenőrizheted")
                                })
                            } catch (_: Exception) { info = "Nincs telepített beszédfelismerő." }
                        }) { Text("🎤 Válasz diktálása") }
                        Button(onClick = {
                            info = if (MessageBuffer.reply(context, notice.key, drafts[notice.key].orEmpty())) {
                                drafts[notice.key] = ""
                                "A gyorsválaszt átadtam az üzenetalkalmazásnak. A kézbesítést ott ellenőrizd."
                            } else "A gyorsválasz nem sikerült. Nyisd meg az eredeti alkalmazást."
                        }, enabled = drafts[notice.key]?.isNotBlank() == true) {
                            Text("Válasz elküldése")
                        }
                    } else {
                        Text("Ehhez az értesítéshez az alkalmazás nem adott gyorsválasz-lehetőséget.",
                            color = JarvisPalette.subtleText)
                    }
                    OutlinedButton(onClick = {
                        info = if (MessageBuffer.openOriginal(context, notice.key))
                            "Megnyitottam az eredeti üzenetalkalmazást."
                        else "Ez az értesítés már nem nyitható meg."
                    }) { Text("Megnyitás az eredeti alkalmazásban") }
                }
            }
        }
    }
}
