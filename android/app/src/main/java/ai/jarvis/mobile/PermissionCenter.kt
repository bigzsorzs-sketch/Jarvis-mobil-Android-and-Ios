package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.Manifest
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Recheck status when returning from the Android permission or settings UI. */
internal object PermissionRefresh {
    var serial by mutableIntStateOf(0)
}

private fun hasPermission(context: Context, name: String): Boolean =
    ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

internal fun openJarvisAppSettings(context: Context) {
    openSystemIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + context.packageName)))
}

private fun notificationSettings(context: Context) {
    if (!openSystemIntent(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)))
        openJarvisAppSettings(context)
}

/** Native Android dialogs request grants. No imitation consent screens or automatic microphone use. */
@Composable
internal fun PermissionCenter(
    context: Context,
    onDone: () -> Unit,
    showContinue: Boolean = true,
    showHeading: Boolean = true
) {
    val refresh = PermissionRefresh.serial
    var status by remember { mutableStateOf("") }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        PermissionRefresh.serial += 1
        status = if (grants.values.all { it }) "Engedélyek frissítve."
            else "A kihagyott engedélyek később is megadhatók."
    }
    fun grant(vararg names: String) {
        val missing = RuntimePermissionPolicy.missing(names.toList(),
            names.filter { hasPermission(context, it) }.toSet())
        if (missing.isEmpty()) openJarvisAppSettings(context)
        else request.launch(missing.toTypedArray())
    }
    val listener = remember(refresh) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val precise = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val approximate = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val telephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    if (showHeading) {
        Text("Hozzáférés a telefonhoz", color = JarvisPalette.mainText,
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
    }
    JarvisSettingsGroup {
        JarvisSettingsRow("🎙", "Mikrofon",
            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) "Engedélyezve" else "Nincs engedély") {
            grant(Manifest.permission.RECORD_AUDIO)
        }
        SettingsDivider()
        JarvisSettingsRow("♧", "Helymeghatározás", when {
            precise -> "Pontos hely"
            approximate -> "Hozzávetőleges hely"
            else -> "Nincs engedély"
        }) { grant(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION) }
        SettingsDivider()
        JarvisSettingsRow("♙", "Névjegyek",
            if (hasPermission(context, Manifest.permission.READ_CONTACTS)) "Engedélyezve" else "Nincs engedély") {
            grant(Manifest.permission.READ_CONTACTS)
        }
        if (telephony) {
            SettingsDivider()
            JarvisSettingsRow("☎", "Telefonhívások",
                if (hasPermission(context, Manifest.permission.CALL_PHONE)) "Engedélyezve" else "Nincs engedély") {
                grant(Manifest.permission.CALL_PHONE)
            }
        }
        SettingsDivider()
        JarvisSettingsRow("♧", "Jarvis-értesítések", if (notifications) "Engedélyezve" else "Kikapcsolva") {
            if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
                grant(Manifest.permission.POST_NOTIFICATIONS)
            else notificationSettings(context)
        }
    }
    Spacer(Modifier.height(16.dp))
    SettingsSectionTitle("Különleges hozzáférések")
    Text("Az értesítési hozzáférés Android-szinten más alkalmazások értesítéseit is " +
        "láthatóvá teszi. Jarvis csak az ismert üzenetalkalmazások friss értesítéseit " +
        "dolgozza fel; legfeljebb 30 előnézetet tart ideiglenesen a memóriában. " +
        "Az üzeneteket nem továbbítja az AI-nak. A hozzáférést az Android " +
        "beállításaiban bármikor kikapcsolhatod.",
        style = MaterialTheme.typography.bodySmall,
        color = JarvisPalette.subtleText)
    Spacer(Modifier.height(8.dp))
    JarvisSettingsGroup {
        JarvisSettingsRow("▢", "Értesítési hozzáférés",
            (if (listener) "Engedélyezve" else "Kikapcsolva") + " · más alkalmazások") {
            if (!openSystemIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                openJarvisAppSettings(context)
        }
        SettingsDivider()
        if (!BuildConfig.IS_PLAY_DISTRIBUTION) {
            JarvisSettingsRow("◷", "Pontos ébresztés",
                if (JarvisAlarms.canSchedule(context)) "Engedélyezve" else "Nincs engedély") {
                if (Build.VERSION.SDK_INT >= 31) JarvisAlarms.requestExactPermission(context)
                else openJarvisAppSettings(context)
            }
            SettingsDivider()
        }
        JarvisSettingsRow("⚙", "Android-engedélyek", "Kezelés a telefon beállításaiban") {
            openJarvisAppSettings(context)
        }
    }
    if (status.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(status, color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
    }
    if (showContinue) {
        Spacer(Modifier.height(20.dp))
        ControlledButton(onClick = onDone, modifier = Modifier.fillMaxWidth(), commandLabel = "Folytatás") { Text("Folytatás") }
    }
    Spacer(Modifier.height(16.dp))
}
