package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale

/** User-directed Android settings *screens*. Never toggles Wi-Fi, Bluetooth,
 * GPS, permissions, airplane mode or security settings itself.
 */
internal object JarvisSystemPanels {
    const val WIFI = "wifi_settings"
    const val BLUETOOTH = "bluetooth_settings"
    const val DISPLAY = "display_settings"
    const val LOCATION = "location_settings"
    const val BATTERY = "battery_settings"
    const val APP_DETAILS = "jarvis_app_settings"

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun identify(raw: String): String? {
        if (!JarvisAiTools.userAskedToOpen(raw) || '?' in raw || raw.length > 240) return null
        val s = norm(raw)
        if (Regex("""\b(ne|nem|dont|never|torold|kuldd|hivd|pay|fizess|and|majd|aztan)\b""")
                .containsMatchIn(s)) return null
        if (!listOf("beallitas", "settings", "panel", "engedely")
                .any { it in s }) return null
        val found = listOfNotNull(
            WIFI.takeIf { "wifi" in s || "wi fi" in s || "internet" in s },
            BLUETOOTH.takeIf { "bluetooth" in s },
            DISPLAY.takeIf { "kijelzo" in s || "display" in s || "screen settings" in s },
            LOCATION.takeIf { "helymeghataroz" in s || "gps" in s || "location" in s },
            BATTERY.takeIf { "akkumulator" in s || "battery" in s },
            APP_DETAILS.takeIf { ("jarvis" in s || "alkalmazasadatok" in s) &&
                ("engedely" in s || "app settings" in s || "alkalmazasadatok" in s) }
        )
        return found.singleOrNull()
    }

    fun open(context: Context, kind: String, original: String): String {
        if (identify(original) != kind) return "A panel nem egyezik a parancsoddal. Nem történt művelet."
        val intent = when (kind) {
            WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
            BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName))
            else -> return "Nem támogatott beállítás."
        }
        return if (openSystemIntent(context, intent))
            "Megnyitottam a kért Android-beállításokat. A módosítást neked kell jóváhagynod."
        else "A telefon nem engedte megnyitni ezt a beállítást."
    }
}
