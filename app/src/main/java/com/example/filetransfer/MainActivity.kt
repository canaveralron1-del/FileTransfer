package com.example.filetransfer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import com.example.filetransfer.presentation.MainViewModel
import com.example.filetransfer.ui.AppRoot
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_PALETTE = "palette"
    }

    private val viewModel: MainViewModel by viewModels()
    val pendingTab: MutableIntState = mutableIntStateOf(0)

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
            } ?: data.data?.let { uris.add(it) }
            if (uris.isNotEmpty()) viewModel.selectFiles(uris, false)
        }
    }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            val ip = contents.trim()
            if (ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                viewModel.connectManual(ip)
                pendingTab.intValue = 0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppRoot(
                viewModel = viewModel,
                pendingTab = pendingTab,
                onPickFiles = ::launchFilePicker,
                onScanQr = ::launchQrScanner,
                onRequestReceivePermissions = ::requestReceivePermissions
            )
        }
        handleSendIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleSendIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    viewModel.selectFiles(listOf(it), false)
                    pendingTab.intValue = 0
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    viewModel.selectFiles(uris, false)
                    pendingTab.intValue = 0
                }
            }
        }
    }

    fun launchFilePicker() {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }.let { filePickerLauncher.launch(it) }
    }

    fun launchQrScanner() {
        qrScannerLauncher.launch(ScanOptions().apply {
            setOrientationLocked(false)
            setPrompt("")
        })
    }

    fun requestReceivePermissions(onGranted: () -> Unit) {
        val needsStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        when {
            needsNotification -> { pendingPermissionGrant = onGranted; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            needsStorage -> { pendingPermissionGrant = onGranted; permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            else -> onGranted()
        }
    }

    private var pendingPermissionGrant: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingPermissionGrant?.invoke()
        pendingPermissionGrant = null
    }
}
