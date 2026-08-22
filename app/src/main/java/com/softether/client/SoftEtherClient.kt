package com.softether.client

import android.util.Log
import com.softether.model.ConnectionException
import com.softether.model.SoftEtherError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SoftEtherClient - JNI bridge wrapper to native SoftEther implementation
 * Provides high-level API for connection management with safe fallback protection.
 */
class SoftEtherClient {

    private val tag = "SoftEtherClient"
    private var nativeHandle: Long = 0
    private val isConnected = AtomicBoolean(false)
    
    // External handle set by ConnectionController when it manages the native connection directly
    @Volatile
    var externalHandle: Long = 0

    // TLS Socket management for SoftEther HTTPS/SSL tunnels
    @Volatile
    private var sslSocket: java.net.Socket? = null
    @Volatile
    private var sslInputStream: java.io.InputStream? = null
    @Volatile
    private var sslOutputStream: java.io.OutputStream? = null

    fun isAvailable(): Boolean = isLibraryLoaded

    fun isTlsActive(): Boolean = sslSocket != null && sslSocket?.isClosed == false

    @Volatile
    private var clientMacBytes: ByteArray = byteArrayOf(
        0x5E.toByte(), 0x5C.toByte(), 0x9B.toByte(), 0x33.toByte(), 0x1A.toByte(), 0x17.toByte()
    )
    @Volatile
    private var gatewayMacBytes: ByteArray = byteArrayOf(
        0x5E.toByte(), 0x2C.toByte(), 0x9A.toByte(), 0xFF.toByte(), 0x62.toByte(), 0x09.toByte()
    )

    init {
        // Generate pseudo-random client MAC with 5E:5C prefix (SoftEther standard virtual NIC prefix)
        val rand = java.util.Random()
        val randomBytes = ByteArray(4)
        rand.nextBytes(randomBytes)
        clientMacBytes = byteArrayOf(
            0x5E.toByte(), 0x5C.toByte(),
            randomBytes[0], randomBytes[1], randomBytes[2], randomBytes[3]
        )
    }

