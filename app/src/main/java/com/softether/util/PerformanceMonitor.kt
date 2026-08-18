package com.softether.util

import com.softether.model.PacketSizeStats
import com.softether.model.PipelineStageStats
import com.softether.model.SocketProtectInfo
import com.softether.model.TunnelDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High performance, zero-allocation metrics tracker for TUN/JNI/SoftEther tunnel.
 */
class PerformanceMonitor {

    private val _diagnosticsFlow = MutableStateFlow(TunnelDiagnostics.EMPTY)
    val diagnosticsFlow: StateFlow<TunnelDiagnostics> = _diagnosticsFlow.asStateFlow()

    // Totals
    val totalTxBytes = AtomicLong(0L)
    val totalRxBytes = AtomicLong(0L)
    val totalTxPackets = AtomicLong(0L)
    val totalRxPackets = AtomicLong(0L)
    val totalTxDrops = AtomicLong(0L)
    val totalRxDrops = AtomicLong(0L)
    val nativeSendFailures = AtomicLong(0L)
    val nativeReceiveErrors = AtomicLong(0L)

    // Pipeline Stages
    val tunTxPackets = AtomicLong(0L)
    val tunTxBytes = AtomicLong(0L)
    val tunTxDrops = AtomicLong(0L)

    val encapPackets = AtomicLong(0L)
    val encapBytes = AtomicLong(0L)

    val jniSendPackets = AtomicLong(0L)
    val jniSendBytes = AtomicLong(0L)
    val jniSendFailures = AtomicLong(0L)

    val seRxPackets = AtomicLong(0L)
    val seRxBytes = AtomicLong(0L)

    val jniRecvPackets = AtomicLong(0L)
    val jniRecvBytes = AtomicLong(0L)
    val jniRecvErrors = AtomicLong(0L)

    val decapPackets = AtomicLong(0L)
    val decapBytes = AtomicLong(0L)

    val tunRxPackets = AtomicLong(0L)
    val tunRxBytes = AtomicLong(0L)
    val tunRxDrops = AtomicLong(0L)

    // Packet Sizes TX
    val txMinSize = AtomicInteger(Int.MAX_VALUE)
    val txMaxSize = AtomicInteger(0)
    val txBucketUnder256 = AtomicLong(0L)
    val txBucket256To512 = AtomicLong(0L)
    val txBucket512To1024 = AtomicLong(0L)
    val txBucket1024To1280 = AtomicLong(0L)
    val txBucket1280To1400 = AtomicLong(0L)
    val txBucket1400To1500 = AtomicLong(0L)
    val txBucketOver1500 = AtomicLong(0L)

    // Packet Sizes RX
    val rxMinSize = AtomicInteger(Int.MAX_VALUE)
    val rxMaxSize = AtomicInteger(0)
    val rxBucketUnder256 = AtomicLong(0L)
    val rxBucket256To512 = AtomicLong(0L)
    val rxBucket512To1024 = AtomicLong(0L)
    val rxBucket1024To1280 = AtomicLong(0L)
    val rxBucket1280To1400 = AtomicLong(0L)
    val rxBucket1400To1500 = AtomicLong(0L)
    val rxBucketOver1500 = AtomicLong(0L)

    // MTU / MSS Diagnostics
    val observedTcpMss = AtomicInteger(0)
    val ipv4Fragments = AtomicLong(0L)
    val ipv6Fragments = AtomicLong(0L)

    // Rate calculation state
    private var lastRateCalcTime = System.currentTimeMillis()
    private var lastTxBytesSnapshot = 0L
    private var lastRxBytesSnapshot = 0L
    private var lastTxPacketsSnapshot = 0L
    private var lastRxPacketsSnapshot = 0L

