package ai.jarvis.mobile

/**
 * Foreground speech handoff only. A streamed sentence is text, NEVER an action.
 * The hands-free service owns audio playback and may reject stale requests.
 * All callbacks run on Android's main thread.
 */
internal object JarvisVoiceStreamBridge {
    var receiver: ((request: String, phrase: String) -> Boolean)? = null
    // Only the foreground Activity may own this callback. It invalidates and
    // cancels the matching active AI coroutine when a real user interrupts.
    var interruptCurrent: (() -> Unit)? = null
    fun interruptActive() {
        val cancel = interruptCurrent
        interruptCurrent = null
        cancel?.invoke()
    }
    fun deliver(request: String, phrase: String): Boolean =
        receiver?.invoke(request, phrase) == true
}
