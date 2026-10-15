package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Developer preview: PAT lasts up to 24 h; a production connector needs OAuth.
 * No vendor password or long-lived client secret is put in the Android APK.
 */
@Composable
internal fun JarvisSmartThingsPanel(context: Context, language: JarvisLanguage) {
    fun tr(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        listOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    var token by remember { mutableStateOf("") }
    var connected by remember {
        mutableStateOf(JarvisSmartThings.loadTestToken(context) != null)
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<JarvisSmartThingsDevice>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Spacer(Modifier.height(18.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text("Samsung SmartThings",
        style = MaterialTheme.typography.titleMedium, color = JarvisPalette.mainText)
    Text(tr(
        "Teszt-integráció: a SmartThingshez hozzáférő rövid élettartamú (legfeljebb 24 órás) tokennel. Gyártói fiók jelszavát ne írd be. Vásárlói kiadáshoz még OAuth szükséges.",
        "Developer test: connect with a short-lived SmartThings personal access token (up to 24h). Never enter your Samsung password. Public release requires OAuth.",
        "Testintegration mit 24h-Token. Nicht dein Samsung-Passwort eingeben. Für Veröffentlichung ist OAuth nötig.",
        "Intégration de test avec jeton 24h. Ne saisissez pas votre mot de passe Samsung. OAuth requis pour la sortie publique.",
        "Conexión de prueba con token de 24 h. No introduzca su contraseña Samsung. OAuth necesario para producción.",
        "Test con token di 24 ore. Non inserire la password Samsung. OAuth necessario per il rilascio.",
        "Test z tokenem 24h. Nie wpisuj hasła Samsung. Wydanie publiczne wymaga OAuth."
    ), style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
    OutlinedTextField(value = token, onValueChange = { token = it },
        label = { Text("SmartThings personal access token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("smartthings-token"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://account.smartthings.com/tokens"))) }
                .onFailure { message = "Could not open SmartThings token page." }
        }) { Text(tr("Token beszerzése", "Get test token", "Testtoken holen",
            "Obtenir un jeton", "Obtener token", "Ottieni token", "Pobierz token")) }
        Button(enabled = !busy && token.isNotBlank(), onClick = {
            runCatching { JarvisSmartThings.saveTestToken(context, token) }
                .onSuccess {
                    connected = true
                    devices = emptyList()
                    token = ""
                    message = tr("Tesztkapcsolat mentve titkosítva.", "Test connection saved securely.",
                        "Gespeichert.", "Connexion enregistrée.",
                        "Guardado.", "Salvato.", "Zapisano.")
                }.onFailure { message = it.message.orEmpty() }
        }, modifier = Modifier.testTag("smartthings-connect")) {
            Text(tr("Mentés", "Save", "Speichern", "Enregistrer", "Guardar",
                "Salva", "Zapisz"))
        }
    }
    OutlinedButton(enabled = connected && !busy, onClick = {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val access = JarvisSmartThings.loadTestToken(context)
                        ?: error("SmartThings test token expired. Reconnect.")
                    JarvisSmartThings.list(access)
                }
            }
            result.onSuccess {
                devices = it
                message = tr("Felderített eszközök: ", "Discovered devices: ",
                    "Gefundene Geräte: ", "Appareils trouvés : ",
                    "Dispositivos: ", "Dispositivi: ", "Urządzenia: ") + it.size
            }.onFailure {
                message = it.message ?: "SmartThings connection could not be verified."
            }
            busy = false
        }
    }, modifier = Modifier.testTag("smartthings-refresh")) {
        Text(tr("Eszközök lekérése", "Fetch devices", "Geräte laden", "Charger",
            "Consultar dispositivos", "Carica dispositivi", "Pobierz urządzenia"))
    }
    OutlinedButton(enabled = connected && !busy, onClick = {
        runCatching { JarvisSmartThings.revoke(context) }.onSuccess {
            connected = false
            devices = emptyList()
            message = tr("Tesztkapcsolat törölve.", "Test connection removed.",
                "Getrennt.", "Déconnecté.", "Desconectado.", "Disconnesso.", "Rozłączono.")
        }.onFailure { message = "Cannot remove connection." }
    }, modifier = Modifier.testTag("smartthings-disconnect")) {
        Text(tr("Leválasztás", "Disconnect", "Trennen", "Déconnecter",
            "Desconectar", "Disconnetti", "Rozłącz"))
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message, color = JarvisPalette.mainText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("smartthings-status"))
    devices.forEach { device ->
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(device.name, color = JarvisPalette.mainText)
            Text(device.category + " • " + device.state,
                color = JarvisPalette.subtleText,
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val access = JarvisSmartThings.loadTestToken(context)
                                    ?: error("SmartThings test token expired.")
                                JarvisSmartThings.readStatus(access, device)
                            }
                        }
                        result.onSuccess { state ->
                            devices = devices.map { entry ->
                                if (entry.id == device.id) entry.copy(state = state) else entry
                            }
                            message = device.name + ": " + state
                        }.onFailure { message = it.message ?: "Could not read status." }
                        busy = false
                    }
                }) {
                    Text(tr("Állapot", "Status", "Status", "État", "Estado", "Stato", "Stan"))
                }
                if (device.canSwitchLight) {
                    for ((label, on) in listOf(
                        tr("Be", "On", "An", "Allumer", "Encender", "Accendi", "Włącz") to true,
                        tr("Ki", "Off", "Aus", "Éteindre", "Apagar", "Spegni", "Wyłącz") to false
                    )) {
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val access = JarvisSmartThings.loadTestToken(context)
                                            ?: error("SmartThings test token expired.")
                                        JarvisSmartThings.switchLight(access, device, on)
                                    }
                                }
                                message = result.getOrElse { it.message ?: "Unverified command." }
                                val verifiedState = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val access = JarvisSmartThings.loadTestToken(context)
                                            ?: error("Expired connection.")
                                        JarvisSmartThings.state(access, device)
                                    }.getOrNull()
                                }
                                if (verifiedState != null) devices = devices.map {
                                    if (it.id == device.id) it.copy(state = verifiedState) else it
                                }
                                busy = false
                            }
                        }) { Text(label) }
                    }
                }
            }
        }
    }
    Text(tr(
        "A kapcsolóval rendelkező, Light kategóriájú lámpák kapcsolhatók. TV-k és háztartási gépek egyelőre csak lekérdezhetők. A token és az eszközlista nem kerül az AI-hoz.",
        "Only devices explicitly categorized as Light with switch capability can be toggled. TVs and appliances are read-only for now. Tokens and device data stay outside AI.",
        "Nur als Licht klassifizierte Geräte sind schaltbar. Sonstige Geräte nur lesbar.",
        "Seuls les appareils de type lumière sont commutables. Les autres sont en lecture seule.",
        "Solo se pueden encender luces. Otros dispositivos son de solo lectura.",
        "Solo luci sono controllabili. Altri dispositivi in sola lettura.",
        "Tylko lampy można włączać. Pozostałe tylko do odczytu."
    ), style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
}