    fun reset() {
        totalTxBytes.set(0L)
        totalRxBytes.set(0L)
        totalTxPackets.set(0L)
        totalRxPackets.set(0L)
        totalTxDrops.set(0L)
        totalRxDrops.set(0L)
        nativeSendFailures.set(0L)
        nativeReceiveErrors.set(0L)

        tunTxPackets.set(0L)
        tunTxBytes.set(0L)
        tunTxDrops.set(0L)
        encapPackets.set(0L)
        encapBytes.set(0L)
        jniSendPackets.set(0L)
        jniSendBytes.set(0L)
        jniSendFailures.set(0L)

        seRxPackets.set(0L)
        seRxBytes.set(0L)
        jniRecvPackets.set(0L)
        jniRecvBytes.set(0L)
        jniRecvErrors.set(0L)
        decapPackets.set(0L)
        decapBytes.set(0L)
        tunRxPackets.set(0L)
        tunRxBytes.set(0L)
        tunRxDrops.set(0L)

        txMinSize.set(Int.MAX_VALUE)
        txMaxSize.set(0)
        txBucketUnder256.set(0L)
        txBucket256To512.set(0L)
        txBucket512To1024.set(0L)
        txBucket1024To1280.set(0L)
        txBucket1280To1400.set(0L)
        txBucket1400To1500.set(0L)
        txBucketOver1500.set(0L)

        rxMinSize.set(Int.MAX_VALUE)
        rxMaxSize.set(0)
        rxBucketUnder256.set(0L)
        rxBucket256To512.set(0L)
        rxBucket512To1024.set(0L)
        rxBucket1024To1280.set(0L)
        rxBucket1280To1400.set(0L)
        rxBucket1400To1500.set(0L)
        rxBucketOver1500.set(0L)

        observedTcpMss.set(0)
        ipv4Fragments.set(0L)
        ipv6Fragments.set(0L)

        lastRateCalcTime = System.currentTimeMillis()
        lastTxBytesSnapshot = 0L
        lastRxBytesSnapshot = 0L
        lastTxPacketsSnapshot = 0L
        lastRxPacketsSnapshot = 0L

        _diagnosticsFlow.value = TunnelDiagnostics.EMPTY
    }

    fun recordTxPacket(size: Int, isDetailedStats: Boolean) {
        totalTxPackets.incrementAndGet()
        totalTxBytes.addAndGet(size.toLong())

        if (isDetailedStats) {
            // Update min/max size
            var currentMin = txMinSize.get()
            while (size < currentMin && !txMinSize.compareAndSet(currentMin, size)) {
                currentMin = txMinSize.get()
            }
            var currentMax = txMaxSize.get()
            while (size > currentMax && !txMaxSize.compareAndSet(currentMax, size)) {
                currentMax = txMaxSize.get()
            }

            // Histogram buckets
            when {
                size < 256 -> txBucketUnder256.incrementAndGet()
                size in 256..512 -> txBucket256To512.incrementAndGet()
                size in 513..1024 -> txBucket512To1024.incrementAndGet()
                size in 1025..1280 -> txBucket1024To1280.incrementAndGet()
                size in 1281..1400 -> txBucket1280To1400.incrementAndGet()
                size in 1401..1500 -> txBucket1400To1500.incrementAndGet()
                else -> txBucketOver1500.incrementAndGet()
            }
        }
    }

    fun recordRxPacket(size: Int, isDetailedStats: Boolean) {
        totalRxPackets.incrementAndGet()
        totalRxBytes.addAndGet(size.toLong())

        if (isDetailedStats) {
            var currentMin = rxMinSize.get()
            while (size < currentMin && !rxMinSize.compareAndSet(currentMin, size)) {
                currentMin = rxMinSize.get()
            }
            var currentMax = rxMaxSize.get()
            while (size > currentMax && !rxMaxSize.compareAndSet(currentMax, size)) {
                currentMax = rxMaxSize.get()
            }

            when {
                size < 256 -> rxBucketUnder256.incrementAndGet()
                size in 256..512 -> rxBucket256To512.incrementAndGet()
                size in 513..1024 -> rxBucket512To1024.incrementAndGet()
                size in 1025..1280 -> rxBucket1024To1280.incrementAndGet()
                size in 1281..1400 -> rxBucket1280To1400.incrementAndGet()
                size in 1401..1500 -> rxBucket1400To1500.incrementAndGet()
                else -> rxBucketOver1500.incrementAndGet()
            }
        }
    }

