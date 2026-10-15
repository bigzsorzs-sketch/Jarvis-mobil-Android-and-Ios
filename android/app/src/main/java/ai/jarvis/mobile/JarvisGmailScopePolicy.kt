package ai.jarvis.mobile

/**
 * Ask Google only for scopes the CURRENT user action requires.
 * A request to read mail never silently asks for permission to send it.
 * Revocation still covers both possible Jarvis-granted Gmail scopes.
 */
internal object JarvisGmailScopePolicy {
    const val READ = "https://www.googleapis.com/auth/gmail.readonly"
    const val SEND = "https://www.googleapis.com/auth/gmail.send"
    val ALL: Set<String> = linkedSetOf(READ, SEND)

    fun required(operation: GmailOperation): Set<String> = when (operation) {
        GmailOperation.Connect, GmailOperation.Check, GmailOperation.ListUnread,
        is GmailOperation.Read, is GmailOperation.ResolveRecipient ->
            setOf(READ)
        is GmailOperation.Send ->
            if (operation.actuallySend) setOf(SEND) else setOf(READ)
        is GmailOperation.Reply -> ALL
        GmailOperation.Disconnect -> emptySet()
        is GmailOperation.Incomplete -> emptySet()
    }

    fun granted(required: Set<String>, actual: Collection<String>): Boolean =
        required.isNotEmpty() && actual.containsAll(required)
}
