package ai.jarvis.mobile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Discover local protocols without connecting to or controlling unknown devices. */
@Composable
internal fun JarvisNearbyPanel(context: android.content.Context, language: JarvisLanguage) {
    fun label(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        listOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    val scanner = remember(context) { JarvisNearbyScanner(context) }
    val found = remember { mutableStateListOf<JarvisNearbyDevice>() }
    var wifiBusy by remember { mutableStateOf(false) }
    var bleBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    DisposableEffect(scanner) { onDispose { scanner.close() } }

    fun bleScan() {
        if (bleBusy) return
        bleBusy = true
        scanner.scanBle(
            onFound = { device ->
                if (found.none { it.key == device.key } &&
                    found.size < JarvisNearbyProtocols.MAX_RESULTS) found.add(device)
            },
            onEnd = {
                bleBusy = false
                notice = it
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (scanner.blePermissionsGranted()) bleScan()
        else notice = label("Bluetooth engedély nélkül nem keresek eszközöket.",
            "Bluetooth permission required for discovery.",
            "Bluetooth-Berechtigung erforderlich.",
            "Autorisation Bluetooth nécessaire.",
            "Se necesita permiso Bluetooth.",
            "Autorizzazione Bluetooth necessaria.",
            "Wymagane uprawnienie Bluetooth.")
    }

    Spacer(Modifier.height(16.dp))
    Text(label("Közeli eszközök felderítése", "Discover nearby devices",
        "Geräte in der Nähe", "Appareils à proximité",
        "Dispositivos cercanos", "Dispositivi vicini", "Urządzenia w pobliżu"),
        style = MaterialTheme.typography.titleMedium, color = JarvisPalette.mainText)
    Text(label(
        "Kézi, rövid idejű keresés a helyi Wi-Fi hálózaton és Bluetooth LE-n. A megtalált eszköz még NINCS csatlakoztatva vagy vezérelhetőként ellenőrizve.",
        "Manual, short Wi-Fi network and Bluetooth LE scans. Discovery does NOT mean paired or controllable.",
        "Kurze manuelle Wi-Fi- und BLE-Suche. Gefunden heißt nicht gekoppelt.",
        "Recherche Wi-Fi et BLE manuelle. Trouvé ne signifie pas appairé.",
        "Búsqueda manual Wi-Fi y BLE. Descubierto no significa vinculado.",
        "Ricerca manuale Wi-Fi e BLE. Trovato non significa associato.",
        "Ręczne skanowanie Wi-Fi i BLE. Wykrycie nie oznacza sparowania."
    ), style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !wifiBusy, onClick = {
            wifiBusy = true
            scanner.scanWifi(
                onFound = { device ->
                    if (found.none { it.key == device.key } &&
                        found.size < JarvisNearbyProtocols.MAX_RESULTS) found.add(device)
                },
                onEnd = {
                    wifiBusy = false
                    notice = it
                }
            )
        }, modifier = Modifier.testTag("smart-home-wifi-discover")) {
            Text(if (wifiBusy) "Wi-Fi…" else "Wi-Fi / mDNS")
        }
        OutlinedButton(enabled = !bleBusy, onClick = {
            if (scanner.blePermissionsGranted()) bleScan()
            else permissionLauncher.launch(
                if (Build.VERSION.SDK_INT >= 31) arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }, modifier = Modifier.testTag("smart-home-ble-discover")) {
            Text(if (bleBusy) "Bluetooth…" else "Bluetooth LE")
        }
    }
    if (wifiBusy || bleBusy) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            scanner.stopWifi()
            scanner.stopBle()
        }) {
            Text(label("Keresés leállítása", "Stop search", "Suche stoppen",
                "Arrêter", "Detener", "Ferma", "Zatrzymaj"))
        }
    }
    if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall,
        color = JarvisPalette.subtleText)
    found.forEach { item ->
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(item.name, color = JarvisPalette.mainText,
                style = MaterialTheme.typography.bodyMedium)
            Text(item.transport + " · " + item.protocol,
                style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
            Text(label("Felderítve, még nincs párosítva.",
                "Found, not yet paired.",
                "Gefunden, noch nicht gekoppelt.",
                "Détecté, pas encore associé.",
                "Detectado, sin vincular.",
                "Rilevato, non associato.",
                "Wykryto, jeszcze niesparowane."),
                style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
        }
    }
}
