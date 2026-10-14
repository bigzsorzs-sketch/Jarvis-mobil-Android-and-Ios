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
            .replace("ł", "l").replace("ß", "ss").replace("ø", "o")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    /** Seven-language exact app launcher. A pure on-device intent parser,
     * independent of the AI, never grants access inside a third-party app.
     */
    fun requestedLabel(raw: String): String? {
        if (raw.length !in 4..240 || '?' in raw) return null
        val clean = normalize(raw)
            .removePrefix("hey jarvis ").removePrefix("jarvis ")
            .removePrefix("kerlek ").removePrefix("please ")
            .removePrefix("bitte ").removePrefix("s il te plait ")
            .removePrefix("por favor ").removePrefix("per favore ")
            .removePrefix("prosze ").trim()
        // Fail closed on negation and requests that contain another action.
        if (Regex("""\b(ne|nem|dont|never|without|soha|torold|delete|erase|kuldd|send|hivd|call|fizess|pay|nicht|kein|keine|pas|jamais|no|nunca|non|sans|senza|nie|usuwa|envoie|envia|invia|rufe|llama|chiama)\b""")
                .containsMatchIn(clean)) return null
        if (Regex("""\b(es|and|majd|then|aztan|und|puis|ensuite|despues|luego|dopo|poi|dann|potem|pozniej)\b""")
                .containsMatchIn(clean)) return null
        val match = Regex(
            """^(?:nyisd meg|nyitnad meg|inditsd el|inditsd|megnyitnad|open|launch|start|offne|starte|starten|ouvre|ouvrez|lance|lancez|abre|abrir|inicia|iniciar|apri|aprite|avvia|avviare|otworz|uruchom)\s+(?:(?:a|az|the|die|den|das|la|le|el|lo|il|l|un|une|una|aplicacion|application|applicazione|aplikacje|aplikacje|aplikacja|app)\s+){0,2}(.+)$"""
        ).matchEntire(clean) ?: return null
        return match.groupValues[1]
            .removeSuffix(" alkalmazast").removeSuffix(" appot")
            .removeSuffix(" application").removeSuffix(" applicazione")
            .removeSuffix(" aplicacion").removeSuffix(" aplikacje")
            .removeSuffix(" app")
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

    /** The original user command was already classified once by Android.
     * Recheck launcher metadata exactly once at the real handoff.
     */
    fun openSelected(context: Context, choice: AppSelection): String {
        val language = JarvisLanguageSettings.load(context)
        return when (choice) {
            is AppSelection.Found -> if (open(context, choice.app)) {
                val label = choice.app.label
                when (language) {
                    JarvisLanguage.HUNGARIAN ->
                        "Az Androidnak átadtam a(z) $label megnyitását. Az alkalmazáson belüli műveleteket nem hajtottam végre."
                    JarvisLanguage.ENGLISH ->
                        "I asked Android to open $label. I have not completed any actions inside that app."
                    JarvisLanguage.GERMAN ->
                        "Ich habe Android gebeten, $label zu öffnen. Innerhalb der App wurde keine Aktion ausgeführt."
                    JarvisLanguage.FRENCH ->
                        "J'ai demandé à Android d'ouvrir $label. Aucune action dans l'application n'a été effectuée."
                    JarvisLanguage.SPANISH ->
                        "He pedido a Android que abra $label. No he realizado acciones dentro de la aplicación."
                    JarvisLanguage.ITALIAN ->
                        "Ho chiesto ad Android di aprire $label. Non ho eseguito azioni all'interno dell'app."
                    JarvisLanguage.POLISH ->
                        "Poprosiłem Androida o otwarcie $label. Nie wykonałem działań wewnątrz aplikacji."
                }
            } else when (language) {
                JarvisLanguage.HUNGARIAN -> "Az Android nem engedte megnyitni ezt az alkalmazást."
                else -> "Android could not open the requested application."
            }
            is AppSelection.Ambiguous -> when (language) {
                JarvisLanguage.HUNGARIAN ->
                    "Több azonos nevű alkalmazás van. Válassz az Alkalmazások menüben."
                else -> "Several apps share that name. Select one on the Apps page."
            }
            AppSelection.Missing -> when (language) {
                JarvisLanguage.HUNGARIAN -> "Nincs ilyen nevű, megnyitható alkalmazás telepítve."
                else -> "I couldn't find an installed, launchable app with that exact name."
            }
            AppSelection.NotARequest -> when (language) {
                JarvisLanguage.HUNGARIAN -> "Ez nem egyértelmű alkalmazásmegnyitási kérés."
                else -> "That isn't a clear request to open an application."
            }
        }
    }

    fun openRequested(context: Context, original: String): String =
        openSelected(context, choose(original, list(context)))
}
