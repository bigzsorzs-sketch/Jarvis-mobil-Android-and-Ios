package ai.jarvis.mobile

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Quiet, bounded, opt-in-through-existing-cloud-consent developer research.
 *
 * No microphone, contacts, chat history, health data, GitHub code or device state
 * is sent. No native tools are executed and no app files are edited.
 * Uses at most one paid AI request on a UTC day, on charging + unmetered network.
 * Model-produced research is NOT independently verified and NEVER executable.
 */
internal object JarvisBackgroundBrain {
    internal const val LAST_ATTEMPT_DAY = "background_brain_attempt_day"
    internal const val NOTE_DAY = "background_brain_note_day"
    internal const val NOTE_TOPIC = "background_brain_note_topic"
    internal const val NOTE = "background_brain_note"

    internal data class Lesson(val id: String, val title: String, val officialSite: String)
    private val curriculum = listOf(
        Lesson("kotlin", "Kotlin coroutines, structured concurrency, cancellation", "https://kotlinlang.org/docs/"),
        Lesson("android", "Android activity lifecycle and background execution limits", "https://developer.android.com/"),
        Lesson("compose", "Jetpack Compose state and recomposition best practices", "https://developer.android.com/"),
        Lesson("swift", "Swift concurrency and async await", "https://docs.swift.org/"),
        Lesson("ios", "SwiftUI state management and iOS app lifecycle", "https://developer.apple.com/documentation/"),
        Lesson("android", "Android speech recognition and microphone lifecycle", "https://developer.android.com/")
    )

    internal fun lesson(day: Long): Lesson = curriculum[Math.floorMod(day, curriculum.size.toLong()).toInt()]

    internal fun matches(question: String, topic: String): Boolean {
        val q = question.lowercase(Locale.ROOT)
        val isTechnical = listOf(
            "kód", "kod", "code", "program", "fejleszt", "debug", "hiba", "bug",
            "build", "compile", "fordít", "javít", "javits", "optimaliz"
        ).any(q::contains)
        if (!isTechnical) return false
        return when (topic) {
            "swift", "ios" -> listOf("swift", "ios", "iphone", "apple", "xcode").any(q::contains)
            "kotlin", "android", "compose" -> listOf(
                "kotlin", "android", "jarvis", "mobil", "compose", "mikrofon", "hang"
            ).any(q::contains)
            else -> false
        }
    }

    /** Research is a non-authoritative note, not a system instruction or permission. */
    fun relevantNote(context: Context, question: String): String? {
        val prefs = SecurePrefs(context)
        val topic = prefs.read(NOTE_TOPIC, "")
        if (!matches(question, topic)) return null
        val day = prefs.read(NOTE_DAY, "").toLongOrNull() ?: return null
        val age = System.currentTimeMillis() / 86_400_000L - day
        if (age !in 0..14) return null
        return prefs.read(NOTE, "").take(1800).takeIf { it.length >= 100 }
    }

    /** Idempotently register periodic work; no screen or extra start button. */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<JarvisLearningWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                "jarvis-background-brain-v1",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }
}

internal class JarvisLearningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        // Existing consent and connection are necessary; never prompt in background.
        if (!JarvisAiGateway.configured(context)) return Result.success()
        val prefs = SecurePrefs(context)
        val day = System.currentTimeMillis() / 86_400_000L
        return try {
            if (prefs.read(JarvisBackgroundBrain.LAST_ATTEMPT_DAY, "") == day.toString()) {
                return Result.success()
            }
            // Mark BEFORE provider request: no retry storms or surprise API charges.
            prefs.write(JarvisBackgroundBrain.LAST_ATTEMPT_DAY, day.toString())
            val lesson = JarvisBackgroundBrain.lesson(day)
            val request = """
                Keress rá az interneten a hivatalos fejlesztői dokumentáció alapján erre:
                ${lesson.title}. Hivatalos kiindulópont: ${lesson.officialSite}.
                Nyilvános fejlesztői tananyag; ne kérj és ne használj személyes adatot,
                chatelőzményt, fájlt, GitHub-hozzáférést vagy eszközműveletet.
                Készíts magyarul 5 rövid, gyakorlati ellenőrzési szempontot Android/iOS
                alkalmazáshibák javításához és legalább egy hivatalos dokumentációs
                hivatkozást. A bizonytalan állításokat jelöld bizonytalannak.
                Ez kizárólag tanulási jegyzet; ne állítsd, hogy Jarvist ellenőrizted vagy javítottad.
            """.trimIndent()
            val outcome = JarvisAiGateway.askStructured(context, request, "hu-HU")
            val note = outcome.message.trim()
            // Never treat background model tool proposals or provider errors as learning.
            val forbiddenAction = outcome.route != null || outcome.deviceAction != null ||
                outcome.callTarget != null || outcome.messageDraft != null ||
                outcome.nativeAction != null
            val valid = !forbiddenAction && note.length in 180..3200 &&
                note.contains(lesson.officialSite.substringBefore("/", "https:").ifBlank { "https" })
            if (valid) {
                prefs.write(JarvisBackgroundBrain.NOTE, note.take(1800))
                prefs.write(JarvisBackgroundBrain.NOTE_TOPIC, lesson.id)
                prefs.write(JarvisBackgroundBrain.NOTE_DAY, day.toString())
            }
            Result.success()
        } catch (_: Exception) {
            // No background exceptions, notifications or credential logging.
            // Retry on the next periodic run, never charge repeatedly today.
            Result.success()
        }
    }
}
