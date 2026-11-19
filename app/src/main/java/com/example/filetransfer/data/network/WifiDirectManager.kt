package com.example.filetransfer.data.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private val channel: WifiP2pManager.Channel by lazy {
        manager.initialize(context, context.mainLooper, null)
    }
    private var receiver: WifiDirectReceiver? = null
    private var registered = false

    var onPeersUpdated: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnected: ((goIp: String, deviceName: String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var currentDeviceName: String = ""

    fun initialize() {
        if (registered) return
        val r = WifiDirectReceiver()
        receiver = r
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(r, filter)
        registered = true
    }

    fun discoverPeers() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WiFi Direct peer discovery started")
            }
            override fun onFailure(reason: Int) {
                val msg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported"
                    WifiP2pManager.BUSY -> "WiFi Direct busy"
                    WifiP2pManager.ERROR -> "WiFi Direct error"
                    else -> "WiFi Direct discovery failed (reason=$reason)"
                }
                Log.e(TAG, msg)
                onError?.invoke(msg)
            }
        })
    }

    fun connect(addr: String, deviceName: String) {
        currentDeviceName = deviceName
        val config = WifiP2pConfig().apply {
            deviceAddress = addr
            groupOwnerIntent = 15
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WiFi Direct connect initiated to $deviceName")
            }
            override fun onFailure(reason: Int) {
                val msg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported"
                    WifiP2pManager.BUSY -> "WiFi Direct busy"
                    WifiP2pManager.ERROR -> "WiFi Direct connection failed"
                    else -> "WiFi Direct connect failed (reason=$reason)"
                }
                Log.e(TAG, msg)
                onError?.invoke(msg)
            }
        })
    }

    fun disconnect() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WiFi Direct group removed")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "removeGroup failed: $reason")
            }
        })
    }

    fun destroy() {
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
            registered = false
        }
        receiver = null
    }

    private fun getGroupOwnerIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (!iface.name.startsWith("p2p") && !iface.name.startsWith("wl")) continue
                val addresses = iface.inetAddresses ?: continue
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "getGroupOwnerIp failed", e)
            null
        }
    }

    private inner class WifiDirectReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!enabled) {
                        onDisconnected?.invoke()
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel) { peers: WifiP2pDeviceList ->
                        val deviceList = peers.deviceList.toList()
                        onPeersUpdated?.invoke(deviceList)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager.requestConnectionInfo(channel) { info ->
                            if (info != null) {
                                val goIp = if (info.isGroupOwner) {
                                    "192.168.49.1"
                                } else {
                                    getGroupOwnerIp() ?: "192.168.49.1"
                                }
                                Log.d(TAG, "Connected via WiFi Direct, GO IP: $goIp")
                                onConnected?.invoke(goIp, currentDeviceName.ifEmpty { info.groupOwnerAddress?.hostAddress ?: goIp })
                            }
                        }
                    } else {
                        onDisconnected?.invoke()
                    }
                }
            }
        }
    }

    private companion object {
        private const val TAG = "WifiDirectManager"
    }
}
