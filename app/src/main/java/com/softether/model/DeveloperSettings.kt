package com.softether.model

/**
 * Packet logging verbosity levels for developer mode
 */
enum class PacketLogLevel {
    OFF,
    BASIC,
    VERBOSE
}

/**
 * TUN Output Flush strategy
 */
enum class FlushStrategy {
    IMMEDIATE,
    AUTO,
    BATCH
}

/**
 * General application debug logging levels
 */
enum class DebugLogLevel {
    OFF,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE
}

/**
 * Memory/Buffer copy strategy in TUN <-> JNI data path
 */
enum class PacketBufferStrategy {
    SAFE_CURRENT,
    REUSABLE_BUFFER,
    MINIMAL_COPY
}

/**
 * UDP Acceleration control
 */
enum class UdpAccelerationSetting {
    AUTO,
    ON,
    OFF
}

/**
 * Complete Developer Mode & Performance Tuning Settings Model.
 *
 * ALL DEFAULT VALUES EXACTLY MATCH PROJECT DEFAULTS:
 * - isDeveloperModeEnabled: false (OFF by default)
 * - maxConnections: 1 (current native setMaxConnection = 1)
 * - mtu: 1400 (matches ConnectionConfig default MTU = 1400)
 * - bufferSize: 65536 (matches default 65535 buffer in TunTerminal & ConnectionController)
 * - flushStrategy: FlushStrategy.IMMEDIATE
 * - packetLogLevel: PacketLogLevel.OFF
 * - isPerformanceStatsEnabled: false (OFF)
 * - statsIntervalMs: 1000L (1000 ms)
 * - forceFlush: true (matches current outputStream.flush() on TUN write)
 * - bufferStrategy: SAFE_CURRENT (matches current copyOf behavior)
 * - udpAcceleration: AUTO
 * - debugLogLevel: DEBUG
 */
data class DeveloperSettings(
    val isDeveloperModeEnabled: Boolean = false,
    val maxConnections: Int = 1,
    val mtu: Int = 1400,
    val bufferSize: Int = 65536,
    val flushStrategy: FlushStrategy = FlushStrategy.IMMEDIATE,
    val packetLogLevel: PacketLogLevel = PacketLogLevel.OFF,
    val isPerformanceStatsEnabled: Boolean = false,
    val statsIntervalMs: Long = 1000L,
    val forceFlush: Boolean = true,
    val bufferStrategy: PacketBufferStrategy = PacketBufferStrategy.SAFE_CURRENT,
    val udpAcceleration: UdpAccelerationSetting = UdpAccelerationSetting.AUTO,
    val debugLogLevel: DebugLogLevel = DebugLogLevel.DEBUG
) {
    companion object {
        val DEFAULT = DeveloperSettings()
    }
}
