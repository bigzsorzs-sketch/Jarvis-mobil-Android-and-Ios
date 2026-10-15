package ai.jarvis.mobile

/** Route ordinary questions to AI while preserving deterministic device actions.
 * AI can only propose validated, allowlisted operations; text never executes as a command. */
internal object JarvisAiRouting {
    fun shouldAskAi(text: String, cloudConversation: Boolean = true): Boolean {
        val command = text.trim()
        if (command.isEmpty()) return false
        if (LocalConversation.reply(command) != null ||
            JarvisCapabilities.isDiscoveryRequest(command)) return cloudConversation
        if (JarvisUpdateVoice.isRequest(command) ||
            JarvisCapabilities.localPageToOpen(command) != null) return false
        // Exact explicit drafts are always handled locally with recipient/body
        // verification and review; they must not depend on model tools.
        if (JarvisGlucoseVoice.isQuery(command)) return false
        if (JarvisMessageIntent.parse(command) != null) return false
        if (JarvisPhoneActions.requestedAction(command) != null ||
            JarvisSystemPanels.identify(command) != null) return false
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

