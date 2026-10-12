package ai.jarvis.mobile

/** Route ordinary questions to AI while preserving deterministic device actions.
 * Cloud AI output is displayed/spoken as text only and NEVER executes tools. */
internal object JarvisAiRouting {
    fun shouldAskAi(text: String): Boolean {
        val command = text.trim()
        if (command.isEmpty() || LocalConversation.reply(command) != null ||
            JarvisCapabilities.replyIfAsked(command) != null ||
            JarvisCapabilities.localPageToOpen(command) != null) return false
        if (BasicCommandRouter.classify(command) != BasicAction.UNKNOWN) return false
        return ExternalTasks.parseMessage(command) == null &&
            !ExternalTasks.parseUcJournal(command) &&
            ExternalTasks.parseGmailSearch(command) == null &&
            !ExternalTasks.resemblesGmailSearch(command) &&
            VoiceEmail.parse(command) == null &&
            VoiceNavigation.destinationFrom(command) == null &&
            VoiceCalling.extractRequest(command) == null &&
            AlarmCommands.parseAlarmTime(command) == null
    }
}
