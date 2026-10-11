package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Invalidates the permission status whenever the activity resumes from system settings. */
internal object PermissionRefresh {
    var serial by mutableIntStateOf(0)
}

private fun hasPermission(context: Context, name: String): Boolean =
    ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

private fun openSettings(context: Context, action: String) {
    if (!openSystemIntent(context, Intent(action))) {
        openSystemIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)))
    }
}

private fun uriName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Kiválasztott fájl"
    } catch (_: Exception) { "Kiválasztott fájl" }
}

@android.annotation.SuppressLint("MissingPermission")
private fun lastLocation(context: Context): String {
    // Explicit permission checks are essential: GPS requires FINE; network
    // provider may use COARSE. Lint cannot trace permission checks through
    // provider selection, hence the narrowly scoped annotation.
    val precise = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val approximate = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!precise && !approximate) return "Ehhez helymeghatározási engedély szükséges."
    return try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = buildList {
            if (precise) add(LocationManager.GPS_PROVIDER)
            if (approximate || precise) add(LocationManager.NETWORK_PROVIDER)
        }
        val location = providers.mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) }
            catch (_: SecurityException) { null }
            catch (_: IllegalArgumentException) { null }
        }.maxByOrNull { it.time }
        if (location == null) "Nincs elérhető korábbi helyadat; kapcsold be a helymeghatározást."
        else {
            val uri = Uri.parse("geo:" + location.latitude + "," + location.longitude +
                "?q=" + location.latitude + "," + location.longitude)
            if (openSystemIntent(context, Intent(Intent.ACTION_VIEW, uri)))
                "Megnyitva az utolsó ismert pozíció. Ez nem feltétlenül az aktuális helyed."
            else "Nincs kompatibilis térképalkalmazás."
        }
    } catch (_: Exception) { "Helyadat jelenleg nem érhető el." }
}


private fun viewSelection(context: Context, uri: Uri): Boolean {
    return try {
        val type = context.contentResolver.getType(uri) ?: "*/*"
        openSystemIntent(context, Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: Exception) { false }
}

@Composable
private fun PermissionTile(title: String, description: String, status: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = JarvisPalette.panel)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = JarvisPalette.mainText, fontWeight = FontWeight.Bold)
            Text(description, color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
            Text(status, color = JarvisPalette.accent, style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = onClick) { Text("Beállítás / megnyitás") }
        }
    }
}

