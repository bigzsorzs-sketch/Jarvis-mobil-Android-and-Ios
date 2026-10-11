package ai.jarvis.mobile

/**
 * Screen-lock privacy. Sensitive actions require an unlocked device by default.
 * Users may explicitly opt in to hands-free private actions while the screen is
 * locked. This does NOT bypass Android system permissions.
 */
internal object VoicePrivacyPolicy {
    fun mayUsePrivateData(deviceLocked: Boolean, explicitOptIn: Boolean): Boolean =
        !deviceLocked || explicitOptIn
}
