package ai.jarvis.mobile

import android.content.Context
import android.media.AudioManager

/**
 * Safe, credential-free output diagnostics. A status can be read on screen
 * without sending a microphone sample, private chat or API token anywhere.
 */
internal object JarvisVoiceReadiness {
    fun reason(context: Context): String? = reasonForState(
        cloudTextApproved = JarvisAiGateway.cloudConsent(context),
        cloudVoiceApproved = JarvisAiGateway.voiceConsent(context),
        connectionSaved = JarvisAiGateway.hasSavedConnection(context),
        connectionConfigured = JarvisAiGateway.configured(context)
    )

    fun reasonForState(
        cloudTextApproved: Boolean,
        cloudVoiceApproved: Boolean,
        connectionSaved: Boolean,
        connectionConfigured: Boolean
    ): String? = when {
        !cloudTextApproved ->
            "Az AI-szöveg feldolgozása nincs engedélyezve. Beállítások → AI-agy."
        !cloudVoiceApproved ->
            "Az AI-hang külön hozzájárulása ki van kapcsolva. Beállítások → AI-agy → AI-hang használata."
        !connectionSaved ->
            "Nincs elmentett AI-kapcsolat. Beállítások → AI-agy alatt ellenőrizd az OpenRouter-kulcsot vagy a Jarvis-szervert."
        !connectionConfigured ->
            "Az AI-hang kapcsolata nincs megfelelően beállítva."
        else -> null
    }

    fun mediaVolumeMessage(context: Context): String {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Nem olvasható az Android médiahangereje."
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return when {
            current <= 0 -> "A médiahang ki van némítva. Emeld fel a telefon médiahangerejét."
            maximum > 0 -> "Médiahang: $current/$maximum. A hang Bluetooth-eszközre is mehet; ellenőrizd a kimenetet."
            else -> "Nem olvasható a média hangerőskálája."
        }
    }
}