@Composable
internal fun PermissionCenter(context: Context, onDone: () -> Unit, onMessages: () -> Unit) {
    val check = PermissionRefresh.serial
    var status by remember { mutableStateOf("") }
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    val secure = remember(context) { SecurePrefs(context) }
    val selectedDocs = remember {
        mutableStateListOf<Uri>().apply {
            try {
                val saved = org.json.JSONArray(secure.read("selected_documents", "[]"))
                for (i in 0 until saved.length()) add(Uri.parse(saved.getString(i)))
            } catch (_: Exception) {}
        }
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PermissionRefresh.serial += 1
        status = if (it.values.any { granted -> granted }) "Helyhozzáférés megadva." else "Helyhozzáférés nincs megadva."
    }
    val microphoneRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PermissionRefresh.serial += 1
        status = if (it) "Mikrofon-hozzáférés engedélyezve." else "Nem engedélyezted a mikrofont."
    }
    val phoneRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PermissionRefresh.serial += 1
        status = if (it) "Telefonálás engedélyezve." else "Telefonálás nem engedélyezve."
    }
    val alertsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PermissionRefresh.serial += 1
        status = if (it) "Értesítések engedélyezve." else "Értesítések elutasítva."
    }
    val contactsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PermissionRefresh.serial += 1
        status = if (it) "Névjegyek engedélyezve." else "Névjegyhozzáférés elutasítva."
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        selectedPhotos.clear()
        selectedPhotos.addAll(uris)
        status = if (uris.isEmpty()) "Nem választottál ki képet." else
            "Kiválasztottál " + uris.size + " képet. Ezekhez ideiglenes hozzáférést kaptam."
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        status = if (uris.isEmpty()) "Nem választottál fájlt." else {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { /* read access for current selection still applies */ }
            }
            selectedDocs.clear()
            selectedDocs.addAll(uris)
            secure.write("selected_documents",
                org.json.JSONArray().apply { selectedDocs.forEach { put(it.toString()) } }.toString())
            "Hozzáférhető fájlok: " + uris.take(3).joinToString(", ") { uriName(context, it) }
        }
    }

    // This read forces recomposition on Activity.onResume, after the Settings screen closes.
    val permissionRefreshed = check >= 0
    val listener = permissionRefreshed &&
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val exactLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val approximateLocation = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val contacts = hasPermission(context, Manifest.permission.READ_CONTACTS)

    Text("Jarvis engedélyközpont", color = Color.White,
        style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "A telefonodhoz való hozzáférésről te döntesz. Minden engedély külön megadható vagy kihagyható. " +
            "Nem kell mindent elfogadnod a Jarvis használatához.",
        color = Color(0xFFB5C3D8)
    )
    Spacer(Modifier.height(14.dp))

    PermissionTile(
        "💬 WhatsApp, Messenger és üzenetértesítések",
        "Az Android külön értesítési hozzáférést kér. Ez rendszer szinten más appok értesítéseire is kiterjed; " +
            "Jarvis csak a támogatott üzenetappok friss értesítéseit dolgozza fel, helyben. " +
            "Felolvasás és felhasználó által jóváhagyott gyorsválasz, ha az app lehetővé teszi.",
        if (listener) "Engedélyezve" else "Nincs engedélyezve"
    ) { openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "📍 Pontos helyzet és térkép",
        "Csak akkor használjuk, amikor helyzetalapú funkciót kérsz. " +
            "Az Androidban választhatsz pontos vagy hozzávetőleges helyet.",
        when { exactLocation -> "Pontos helyzet engedélyezve"
            approximateLocation -> "Csak hozzávetőleges helyzet"
            else -> "Nincs engedély" }
    ) {
        locationRequest.launch(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = { status = lastLocation(context) }) {
        Text("Utolsó ismert hely megnyitása térképen")
    }
    Spacer(Modifier.height(10.dp))

    PermissionTile(
        "🖼️ Fotók és videók",
        "Az Android saját képválasztójában csak a kiválasztott képekhez adsz hozzáférést, " +
            "nem az egész galériához.",
        "Kiválasztáskor kér hozzáférést"
    ) { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    selectedPhotos.take(5).forEachIndexed { index, uri ->
        TextButton(onClick = { status = if (viewSelection(context, uri)) "Kép megnyitva." else "Nem nyitható meg a kép." }) {
            Text("Kiválasztott kép " + (index + 1) + " – megnyitás")
        }
    }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "📂 Dokumentumok és tárhely",
        "Android fájlválasztó: a saját magad által megjelölt dokumentumokhoz adsz hozzáférést. " +
            "A telefon összes fájljához nem kérünk korlátlan hozzáférést.",
        "Kiválasztott fájlokhoz engedély"
    ) { documentPicker.launch(arrayOf("*/*")) }
    selectedDocs.take(12).forEach { uri ->
        TextButton(onClick = { status = if (viewSelection(context, uri)) "Dokumentum megnyitva." else "A fájlhoz már nincs hozzáférés." }) {
            Text("📄 " + uriName(context, uri))
        }
    }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "👤 Névjegyek",
        "Csak a felhasználó jóváhagyásával. Telefonos és későbbi kommunikációs funkciókhoz.",
        if (contacts) "Engedélyezve" else "Nincs engedély"
    ) { contactsRequest.launch(Manifest.permission.READ_CONTACTS) }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "📞 Hangos telefonhívások",
        "Névjegy alapján hívás indítása csak külön CALL_PHONE engedéllyel és hangos igen/nem " +
            "megerősítéssel. Minden hívást neked kell jóváhagynod.",
        if (VoiceCalling.canCall(context)) "Telefon engedélyezve" else "Nincs telefonengedély"
    ) { phoneRequest.launch(Manifest.permission.CALL_PHONE) }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "📧 Email és Gmail",
        "Az emailírást most megnyithatod. A teljes Gmail-fiók olvasása " +
            "Google OAuth-bejelentkezést igényel, nem Android tárhelyengedélyt.",
        "Emailírás működik · Gmail-fiók nincs összekapcsolva"
    ) { status = if (openEmail(context)) "Az emailalkalmazás megnyílt." else "Nincs telepített emailalkalmazás." }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "🔔 Jarvis értesítések",
        "Az ébresztők és a hangfigyelés állapotjelzéséhez. Ez nem ad hozzáférést más alkalmazások üzeneteihez.",
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
            "Jarvis értesítések engedélyezve"
        else "Nincs értesítési engedély"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            alertsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        else status = "A régebbi Androidon nincs külön értesítési runtime engedély."
    }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "⏰ Jarvis pontos ébresztő",
        "A hanggal beállított Jarvis-ébresztő az alkalmazás bezárása után is jelezhet. " +
            "A pontos ébresztéshez Android-különengedély és értesítési engedély szükséges. " +
            "Jelenleg hangos értesítés, még nem teljes képernyős óraalkalmazás.",
        if (JarvisAlarms.canSchedule(context)) "Pontos ébresztés engedélyezve"
        else "Különengedély szükséges"
    ) { JarvisAlarms.requestExactPermission(context) }
    Spacer(Modifier.height(10.dp))
    PermissionTile(
        "🎙️ Hey Jarvis – mikrofon és hangvezérlés",
        "Az engedély önmagában nem kapcsolja be a mikrofont. A Főoldalon külön indítható " +
            "egy látható, maximum 15 perces hangfigyelő munkamenet. A beszédfelismerő " +
            "Android-szolgáltatója internetet is használhat.",
        if (hasPermission(context, Manifest.permission.RECORD_AUDIO))
            "Mikrofon engedélyezve · Hangos mód külön indítható"
        else "Nincs mikrofonengedély"
    ) { microphoneRequest.launch(Manifest.permission.RECORD_AUDIO) }
    Spacer(Modifier.height(10.dp))
    if (listener) {
        Button(onClick = onMessages) { Text("📨 Beérkező üzenetek") }
        Spacer(Modifier.height(8.dp))
    }
    if (status.isNotBlank()) Text(status, color = Color(0xFF60D5E5))
    Spacer(Modifier.height(12.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text("Folytatás a Jarvis főoldalára")
    }
    Spacer(Modifier.height(20.dp))
}
