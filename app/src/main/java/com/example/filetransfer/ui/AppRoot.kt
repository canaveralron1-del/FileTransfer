package com.example.filetransfer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog as AppCompatAlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.filetransfer.MainActivity
import com.example.filetransfer.R
import com.example.filetransfer.data.network.QrUtils
import com.example.filetransfer.presentation.MainViewModel
import com.example.filetransfer.ui.components.SettingsSheet
import com.example.filetransfer.ui.components.TransferCard
import com.example.filetransfer.ui.screens.ReceiveScreen
import com.example.filetransfer.ui.screens.SendScreen
import com.example.filetransfer.ui.theme.FileTransferTheme
import com.example.filetransfer.ui.theme.LocalStatusColors
import com.example.filetransfer.ui.theme.TabularNumbers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    viewModel: MainViewModel,
    pendingTab: MutableIntState,
    onPickFiles: () -> Unit,
    onScanQr: () -> Unit,
    onRequestReceivePermissions: (onGranted: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    var palette by remember { mutableStateOf(prefs.getString(MainActivity.KEY_PALETTE, "default") ?: "default") }

    FileTransferTheme(palette) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        var showSettings by remember { mutableStateOf(false) }
        var pinDialogFor by remember { mutableStateOf<Pair<String, String>?>(null) } // cleanName to expectedPin
        var pinInput by remember { mutableStateOf("") }
        var pinTargetIp by remember { mutableStateOf("") }

        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

        LaunchedEffect(pendingTab.intValue) {
            if (pagerState.currentPage != pendingTab.intValue) {
                pagerState.animateScrollToPage(pendingTab.intValue)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage == 1) {
                viewModel.switchToReceiveMode()
                onRequestReceivePermissions { viewModel.startReceiving() }
            } else {
                viewModel.switchToSendMode()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("File Transfer", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        if (pagerState.currentPage == 0) {
                            IconButton(
                                onClick = { viewModel.refreshDeviceList() },
                                enabled = !state.isRefreshing
                            ) {
                                Icon(
                                    Icons.Outlined.Radar,
                                    contentDescription = "Rescan devices",
                                    tint = if (state.isRefreshing)
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    TransferCard(
                        transfer = state.activeTransfer,
                        onCancel = viewModel::cancelTransfer,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    AnimatedVisibility(visible = state.activeTransfer == null) {
                        if (state.statusText.isNotEmpty()) {
                            val statusColor = when (state.statusType) {
                                "error" -> LocalStatusColors.current.failed
                                "success" -> LocalStatusColors.current.success
                                "transfer" -> LocalStatusColors.current.transfer
                                else -> LocalStatusColors.current.idle
                            }
                            Text(
                                text = state.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 10.dp, bottom = 18.dp)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    listOf("Send", "Receive").forEachIndexed { index, label ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> SendScreen(
                            state = state,
                            onRefreshDevices = viewModel::refreshDeviceList,
                            onSelectDevice = { ip, name ->
                                val pinMatch = Regex(" \\[PIN: (\\d+)\\]$").find(name)
                                if (pinMatch != null && state.pinEnabled) {
                                    pinDialogFor = name.replace(Regex(" \\[PIN: \\d+\\]$"), "") to pinMatch.groupValues[1]
                                    pinTargetIp = ip
                                    pinInput = ""
                                } else {
                                    viewModel.selectDevice(ip, name)
                                }
                            },
                            onConnectManual = viewModel::connectManual,
                            onPickFiles = onPickFiles,
                            onRemoveFile = viewModel::removeFile,
                            onClearFiles = viewModel::clearFiles,
                            onSendFiles = viewModel::confirmSend,
                            onScanQr = onScanQr
                        )
                        else -> ReceiveScreen(
                            state = state,
                            onShowQr = {
                                QrUtils.generateQrBitmap(state.deviceIp)?.let { bitmap ->
                                    AppCompatAlertDialog.Builder(context)
                                        .setTitle(R.string.qr_code_title)
                                        .setView(ImageView(context).apply {
                                            setImageBitmap(bitmap)
                                            setPadding(32, 32, 32, 32)
                                        })
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                            },
                            onCancelReceive = viewModel::cancelReceive,
                            onOpenFile = { index ->
                                state.recentReceives.getOrNull(index)?.let { info ->
                                    openReceived(context, info.uri, info.fileName)
                                }
                            },
                            onRemoveRecent = viewModel::removeRecentReceive,
                            onClearAll = viewModel::clearRecentReceives,
                            onUndoRemove = viewModel::undoRemoveRecent
                        )
                    }
                }
            }
        }

        if (showSettings) {
            SettingsSheet(
                state = state,
                palette = palette,
                onPaletteChange = { selected ->
                    prefs.edit().putString(MainActivity.KEY_PALETTE, selected).apply()
                    palette = selected
                },
                onDismiss = { showSettings = false },
                onToggleManualIp = viewModel::toggleManualIp,
                onToggleBackgroundReceive = viewModel::toggleBackgroundReceive,
                onToggleWifiDirect = viewModel::toggleWifiDirect,
                onToggleQr = viewModel::toggleQrEnabled,
                onTogglePin = viewModel::togglePinEnabled
            )
        }

        pinDialogFor?.let { (cleanName, expectedPin) ->
            AlertDialog(
                onDismissRequest = { pinDialogFor = null },
                title = { Text("Enter PIN") },
                text = {
                    Column {
                        Text("Enter the PIN shown on $cleanName", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                            singleLine = true,
                            placeholder = { Text("PIN", style = TabularNumbers) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput == expectedPin) {
                            viewModel.selectDevice(pinTargetIp, cleanName)
                        } else {
                            Toast.makeText(context, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
                        }
                        pinDialogFor = null
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { pinDialogFor = null }) { Text("Cancel") }
                }
            )
        }
    }
}

private fun openReceived(context: Context, uriString: String, fileName: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), mimeFor(fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, fileName))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
    }
}

private fun mimeFor(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: "application/octet-stream"
}
