package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import org.json.JSONArray

/**
 * Manual GitHub release updater. No root, AccessibilityService, background
 * install or automatic permission grants. A change of signing key cannot be
 * solved by any in-app updater; the existing app stays installed on mismatch.
 */
internal data class JarvisUpdateCandidate(
    val tag: String, val name: String, val apkUrl: String,
    val expectedSha256: String, val sizeBytes: Long, val version: List<Int>
)
internal sealed class JarvisUpdateResult {
    data class Available(val candidate: JarvisUpdateCandidate) : JarvisUpdateResult()
    data object Latest : JarvisUpdateResult()
    data object NoCompatibleRelease : JarvisUpdateResult()
    data object NetworkError : JarvisUpdateResult()
}
internal enum class JarvisInstallResult {
    READY, NOT_NEWER, DIFFERENT_PACKAGE, SIGNATURE_MISMATCH,
    CORRUPTED_APK, UNSUPPORTED_INSTALL, DOWNLOAD_FAILED
}
internal data class JarvisDownloadResult(
    val outcome: JarvisInstallResult, val file: File? = null
)

internal object JarvisUpdateRules {
    private const val REPOSITORY_PATH = "/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases/download/"
    private val releasePattern =
        Regex("""^v(\d{1,3})\.(\d{1,3})\.(\d{1,4})-(?:dev-test|signed-test)\.(\d{1,4})$""")
    private val hexSha = Regex("[0-9a-f]{64}")
    fun version(tag: String): List<Int>? {
        val match = releasePattern.matchEntire(tag) ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }
    fun allowedDownloadUrl(raw: String, tag: String): Boolean = try {
        val parsed = java.net.URI(raw)
        parsed.scheme == "https" && parsed.host == "github.com" &&
            parsed.userInfo == null && parsed.rawQuery == null &&
            parsed.rawFragment == null && parsed.port == -1 &&
            parsed.rawPath.startsWith(REPOSITORY_PATH + tag + "/") &&
            parsed.rawPath.endsWith(".apk")
    } catch (_: Exception) { false }
    fun allowedRedirect(raw: String): Boolean = try {
        val uri = java.net.URI(raw)
        uri.scheme == "https" && uri.userInfo == null &&
            uri.host != null && (uri.host == "github.com" ||
                uri.host == "githubusercontent.com" ||
                uri.host.endsWith(".githubusercontent.com"))
    } catch (_: Exception) { false }

