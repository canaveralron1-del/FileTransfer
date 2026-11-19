package com.example.filetransfer.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filetransfer.data.model.FileStatus
import com.example.filetransfer.data.model.MainUiState
import com.example.filetransfer.data.model.SelectedFileInfo
import com.example.filetransfer.ui.components.DeviceCard
import com.example.filetransfer.ui.components.DiscoveryRadar
import com.example.filetransfer.ui.components.FileTypeBadge
import com.example.filetransfer.ui.components.formatBytes
import com.example.filetransfer.ui.theme.TabularNumbers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    state: MainUiState,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (ip: String, name: String) -> Unit,
    onConnectManual: (String) -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onClearFiles: () -> Unit,
    onSendFiles: (zip: Boolean) -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefreshDevices,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = Color(0xFF10B981)
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Available devices") }

            if (state.isRefreshing && state.discoveredDevices.isEmpty()) {
                item { RadarPlaceholder(state.refreshCountdown) }
            } else {
                items(state.discoveredDevices, key = { it.ip + it.name }) { device ->
                    DeviceCard(
                        device = device,
                        isSelected = state.selectedDeviceIp == device.ip,
                        onSendClick = {
                            onSelectDevice(device.ip, device.name)
                            if (state.selectedFiles.isNotEmpty()) onSendFiles(false)
                        }
                    )
                }
                if (state.discoveredDevices.isEmpty()) {
                    item { EmptyDevicesHint(onRefreshDevices, state.qrEnabled, onScanQr) }
                }
                if (state.qrEnabled) {
                    item {
                        Surface(
                            onClick = onScanQr,
                            shape = SectionCardShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Scan QR Code",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                if (state.showManualIp) {
                    item { ManualIpInput(onConnectManual) }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Files to send")
            }

            if (state.selectedFiles.isEmpty()) {
                item { DropZone(onPickFiles) }
            } else {
                items(state.selectedFiles.size) { index ->
                    SelectedFileRow(state.selectedFiles[index], index, onRemoveFile)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = onPickFiles,
                            shape = ActionButtonShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Add more",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Surface(
                            onClick = onClearFiles,
                            shape = ActionButtonShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Text(
                                    "Clear all",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val SectionCardShape = RoundedCornerShape(16.dp)
private val ActionButtonShape = RoundedCornerShape(12.dp)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun RadarPlaceholder(countdown: Int) {
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        DiscoveryRadar(label = if (countdown > 0) "Searching (${countdown}s)" else "Searching for devices")
    }
}

@Composable
private fun EmptyDevicesHint(onRefresh: () -> Unit, qrEnabled: Boolean, onScanQr: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No devices found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Make sure the other device is in Receive mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ManualIpInput(onConnect: (String) -> Unit) {
    var ip by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            placeholder = { Text("192.168.1.100", style = TabularNumbers) },
            singleLine = true,
            shape = ActionButtonShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { if (ip.isNotBlank()) onConnect(ip.trim()) },
            enabled = ip.isNotBlank(),
            shape = ActionButtonShape
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun DropZone(onPickFiles: () -> Unit) {
    Surface(
        onClick = onPickFiles,
        shape = SectionCardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
        ) {
            Icon(
                Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Tap to select files",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Any file type supported",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SelectedFileRow(file: SelectedFileInfo, index: Int, onRemove: (Int) -> Unit) {
    val statusText = remember(file.status, file.fileSize) {
        when (file.status) {
            FileStatus.SENDING -> "Sending…"
            FileStatus.DONE -> "Done"
            FileStatus.FAILED -> "Failed"
            FileStatus.PENDING -> formatBytes(file.fileSize)
        }
    }
    Surface(shape = SectionCardShape, color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            FileTypeBadge(file.fileName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall + TabularNumbers,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onRemove(index) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove ${file.fileName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
