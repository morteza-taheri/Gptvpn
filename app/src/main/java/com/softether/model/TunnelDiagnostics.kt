package com.softether.model

data class SocketProtectInfo(
    val fd: Int,
    val socketType: String = "TCP",
    val isProtected: Boolean = true,
    val name: String = socketType
)

data class PipelineStageStats(
    val stageName: String = "",
    val packets: Long = 0L,
    val bytes: Long = 0L,
    val errors: Long = 0L,
    val drops: Long = 0L
)

data class PacketSizeStats(
    val minSize: Int = 0,
    val maxSize: Int = 0,
    val avgSize: Int = 0,
    val bucketUnder256: Long = 0L,
    val bucket256To512: Long = 0L,
    val bucket512To1024: Long = 0L,
    val bucket1024To1280: Long = 0L,
    val bucket1280To1400: Long = 0L,
    val bucket1400To1500: Long = 0L,
    val bucketOver1500: Long = 0L
)

data class TunnelDiagnostics(
    val isConnected: Boolean = false,
    val connectionState: String = "DISCONNECTED",
    // Target Server Info
    val serverHost: String = "N/A",
    val serverPort: Int = 443,
    val virtualHub: String = "DEFAULT",
    val serverIp: String = "N/A",
    val isServerIpExcluded: Boolean = true,
    val underlyingNetwork: String = "Default",
    // Transport & Connections
    val transportProtocol: String = "TCP",
    val udpAccelerationStatus: String = "UDP Acceleration Inactive",
    val isRudpEnabled: Boolean = false,
    val rudpVersion: String = "Inactive",
    val requestedConnections: Int = 1,
    val activeConnections: Int = 0,
    val numConnections: Int = 0,
    val serverMaxConnections: Int = 0,
    // TUN Interface & IP assignment
    val tunCreated: Boolean = false,
    val tunMtu: Int = 1400,
    val currentMtu: Int = 1400,
    val vpnIp: String = "N/A",
    val assignedIp: String = "N/A",
    val vpnNetmask: String = "N/A",
    val gateway: String = "N/A",
    val gatewayIp: String = "N/A",
    val dnsServers: List<String> = emptyList(),
    val isIpv4: Boolean = true,
    val isIpv6: Boolean = false,
    // Routing
    val routes: List<String> = emptyList(),
    val excludedRoutes: List<String> = emptyList(),
    // Socket Protection
    val sockets: List<SocketProtectInfo> = emptyList(),
    // Rates & Totals
    val txBytesPerSec: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txPacketsPerSec: Long = 0L,
    val rxPacketsPerSec: Long = 0L,
    val totalTxBytes: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxPackets: Long = 0L,
    val totalRxPackets: Long = 0L,
    val packetDrops: Long = 0L,
    val nativeSendFailures: Long = 0L,
    val nativeReceiveErrors: Long = 0L,
    // Pipeline diagnostics (7 distinct stages)
    val stageTunTx: PipelineStageStats = PipelineStageStats("1. TUN Read (TX)"),
    val stageTunRead: PipelineStageStats = stageTunTx,
    val stageEncapsulation: PipelineStageStats = PipelineStageStats("2. Encapsulation"),
    val stageJniSend: PipelineStageStats = PipelineStageStats("3. JNI Send"),
    val stageSoftEtherRx: PipelineStageStats = PipelineStageStats("4. SE Native RX"),
    val stageJniReceive: PipelineStageStats = PipelineStageStats("5. JNI Receive"),
    val stageDecapsulation: PipelineStageStats = PipelineStageStats("6. Decapsulation"),
    val stageTunRx: PipelineStageStats = PipelineStageStats("7. TUN Write (RX)"),
    val stageTunWrite: PipelineStageStats = stageTunRx,
    // Packet size distributions
    val txSizeStats: PacketSizeStats = PacketSizeStats(),
    val rxSizeStats: PacketSizeStats = PacketSizeStats(),
    // MTU / MSS Diagnostics
    val observedTcpMss: Int = 0,
    val ipv4Fragments: Long = 0L,
    val ipv6Fragments: Long = 0L,
    val lastUpdatedTimestamp: Long = 0L
) {
    val txKbps: Float get() = (txBytesPerSec * 8f) / 1000f
    val rxKbps: Float get() = (rxBytesPerSec * 8f) / 1000f

    companion object {
        val EMPTY = TunnelDiagnostics()
    }
}
