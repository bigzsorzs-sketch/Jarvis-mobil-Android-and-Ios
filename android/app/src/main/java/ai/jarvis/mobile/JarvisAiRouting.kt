package ai.jarvis.mobile

/** Route ordinary questions to AI while preserving deterministic device actions.
 * AI can only propose validated, allowlisted operations; text never executes as a command. */
internal object JarvisAiRouting {
    /**
     * A hands-free turn already sent to GPT-5.2 must not submit the exact same
     * ordinary question to the provider AGAIN after a plain answer arrived.
     * Existing local actions and the specialized research pipeline still run
     * through their independent original-user/permission validators.
     *
     * Both valid conversation replies and provider errors are terminal for
     * this turn: automatically retrying an HTTP/provider error can double
     * charge without consent, and it cannot guarantee better recovery.
     */
    fun mayReuseVoiceResponse(
        text: String,
        proposal: JarvisAiOutcome,
        cloudConversation: Boolean = true
    ): Boolean =
        cloudConversation &&
            shouldAskAi(text, cloudConversation) &&
            !JarvisAgentResearch.shouldHandle(text) &&
            proposal.message.isNotBlank() &&
            proposal.route == null &&
            proposal.deviceAction == null &&
            proposal.callTarget == null &&
            proposal.messageDraft == null &&
            proposal.nativeAction == null

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

