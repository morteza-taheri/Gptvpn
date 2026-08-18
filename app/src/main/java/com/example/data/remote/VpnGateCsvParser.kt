package com.example.data.remote

import com.example.data.model.VpnServer
import java.io.BufferedReader
import java.io.StringReader

object VpnGateCsvParser {
    fun parseCsv(csvText: String, sourceName: String = "Official CSV"): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()
        val reader = BufferedReader(StringReader(csvText))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line?.trim() ?: continue
            if (currentLine.isEmpty() || currentLine.startsWith("*") || currentLine.startsWith("#")) {
                continue
            }

            val tokens = parseCsvRow(currentLine)
            if (tokens.size < 14) continue

            try {
                val hostName = tokens.getOrNull(0)?.trim().orEmpty()
                val ip = tokens.getOrNull(1)?.trim().orEmpty()
                if (hostName.isEmpty() && ip.isEmpty()) continue

                val score = tokens.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
                val pingMs = tokens.getOrNull(3)?.trim()?.toIntOrNull() ?: -1
                val speedBps = tokens.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
                val countryLong = tokens.getOrNull(5)?.trim().orEmpty()
                val countryShort = tokens.getOrNull(6)?.trim().orEmpty()
                val numSessions = tokens.getOrNull(7)?.trim()?.toIntOrNull() ?: 0
                val uptime = tokens.getOrNull(8)?.trim()?.toLongOrNull() ?: 0L
                val totUsers = tokens.getOrNull(9)?.trim()?.toLongOrNull() ?: 0L
                val totTraffic = tokens.getOrNull(10)?.trim()?.toLongOrNull() ?: 0L
                val logType = tokens.getOrNull(11)?.trim().orEmpty()
                val operator = tokens.getOrNull(12)?.trim().orEmpty()
                val comment = tokens.getOrNull(13)?.trim().orEmpty()
                val openVpnConfigData = tokens.getOrNull(14)?.trim()?.takeIf { it.isNotBlank() }

                var parsedTcpPort: Int? = null
                var parsedUdpPort: Int? = null
                var isUdpSupported = true

                if (!openVpnConfigData.isNullOrBlank()) {
                    try {
                        val decodedBytes = android.util.Base64.decode(openVpnConfigData, android.util.Base64.DEFAULT)
                        val configText = String(decodedBytes, Charsets.UTF_8)
                        
                        var currentProto = "tcp"
                        for (rawConfigLine in configText.split("\n")) {
                            val cfgLine = rawConfigLine.trim()
                            if (cfgLine.startsWith("proto ", ignoreCase = true)) {
                                currentProto = cfgLine.substring(6).trim().lowercase()
                            } else if (cfgLine.startsWith("remote ", ignoreCase = true)) {
                                val parts = cfgLine.split("\\s+".toRegex())
                                if (parts.size >= 3) {
                                    val port = parts[2].toIntOrNull()
                                    if (port != null && port in 1..65535) {
                                        val lineProto = if (parts.size >= 4) parts[3].lowercase() else currentProto
                                        if (lineProto.contains("udp")) {
                                            if (parsedUdpPort == null) parsedUdpPort = port
                                        } else {
                                            if (parsedTcpPort == null) parsedTcpPort = port
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                val primaryTcpPort = parsedTcpPort ?: 443
                val primaryUdpPort = parsedUdpPort ?: 1194

                val id = "${hostName}_${ip}"

                val server = VpnServer(
                    id = id,
                    hostName = hostName,
                    ip = ip,
                    score = score,
                    pingMs = pingMs,
                    speedBps = speedBps,
                    countryLong = countryLong,
                    countryShort = countryShort,
                    numSessions = numSessions,
                    uptime = uptime,
                    totUsers = totUsers,
                    totTraffic = totTraffic,
                    logType = logType,
                    operator = operator,
                    comment = comment,
                    openVpnConfigData = openVpnConfigData,
                    softEtherTcpPort = primaryTcpPort,
                    softEtherUdpSupported = isUdpSupported,
                    l2tpSupported = true,
                    openVpnTcpPort = primaryTcpPort,
                    openVpnUdpPort = primaryUdpPort,
                    source = sourceName,
                    lastSeenTime = System.currentTimeMillis()
                )
                servers.add(server)
            } catch (e: Exception) {
                // Ignore malformed rows
            }
        }
        return servers
    }

    /**
     * Parses a standard CSV line accounting for quoted fields with commas and escaped quotes ("").
     */
    fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
