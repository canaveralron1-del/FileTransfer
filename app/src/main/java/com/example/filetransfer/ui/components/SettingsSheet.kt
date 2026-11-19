package com.example.filetransfer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.filetransfer.data.model.MainUiState

private data class PaletteEntry(val key: String, val label: String, val swatch: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: MainUiState,
    palette: String,
    onPaletteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleManualIp: () -> Unit,
    onToggleBackgroundReceive: () -> Unit,
    onToggleWifiDirect: () -> Unit,
    onToggleQr: () -> Unit,
    onTogglePin: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(20.dp))
            Text(
                "COLOR THEME",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val entries = listOf(
                    PaletteEntry("default", "Default", MaterialTheme.colorScheme.primary),
                    PaletteEntry("emerald", "Emerald", Color(0xFF10B981)),
                    PaletteEntry("violet", "Violet", Color(0xFF8B5CF6)),
                    PaletteEntry("teal", "Teal", Color(0xFF14B8A6)),
                    PaletteEntry("rose", "Rose", Color(0xFFF43F5E)),
                    PaletteEntry("amber", "Amber", Color(0xFFF59E0B))
                )
                entries.forEach { entry ->
                    val selected = palette == entry.key
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .semantics { contentDescription = entry.label }
                            .background(entry.swatch, CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onPaletteChange(entry.key) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsSwitchRow(
                title = "Show manual IP input",
                checked = state.showManualIp,
                onToggle = onToggleManualIp
            )
            SettingsSwitchRow(
                title = "Keep receiving in background",
                checked = state.backgroundReceive,
                onToggle = onToggleBackgroundReceive
            )
            SettingsSwitchRow(
                title = "WiFi Direct",
                checked = state.wifiDirectEnabled,
                onToggle = onToggleWifiDirect
            )
            SettingsSwitchRow(
                title = "QR code sharing",
                checked = state.qrEnabled,
                onToggle = onToggleQr
            )
            SettingsSwitchRow(
                title = "Require PIN to connect",
                checked = state.pinEnabled,
                onToggle = onTogglePin
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
