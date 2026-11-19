package com.example.filetransfer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.example.filetransfer.data.model.MainUiState
import com.example.filetransfer.data.model.ReceivedFileInfo
import com.example.filetransfer.data.model.TransferDirection
import com.example.filetransfer.ui.components.FileTransferItem
import com.example.filetransfer.ui.components.formatBytes
import com.example.filetransfer.ui.theme.LocalReduceMotion
import com.example.filetransfer.ui.theme.LocalStatusColors
import com.example.filetransfer.ui.theme.TabularNumbers
import com.example.filetransfer.ui.theme.TransferBlue
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ReceiveScreen(
    state: MainUiState,
    onShowQr: () -> Unit,
    onCancelReceive: () -> Unit,
    onOpenFile: (Int) -> Unit,
    onRemoveRecent: (Int) -> Unit,
    onUndoRemove: (Int, ReceivedFileInfo) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCopiedToast by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val ipText = state.deviceIp.ifEmpty { "—" }

    // Group recent receives by date for accordion
    val groupedByDate = remember(state.recentReceives) {
        groupByDate(state.recentReceives)
    }
    val dateKeys = groupedByDate.keys.toList()
    val expandedDates: SnapshotStateList<String> = remember { mutableListOf<String>().toMutableStateList() }

    // Announce receive-mode state changes for accessibility
    LaunchedEffect(state.isReceivingActive) {
        view.announceForAccessibility(
            if (state.isReceivingActive) "Receive mode active" else "Receive mode stopped"
        )
    }

    // Auto-expand the most recent date when the list changes
    LaunchedEffect(dateKeys) {
        if (dateKeys.isNotEmpty() && !expandedDates.contains(dateKeys.first())) {
            expandedDates.clear()
            expandedDates.add(dateKeys.first())
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        bottomBar = {
            BottomActionBar(
                isReceivingActive = state.isReceivingActive,
                hasHistory = state.recentReceives.isNotEmpty(),
                onClearAll = { showClearConfirmation = true },
                onCancelReceive = onCancelReceive
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status-aware connection card
            item {
                ConnectionStatusCard(
                    isActive = state.isReceivingActive,
                    ipText = ipText,
                    pinEnabled = state.pinEnabled,
                    pinCode = state.pinCode,
                    qrEnabled = state.qrEnabled,
                    reduceMotion = reduceMotion,
                    onCopyIp = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("IP Address", ipText)
                        @Suppress("DEPRECATION") clipboard.setPrimaryClip(clip)
                        showCopiedToast = true
                    },
                    onShowQr = onShowQr
                )
                if (showCopiedToast) {
                    LaunchedEffect(Unit) {
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showCopiedToast = false
                    }
                }
            }

            // Active Transfer Card — shown at top when receiving a file
            val activeReceive = state.activeTransfer?.takeIf { it.direction == TransferDirection.RECEIVE }
            if (activeReceive != null) {
                item {
                    ActiveTransferCard(
                        fileName = activeReceive.fileName,
                        totalBytes = activeReceive.totalBytes,
                        transferredBytes = activeReceive.transferredBytes,
                        speedBytesPerSec = activeReceive.speedBytesPerSec,
                        etaSeconds = activeReceive.etaSeconds
                    )
                }
            }

            // Transfer History Header
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "TRANSFER HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    if (state.recentReceives.isNotEmpty()) {
                        Text(
                            "${state.recentReceives.size} ${if (state.recentReceives.size == 1) "item" else "items"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // History List with Accordion
            if (state.recentReceives.isEmpty()) {
                item {
                    EmptyHistoryState(
                        isReceivingActive = state.isReceivingActive,
                        qrEnabled = state.qrEnabled,
                        onShowQr = onShowQr
                    )
                }
            } else {
                dateKeys.forEach { dateKey ->
                    val items = groupedByDate[dateKey] ?: emptyList()
                    val isExpanded = expandedDates.contains(dateKey)

                    // Sticky date label
                    item(key = "header-$dateKey") {
                        StickyDateLabel(
                            dateKey = dateKey,
                            itemCount = items.size,
                            totalBytes = items.sumOf { it.fileSize },
                            isExpanded = isExpanded,
                            onToggle = {
                                if (expandedDates.contains(dateKey)) {
                                    expandedDates.remove(dateKey)
                                } else {
                                    expandedDates.add(dateKey)
                                }
                            }
                        )
                    }

                    // Animated file items
                    if (isExpanded) {
                        items.forEachIndexed { index, info ->
                            item(key = "file-$dateKey-$index-${info.fileName}") {
                                val staggerDelay = if (reduceMotion) 0 else index * 30
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(if (reduceMotion) snap() else tween(150, delayMillis = staggerDelay)) +
                                            expandVertically(if (reduceMotion) snap() else tween(200, delayMillis = staggerDelay)),
                                    exit = fadeOut(if (reduceMotion) snap() else tween(100)) +
                                            shrinkVertically(if (reduceMotion) snap() else tween(150))
                                ) {
                                    FileTransferItem(
                                        info = info,
                                        onDismiss = {
                                            val globalIndex = state.recentReceives.indexOf(info)
                                            if (globalIndex >= 0) {
                                                onRemoveRecent(globalIndex)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "File removed",
                                                        actionLabel = "Undo",
                                                        withDismissAction = true
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        onUndoRemove(globalIndex, info)
                                                    }
                                                }
                                            }
                                        },
                                        onClick = { onOpenFile(state.recentReceives.indexOf(info)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear history?") },
            text = { Text("Clear all received file history? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    onClearAll()
                }) {
                    Text("Clear", color = LocalStatusColors.current.failed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun groupByDate(receives: List<ReceivedFileInfo>): LinkedHashMap<String, List<ReceivedFileInfo>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    val grouped = receives.groupBy { info ->
        if (info.timestamp == 0L) {
            "Unknown date"
        } else {
            val date = Instant.ofEpochMilli(info.timestamp).atZone(zone).toLocalDate()
            when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> date.format(dateFormatter)
            }
        }
    }

    val result = LinkedHashMap<String, List<ReceivedFileInfo>>()
    if (grouped.containsKey("Today")) result["Today"] = grouped["Today"]!!
    if (grouped.containsKey("Yesterday")) result["Yesterday"] = grouped["Yesterday"]!!
    grouped.keys
        .filter { it != "Today" && it != "Yesterday" && it != "Unknown date" }
        .sortedByDescending { key -> grouped[key]?.firstOrNull()?.timestamp ?: 0L }
        .forEach { key -> result[key] = grouped[key]!! }
    if (grouped.containsKey("Unknown date")) result["Unknown date"] = grouped["Unknown date"]!!

    return result
}

/**
 * Status-aware connection card. Glows emerald when actively listening, muted when idle.
 */
@Composable
private fun ConnectionStatusCard(
    isActive: Boolean,
    ipText: String,
    pinEnabled: Boolean,
    pinCode: String,
    qrEnabled: Boolean,
    reduceMotion: Boolean,
    onCopyIp: () -> Unit,
    onShowQr: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Status indicator row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = if (isActive) LocalStatusColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val dotAlpha = if (isActive && !reduceMotion) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween<Float>(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse-alpha"
                    )
                    alpha
                } else 1f

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isActive) "Listening for connections" else "Not receiving",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // IP address row — tap to copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable(onClick = onCopyIp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = "Wi-Fi",
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = ipText,
                    style = MaterialTheme.typography.titleMedium + TabularNumbers,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (pinEnabled && pinCode.isNotEmpty()) {
                    Text(
                        text = "PIN $pinCode",
                        style = MaterialTheme.typography.labelSmall + TabularNumbers,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy IP",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                if (qrEnabled) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = "Show QR Code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onShowQr)
                    )
                }
            }
        }
    }
}

/**
 * Subtle sticky date label — thin divider with date text and chevron, not a full card.
 */
@Composable
private fun StickyDateLabel(
    dateKey: String,
    itemCount: Int,
    totalBytes: Long = 0L,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "chevron-rotation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .semantics {
                role = Role.Button
                stateDescription = if (isExpanded) "expanded" else "collapsed"
            }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(
            text = dateKey,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "·  $itemCount ${if (itemCount == 1) "file" else "files"}${if (totalBytes > 0) "  ·  ${formatBytes(totalBytes)}" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation)
        )
    }
}

/**
 * Active transfer card with live progress, shown at top of the list.
 */
@Composable
private fun ActiveTransferCard(
    fileName: String,
    totalBytes: Long,
    transferredBytes: Long,
    speedBytesPerSec: Long,
    etaSeconds: Long
) {
    val progress = if (totalBytes > 0) transferredBytes.toFloat() / totalBytes else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TransferBlue.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, TransferBlue.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = TransferBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Receiving",
                    style = MaterialTheme.typography.labelMedium,
                    color = TransferBlue
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = TransferBlue,
                trackColor = TransferBlue.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatBytes(transferredBytes)} / ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall + TabularNumbers,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (speedBytesPerSec > 0) {
                    val etaText = if (etaSeconds > 0) {
                        val mins = etaSeconds / 60
                        val secs = etaSeconds % 60
                        if (mins > 0) "${mins}m ${secs}s left" else "${secs}s left"
                    } else ""
                    Text(
                        text = "${formatBytes(speedBytesPerSec)}/s${if (etaText.isNotEmpty()) "  ·  $etaText" else ""}",
                        style = MaterialTheme.typography.bodySmall + TabularNumbers,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Empty state — styled like SendScreen's DropZone for visual consistency.
 * Shows contextual message and a QR CTA when actively receiving.
 */
@Composable
private fun EmptyHistoryState(
    isReceivingActive: Boolean,
    qrEnabled: Boolean = false,
    onShowQr: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isReceivingActive) Icons.Outlined.CloudDownload else Icons.Outlined.Inbox,
            contentDescription = null,
            tint = if (isReceivingActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isReceivingActive) "Waiting for files…" else "No files received yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isReceivingActive) "Files sent to this device will appear here"
                   else "Switch to receive mode to accept files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun BottomActionBar(
    isReceivingActive: Boolean,
    hasHistory: Boolean,
    onClearAll: () -> Unit,
    onCancelReceive: () -> Unit
) {
    if (!isReceivingActive && !hasHistory) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isReceivingActive) {
                Button(
                    onClick = onCancelReceive,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalStatusColors.current.failed.copy(alpha = 0.15f),
                        contentColor = LocalStatusColors.current.failed
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Outlined.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Stop Receiving", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (hasHistory) {
                FilledTonalButton(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clear History", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
