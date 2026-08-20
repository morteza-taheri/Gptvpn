package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnServer
import com.example.ui.localization.Strings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailBottomSheet(
    server: VpnServer?,
    strings: Strings,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    if (server == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val primaryTcp = server.softEtherTcpPort ?: server.openVpnTcpPort ?: server.effectivePort
    val primaryUdp = server.softEtherUdpPort ?: server.openVpnUdpPort
    val uptimeFormatted = formatDetailUptime(server.uptime)
    val trafficFormatted = formatDetailTraffic(server.totTraffic)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryFlag(countryCode = server.countryShort, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (server.countryLong.isNotEmpty()) server.countryLong else server.countryShort,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${server.source} • ${server.countryShort.uppercase(Locale.ROOT)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Ports & Protocols
            Text(
                text = "Ports & Connection Details",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            DetailItem(
                label = strings.tcpPortLabel,
                value = "$primaryTcp (TCP)",
                isMonospace = true,
                valueColor = MaterialTheme.colorScheme.primary
            )

            DetailItem(
                label = strings.udpPortLabel,
                value = when {
                    primaryUdp != null -> "$primaryUdp (UDP / Fast-Path)"
                    server.softEtherUdpSupported -> "SoftEther RUDP Supported"
                    else -> "Not Supported"
                },
                isMonospace = true,
                valueColor = if (primaryUdp != null || server.softEtherUdpSupported) Color(0xFF10B981) else Color(0xFF8D8D8D)
            )

            if (server.candidatePorts.isNotEmpty()) {
                DetailItem(
                    label = strings.candidatePortsLabel,
                    value = server.candidatePorts.joinToString(", "),
                    isMonospace = true
                )
            }

            DetailItem(
                label = strings.filterProtocol,
                value = buildString {
                    append("SoftEther TCP")
                    if (server.softEtherUdpSupported) append(" + UDP (RUDP)")
                    if (server.l2tpSupported) append(" • L2TP")
                    if (server.sstpPort != null) append(" • SSTP (${server.sstpPort})")
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Network & Server Identification
            Text(
                text = "Server Addressing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            DetailItem(label = strings.ipAddress, value = server.ip, isMonospace = true)
            DetailItem(label = strings.hostname, value = server.hostName.ifEmpty { "N/A" }, isMonospace = true)

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Speed, Ping & Quality
            Text(
                text = "Performance & Traffic",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            DetailItem(label = strings.speed, value = String.format(Locale.US, "%.2f Mbps", server.speedMbps))
            DetailItem(
                label = strings.ping,
                value = if (server.pingMs > 0) "${server.pingMs} ms" else "N/A",
                valueColor = when {
                    server.pingMs <= 0 -> Color(0xFF8D8D8D)
                    server.pingMs < 100 -> Color(0xFF10B981)
                    server.pingMs < 250 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
            )
            DetailItem(label = strings.score, value = if (server.score > 0) "${server.score}" else "N/A")

            if (trafficFormatted.isNotEmpty()) {
                DetailItem(label = strings.totalTrafficLabel, value = trafficFormatted)
            }

            if (uptimeFormatted.isNotEmpty()) {
                DetailItem(label = strings.uptime, value = uptimeFormatted)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Community & Policies
            Text(
                text = "Community & Logs",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            DetailItem(label = strings.activeSessionsLabel, value = "${server.numSessions} sessions")
            if (server.totUsers > 0) {
                DetailItem(label = strings.totalUsersLabel, value = "${server.totUsers}")
            }
            if (server.logType.isNotEmpty()) {
                DetailItem(label = strings.logPolicyLabel, value = server.logType)
            }
            if (server.operator.isNotEmpty()) {
                DetailItem(label = strings.operator, value = server.operator)
            }
            if (server.comment.isNotEmpty()) {
                DetailItem(label = "Comment", value = server.comment)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onConnect()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = strings.connect,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                color = valueColor,
                fontSize = 13.sp
            )
        }
    }
}

private fun formatDetailUptime(uptime: Long): String {
    if (uptime <= 0) return ""
    val totalSeconds = if (uptime > 1_000_000_000L) uptime / 1000 else uptime
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val mins = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days} days, ${hours} hours"
        hours > 0 -> "${hours} hours, ${mins} minutes"
        mins > 0 -> "${mins} minutes"
        else -> "${totalSeconds} seconds"
    }
}

private fun formatDetailTraffic(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1.0 -> String.format(Locale.US, "%.2f TB", tb)
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
