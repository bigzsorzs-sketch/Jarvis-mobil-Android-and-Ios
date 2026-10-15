package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First-party Jarvis Smart Home screen. HA connections are optional;
 * no TV, washer or microwave is represented as paired until verified.
 */
@Composable
internal fun JarvisSmartHomePanel(context: Context, language: JarvisLanguage) {
    fun tr(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        listOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    val stored = remember(context) { runCatching { JarvisHomeConnect.load(context) }.getOrNull() }
    var origin by remember { mutableStateOf(stored?.origin.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<JarvisHomeEntity>>(emptyList()) }
    var connected by remember { mutableStateOf(stored != null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(tr("Jarvis Smart Home", "Jarvis Smart Home", "Jarvis Smart Home",
        "Maison connectée Jarvis", "Casa inteligente Jarvis",
        "Casa intelligente Jarvis", "Inteligentny dom Jarvis"),
        style = MaterialTheme.typography.titleLarge, color = JarvisPalette.mainText)
    Text(tr(
        "Első kapcsolat: Home Assistant. A hozzákapcsolt lámpák és tévék be/ki kapcsolhatók, ha a tényleges állapotuk visszaigazolható. Mosógép, mikró, zár, termosztát és egyéb készülék: egyelőre csak állapot.",
        "First connector: Home Assistant. Compatible lights and TVs support on/off with state confirmation. Washers, microwaves, locks, thermostats and other appliances are status-only.",
        "Erster Anschluss: Home Assistant. Lampen/TV mit Rückmeldung; Haushaltsgeräte nur Status.",
        "Premier connecteur : Home Assistant. Lampes/TV contrôlables, appareils ménagers : état seulement.",
        "Primer conector: Home Assistant. Luces/TV con verificación; electrodomésticos: solo estado.",
        "Primo connettore: Home Assistant. Luci/TV verificati; elettrodomestici: solo stato.",
        "Pierwsze połączenie: Home Assistant. Światła/TV z weryfikacją; AGD: tylko stan."
    ), color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = origin, onValueChange = { origin = it },
        label = { Text(tr("Home Assistant HTTPS cím", "Home Assistant HTTPS URL",
            "Home Assistant HTTPS-Adresse", "Adresse HTTPS Home Assistant",
            "URL HTTPS Home Assistant", "URL HTTPS Home Assistant",
            "Adres HTTPS Home Assistant")) },
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("smart-home-origin"))
    OutlinedTextField(value = token, onValueChange = { token = it },
        label = { Text(tr("Hozzáférési token (titkosítva mentjük)", "Access token (encrypted)",
            "Zugangstoken (verschlüsselt)", "Jeton d'accès (chiffré)",
            "Token de acceso (cifrado)", "Token di accesso (crittografato)",
            "Token dostępu (szyfrowany)")) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("smart-home-token"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy && token.isNotBlank(), onClick = {
            runCatching { JarvisHomeConnect.save(context, origin, token) }
                .onSuccess {
                    token = ""
                    connected = true
                    devices = emptyList()
                    status = tr("Kapcsolat titkosítva mentve. Eszközök betöltéséhez frissíts.",
                        "Connection saved securely. Refresh to load devices.",
                        "Gespeichert. Jetzt aktualisieren.", "Enregistré. Actualiser.",
                        "Guardado. Actualizar.", "Salvato. Aggiorna.", "Zapisano. Odśwież.")
                }
                .onFailure { status = it.message.orEmpty() }
        }, modifier = Modifier.testTag("smart-home-save")) {
            Text(tr("Mentés", "Save", "Speichern", "Enregistrer", "Guardar", "Salva", "Zapisz"))
        }
        OutlinedButton(enabled = !busy && connected, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val config = JarvisHomeConnect.load(context)
                            ?: error("Nincs aktív Home Assistant kapcsolat.")
                        JarvisHomeConnect.discover(config)
                    }
                }
                result.onSuccess {
                    devices = it
                    status = tr("Elérhető eszközök: ", "Available devices: ",
                        "Geräte: ", "Appareils : ", "Dispositivos: ",
                        "Dispositivi: ", "Urządzenia: ") + it.size
                }.onFailure { status = it.message ?: "Kapcsolódási hiba." }
                busy = false
            }
        }, modifier = Modifier.testTag("smart-home-refresh")) {
            Text(tr("Eszközök lekérése", "Find devices", "Geräte laden", "Chercher",
                "Buscar", "Cerca", "Szukaj"))
        }
    }
    OutlinedButton(enabled = !busy && connected, onClick = {
        runCatching { JarvisHomeConnect.disconnect(context) }
            .onSuccess {
                token = ""; origin = ""; devices = emptyList(); connected = false
                status = tr("Kapcsolat leválasztva.", "Disconnected.",
                    "Verbindung getrennt.", "Déconnecté.", "Desconectado.",
                    "Disconnesso.", "Rozłączono.")
            }.onFailure { status = "A leválasztás nem sikerült." }
    }, modifier = Modifier.testTag("smart-home-disconnect")) {
        Text(tr("Kapcsolat törlése", "Disconnect", "Trennen", "Déconnecter",
            "Desconectar", "Disconnetti", "Rozłącz"))
    }

    if (status.isNotBlank()) Text(status, color = JarvisPalette.mainText,
        modifier = Modifier.testTag("smart-home-status"))
    if (busy) CircularProgressIndicator()
    Spacer(Modifier.height(10.dp))
    // No device control is enabled for appliance domains, even if their
    // integration happens to expose a service with a similar name.
    devices.forEach { item ->
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(item.name, color = JarvisPalette.mainText,
                style = MaterialTheme.typography.bodyLarge)
            Text(item.domain + " · " + item.state, color = JarvisPalette.subtleText,
                style = MaterialTheme.typography.bodySmall)
            if (item.canToggle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, turnOn) in listOf(
                        tr("Be", "On", "An", "Allumer", "Encender", "Accendi", "Włącz") to true,
                        tr("Ki", "Off", "Aus", "Éteindre", "Apagar", "Spegni", "Wyłącz") to false)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        JarvisHomeConnect.toggle(
                                            JarvisHomeConnect.load(context)
                                                ?: error("Nincs Home Assistant kapcsolat."),
                                            item.id, turnOn)
                                    }
                                }
                                status = result.getOrElse {
                                    it.message ?: "Nem sikerült a vezérlés."
                                }
                                // Always reread HA; never optimistically fake a state.
                                val latest = withContext(Dispatchers.IO) {
                                    runCatching {
                                        JarvisHomeConnect.state(
                                            JarvisHomeConnect.load(context)
                                                ?: error("Nincs kapcsolat."), item.id)
                                    }.getOrNull()
                                }
                                if (latest != null) devices = devices.map {
                                    if (it.id == item.id) latest else it
                                }
                                busy = false
                            }
                        }) { Text(label) }
                    }
                }
            } else Text(tr("Csak állapotlekérdezés", "Status only", "Nur Status",
                "Statut seulement", "Solo estado", "Solo stato", "Tylko stan"),
                color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(tr(
        "A kapcsolathoz saját Home Assistant szerver és érvényes HTTPS/tanúsítvány szükséges. A helyi HTTP alapértelmezés nem engedélyezett. Eszközt csak akkor lát a Jarvis, ha azt a Home Assistant valóban elérhetővé teszi.",
        "Requires your own Home Assistant server with a valid HTTPS certificate. Plain local HTTP is not allowed. Only entities actually exposed by Home Assistant can be controlled.",
        "Eigener Home Assistant mit gültigem HTTPS-Zertifikat erforderlich.",
        "Un serveur Home Assistant HTTPS valide est nécessaire.",
        "Se requiere Home Assistant con HTTPS válido.",
        "Serve Home Assistant con HTTPS valido.",
        "Wymagany Home Assistant z poprawnym HTTPS."
    ), color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
}
