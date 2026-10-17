package ai.jarvis.mobile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** The upload is still explicit: these local thumbnails never initiate network IO. */
@Composable
internal fun JarvisAttachmentPreview(
    attachments: List<JarvisAttachment>,
    onRemove: (JarvisAttachment) -> Unit
) {
    if (attachments.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
        modifier = Modifier.fillMaxWidth().testTag("jarvis-attachment-preview")
    ) {
        items(attachments, key = { it.id }) { item ->
            Box(Modifier.size(90.dp).clip(RoundedCornerShape(17.dp))
                .background(JarvisPalette.panel)) {
                val bitmap = remember(item.id, item.sizeBytes) {
                    if (!item.isImage) null else runCatching {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(item.localFile.path, opts)
                        var sample = 1
                        while (opts.outWidth / sample > 240 || opts.outHeight / sample > 240) sample *= 2
                        BitmapFactory.decodeFile(item.localFile.path,
                            BitmapFactory.Options().apply { inSampleSize = sample })
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = item.displayName,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(if (item.isImage) "Kép" else item.displayName.substringAfterLast('.').uppercase().take(7),
                        color = JarvisPalette.mainText, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center))
                }
                Text(item.displayName, color = Color.White,
                    fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color(0xB0000000)).padding(horizontal = 5.dp, vertical = 4.dp))
                IconButton(
                    onClick = { onRemove(item) },
                    modifier = Modifier.align(Alignment.TopEnd).size(30.dp)
                        .testTag("jarvis-preview-remove-" + item.id)
                        .background(Color(0xDDF5F5F5), RoundedCornerShape(20.dp))
                ) {
                    Text("×", color = Color(0xFF101820),
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
