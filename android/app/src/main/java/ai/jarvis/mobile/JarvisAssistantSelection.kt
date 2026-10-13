package ai.jarvis.mobile

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Selecting the system assistant is an Android user-consent flow.
 * Merely opening voice-input settings does not grant ROLE_ASSISTANT.
 */
internal object JarvisAssistantSelection {
    internal fun needsRolePrompt(sdk: Int, available: Boolean, alreadyHeld: Boolean): Boolean =
        sdk >= 29 && available && !alreadyHeld

    /** Request via Android's role prompt when supported, or show default-app settings. */
    fun intent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val roleIntent = runCatching {
                val roles = context.getSystemService(RoleManager::class.java)
                if (roles != null && needsRolePrompt(
                        Build.VERSION.SDK_INT,
                        roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT),
                        roles.isRoleHeld(RoleManager.ROLE_ASSISTANT))) {
                    roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                } else null
            }.getOrNull()
            if (roleIntent != null) return roleIntent
            return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }
}
