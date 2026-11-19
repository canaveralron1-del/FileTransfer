package com.example.filetransfer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.filetransfer.MainActivity
import com.example.filetransfer.R
import com.example.filetransfer.data.network.FileTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class ReceiveService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var sslContext: SSLContext? = null
    private var trustManager: FileTransfer.FingerprintTrustManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.receive_mode_waiting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.receive_mode_waiting)))
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                val (keyPair, cert) = FileTransfer.generateSelfSignedCert()
                val fingerprint = FileTransfer.extractFingerprint(cert)
                serverFingerprint = fingerprint
                trustManager = FileTransfer.FingerprintTrustManager()
                sslContext = FileTransfer.createSslContext(keyPair, cert)

                Log.i(TAG, "TLS server fingerprint: $fingerprint")
                serverSocket = ServerSocket(FileTransfer.PORT)
                _events.emit(ServiceEvent.Ready)

                while (isActive && serverSocket?.isClosed == false) {
                    val client = serverSocket?.accept() ?: break
                    FileTransfer.tuneSocket(client)
                    updateNotification(getString(R.string.receiving))
                    launch {
                        var tlsSocket: SSLSocket? = null
                        try {
                            tlsSocket = FileTransfer.wrapSocketAsServer(
                                client,
                                sslContext!!,
                                trustManager!!
                            )
                            receiveOneFile(tlsSocket)
                        } catch (e: Exception) {
                            Log.e(TAG, "receive error", e)
                            _events.emit(ServiceEvent.Error(e.message ?: "Receive failed"))
                        } finally {
                            tlsSocket?.close()
                            client.close()
                        }
                    }
                }
            } catch (e: SocketException) {
                if (e.message != "Socket closed") {
                    _events.emit(ServiceEvent.Error(e.message ?: "Server error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "server error", e)
                _events.emit(ServiceEvent.Error(e.message ?: "Server error"))
            } finally {
                updateNotification(getString(R.string.receive_stopped))
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun progressEvent(header: FileTransfer.FileHeader, resumeOffset: Long, percent: Int): ServiceEvent.Progress {
        val received = resumeOffset + (percent.toLong() * (header.size - resumeOffset)) / 100
        return ServiceEvent.Progress(percent, header.displayName, header.size, received)
    }

    private suspend fun receiveOneFile(client: Socket) {
        val input = DataInputStream(client.getInputStream())
        var pinVerified = false
        // Batch session: loop until the sender closes cleanly (EOF while idle between files).
        while (true) {
            if (pinCode.isNotEmpty() && !pinVerified) {
                val receivedPin = FileTransfer.receivePin(input)
                if (receivedPin != pinCode) {
                    FileTransfer.sendPinResult(client, false)
                    client.close()
                    return
                }
                FileTransfer.sendPinResult(client, true)
                pinVerified = true
            }
            // Leading readLong per file; EOF here = sender finished the batch cleanly.
            val resumeOffset = try {
                input.readLong()
            } catch (_: Exception) {
                return
            }
            val header = FileTransfer.receiveHeader(input)

        val (fileName, fileUri) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val partName = "${header.name}.part"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, if (resumeOffset > 0) partName else header.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FileTransfer")
            }
            val uri = if (resumeOffset > 0) {
                val existing = contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, null,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(partName), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        if (idCol >= 0) Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getString(idCol))
                        else null
                    } else null
                }
                existing ?: contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Cannot create save file")
            } else {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Cannot create save file")
            }
            val mode = if (resumeOffset > 0) "wa" else "wt"
            contentResolver.openOutputStream(uri, mode)?.use { output ->
                BufferedOutputStream(output).use { buf ->
                        FileTransfer.receiveFileData(input, buf, header.size - resumeOffset, resumeOffset) { percent ->
                            updateNotification("${header.displayName} — $percent%")
                            _events.tryEmit(progressEvent(header, resumeOffset, percent))
                        }
                }
            } ?: throw IOException("Cannot open output stream")
            if (resumeOffset > 0) {
                val finalValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, header.displayName)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(uri, finalValues, null, null)
            }
            Pair(header.displayName, uri.toString())
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileTransfer")
            if (!dir.exists()) dir.mkdirs()
            val partFile = File(dir, "${header.name}.part")
            val finalFile = File(dir, header.displayName)
            val outputFile = if (resumeOffset > 0) partFile else finalFile
            outputFile.outputStream().let { raw ->
                if (resumeOffset > 0) {
                    java.io.FileOutputStream(outputFile, true).use { appendStream ->
                        BufferedOutputStream(appendStream).use { buf ->
                            FileTransfer.receiveFileData(input, buf, header.size - resumeOffset, resumeOffset) { percent ->
                                updateNotification("${header.displayName} — $percent%")
                                _events.tryEmit(progressEvent(header, resumeOffset, percent))
                            }
                        }
                    }
                } else {
                    raw.use { output ->
                        BufferedOutputStream(output).use { buf ->
                            FileTransfer.receiveFileData(input, buf, header.size - resumeOffset, resumeOffset) { percent ->
                                updateNotification("${header.displayName} — $percent%")
                                _events.tryEmit(progressEvent(header, resumeOffset, percent))
                            }
                        }
                    }
                }
            }
            if (resumeOffset > 0 && partFile.exists()) {
                partFile.renameTo(finalFile)
            }
            Pair(header.displayName, Uri.fromFile(finalFile).toString())
        }

        val expectedChecksum = FileTransfer.receiveChecksum(input)
        if (expectedChecksum.isNotEmpty()) {
            val actualChecksum = try {
                contentResolver.openInputStream(Uri.parse(fileUri))?.use { FileTransfer.computeSha256(it) } ?: ""
            } catch (_: Exception) { "" }
            if (actualChecksum.isNotEmpty() && actualChecksum != expectedChecksum) {
                Log.e("ReceiveService", "Checksum mismatch: expected $expectedChecksum, got $actualChecksum")
                _events.emit(ServiceEvent.Error("Checksum mismatch for $fileName"))
            }
        }

        val senderIp = client.inetAddress.hostAddress ?: "unknown"
        val timestamp = System.currentTimeMillis()
        _events.emit(ServiceEvent.FileReceived(senderIp, fileName, header.size, timestamp, fileUri))
        updateReceivedNotification(fileName, fileUri, senderIp)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateReceivedNotification(fileName: String, fileUri: String, senderIp: String) {
        val openIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(fileUri), "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val file = File(Uri.parse(fileUri).path!!)
            val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val pendingIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.received_file, fileName, senderIp))
            .setContentText(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.open), openPendingIntent)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serverJob?.cancel()
        serverSocket?.close()
        scope.cancel()
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "ReceiveService"
        private const val CHANNEL_ID = "receive_service"
        private const val NOTIFICATION_ID = 1001
        var pinCode: String = ""
        var serverFingerprint: String = ""

        private val _events = MutableSharedFlow<ServiceEvent>(extraBufferCapacity = 16)
        val events: SharedFlow<ServiceEvent> = _events.asSharedFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ReceiveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReceiveService::class.java))
        }
    }
}

sealed class ServiceEvent {
    data object Ready : ServiceEvent()
    data class Progress(
        val percent: Int,
        val fileName: String = "",
        val totalBytes: Long = 0,
        val receivedBytes: Long = 0
    ) : ServiceEvent()
    data class FileReceived(val senderIp: String, val fileName: String, val fileSize: Long, val timestamp: Long, val uri: String = "") : ServiceEvent()
    data class Error(val message: String) : ServiceEvent()
}
