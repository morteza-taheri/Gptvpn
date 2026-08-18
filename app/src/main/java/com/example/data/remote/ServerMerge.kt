package com.example.data.remote

import com.example.data.model.VpnServer

/**
 * Utility to merge duplicate VPN servers discovered across multiple sources/protocols.
 */
object ServerMerge {
    fun mergeServers(servers: List<VpnServer>): List<VpnServer> {
        val grouped = servers.groupBy { it.ip.ifEmpty { it.hostName } }
        return grouped.map { (_, list) ->
            if (list.size == 1) {
                list.first()
            } else {
                val primary = list.maxByOrNull { it.score } ?: list.first()
                val mergedSourceIds = list.flatMap { it.sourceIds.ifEmpty { listOf(it.id) } }.distinct()
                val softEtherTcp = list.mapNotNull { it.softEtherTcpPort }.firstOrNull()
                val softEtherUdp = list.mapNotNull { it.softEtherUdpPort }.firstOrNull()
                val openVpnTcp = list.mapNotNull { it.openVpnTcpPort }.firstOrNull()
                val openVpnUdp = list.mapNotNull { it.openVpnUdpPort }.firstOrNull()
                val sstpHost = list.mapNotNull { it.sstpHostname }.firstOrNull()
                val sstpPort = list.mapNotNull { it.sstpPort }.firstOrNull()
                val sourceUrl = list.mapNotNull { it.sourceUrl }.firstOrNull()

                primary.copy(
                    softEtherTcpPort = softEtherTcp ?: primary.softEtherTcpPort,
                    softEtherUdpPort = softEtherUdp ?: primary.softEtherUdpPort,
                    softEtherUdpSupported = softEtherUdp != null || list.any { it.softEtherUdpSupported },
                    openVpnTcpPort = openVpnTcp ?: primary.openVpnTcpPort,
                    openVpnUdpPort = openVpnUdp ?: primary.openVpnUdpPort,
                    sstpHostname = sstpHost ?: primary.sstpHostname,
                    sstpPort = sstpPort ?: primary.sstpPort,
                    sourceUrl = sourceUrl ?: primary.sourceUrl,
                    sourceIds = mergedSourceIds,
                    sourceCount = mergedSourceIds.size.coerceAtLeast(list.size)
                )
            }
        }
    }
}
