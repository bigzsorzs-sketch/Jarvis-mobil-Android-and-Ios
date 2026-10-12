package ai.jarvis.mobile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.util.Locale

internal enum class AppHandoffResult { OPENED, SEARCH_OPENED, UNAVAILABLE }

internal object JarvisExternalApps {
    /** Search by format only: private document names and contents never enter the query. */
    fun viewerQuery(displayName: String, mime: String): String {
        val extension = JarvisAttachments.localExtension(displayName)
        return when {
            extension == "exe" -> "EXE file viewer"
            mime == "application/pdf" -> "PDF reader"
            extension in setOf("doc", "docx", "odt", "rtf") -> "Word document viewer"
            extension in setOf("xls", "xlsx", "ods") -> "Excel spreadsheet viewer"
            extension in setOf("ppt", "pptx", "odp") -> "PowerPoint viewer"
            mime.startsWith("video/") -> "video player " + extension
            mime.startsWith("audio/") -> "audio player " + extension
            mime.startsWith("image/") -> "image viewer " + extension
            extension in setOf("zip", "rar", "7z", "tar", "gz") -> "archive viewer " + extension
            extension == "bin" -> "file viewer"
            else -> extension.uppercase(Locale.ROOT) + " file viewer"
        }
    }

    fun search(context: Context, query: String): Boolean {
        val market = Uri.Builder().scheme("market").authority("search")
            .appendQueryParameter("q", query).appendQueryParameter("c", "apps").build()
        if (openSystemIntent(context, Intent(Intent.ACTION_VIEW, market)
                .setPackage("com.android.vending"))) return true
        val web = Uri.Builder().scheme("https").authority("play.google.com")
            .path("/store/search").appendQueryParameter("q", query)
            .appendQueryParameter("c", "apps").build()
        return openSystemIntent(context, Intent(Intent.ACTION_VIEW, web))
    }

    fun openFile(context: Context, attachment: JarvisAttachment): AppHandoffResult {
        if (!attachment.localFile.isFile) return AppHandoffResult.UNAVAILABLE
        return try {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".files", attachment.localFile
            )
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri(attachment.displayName, uri) }
            context.startActivity(intent)
            AppHandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            if (search(context, viewerQuery(attachment.displayName, attachment.mimeType)))
                AppHandoffResult.SEARCH_OPENED else AppHandoffResult.UNAVAILABLE
        } catch (_: Exception) {
            // An unreadable/deleted file or denied URI is not proof that an app is missing.
            AppHandoffResult.UNAVAILABLE
        }
    }

    fun notesPackages(manufacturer: String): List<String> {
        val packages = listOf("com.miui.notes", "com.samsung.android.app.notes",
            "com.google.android.keep")
        val vendor = manufacturer.lowercase(Locale.ROOT)
        return when {
            vendor.contains("samsung") -> listOf(packages[1], packages[2], packages[0])
            listOf("xiaomi", "redmi", "poco").any { it in vendor } -> packages
            else -> listOf(packages[2], packages[0], packages[1])
        }
    }

    fun openNotes(context: Context): AppHandoffResult {
        val manager = context.packageManager
        val actions = listOf("android.intent.action.CREATE_NOTE",
            "com.google.android.gms.actions.CREATE_NOTE")
        // Respect a compatible default handler, opening its main notes list when available.
        for (action in actions.take(1)) {
            val resolved = manager.resolveActivity(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (resolved != null && resolved != "android") {
                val home = manager.getLaunchIntentForPackage(resolved)
                if (home != null && openSystemIntent(context, home)) return AppHandoffResult.OPENED
            }
        }
        for (name in notesPackages(Build.MANUFACTURER)) {
            val home = manager.getLaunchIntentForPackage(name)
            if (home != null && openSystemIntent(context, home)) return AppHandoffResult.OPENED
        }
        for (action in actions) {
            if (openSystemIntent(context, Intent(action))) return AppHandoffResult.OPENED
        }
        return if (search(context, "notes app")) AppHandoffResult.SEARCH_OPENED
            else AppHandoffResult.UNAVAILABLE
    }
}
