package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The account gate is activated only when the Supabase public configuration
 * is supplied in a QA build. Existing releases/installed direct-key testers
 * are unaffected until this feature is explicitly deployed and verified.
 */
@Composable
internal fun JarvisAuthRoot(
    context: Context,
    launcherRequest: String?,
    authRedirect: Uri?,
    onRedirectConsumed: () -> Unit
) {
    val revision = JarvisSupabaseAuth.revision
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authRedirect) {
        if (authRedirect != null) {
            try {
                busy = true
                JarvisSupabaseAuth.completeGoogleSignIn(context, authRedirect)
                error = ""
            } catch (e: Exception) {
                error = e.message ?: "A Google-belépés sikertelen."
            } finally {
                busy = false
                onRedirectConsumed()
            }
        }
    }

    if (!JarvisSupabaseAuth.enabled()) {
        JarvisApp(context, launcherRequest)
        return
    }
    // Reading revision is intentional: Compose reacts to login/logout/refresh.
    val signedIn = remember(revision) { JarvisSupabaseAuth.hasSession(context) }
    if (signedIn) {
        JarvisApp(context, launcherRequest)
        return
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    val text = Color(0xFFE8F2FF)
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080E19))
            .systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("JARVIS", color = Color(0xFF65D7F8), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))
        Text(if (registering) "Új fiók létrehozása" else "Bejelentkezés",
            color = text, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it.take(254) },
            label = { Text("E-mail-cím") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it.take(256) },
            label = { Text("Jelszó") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            scope.launch {
                busy = true
                error = ""
                try {
                    error = if (registering)
                        JarvisSupabaseAuth.signUp(context, email, password)
                    else {
                        JarvisSupabaseAuth.signIn(context, email, password)
                        ""
                    }
                    password = ""
                } catch (e: Exception) {
                    error = e.message ?: "Sikertelen bejelentkezés."
                } finally {
                    busy = false
                }
            }
        }, enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) {
            Text(if (registering) "Regisztráció" else "Belépés")
        }
        TextButton(onClick = {
            registering = !registering
            error = ""
            password = ""
        }, enabled = !busy) {
            Text(if (registering) "Már van fiókom" else "Új fiók létrehozása", color = text)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = {
            try {
                error = ""
                JarvisSupabaseAuth.startGoogleSignIn(context)
            } catch (e: Exception) {
                error = e.message ?: "A Google-belépést nem sikerült megnyitni."
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Folytatás Google-fiókkal")
        }
        if (busy) {
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator()
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(error, color = Color(0xFFFFB4AB))
        }
        Spacer(Modifier.height(20.dp))
        Text("A belépési adatok a Supabase hitelesítési szerverére kerülnek. " +
            "A Jarvis nem tárol jelszót a telefonon. " +
            "A korábbi helyi beszélgetések még nincsenek felhasználónként elkülönítve.",
            color = Color(0xFFA6B9CA), style = MaterialTheme.typography.bodySmall)
    }
}
