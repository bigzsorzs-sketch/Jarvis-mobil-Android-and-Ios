package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import java.text.Normalizer
import java.util.Locale

/** Launchable Android apps only; never controls their private screens or data. */
internal data class JarvisInstalledApp(val label: String, val packageName: String, val activityClass: String)

internal sealed class AppSelection {
    data object NotARequest : AppSelection()
    data object Missing : AppSelection()
    data class Ambiguous(val names: List<String>) : AppSelection()
    data class Found(val app: JarvisInstalledApp) : AppSelection()
}

internal object JarvisInstalledApps {
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    /** Strict one-app launch requests. Reject negation, questions and multi-step commands. */
    fun requestedLabel(raw: String): String? {
        if (raw.length > 240 || '?' in raw) return null
        val clean = normalize(raw)
            .removePrefix("hey jarvis ").removePrefix("jarvis ")
            .removePrefix("kerlek ").removePrefix("please ").trim()
        if (Regex("""\b(ne|nem|dont|never|without|soha|torold|delete|erase|kuldd|send|hivd|call|fizess|pay)\b""")
                .containsMatchIn(clean)) return null
        if (Regex("""\b(es|and|majd|then|aztan)\b""").containsMatchIn(clean)) return null
        val match = Regex("""^(?:nyisd meg|nyitnad meg|inditsd el|inditsd|megnyitnad|open|launch|start)\s+(?:(?:a|az|the)\s+)?(.+)$""")
            .matchEntire(clean) ?: return null
        return match.groupValues[1].removeSuffix(" alkalmazast")
            .removeSuffix(" appot").removeSuffix(" app")
            .trim().takeIf { it.length in 2..80 }
    }

    /** Hungarian suffixes apply only to the exact, full Android app label. */
    fun matchesLabel(target: String, actualLabel: String): Boolean {
        val label = normalize(actualLabel)
        if (label.length < 2) return false
        return target in setOf(label, label + " t", label + " ot", label + " at", label + " et",
            label + "t", label + "ot", label + "at", label + "et", label + " alkalmazas")
    }

    fun choose(request: String, apps: List<JarvisInstalledApp>): AppSelection {
        val name = requestedLabel(request) ?: return AppSelection.NotARequest
        val matches = apps.filter { matchesLabel(name, it.label) }
            .distinctBy { it.packageName to it.activityClass }
        return when {
            matches.isEmpty() -> AppSelection.Missing
            matches.size > 1 -> AppSelection.Ambiguous(
                matches.map { it.label + " (" + it.packageName + ")" })
            else -> AppSelection.Found(matches.single())
        }
    }

    /** Package visibility is limited to launchable apps, not all installed packages. */
    fun list(context: Context): List<JarvisInstalledApp> {
        val pm = context.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val installed = pm.queryIntentActivities(launch, 0)
        return installed.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.exported) return@mapNotNull null
            val label = info.loadLabel(pm).toString().trim()
            if (label.isEmpty()) return@mapNotNull null
            JarvisInstalledApp(label, activity.packageName, activity.name)
        }.distinctBy { it.packageName to it.activityClass }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun open(context: Context, app: JarvisInstalledApp): Boolean {
        // Re-resolve immediately before use. Never trust an AI-chosen class name.
        val current = list(context).singleOrNull {
            it.packageName == app.packageName && it.activityClass == app.activityClass
        } ?: return false
        return try {
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(current.packageName, current.activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        } catch (_: Exception) { false }
    }

    fun openRequested(context: Context, original: String): String =
        when (val choice = choose(original, list(context))) {
            is AppSelection.Found -> if (open(context, choice.app))
                "Átadtam a(z) " + choice.app.label +
                    " alkalmazás megnyitását az Androidnak. Az alkalmazáson belüli feladatot nem végeztem el."
                else "Az alkalmazást az Android nem engedte megnyitni."
            is AppSelection.Ambiguous ->
                "Több azonos nevű alkalmazás található. Válassz az Alkalmazások menüben."
            AppSelection.Missing -> "Nincs ilyen nevű, megnyitható alkalmazás telepítve."
            AppSelection.NotARequest -> "Ez nem egyértelmű alkalmazásmegnyitási kérés."
        }
}
