package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings

/**
 * Libre 2 Plus NFC initiation must be performed by Abbott's validated
 * FreeStyle LibreLink app. Jarvis never intercepts Abbott NFC tags, emulates a
 * sensor, tries to decrypt protocol traffic, or claims access to live glucose.
 */
internal object JarvisLibreNfc {
    const val OFFICIAL_GB_PACKAGE = "com.freestylelibre.app.gb"

    fun hasNfc(context: Context): Boolean =
        runCatching { NfcAdapter.getDefaultAdapter(context) != null }.getOrDefault(false)

    fun isEnabled(context: Context): Boolean =
        runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }.getOrDefault(false)

    fun openNfcSettings(context: Context): Boolean =
        runCatching {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    /** Opens the verified GB app; offers its official Play Store listing if absent. */
    fun openOfficialApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(OFFICIAL_GB_PACKAGE)
        if (launch != null) {
            return runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
        val play = Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${OFFICIAL_GB_PACKAGE}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(play); true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${OFFICIAL_GB_PACKAGE}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
    }
}
