package ai.jarvis.mobile

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

/**
 * A single Android-scheduled Jarvis wake-up alarm for the Basic beta.
 * Unlike a VoiceService timer, AlarmManager can trigger with the app closed.
 * System permission / notifications / OEM device settings still affect delivery.
 */
internal object JarvisAlarms {
    private const val PREFERENCES = "jarvis_alarm_beta"
    private const val ID = 5201
    private const val ACTION_RING = "ai.jarvis.mobile.ACTION_RING"
    private const val CHANNEL = "jarvis_alarm_sound"

    sealed class Result {
        data class Created(val time: String) : Result()
        data object NoExactAlarmPermission : Result()
        data object NoNotificationPermission : Result()
        data object Failed : Result()
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager(context).canScheduleExactAlarms()

    fun requestExactPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openSystemIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + context.packageName)))
        }
    }

    private fun notificationAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ringIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, ID, Intent(context, JarvisAlarmReceiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun current(context: Context): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong("trigger_millis", 0L)

    @android.annotation.SuppressLint("ApplySharedPref")
    fun schedule(context: Context, time: AlarmTime): Result {
        if (!canSchedule(context)) return Result.NoExactAlarmPermission
        if (!notificationAllowed(context)) return Result.NoNotificationPermission
        return try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            val showIntent = PendingIntent.getActivity(
                context, ID, Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // Only one active Jarvis alarm at a time. Replaces previous Jarvis alarm.
            manager(context).setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, showIntent),
                ringIntent(context)
            )
            // Synchronous commit is intentional here: do not report the alarm
            // as saved if persistence fails. Cancel any orphan scheduling.
            val persisted = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putLong("trigger_millis", calendar.timeInMillis).commit()
            if (!persisted) {
                manager(context).cancel(ringIntent(context))
                Result.Failed
            } else {
                Result.Created(String.format(Locale.UK, "%02d:%02d", time.hour, time.minute))
            }
        } catch (_: Exception) {
            Result.Failed
        }
    }

    fun cancel(context: Context): Boolean {
        if (current(context) == 0L) return false
        return try {
            manager(context).cancel(ringIntent(context))
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove("trigger_millis").apply()
            true
        } catch (_: Exception) { false }
    }

    fun afterFiring(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove("trigger_millis").apply()
    }

    internal fun alarmNotification(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM).build()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jarvis ébresztő", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(ringtone, attrs)
            }
        )
        val open = PendingIntent.getActivity(
            context, ID + 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Jarvis – ébresztő")
            .setContentText("Elérkezett a beállított ébresztési idő.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notifications.notify(ID, notification)
    }
}

class JarvisAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "ai.jarvis.mobile.ACTION_RING") return
        JarvisAlarms.afterFiring(context)
        try { JarvisAlarms.alarmNotification(context) } catch (_: Exception) {
            // The notification may be blocked by the OS. Never mark it delivered.
        }
    }
}

/** Re-establish a pending alarm on an unlocked boot if Android deleted old alarms. */
class JarvisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { JarvisDepartureGuard.rearmAfterBoot(context) }
        val saved = JarvisAlarms.current(context)
        if (saved <= 0L || saved < System.currentTimeMillis()) return
        if (!JarvisAlarms.canSchedule(context)) return
        val calendar = Calendar.getInstance().apply { timeInMillis = saved }
        JarvisAlarms.schedule(context, AlarmTime(
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
        ))
    }
}
