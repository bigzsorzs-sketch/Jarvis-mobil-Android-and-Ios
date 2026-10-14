package ai.jarvis.mobile

/**
 * Foreground speech handoff only. A streamed sentence is text, NEVER an action.
 * The hands-free service owns audio playback and may reject stale requests.
 * All callbacks run on Android's main thread.
 */
internal object JarvisVoiceStreamBridge {
    var receiver: ((request: String, phrase: String) -> Boolean)? = null
    fun deliver(request: String, phrase: String): Boolean =
        receiver?.invoke(request, phrase) == true
}
