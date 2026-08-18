package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VpnSourceConfig(
    val id: String,
    val nameKey: String,
    val url: String,
    val isCsv: Boolean = false,
    val isEnabled: Boolean = true
)

data class DnsPreset(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val primary: String,
    val secondary: String,
    val descriptionEn: String,
    val descriptionFa: String
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vpngate_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_LANGUAGE = "key_language" // "en" or "fa"
        const val KEY_THEME = "key_theme" // "system", "dark", "light"
        const val KEY_PROTOCOL = "key_protocol" // "AUTO", "TCP", "UDP_V2", "UDP_V1"
        const val KEY_AUTH_METHOD = "key_auth_method" // "AUTO", "PASSWORD", "PLAIN_PASSWORD", "ANONYMOUS"
        const val KEY_LAST_UPDATE_TIME = "key_last_update_time"
        const val KEY_SPLIT_TUNNEL_ENABLED = "key_split_tunnel_enabled"
        const val KEY_SPLIT_TUNNEL_MODE = "key_split_tunnel_mode" // "exclude" or "include"
        const val KEY_SPLIT_TUNNEL_PACKAGES = "key_split_tunnel_packages"
        
        // DNS preferences
        const val KEY_DNS_MODE = "key_dns_mode" // "PRESET" or "CUSTOM"
        const val KEY_DNS_PRESET_ID = "key_dns_preset_id"
        const val KEY_DNS_PRIMARY = "key_dns_primary"
        const val KEY_DNS_SECONDARY = "key_dns_secondary"

        // Connection Target Host Mode: "HOSTNAME" or "IP"
        const val KEY_CONNECTION_HOST_MODE = "key_connection_host_mode"

        // Sources enabled/disabled keys
        const val PREFIX_SOURCE_ENABLED = "source_enabled_"

        // Developer / Performance Mode keys
        const val KEY_DEV_MODE_ENABLED = "key_dev_mode_enabled"
        const val KEY_DEV_MAX_CONNECTIONS = "key_dev_max_connections"
        const val KEY_DEV_MTU = "key_dev_mtu"
        const val KEY_DEV_BUFFER_SIZE = "key_dev_buffer_size"
        const val KEY_DEV_PACKET_LOG_LEVEL = "key_dev_packet_log_level"
        const val KEY_DEV_STATS_ENABLED = "key_dev_stats_enabled"
        const val KEY_DEV_STATS_INTERVAL_MS = "key_dev_stats_interval_ms"
        const val KEY_DEV_FORCE_FLUSH = "key_dev_force_flush"
        const val KEY_DEV_FLUSH_STRATEGY = "key_dev_flush_strategy"
        const val KEY_DEV_BUFFER_STRATEGY = "key_dev_buffer_strategy"
        const val KEY_DEV_UDP_ACCELERATION = "key_dev_udp_acceleration"
        const val KEY_DEV_DEBUG_LOG_LEVEL = "key_dev_debug_log_level"
    }

    val defaultDnsPresets = listOf(
        DnsPreset(
            id = "SHECAN",
            nameEn = "Shecan (Anti-Sanction Iran)",
            nameFa = "شکن (رفع تحریم ایران)",
            primary = "178.22.122.100",
            secondary = "185.51.200.2",
            descriptionEn = "Bypass sanctions for developers & Iranian users",
            descriptionFa = "رفع تحریم اینترنتی و برنامه‌نویسی مخصوص ایران"
        ),
        DnsPreset(
            id = "ELECTRO",
            nameEn = "Electro (Gaming & Anti-Sanction)",
            nameFa = "الکترو (گیمینگ و رفع تحریم)",
            primary = "78.157.42.100",
            secondary = "78.157.42.101",
            descriptionEn = "Optimized for online games & restricted services",
            descriptionFa = "بهینه‌شده برای بازی‌های آنلاین و سرویس‌های تحریم‌شده"
        ),
        DnsPreset(
            id = "403_ONLINE",
            nameEn = "403 Online",
            nameFa = "۴۰۳ آنلاین (رفع تحریم)",
            primary = "10.202.10.202",
            secondary = "10.202.10.102",
            descriptionEn = "Bypass 403 Forbidden developer sanctions",
            descriptionFa = "رفع خطای ۴۰۳ و تحریم‌های برنامه‌نویسان"
        ),
        DnsPreset(
            id = "RADAR_GAME",
            nameEn = "Radar Game",
            nameFa = "رادار گیم (کاهش پینگ)",
            primary = "10.202.10.10",
            secondary = "10.202.10.11",
            descriptionEn = "Low ping & anti-sanction for gamers",
            descriptionFa = "کاهش پینگ و رفع تحریم بازی‌ها"
        ),
        DnsPreset(
            id = "CLOUDFLARE",
            nameEn = "Cloudflare (1.1.1.1)",
            nameFa = "کلادفلر (1.1.1.1)",
            primary = "1.1.1.1",
            secondary = "1.0.0.1",
            descriptionEn = "Ultra fast private public DNS",
            descriptionFa = "دی‌ان‌اس بسیار سریع و عمومی با حریم خصوصی بالا"
        ),
        DnsPreset(
            id = "GOOGLE",
            nameEn = "Google DNS",
            nameFa = "گوگل دی‌ان‌اس (8.8.8.8)",
            primary = "8.8.8.8",
            secondary = "8.8.4.4",
            descriptionEn = "Reliable global DNS service",
            descriptionFa = "دی‌ان‌اس جهانی و باثبات گوگل"
        ),
        DnsPreset(
            id = "ADGUARD",
            nameEn = "AdGuard (Ad Blocker)",
            nameFa = "ادگارد (مسدودکننده تبلیغات)",
            primary = "94.140.14.14",
            secondary = "94.140.15.15",
            descriptionEn = "Blocks ads, trackers & phishing sites",
            descriptionFa = "مسدودسازی تبلیغات و سایت‌های مخرب"
        ),
        DnsPreset(
            id = "DEFAULT",
            nameEn = "Default (Google & Cloudflare)",
            nameFa = "پیش‌فرض (گوگل و کلادفلر)",
            primary = "8.8.8.8",
            secondary = "1.1.1.1",
            descriptionEn = "Standard VPN DNS servers",
            descriptionFa = "سرورهای عمومی پیش‌فرض وی‌پی‌ان"
        )
    )

    val defaultSources = listOf(
        VpnSourceConfig("csv_official", "source_official_api", "https://www.vpngate.net/api/iphone/", isCsv = true),
        VpnSourceConfig("mirror_hr_1", "source_mirror_hr_1", "http://150.40.105.16:33958/en/"),
        VpnSourceConfig("mirror_hr_2", "source_mirror_hr_2", "http://150.40.105.7:15596/en/"),
        VpnSourceConfig("mirror_hr_3", "source_mirror_hr_3", "http://150.40.105.8:61446/en/"),
        VpnSourceConfig("mirror_am_1", "source_mirror_am_1", "http://185.215.244.28:6912/en/"),
        VpnSourceConfig("mirror_hr_4", "source_mirror_hr_4", "http://150.40.105.19:35399/en/"),
        VpnSourceConfig("mirror_hr_5", "source_mirror_hr_5", "http://150.40.105.25:65488/en/"),
        VpnSourceConfig("vpngate_original", "source_official_web", "https://www.vpngate.net/en/")
    )

    private val _languageFlow = MutableStateFlow(getLanguage())
    val languageFlow: StateFlow<String> = _languageFlow

    private val _themeFlow = MutableStateFlow(getTheme())
    val themeFlow: StateFlow<String> = _themeFlow

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _languageFlow.value = lang
    }

    fun getTheme(): String = prefs.getString(KEY_THEME, "system") ?: "system"
    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _themeFlow.value = theme
    }

    fun getProtocol(): String = prefs.getString(KEY_PROTOCOL, "AUTO") ?: "AUTO"
    fun setProtocol(protocol: String) {
        prefs.edit().putString(KEY_PROTOCOL, protocol).apply()
    }

    fun getAuthMethod(): String = prefs.getString(KEY_AUTH_METHOD, "AUTO") ?: "AUTO"
    fun setAuthMethod(authMethod: String) {
        prefs.edit().putString(KEY_AUTH_METHOD, authMethod).apply()
    }

    fun getLastUpdateTime(): Long = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
    fun setLastUpdateTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_TIME, timestamp).apply()
    }

    fun isSourceEnabled(sourceId: String): Boolean {
        // By default only the healthy HTML main-page source is enabled; all
        // other sources (CSV API and mirrors) are disabled until verified.
        return prefs.getBoolean(PREFIX_SOURCE_ENABLED + sourceId, sourceId == "vpngate_original")
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        prefs.edit().putBoolean(PREFIX_SOURCE_ENABLED + sourceId, enabled).apply()
    }

    fun isSplitTunnelEnabled(): Boolean = prefs.getBoolean(KEY_SPLIT_TUNNEL_ENABLED, false)
    fun setSplitTunnelEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPLIT_TUNNEL_ENABLED, enabled).apply()
    }

    fun getSplitTunnelMode(): String = prefs.getString(KEY_SPLIT_TUNNEL_MODE, "exclude") ?: "exclude"
    fun setSplitTunnelMode(mode: String) {
        prefs.edit().putString(KEY_SPLIT_TUNNEL_MODE, mode).apply()
    }

    fun getSplitTunnelPackages(): Set<String> {
        return prefs.getStringSet(KEY_SPLIT_TUNNEL_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setSplitTunnelPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SPLIT_TUNNEL_PACKAGES, packages).apply()
    }

    // DNS Methods
    fun getDnsMode(): String = prefs.getString(KEY_DNS_MODE, "PRESET") ?: "PRESET"
    fun setDnsMode(mode: String) {
        prefs.edit().putString(KEY_DNS_MODE, mode).apply()
    }

    fun getDnsPresetId(): String = prefs.getString(KEY_DNS_PRESET_ID, "GOOGLE") ?: "GOOGLE"
    fun setDnsPresetId(id: String) {
        prefs.edit().putString(KEY_DNS_PRESET_ID, id).apply()
    }

    fun getCustomDnsPrimary(): String = prefs.getString(KEY_DNS_PRIMARY, "8.8.8.8") ?: "8.8.8.8"
    fun setCustomDnsPrimary(ip: String) {
        prefs.edit().putString(KEY_DNS_PRIMARY, ip).apply()
    }

    fun getCustomDnsSecondary(): String = prefs.getString(KEY_DNS_SECONDARY, "8.8.4.4") ?: "8.8.4.4"
    fun setCustomDnsSecondary(ip: String) {
        prefs.edit().putString(KEY_DNS_SECONDARY, ip).apply()
    }

    fun getEffectiveDnsServers(): List<String> {
        val mode = getDnsMode()
        if (mode == "CUSTOM") {
            val primary = getCustomDnsPrimary()
            val secondary = getCustomDnsSecondary()
            val list = mutableListOf<String>()
            if (primary.isNotBlank()) list.add(primary.trim())
            if (secondary.isNotBlank()) list.add(secondary.trim())
            if (list.isEmpty()) return listOf("178.22.122.100", "185.51.200.2")
            return list
        } else {
            val presetId = getDnsPresetId()
            val preset = defaultDnsPresets.find { it.id == presetId } ?: defaultDnsPresets.first()
            return listOf(preset.primary, preset.secondary)
        }
    }

    fun getConnectionHostMode(): String = prefs.getString(KEY_CONNECTION_HOST_MODE, "HOSTNAME") ?: "HOSTNAME"
    fun setConnectionHostMode(mode: String) {
        prefs.edit().putString(KEY_CONNECTION_HOST_MODE, mode).apply()
    }

    // Developer / Performance Mode Methods
    fun isDeveloperModeEnabled(): Boolean = prefs.getBoolean(KEY_DEV_MODE_ENABLED, false)
    fun setDeveloperModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_MODE_ENABLED, enabled).apply()
    }

    fun getDeveloperSettings(): com.softether.model.DeveloperSettings {
        return com.softether.model.DeveloperSettings(
            isDeveloperModeEnabled = prefs.getBoolean(KEY_DEV_MODE_ENABLED, false),
            maxConnections = prefs.getInt(KEY_DEV_MAX_CONNECTIONS, 1),
            mtu = prefs.getInt(KEY_DEV_MTU, 1400),
            bufferSize = prefs.getInt(KEY_DEV_BUFFER_SIZE, 65536),
            flushStrategy = try {
                com.softether.model.FlushStrategy.valueOf(prefs.getString(KEY_DEV_FLUSH_STRATEGY, "IMMEDIATE") ?: "IMMEDIATE")
            } catch (_: Exception) { com.softether.model.FlushStrategy.IMMEDIATE },
            packetLogLevel = try {
                com.softether.model.PacketLogLevel.valueOf(prefs.getString(KEY_DEV_PACKET_LOG_LEVEL, "OFF") ?: "OFF")
            } catch (_: Exception) { com.softether.model.PacketLogLevel.OFF },
            isPerformanceStatsEnabled = prefs.getBoolean(KEY_DEV_STATS_ENABLED, false),
            statsIntervalMs = prefs.getLong(KEY_DEV_STATS_INTERVAL_MS, 1000L),
            forceFlush = prefs.getBoolean(KEY_DEV_FORCE_FLUSH, true),
            bufferStrategy = try {
                com.softether.model.PacketBufferStrategy.valueOf(prefs.getString(KEY_DEV_BUFFER_STRATEGY, "SAFE_CURRENT") ?: "SAFE_CURRENT")
            } catch (_: Exception) { com.softether.model.PacketBufferStrategy.SAFE_CURRENT },
            udpAcceleration = try {
                com.softether.model.UdpAccelerationSetting.valueOf(prefs.getString(KEY_DEV_UDP_ACCELERATION, "AUTO") ?: "AUTO")
            } catch (_: Exception) { com.softether.model.UdpAccelerationSetting.AUTO },
            debugLogLevel = try {
                com.softether.model.DebugLogLevel.valueOf(prefs.getString(KEY_DEV_DEBUG_LOG_LEVEL, "DEBUG") ?: "DEBUG")
            } catch (_: Exception) { com.softether.model.DebugLogLevel.DEBUG }
        )
    }

    fun setDeveloperSettings(settings: com.softether.model.DeveloperSettings) {
        prefs.edit()
            .putBoolean(KEY_DEV_MODE_ENABLED, settings.isDeveloperModeEnabled)
            .putInt(KEY_DEV_MAX_CONNECTIONS, settings.maxConnections)
            .putInt(KEY_DEV_MTU, settings.mtu)
            .putInt(KEY_DEV_BUFFER_SIZE, settings.bufferSize)
            .putString(KEY_DEV_FLUSH_STRATEGY, settings.flushStrategy.name)
            .putString(KEY_DEV_PACKET_LOG_LEVEL, settings.packetLogLevel.name)
            .putBoolean(KEY_DEV_STATS_ENABLED, settings.isPerformanceStatsEnabled)
            .putLong(KEY_DEV_STATS_INTERVAL_MS, settings.statsIntervalMs)
            .putBoolean(KEY_DEV_FORCE_FLUSH, settings.forceFlush)
            .putString(KEY_DEV_BUFFER_STRATEGY, settings.bufferStrategy.name)
            .putString(KEY_DEV_UDP_ACCELERATION, settings.udpAcceleration.name)
            .putString(KEY_DEV_DEBUG_LOG_LEVEL, settings.debugLogLevel.name)
            .apply()
    }

    fun saveDeveloperSettings(settings: com.softether.model.DeveloperSettings) {
        setDeveloperSettings(settings)
    }

    fun resetDeveloperSettings() {
        val currentEnabled = isDeveloperModeEnabled()
        setDeveloperSettings(com.softether.model.DeveloperSettings.DEFAULT.copy(isDeveloperModeEnabled = currentEnabled))
    }
}

