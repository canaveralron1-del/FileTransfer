package com.example.filetransfer.data.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

object FileTransfer {

    const val PORT = 5001
    const val MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024
    const val BUFFER_SIZE = 262144

    /** No Nagle for bulk streams; leave TCP window sizing to kernel autotuning. */
    fun tuneSocket(socket: Socket) {
        try { socket.tcpNoDelay = true } catch (_: Exception) {}
    }

    data class FileHeader(val name: String, val size: Long, val displayName: String)
    data class ResumeHeader(val offset: Long) {
        companion object {
            const val NONE = 0L
        }
    }

    fun receiveResumeOffset(input: DataInputStream): Long {
        return try { input.readLong() } catch (_: Exception) { 0L }
    }

    fun sendResumeOffset(output: DataOutputStream, offset: Long) {
        output.writeLong(offset)
    }

    fun receiveHeader(input: DataInputStream): FileHeader {
        val nameLen = input.readInt()
        if (nameLen !in 1..1024) throw IOException("Invalid name length")
        val originalName = String(ByteArray(nameLen).also { input.readFully(it) })
        val size = input.readLong()
        if (size <= 0 || size > MAX_FILE_SIZE) throw IOException("Invalid size")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        val displayName = if (ext.isNotEmpty()) "${baseName}_$timestamp$ext" else "${originalName}_$timestamp"
        return FileHeader(originalName, size, displayName)
    }

    fun receiveFileData(
        input: InputStream,
        output: OutputStream,
        fileSize: Long,
        resumeOffset: Long = 0,
        onProgress: (Int) -> Unit
    ) {
        val totalSize = resumeOffset + fileSize
        var received = resumeOffset
        val buf = ByteArray(BUFFER_SIZE)
        while (received - resumeOffset < fileSize) {
            val remaining = (fileSize - (received - resumeOffset)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val rd = input.read(buf, 0, minOf(buf.size, remaining))
            if (rd == -1) break
            output.write(buf, 0, rd)
            received += rd
            onProgress(((received) * 100 / totalSize).toInt())
        }
    }

    fun sendPin(socket: Socket, pin: String) {
        DataOutputStream(socket.getOutputStream()).writeUTF(pin)
    }

    fun receivePin(input: DataInputStream): String {
        return input.readUTF()
    }

    fun sendPinResult(socket: Socket, success: Boolean) {
        DataOutputStream(socket.getOutputStream()).writeBoolean(success)
    }

    fun receivePinResult(socket: Socket): Boolean {
        return DataInputStream(socket.getInputStream()).readBoolean()
    }

    fun sendChecksum(output: DataOutputStream, checksum: String) {
        output.writeUTF(checksum)
        output.flush()
    }

    /**
     * Close a sender socket after the final flush. On Android's Conscrypt,
     * closing an SSLSocket immediately after a flush can drop the last buffered
     * TLS record over Wi-Fi (receiver gets a clean EOF at an integral 256KB
     * buffer boundary, short of the true file size). Draining the SSL engine's
     * final write before close lets the trailing record leave the device.
     */
    fun closeAfterSend(socket: Socket) {
        try {
            socket.getOutputStream().flush()
            // Let the trailing TLS records reach the kernel buffers before close.
            Thread.sleep(200)
        } catch (_: Exception) {}
        try { socket.close() } catch (_: Exception) {}
    }

    fun receiveChecksum(input: DataInputStream): String {
        return try { input.readUTF() } catch (_: Exception) { "" }
    }

    fun computeSha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val rd = input.read(buf)
            if (rd == -1) break
            digest.update(buf, 0, rd)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun sendFile(
        socket: Socket,
        name: String,
        size: Long,
        resumeOffset: Long = 0,
        dataSource: suspend (OutputStream, Long) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        val out = DataOutputStream(socket.getOutputStream())
        sendResumeOffset(out, resumeOffset)
        out.writeInt(name.length)
        out.write(name.toByteArray())
        out.writeLong(size)
        out.flush()
        val countingStream = object : OutputStream() {
            private var sent = 0L
            override fun write(b: Int) {
                out.write(b)
                sent++
                onProgress(((resumeOffset + sent) * 100 / size).toInt())
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
                sent += len
                onProgress(((resumeOffset + sent) * 100 / size).toInt())
            }
        }
        dataSource(countingStream, resumeOffset)
        out.flush()
    }

    // ── TLS 1.3 support ──────────────────────────────────────────────

    data class TlsSession(val sslContext: SSLContext, val peerFingerprint: String)

    class FingerprintTrustManager : X509TrustManager {

        val capturedPeerCert = AtomicReference<X509Certificate?>(null)
        var expectedFingerprint: String? = null

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isNotEmpty()) capturedPeerCert.set(chain[0])
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isNotEmpty()) capturedPeerCert.set(chain[0])
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun generateSelfSignedCert(): Pair<java.security.KeyPair, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"), SecureRandom())
        }.generateKeyPair()

        val subject = X500Name("CN=FileTransfer, O=Local")
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000)

        val certBuilder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider(BouncyCastleProvider()).build(keyPair.private)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certBuilder.build(signer))

        return Pair(keyPair, cert)
    }

    fun createSslContext(keyPair: java.security.KeyPair, cert: X509Certificate): SSLContext {
        val keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider()).apply {
            load(null, null)
            setKeyEntry("key", keyPair.private, "".toCharArray(), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "".toCharArray())
        }

        val trustManager = FingerprintTrustManager()
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }

        val context = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(trustManager), null)
        }
        return context
    }

    fun extractFingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
    }

    fun wrapSocketAsServer(socket: Socket, sslContext: SSLContext, trustManager: FingerprintTrustManager): SSLSocket {
        val sslSocket = sslContext.socketFactory.createSocket(
            socket, socket.inetAddress.hostName, socket.port, true
        ) as SSLSocket
        sslSocket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
        sslSocket.useClientMode = false
        sslSocket.startHandshake()
        return sslSocket
    }

    fun wrapSocketAsClient(socket: Socket, sslContext: SSLContext, trustManager: FingerprintTrustManager): SSLSocket {
        val sslSocket = sslContext.socketFactory.createSocket(
            socket, socket.inetAddress.hostAddress, socket.port, true
        ) as SSLSocket
        sslSocket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
        sslSocket.startHandshake()
        return sslSocket
    }

    // ── Client-side TLS (sender) ─────────────────────────────────────

    private val clientTrustManager by lazy { FingerprintTrustManager() }

    /** SHA-256 fingerprint of the last server cert seen by the client side. */
    val lastServerFingerprint: String?
        get() = clientTrustManager.capturedPeerCert.get()?.let { extractFingerprint(it) }

    private val clientSslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(clientTrustManager), null)
        }
    }

    fun wrapSocketAsClient(socket: Socket): SSLSocket =
        wrapSocketAsClient(socket, clientSslContext, clientTrustManager)
}
