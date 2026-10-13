package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** An on-demand, foreground-only update with an OS-owned install confirmation. */
@Composable
internal fun JarvisUpdatePanel(context: Context, language: JarvisLanguage) {
    val t = { key: String -> JarvisUpdateTexts.get(language, key) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<JarvisUpdateCandidate?>(null) }
    var verifiedFile by remember { mutableStateOf<File?>(null) }
    var lastHandledToken by remember { mutableIntStateOf(0) }

    fun install(target: JarvisUpdateCandidate) {
        if (busy) return
        busy = true
        status = t("downloading")
        scope.launch {
            val result = if (verifiedFile?.isFile == true) {
                JarvisDownloadResult(JarvisInstallResult.READY, verifiedFile)
            } else withContext(Dispatchers.IO) {
                JarvisAppUpdater.downloadAndVerify(context.applicationContext, target)
            }
            when (result.outcome) {
                JarvisInstallResult.READY -> {
                    verifiedFile = result.file
                    if (!JarvisAppUpdater.canInstall(context)) {
                        status = t("installerPermission")
                    } else if (result.file == null ||
                        !JarvisAppUpdater.installWithAndroid(context, result.file)) {
                        status = t("installFailed")
                    } else status = t("ready")
                }
                JarvisInstallResult.SIGNATURE_MISMATCH -> status = t("signatureMismatch")
                JarvisInstallResult.NOT_NEWER -> status = t("notNewer")
                JarvisInstallResult.DIFFERENT_PACKAGE -> status = t("differentPackage")
                JarvisInstallResult.CORRUPTED_APK -> status = t("corrupted")
                else -> status = t("failed")
            }
            busy = false
        }
    }

    fun check(autoInstall: Boolean = false) {
        if (busy) return
        candidate = null
        verifiedFile = null
        busy = true
        status = t("checking")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                JarvisAppUpdater.check(context.applicationContext)
            }
            when (result) {
                is JarvisUpdateResult.Available -> {
                    candidate = result.candidate
                    status = t("available") + " " + result.candidate.tag
                }
                JarvisUpdateResult.Latest -> status = t("latest")
                JarvisUpdateResult.NoCompatibleRelease -> status = t("latest")
                JarvisUpdateResult.NetworkError -> status = t("failed")
            }
            busy = false
            if (result is JarvisUpdateResult.Available && autoInstall) install(result.candidate)
        }
    }

    // A voice request opens this panel and triggers exactly one check, rather
    // than executing an installer in the microphone's background service.
    val requestId = JarvisUpdateNavigation.requestCheck
    LaunchedEffect(requestId) {
        if (requestId > 0 && requestId != lastHandledToken) {
            lastHandledToken = requestId
            check(JarvisUpdateNavigation.installRequested)
        }
    }

    SettingsSectionTitle(t("heading"))
    JarvisSettingsGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Text(
                t("version") + " " + JarvisAppUpdater.currentVersion(context),
                color = JarvisPalette.mainText)
            androidx.compose.material3.Text(
                t("explanation"), color = JarvisPalette.subtleText,
                style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy, onClick = { check() },
                modifier = Modifier.fillMaxWidth().testTag("jarvis-update-check")
            ) {
                androidx.compose.material3.Text(t("check"))
            }
            if (candidate != null) {
                androidx.compose.material3.Text(candidate!!.tag,
                    color = JarvisPalette.mainText)
                Button(
                    enabled = !busy, onClick = { install(candidate!!) },
                    modifier = Modifier.fillMaxWidth().testTag("jarvis-update-install")
                ) { androidx.compose.material3.Text(t("download")) }
            }
            if (verifiedFile?.isFile == true && !JarvisAppUpdater.canInstall(context)) {
                OutlinedButton(
                    onClick = { JarvisAppUpdater.requestInstallerPermission(context) },
                    modifier = Modifier.fillMaxWidth().testTag("jarvis-update-permission")
                ) { androidx.compose.material3.Text(t("permissionButton")) }
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            if (status.isNotBlank()) androidx.compose.material3.Text(status,
                color = JarvisPalette.subtleText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("jarvis-update-status"))
        }
    }
}