    /**
     * Establishes a direct TLS / SSL tunnel with the SoftEther server.
     * Performs standard SSL/TLS handshake (accepting self-signed VPNGate certificates),
     * protects the socket from VPN routing loops, and sends the SoftEther HTTP POST handshake.
     */
    fun establishTlsConnection(
        host: String,
        port: Int,
        hubName: String,
        username: String,
        password: String,
        timeoutMs: Int = 15000,
        protectCallback: ((java.net.Socket) -> Boolean)? = null
    ): Boolean {
        return try {
            com.softether.SoftEtherVpnService.log("D", tag, "Establishing protected TLS tunnel to $host:$port (Hub: $hubName)")
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            })

            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }

            // 1. Create underlying plain TCP socket
            val plainSocket = java.net.Socket()
            plainSocket.tcpNoDelay = true
            plainSocket.keepAlive = true
            plainSocket.sendBufferSize = 1048576
            plainSocket.receiveBufferSize = 1048576

            // 2. Protect plain TCP socket from TUN routing loops before connecting
            val protected = protectCallback?.invoke(plainSocket) ?: false
            com.softether.SoftEtherVpnService.log("D", tag, "Plain TCP socket protection result: $protected")

            // 3. Connect TCP socket
            plainSocket.connect(java.net.InetSocketAddress(host, port), timeoutMs.coerceAtLeast(10000))

            // 4. Wrap with TLS SSLSocket
            val socket = sslContext.socketFactory.createSocket(
                plainSocket,
                host,
                port,
                true
            ) as javax.net.ssl.SSLSocket
            socket.soTimeout = 15000 // 15 seconds for handshake
            socket.startHandshake()

            protectCallback?.invoke(socket)

            val out = socket.outputStream
            val inStream = socket.inputStream

            // 5. Send SoftEther HTTP POST handshake header (standard SoftEther Cedar protocol endpoint)
            val requestHeader = "POST /vpnsvc/connect.cgi HTTP/1.1\r\n" +
                "Host: $host:$port\r\n" +
                "Keep-Alive: timeout=15; max=19\r\n" +
                "Connection: Keep-Alive\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "\r\n"
            out.write(requestHeader.toByteArray(Charsets.US_ASCII))
            out.flush()

            // 6. Read HTTP response status & headers up to \r\n\r\n
            val headerBuffer = ByteArray(4096)
            var headerBytesRead = 0
            var matchedEnd = false
            while (headerBytesRead < headerBuffer.size && !matchedEnd) {
                val b = inStream.read()
                if (b == -1) break
                headerBuffer[headerBytesRead++] = b.toByte()
                if (headerBytesRead >= 4 &&
                    headerBuffer[headerBytesRead - 4] == '\r'.code.toByte() &&
                    headerBuffer[headerBytesRead - 3] == '\n'.code.toByte() &&
                    headerBuffer[headerBytesRead - 2] == '\r'.code.toByte() &&
                    headerBuffer[headerBytesRead - 1] == '\n'.code.toByte()
                ) {
                    matchedEnd = true
                }
            }

            val responseStr = String(headerBuffer, 0, headerBytesRead, Charsets.US_ASCII)
            val firstLine = responseStr.lines().firstOrNull() ?: ""
            com.softether.SoftEtherVpnService.log("D", tag, "SoftEther HTTP handshake response: $firstLine")

            if (!firstLine.contains("200")) {
                // Try fallback endpoint /vpnsvc/vpn.cgi if connect.cgi returned non-200
                com.softether.SoftEtherVpnService.log("D", tag, "Trying fallback endpoint /vpnsvc/vpn.cgi...")
                val fallbackHeader = "POST /vpnsvc/vpn.cgi HTTP/1.1\r\n" +
                    "Host: $host:$port\r\n" +
                    "Connection: Keep-Alive\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "\r\n"
                out.write(fallbackHeader.toByteArray(Charsets.US_ASCII))
                out.flush()

                headerBytesRead = 0
                matchedEnd = false
                while (headerBytesRead < headerBuffer.size && !matchedEnd) {
                    val b = inStream.read()
                    if (b == -1) break
                    headerBuffer[headerBytesRead++] = b.toByte()
                    if (headerBytesRead >= 4 &&
                        headerBuffer[headerBytesRead - 4] == '\r'.code.toByte() &&
                        headerBuffer[headerBytesRead - 3] == '\n'.code.toByte() &&
                        headerBuffer[headerBytesRead - 2] == '\r'.code.toByte() &&
                        headerBuffer[headerBytesRead - 1] == '\n'.code.toByte()
                    ) {
                        matchedEnd = true
                    }
                }
                val fbLine = String(headerBuffer, 0, headerBytesRead, Charsets.US_ASCII).lines().firstOrNull() ?: ""
                com.softether.SoftEtherVpnService.log("D", tag, "SoftEther fallback handshake response: $fbLine")
                if (!fbLine.contains("200")) {
                    com.softether.SoftEtherVpnService.log("W", tag, "SoftEther server rejected HTTP tunnel handshake: $fbLine")
                    closeTls()
                    return false
                }
            }

            // 7. Switch socket to 2000ms polling timeout for non-blocking packet polling
            socket.soTimeout = 2000

            sslSocket = socket
            sslInputStream = inStream
            sslOutputStream = out
            isConnected.set(true)
            com.softether.SoftEtherVpnService.log("D", tag, "Protected SoftEther TLS session active to $host:$port")
            true
        } catch (e: Exception) {
            com.softether.SoftEtherVpnService.log("W", tag, "TLS tunnel attempt failed: ${e.message}")
            closeTls()
            false
        }
    }

    fun closeTls() {
        try {
            sslInputStream?.close()
            sslOutputStream?.close()
            sslSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        sslInputStream = null
        sslOutputStream = null
        sslSocket = null
    }

    /**
     * Connect to SoftEther VPN server
     */
    @Throws(ConnectionException::class)
    fun connect(host: String, port: Int, username: String, password: String) {
        connect(host, port, username, password, DEFAULT_HUB_NAME)
    }

    /**
     * Connect to SoftEther VPN server with hub name
     */
    @Throws(ConnectionException::class)
    fun connect(host: String, port: Int, username: String, password: String, hubName: String) {
        connect(host, port, username, password, hubName, com.softether.model.AuthMethod.AUTO)
    }

    /**
     * Connect to SoftEther VPN server with hub name and explicit auth method
     */
    @Throws(ConnectionException::class)
    fun connect(host: String, port: Int, username: String, password: String, hubName: String, authMethod: com.softether.model.AuthMethod = com.softether.model.AuthMethod.AUTO) {
        if (!isLibraryLoaded) {
            throw ConnectionException("Native SoftEther engine (libsoftether.so) is not loaded.")
        }
        com.softether.SoftEtherVpnService.log("D", tag, "Connecting to $host:$port as $username (hub: $hubName, auth: $authMethod)")

        // Create native connection
        nativeHandle = nativeCreate()
        if (nativeHandle == 0L) {
            throw ConnectionException("Failed to create native connection")
        }

        // Set default timeout
        nativeSetOption(nativeHandle, OPTION_TIMEOUT, 30000L)

        // Set auth type
        val authTypeInt = when (authMethod) {
            com.softether.model.AuthMethod.ANONYMOUS -> 0
            com.softether.model.AuthMethod.PASSWORD -> 1
            com.softether.model.AuthMethod.PLAIN_PASSWORD -> 2
            com.softether.model.AuthMethod.AUTO -> -1
        }
        nativeSetAuthType(nativeHandle, authTypeInt)

        // Build client info for server session list
        val clientInfo = com.softether.model.ClientInfoFactory.build(
            productName = "SoftEther VPN Client for Android",
            productVersion = "2.3.2",
            productBuild = 132,
            config = com.softether.model.ConnectionConfig(
                serverHost = host,
                serverPort = port,
                username = username,
                password = password,
                virtualHub = hubName
            )
        )

        // Connect to server with hub name
        val result = nativeConnectWithHub(nativeHandle, host, port, username, password, hubName,
            false,
            clientInfo.productName, clientInfo.productVersion, clientInfo.productBuild,
            clientInfo.osName, clientInfo.osVersion, clientInfo.osProductId,
            clientInfo.hostName, clientInfo.clientIpAddress, clientInfo.clientPort,
            clientInfo.serverHostName, clientInfo.serverIpAddress, clientInfo.serverPort)

        if (result != SoftEtherError.ERR_NONE) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
            throw ConnectionException("Connection failed: ${SoftEtherError.getErrorString(result)}")
        }

        isConnected.set(true)
        com.softether.SoftEtherVpnService.log("D", tag, "Connected successfully")
    }

    fun setAuthType(authMethod: com.softether.model.AuthMethod) {
        if (nativeHandle == 0L) return
        val authTypeInt = when (authMethod) {
            com.softether.model.AuthMethod.ANONYMOUS -> 0
            com.softether.model.AuthMethod.PASSWORD -> 1
            com.softether.model.AuthMethod.PLAIN_PASSWORD -> 2
            com.softether.model.AuthMethod.AUTO -> -1
        }
        nativeSetAuthType(nativeHandle, authTypeInt)
    }

    fun setMaxConnection(maxConnections: Int) {
        if (nativeHandle == 0L) return
        nativeSetMaxConnection(nativeHandle, maxConnections.coerceIn(1, 8))
    }

    fun getNumConnections(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetNumConnections(nativeHandle)
    }

    fun getAllSocketFds(): IntArray? {
        if (nativeHandle == 0L) return null
        return nativeGetAllSocketFds(nativeHandle)
    }

    fun disconnect() {
        closeTls()
        if (!isConnected.getAndSet(false) || nativeHandle == 0L) {
            return
        }

        com.softether.SoftEtherVpnService.log("D", tag, "Disconnecting...")
        nativeDisconnect(nativeHandle)
        nativeDestroy(nativeHandle)
        nativeHandle = 0
        com.softether.SoftEtherVpnService.log("D", tag, "Disconnected")
    }

    fun send(data: ByteArray): Int {
        val out = sslOutputStream
        if (out != null) {
            return try {
                val len = data.size
                val frame = ByteArray(len + 2)
                frame[0] = ((len ushr 8) and 0xFF).toByte()
                frame[1] = (len and 0xFF).toByte()
                System.arraycopy(data, 0, frame, 2, len)
                synchronized(out) {
                    out.write(frame)
                    out.flush()
                }
                len
            } catch (e: Exception) {
                -1
            }
        }
        val handle = externalHandle.takeIf { it != 0L } ?: nativeHandle
        if (handle == 0L) return -1
        return nativeSend(handle, data, data.size)
    }

    fun receive(buffer: ByteArray): Int {
        val inStream = sslInputStream
        if (inStream != null) {
            return try {
                val b1 = inStream.read()
                if (b1 == -1) return -1
                val b2 = inStream.read()
                if (b2 == -1) return -1
                val frameLen = ((b1 and 0xFF) shl 8) or (b2 and 0xFF)
                if (frameLen == 0) return 0 // Keepalive frame
                if (frameLen > buffer.size) {
                    var skipped = 0
                    while (skipped < frameLen) {
                        val s = inStream.skip((frameLen - skipped).toLong())
                        if (s <= 0) break
                        skipped += s.toInt()
                    }
                    return 0
                }
                var totalRead = 0
                while (totalRead < frameLen) {
                    val r = inStream.read(buffer, totalRead, frameLen - totalRead)
                    if (r <= 0) return -1
                    totalRead += r
                }
                totalRead
            } catch (e: java.net.SocketTimeoutException) {
                0 // Polling timeout is normal
            } catch (e: Exception) {
                -1
            }
        }
        val handle = externalHandle.takeIf { it != 0L } ?: nativeHandle
        if (handle == 0L) return -1
        return nativeReceive(handle, buffer, buffer.size)
    }

    fun isConnected(): Boolean = isConnected.get()

    fun setTimeout(timeoutMs: Int) {
        if (nativeHandle != 0L) {
            nativeSetOption(nativeHandle, OPTION_TIMEOUT, timeoutMs.toLong())
        }
    }

    fun setKeepAliveInterval(intervalMs: Int) {
        if (nativeHandle != 0L) {
            nativeSetOption(nativeHandle, OPTION_KEEPALIVE_INTERVAL, intervalMs.toLong())
        }
    }

    fun setMtu(mtu: Int) {
        if (nativeHandle != 0L) {
            nativeSetOption(nativeHandle, OPTION_MTU, mtu.toLong())
        }
    }

    fun cleanup() {
        disconnect()
    }

    // Safe wrappers around native methods to prevent UnsatisfiedLinkError crashes
    fun nativeCreate(): Long {
        if (!isLibraryLoaded) return 0L
        return try { _nativeCreate() } catch (t: Throwable) { Log.e(tag, "nativeCreate error", t); 0L }
    }

    fun nativeDestroy(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try { _nativeDestroy(handle) } catch (t: Throwable) { Log.e(tag, "nativeDestroy error", t) }
    }

    fun nativeConnect(handle: Long, host: String, port: Int, username: String, password: String): Int {
        if (!isLibraryLoaded || handle == 0L) return SoftEtherError.ERR_UNKNOWN
        return try { _nativeConnect(handle, host, port, username, password) } catch (t: Throwable) { Log.e(tag, "nativeConnect error", t); SoftEtherError.ERR_UNKNOWN }
    }

    fun nativeConnectWithHub(
        handle: Long,
        host: String,
        port: Int,
        username: String,
        password: String,
        hubName: String,
        useTcp: Boolean,
        clientProductName: String,
        clientVersion: String,
        clientBuild: Int,
        clientOsName: String,
        clientOsVersion: String,
        clientOsProductId: String,
        clientHostName: String,
        clientIpAddress: String,
        clientPort: Int,
        serverHostName: String,
        serverIpAddress: String,
        serverPort: Int
    ): Int {
        if (!isLibraryLoaded || handle == 0L) return SoftEtherError.ERR_UNKNOWN
        return try {
            _nativeConnectWithHub(
                handle, host, port, username, password, hubName, useTcp,
                clientProductName, clientVersion, clientBuild,
                clientOsName, clientOsVersion, clientOsProductId,
                clientHostName, clientIpAddress, clientPort,
                serverHostName, serverIpAddress, serverPort
            )
        } catch (t: Throwable) {
            Log.e(tag, "nativeConnectWithHub error", t)
            SoftEtherError.ERR_UNKNOWN
        }
    }

    fun nativeDisconnect(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try { _nativeDisconnect(handle) } catch (t: Throwable) { Log.e(tag, "nativeDisconnect error", t) }
    }

    fun nativeGetState(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try { _nativeGetState(handle) } catch (t: Throwable) { 0 }
    }

    fun nativeSend(handle: Long, data: ByteArray, length: Int): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try { _nativeSend(handle, data, length) } catch (t: Throwable) { -1 }
    }

    fun nativeReceive(handle: Long, buffer: ByteArray, maxLength: Int): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try { _nativeReceive(handle, buffer, maxLength) } catch (t: Throwable) { -1 }
    }

    fun nativeSetOption(handle: Long, option: Int, value: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try { _nativeSetOption(handle, option, value) } catch (t: Throwable) { Log.e(tag, "nativeSetOption error", t) }
    }

    fun nativeGetSocketFd(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try { _nativeGetSocketFd(handle) } catch (t: Throwable) { -1 }
    }

    fun nativeGetRudpSocketFd(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return -1
        return try { _nativeGetRudpSocketFd(handle) } catch (t: Throwable) { -1 }
    }

    fun nativeDoDhcp(handle: Long): IntArray? {
        if (!isLibraryLoaded || handle == 0L) return null
        return try { _nativeDoDhcp(handle) } catch (t: Throwable) { null }
    }

    fun nativeSetAuthType(handle: Long, authType: Int) {
        if (!isLibraryLoaded || handle == 0L) return
        try { _nativeSetAuthType(handle, authType) } catch (t: Throwable) { Log.e(tag, "nativeSetAuthType error", t) }
    }

    fun nativeSetMaxConnection(handle: Long, maxConnections: Int) {
        if (!isLibraryLoaded || handle == 0L) return
        try { _nativeSetMaxConnection(handle, maxConnections) } catch (t: Throwable) { Log.e(tag, "nativeSetMaxConnection error", t) }
    }

    fun nativeGetNumConnections(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try { _nativeGetNumConnections(handle) } catch (t: Throwable) { 0 }
    }

    fun nativeGetAllSocketFds(handle: Long): IntArray? {
        if (!isLibraryLoaded || handle == 0L) return null
        return try { _nativeGetAllSocketFds(handle) } catch (t: Throwable) { null }
    }

    fun nativeGetClientMac(handle: Long): ByteArray? {
        if (!isLibraryLoaded || handle == 0L) return null
        return try { _nativeGetClientMac(handle) } catch (t: Throwable) { null }
    }

    fun nativeGetGatewayMac(handle: Long): ByteArray? {
        if (!isLibraryLoaded || handle == 0L) return null
        return try { _nativeGetGatewayMac(handle) } catch (t: Throwable) { null }
    }

    fun nativeGetRudpVersion(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try { _nativeGetRudpVersion(handle) } catch (t: Throwable) { 0 }
    }

    fun nativeIsRudpEnabled(handle: Long): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try { _nativeIsRudpEnabled(handle) } catch (t: Throwable) { false }
    }

    fun nativeGetServerMaxConnection(handle: Long): Int {
        if (!isLibraryLoaded || handle == 0L) return 0
        return try { _nativeGetServerMaxConnection(handle) } catch (t: Throwable) { 0 }
    }

    fun nativeIsIpv6(handle: Long): Boolean {
        if (!isLibraryLoaded || handle == 0L) return false
        return try { _nativeIsIpv6(handle) } catch (t: Throwable) { false }
    }

    fun getRudpVersion(handle: Long = externalHandle.takeIf { it != 0L } ?: nativeHandle): Int {
        if (handle == 0L) return 0
        return nativeGetRudpVersion(handle)
    }

    fun isRudpEnabled(handle: Long = externalHandle.takeIf { it != 0L } ?: nativeHandle): Boolean {
        if (handle == 0L) return false
        return nativeIsRudpEnabled(handle)
    }

    fun getServerMaxConnection(handle: Long = externalHandle.takeIf { it != 0L } ?: nativeHandle): Int {
        if (handle == 0L) return 0
        return nativeGetServerMaxConnection(handle)
    }

    fun isIpv6(handle: Long = externalHandle.takeIf { it != 0L } ?: nativeHandle): Boolean {
        if (handle == 0L) return false
        return nativeIsIpv6(handle)
    }

    fun doDhcp(handle: Long = nativeHandle): DhcpResult? {
        if (handle == 0L) return null
        val arr = nativeDoDhcp(handle) ?: return null
        if (arr.size < 7 || arr[0] == 0) return null
        return DhcpResult(
            assignedIp = intToIpString(arr[1]),
            subnetMask = intToIpString(arr[2]),
            gateway = intToIpString(arr[3]),
            dnsServer = intToIpString(arr[4]),
            dnsServer2 = intToIpString(arr[5]),
            leaseTime = arr[6],
            prefixLength = subnetMaskToPrefix(arr[2])
        )
    }

    fun getClientMac(): ByteArray? {
        val handle = externalHandle.takeIf { it != 0L } ?: nativeHandle
        if (handle != 0L) {
            val mac = nativeGetClientMac(handle)
            if (mac != null && mac.size >= 6) return mac
        }
        return clientMacBytes
    }

    fun getGatewayMac(): ByteArray? {
        val handle = externalHandle.takeIf { it != 0L } ?: nativeHandle
        if (handle != 0L) {
            val mac = nativeGetGatewayMac(handle)
            if (mac != null && mac.size >= 6) return mac
        }
        return gatewayMacBytes
    }

    // Underlying JNI method declarations
    private external fun _nativeCreate(): Long
    private external fun _nativeDestroy(handle: Long)
    private external fun _nativeConnect(
        handle: Long,
        host: String,
        port: Int,
        username: String,
        password: String
    ): Int
    private external fun _nativeConnectWithHub(
        handle: Long,
        host: String,
        port: Int,
        username: String,
        password: String,
        hubName: String,
        useTcp: Boolean,
        clientProductName: String,
        clientVersion: String,
        clientBuild: Int,
        clientOsName: String,
        clientOsVersion: String,
        clientOsProductId: String,
        clientHostName: String,
        clientIpAddress: String,
        clientPort: Int,
        serverHostName: String,
        serverIpAddress: String,
        serverPort: Int
    ): Int
    private external fun _nativeDisconnect(handle: Long)
    private external fun _nativeGetState(handle: Long): Int
    private external fun _nativeSend(handle: Long, data: ByteArray, length: Int): Int
    private external fun _nativeReceive(handle: Long, buffer: ByteArray, maxLength: Int): Int
    private external fun _nativeSetOption(handle: Long, option: Int, value: Long)
    private external fun _nativeGetSocketFd(handle: Long): Int
    private external fun _nativeGetRudpSocketFd(handle: Long): Int
    private external fun _nativeDoDhcp(handle: Long): IntArray?
    private external fun _nativeSetAuthType(handle: Long, authType: Int)
    private external fun _nativeSetMaxConnection(handle: Long, maxConnections: Int)
    private external fun _nativeGetNumConnections(handle: Long): Int
    private external fun _nativeGetAllSocketFds(handle: Long): IntArray?
    private external fun _nativeGetClientMac(handle: Long): ByteArray?
    private external fun _nativeGetGatewayMac(handle: Long): ByteArray?
    private external fun _nativeGetRudpVersion(handle: Long): Int
    private external fun _nativeIsRudpEnabled(handle: Long): Boolean
    private external fun _nativeGetServerMaxConnection(handle: Long): Int
    private external fun _nativeIsIpv6(handle: Long): Boolean

    companion object {
        private const val TAG = "SoftEtherClient"
        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("softether")
                isLibraryLoaded = true
                Log.i(TAG, "Native library 'softether' loaded successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "Native library 'softether' not available: ${t.message}")
                isLibraryLoaded = false
            }
        }

        fun isNativeAvailable(): Boolean = isLibraryLoaded

        const val OPTION_TIMEOUT = 1
        const val OPTION_KEEPALIVE_INTERVAL = 2
        const val OPTION_MTU = 3
        const val DEFAULT_HUB_NAME = "VPN"

        private fun intToIpString(ip: Int): String {
            return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
        }

        private fun subnetMaskToPrefix(mask: Int): Int {
            var m = mask
            var prefix = 0
            for (i in 31 downTo 0) {
                if ((m and (1 shl i)) != 0) prefix++ else break
            }
            return if (prefix in 1..32) prefix else 24
        }
    }
}

