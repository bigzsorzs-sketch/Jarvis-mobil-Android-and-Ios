package ai.jarvis.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val JarvisBlue = Color(0xFF075FF1)
private val JarvisInk = Color(0xFF08142E)
private val JarvisMuted = Color(0xFF78859D)

@Composable
internal fun JarvisHomeScreen(
    context: Context,
    onMenu: () -> Unit,
    onNavigate: (String) -> Unit,
    onCommand: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val handsFree = HandsFreeStatus.running

    fun submit(value: String) {
        val command = value.trim()
        if (command.isBlank()) return
        keyboard?.hide()
        text = ""
        issue = ""
        // The existing Chat command executor handles the actual operation;
        // the home does not invent a second, divergent command handler.
        onCommand(VoiceIntentParser.afterWakeWord(command)?.takeIf { it.isNotBlank() } ?: command)
    }
    fun startVoice() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.START)
            )
            issue = ""
        } catch (_: Exception) {
            issue = "A telefon nem engedte elindítani a mikrofont."
        }
    }

    val voicePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) startVoice()
        else issue = "A kéz nélküli módhoz mikrofonengedély szükséges."
    }
    val dictate = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!heard.isNullOrBlank()) submit(heard)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFFF7FAFF), Color.White, Color(0xFFF3F8FF))
            )
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape, color = Color.White, shadowElevation = 3.dp
            ) {
                IconButton(onClick = onMenu) {
                    Column(
                        modifier = Modifier.width(23.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().height(2.5.dp)
                            .background(JarvisInk, RoundedCornerShape(4.dp)))
                        Box(Modifier.fillMaxWidth(0.72f).height(2.5.dp)
                            .background(JarvisInk, RoundedCornerShape(4.dp)))
                    }
                }
            }
            Row(
                modifier = Modifier.background(Color(0xFFE9EEF6), RoundedCornerShape(36.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White, shape = RoundedCornerShape(30.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable { onNavigate("Chat") }
                ) {
                    Text("Chat", color = JarvisInk, fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 25.dp, vertical = 12.dp))
                }
                TextButton(onClick = { onNavigate("Munka") }) {
                    Text("Munka", color = JarvisMuted, fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            Surface(
                shape = CircleShape, color = Color.White, shadowElevation = 3.dp
            ) {
                IconButton(onClick = { onNavigate("Rendszerállapot") }) {
                    Text("◉", fontSize = 24.sp, color = JarvisInk)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        JarvisVoiceOrb()
        Text(
            "Jarvis",
            fontSize = 37.sp, fontWeight = FontWeight.Bold, color = JarvisInk
        )
        Text(
            "Egy agy. Hanggal mindent elérek.",
            color = JarvisMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(18.dp))
        val spokenStatus = when {
            HandsFreeStatus.speaking -> "Jarvis beszél..."
            handsFree -> "Figyelek rád..."
            else -> "Készen állok"
        }
        Surface(color = Color(0xFFE8F2FF), shape = RoundedCornerShape(28.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", fontSize = 18.sp,
                    color = if (handsFree) JarvisBlue else Color(0xFF9AAAC1))
                Spacer(Modifier.width(8.dp))
                Text(spokenStatus, fontSize = 14.sp, color = JarvisBlue)
            }
        }
        if (issue.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(issue, color = Color(0xFFAA2732), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, bottom = 14.dp)
                .shadow(9.dp, RoundedCornerShape(36.dp))
                .background(Color.White, RoundedCornerShape(36.dp))
                .padding(start = 3.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onNavigate("Fájlok") }) {
                Text("+", fontSize = 29.sp, color = JarvisInk)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = JarvisInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(text) }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isBlank()) Text("Kérdezd meg Jarvist",
                            color = JarvisMuted, fontSize = 15.sp)
                        field()
                    }
                }
            )
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    submit(text)
                } else {
                    try {
                        dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd a Jarvis parancsot")
                        })
                    } catch (_: Exception) { issue = "Nincs kompatibilis beszédfelismerő." }
                }
            }) {
                Text(if (text.isBlank()) "🎙" else "➤",
                    fontSize = 23.sp, color = JarvisInk)
            }
            Surface(
                color = if (handsFree) Color(0xFFEC455C) else JarvisBlue,
                shape = CircleShape,
                shadowElevation = 5.dp,
                modifier = Modifier.size(57.dp)
            ) {
                IconButton(onClick = {
                    if (handsFree) {
                        context.startService(
                            Intent(context, HandsFreeService::class.java)
                                .setAction(HandsFreeService.STOP)
                        )
                    } else {
                        val permissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val missing = permissions.filter {
                            ContextCompat.checkSelfPermission(context, it) !=
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) startVoice()
                        else voicePermissions.launch(missing.toTypedArray())
                    }
                }) {
                    Text(if (handsFree) "■" else "≋",
                        color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
