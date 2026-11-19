package com.example.filetransfer.presentation

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filetransfer.R
import com.example.filetransfer.data.local.FileStorage
import com.example.filetransfer.data.model.ActiveTransfer
import com.example.filetransfer.data.model.FileStatus
import com.example.filetransfer.data.model.MainUiState
import com.example.filetransfer.data.model.ReceivedFileInfo
import com.example.filetransfer.data.model.SelectedFileInfo
import com.example.filetransfer.data.model.TransferDirection
import com.example.filetransfer.data.network.DeviceDiscovery
import com.example.filetransfer.data.network.FileTransfer
import com.example.filetransfer.data.network.WifiDirectManager
import com.example.filetransfer.service.ReceiveService
import com.example.filetransfer.service.ServiceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val app: Application by lazy { getApplication() }
    private val deviceNameMap = mutableMapOf<String, String>()
    private var responderJob: Job? = null
    private var refreshJob: Job? = null
    private var serviceEventJob: Job? = null
    private val prefs = app.getSharedPreferences("recent", Context.MODE_PRIVATE)
    private val recentReceives = loadRecentReceives().toMutableList()
    private val wifiDirectManager = WifiDirectManager(app)
    private val resumeCheckpoints = mutableMapOf<String, Long>()

    init {
        _uiState.update { it.copy(recentReceives = recentReceives.toList()) }
        startDiscoveryResponder()
        observeServiceEvents()
        setupWifiDirect()
    }

    private fun setupWifiDirect() {
        wifiDirectManager.onPeersUpdated = { peers ->
            val devices = peers.map {
                com.example.filetransfer.data.model.DiscoveredDevice(
                    ip = "P2P:${it.deviceAddress}",
                    name = "${it.deviceName} (WiFi Direct)",
                    deviceAddress = it.deviceAddress
                )
            }
            _uiState.update { it.copy(discoveredDevices = devices) }
        }
        wifiDirectManager.onConnected = { goIp, name ->
            _uiState.update {
                it.copy(
                    selectedDeviceIp = goIp,
                    selectedDeviceName = name,
                    statusText = app.getString(R.string.wifi_direct_connected, name),
                    statusType = "success"
                )
            }
        }
        wifiDirectManager.onDisconnected = {
            _uiState.update {
                it.copy(
                    statusText = app.getString(R.string.wifi_direct_disconnected),
                    statusType = "error"
                )
            }
        }
        wifiDirectManager.onError = { msg ->
            _uiState.update {
                it.copy(
                    statusText = msg,
                    statusType = "error"
                )
            }
        }
    }

    private var sendJob: Job? = null
    private var lastTransferPushMs = 0L

    private fun observeServiceEvents() {
        serviceEventJob = viewModelScope.launch {
            ReceiveService.events.collect { event ->
                when (event) {
                    is ServiceEvent.Ready -> {
                        _uiState.update {
                            it.copy(
                                isReceivingActive = true,
                                statusText = app.getString(R.string.ready_to_receive),
                                statusType = "idle"
                            )
                        }
                    }
                    is ServiceEvent.Progress -> {
                        pushTransferProgress(
                            TransferDirection.RECEIVE, event.fileName,
                            event.totalBytes, event.receivedBytes
                        )
                        _uiState.update { it.copy(progress = event.percent) }
                    }
                    is ServiceEvent.FileReceived -> {
                        val senderName = deviceNameMap[event.senderIp] ?: event.senderIp
                        val info = ReceivedFileInfo(senderName, event.fileName, event.fileSize, event.timestamp, event.uri, event.senderIp)
                        recentReceives.add(0, info)
                        saveRecentReceives()
                        _uiState.update {
                            it.copy(
                                recentReceives = recentReceives.toList(),
                                progress = 0,
                                activeTransfer = null,
                                statusText = app.getString(R.string.received_file, event.fileName, senderName),
                                statusType = "success"
                            )
                        }
                    }
                    is ServiceEvent.Error -> {
                        Log.e("ReceiveService", "error: ${event.message}")
                        _uiState.update {
                            it.copy(
                                isReceivingActive = false,
                                activeTransfer = null,
                                statusText = app.getString(R.string.receive_failed, event.message),
                                statusType = "error"
                            )
                        }
                    }
                }
            }
        }
    }

    fun switchToSendMode() {
        if (!_uiState.value.backgroundReceive) {
            stopReceiveService()
        }
        _uiState.update {
            it.copy(
                isSendMode = true,
                isReceivingActive = false,
                statusText = app.getString(R.string.send_mode_select_device),
                statusType = "idle"
            )
        }
    }

    fun switchToReceiveMode() {
        if (!_uiState.value.backgroundReceive) {
            stopReceiveService()
        }
        val ip = getDeviceIp()
        _uiState.update {
            it.copy(
                isSendMode = false,
                isReceivingActive = false,
                deviceIp = ip,
                statusText = app.getString(R.string.receive_mode_waiting),
                statusType = "idle"
            )
        }
    }

    fun startReceiving() {
        ReceiveService.pinCode = _uiState.value.pinCode
        ReceiveService.start(app)
        _uiState.update { it.copy(isReceivingActive = true) }
    }

    fun selectDevice(ip: String, name: String) {
        _uiState.update {
            it.copy(
                selectedDeviceIp = ip,
                selectedDeviceName = name,
                statusText = app.getString(R.string.ready_to_send_to, name),
                statusType = "idle"
            )
        }
    }

    fun connectManual(ip: String) {
        _uiState.update {
            it.copy(
                selectedDeviceIp = ip,
                selectedDeviceName = "Manual IP: $ip",
                statusText = app.getString(R.string.ready_to_send_to, ip),
                statusType = "idle"
            )
        }
    }

    fun refreshDeviceList() {
        if (_uiState.value.isRefreshing) return // already scanning; ignore until complete
        if (!_uiState.value.isSendMode) {
            _uiState.update {
                it.copy(statusText = app.getString(R.string.switch_to_send_mode), statusType = "error")
            }
            return
        }
        refreshJob?.cancel()

        if (_uiState.value.wifiDirectEnabled) {
            _uiState.update { it.copy(isRefreshing = true, statusText = app.getString(R.string.wifi_direct_searching), statusType = "transfer") }
            wifiDirectManager.discoverPeers()
            viewModelScope.launch {
                kotlinx.coroutines.delay(6000)
                _uiState.update {
                    val devices = it.discoveredDevices
                    it.copy(
                        isRefreshing = false,
                        statusText = if (devices.isEmpty()) app.getString(R.string.no_devices_found)
                        else app.getString(R.string.found_devices, devices.size),
                        statusType = if (devices.isEmpty()) "error" else "success"
                    )
                }
            }
            return
        }

        _uiState.update { it.copy(isRefreshing = true, statusText = app.getString(R.string.refreshing_devices), statusType = "transfer") }

        viewModelScope.launch(Dispatchers.IO) {
            DeviceDiscovery.scanForDevices(
                onProgress = { _, _ -> },
                onFound = { devices ->
                    deviceNameMap.clear()
                    devices.forEach { deviceNameMap[it.ip] = it.name }
                    val pairs = devices.map { com.example.filetransfer.data.model.DiscoveredDevice(it.ip, it.name) }
                    _uiState.update {
                        it.copy(
                            discoveredDevices = pairs, isRefreshing = false, refreshCountdown = 0,
                            statusText = if (devices.isEmpty()) app.getString(R.string.no_devices_found)
                            else app.getString(R.string.found_devices, devices.size),
                            statusType = if (devices.isEmpty()) "error" else "success",
                            selectedDeviceIp = if (devices.isEmpty()) null else it.selectedDeviceIp,
                            selectedDeviceName = if (devices.isEmpty()) null else it.selectedDeviceName
                        )
                    }
                },
                onError = { e ->
                    Log.e("DeviceDiscovery", "scan failed", e)
                    _uiState.update {
                        it.copy(isRefreshing = false, refreshCountdown = 0,
                            statusText = app.getString(R.string.discovery_error, e.message), statusType = "error")
                    }
                }
            )
        }
    }

    fun selectFiles(uris: List<Uri>, shouldZip: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFiles = uris.mapNotNull { uri ->
                val name = FileStorage.getFileNameFromUri(uri, app.contentResolver) ?: return@mapNotNull null
                val size = FileStorage.getFileSize(uri, app.contentResolver)
                SelectedFileInfo(uri, name, size)
            }
            _uiState.update {
                val allFiles = it.selectedFiles + newFiles
                it.copy(
                    selectedFiles = allFiles,
                    statusText = if (allFiles.isEmpty()) app.getString(R.string.no_file_selected)
                    else app.getString(R.string.files_selected, allFiles.size),
                    statusType = "idle"
                )
            }
        }
    }

    fun removeFile(index: Int) {
        val current = _uiState.value.selectedFiles.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(selectedFiles = current) }
        }
    }

    fun clearFiles() {
        _uiState.update { it.copy(selectedFiles = emptyList()) }
    }

    fun confirmSend(forceZip: Boolean = false) {
        val state = _uiState.value
        val ip = state.selectedDeviceIp ?: return
        val files = state.selectedFiles
        if (files.isEmpty()) return

        if (state.pinEnabled && state.pinCode.isEmpty()) {
            _uiState.update { it.copy(statusText = app.getString(R.string.error_pin_required), statusType = "error") }
            return
        }

        _uiState.update { it.copy(statusText = app.getString(R.string.preparing_files, files.size), statusType = "transfer") }

        sendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val zipEnabled = forceZip || files.size > 1
                if (zipEnabled) {
                    sendZipped(files.map { it.uri }, ip)
                    _uiState.update {
                        it.copy(
                            statusText = app.getString(R.string.send_completed),
                            statusType = "success",
                            progress = 0,
                            activeTransfer = null,
                            selectedFiles = emptyList()
                        )
                    }
                } else {
                    sendIndividual(files.map { it.uri }, ip)
                    val hasFailed = _uiState.value.selectedFiles.any { it.status == FileStatus.FAILED }
                    if (!hasFailed) {
                        _uiState.update {
                            it.copy(
                                statusText = app.getString(R.string.send_completed),
                                statusType = "success",
                                progress = 0,
                                activeTransfer = null,
                                selectedFiles = emptyList()
                            )
                        }
                    } else {
                        _uiState.update { it.copy(activeTransfer = null) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FileSend", "send failed", e)
                val wifiDirectHint = if (!_uiState.value.wifiDirectEnabled)
                    " " + app.getString(R.string.try_wifi_direct) else ""
                val msg = when {
                    e.message?.contains("ECONNREFUSED") == true -> app.getString(R.string.error_receiver_not_ready) + wifiDirectHint
                    e.message?.contains("timeout") == true -> app.getString(R.string.error_connection_timeout) + wifiDirectHint
                    e.message?.contains("blocked") == true -> e.message!! + wifiDirectHint
                    else -> app.getString(R.string.send_failed, e.message) + wifiDirectHint
                }
                _uiState.update { it.copy(statusText = msg, statusType = "error", activeTransfer = null) }
            }
        }
    }

    private suspend fun connectWithRetry(ip: String, port: Int): Socket {
        val delays = listOf(1000L, 2000L, 4000L)
        var lastError: Exception? = null
        for ((attempt, delay) in delays.withIndex()) {
            try {
                _uiState.update { it.copy(statusText = app.getString(R.string.connecting_attempt, attempt + 1, delays.size), statusType = "transfer") }
                val socket = Socket()
                FileTransfer.tuneSocket(socket)
                socket.connect(InetSocketAddress(ip, port), 5000)
                return FileTransfer.wrapSocketAsClient(socket)
            } catch (e: SocketTimeoutException) {
                lastError = e
                if (attempt == delays.lastIndex) {
                    throw IOException(describeBlockedConnection(ip))
                }
                kotlinx.coroutines.delay(delay)
            } catch (e: ConnectException) {
                lastError = e
                if (attempt == delays.lastIndex) throw e
                kotlinx.coroutines.delay(delay)
            } catch (e: NoRouteToHostException) {
                lastError = e
                if (attempt == delays.lastIndex) throw e
                kotlinx.coroutines.delay(delay)
            }
        }
        throw lastError ?: ConnectException("Connection failed to $ip:$port")
    }

    private fun describeBlockedConnection(ip: String): String {
        val ownIp = DeviceDiscovery.getLocalIpAddress()
        val ownPrefix = ownIp.substringBeforeLast('.')
        val targetPrefix = ip.substringBeforeLast('.')
        val sameSubnet = ownPrefix == targetPrefix
        return if (sameSubnet) {
            app.getString(R.string.error_connection_blocked_same_subnet)
        } else {
            app.getString(R.string.error_connection_blocked_different_subnet)
        }
    }

    private fun isPrecompressedType(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return ext in PRECOMPRESSED_EXTENSIONS
    }

    private suspend fun sendZipped(uris: List<Uri>, ip: String) {
        val zipFile = File(app.cacheDir, "files_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (uri in uris) {
                val name = FileStorage.getFileNameFromUri(uri, app.contentResolver) ?: continue
                // Precompressed media: deflate with NO_COMPRESSION -> stored blocks,
                // single read pass, near-zero CPU, CRC computed by the zip stream.
                zos.setLevel(
                    if (isPrecompressedType(name)) java.util.zip.Deflater.NO_COMPRESSION
                    else java.util.zip.Deflater.DEFAULT_COMPRESSION
                )
                app.contentResolver.openInputStream(uri)?.use { inp ->
                    zos.putNextEntry(ZipEntry(name))
                    inp.copyTo(zos, bufferSize = FileTransfer.BUFFER_SIZE)
                    zos.closeEntry()
                }
            }
        }
        _uiState.update { it.copy(statusText = app.getString(R.string.sending_zip, zipFile.length() / 1024), statusType = "transfer") }
        val socket = connectWithRetry(ip, FileTransfer.PORT)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileTransfer.sendFile(socket, zipFile.name, zipFile.length(), resumeOffset = 0, dataSource = { out, skip ->
                var sent = skip
                zipFile.inputStream().use { inp ->
                    if (skip > 0) inp.skip(skip)
                    val buf = ByteArray(FileTransfer.BUFFER_SIZE)
                    while (true) {
                        val rd = inp.read(buf)
                        if (rd == -1) break
                        digest.update(buf, 0, rd)
                        out.write(buf, 0, rd)
                        sent += rd
                        pushTransferProgress(TransferDirection.SEND, zipFile.name, zipFile.length(), sent)
                    }
                }
            }) { percent ->
                _uiState.update { it.copy(progress = percent) }
            }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            FileTransfer.sendChecksum(DataOutputStream(socket.getOutputStream()), checksum)
        } finally {
            FileTransfer.closeAfterSend(socket)
        }
        zipFile.delete()
    }

    private suspend fun sendIndividual(uris: List<Uri>, ip: String) {
        val total = uris.size
        // One TLS session for the whole batch; receiver loops until we close.
        val socket = connectWithRetry(ip, FileTransfer.PORT)
        try {
            val pin = _uiState.value.pinCode
            if (pin.isNotEmpty()) {
                FileTransfer.sendPin(socket, pin)
                if (!FileTransfer.receivePinResult(socket)) {
                    throw IOException(app.getString(R.string.pin_incorrect))
                }
            }
            for ((index, uri) in uris.withIndex()) {
                val name = FileStorage.getFileNameFromUri(uri, app.contentResolver) ?: continue
                val size = FileStorage.getFileSize(uri, app.contentResolver)
                if (size <= 0) { updateFileStatus(index, FileStatus.FAILED); continue }
                updateFileStatus(index, FileStatus.SENDING)
                val checkpointKey = "$ip:$name"
                val resumeOffset = resumeCheckpoints.remove(checkpointKey) ?: 0L
                _uiState.update {
                    it.copy(statusText = app.getString(R.string.sending_file_number, index + 1, total, name), statusType = "transfer")
                }
                var bytesWritten = resumeOffset
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    FileTransfer.sendFile(socket, name, size, resumeOffset, { out, skip ->
                        app.contentResolver.openInputStream(uri)?.use { inp ->
                            if (skip > 0) inp.skip(skip)
                            val buf = ByteArray(FileTransfer.BUFFER_SIZE)
                            while (true) {
                                val rd = inp.read(buf)
                                if (rd == -1) break
                                digest.update(buf, 0, rd)
                                out.write(buf, 0, rd)
                                bytesWritten += rd
                            }
                        }
                    }) { percent ->
                        val overall = (index * 100 + percent) / total
                        pushTransferProgress(TransferDirection.SEND, name, size, bytesWritten)
                        _uiState.update {
                            it.copy(progress = overall, statusText = "[${index + 1}/$total] $name — ${percent}%", statusType = "transfer")
                        }
                    }
                    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                    FileTransfer.sendChecksum(DataOutputStream(socket.getOutputStream()), checksum)
                    updateFileStatus(index, FileStatus.DONE)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("FileSend", "send failed for $name", e)
                    if (bytesWritten > 0) {
                        resumeCheckpoints[checkpointKey] = bytesWritten
                    }
                    updateFileStatus(index, FileStatus.FAILED)
                    _uiState.update { it.copy(statusText = app.getString(R.string.send_failed, "$name — ${e.message}"), statusType = "error") }
                    break // stream state unknown after an error; abort remaining batch
                }
            }
        } finally {
            FileTransfer.closeAfterSend(socket)
        }
    }

    private fun updateFileStatus(index: Int, status: FileStatus) {
        _uiState.update { state ->
            val files = state.selectedFiles.toMutableList()
            if (index in files.indices) {
                files[index] = files[index].copy(status = status)
            }
            state.copy(selectedFiles = files)
        }
    }

    fun cancelReceive() {
        stopReceiveService()
        _uiState.update {
            it.copy(
                isReceivingActive = false,
                statusText = app.getString(R.string.receive_stopped),
                statusType = "error", progress = 0
            )
        }
    }

    fun cancelTransfer() {
        val transfer = _uiState.value.activeTransfer ?: return
        if (transfer.direction == TransferDirection.SEND) {
            sendJob?.cancel()
            sendJob = null
            _uiState.update {
                it.copy(
                    activeTransfer = null,
                    progress = 0,
                    statusText = app.getString(R.string.transfer_cancelled),
                    statusType = "error"
                )
            }
        } else {
            cancelReceive()
        }
    }

    private val sendRateMeter = RateMeter()
    private val receiveRateMeter = RateMeter()

    private fun pushTransferProgress(
        direction: TransferDirection,
        fileName: String,
        totalBytes: Long,
        transferredBytes: Long
    ) {
        val current = _uiState.value.activeTransfer
        val meter = if (direction == TransferDirection.SEND) sendRateMeter else receiveRateMeter
        if (current?.fileName != fileName || current.direction != direction) meter.reset()

        val now = System.currentTimeMillis()
        val isFinal = totalBytes in 1..transferredBytes
        if (!isFinal && now - lastTransferPushMs < 100) return
        lastTransferPushMs = now

        val speed = meter.sample(transferredBytes)
        val eta = if (speed > 0 && totalBytes > transferredBytes) {
            (totalBytes - transferredBytes) / speed
        } else -1L

        _uiState.update {
            it.copy(
                activeTransfer = ActiveTransfer(
                    fileName = fileName,
                    totalBytes = totalBytes,
                    transferredBytes = transferredBytes,
                    speedBytesPerSec = speed,
                    etaSeconds = eta,
                    direction = direction
                )
            )
        }
    }

    fun toggleManualIp() {
        _uiState.update { it.copy(showManualIp = !it.showManualIp) }
    }

    fun toggleBackgroundReceive() {
        _uiState.update { it.copy(backgroundReceive = !it.backgroundReceive) }
    }

    fun toggleWifiDirect() {
        val current = _uiState.value.wifiDirectEnabled
        if (current) {
            wifiDirectManager.disconnect()
            wifiDirectManager.destroy()
            _uiState.update { it.copy(wifiDirectEnabled = false) }
        } else {
            wifiDirectManager.initialize()
            _uiState.update { it.copy(wifiDirectEnabled = true) }
        }
    }

    fun wifiDirectConnect(deviceAddress: String, deviceName: String) {
        wifiDirectManager.connect(deviceAddress, deviceName)
        _uiState.update {
            it.copy(
                statusText = app.getString(R.string.wifi_direct_connecting, deviceName),
                statusType = "transfer"
            )
        }
    }

    fun toggleQrEnabled() {
        _uiState.update { it.copy(qrEnabled = !it.qrEnabled) }
    }

    fun togglePinEnabled() {
        val current = _uiState.value.pinEnabled
        if (current) {
            _uiState.update { it.copy(pinEnabled = false, pinCode = "") }
        } else {
            val pin = String.format(Locale.US, "%04d", (1000..9999).random())
            _uiState.update { it.copy(pinEnabled = true, pinCode = pin) }
        }
        restartDiscoveryResponder()
    }

    private fun restartDiscoveryResponder() {
        responderJob?.cancel()
        startDiscoveryResponder()
    }

    fun removeRecentReceive(index: Int) {
        if (index in recentReceives.indices) {
            recentReceives.removeAt(index)
            saveRecentReceives()
            _uiState.update { it.copy(recentReceives = recentReceives.toList()) }
        }
    }

    fun undoRemoveRecent(index: Int, info: ReceivedFileInfo) {
        val clampedIndex = index.coerceIn(0, recentReceives.size)
        recentReceives.add(clampedIndex, info)
        saveRecentReceives()
        _uiState.update { it.copy(recentReceives = recentReceives.toList()) }
    }

    fun clearRecentReceives() {
        recentReceives.clear()
        prefs.edit().remove("receives").apply()
        _uiState.update { it.copy(recentReceives = emptyList()) }
    }

    private fun loadRecentReceives(): List<ReceivedFileInfo> {
        val json = prefs.getString("receives", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                // Migration: old entries stored timestamp as a time-only string (HH:mm:ss).
                // New entries store epoch millis as a Long.
                val timestamp = if (obj.optLong("timestamp", -1L) != -1L) {
                    obj.getLong("timestamp")
                } else {
                    // Old string timestamp — no date info, fall back to 0
                    0L
                }
                ReceivedFileInfo(
                    deviceName = obj.getString("deviceName"),
                    fileName = obj.getString("fileName"),
                    fileSize = obj.getLong("fileSize"),
                    timestamp = timestamp,
                    uri = obj.optString("uri", ""),
                    senderIp = obj.optString("senderIp", "")
                )
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "loadRecentReceives failed", e)
            emptyList()
        }
    }

    private fun saveRecentReceives() {
        try {
            val arr = org.json.JSONArray()
            for (info in recentReceives) {
                arr.put(org.json.JSONObject().apply {
                    put("deviceName", info.deviceName)
                    put("fileName", info.fileName)
                    put("fileSize", info.fileSize)
                    put("timestamp", info.timestamp)
                    put("uri", info.uri)
                    put("senderIp", info.senderIp)
                })
            }
            prefs.edit().putString("receives", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("MainViewModel", "saveRecentReceives failed", e)
        }
    }

    private fun startDiscoveryResponder() {
        responderJob = viewModelScope.launch(Dispatchers.IO) {
            DeviceDiscovery.startResponder(
                isActive = { isActive },
                getDeviceName = { getDeviceName() }
            )
        }
    }

    private fun stopReceiveService() {
        ReceiveService.pinCode = ""
        ReceiveService.stop(app)
    }

    private fun getDeviceIp(): String {
        return try {
            val ip = com.example.filetransfer.data.network.DeviceDiscovery.getLocalIpAddress()
            if (ip == "127.0.0.1") "" else ip
        } catch (e: Exception) {
            Log.e("MainViewModel", "getDeviceIp failed", e)
            ""
        }
    }

    private fun getDeviceName(): String {
        val baseName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Build.MODEL.ifBlank { app.getString(R.string.default_device_name) }
            } else {
                try {
                    val btManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = btManager.adapter
                    if (adapter != null && adapter.isEnabled) {
                        adapter.name?.takeIf { it.isNotBlank() } ?: Build.MODEL
                    } else Build.MODEL
                } catch (e: Exception) {
                    Log.e("MainViewModel", "getDeviceName failed", e)
                    Build.MODEL.ifBlank { app.getString(R.string.default_device_name) }
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Build.MODEL.ifBlank { app.getString(R.string.default_device_name) }
            } else {
                try {
                    val btManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = btManager.adapter
                    if (adapter != null && adapter.isEnabled) {
                        adapter.name?.takeIf { it.isNotBlank() } ?: Build.MODEL
                    } else Build.MODEL
                } catch (e: Exception) {
                    Log.e("MainViewModel", "getDeviceName failed", e)
                    Build.MODEL.ifBlank { app.getString(R.string.default_device_name) }
                }
            }
        }
        val pin = _uiState.value.pinCode
        return if (pin.isNotEmpty()) "$baseName|PIN:$pin" else baseName
    }

    override fun onCleared() {
        responderJob?.cancel()
        stopReceiveService()
        refreshJob?.cancel()
        serviceEventJob?.cancel()
        wifiDirectManager.disconnect()
        wifiDirectManager.destroy()
        super.onCleared()
    }
}

private class RateMeter {
    private var lastMs = 0L
    private var lastBytes = 0L
    private var smoothed = 0.0

    fun sample(bytes: Long): Long {
        val now = System.currentTimeMillis()
        if (lastMs == 0L) {
            lastMs = now
            lastBytes = bytes
            return 0L
        }
        val dtSeconds = (now - lastMs) / 1000.0
        if (dtSeconds < 0.25) return smoothed.toLong()
        val instant = (bytes - lastBytes) / dtSeconds
        lastMs = now
        lastBytes = bytes
        smoothed = if (smoothed == 0.0) instant else smoothed * 0.7 + instant * 0.3
        return smoothed.toLong()
    }

    fun reset() {
        lastMs = 0L
        lastBytes = 0L
        smoothed = 0.0
    }
}

private val PRECOMPRESSED_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff",
    "mp4", "mov", "avi", "mkv", "webm", "3gp", "m4v",
    "mp3", "aac", "ogg", "opus", "flac", "wav", "m4a",
    "zip", "7z", "rar", "gz", "bz2", "xz", "zst",
    "apk", "ipa", "jar", "pdf", "docx", "xlsx", "pptx", "epub"
)
