package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(title, color = JarvisPalette.subtleText, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
}

@Composable
internal fun JarvisSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(JarvisPalette.panel), content = content)
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = JarvisPalette.subtleText.copy(alpha = 0.15f)
    )
}

@Composable
internal fun JarvisSettingsRow(icon: String, title: String, detail: String, onClick: () -> Unit) {
    RegisterJarvisControl(title, click = onClick)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp, color = JarvisPalette.accent,
            modifier = Modifier.width(32.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = JarvisPalette.mainText, fontSize = 15.sp)
            if (detail.isNotBlank()) Text(detail, color = JarvisPalette.subtleText,
                fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = JarvisPalette.subtleText, fontSize = 24.sp)
    }
}

@Composable
internal fun JarvisSettingsScreen(
    context: Context, isDark: Boolean, onToggleTheme: () -> Unit, onVoice: () -> Unit,
    onMore: () -> Unit, onSmartHome: () -> Unit,
    language: JarvisLanguage
) {
    SettingsSectionTitle("Általános")
    JarvisSettingsGroup {
        JarvisSettingsRow(if (isDark) "☾" else "☀", "Megjelenés",
            if (isDark) "Sötét" else "Világos", onToggleTheme)
        SettingsDivider()
        JarvisSettingsRow("≋", "Hangvezérlés",
            if (HandsFreeStatus.running) "Aktív" else "Kikapcsolva", onVoice)
        SettingsDivider()
        val placesTitle = when (language) {
            JarvisLanguage.HUNGARIAN -> "Mentett helyek és otthoni cím"
            JarvisLanguage.ENGLISH -> "Saved places and home address"
            JarvisLanguage.GERMAN -> "Gespeicherte Orte und Zuhause"
            JarvisLanguage.FRENCH -> "Lieux enregistrés et domicile"
            JarvisLanguage.SPANISH -> "Lugares guardados y domicilio"
            JarvisLanguage.ITALIAN -> "Luoghi salvati e indirizzo di casa"
            JarvisLanguage.POLISH -> "Zapisane miejsca i adres domu"
        }
        val placesHelp = when (language) {
            JarvisLanguage.HUNGARIAN -> "Otthoni cím, Google Takeout-importálás"
            JarvisLanguage.ENGLISH -> "Home address, Google Takeout import"
            JarvisLanguage.GERMAN -> "Heimadresse, Google Takeout-Import"
            JarvisLanguage.FRENCH -> "Adresse personnelle, import Google Takeout"
            JarvisLanguage.SPANISH -> "Dirección de casa, importar Google Takeout"
            JarvisLanguage.ITALIAN -> "Indirizzo di casa, importazione Google Takeout"
            JarvisLanguage.POLISH -> "Adres domu, import Google Takeout"
        }
        JarvisSettingsRow("➤", placesTitle, placesHelp, onMore)
        SettingsDivider()
        JarvisSettingsRow("⌂", "Smart Home",
            "Home Assistant: eszközök, lámpák, TV és háztartási gépek állapota", onSmartHome)
    }
    Spacer(Modifier.height(16.dp))
    Spacer(Modifier.height(20.dp))
    // GitHub updates remain explicitly user-controlled and never wipe local data.
    JarvisUpdatePanel(context, language)
    Spacer(Modifier.height(16.dp))
    SettingsSectionTitle("Telefon-hozzáférés")
    PermissionCenter(context, onDone = {}, showContinue = false, showHeading = false)
    Spacer(Modifier.height(14.dp))
    if (!BuildConfig.IS_PLAY_DISTRIBUTION) {
        SettingsSectionTitle("Android képernyővezérlés — opcionális")
        JarvisSettingsGroup {
            JarvisSettingsRow("◎", "Képernyővezérlés engedélyezése",
                "Az Android Beállításokban, külön jóváhagyással. Vissza, Főképernyő, görgetés és egyértelmű gombok.") {
                JarvisScreenControl.openPermissionSettings(context)
            }
        }
        Text("Csak külön kimondott vagy leírt műveletet hajt végre. Külső alkalmazás tartalmát nem küldi az AI-nak. Nem hagy jóvá fizetést, törlést vagy más érzékeny műveletet.",
            color = JarvisPalette.subtleText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp))
    }
    Spacer(Modifier.height(16.dp))
    SettingsSectionTitle("Adatvédelem és helyi adatok")
    JarvisSettingsGroup {
        JarvisSettingsRow("▣", "Saját adatok kezelése",
            "Android alkalmazásadatok: helyi jegyzetek, vércukornapló, mentett kapcsolat") {
            // Android's own Clear storage is an explicit user action and
            // deletes the Keystore-linked local data; never claim to wipe remotely.
            openJarvisAppSettings(context)
        }

    }
    Spacer(Modifier.height(16.dp))
    // Explicit user-chosen cloud destination, never a background export.
    JarvisBackupSettings(context, language)
    SettingsDivider()
    JarvisSettingsRow("ⓘ",
        if (language == JarvisLanguage.HUNGARIAN) "Adatvédelmi szabályzat" else "Privacy policy",
        "Jarvis Mobile – Google Play és GitHub") {
        openSystemIntent(context, android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/blob/main/docs/PRIVACY_POLICY.md")))
    }
    Spacer(Modifier.height(8.dp))
    Text("A Jarvis AI-kérdések felhőbe küldését az AI-adatvédelem és hozzáférés menüpontban " +
        "engedélyezheted vagy a Leválasztás gombbal visszavonhatod. " +
        "Az alkalmazás adatainak törlését az Android Alkalmazásadatok / Tárhely " +
        "menüjében indíthatod; ez visszavonhatatlanul törli a helyi adatokat.",
        color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(12.dp))
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) { "" }
    Text("Jarvis Mobile " + version, color = JarvisPalette.subtleText, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(12.dp))
}

