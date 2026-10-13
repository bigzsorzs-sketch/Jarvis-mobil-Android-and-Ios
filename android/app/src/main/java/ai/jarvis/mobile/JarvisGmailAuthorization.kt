package ai.jarvis.mobile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-bound interactive Google OAuth authorization.
 * Access tokens exist only in the method's lifetime and are never persisted.
 * Never request offline server access or embed a Google password/client secret.
 */
@Composable
internal fun rememberJarvisGmail(
    onCompleted: (GmailRequest, GmailResult) -> Unit
): (GmailRequest) -> Unit {
    val activity = LocalContext.current as Activity
    val client = remember(activity) { Identity.getAuthorizationClient(activity) }
    val scope = rememberCoroutineScope()
    val currentCallback by rememberUpdatedState(onCompleted)
    var pending by remember { mutableStateOf<GmailRequest?>(null) }
    val requestedScopes = remember {
        listOf(
            Scope("https://www.googleapis.com/auth/gmail.readonly"),
            Scope("https://www.googleapis.com/auth/gmail.send")
        )
    }

    fun finish(result: GmailResult) {
        val item = pending ?: return
        pending = null
        currentCallback(item, result)
    }
    fun executeWithToken(token: String?) {
        val item = pending ?: return
        if (token.isNullOrBlank()) {
            finish(GmailResult("Nem kaptam érvényes Google-hozzáférést. Nem történt Gmail-művelet."))
            return
        }
        scope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { JarvisGmailApi.execute(activity, token, item.operation) }
                    .getOrElse { GmailResult(it.message ?: "Nem sikerült a Gmail-kérés. Nem igazolt a végrehajtás.") }
            }
            if (pending == item) finish(response)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pending == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            finish(GmailResult("A Google-fiók hozzáférése nem lett engedélyezve. Nem történt Gmail-művelet."))
        } else {
            runCatching { client.getAuthorizationResultFromIntent(result.data!!) }
                .onSuccess { auth ->
                    if (!auth.grantedScopes.containsAll(requestedScopes.map { it.scopeUri })) {
                        finish(GmailResult("A Gmail olvasásához és küldéséhez szükséges jogosultságokat nem kaptam meg."))
                    } else executeWithToken(auth.accessToken)
                }.onFailure { finish(GmailResult("A Google-engedélyezés sikertelen. Nem történt Gmail-művelet.")) }
        }
    }
    return { requested ->
        // Reject overlapping operations; particularly never queue an old send
        // behind an account selection dialogue for a different new command.
        if (pending != null) {
            currentCallback(requested, GmailResult("Még egy Gmail-művelet folyamatban van. Várd meg a végét."))
        } else if (requested.operation is GmailOperation.Incomplete) {
            currentCallback(requested, GmailResult(requested.operation.reason))
        } else {
            pending = requested
            if (requested.operation == GmailOperation.Disconnect) {
                client.revokeAccess(
                    RevokeAccessRequest.builder().setScopes(requestedScopes).build()
                ).addOnSuccessListener {
                    finish(GmailResult("A Gmail-hozzáférés visszavonva."))
                }.addOnFailureListener {
                    finish(GmailResult("A Gmail-hozzáférés visszavonását nem sikerült igazolni."))
                }
            } else {
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(requestedScopes).build()
                client.authorize(authRequest)
                    .addOnSuccessListener { auth ->
                        if (auth.hasResolution()) {
                            val intent = auth.pendingIntent
                            if (intent == null) finish(GmailResult("Nem sikerült megnyitni a Google-fiók engedélykérését."))
                            else runCatching {
                                launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                            }.onFailure { finish(GmailResult("A Google-fiók engedélykérése nem indult el.")) }
                        } else if (!auth.grantedScopes.containsAll(requestedScopes.map { it.scopeUri })) {
                            finish(GmailResult("Hiányzó Gmail-jogosultság. Nem történt művelet."))
                        } else executeWithToken(auth.accessToken)
                    }.addOnFailureListener {
                        finish(GmailResult("Nem sikerült csatlakozni a Gmailhez. Ellenőrizd, hogy a Gmail API és az Android OAuth kliens (csomagnév + aláírás SHA-1) be van-e állítva."))
                    }
            }
        }
    }
}
