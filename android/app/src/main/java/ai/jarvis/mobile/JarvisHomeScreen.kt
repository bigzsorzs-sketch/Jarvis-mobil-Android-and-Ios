package ai.jarvis.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val JarvisBlue = Color(0xFF075FF1)
private val JarvisInk: Color @Composable get() = JarvisPalette.mainText
private val JarvisMuted: Color @Composable get() = JarvisPalette.subtleText

@Composable
internal fun JarvisHomeScreen(
    context: Context,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onMenu: () -> Unit,
    onNavigate: (String) -> Unit,
    onCommand: (String) -> Unit,
    history: List<Pair<String, String>>,
    pendingCall: VoiceCalling.Target?,
    onCallConfirm: () -> Unit,
    onCallDismiss: () -> Unit
) {
    // Retain unsent writing if Android recreates this screen while the IME changes.
    var text by rememberSaveable { mutableStateOf("") }
    // A small-height layout is essential: without it, the fixed-height orb
    // pushes the composer below the IME on smaller screens.
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var issue by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val handsFree = HandsFreeStatus.running
    var ttsReady by remember { mutableStateOf(false) }
    val player = remember(context) {
        TextToSpeech(context) { code -> ttsReady = code == TextToSpeech.SUCCESS }
    }
    DisposableEffect(player) {
        val mainThread = Handler(Looper.getMainLooper())
        player.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainThread.post { HandsFreeStatus.speaking = true }
            }
            override fun onDone(utteranceId: String?) {
                mainThread.post {
                    HandsFreeStatus.speaking = false
                    HandsFreeStatus.outputLevel = 0f
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainThread.post {
                    HandsFreeStatus.speaking = false
                    HandsFreeStatus.outputLevel = 0f
                }
            }
        })
        onDispose {
            player.stop()
            player.shutdown()
            HandsFreeStatus.speaking = false
            HandsFreeStatus.outputLevel = 0f
        }
    }

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
                if (isDark) listOf(Color(0xFF080E19), Color(0xFF0B1530), Color(0xFF080E19))
                else listOf(Color(0xFFF7FAFF), Color.White, Color(0xFFF3F8FF))
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
                shape = CircleShape, color = JarvisPalette.panel, shadowElevation = 3.dp
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
                modifier = Modifier.background(
                    if (isDark) Color(0xFF202B42) else Color(0xFFE9EEF6),
                    RoundedCornerShape(36.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = JarvisPalette.panel, shape = RoundedCornerShape(30.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable { onNavigate("Főoldal") }
                ) {
                    Text("Jarvis", color = JarvisInk, fontSize = 15.sp,
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
                IconButton(onClick = onToggleTheme) {
                    Text(if (isDark) "☀" else "☾", fontSize = 26.sp, color = JarvisInk)
                }
            }
        }
        if (!keyboardVisible) {
            // The orb and tagline deliberately collapse when the keyboard opens.
            // This reserves a guaranteed bottom area for the text composer.
            Spacer(Modifier.height(18.dp))
            JarvisVoiceOrb()
            Text("Jarvis", fontSize = 37.sp, fontWeight = FontWeight.Bold,
                color = JarvisInk)
            Spacer(Modifier.height(18.dp))
            val spokenStatus = when {
                HandsFreeStatus.speaking -> "Jarvis beszél..."
                handsFree -> "Figyelek rád..."
                else -> "Készen állok"
            }
            Surface(
                color = if (isDark) Color(0xFF192C4C) else Color(0xFFE8F2FF),
                shape = RoundedCornerShape(28.dp)
            ) {
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
        }
        if (issue.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(issue, color = Color(0xFFAA2732), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (pendingCall != null) {
            AlertDialog(
                onDismissRequest = onCallDismiss,
                title = { Text("Hívás jóváhagyása") },
                text = { Text("Fel szeretnéd hívni " + pendingCall.label +
                    " számát: " + pendingCall.number + "?") },
                confirmButton = {
                    TextButton(onClick = onCallConfirm) { Text("Hívás indítása") }
                },
                dismissButton = {
                    TextButton(onClick = onCallDismiss) { Text("Mégse") }
                }
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            history.takeLast(8).asReversed().forEach { (request, answer) ->
                Surface(
                    color = if (isDark) Color(0xFF172741) else Color(0xFFF0F4FC),
                    shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(request, color = JarvisInk, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(answer, color = JarvisMuted, fontSize = 13.sp)
                        TextButton(onClick = {
                            if (ttsReady && !handsFree) {
                                player.language = Locale.forLanguageTag("hu-HU")
                                val success = player.speak(
                                    answer, TextToSpeech.QUEUE_FLUSH, null,
                                    "home-" + System.currentTimeMillis()
                                )
                                if (success != TextToSpeech.SUCCESS)
                                    issue = "A telefon beszédmotorja nem érhető el."
                            }
                        }, enabled = ttsReady && !handsFree) {
                            Text("🔊 Felolvasás")
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp,
                    bottom = if (keyboardVisible) 5.dp else 14.dp)
                .shadow(9.dp, RoundedCornerShape(36.dp))
                .background(JarvisPalette.panel, RoundedCornerShape(36.dp))
                .padding(start = 3.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JarvisAttachmentPicker(context, isDark, onStatus = { issue = it })
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
