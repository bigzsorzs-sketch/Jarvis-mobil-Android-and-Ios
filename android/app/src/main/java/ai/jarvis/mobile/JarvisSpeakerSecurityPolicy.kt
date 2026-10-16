package ai.jarvis.mobile

/**
 * A spoken transcript does not prove the identity of its speaker.
 *
 * Until a separate, consented, tested speaker-verification implementation
 * exists, Jarvis must NEVER authorize personal actions or Home Assistant
 * commands while Android reports the device locked.
 *
 * The LIGHTING type is retained so a future verified-identity policy can
 * distinguish lighting from personal/physical safety-critical operations.
 * The legacy lightingOptIn preference is deliberately NOT authentication.
 */
internal enum class JarvisVoiceRisk {
    PERSONAL,
    LIGHTING
}

internal object JarvisSpeakerSecurityPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun permitted(
        deviceLocked: Boolean,
        risk: JarvisVoiceRisk,
        lightingOptIn: Boolean = false
    ): Boolean {
        // Android device unlock is the only trust proof for this release.
        return !deviceLocked
    }

}
