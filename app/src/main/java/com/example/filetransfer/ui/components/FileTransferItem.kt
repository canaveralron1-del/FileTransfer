package com.example.filetransfer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.filetransfer.ui.theme.LocalReduceMotion
import com.example.filetransfer.data.model.ReceivedFileInfo
import com.example.filetransfer.ui.theme.FileTransferTheme
import com.example.filetransfer.ui.theme.FileApk
import com.example.filetransfer.ui.theme.FileArchive
import com.example.filetransfer.ui.theme.FileAudio
import com.example.filetransfer.ui.theme.FileCode
import com.example.filetransfer.ui.theme.FileImage
import com.example.filetransfer.ui.theme.FileOther
import com.example.filetransfer.ui.theme.FileVideo
import com.example.filetransfer.ui.theme.SwipeDangerRed
import com.example.filetransfer.ui.theme.TabularNumbers

data class FileTypeVisual(val icon: ImageVector, val tint: Color, val backgroundTint: Color)

// Pre-allocated shape constants — avoid creating new objects on every recomposition
private val CardShape = RoundedCornerShape(16.dp)
private val BadgeShape = RoundedCornerShape(14.dp)
private val SwipeBackgroundShape = RoundedCornerShape(16.dp)

// Cached visuals — one instance per file type, never reallocated
private val ImageVisual = FileTypeVisual(Icons.Outlined.Image, FileImage, Color(0xFF4ADE80))
private val VideoVisual = FileTypeVisual(Icons.Outlined.VideoFile, FileVideo, Color(0xFF60A5FA))
private val AudioVisual = FileTypeVisual(Icons.Outlined.AudioFile, FileAudio, Color(0xFFA78BFA))
private val ArchiveVisual = FileTypeVisual(Icons.Outlined.Archive, FileArchive, Color(0xFF10B981))
private val PdfVisual = FileTypeVisual(Icons.Outlined.Archive, FileArchive, Color(0xFFEF4444))
private val DocVisual = FileTypeVisual(Icons.AutoMirrored.Outlined.InsertDriveFile, FileArchive, Color(0xFF2563EB))
private val CodeVisual = FileTypeVisual(Icons.Outlined.Code, FileCode, Color(0xFFF59E0B))
private val ApkVisual = FileTypeVisual(Icons.Outlined.Android, FileApk, Color(0xFF84CC16))
private val FolderVisual = FileTypeVisual(Icons.Outlined.Folder, FileOther, Color(0xFF94A3B8))
private val DefaultFileVisual = FileTypeVisual(Icons.AutoMirrored.Outlined.InsertDriveFile, FileOther, Color(0xFF94A3B8))

fun fileTypeIcon(fileName: String): FileTypeVisual {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> ImageVisual
        "mp4", "mkv", "avi", "mov", "webm" -> VideoVisual
        "mp3", "wav", "flac", "ogg", "m4a" -> AudioVisual
        "zip", "rar", "7z", "tar", "gz" -> ArchiveVisual
        "pdf" -> PdfVisual
        "doc", "docx" -> DocVisual
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "xml", "json", "html", "css" -> CodeVisual
        "apk" -> ApkVisual
        else -> if (ext.isEmpty()) FolderVisual else DefaultFileVisual
    }
}

@Composable
fun FileTypeBadge(fileName: String, modifier: Modifier = Modifier) {
    val visual = remember(fileName) { fileTypeIcon(fileName) }
    val bgColor = remember(visual) { visual.backgroundTint.copy(alpha = 0.15f) }
    Box(
        modifier = modifier
            .size(44.dp)
            .background(bgColor, BadgeShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.backgroundTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

private val US_LOCALE = java.util.Locale.US

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(US_LOCALE, "%.2f GB", gb)
        mb >= 1 -> String.format(US_LOCALE, "%.1f MB", mb)
        kb >= 1 -> String.format(US_LOCALE, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

// Thread-local cached formatter — avoids allocating SimpleDateFormat per call
private val timeFormatter = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue() = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
}
private val reusableDate = java.util.Date()

fun formatTime(epochMillis: Long): String {
    if (epochMillis == 0L) return "—"
    reusableDate.time = epochMillis
    return timeFormatter.get()!!.format(reusableDate)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTransferItem(
    info: ReceivedFileInfo,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth().clip(CardShape),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> SwipeDangerRed
                    else -> Color.Transparent
                },
                animationSpec = if (reduceMotion) snap() else tween(),
                label = "swipe-bg"
            )
            val scale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f,
                animationSpec = if (reduceMotion) snap() else tween(),
                label = "icon-scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, SwipeBackgroundShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.scale(scale)
                )
            }
        }
    ) {
        val elevation by animateDpAsState(
            targetValue = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) 4.dp else 1.dp,
            animationSpec = if (reduceMotion) snap() else tween(),
            label = "card-elevation"
        )

        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onDismiss
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .semantics(mergeDescendants = true) { }
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                FileTypeBadge(info.fileName)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatBytes(info.fileSize),
                            style = MaterialTheme.typography.bodySmall + TabularNumbers,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatTime(info.timestamp),
                            style = MaterialTheme.typography.bodySmall + TabularNumbers,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (info.deviceName.isNotEmpty()) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = info.deviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun FileTransferItemPreview() {
    FileTransferTheme {
        Column(Modifier.padding(16.dp)) {
            FileTransferItem(
                info = ReceivedFileInfo(
                    deviceName = "Pixel 9",
                    fileName = "holiday-photos-2026-backup.zip",
                    fileSize = 15_728_640L,
                    senderIp = "192.168.1.42",
                    timestamp = System.currentTimeMillis()
                ),
                onDismiss = {},
                onClick = {}
            )
        }
    }
}
