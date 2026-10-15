package ai.jarvis.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** User-initiated home capture; never enable background location silently. */
@Composable
internal fun JarvisDeparturePanel(context: Context, language: JarvisLanguage) {
    fun tr(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        listOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    val permissionRefresh = PermissionRefresh.serial
    val saved = remember(permissionRefresh) { JarvisDepartureGuard.home(context) }
    var radius by remember { mutableStateOf(saved?.radiusMeters ?: 150f) }
    var enabled by remember { mutableStateOf(JarvisDepartureGuard.enabled(context)) }
    var hasHome by remember { mutableStateOf(saved != null) }
    var status by remember { mutableStateOf("") }
    var capturing by remember { mutableStateOf(false) }
    val fine = ContextCompat.checkSelfPermission(context,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val background = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun captureHome() {
        capturing = true
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                capturing = false
                if (loc == null) {
                    status = "Nincs pontos GPS-helyzet. Próbáld újra otthon."
                } else {
                    runCatching { JarvisDepartureGuard.storeHome(context,
                        loc.latitude, loc.longitude, loc.accuracy, radius) }
                        .onSuccess {
                            hasHome = true
                            status = "Az otthoni GPS-hely titkosítva elmentve."
                        }.onFailure { status = it.message.orEmpty() }
                }
            }
            .addOnFailureListener {
                capturing = false
                status = "Nem sikerült lekérni a GPS-helyet."
            }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        if (it) captureHome() else status = "Helyengedély szükséges."
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        status = if (it) "Az értesítések engedélyezve." else
            "Értesítések nélkül nem tudok figyelmeztetni."
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
    Text(tr("Távozás – lámpaellenőrzés", "Leaving home – check lights",
        "Zuhause verlassen – Lichter prüfen", "Départ – vérifier les lampes",
        "Salida – comprobar luces", "Uscita – controllo luci",
        "Wyjście – sprawdzenie świateł"),
        style = MaterialTheme.typography.titleMedium, color = JarvisPalette.mainText)
    Text(tr(
        "Ha elhagyod a kijelölt otthoni zónát, Jarvis megpróbálja lekérni a lámpák állapotát. Ha világítanak, értesít. Nem kapcsolja le őket automatikusan. A helyfigyelés késhet és tévedhet.",
        "After leaving the home zone, Jarvis checks Home Assistant light states and alerts you if any are on. It never turns them off automatically. Location detection may be delayed.",
        "Nach Verlassen der Zone prüft Jarvis HA-Leuchten und benachrichtigt. Keine automatische Abschaltung.",
        "En quittant la zone, Jarvis vérifie les lampes HA. Aucune extinction automatique.",
        "Al salir, Jarvis comprueba las luces HA. No las apaga automáticamente.",
        "Uscendo, Jarvis controlla le luci HA senza spegnerle.",
        "Po wyjściu Jarvis sprawdzi światła HA, bez automatycznego wyłączania."
    ), style = MaterialTheme.typography.bodySmall, color = JarvisPalette.subtleText)
    Text(tr("Körzet", "Radius", "Radius", "Rayon", "Radio", "Raggio", "Promień") +
        ": " + radius.toInt().toString() + " m", color = JarvisPalette.mainText)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in listOf(100f, 150f)) {
            FilterChip(selected = radius == choice, enabled = !enabled && !capturing,
                onClick = { radius = choice }, label = { Text(choice.toInt().toString() + " m") })
        }
    }
    Text(if (hasHome)
        tr("Otthoni hely mentve.", "Home saved.", "Zuhause gespeichert.",
            "Domicile enregistré.", "Casa guardada.", "Casa salvata.", "Dom zapisany.")
    else tr("Még nincs otthoni hely.", "No home location set.",
        "Kein Zuhause festgelegt.", "Domicile non défini.",
        "Sin ubicación de casa.", "Casa non impostata.", "Brak lokalizacji domu."),
        color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(enabled = !enabled && !capturing, onClick = {
        if (fine) captureHome() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }, modifier = Modifier.testTag("smart-home-capture-home")) {
        Text(if (capturing) "GPS…" else tr("Itt vagyok otthon – mentés",
            "I'm home – save location", "Ich bin zuhause – speichern",
            "Je suis chez moi – enregistrer", "Estoy en casa – guardar",
            "Sono a casa – salva", "Jestem w domu – zapisz"))
    }
    if (!background) {
        Text(tr("Az Android alkalmazásengedélyeknél állítsd a helyet „Mindig engedélyezve” állapotra.",
            "In Android app permissions set Location to 'Allow all the time'.",
            "Android-App-Berechtigung: Standort immer erlauben.",
            "Android : autoriser la position en permanence.",
            "Android: permitir ubicación siempre.",
            "Android: consenti posizione sempre.",
            "Android: zezwól na lokalizację zawsze."),
            color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { status = "Nem nyíltak meg az Android engedélyek." }
        }) {
            Text(tr("Engedélyek megnyitása", "Open permissions",
                "Berechtigungen öffnen", "Ouvrir les autorisations",
                "Abrir permisos", "Apri autorizzazioni", "Otwórz uprawnienia"))
        }
    }
    if (!notifications && Build.VERSION.SDK_INT >= 33) {
        OutlinedButton(onClick = {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text(tr("Értesítések engedélyezése", "Allow notifications",
            "Benachrichtigungen erlauben", "Autoriser les notifications",
            "Permitir notificaciones", "Consenti notifiche",
            "Zezwól na powiadomienia")) }
    }
    val ready = runCatching { JarvisHomeConnect.load(context) != null }.getOrDefault(false)
    Button(enabled = enabled || hasHome && fine && background && notifications && ready,
        onClick = {
            if (enabled) {
                JarvisDepartureGuard.disable(context) { status = it }
                enabled = false
            } else {
                JarvisDepartureGuard.enable(context) { ok, result ->
                    enabled = ok
                    status = result
                }
            }
        }, modifier = Modifier.testTag("smart-home-departure-toggle")) {
        Text(if (enabled) tr("Figyelés kikapcsolása", "Disable departure check",
            "Deaktivieren", "Désactiver", "Desactivar", "Disattiva", "Wyłącz")
        else tr("Távozás ellenőrzésének bekapcsolása", "Enable departure check",
            "Aktivieren", "Activer", "Activar", "Attiva", "Włącz"))
    }
    if (status.isNotBlank()) Text(status, color = JarvisPalette.mainText)
    val last = remember(enabled, permissionRefresh) {
        JarvisDepartureGuard.lastReport(context)
    }
    if (last.isNotBlank()) Text(last, color = JarvisPalette.subtleText,
        style = MaterialTheme.typography.bodySmall)
    Text(tr(
        "Kell hozzá egy távolról is elérhető, biztonságos HTTPS Home Assistant kapcsolat. Ez kényelmi emlékeztető, nem riasztórendszer.",
        "Requires a remotely reachable secure HTTPS Home Assistant. This is a convenience reminder, not a security alarm.",
        "Benötigt sicheres externes HTTPS Home Assistant. Kein Sicherheitssystem.",
        "Nécessite Home Assistant HTTPS distant. Pas une alarme de sécurité.",
        "Requiere Home Assistant HTTPS remoto. No es una alarma.",
        "Serve Home Assistant HTTPS remoto. Non è un allarme.",
        "Wymaga zdalnego Home Assistant HTTPS. To nie alarm."
    ), color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
}
