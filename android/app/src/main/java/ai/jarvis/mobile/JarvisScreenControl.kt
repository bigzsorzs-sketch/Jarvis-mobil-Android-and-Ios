package ai.jarvis.mobile

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import java.text.Normalizer
import java.util.Locale

/**
 * Optional, manually enabled Android accessibility bridge.
 * ONE explicit user command -> ONE deterministic UI action. No AI agent,
 * autonomous navigation, background polling or private screen upload.
 * Sensitive confirmations and financial actions are never automated.
 */
internal object JarvisScreenControl {
    sealed class Action {
        data object Back : Action()
        data object Home : Action()
        data object Recents : Action()
        data object Notifications : Action()
        data object QuickSettings : Action()
        data object ScrollDown : Action()
        data object ScrollUp : Action()
        data class TapLabel(val value: String) : Action()
    }

    private fun normalized(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace("ł", "l").replace("ß", "ss")
            .replace(Regex("""[^a-z0-9 ]+"""), " ")
            .trim().replace(Regex("""\s+"""), " ")

    /** A click on a third-party screen is not proof of what it will do.
     * Never automate labels that could submit, authorize, pay, erase, install,
     * change permissions or approve a transaction. All seven UI languages
     * are normalized before checking, including Hungarian and Polish accents.
     * This is an explicit deny gate, not a permission to bypass Android prompts.
     */
    private val blockedWords = setOf(
        // Generic confirmation or next-step labels: the adjacent screen may be sensitive.
        "ok", "yes", "igen", "rendben", "continue", "next", "accept", "agree",
        "done", "finish", "save", "mentes", "folytatas", "tovabb", "elfogadom",
        "kesz", "jo", "mentesek", "speichern", "enregistrer", "guardar",
        "salva", "zapisz", "confirmar", "accepter", "weiter", "suivant", "continuer",
        "aceptar", "continuar", "siguiente", "accetta", "continua",
        "prosegui", "akceptuj", "dalej", "potwierdz", "zatwierdz",
        // Payments, messages, deletion, authentication, permissions, apps.
        "pay", "payment", "purchase", "buy", "subscribe", "transfer",
        "send", "submit", "delete", "remove", "erase", "confirm", "approve",
        "grant", "allow", "uninstall", "install", "password", "pin", "otp",
        "checkout", "authorize", "authorization", "login",
        "fizetes", "fizess", "fizetek", "vasarlas", "vasarol", "utalas",
        "atutalas", "kuldes", "kuldd", "torles", "torold", "jovahagyas",
        "jovahagy", "engedelyezes", "engedelyez", "telepites",
        "eltavolitas", "hivas", "hivd", "bejelentkezes",
        "bezahlen", "zahlen", "zahlung", "kaufen", "uberweisen",
        "senden", "loschen", "entfernen", "bestatigen", "erlauben",
        "genehmigen", "installieren", "deinstallieren", "anmelden",
        "payer", "paiement", "acheter", "achat", "envoyer",
        "supprimer", "effacer", "confirmer", "autoriser", "installer",
        "desinstaller", "connexion",
        "pagar", "pago", "comprar", "compra", "enviar", "borrar",
        "eliminar", "aprobar", "autorizar", "permitir", "instalar",
        "desinstalar", "iniciar",
        "pagare", "pagamento", "acquistare", "acquista", "comprare",
        "inviare", "invia", "eliminare", "cancellare", "confermare",
        "approvare", "autorizzare", "consentire", "installare",
        "disinstallare", "accedi",
        "zaplac", "platnosc", "kup", "kupic", "wyslij", "usun",
        "usuwanie", "skasuj", "potwierdzic", "autoryzuj",
        "zezwol", "zezwolic", "instaluj", "odinstaluj", "zaloguj"
    )
    private val blockedPrefixes = listOf(
        "payment", "purchas", "transferr", "authoriz", "delet",
        "fizet", "vasar", "atutal", "jovahagy", "engedelyez", "telepit",
        "bezah", "zahl", "kauf", "uberweis", "losch", "bestatig",
        "paiem", "achet", "supprim", "autoris", "compr", "paga", "pago",
        "elimin", "approv", "acquist", "cancell", "conferm",
        "zaplac", "platn", "potwierdz", "odinstal"
    )

    internal fun unsafeTapLabel(label: String): Boolean {
        val tokens = normalized(label).split(' ').filter { it.isNotBlank() }
        return tokens.isEmpty() || tokens.any { word ->
            word in blockedWords || blockedPrefixes.any { word.startsWith(it) }
        }
    }

    fun parse(command: String): Action? {
        val raw = normalized(command).removePrefix("hey jarvis ")
            .removePrefix("jarvis ").removePrefix("kerlek ").trim()
        if (raw.isBlank() || raw.length > 140 || raw.startsWith("ne ") ||
            raw.startsWith("nem ") || raw.startsWith("dont ") ||
            raw.startsWith("do not ") || raw.contains(" es ") ||
            raw.contains(" and ") || raw.contains(" majd ")) return null
        return when (raw) {
            "vissza", "menj vissza", "lepes vissza", "go back" -> Action.Back
            "kezdokepernyo", "menj a kezdokepernyore", "android fooldal",
            "go home", "home screen" -> Action.Home
            "alkalmazasvalto", "legutobbi alkalmazasok", "open recents" -> Action.Recents
            "ertesitesek lehuzasa", "nyisd meg az ertesiteseket",
            "show notifications", "open notifications" -> Action.Notifications
            "gyorsbeallitasok", "nyisd meg a gyorsbeallitasokat",
            "open quick settings" -> Action.QuickSettings
            "gorgess le", "gorgetes le", "scroll down" -> Action.ScrollDown
            "gorgess fel", "gorgetes fel", "scroll up" -> Action.ScrollUp
            else -> {
                val m = Regex(
                    """^(?:koppints|erintsd meg|nyomd meg|tap|click)\s+(?:(?:az|a|the)\s+)?(.+?)(?:\s+(?:gombra|gombot|button))?$"""
                ).matchEntire(raw) ?: return null
                val label = m.groupValues[1].trim()
                // Generic "OK"/"Continue" buttons may approve a payment or
                // permission without revealing the operation. Never click them.
                if (label.length !in 2..50 || unsafeTapLabel(label)) return null
                Action.TapLabel(label)
            }
        }
    }

    fun execute(context: Context, requested: Action): String {
        val service = JarvisAccessibilityService.current()
            ?: return "A képernyő vezérléséhez külön engedélyezd a Jarvis akadálymentesítési szolgáltatását az Android Beállításokban."
        val attempted = service.singleUserAction(requested)
        return if (attempted) "A kért Android-képernyőműveletet elküldtem. Az eredményt a kijelzőn ellenőrizd."
        else "Nem találtam egyértelmű, megengedett vezérlőt az aktuális képernyőn. Semmit nem nyomtam meg."
    }

    fun openPermissionSettings(context: Context): Boolean =
        openSystemIntent(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var live = WeakReference<JarvisAccessibilityService>(null)
        internal fun current(): JarvisAccessibilityService? = live.get()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        live = WeakReference(this)
    }

    override fun onDestroy() {
        if (live.get() === this) live = WeakReference(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Never persist, transmit or send third-party screen content to GPT.
    }
    override fun onInterrupt() {}

    internal fun singleUserAction(action: JarvisScreenControl.Action): Boolean {
        return when (action) {
            JarvisScreenControl.Action.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            JarvisScreenControl.Action.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            JarvisScreenControl.Action.Recents -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            JarvisScreenControl.Action.Notifications ->
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            JarvisScreenControl.Action.QuickSettings ->
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            JarvisScreenControl.Action.ScrollDown ->
                scrollCurrent(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            JarvisScreenControl.Action.ScrollUp ->
                scrollCurrent(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            is JarvisScreenControl.Action.TapLabel -> tapUnique(action.value)
        }
    }

    private fun traverse(root: AccessibilityNodeInfo?, limit: Int = 220): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && result.size < limit) {
            val node = queue.removeFirst()
            result.add(node)
            for (index in 0 until node.childCount.coerceAtMost(60)) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun scrollCurrent(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() == packageName) return false
        return traverse(root).firstOrNull { it.isVisibleToUser && it.isScrollable }
            ?.performAction(action) ?: false
    }

    private fun tapUnique(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() == packageName) return false
        fun plain(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("""\p{M}+"""), "").trim()
        val query = plain(label)
        val matches = traverse(root).filter { node ->
            node.isVisibleToUser &&
                (plain(node.text?.toString().orEmpty()) == query ||
                 plain(node.contentDescription?.toString().orEmpty()) == query)
        }
        // Never guess between two "OK", "Open" or other duplicate controls.
        if (matches.size != 1) return false
        var node: AccessibilityNodeInfo? = matches.single()
        repeat(4) {
            if (node?.isClickable == true) return node?.performAction(
                AccessibilityNodeInfo.ACTION_CLICK) == true
            node = node?.parent
        }
        return false
    }
}
