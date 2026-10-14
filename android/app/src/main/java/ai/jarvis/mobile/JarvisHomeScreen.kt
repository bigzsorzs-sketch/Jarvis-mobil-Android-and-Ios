package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val JarvisBlue = Color(0xFF075FF1)
private val JarvisInk: Color @Composable get() = JarvisPalette.mainText
private val JarvisMuted: Color @Composable get() = JarvisPalette.subtleText

@Composable
internal fun JarvisHomeScreen(
    context: Context,
    loadingConversation: Boolean = false,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    language: JarvisLanguage,
    onSelectLanguage: (JarvisLanguage) -> Unit,
    onMenu: () -> Unit,
    onCommand: (String) -> Unit,
    history: List<Pair<String, String>>,
    hasOlderHistory: Boolean,
    loadingOlderHistory: Boolean,
    onLoadOlderHistory: suspend () -> Unit,
    pendingCall: VoiceCalling.Target?,
    pendingMessage: OutgoingMessage?,
    pendingSequence: JarvisSequencePlan?,
    onSequenceConfirm: () -> Unit,
    onSequenceDismiss: () -> Unit,
    onMessageConfirm: () -> Unit,
    onMessageDismiss: () -> Unit,
    onCallConfirm: () -> Unit,
    onCallDismiss: () -> Unit,
    onAttachmentsSubmit: (String, List<JarvisAttachment>) -> Unit = { command, _ ->
        onCommand(command)
    }
) {
    // Retain unsent writing if Android recreates this screen while the IME changes.
    var text by rememberSaveable { mutableStateOf("") }
    // Staged files belong to THIS chat composer. Selection alone never uploads.
    val stagedAttachments = remember { mutableStateListOf<JarvisAttachment>() }
    // A small-height layout is essential: without it, the fixed-height orb
    // pushes the composer below the IME on smaller screens.
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var issue by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val handsFree = HandsFreeStatus.running
    val chatListState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    // New messages and completed AI answers stay at the bottom, like a messenger.
    // Loading older pages does not jump back to the newest turn.
    LaunchedEffect(history.lastOrNull()) {
        if (history.isNotEmpty()) {
            chatListState.animateScrollToItem(
                history.lastIndex + if (hasOlderHistory) 1 else 0
            )
        }
    }
    fun submit(value: String) {
        if (loadingConversation) return
        val selectedFiles = stagedAttachments.toList()
        val command = value.trim().ifBlank {
            if (selectedFiles.isNotEmpty()) "Elemezd a csatolt fájlokat." else ""
        }
        if (command.isBlank()) return
        if (selectedFiles.isNotEmpty()) {
            // Reject locally BEFORE clearing the draft or the user's files.
            JarvisAiAttachments.rejectionReason(selectedFiles)?.let { failure ->
                issue = failure
                return
            }
            if (!JarvisAiGateway.configured(context)) {
                issue = "AI-feltöltéshez a Beállítások → AI-agy alatt előbb engedélyezd és állítsd be az AI-kapcsolatot. A fájlok és az üzenet megmaradtak."
                return
            }
            if (JarvisAiGateway.mode(context) != JarvisAiGateway.DIRECT_TEST) {
                issue = "Fájlok AI-elemzése jelenleg csak közvetlen OpenRouter-kapcsolattal működik. A kiválasztott fájlok megmaradtak."
                return
            }
        }
        keyboard?.hide()
        text = ""
        issue = ""
        // The existing Chat command executor handles the actual operation;
        // the home does not invent a second, divergent command handler.
        val normalized = VoiceIntentParser.afterWakeWord(command)?.takeIf { it.isNotBlank() } ?: command
        if (selectedFiles.isEmpty()) onCommand(normalized)
        else {
            // The user's SEND gesture is explicit cloud upload consent.
            onAttachmentsSubmit(normalized, selectedFiles)
            stagedAttachments.clear()
        }
    }
    RegisterJarvisControl("Küldés", text.isNotBlank() || stagedAttachments.isNotEmpty(), click = { submit(text) })
    fun startVoice() {
        if (HandsFreeStatus.running) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.START_TAP)
            )
            // Starting speech recognition does not guarantee that voice
            // generation is configured. Keep capture available for commands,
            // but prominently explain why an audible reply may be missing.
            issue = JarvisVoiceReadiness.reason(context).orEmpty()
        } catch (_: Exception) {
            issue = "A telefon nem engedte elindítani a mikrofont."
        }
    }

    val voicePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (RuntimePermissionPolicy.mayStartVoice(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED, grants)) startVoice()
        else issue = "A kéz nélküli módhoz mikrofonengedély szükséges."
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Reserve room for chat and the composer on compact phones.
    val orbHeight = (maxHeight * 0.235f).coerceIn(100.dp, 225.dp)
    Column(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (isDark) listOf(Color(0xFF080E19), Color(0xFF0B1530), Color(0xFF080E19))
                else listOf(Color(0xFFF7FAFF), Color.White, Color(0xFFF3F8FF))
            )
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            JarvisMenuButton({ keyboard?.hide(); onMenu() },
                Modifier.align(Alignment.CenterStart).testTag("jarvis-home-drawer-trigger"))
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisLanguagePicker(language, onSelectLanguage)
                Surface(
                    shape = CircleShape, color = JarvisPalette.panel, shadowElevation = 3.dp
                ) {
                    IconButton(onClick = onToggleTheme) {
                        Text(if (isDark) "☀" else "☾", fontSize = 26.sp, color = JarvisInk)
                    }
                }
            }
        }
        if (!keyboardVisible) {
            // The orb and heading deliberately collapse when the keyboard opens.
            // This reserves a guaranteed bottom area for the text composer.
            Spacer(Modifier.height(0.dp))
            JarvisVoiceOrb(Modifier.height(orbHeight))
            Text("Jarvis", fontSize = 37.sp, fontWeight = FontWeight.Bold,
                color = JarvisInk,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Spacer(Modifier.height(6.dp))
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
        if (loadingConversation) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("jarvis-chat-loading"))
        if (issue.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(issue, color = Color(0xFFAA2732), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (HandsFreeStatus.audioError.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("AI-hang hiba: " + HandsFreeStatus.audioError,
                color = if (isDark) Color(0xFFFFB6B6) else Color(0xFFAA2732),
                fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (HandsFreeStatus.status != "Kikapcsolva") {
            Text(HandsFreeStatus.status, color = JarvisMuted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (pendingSequence != null) {
            AlertDialog(
                onDismissRequest = onSequenceDismiss,
                title = { Text("Két művelet jóváhagyása") },
                text = {
                    Text("1. Jarvis-ébresztő " +
                        String.format("%02d:%02d", pendingSequence.alarm.hour, pendingSequence.alarm.minute) +
                        "\n2. Android-alkalmazás megnyitása: " + pendingSequence.app +
                        "\n\nA második lépés csak sikeres ébresztőbeállítás után indul.")
                },
                confirmButton = {
                    ControlledTextButton(onClick = onSequenceConfirm, commandLabel = "Mindkét lépés indítása") { Text("Mindkét lépés indítása") }
                },
                dismissButton = {
                    ControlledTextButton(onClick = onSequenceDismiss, commandLabel = "Mégse") { Text("Mégse") }
                }
            )
        }
        if (pendingMessage != null) {
            AlertDialog(
                onDismissRequest = onMessageDismiss,
                title = { Text("Üzenetpiszkozat ellenőrzése") },
                text = {
                    Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            "Alkalmazás: " + pendingMessage.platform.name +
                            "\nCímzett: " + pendingMessage.recipient +
                            "\nSzöveg: " + pendingMessage.text +
                            "\n\nA küldés csak a külső alkalmazásban történhet meg."
                        )
                    }
                },
                confirmButton = {
                    ControlledTextButton(onClick = onMessageConfirm, commandLabel = "Piszkozat megnyitása") { Text("Piszkozat megnyitása") }
                },
                dismissButton = {
                    ControlledTextButton(onClick = onMessageDismiss, commandLabel = "Mégse") { Text("Mégse") }
                }
            )
        }
        if (pendingCall != null) {
            AlertDialog(
                onDismissRequest = onCallDismiss,
                title = { Text("Hívás jóváhagyása") },
                text = { Text(JarvisCallText.get(language, "dialog",
                    pendingCall.label, pendingCall.number)) },
                confirmButton = {
                    ControlledTextButton(onClick = onCallConfirm, commandLabel = "Hívás indítása") { Text("Hívás indítása") }
                },
                dismissButton = {
                    ControlledTextButton(onClick = onCallDismiss, commandLabel = "Mégse") { Text("Mégse") }
                }
            )
        }
        LazyColumn(
            state = chatListState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .testTag("jarvis-home-chat-timeline"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasOlderHistory) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ControlledOutlinedButton(
                            enabled = !loadingOlderHistory,
                            onClick = {
                                chatScope.launch {
                                    val oldCount = history.size
                                    val position = chatListState.firstVisibleItemIndex
                                    val offset = chatListState.firstVisibleItemScrollOffset
                                    onLoadOlderHistory()
                                    val added = (history.size - oldCount).coerceAtLeast(0)
                                    if (added > 0) {
                                        chatListState.scrollToItem(position + added, offset)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("jarvis-chat-older")
                        , commandLabel = if (loadingOlderHistory) "Betöltés…" else "Korábbi üzenetek") { Text(if (loadingOlderHistory) "Betöltés…" else "Korábbi üzenetek") }
                    }
                }
            }
            itemsIndexed(history) { _, turn ->
                val (request, answer) = turn
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            color = if (isDark) Color(0xFF174892) else Color(0xFFDCEAFF),
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            androidx.compose.material3.Text(request, color = JarvisInk, fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(
                            color = if (isDark) Color(0xFF172741) else Color(0xFFF0F4FC),
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier.fillMaxWidth(0.90f)
                        ) {
                            androidx.compose.material3.Text(answer, color = JarvisMuted, fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
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
            JarvisAttachmentPicker(context, isDark, onStatus = { issue = it },
                items = stagedAttachments)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    .testTag("jarvis-home-input"),
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
            // A send button is only shown for a composed text or attachment.
            // Voice has ONE entry point: the blue/red microphone on Home.
            if (text.isNotBlank() || stagedAttachments.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.testTag("jarvis-home-send"),
                    onClick = { submit(text) }
                ) {
                    Text("➤", fontSize = 23.sp, color = JarvisInk)
                }
            }
            Surface(
                color = if (handsFree) Color(0xFFEC455C) else JarvisBlue,
                shape = CircleShape,
                shadowElevation = 5.dp,
                modifier = Modifier.size(57.dp)
            ) {
                IconButton(
                    modifier = Modifier.testTag("jarvis-large-voice-mic"),
                    onClick = {
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
                    Text(if (handsFree) "■" else "🎙",
                        color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }
}