    fun digest(raw: String): String? {
        val value = raw.removePrefix("sha256:").lowercase(Locale.ROOT)
        return value.takeIf { hexSha.matches(it) }
    }
    fun choose(json: String, installed: String): JarvisUpdateCandidate? {
        val oldVersion = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(installed)
            ?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
        val releases = JSONArray(json)
        val candidates = ArrayList<JarvisUpdateCandidate>()
        for (index in 0 until minOf(releases.length(), 25)) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            val version = version(tag) ?: continue
            if (version.take(3).zip(oldVersion).firstOrNull { it.first != it.second }?.let { it.first < it.second } != false) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (idx in 0 until assets.length()) {
                val asset = assets.optJSONObject(idx) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                val length = asset.optLong("size")
                val digest = digest(asset.optString("digest")) ?: continue
                if (name.endsWith(".apk") && length in 1..120_000_000L &&
                    allowedDownloadUrl(url, tag)) {
                    candidates.add(JarvisUpdateCandidate(tag, name, url, digest,
                        length, version))
                }
            }
        }
        return candidates.maxWithOrNull(compareBy<JarvisUpdateCandidate> { it.version[0] }
            .thenBy { it.version[1] }.thenBy { it.version[2] }
            .thenBy { it.version[3] }
            .thenBy { if ("signed-test" in it.tag) 1 else 0 })
    }

    fun sameSigner(installed: List<ByteArray>, candidate: List<ByteArray>): Boolean {
        if (installed.isEmpty() || candidate.isEmpty()) return false
        val hashes = { certs: List<ByteArray> ->
            certs.map { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        return hashes(installed) == hashes(candidate)
    }
}

internal object JarvisAppUpdater {
    private const val RELEASES_API =
        "https://api.github.com/repos/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases?per_page=25"
    private const val MAX_APK_BYTES = 120_000_000L
    private const val CONNECT_TIMEOUT_MS = 15_000

    private fun installedInfo(context: Context): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33)
            context.packageManager.getPackageInfo(context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName,
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES)

    private fun certs(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= 28) {
            val signers = info.signingInfo?.apkContentsSigners ?: emptyArray()
            signers.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            (info.signatures ?: emptyArray()).map { it.toByteArray() }
        }

    fun currentVersion(context: Context): String =
        runCatching { installedInfo(context).versionName.orEmpty() }.getOrDefault("")

    fun check(context: Context): JarvisUpdateResult {
        val url = URL(RELEASES_API)
        val connection = (url.openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = CONNECT_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Jarvis-Mobile-Android-Updater")
            if (connection.responseCode != 200) return JarvisUpdateResult.NetworkError
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (json.length > 600_000) return JarvisUpdateResult.NetworkError
            val current = currentVersion(context)
            val candidate = JarvisUpdateRules.choose(json, current)
            if (candidate == null) JarvisUpdateResult.Latest
            else JarvisUpdateResult.Available(candidate)
        } catch (_: Exception) {
            JarvisUpdateResult.NetworkError
        } finally { connection.disconnect() }
    }

    private fun connectToAsset(candidate: JarvisUpdateCandidate): HttpURLConnection {
        var target = candidate.apkUrl
        repeat(6) {
            require(JarvisUpdateRules.allowedRedirect(target)) { "Untrusted download location" }
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Jarvis-Mobile-Android-Updater")
            when (conn.responseCode) {
                200 -> return conn
                301, 302, 303, 307, 308 -> {
                    val where = conn.getHeaderField("Location")
                        ?: error("Missing download redirect")
                    val next = URL(URL(target), where).toString()
                    conn.disconnect()
                    target = next
                }
                else -> {
                    conn.disconnect()
                    error("GitHub APK download unavailable")
                }
            }
        }
        error("Too many redirects")
    }

    @Suppress("DEPRECATION")
    private fun validateApk(context: Context, file: File): JarvisInstallResult {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES
        val apk = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return JarvisInstallResult.CORRUPTED_APK
        if (apk.packageName != context.packageName)
            return JarvisInstallResult.DIFFERENT_PACKAGE
        val old = installedInfo(context)
        val oldVersion = if (Build.VERSION.SDK_INT >= 28) old.longVersionCode
                         else old.versionCode.toLong()
        val newVersion = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode
                         else apk.versionCode.toLong()
        if (newVersion <= oldVersion) return JarvisInstallResult.NOT_NEWER
        if (!JarvisUpdateRules.sameSigner(certs(old), certs(apk)))
            return JarvisInstallResult.SIGNATURE_MISMATCH
        return JarvisInstallResult.READY
    }

    /** Must run on Dispatchers.IO. Never installs or uninstalls automatically. */
    fun downloadAndVerify(context: Context, candidate: JarvisUpdateCandidate):
        JarvisDownloadResult {
        if (!JarvisUpdateRules.allowedDownloadUrl(candidate.apkUrl, candidate.tag) ||
            candidate.sizeBytes !in 1..MAX_APK_BYTES ||
            JarvisUpdateRules.digest(candidate.expectedSha256) == null) {
            return JarvisDownloadResult(JarvisInstallResult.DOWNLOAD_FAILED)
        }
        val dir = File(context.cacheDir, "jarvis_updates").apply { mkdirs() }
        val destination = File(dir, "jarvis-update.apk")
        val temporary = File(dir, "jarvis-update.partial")
        temporary.delete()
        destination.delete()
        var connection: HttpURLConnection? = null
        return try {
            connection = connectToAsset(candidate)
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buf)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) error("APK exceeds download size limit")
                        digest.update(buf, 0, count)
                        output.write(buf, 0, count)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (total != candidate.sizeBytes ||
                !actual.equals(candidate.expectedSha256, ignoreCase = true))
                return JarvisDownloadResult(JarvisInstallResult.CORRUPTED_APK)
            if (!temporary.renameTo(destination)) error("Unable to finalize downloaded APK")
            val result = validateApk(context, destination)
            if (result != JarvisInstallResult.READY) destination.delete()
            JarvisDownloadResult(result,
                if (result == JarvisInstallResult.READY) destination else null)
        } catch (_: Exception) {
            JarvisDownloadResult(JarvisInstallResult.DOWNLOAD_FAILED)
        } finally {
            connection?.disconnect()
            temporary.delete()
        }
    }

    fun installWithAndroid(context: Context, file: File): Boolean {
        if (!file.isFile || !file.canonicalPath.startsWith(
                File(context.cacheDir, "jarvis_updates").canonicalPath + File.separator))
            return false
        return try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
        } catch (_: Exception) { false }
    }

    fun requestInstallerPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
}

/** UI and spoken commands request a check, never initiate an unapproved install. */
internal object JarvisUpdateVoice {
    private fun normalize(value: String): String = java.text.Normalizer.normalize(
        value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
    fun isRequest(value: String): Boolean {
        val s = normalize(value)
        val action = listOf("frissit", "ellenoriz", "update", "check", "aktualisier",
            "pruf", "verifi", "mise a jour", "actualiz", "comprobar", "aggiorn",
            "controll", "aktualizac", "sprawdz").any { it in s }
        val target = listOf("jarvis", "frissites", "verzio", "update", "version",
            "aktualisierung", "mise a jour", "actualizacion", "aggiornamento",
            "aktualizacja", "applik", "app").any { it in s }
        return action && target
    }
}

internal object JarvisUpdateNavigation {
    var requestCheck by androidx.compose.runtime.mutableIntStateOf(0)
    fun request() { requestCheck++ }
}
