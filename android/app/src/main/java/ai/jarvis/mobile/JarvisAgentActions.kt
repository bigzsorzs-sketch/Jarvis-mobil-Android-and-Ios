package ai.jarvis.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The model never invents executable arguments; every step comes from the user. */
internal enum class AgentKind { PAGE, THEME, LANGUAGE, PHONE_APP, NAVIGATION }
internal data class AgentStep(val index: Int, val input: String, val kind: AgentKind,
    val target: String)
internal enum class AgentStatus { VERIFIED, HANDOFF, FAILED, CANCELLED }
internal data class AgentReceipt(val index: Int, val status: AgentStatus, val detail: String)

internal object JarvisAgentActions {
    private val split = Regex("""(?iu)\s*,?\s+(?:majd|aztán|és\s+utána|and\s+then|then|és)\s+""")
    private val dangerous = Regex("""(?iu)\b(?:hívd|hivd|call|küldj|kuldd|send|töröld|torold|delete|fizess|pay|transfer|utalj|vásárolj|vasarolj|install|telepíts|telepits|engedélyezz|engedelyezz|approve|confirm|password|jelszó|jelszo|api\s*key|api\s*kulcs)\b""")

    /** At most three non-destructive steps, each independently parseable locally. */
    fun eligible(raw: String): List<AgentStep>? {
        val original = JarvisNativeActions.stripped(raw)
        if (original.length !in 16..500 || '?' in original || dangerous.containsMatchIn(original))
            return null
        val parts = split.split(original)
        if (parts.size !in 2..3 || parts.any { it.isBlank() || it.length > 230 }) return null
        val steps = parts.mapIndexed { i, part ->
            val input = part.trim().trimEnd('.', '!', ',').trim()
            if (input.isEmpty() || dangerous.containsMatchIn(input)) return null
            val setting = JarvisLocalPreferences.parse(input)
            val page = JarvisCapabilities.localPageToOpen(input)
            val app = JarvisPhoneActions.requestedAction(input)
            val navigation = VoiceNavigation.destinationFrom(input)
            if (listOf(setting != null, page != null, app != null, navigation != null)
                    .count { it } != 1) return null
            when {
                setting is JarvisLocalPreferences.Command.Theme ->
                    AgentStep(i + 1, input, AgentKind.THEME, if (setting.dark) "dark" else "light")
                setting is JarvisLocalPreferences.Command.Language ->
                    AgentStep(i + 1, input, AgentKind.LANGUAGE, setting.selected.tag)
                page != null && page in (JarvisToolCatalog.all + JarvisToolCatalog.subpages)
                    .map { it.route } -> AgentStep(i + 1, input, AgentKind.PAGE, page)
                app != null -> AgentStep(i + 1, input, AgentKind.PHONE_APP, app)
                navigation != null -> AgentStep(i + 1, input, AgentKind.NAVIGATION, navigation)
                else -> return null
            }
        }
        // External handoffs may only occur at the end; their outcome is not observable.
        if (steps.dropLast(1).any { it.kind in setOf(AgentKind.PHONE_APP, AgentKind.NAVIGATION) })
            return null
        return steps
    }

    /** Exact list and order only: no extra actions, parameters or commands. */
    fun validModelPlan(raw: String, authorized: List<AgentStep>): Boolean {
        if (raw.length !in 12..1200 || authorized.size !in 2..3) return false
        val clean = raw.trim().removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val obj = try { JSONObject(clean) } catch (_: Exception) { return false }
        if (obj.length() != 1 || !obj.has("steps")) return false
        val a = obj.optJSONArray("steps") ?: return false
        if (a.length() != authorized.size) return false
        return authorized.indices.all { i ->
            val entry = a.optJSONObject(i) ?: return@all false
            entry.length() == 2 && entry.opt("index") is Number &&
                entry.opt("action") is String && entry.optInt("index") == i + 1 &&
                entry.optString("action") == authorized[i].kind.name
        }
    }

    fun authorized(original: String, step: AgentStep): Boolean =
        eligible(original)?.getOrNull(step.index - 1) == step

    private fun textOnly(o: JarvisAiOutcome): Boolean =
        o.route == null && o.deviceAction == null && o.nativeAction == null &&
            o.callTarget == null && o.messageDraft == null

    /**
     * Foreground operation only. Each AI turn can stop, not add steps.
     * Every receipt is validated on Android before asking the model to continue.
     * Changing the active user command prevents further operations.
     */
    suspend fun run(context: Context, original: String, language: String,
        isCurrent: () -> Boolean, execute: (AgentStep) -> AgentReceipt): String {
        val steps = eligible(original)
            ?: return "Nem támogatott műveletsor; nem indítottam semmit."
        if (!runCatching { JarvisAiGateway.configured(context) }.getOrDefault(false))
            return "A több lépéses AI-feladathoz engedélyezett AI-kapcsolat szükséges."
        val plan = "Tervezési ellenőrzés. Kizárólag JSON-t válaszolj, semmilyen eszközt ne " +
            "használj. Az Android ellenőrizte a felhasználó eredeti parancsait. " +
            "Tartsd meg minden lépés sorrendjét és típusát; " +
            "formátum: {\"steps\":[{\"index\":1,\"action\":\"THEME\"}]}. " +
            "Az engedélyezett lépések: " +
            steps.joinToString("; ") { it.index.toString() + ":" + it.kind.name }
        currentCoroutineContext().ensureActive()
        val proposed = JarvisAiGateway.askStructured(context, plan, language)
        if (!isCurrent()) return "Új parancs érkezett, a műveletsor megszakadt."
        if (!textOnly(proposed) || !validModelPlan(proposed.message, steps))
            return "A GPT-5.2 nem adott érvényes tervet, ezért nem történt művelet."
        val receipts = mutableListOf<AgentReceipt>()
        for ((position, step) in steps.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent() || !authorized(original, step))
                return report(receipts, "A feladat megszakadt vagy a parancs megváltozott.")
            val receipt = withContext(Dispatchers.Main.immediate) {
                if (!isCurrent() || !authorized(original, step))
                    AgentReceipt(step.index, AgentStatus.CANCELLED, "Új kérés érkezett.")
                else execute(step)
            }
            receipts.add(receipt)
            if (receipt.status != AgentStatus.VERIFIED)
                return report(receipts, if (receipt.status == AgentStatus.HANDOFF)
                    "Külső alkalmazásnak átadva; az ottani végrehajtás nem ellenőrzött."
                    else "A következő műveletet nem indítottam el.")
            if (position < steps.lastIndex) {
                val next = steps[position + 1]
                val decision = "Állapotellenőrzés. Egy helyi Android-művelet ténylegesen " +
                    "VERIFIED státuszt kapott: " + step.kind.name +
                    ". Következő engedélyezett művelet: " + next.kind.name +
                    ". Válaszolj pontosan CONTINUE vagy STOP szöveggel; " +
                    "nem választhatsz új műveletet, ne használj eszközt."
                currentCoroutineContext().ensureActive()
                val outcome = JarvisAiGateway.askStructured(context, decision, language)
                if (!isCurrent()) return report(receipts, "Új kérés megszakította.")
                if (!textOnly(outcome) || outcome.message.trim() != "CONTINUE")
                    return report(receipts, "Az AI nem engedélyezte a következő lépést.")
            }
        }
        return report(receipts, "A helyben igazolható műveletek befejeződtek.")
    }

    private fun report(receipts: List<AgentReceipt>, message: String): String =
        (if (receipts.isEmpty()) "" else receipts.joinToString("\n") {
            it.index.toString() + ". [" + it.status.name + "] " + it.detail.take(150)
        } + "\n") + message
}
