package com.softether.terminal

import android.os.ParcelFileDescriptor
import android.util.Log
import com.softether.model.DeveloperSettings
import com.softether.model.PacketLogLevel
import com.softether.model.PacketBufferStrategy
import com.softether.util.PerformanceMonitor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TunTerminal — forwards L3 IP packets between the Android TUN interface and the
 * SoftEther native data channel (nativeSend / nativeReceive).
 *
 * Supports Developer Mode configuration:
 * - Dynamic TUN Buffer Size
 * - Configurable Output Flush Strategy
 * - Diagnostic Logging & Zero-Allocation Performance Monitoring
 */
class TunTerminal(
    private val vpnInterface: ParcelFileDescriptor,
    private val developerSettings: DeveloperSettings = DeveloperSettings.DEFAULT,
    private val performanceMonitor: PerformanceMonitor? = null
) {
    companion object {
        private const val TAG = "TunTerminal"
        private const val DEFAULT_BUFFER_SIZE = 65535
        private const val IPV4 = 0x04
        private const val IPV6 = 0x06
    }

    var onPacketReceived: ((ByteArray) -> Unit)? = null

    // Dynamic MACs obtained from DHCP/ARP resolution
    @Volatile var gatewayMac: ByteArray? = null
    @Volatile var clientMac: ByteArray? = null

    @Volatile var isRunning = false
        private set

    // RX counters
    private var rxPackets = 0L
    private var rxBytes = 0L

    private var readThread: Thread? = null
    private val inputStream = FileInputStream(vpnInterface.fileDescriptor)
    private val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

    private val bufferSize = if (developerSettings.isDeveloperModeEnabled) {
        developerSettings.bufferSize.coerceIn(1500, 262144)
    } else {
        DEFAULT_BUFFER_SIZE
    }

    private val flushStrategy = if (developerSettings.isDeveloperModeEnabled) {
        developerSettings.flushStrategy
    } else {
        com.softether.model.FlushStrategy.IMMEDIATE
    }

    private val packetLogLevel = if (developerSettings.isDeveloperModeEnabled) {
        developerSettings.packetLogLevel
    } else {
        PacketLogLevel.OFF
    }

    private val isDetailedStats = developerSettings.isDeveloperModeEnabled && developerSettings.isPerformanceStatsEnabled

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "TUN terminal starting (L3 passthrough) GW=${formatMac(gatewayMac)} CLIENT=${formatMac(clientMac)} bufferSize=$bufferSize flushStrategy=$flushStrategy")
        readThread = Thread({ readLoop() }, "TUN-Reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readLoop() {
        val ipBuffer = ByteArray(bufferSize)
        var txPackets = 0L
        var txBytes = 0L
        while (isRunning) {
            try {
                val ipLen = inputStream.read(ipBuffer)
                if (ipLen < 0) break
                if (ipLen <= 0) continue

                // IP version nibble (high 4 bits of byte 0)
                val version = (ipBuffer[0].toInt() ushr 4) and 0x0F

                // Guard: Only process IPv4 or routable global IPv6 packets.
                // In SoftEther/VPNGate VPNs, virtual hubs only assign and route IPv4.
                // Sending Android OS IPv6 link-local (fe80::), all-nodes multicast (ff02::),
                // or router solicitations across the SoftEther L3 channel causes the remote
                // SoftEther NAT/Hub to reject the stream and RST the connection.
                if (version != IPV4) {
                    // Check if IPv6 is link-local (fe80::) or multicast (ff02::)
                    if (version == IPV6 && ipLen >= 40) {
                        val isLinkLocalOrMulticast = (ipBuffer[8] == 0xFE.toByte() && (ipBuffer[9].toInt() and 0xC0) == 0x80) ||
                                (ipBuffer[24] == 0xFF.toByte() && ipBuffer[25] == 0x02.toByte())
                        if (isLinkLocalOrMulticast) {
                            // Silently ignore Android OS IPv6 discovery / multicast traffic
                            continue
                        }
                    } else {
                        performanceMonitor?.tunTxDrops?.incrementAndGet()
                        continue
                    }
                }

                txPackets++
                txBytes += ipLen
                performanceMonitor?.tunTxPackets?.incrementAndGet()
                performanceMonitor?.tunTxBytes?.addAndGet(ipLen.toLong())

                if (isDetailedStats) {
                    performanceMonitor?.inspectIpHeader(ipBuffer, isTx = true)
                }

                val shouldLogVerbose = packetLogLevel == PacketLogLevel.VERBOSE
                val shouldLogBasic = if (developerSettings.isDeveloperModeEnabled) {
                    packetLogLevel == PacketLogLevel.BASIC && (txPackets <= 5 || txPackets % 100L == 0L)
                } else {
                    txPackets <= 10 || txPackets % 50L == 0L
                }

                if (shouldLogVerbose) {
                    val proto = if (ipLen > 9) ipBuffer[9].toInt() and 0xFF else 0
                    val enoughForAddr = if (version == IPV6) ipLen >= 40 else ipLen >= 20
                    val addrStr = if (enoughForAddr) {
                        val (srcIp, dstIp) = ipAddrs(ipBuffer)
                        "$srcIp -> $dstIp"
                    } else "N/A"
                    Log.d(TAG, "[DEV PACKET TX] #$txPackets: IP(v$version) $addrStr proto=$proto len=$ipLen")
                } else if (shouldLogBasic) {
                    val proto = if (ipLen > 9) ipBuffer[9].toInt() and 0xFF else 0
                    val enoughForAddr = if (version == IPV6) ipLen >= 40 else ipLen >= 20
                    if (enoughForAddr) {
                        val (srcIp, dstIp) = ipAddrs(ipBuffer)
                        Log.d(TAG, "TX #$txPackets: IP(v$version) $srcIp -> $dstIp proto=$proto len=$ipLen | " +
                                "TUN->Native L3 (packets=$txPackets bytes=$txBytes)")
                    } else {
                        Log.d(TAG, "TX #$txPackets: short L3 packet version=0x${version.toString(16)} len=$ipLen | " +
                                "TUN->Native L3 (packets=$txPackets bytes=$txBytes)")
                    }
                }

                onPacketReceived?.invoke(ipBuffer.copyOf(ipLen))
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Error in TUN read thread: ${e.message}")
                break
            }
        }
    }

    /**
     * RX direction (SoftEther -> TUN). Writes the L3 IP packet (already decapsulated by
     * nativeReceive) directly into the TUN interface.
     */
    fun write(buffer: ByteArray): Int = write(buffer, 0, buffer.size)

    /** Allocation-free version of [write] writing only the [offset, offset+length) slice. */
    fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0 || offset < 0 || length > buffer.size - offset) return 0
        try {
            outputStream.write(buffer, offset, length)
            when (flushStrategy) {
                com.softether.model.FlushStrategy.IMMEDIATE -> outputStream.flush()
                com.softether.model.FlushStrategy.AUTO -> { /* let OS kernel buffer/flush */ }
                com.softether.model.FlushStrategy.BATCH -> if (rxPackets % 16L == 0L) outputStream.flush()
            }

            rxPackets++
            rxBytes += length
            performanceMonitor?.tunRxPackets?.incrementAndGet()
            performanceMonitor?.tunRxBytes?.addAndGet(length.toLong())

            if (isDetailedStats) {
                performanceMonitor?.inspectIpHeader(buffer, isTx = false)
            }

            val shouldLogVerbose = packetLogLevel == PacketLogLevel.VERBOSE
            val shouldLogBasic = if (developerSettings.isDeveloperModeEnabled) {
                packetLogLevel == PacketLogLevel.BASIC && (rxPackets <= 5 || rxPackets % 100L == 0L)
            } else {
                rxPackets <= 10 || rxPackets % 50L == 0L
            }

            if (shouldLogVerbose) {
                val version = (buffer[offset].toInt() ushr 4) and 0x0F
                val proto = if (length > 9) buffer[offset + 9].toInt() and 0xFF else 0
                val enoughForAddr = if (version == IPV6) length >= 40 else length >= 20
                val addrStr = if (enoughForAddr) {
                    val (srcIp, dstIp) = ipAddrs(buffer, offset)
                    "$srcIp -> $dstIp"
                } else "N/A"
                Log.d(TAG, "[DEV PACKET RX] #$rxPackets: IP(v$version) $addrStr proto=$proto len=$length")
            } else if (shouldLogBasic) {
                val version = (buffer[offset].toInt() ushr 4) and 0x0F
                val proto = if (length > 9) buffer[offset + 9].toInt() and 0xFF else 0
                val enoughForAddr = if (version == IPV6) length >= 40 else length >= 20
                if (enoughForAddr) {
                    val (srcIp, dstIp) = ipAddrs(buffer, offset)
                    Log.d(TAG, "RX #$rxPackets: IP(v$version) $srcIp -> $dstIp proto=$proto len=$length | " +
                            "Native->TUN L3 (packets=$rxPackets bytes=$rxBytes)")
                } else {
                    Log.d(TAG, "RX #$rxPackets: short L3 packet version=0x${version.toString(16)} len=$length | " +
                            "Native->TUN L3 (packets=$rxPackets bytes=$rxBytes)")
                }
            }
            return length
        } catch (e: Exception) {
            performanceMonitor?.tunRxDrops?.incrementAndGet()
            if (isRunning) Log.e(TAG, "Error in TUN write: ${e.message}")
            return -1
        }
    }

    fun stop() {
        isRunning = false
        try {
            readThread?.interrupt()
            inputStream.close()
            outputStream.close()
            vpnInterface.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TUN: ${e.message}")
        }
    }

    /**
     * Read source/destination addresses from an L3 packet at the given base offset.
     * IPv4: src @ +12, dst @ +16. IPv6: src @ +8, dst @ +24.
     */
    private fun ipAddrs(buffer: ByteArray, base: Int = 0): Pair<String, String> {
        val version = (buffer[base].toInt() ushr 4) and 0x0F
        return if (version == IPV6) {
            ipv6ToString(buffer, base + 8) to ipv6ToString(buffer, base + 24)
        } else {
            ipv4ToString(buffer, base + 12) to ipv4ToString(buffer, base + 16)
        }
    }

    private fun ipv4ToString(b: ByteArray, off: Int): String =
        "${b[off].toInt() and 0xFF}.${b[off + 1].toInt() and 0xFF}." +
            "${b[off + 2].toInt() and 0xFF}.${b[off + 3].toInt() and 0xFF}"

    private fun ipv6ToString(b: ByteArray, off: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 8) {
            if (i > 0) sb.append(':')
            val h = ((b[off + i * 2].toInt() and 0xFF) shl 8) or (b[off + i * 2 + 1].toInt() and 0xFF)
            sb.append(h.toString(16))
        }
        return sb.toString()
    }

    private fun formatMac(mac: ByteArray?): String {
        if (mac == null) return "null"
        return mac.joinToString(":") { "%02X".format(it) }
    }
}