    fun inspectIpHeader(packet: ByteArray, isTx: Boolean) {
        if (packet.isEmpty()) return
        val version = (packet[0].toInt().ushr(4)) and 0x0F
        if (version == 4 && packet.size >= 20) {
            val flagsAndFrag = ((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF)
            val moreFragments = (packet[6].toInt() and 0x20) != 0
            val fragOffset = flagsAndFrag and 0x1FFF
            if (moreFragments || fragOffset > 0) {
                ipv4Fragments.incrementAndGet()
            }
            val protocol = packet[9].toInt() and 0xFF
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (protocol == 6 && packet.size >= ihl + 20) { // TCP
                val tcpFlags = packet[ihl + 13].toInt() and 0x3F
                val isSyn = (tcpFlags and 0x02) != 0
                if (isSyn) {
                    val dataOffset = ((packet[ihl + 12].toInt().ushr(4)) and 0x0F) * 4
                    var optIdx = ihl + 20
                    while (optIdx + 4 <= ihl + dataOffset && optIdx < packet.size) {
                        val kind = packet[optIdx].toInt() and 0xFF
                        if (kind == 0) break
                        if (kind == 1) {
                            optIdx++
                            continue
                        }
                        val len = packet[optIdx + 1].toInt() and 0xFF
                        if (len < 2) break
                        if (kind == 2 && len == 4 && optIdx + 4 <= packet.size) { // MSS
                            val mss = ((packet[optIdx + 2].toInt() and 0xFF) shl 8) or (packet[optIdx + 3].toInt() and 0xFF)
                            observedTcpMss.set(mss)
                            break
                        }
                        optIdx += len
                    }
                }
            }
        } else if (version == 6 && packet.size >= 40) {
            val nextHeader = packet[6].toInt() and 0xFF
            if (nextHeader == 44) { // IPv6 Fragment Header
                ipv6Fragments.incrementAndGet()
            }
        }
    }

    fun updateDiagnostics(
        isConnected: Boolean,
        connectionState: String,
        transportProtocol: String,
        udpAccelerationStatus: String,
        rudpVersion: String,
        requestedConnections: Int,
        activeConnections: Int,
        serverMaxConnections: Int,
        tunCreated: Boolean,
        tunMtu: Int,
        vpnIp: String,
        vpnNetmask: String,
        gateway: String,
        dnsServers: List<String>,
        isIpv4: Boolean,
        isIpv6: Boolean,
        routes: List<String>,
        excludedRoutes: List<String>,
        serverIp: String,
        isServerIpExcluded: Boolean,
        underlyingNetwork: String,
        sockets: List<SocketProtectInfo>
    ) {
        val now = System.currentTimeMillis()
        val timeDiff = (now - lastRateCalcTime).coerceAtLeast(1L)

        val curTxBytes = totalTxBytes.get()
        val curRxBytes = totalRxBytes.get()
        val curTxPackets = totalTxPackets.get()
        val curRxPackets = totalRxPackets.get()

        val txBytesPerSec = ((curTxBytes - lastTxBytesSnapshot) * 1000L) / timeDiff
        val rxBytesPerSec = ((curRxBytes - lastRxBytesSnapshot) * 1000L) / timeDiff
        val txPktsPerSec = ((curTxPackets - lastTxPacketsSnapshot) * 1000L) / timeDiff
        val rxPktsPerSec = ((curRxPackets - lastRxPacketsSnapshot) * 1000L) / timeDiff

        lastRateCalcTime = now
        lastTxBytesSnapshot = curTxBytes
        lastRxBytesSnapshot = curRxBytes
        lastTxPacketsSnapshot = curTxPackets
        lastRxPacketsSnapshot = curRxPackets

        val txMin = txMinSize.get().let { if (it == Int.MAX_VALUE) 0 else it }
        val rxMin = rxMinSize.get().let { if (it == Int.MAX_VALUE) 0 else it }
        val txAvg = if (curTxPackets > 0) (curTxBytes / curTxPackets).toInt() else 0
        val rxAvg = if (curRxPackets > 0) (curRxBytes / curRxPackets).toInt() else 0

        val txStats = PacketSizeStats(
            minSize = txMin,
            maxSize = txMaxSize.get(),
            avgSize = txAvg,
            bucketUnder256 = txBucketUnder256.get(),
            bucket256To512 = txBucket256To512.get(),
            bucket512To1024 = txBucket512To1024.get(),
            bucket1024To1280 = txBucket1024To1280.get(),
            bucket1280To1400 = txBucket1280To1400.get(),
            bucket1400To1500 = txBucket1400To1500.get(),
            bucketOver1500 = txBucketOver1500.get()
        )

        val rxStats = PacketSizeStats(
            minSize = rxMin,
            maxSize = rxMaxSize.get(),
            avgSize = rxAvg,
            bucketUnder256 = rxBucketUnder256.get(),
            bucket256To512 = rxBucket256To512.get(),
            bucket512To1024 = rxBucket512To1024.get(),
            bucket1024To1280 = rxBucket1024To1280.get(),
            bucket1280To1400 = rxBucket1280To1400.get(),
            bucket1400To1500 = rxBucket1400To1500.get(),
            bucketOver1500 = rxBucketOver1500.get()
        )

        _diagnosticsFlow.value = TunnelDiagnostics(
            isConnected = isConnected,
            connectionState = connectionState,
            transportProtocol = transportProtocol,
            udpAccelerationStatus = udpAccelerationStatus,
            rudpVersion = rudpVersion,
            requestedConnections = requestedConnections,
            activeConnections = activeConnections,
            serverMaxConnections = serverMaxConnections,
            tunCreated = tunCreated,
            tunMtu = tunMtu,
            vpnIp = vpnIp,
            vpnNetmask = vpnNetmask,
            gateway = gateway,
            dnsServers = dnsServers,
            isIpv4 = isIpv4,
            isIpv6 = isIpv6,
            routes = routes,
            excludedRoutes = excludedRoutes,
            serverIp = serverIp,
            isServerIpExcluded = isServerIpExcluded,
            underlyingNetwork = underlyingNetwork,
            sockets = sockets,
            txBytesPerSec = txBytesPerSec.coerceAtLeast(0L),
            rxBytesPerSec = rxBytesPerSec.coerceAtLeast(0L),
            txPacketsPerSec = txPktsPerSec.coerceAtLeast(0L),
            rxPacketsPerSec = rxPktsPerSec.coerceAtLeast(0L),
            totalTxBytes = curTxBytes,
            totalRxBytes = curRxBytes,
            totalTxPackets = curTxPackets,
            totalRxPackets = curRxPackets,
            packetDrops = totalTxDrops.get() + totalRxDrops.get(),
            nativeSendFailures = nativeSendFailures.get(),
            nativeReceiveErrors = nativeReceiveErrors.get(),
            stageTunTx = PipelineStageStats(stageName = "1. TUN Read (TX)", packets = tunTxPackets.get(), bytes = tunTxBytes.get(), errors = 0L, drops = tunTxDrops.get()),
            stageEncapsulation = PipelineStageStats(stageName = "2. Encapsulation", packets = encapPackets.get(), bytes = encapBytes.get(), errors = 0L, drops = 0L),
            stageJniSend = PipelineStageStats(stageName = "3. JNI Send", packets = jniSendPackets.get(), bytes = jniSendBytes.get(), errors = jniSendFailures.get(), drops = 0L),
            stageSoftEtherRx = PipelineStageStats(stageName = "4. SE Native RX", packets = seRxPackets.get(), bytes = seRxBytes.get(), errors = 0L, drops = 0L),
            stageJniReceive = PipelineStageStats(stageName = "5. JNI Receive", packets = jniRecvPackets.get(), bytes = jniRecvBytes.get(), errors = jniRecvErrors.get(), drops = 0L),
            stageDecapsulation = PipelineStageStats(stageName = "6. Decapsulation", packets = decapPackets.get(), bytes = decapBytes.get(), errors = 0L, drops = 0L),
            stageTunRx = PipelineStageStats(stageName = "7. TUN Write (RX)", packets = tunRxPackets.get(), bytes = tunRxBytes.get(), errors = 0L, drops = tunRxDrops.get()),
            txSizeStats = txStats,
            rxSizeStats = rxStats,
            observedTcpMss = observedTcpMss.get(),
            ipv4Fragments = ipv4Fragments.get(),
            ipv6Fragments = ipv6Fragments.get(),
            lastUpdatedTimestamp = now
        )
    }

    fun buildSnapshot(
        devSettings: com.softether.model.DeveloperSettings,
        assignedIp: String,
        gatewayIp: String,
        dnsServers: List<String>,
        currentMtu: Int,
        serverHost: String,
        serverPort: Int,
        virtualHub: String,
        numConnections: Int,
        serverMaxConnections: Int,
        isRudpEnabled: Boolean,
        rudpVersion: Int,
        isIpv6: Boolean,
        sockets: List<SocketProtectInfo>
    ): TunnelDiagnostics {
        val now = System.currentTimeMillis()
        val timeDiff = (now - lastRateCalcTime).coerceAtLeast(1L)

        val curTxBytes = totalTxBytes.get()
        val curRxBytes = totalRxBytes.get()
        val curTxPackets = totalTxPackets.get()
        val curRxPackets = totalRxPackets.get()

        val txBytesPerSec = ((curTxBytes - lastTxBytesSnapshot) * 1000L) / timeDiff
        val rxBytesPerSec = ((curRxBytes - lastRxBytesSnapshot) * 1000L) / timeDiff
        val txPktsPerSec = ((curTxPackets - lastTxPacketsSnapshot) * 1000L) / timeDiff
        val rxPktsPerSec = ((curRxPackets - lastRxPacketsSnapshot) * 1000L) / timeDiff

        lastRateCalcTime = now
        lastTxBytesSnapshot = curTxBytes
        lastRxBytesSnapshot = curRxBytes
        lastTxPacketsSnapshot = curTxPackets
        lastRxPacketsSnapshot = curRxPackets

        val txMin = txMinSize.get().let { if (it == Int.MAX_VALUE) 0 else it }
        val rxMin = rxMinSize.get().let { if (it == Int.MAX_VALUE) 0 else it }
        val txAvg = if (curTxPackets > 0) (curTxBytes / curTxPackets).toInt() else 0
        val rxAvg = if (curRxPackets > 0) (curRxBytes / curRxPackets).toInt() else 0

        val txStats = PacketSizeStats(
            minSize = txMin,
            maxSize = txMaxSize.get(),
            avgSize = txAvg,
            bucketUnder256 = txBucketUnder256.get(),
            bucket256To512 = txBucket256To512.get(),
            bucket512To1024 = txBucket512To1024.get(),
            bucket1024To1280 = txBucket1024To1280.get(),
            bucket1280To1400 = txBucket1280To1400.get(),
            bucket1400To1500 = txBucket1400To1500.get(),
            bucketOver1500 = txBucketOver1500.get()
        )

        val rxStats = PacketSizeStats(
            minSize = rxMin,
            maxSize = rxMaxSize.get(),
            avgSize = rxAvg,
            bucketUnder256 = rxBucketUnder256.get(),
            bucket256To512 = rxBucket256To512.get(),
            bucket512To1024 = rxBucket512To1024.get(),
            bucket1024To1280 = rxBucket1024To1280.get(),
            bucket1280To1400 = rxBucket1280To1400.get(),
            bucket1400To1500 = rxBucket1400To1500.get(),
            bucketOver1500 = rxBucketOver1500.get()
        )

        val snapshot = TunnelDiagnostics(
            isConnected = true,
            connectionState = "CONNECTED",
            serverHost = serverHost,
            serverPort = serverPort,
            virtualHub = virtualHub,
            assignedIp = assignedIp,
            gatewayIp = gatewayIp,
            currentMtu = currentMtu,
            tunMtu = currentMtu,
            vpnIp = assignedIp,
            gateway = gatewayIp,
            dnsServers = dnsServers,
            isIpv4 = !isIpv6,
            isIpv6 = isIpv6,
            transportProtocol = if (isRudpEnabled) "RUDP (UDP)" else "TCP",
            isRudpEnabled = isRudpEnabled,
            rudpVersion = "v$rudpVersion",
            requestedConnections = devSettings.maxConnections,
            activeConnections = numConnections,
            numConnections = numConnections,
            serverMaxConnections = serverMaxConnections,
            tunCreated = true,
            sockets = sockets,
            txBytesPerSec = txBytesPerSec.coerceAtLeast(0L),
            rxBytesPerSec = rxBytesPerSec.coerceAtLeast(0L),
            txPacketsPerSec = txPktsPerSec.coerceAtLeast(0L),
            rxPacketsPerSec = rxPktsPerSec.coerceAtLeast(0L),
            totalTxBytes = curTxBytes,
            totalRxBytes = curRxBytes,
            totalTxPackets = curTxPackets,
            totalRxPackets = curRxPackets,
            packetDrops = totalTxDrops.get() + totalRxDrops.get(),
            nativeSendFailures = nativeSendFailures.get(),
            nativeReceiveErrors = nativeReceiveErrors.get(),
            stageTunTx = PipelineStageStats(stageName = "1. TUN Read (TX)", packets = tunTxPackets.get(), bytes = tunTxBytes.get(), errors = 0L, drops = tunTxDrops.get()),
            stageEncapsulation = PipelineStageStats(stageName = "2. Encapsulation", packets = encapPackets.get(), bytes = encapBytes.get(), errors = 0L, drops = 0L),
            stageJniSend = PipelineStageStats(stageName = "3. JNI Send", packets = jniSendPackets.get(), bytes = jniSendBytes.get(), errors = jniSendFailures.get(), drops = 0L),
            stageSoftEtherRx = PipelineStageStats(stageName = "4. SE Native RX", packets = seRxPackets.get(), bytes = seRxBytes.get(), errors = 0L, drops = 0L),
            stageJniReceive = PipelineStageStats(stageName = "5. JNI Receive", packets = jniRecvPackets.get(), bytes = jniRecvBytes.get(), errors = jniRecvErrors.get(), drops = 0L),
            stageDecapsulation = PipelineStageStats(stageName = "6. Decapsulation", packets = decapPackets.get(), bytes = decapBytes.get(), errors = 0L, drops = 0L),
            stageTunRx = PipelineStageStats(stageName = "7. TUN Write (RX)", packets = tunRxPackets.get(), bytes = tunRxBytes.get(), errors = 0L, drops = tunRxDrops.get()),
            txSizeStats = txStats,
            rxSizeStats = rxStats,
            observedTcpMss = observedTcpMss.get(),
            ipv4Fragments = ipv4Fragments.get(),
            ipv6Fragments = ipv6Fragments.get(),
            lastUpdatedTimestamp = now
        )
        _diagnosticsFlow.value = snapshot
        return snapshot
    }
}
