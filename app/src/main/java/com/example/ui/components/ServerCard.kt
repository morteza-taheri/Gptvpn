package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnServer
import com.example.ui.localization.Strings
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerCard(
    server: VpnServer,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
    strings: Strings? = null
) {
    val borderColor = when {
        isSelected && isConnected -> Color(0xFF10B981)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    }

    val containerColor = when {
        isSelected && isConnected -> Color(0xFF10B981).copy(alpha = 0.12f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }

    val primaryTcp = server.softEtherTcpPort ?: server.openVpnTcpPort ?: server.effectivePort
    val primaryUdp = server.softEtherUdpPort ?: server.openVpnUdpPort
    val uptimeFormatted = formatUptime(server.uptime)
    val trafficFormatted = formatTraffic(server.totTraffic)
    val scoreFormatted = formatScore(server.score)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header Row: Flag + Country/Host + Ping/Speed + Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Country Flag
                CountryFlag(countryCode = server.countryShort, size = 38.dp)

                Spacer(modifier = Modifier.width(10.dp))

                // Server Info (Country Name, Source / Score, Hostname & IP)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (server.countryLong.isNotEmpty()) server.countryLong else server.countryShort,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (scoreFormatted.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = scoreFormatted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${server.hostName.ifEmpty { server.ip }} • ${server.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Ping & Speed Column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    val pingColor = when {
                        server.pingMs <= 0 -> Color(0xFF8D8D8D)
                        server.pingMs < 100 -> Color(0xFF10B981)
                        server.pingMs < 250 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }

                    Text(
                        text = if (server.pingMs > 0) "${server.pingMs} ms" else "N/A",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = pingColor
                    )

                    val speedMbps = server.speedBps / 1_000_000.0
                    val speedText = if (speedMbps >= 1.0) {
                        String.format(Locale.US, "%.1f Mbps", speedMbps)
                    } else if (server.speedBps > 0) {
                        String.format(Locale.US, "%.0f Kbps", server.speedBps / 1000.0)
                    } else {
                        "N/A"
                    }

                    Text(
                        text = speedText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Info Action Button
                IconButton(
                    onClick = onShowDetails,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.7.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Detailed Ports & Protocols Row (TCP: port, UDP: port, etc.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: TCP and UDP Port badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // TCP Port Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "TCP: $primaryTcp",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // UDP Port Badge
                    val udpColor = if (primaryUdp != null || server.softEtherUdpSupported) Color(0xFF10B981) else Color(0xFF8D8D8D)
                    val udpBg = if (primaryUdp != null || server.softEtherUdpSupported) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF8D8D8D).copy(alpha = 0.1f)
                    val udpBorder = if (primaryUdp != null || server.softEtherUdpSupported) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFF8D8D8D).copy(alpha = 0.25f)
                    val udpText = when {
                        primaryUdp != null -> "UDP: $primaryUdp"
                        server.softEtherUdpSupported -> "UDP: RUDP"
                        else -> "UDP: Off"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(udpBg)
                            .border(0.7.dp, udpBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = udpText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = udpColor
                        )
                    }

                    // Active Sessions Badge
                    if (server.numSessions > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${server.numSessions}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Right: Uptime or Traffic metric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uptimeFormatted.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = uptimeFormatted,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (trafficFormatted.isNotEmpty()) {
                        Text(
                            text = "• $trafficFormatted",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Optional extra line if candidate alternative ports or operator/log policy are present
            val altPorts = server.candidatePorts.filter { it != primaryTcp && (primaryUdp == null || it != primaryUdp) }
            if (altPorts.isNotEmpty() || server.logType.isNotEmpty() || server.operator.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (altPorts.isNotEmpty()) {
                        Text(
                            text = "Alt Ports: ${altPorts.take(4).joinToString(", ")}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (server.operator.isNotEmpty()) {
                        Text(
                            text = "Op: ${server.operator}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (server.logType.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = server.logType,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatUptime(uptime: Long): String {
    if (uptime <= 0) return ""
    val totalSeconds = if (uptime > 1_000_000_000L) uptime / 1000 else uptime
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val mins = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${totalSeconds}s"
    }
}

private fun formatTraffic(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1.0 -> String.format(Locale.US, "%.1f TB", tb)
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

private fun formatScore(score: Long): String {
    if (score <= 0) return ""
    return when {
        score >= 1_000_000 -> String.format(Locale.US, "%.1fM", score / 1_000_000.0)
        score >= 1_000 -> String.format(Locale.US, "%.0fK", score / 1_000.0)
        else -> score.toString()
    }
}
