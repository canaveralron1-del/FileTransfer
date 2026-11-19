package com.example.filetransfer.data.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

object DeviceDiscovery {

    private const val DISCOVERY_PORT = 5002
    private const val DISCOVERY_MESSAGE = "FILE_TRANSFER_DISCOVERY"
    private const val SCAN_DURATION_MS = 4000L
    private const val SO_TIMEOUT_MS = 1000

    data class DiscoveredDevice(val ip: String, val name: String, val pin: String = "")

    fun getLocalIpAddress(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceDiscovery", "getLocalIpAddress failed", e)
        }
        return "127.0.0.1"
    }

    suspend fun scanForDevices(
        onProgress: suspend (String, String) -> Unit,
        onFound: suspend (List<DiscoveredDevice>) -> Unit,
        onError: suspend (Exception) -> Unit
    ) {
        val ownIp = getLocalIpAddress()
        val devices = mutableMapOf<String, String>()

        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            val msg = DISCOVERY_MESSAGE.toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val multicastAddr = InetAddress.getByName("224.0.0.1")

            repeat(5) {
                socket.send(DatagramPacket(msg, msg.size, broadcastAddr, DISCOVERY_PORT))
                socket.send(DatagramPacket(msg, msg.size, multicastAddr, DISCOVERY_PORT))
                kotlinx.coroutines.delay(100)
            }

            socket.soTimeout = SO_TIMEOUT_MS
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < SCAN_DURATION_MS) {
                try {
                    val buf = ByteArray(1024)
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    val parts = String(p.data, 0, p.length).split("\\|".toRegex())
                    if (parts.size >= 2) {
                        val ip = parts[0]
                        val name = parts[1]
                        val pin = if (parts.size >= 3) parts[2].removePrefix("PIN:") else ""
                        if (ip != ownIp) {
                            devices[ip] = if (pin.isNotEmpty()) "$name [PIN: $pin]" else name
                        }
                    }
                } catch (_: SocketTimeoutException) { }
            }
            socket.close()
        } catch (e: Exception) {
            onError(e)
            return
        }

        onFound(devices.map { DiscoveredDevice(it.key, it.value) })
    }

    suspend fun startResponder(
        isActive: suspend () -> Boolean,
        getDeviceName: suspend () -> String
    ) {
        val udp = try {
            DatagramSocket(DISCOVERY_PORT)
        } catch (e: Exception) {
            Log.e("DeviceDiscovery", "failed to bind responder socket", e)
            return
        }
        udp.broadcast = true
        val buffer = ByteArray(1024)
        while (isActive()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                udp.receive(packet)
                if (String(packet.data, 0, packet.length) == DISCOVERY_MESSAGE) {
                    val localIp = getLocalIpAddress()
                    val deviceName = getDeviceName()
                    val reply = "$localIp|$deviceName"
                    udp.send(DatagramPacket(reply.toByteArray(), reply.length, packet.address, packet.port))
                }
            } catch (e: Exception) {
                Log.e("DeviceDiscovery", "responder error", e)
            }
        }
        udp.close()
    }
}
