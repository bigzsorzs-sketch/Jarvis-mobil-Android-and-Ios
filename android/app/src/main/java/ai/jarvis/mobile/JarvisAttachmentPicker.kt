package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Android-owned Camera, Photo Picker, and scoped document picker. No broad storage permission. */
@Composable
internal fun JarvisAttachmentPicker(
    context: Context, dark: Boolean, onStatus: (String) -> Unit
) {
    val items = remember { mutableStateListOf<JarvisAttachment>() }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    fun stage(uris: List<Uri>, image: Boolean, sourceFile: File? = null) {
        if (busy || uris.isEmpty()) return
        busy = true
        scope.launch {
            var accepted = 0
            var errors = ""
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    JarvisAttachments.importSelected(context, uri, items.toList(), image)
                }
                result.onSuccess { items.add(it); accepted++ }
                    .onFailure { errors = it.message ?: "Nem sikerült beolvasni a csatolmányt." }
            }
            sourceFile?.delete()
            busy = false
            onStatus(
                (if (accepted > 0) "Helyben előkészítve: " + accepted + " csatolmány. " +
                    "AI-feltöltés még nincs bekötve." else "") +
                    (if (errors.isNotBlank()) " " + errors else "")
            )
        }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(JarvisAttachments.MAX_IMAGES)
    ) { selected -> stage(selected, true) }

    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { selected -> stage(selected, false) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val target = pendingCameraFile
        pendingCameraFile = null
        if (saved && target != null) {
            val uri = FileProvider.getUriForFile(context,
                context.packageName + ".files", target)
            stage(listOf(uri), true, target)
        } else target?.delete()
    }

    IconButton(onClick = { open = true }) {
        Text("+", fontSize = 29.sp,
            color = JarvisPalette.mainText)
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { if (!busy) open = false },
            containerColor = JarvisPalette.panel
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Csatolás Jarvishoz", style = MaterialTheme.typography.titleLarge,
                    color = JarvisPalette.mainText)
                Spacer(Modifier.height(12.dp))
                Text("Legfeljebb 20 kép · összesen maximum 1 GB. " +
                    "A fájlokat a telefon helyi tárhelyén készítem elő.",
                    color = JarvisPalette.subtleText)
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = {
                    if (busy) return@TextButton
                    try {
                        val folder = File(context.cacheDir, "jarvis_attachments").apply { mkdirs() }
                        val temp = File.createTempFile("camera_", ".jpg", folder)
                        pendingCameraFile = temp
                        val uri = FileProvider.getUriForFile(context,
                            context.packageName + ".files", temp)
                        open = false
                        camera.launch(uri)
                    } catch (_: Exception) { onStatus("Nem sikerült megnyitni a kamerát.") }
                }, enabled = !busy) {
                    Text("📷  Kamera", color = JarvisPalette.mainText)
                }
                TextButton(onClick = {
                    open = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = !busy && JarvisAttachments.imageCount(items) < JarvisAttachments.MAX_IMAGES) {
                    Text("▧  Fényképek", color = JarvisPalette.mainText)
                }
                TextButton(onClick = {
                    open = false
                    files.launch(arrayOf("*/*"))
                }, enabled = !busy) {
                    Text("▤  Fájlok", color = JarvisPalette.mainText)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                val mb = JarvisAttachments.total(items).toDouble() / 1_048_576.0
                Text(
                    JarvisAttachments.imageCount(items).toString() + "/20 kép · " +
                        String.format(Locale.UK, "%.1f", mb) + " / 1024 MB",
                    color = JarvisPalette.accent
                )
                items.forEach { attachment ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(attachment.displayName.take(29), color = JarvisPalette.mainText,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            JarvisAttachments.delete(attachment)
                            items.remove(attachment)
                        }) { Text("Eltávolítás") }
                    }
                }
                Text("A csatolmányok helyben maradnak; a felhőalapú AI-feldolgozás " +
                    "még nincs összekapcsolva.",
                    color = JarvisPalette.subtleText,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}
