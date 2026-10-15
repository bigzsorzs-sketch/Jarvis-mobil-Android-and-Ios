package ai.jarvis.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONObject

internal data class JarvisHomeZone(val lat: Double, val lon: Double, val radiusMeters: Float)

internal object JarvisDeparturePolicy {
    const val DEFAULT_RADIUS = 150f
    const val MIN_RADIUS = 100f
    const val MAX_RADIUS = 150f
    const val MAX_HOME_ACCURACY_METERS = 60f
    const val EXIT_COOLDOWN_MS = 10 * 60 * 1000L
    const val MAX_WORK_DELAY_MS = 30 * 60 * 1000L

    fun validZone(lat: Double, lon: Double, radius: Float): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 &&
            lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0) &&
            radius in MIN_RADIUS..MAX_RADIUS

    fun assessment(devices: List<JarvisHomeEntity>): JarvisDepartureAssessment {
        val bulbs = devices.filter { it.domain == "light" }
        val on = bulbs.filter { it.state == "on" }.map { it.name.take(80) }
        val unknown = bulbs.any { it.state in setOf("unknown", "unavailable") }
        return when {
            on.isNotEmpty() -> JarvisDepartureAssessment.LightsOn(on.take(12))
            unknown -> JarvisDepartureAssessment.Unverified
            bulbs.isEmpty() -> JarvisDepartureAssessment.NoLightsConfigured
            else -> JarvisDepartureAssessment.AllOff
        }
    }
}
internal sealed class JarvisDepartureAssessment {
    data class LightsOn(val names: List<String>) : JarvisDepartureAssessment()
    data object AllOff : JarvisDepartureAssessment()
    data object Unverified : JarvisDepartureAssessment()
    data object NoLightsConfigured : JarvisDepartureAssessment()
}

/** Opt-in one-zone automation. No live GPS collection, no AI route, no auto shutoff. */
internal object JarvisDepartureGuard {
    private const val ZONE = "departure_light_zone"
    private const val ENABLED = "departure_light_enabled"
    private const val REPORT = "departure_light_last_result"
    private const val GEOFENCE_ID = "jarvis_home_lights_departure_v1"

    fun home(context: Context): JarvisHomeZone? = runCatching {
        val json = JSONObject(SecurePrefs(context).read(ZONE, ""))
        val lat = json.getDouble("lat")
        val lon = json.getDouble("lon")
        val radius = json.getDouble("radius").toFloat()
        JarvisHomeZone(lat, lon, radius).takeIf {
            JarvisDeparturePolicy.validZone(it.lat, it.lon, it.radiusMeters)
        }
    }.getOrNull()

    fun storeHome(context: Context, lat: Double, lon: Double,
                  accuracyMeters: Float, radius: Float) {
        require(accuracyMeters.isFinite() && accuracyMeters in 0f..JarvisDeparturePolicy.MAX_HOME_ACCURACY_METERS) {
            "A helymeghatározás túl pontatlan; próbáld újra a háznál."
        }
        require(JarvisDeparturePolicy.validZone(lat, lon, radius)) {
            "Érvénytelen otthoni hely vagy túl kicsi körzet."
        }
        SecurePrefs(context).write(ZONE, JSONObject().put("lat", lat)
            .put("lon", lon).put("radius", radius.toDouble()).toString())
    }

    fun enabled(context: Context) =
        runCatching { SecurePrefs(context).read(ENABLED, "false") == "true" }.getOrDefault(false)
    fun lastReport(context: Context) =
        runCatching { SecurePrefs(context).read(REPORT, "") }.getOrDefault("")
    internal fun setReport(context: Context, value: String) {
        runCatching { SecurePrefs(context).write(REPORT, value.take(300)) }
    }
    private fun permitted(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine && background
    }
    fun canEnable(context: Context): Boolean = permitted(context) &&
        home(context) != null && JarvisHomeConnect.load(context) != null

