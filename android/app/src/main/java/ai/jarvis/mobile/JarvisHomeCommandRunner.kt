package ai.jarvis.mobile

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared, deterministic Smart Home execution for chat and explicitly activated
 * voice. Never forwards account credentials, entity lists or commands to AI.
 * Only user-named, unambiguous, policy-allowlisted devices can be changed.
 */
internal object JarvisHomeCommandRunner {
    fun handles(command: String): Boolean =
        JarvisHomeCommands.parse(command) != null ||
            JarvisHomeCommands.statusTarget(command) != null

    /**
     * Injectable device operation makes safety behaviour testable without
     * connecting to a real Home Assistant server.
     */
    internal fun evaluate(
        command: String,
        devices: List<JarvisHomeEntity>,
        toggle: (JarvisHomeEntity, Boolean) -> String
    ): String {
        val action = JarvisHomeCommands.parse(command)
        val status = if (action == null) JarvisHomeCommands.statusTarget(command) else null
        if (action == null && status == null) return "Nem ismert Smart Home parancs."
        val target = action ?: JarvisHomeCommands.Toggle(status.orEmpty(), true)
        val device = JarvisHomeCommands.match(target, devices)
            ?: return "Nem találtam egyetlen egyértelműen egyező Smart Home eszközt."
        if (action == null) {
            return "Home Assistant szerint: " + device.name + " – " + device.state + "."
        }
        if (!device.canToggle || !JarvisHomePolicy.toggleAllowed(device.id)) {
            return "Ez az eszköz csak állapotlekérdezésre használható."
        }
        return toggle(device, action.on)
    }

    /** I/O never runs on Android's UI or microphone callback thread. */
    suspend fun execute(context: Context, command: String): String =
        withContext(Dispatchers.IO) {
            if (!handles(command)) return@withContext "Nem ismert Smart Home parancs."
            val config = try {
                JarvisHomeConnect.load(context)
            } catch (_: Exception) {
                null
            }
            if (config == null) {
                "A Smart Home még nincs csatlakoztatva. " +
                    "Nyisd meg a Smart Home oldalt és állítsd be a Home Assistant HTTPS kapcsolatot."
            } else {
                try {
                    val devices = JarvisHomeConnect.discover(config)
                    evaluate(command, devices) { entity, on ->
                        JarvisHomeConnect.toggle(config, entity.id, on)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    // Never include the Home Assistant token or URL in an error.
                    val reason = when (e) {
                        is java.net.SocketTimeoutException -> "Időtúllépés: ellenőrizd a Home Assistant kapcsolatot."
                        else -> "A Home Assistant nem válaszolt megfelelően; a művelet nem igazolt."
                    }
                    "Smart Home hiba: " + reason
                }
            }
        }
}
