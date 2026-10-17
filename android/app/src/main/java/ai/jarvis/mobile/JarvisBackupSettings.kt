package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google Drive and OneDrive are Android Storage Access Framework providers.
 * No Google/Microsoft OAuth, auto-upload or account-wide storage access is used.
 */
@Composable
internal fun JarvisBackupSettings(context: Context, language: JarvisLanguage) {
    val en = language != JarvisLanguage.HUNGARIAN
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("Google Drive") }
    var passphrase by remember { mutableStateOf("") }
    var pendingSecret by remember { mutableStateOf<CharArray?>(null) }
    var toRestore by remember { mutableStateOf<JarvisSecureBackup.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val secret = pendingSecret
        pendingSecret = null
        if (uri == null || secret == null) {
            secret?.fill('\u0000')
            status = if (en) "Backup cancelled." else "Mentés megszakítva."
        } else {
            scope.launch {
                busy = true
                val success = withContext(Dispatchers.IO) {
                    try {
                        val data = JarvisSecureBackup.create(context, secret)
                        (context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Cannot open destination")).use {
                            it.write(data)
                            it.flush()
                        }
                        true
                    } catch (_: Exception) { false }
                    finally { secret.fill('\u0000') }
                }
                status = if (success) {
                    if (en) "Encrypted backup saved. Check the selected cloud folder."
                    else "Titkosított mentés kész. Ellenőrizd a választott felhőmappát."
                } else if (en) "Backup failed; no completed file confirmed."
                  else "Mentési hiba; nem igazolt a fájl mentése."
                busy = false
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val secret = pendingSecret
        pendingSecret = null
        if (uri == null || secret == null) {
            secret?.fill('\u0000')
            status = if (en) "Restore cancelled." else "Visszaállítás megszakítva."
        } else {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    try {
                        val raw = (context.contentResolver.openInputStream(uri)
                            ?: error("Cannot read file")).use { stream ->
                            ByteArrayOutputStream().use { out ->
                                val buffer = ByteArray(16384)
                                while (true) {
                                    val n = stream.read(buffer)
                                    if (n == -1) break
                                    require(out.size() + n <= JarvisSecureBackup.MAX_ARCHIVE) { "Archive too large" }
                                    out.write(buffer, 0, n)
                                }
                                out.toByteArray()
                            }
                        }
                        Result.success(JarvisSecureBackup.inspect(raw, secret))
                    } catch (error: Exception) {
                        Result.failure<JarvisSecureBackup.Snapshot>(error)
                    } finally { secret.fill('\u0000') }
                }
                result.onSuccess { toRestore = it; dialog = "confirmRestore"; status = "" }
                    .onFailure {
                        status = if (en) "Wrong password or invalid archive. No data restored."
                                 else "Hibás jelszó vagy sérült mentés. Az adatok változatlanok."
                    }
                busy = false
            }
        }
    }

    SettingsSectionTitle(if (en) "Backup and account" else "Mentés és fiók")
    JarvisSettingsGroup {
        JarvisSettingsRow("☁",
            if (en) "Secure backup" else "Biztonságos mentés",
            if (en) "Encrypted data to Google Drive or OneDrive"
            else "Titkosított mentés Google Drive-ra vagy OneDrive-ra") {
            if (!busy) { provider = "Google Drive"; passphrase = ""; dialog = "backup" }
        }
        SettingsDivider()
        JarvisSettingsRow("↺",
            if (en) "Restore backup" else "Mentés visszaállítása",
            if (en) "Import password-protected backup" else "Jelszóval védett mentés betöltése") {
            if (!busy) { passphrase = ""; dialog = "restore" }
        }
        SettingsDivider()
        JarvisSettingsRow("⇥",
            if (en) "Log out" else "Kijelentkezés",
            if (en) "Disconnect Jarvis AI access; keep local data"
            else "Jarvis AI-kapcsolat bontása, helyi adatok megtartása") {
            if (!busy) dialog = "logout"
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)

    if (dialog == "backup" || dialog == "restore") {
        val backup = dialog == "backup"
        AlertDialog(
            onDismissRequest = { if (!busy) { dialog = ""; passphrase = "" } },
            title = { Text(if (backup) {
                if (en) "Create encrypted backup" else "Titkosított mentés készítése"
            } else if (en) "Restore backup" else "Mentés visszaállítása") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (en)
                        "Includes chats, glucose log, notes, billing and saved places. Excludes API keys, login tokens and passwords."
                        else "Tartalmazza a beszélgetéseket, vércukornaplót, jegyzeteket, számlázási adatokat és mentett helyeket. API-kulcsot, belépési tokent, jelszót nem.")
                    if (backup) {
                        Text(if (en) "Preferred location:" else "Ajánlott mentési hely:")
                        listOf("Google Drive", "Microsoft OneDrive").forEach { name ->
                            Row {
                                RadioButton(selected = provider == name,
                                    onClick = { provider = name })
                                Text(if (name == "Google Drive" && !en) "Google Drive (ajánlott)"
                                    else if (name == "Google Drive") "Google Drive (recommended)"
                                    else name, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        Text(if (en)
                            "Select " + provider + " inside Android's Save picker. Jarvis does not access your entire cloud account."
                        else "Az Android Mentés ablakában válaszd ki a(z) " + provider + " mappát. A Jarvis nem fér hozzá a teljes felhőfiókodhoz.")
                    } else {
                        Text(if (en)
                            "Restoring adds chats, but replaces matching local data. You will confirm after opening the file."
                        else "A visszaállítás beszélgetéseket ad hozzá, de az érintett helyi adatokat felülírja. Fájlmegnyitás után külön megerősítést kér.")
                    }
                    OutlinedTextField(
                        value = passphrase, onValueChange = { passphrase = it.take(256) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(if (en) "Backup password (12+ characters)"
                                       else "Mentési jelszó (minimum 12 karakter)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(if (en) "Keep this password safe; it cannot be recovered."
                    else "Őrizd meg a jelszót, mert nem tudjuk visszaállítani.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = passphrase.length >= 12 && !busy, onClick = {
                    pendingSecret?.fill('\u0000')
                    pendingSecret = passphrase.toCharArray()
                    passphrase = ""
                    dialog = ""
                    if (backup) {
                        val date = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                        createFile.launch("Jarvis-backup-" + date + ".jarvisbackup")
                    } else openFile.launch(arrayOf("*/*"))
                }) { Text(if (en) "Continue" else "Tovább") }
            },
            dismissButton = {
                TextButton(onClick = { passphrase = ""; dialog = "" }) {
                    Text(if (en) "Cancel" else "Mégse")
                }
            }
        )
    }
    val snapshot = toRestore
    if (dialog == "confirmRestore" && snapshot != null) {
        AlertDialog(
            onDismissRequest = { dialog = ""; toRestore = null },
            title = { Text(if (en) "Confirm restore" else "Visszaállítás megerősítése") },
            text = { Text(if (en)
                "Add " + snapshot.conversations.size + " chat records and overwrite " +
                    snapshot.fields.size + " local data fields? Existing chats will not be deleted."
                else "Hozzáadjak " + snapshot.conversations.size + " beszélgetést és felülírjak " +
                    snapshot.fields.size + " helyi adatmezőt? Meglévő beszélgetéseket nem törlök.") },
            confirmButton = {
                TextButton(onClick = {
                    toRestore = null
                    dialog = ""
                    scope.launch {
                        busy = true
                        val success = withContext(Dispatchers.IO) {
                            runCatching { JarvisSecureBackup.restore(context, snapshot) }.isSuccess
                        }
                        status = if (success) {
                            if (en) "Restored. Restart Jarvis to refresh the local screens."
                            else "Visszaállítva. Indítsd újra a Jarvist az adatok frissítéséhez."
                        } else if (en) "Restore failed. Some records may have been applied; don't repeat blindly."
                          else "Visszaállítási hiba. Egyes adatok már bekerülhettek; ne ismételd ellenőrzés nélkül."
                        busy = false
                    }
                }) { Text(if (en) "Restore" else "Visszaállítás") }
            },
            dismissButton = {
                TextButton(onClick = { toRestore = null; dialog = "" }) {
                    Text(if (en) "Cancel" else "Mégse")
                }
            }
        )
    }
    if (dialog == "logout") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(if (en) "Log out of Jarvis AI?" else "Kijelentkezés a Jarvis AI-ból?") },
            text = { Text(if (en)
                "Removes the AI session token/key and AI consent stored on this phone. Chats, glucose, notes and places remain. Gmail and other apps stay signed in. AI access may need to be authorized again."
                else "A telefonon mentett AI-tokent vagy kulcsot és a hozzájárulásokat törlöm. A beszélgetések, vércukoradatok, jegyzetek és helyek megmaradnak. A Gmailből és más appokból NEM lépsz ki. Az AI-t újraengedélyezni kellhet.") },
            confirmButton = {
                TextButton(onClick = {
                    val success = runCatching {
                        context.stopService(Intent(context, HandsFreeService::class.java))
                        JarvisAiGateway.clear(context)
                    }.isSuccess
                    status = if (success) {
                        if (en) "Jarvis AI access disconnected. Local data retained."
                        else "Jarvis AI-kapcsolat bontva. A helyi adatok megmaradtak."
                    } else if (en) "Logout could not be confirmed."
                      else "Nem sikerült igazolni a kijelentkezést."
                    dialog = ""
                }) { Text(if (en) "Log out" else "Kijelentkezés") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = "" }) { Text(if (en) "Cancel" else "Mégse") }
            }
        )
    }
}
