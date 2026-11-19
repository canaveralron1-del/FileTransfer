package com.example.filetransfer.data.model

data class ReceivedFileInfo(
    val deviceName: String,
    val fileName: String,
    val fileSize: Long,
    val timestamp: Long,
    val uri: String = "",
    val senderIp: String = ""
)

data class DiscoveredDevice(
    val ip: String,
    val name: String,
    val deviceAddress: String = ""
)

enum class FileStatus { PENDING, SENDING, DONE, FAILED }

data class SelectedFileInfo(
    val uri: android.net.Uri,
    val fileName: String,
    val fileSize: Long,
    val status: FileStatus = FileStatus.PENDING
)

enum class TransferDirection { SEND, RECEIVE }

data class ActiveTransfer(
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = -1,
    val direction: TransferDirection = TransferDirection.SEND
)

data class MainUiState(
    val isSendMode: Boolean = true,
    val statusText: String = "",
    val statusType: String = "idle",
    val progress: Int = 0,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDeviceIp: String? = null,
    val selectedDeviceName: String? = null,
    val selectedFiles: List<SelectedFileInfo> = emptyList(),
    val recentReceives: List<ReceivedFileInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val refreshCountdown: Int = 0,
    val isReceivingActive: Boolean = false,
    val showManualIp: Boolean = false,
    val backgroundReceive: Boolean = false,
    val wifiDirectEnabled: Boolean = false,
    val qrEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinCode: String = "",
    val deviceIp: String = "",
    val activeTransfer: ActiveTransfer? = null
)