data class DhcpResult(
    val assignedIp: String,
    val subnetMask: String,
    val gateway: String,
    val dnsServer: String,
    val dnsServer2: String,
    val leaseTime: Int,
    val prefixLength: Int
)

class ConnectionException(message: String) : Exception(message)

object SoftEtherError {
    const val ERR_NONE = 0
    const val ERR_TCP_CONNECT = 1
    const val ERR_TLS_HANDSHAKE = 2
    const val ERR_PROTOCOL_VERSION = 3
    const val ERR_AUTHENTICATION = 4
    const val ERR_SESSION = 5
    const val ERR_DATA_TRANSMISSION = 6
    const val ERR_TIMEOUT = 7
    const val ERR_UNKNOWN = 99

    fun getErrorString(code: Int): String {
        return when (code) {
            ERR_NONE -> "No error"
            ERR_TCP_CONNECT -> "TCP connection failed"
            ERR_TLS_HANDSHAKE -> "TLS handshake failed"
            ERR_PROTOCOL_VERSION -> "Protocol version mismatch"
            ERR_AUTHENTICATION -> "Authentication failed"
            ERR_SESSION -> "Session setup failed"
            ERR_DATA_TRANSMISSION -> "Data transmission failed"
            ERR_TIMEOUT -> "Operation timed out"
            ERR_UNKNOWN -> "Unknown error"
            else -> "Undefined error ($code)"
        }
    }
}
