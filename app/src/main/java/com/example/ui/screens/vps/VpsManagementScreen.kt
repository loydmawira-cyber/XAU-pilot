package com.example.ui.screens.vps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.VpsStatus
import com.example.ui.XauPilotViewModel
import com.example.ui.components.GoldButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun VpsManagementScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val vpsInstances by viewModel.vpsInstances.collectAsState()
    val vpsLogs by viewModel.vpsLogs.collectAsState()

    val vps = vpsInstances.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldLight)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "MANAGED VPS HOSTING",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "Isolated Cloud Terminals for 24/7 Execution",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (vps != null) {
            // Main VPS Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CharcoalSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = TradingBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(vps.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimaryDark)
                                    Text(vps.region, fontSize = 11.sp, color = TextSecondaryDark)
                                }
                            }
                            StatusBadge(
                                statusText = vps.status.name,
                                color = when (vps.status) {
                                    VpsStatus.RUNNING -> TradingGreen
                                    VpsStatus.REBOOTING -> TradingAmber
                                    else -> TradingRed
                                },
                                backgroundColor = when (vps.status) {
                                    VpsStatus.RUNNING -> TradingGreenBg
                                    VpsStatus.REBOOTING -> TradingAmberBg
                                    else -> TradingRedBg
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Specs Grid
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CharcoalBackground, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("CPU Cores", fontSize = 10.sp, color = TextSecondaryDark)
                                Text("${vps.cpuCores} vCPU (${vps.cpuUsagePercent}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            }
                            Column {
                                Text("RAM", fontSize = 10.sp, color = TextSecondaryDark)
                                Text("${vps.ramGb} GB (${vps.ramUsagePercent}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            }
                            Column {
                                Text("SSD Storage", fontSize = 10.sp, color = TextSecondaryDark)
                                Text("${vps.ssdGb} GB NVMe", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Uptime", fontSize = 10.sp, color = TextSecondaryDark)
                                Text("${vps.uptimeHours} hrs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TradingGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Terminal Engine: ${vps.mt5TerminalStatus}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldLight
                        )
                        Text(
                            text = "IP Address: ${vps.ipAddress} • OS: ${vps.os}",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restartVps(vps.id) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TradingAmber.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("restart_vps_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = TradingAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reboot VPS", color = TradingAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopVps(vps.id) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TradingRed.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_vps_button")
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = TradingRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Halt VPS", color = TradingRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Real-time Logs Viewer
            item {
                SectionHeader(
                    title = "TERMINAL & BRIDGE LOGS",
                    subtitle = "Live stdout stream from MetaTrader 5 process"
                )
            }

            items(vpsLogs) { log ->
                Surface(
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = when (log.level) {
                                "INFO" -> TradingGreenBg
                                "WARN" -> TradingAmberBg
                                else -> TradingBlueBg
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = log.level,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (log.level) {
                                    "INFO" -> TradingGreen
                                    "WARN" -> TradingAmber
                                    else -> TradingBlue
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = log.message,
                                fontSize = 11.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = log.timestampUtc,
                                fontSize = 9.sp,
                                color = TextTertiaryDark
                            )
                        }
                    }
                }
            }
        }
    }
}
