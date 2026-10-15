package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.OutcomeReceiver
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsResponse
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.BloodGlucoseRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume

/**
 * Optional Android Health Connect reader. After one-time user authorization,
 * it can read records shared by OTHER compatible apps. Abbott LibreLink GB is
 * not known to publish readings here; this is NOT a LibreLink API or NFC reader.
 */
@android.annotation.SuppressLint("NewApi") // All API 34 calls are guarded by supported() and isAuthorized().
internal object JarvisHealthGlucose {
    const val READ_PERMISSION = "android.permission.health.READ_BLOOD_GLUCOSE"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 34

    fun isAuthorized(context: Context): Boolean =
        supported() && context.checkSelfPermission(READ_PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun latest(context: Context): JarvisGlucoseData.Point? {
        if (!isAuthorized(context)) return null
        return try {
            val mgr = context.getSystemService(HealthConnectManager::class.java) ?: return null
            val req = ReadRecordsRequestUsingFilters.Builder(BloodGlucoseRecord::class.java)
                .setTimeRangeFilter(
                    TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now().minusSeconds(7 * 24 * 3600L))
                        .setEndTime(Instant.now().plusSeconds(60L)).build()
                )
                .setAscending(false)
                .setPageSize(10)
                .build()
            suspendCancellableCoroutine<JarvisGlucoseData.Point?> { cont ->
                mgr.readRecords(req, context.mainExecutor,
                    object : OutcomeReceiver<ReadRecordsResponse<BloodGlucoseRecord>, HealthConnectException> {
                        override fun onResult(result: ReadRecordsResponse<BloodGlucoseRecord>) {
                            val r = result.records.maxByOrNull { it.time }
                            val mmol = r?.level?.inMillimolesPerLiter
                            if (cont.isActive) {
                                cont.resume(if (r != null && mmol != null &&
                                        mmol.isFinite() && mmol in 1.0..35.0)
                                    JarvisGlucoseData.Point(r.time.toEpochMilli(),
                                        mmol, "health-connect")
                                    else null)
                            }
                        }
                        override fun onError(error: HealthConnectException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
            }
        } catch (cancelled: CancellationException) {
            // STOP, a new voice turn, or a screen change must cancel the
            // suspended Health Connect request instead of yielding stale
            // health data to an already cancelled conversation.
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
