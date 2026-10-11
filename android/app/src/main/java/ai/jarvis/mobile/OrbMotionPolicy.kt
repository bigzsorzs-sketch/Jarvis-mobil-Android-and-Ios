package ai.jarvis.mobile

/** The orb must never start an animation merely because the mic is armed. */
internal object OrbMotionPolicy {
    fun animate(userActuallySpeaking: Boolean, jarvisActuallySpeaking: Boolean): Boolean =
        userActuallySpeaking || jarvisActuallySpeaking
}
