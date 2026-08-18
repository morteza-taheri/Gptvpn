package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.localization.Strings
import com.example.ui.viewmodel.VpnViewModel
import com.softether.model.DeveloperSettings
import com.softether.model.FlushStrategy
import com.softether.model.PacketLogLevel
import com.softether.model.TunnelDiagnostics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VpnViewModel,
    strings: Strings,
    onNavigateToSplitTunnel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val devSettings by viewModel.developerSettings.collectAsState()
    val diagnostics by viewModel.tunnelDiagnostics.collectAsState()

    var protocolMode by remember { mutableStateOf(viewModel.prefs.getProtocol()) }
    var authMethod by remember { mutableStateOf(viewModel.prefs.getAuthMethod()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settings,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Split Tunneling Shortcut Card
            Card(
                onClick = onNavigateToSplitTunnel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.splitTunnelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.splitTunnelSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Custom DNS Settings Section
            var dnsMode by remember { mutableStateOf(viewModel.prefs.getDnsMode()) }
            var selectedPresetId by remember { mutableStateOf(viewModel.prefs.getDnsPresetId()) }
            var customPrimary by remember { mutableStateOf(viewModel.prefs.getCustomDnsPrimary()) }
            var customSecondary by remember { mutableStateOf(viewModel.prefs.getCustomDnsSecondary()) }

            SectionCard(
                title = strings.dnsTitle,
                subtitle = strings.dnsSubtitle,
                icon = Icons.Default.Dns
            ) {
                // Mode selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dnsMode == "PRESET",
                        onClick = {
                            dnsMode = "PRESET"
                            viewModel.prefs.setDnsMode("PRESET")
                        },
                        label = { Text(strings.dnsModePreset, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = dnsMode == "CUSTOM",
                        onClick = {
                            dnsMode = "CUSTOM"
                            viewModel.prefs.setDnsMode("CUSTOM")
                        },
                        label = { Text(strings.dnsModeCustom, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (dnsMode == "PRESET") {
                    viewModel.prefs.defaultDnsPresets.forEach { preset ->
                        val isSelected = selectedPresetId == preset.id
                        Card(
                            onClick = {
                                selectedPresetId = preset.id
                                viewModel.prefs.setDnsPresetId(preset.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPresetId = preset.id
                                        viewModel.prefs.setDnsPresetId(preset.id)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (language == "fa") preset.nameFa else preset.nameEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${preset.primary} • ${preset.secondary}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (language == "fa") preset.descriptionFa else preset.descriptionEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = customPrimary,
                            onValueChange = {
                                customPrimary = it
                                viewModel.prefs.setCustomDnsPrimary(it)
                            },
                            label = { Text(strings.dnsPrimary) },
                            placeholder = { Text(strings.dnsCustomPlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = customSecondary,
                            onValueChange = {
                                customSecondary = it
                                viewModel.prefs.setCustomDnsSecondary(it)
                            },
                            label = { Text(strings.dnsSecondary) },
                            placeholder = { Text("185.51.200.2") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Protocol Settings Section
            SectionCard(
                title = strings.protocolTitle,
                icon = Icons.Default.Tune
            ) {
                listOf(
                    "AUTO" to "Auto Select (TCP / UDP V2 / UDP V1)",
                    "TCP" to "SoftEther Standard TCP (SSL 443/995)",
                    "UDP_V2" to "SoftEther UDP Acceleration (UDP V2)",
                    "UDP_V1" to "SoftEther UDP Legacy (UDP V1)"
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = protocolMode == key,
                            onClick = {
                                protocolMode = key
                                viewModel.prefs.setProtocol(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Connection Target Address Mode (Hostname vs IP)
            var connectionHostMode by remember { mutableStateOf(viewModel.prefs.getConnectionHostMode()) }
            SectionCard(
                title = strings.hostModeTitle,
                subtitle = strings.hostModeSubtitle,
                icon = Icons.Default.Dns
            ) {
                listOf(
                    "HOSTNAME" to strings.hostModeHostname,
                    "IP" to strings.hostModeIp
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = connectionHostMode == key,
                            onClick = {
                                connectionHostMode = key
                                viewModel.prefs.setConnectionHostMode(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Authentication Method Section
            SectionCard(
                title = strings.authMethodTitle,
                icon = Icons.Default.Security
            ) {
                listOf(
                    "AUTO" to "Auto (SoftEther Standard)",
                    "PASSWORD" to "Standard Hash Authentication (vpn/vpn)",
                    "PLAIN_PASSWORD" to "Plain Password Mode",
                    "ANONYMOUS" to "Anonymous Login"
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = authMethod == key,
                            onClick = {
                                authMethod = key
                                viewModel.prefs.setAuthMethod(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Mirror Sources Section
            SectionCard(
                title = strings.sourcesTitle,
                subtitle = strings.sourcesSubtitle,
                icon = Icons.Default.Public
            ) {
                viewModel.prefs.defaultSources.forEach { source ->
                    var isEnabled by remember { mutableStateOf(viewModel.prefs.isSourceEnabled(source.id)) }
                    val nameStr = when (source.nameKey) {
                        "source_official_api" -> strings.sourceOfficialApi
                        "source_official_web" -> strings.sourceOfficialWeb
                        "source_mirror_hr_1" -> strings.sourceMirrorCroatia1
                        "source_mirror_hr_2" -> strings.sourceMirrorCroatia2
                        "source_mirror_hr_3" -> strings.sourceMirrorCroatia3
                        "source_mirror_am_1" -> strings.sourceMirrorArmenia
                        "source_mirror_hr_4" -> strings.sourceMirrorCroatia4
                        "source_mirror_hr_5" -> strings.sourceMirrorCroatia5
                        else -> source.id
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nameStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                viewModel.setSourceEnabled(source.id, checked)
                            }
                        )
                    }
                }
            }

            // Language & Theme Section
            SectionCard(
                title = strings.languageTitle,
                icon = Icons.Default.Language
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == "fa") strings.persian else strings.english,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = language == "fa",
                        onCheckedChange = { viewModel.toggleLanguage() }
                    )
                }
            }

            SectionCard(
                title = strings.themeTitle,
                icon = Icons.Default.Palette
            ) {
                listOf(
                    "dark" to strings.darkTheme,
                    "light" to strings.lightTheme,
                    "system" to strings.systemTheme
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == key,
                            onClick = { viewModel.setTheme(key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ==========================================
            // DEVELOPER / PERFORMANCE TUNING SECTION
            // ==========================================
            DeveloperPerformanceSection(
                viewModel = viewModel,
                strings = strings,
                devSettings = devSettings,
                diagnostics = diagnostics
            )

            // Clear Database Section
            val context = LocalContext.current
            SectionCard(
                title = strings.clearDatabase,
                subtitle = strings.clearDatabaseSubtitle,
                icon = Icons.Default.DeleteForever
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearLocalDatabase()
                        Toast.makeText(context, strings.databaseCleared, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = strings.clearDatabase, fontWeight = FontWeight.Bold)
                }
            }

            // About Section
            SectionCard(
                title = strings.aboutTitle,
                icon = Icons.Default.Info
            ) {
                Text(
                    text = strings.aboutDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Version 1.0.0 • SoftEther VPN Client Core",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
fun DeveloperPerformanceSection(
    viewModel: VpnViewModel,
    strings: Strings,
    devSettings: DeveloperSettings,
    diagnostics: TunnelDiagnostics
) {
    SectionCard(
        title = strings.developerModeTitle,
        subtitle = strings.developerModeSubtitle,
        icon = Icons.Default.Tune
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Master Developer Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.developerModeToggle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (devSettings.isDeveloperModeEnabled) "Active (Tuning enabled)" else "Disabled (Default safe values)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (devSettings.isDeveloperModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = devSettings.isDeveloperModeEnabled,
                    onCheckedChange = { viewModel.setDeveloperMode(it) }
                )
            }

            if (devSettings.isDeveloperModeEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 1. MTU Configuration
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.mtuLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${devSettings.mtu} bytes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = devSettings.mtu.toFloat(),
                        onValueChange = { viewModel.setDeveloperMtu(it.toInt()) },
                        valueRange = 1280f..1500f,
                        steps = 21
                    )
                    Text(
                        text = "Standard: 1500 (Default) • Min: 1280 (IPv6 base) • Lower values reduce fragmentation on lossy mobile networks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. Buffer Size
                Column {
                    Text(
                        text = strings.bufferSizeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            16384 to "16 KB",
                            32768 to "32 KB",
                            65535 to "64 KB (Def)",
                            131072 to "128 KB",
                            262144 to "256 KB"
                        ).forEach { (size, label) ->
                            FilterChip(
                                selected = devSettings.bufferSize == size,
                                onClick = { viewModel.setDeveloperBufferSize(size) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. Max Connections
                Column {
                    Text(
                        text = strings.maxConnectionsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1 to "1 (Default / Safe)",
                            2 to "2",
                            4 to "4",
                            8 to "8"
                        ).forEach { (conn, label) ->
                            FilterChip(
                                selected = devSettings.maxConnections == conn,
                                onClick = { viewModel.setDeveloperMaxConnections(conn) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text(
                        text = "1 connection is recommended to prevent TUN routing feedback loops on Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 4. Flush Strategy
                Column {
                    Text(
                        text = strings.flushStrategyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlushStrategy.values().forEach { strategy ->
                            FilterChip(
                                selected = devSettings.flushStrategy == strategy,
                                onClick = { viewModel.setDeveloperFlushStrategy(strategy) },
                                label = {
                                    Text(
                                        when (strategy) {
                                            FlushStrategy.IMMEDIATE -> "IMMEDIATE (Def)"
                                            FlushStrategy.AUTO -> "AUTO"
                                            FlushStrategy.BATCH -> "BATCH"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 5. Packet Log Level
                Column {
                    Text(
                        text = strings.packetLogLevelLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PacketLogLevel.values().forEach { level ->
                            FilterChip(
                                selected = devSettings.packetLogLevel == level,
                                onClick = { viewModel.setDeveloperPacketLogging(level) },
                                label = {
                                    Text(
                                        when (level) {
                                            PacketLogLevel.OFF -> "OFF (Def)"
                                            PacketLogLevel.BASIC -> "BASIC"
                                            PacketLogLevel.VERBOSE -> "VERBOSE"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 6. Diagnostics Refresh Interval
                Column {
                    Text(
                        text = strings.statsIntervalLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            500L to "500ms",
                            1000L to "1000ms (Def)",
                            2000L to "2000ms",
                            5000L to "5000ms"
                        ).forEach { (interval, label) ->
                            FilterChip(
                                selected = devSettings.statsIntervalMs == interval,
                                onClick = { viewModel.setDeveloperStatsInterval(interval) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 7. Real-time Diagnostics Pipeline Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.performanceStatsToggle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Live throughput, latency, and socket monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = devSettings.isPerformanceStatsEnabled,
                        onCheckedChange = { viewModel.setPerformanceStatsEnabled(it) }
                    )
                }

                // 8. Live Diagnostics Display
                if (devSettings.isPerformanceStatsEnabled) {
                    LiveDiagnosticsCard(strings = strings, diagnostics = diagnostics)
                }

                // 9. Reset Button
                OutlinedButton(
                    onClick = { viewModel.resetDeveloperSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = strings.resetDeveloperDefaults, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LiveDiagnosticsCard(
    strings: Strings,
    diagnostics: TunnelDiagnostics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = strings.liveDiagnosticsTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // General Connection Metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DiagnosticRow("Target Server", "${diagnostics.serverHost}:${diagnostics.serverPort} (${diagnostics.virtualHub})")
                DiagnosticRow("Assigned IP / GW", "${diagnostics.assignedIp} / ${diagnostics.gatewayIp}")
                DiagnosticRow("DNS Servers", diagnostics.dnsServers.joinToString(", ").ifEmpty { "Default" })
                DiagnosticRow("Active MTU", "${diagnostics.currentMtu} bytes")
                DiagnosticRow("RUDP Mode", if (diagnostics.isRudpEnabled) "Enabled (v${diagnostics.rudpVersion})" else "Disabled (TCP Only)")
                DiagnosticRow("Active Connections", "${diagnostics.numConnections} (Max: ${diagnostics.serverMaxConnections})")
                DiagnosticRow("Throughput TX / RX", "${"%.1f".format(diagnostics.txKbps)} kbps / ${"%.1f".format(diagnostics.rxKbps)} kbps")
                DiagnosticRow("Packet Rate TX / RX", "${"%.1f".format(diagnostics.txPacketsPerSec)} pps / ${"%.1f".format(diagnostics.rxPacketsPerSec)} pps")
                DiagnosticRow("Native Errors TX / RX", "${diagnostics.nativeSendFailures} / ${diagnostics.nativeReceiveErrors}")
            }

            // Sockets Protection List
            if (diagnostics.sockets.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text(
                    text = "Protected Sockets (${diagnostics.sockets.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    diagnostics.sockets.forEach { sock ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FD ${sock.fd} • ${sock.socketType}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (sock.isProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (sock.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.height(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sock.isProtected) "Protected" else "Unprotected",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (sock.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Pipeline Stages Breakdown
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text(
                text = "Pipeline Stages Breakdown",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    diagnostics.stageTunRead,
                    diagnostics.stageEncapsulation,
                    diagnostics.stageJniSend,
                    diagnostics.stageSoftEtherRx,
                    diagnostics.stageJniReceive,
                    diagnostics.stageDecapsulation,
                    diagnostics.stageTunWrite
                ).forEach { stage ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stage.stageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${stage.packets} pkts • ${stage.bytes / 1024} KB${if (stage.errors > 0) " • ${stage.errors} err" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (stage.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}
