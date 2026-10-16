package ai.jarvis.mobile

/**
 * Speaker identity is NOT the transcript from Android SpeechRecognizer.
 * UNKNOWN is mandatory until a separately consented, tested speaker-verification
 * engine has examined the actual microphone audio. Voice resemblance by itself
 * cannot be promoted to authenticated identity.
 */
internal enum class JarvisSpeakerCheck { UNKNOWN, VERIFIED, MISMATCH }
internal enum class JarvisVoiceRisk { CONVERSATION, LIGHTING, PERSONAL, HEATING, FINANCIAL_OR_SECURITY }

internal object JarvisSpeakerSecurityPolicy {
    fun permitted(
        phoneLocked: Boolean,
        risk: JarvisVoiceRisk,
        speaker: JarvisSpeakerCheck = JarvisSpeakerCheck.UNKNOWN,
        lightingOptIn: Boolean = false
    ): Boolean = when (risk) {
        JarvisVoiceRisk.CONVERSATION -> true
        // The user can explicitly allow low-risk lights on the lock screen.
        // If a real verifier detects a mismatch, even this is denied.
        JarvisVoiceRisk.LIGHTING -> !phoneLocked ||
            (lightingOptIn && speaker != JarvisSpeakerCheck.MISMATCH)
        // No speaker verification is currently wired up. UNKNOWN never grants
        // private account, health, messages or call privileges on a locked phone.
        JarvisVoiceRisk.PERSONAL -> !phoneLocked ||
            speaker == JarvisSpeakerCheck.VERIFIED
        // The current app exposes heating only as read-only state. Even a
        // verified voice must NOT operate a gas boiler or heater through this API.
        JarvisVoiceRisk.HEATING -> false
        // Voice alone is never strong authentication for money or security.
        JarvisVoiceRisk.FINANCIAL_OR_SECURITY -> false
    }

    /** Prevent private typed/voice intents from escaping via Activity handoff. */
    fun isPersonalRequest(command: String): Boolean =
        JarvisGmailCommands.parse(command) != null ||
            VoiceCalling.extractRequest(command) != null ||
            VoiceCalling.needsTarget(command) ||
            JarvisGlucoseVoice.isQuery(command) ||
            VoiceIntentParser.isReadMessage(command) ||
            JarvisMessageIntent.parse(command) != null ||
            ExternalTasks.parseMessage(command) != null
}
