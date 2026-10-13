package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Android binds this only after the user selects Jarvis as their assistant.
 * It does not acquire a microphone, capture a screen or grant permissions. */
class JarvisAssistantService : VoiceInteractionService()

class JarvisAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JarvisAssistantSession(this)
}

internal object JarvisAssistantLaunch {
    private var token: String? = null
    private var expires = 0L
    fun intent(context: Context): Intent {
        val fresh = java.util.UUID.randomUUID().toString()
        token = fresh
        expires = android.os.SystemClock.elapsedRealtime() + 15_000L
        return Intent(context, MainActivity::class.java).setAction(Intent.ACTION_ASSIST)
            .putExtra("jarvis_assistant_launch", fresh)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    fun consume(intent: Intent?): Boolean {
        val supplied = intent?.getStringExtra("jarvis_assistant_launch") ?: return false
        if (supplied != token || android.os.SystemClock.elapsedRealtime() > expires) return false
        token = null
        return true
    }
}

private class JarvisAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        setUiEnabled(false)
        try {
            startAssistantActivity(JarvisAssistantLaunch.intent(context))
        } catch (_: Exception) {
            HandsFreeStatus.status = "Nem sikerült megnyitni Jarvist az asszisztensgombbal."
        } finally { finish() }
    }
}