    private fun intent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 3159,
            Intent(context, JarvisDepartureReceiver::class.java)
                .setAction("ai.jarvis.mobile.HOME_GEOFENCE_EXIT"), flags)
    }

    @SuppressLint("MissingPermission")
    fun enable(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!canEnable(context)) {
            onResult(false, "Előbb állítsd be az otthoni helyet, a Home Assistantot és " +
                "engedélyezd a pontos és állandó háttérbeli helyhozzáférést.")
            return
        }
        val zone = home(context) ?: return onResult(false, "Hiányzó otthoni hely.")
        val geofence = Geofence.Builder().setRequestId(GEOFENCE_ID)
            .setCircularRegion(zone.lat, zone.lon, zone.radiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(60_000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence).build()
        try {
            LocationServices.getGeofencingClient(context).addGeofences(request, intent(context))
                .addOnSuccessListener {
                    SecurePrefs(context).write(ENABLED, "true")
                    onResult(true, "Otthonelhagyási lámpaellenőrzés bekapcsolva.")
                }
                .addOnFailureListener {
                    onResult(false, "A geofence nem indult el. " +
                        "Ellenőrizd az Android helyengedélyeit.")
                }
        } catch (_: Exception) { onResult(false, "A helyfigyelés nem indítható.") }
    }

    fun disable(context: Context, onResult: (String) -> Unit = {}) {
        // Fail closed even if Google's background geofence removal fails.
        SecurePrefs(context).write(ENABLED, "false")
        LocationServices.getGeofencingClient(context).removeGeofences(intent(context))
            .addOnCompleteListener { onResult("Otthonelhagyási figyelés kikapcsolva.") }
    }

    /** Boot may remove geofences; re-arm only if already explicitly opted in. */
    fun rearmAfterBoot(context: Context) {
        if (!enabled(context)) return
        enable(context) { succeeded, reason ->
            if (!succeeded) setReport(context, reason)
        }
    }
}

/** Receives background exit transition and delegates all network I/O to WorkManager. */
class JarvisDepartureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "ai.jarvis.mobile.HOME_GEOFENCE_EXIT" ||
            !JarvisDepartureGuard.enabled(context)) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT)
            return
        val prefs = context.getSharedPreferences("jarvis_departure_guard_events", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_exit", 0L) < JarvisDeparturePolicy.EXIT_COOLDOWN_MS)
            return
        prefs.edit().putLong("last_exit", now).apply()
        val work = OneTimeWorkRequestBuilder<JarvisDepartureWorker>()
            .setInputData(androidx.work.workDataOf("occurred_ms" to now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("jarvis-leaving-home-check",
            ExistingWorkPolicy.REPLACE, work)
    }
}

/** Only visible light entities can raise a reminder; no physical action is executed. */
class JarvisDepartureWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!JarvisDepartureGuard.enabled(ctx)) return Result.success()
        val occurred = inputData.getLong("occurred_ms", 0L)
        if (occurred <= 0L || System.currentTimeMillis() - occurred >
            JarvisDeparturePolicy.MAX_WORK_DELAY_MS) return Result.success()
        val config = runCatching { JarvisHomeConnect.load(ctx) }.getOrNull()
        if (config == null) {
            JarvisDepartureGuard.setReport(ctx, "Nem tudtam ellenőrizni a lámpákat: nincs kapcsolat.")
            return Result.success()
        }
        val assessment = try {
            JarvisDeparturePolicy.assessment(JarvisHomeConnect.discover(config))
        } catch (_: Exception) {
            JarvisDepartureAssessment.Unverified
        }
        when (assessment) {
            is JarvisDepartureAssessment.LightsOn -> {
                val names = assessment.names.joinToString(", ")
                JarvisDepartureGuard.setReport(ctx, "Otthon elhagyva: égve maradt: $names")
                alert(ctx, "Égve maradt otthon a világítás", names)
            }
            JarvisDepartureAssessment.Unverified -> {
                JarvisDepartureGuard.setReport(ctx, "A lámpák állapota nem ellenőrizhető.")
                alert(ctx, "Jarvis: ellenőrzés nem sikerült",
                    "Nem tudtam megerősíteni, hogy minden lámpa le van kapcsolva.")
            }
            JarvisDepartureAssessment.NoLightsConfigured ->
                JarvisDepartureGuard.setReport(ctx, "Nincs Home Assistantban felismert lámpa.")
            JarvisDepartureAssessment.AllOff ->
                JarvisDepartureGuard.setReport(ctx, "Ellenőrizve: minden elérhető lámpa kikapcsolva.")
        }
        return Result.success()
    }

    private fun alert(context: Context, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val channelId = "jarvis_home_departure"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channelId,
            "Jarvis otthoni lámpaellenőrzés", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content.take(170))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.take(600)))
            .setContentIntent(open)
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(4312, notification) }
        catch (_: SecurityException) { /* Never claim that a notification was delivered. */ }
    }
}
