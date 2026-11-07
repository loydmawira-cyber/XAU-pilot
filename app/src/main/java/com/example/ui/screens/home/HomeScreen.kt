package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.*
import com.example.ui.ScreenDestination
import com.example.ui.XauPilotViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val installations by viewModel.installations.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val marketQuote by viewModel.marketQuote.collectAsState()
    val candlesticks by viewModel.candlesticks.collectAsState()
    val vpsInstances by viewModel.vpsInstances.collectAsState()

    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

    // Aggregate metrics
    val totalBalance = accounts.sumOf { it.balance }
    val totalEquity = accounts.sumOf { it.equity }
    val totalFloatingPl = positions.sumOf { it.floatingProfitLoss }
    val runningEaCount = installations.count { it.runtimeStatus == EaRuntimeStatus.RUNNING }
    val pausedEaCount = installations.count { it.runtimeStatus == EaRuntimeStatus.PAUSED }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Top Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CharcoalSurface)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "XAU Pilot Logo",
                            tint = GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "XAU PILOT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Automated MT5 Trading Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }

                // Quick Mode Badge
                StatusBadge(
                    statusText = "MT5 PENDING",
                    color = TradingAmber,
                    backgroundColor = TradingAmberBg,
                    modifier = Modifier.clickable { viewModel.navigateTo(ScreenDestination.Profile) }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Honest integration status: this release is for account setup and EA publishing tests.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, TradingAmber.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("MT5 INTEGRATION NOT CONNECTED", color = TradingAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("No live prices, broker accounts, positions or trading activity are being simulated. Connect MT5 in the later integration phase.", color = TextSecondaryDark, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Executive Hero Banner (Optional rich visual header)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Try to load local gold banner drawable if available
                    Image(
                        painter = painterResource(id = com.example.R.drawable.gold_pilot_banner),
                        contentDescription = "Gold Trading Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        CharcoalSurface.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PORTFOLIO BALANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Text(
                                text = "${accounts.size} ACCOUNTS CONNECTED",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "$${String.format("%,.2f", totalEquity)}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = "Balance: $${String.format("%,.2f", totalBalance)}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val plSign = if (totalFloatingPl >= 0) "+" else ""
                                Text(
                                    text = "$plSign$${String.format("%.2f", totalFloatingPl)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalFloatingPl >= 0) TradingGreen else TradingRed
                                )
                                Text(
                                    text = "Floating P/L",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Account Selector Pills
        item {
            Text(
                text = "ACTIVE MT5 ACCOUNT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts) { acc ->
                    val isSelected = (acc.id == currentAccount?.id)
                    Surface(
                        color = if (isSelected) GoldContainer else CharcoalSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else CharcoalCardBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { viewModel.selectAccount(acc.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (isSelected) GoldLight else TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = acc.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) GoldLight else TextPrimaryDark
                                )
                                Text(
                                    text = "${acc.brokerName} (${acc.accountNumberMasked})",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Operational Status KPI Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Running EAs",
                    value = "$runningEaCount Active",
                    subValue = "$pausedEaCount Paused",
                    icon = Icons.Default.PlayCircle,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(ScreenDestination.Accounts) }
                )
                MetricCard(
                    title = "Open Trades",
                    value = "${positions.size} Positions",
                    subValue = "Margin: 4223%",
                    icon = Icons.Default.ShowChart,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(ScreenDestination.Positions) }
                )
                MetricCard(
                    title = "Managed VPS",
                    value = "LD4 0.8ms",
                    subValue = "MT5 Active",
                    icon = Icons.Default.Dns,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(ScreenDestination.VpsManagement) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Remote Emergency & Pause Control Bar
        item {
            SectionHeader(
                title = "REMOTE CONTROL",
                subtitle = "Control EA entries and emergency position closure"
            )
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentAccount?.let { viewModel.pauseAccount(it.id) } },
                        border = BorderStroke(1.dp, TradingAmber.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TradingAmber),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("quick_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSE NEW TRADES", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { currentAccount?.let { viewModel.resumeAccount(it.id) } },
                        border = BorderStroke(1.dp, TradingGreen.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TradingGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("quick_resume_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESUME EAs", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { viewModel.openEmergencyDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = TradingRed),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("quick_emergency_stop_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP & CLOSE ALL", fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Live Candlestick Chart View
        item {
            SectionHeader(
                title = "MARKET PULSE",
                subtitle = "Gold (XAUUSD) Spot Terminal",
                actionText = "Full Chart",
                onActionClick = { viewModel.navigateTo(ScreenDestination.Positions) }
            )
            CandlestickChartView(
                candlesticks = candlesticks,
                marketQuote = marketQuote
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Installed EAs for this Account
        item {
            SectionHeader(
                title = "INSTALLED EXPERT ADVISORS",
                subtitle = "Assigned to ${currentAccount?.label ?: "Account"}",
                actionText = "Manage All",
                onActionClick = { viewModel.navigateTo(ScreenDestination.Accounts) }
            )
        }

        val currentInst = installations.filter { it.brokerAccountId == currentAccount?.id }
        if (currentInst.isEmpty()) {
            item {
                Surface(
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, tint = GoldLight, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No EAs assigned to this account", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                        Text("Browse our marketplace to purchase and install institutional EAs", color = TextSecondaryDark, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        GoldButton(
                            text = "Browse EA Marketplace",
                            onClick = { viewModel.navigateTo(ScreenDestination.Marketplace) }
                        )
                    }
                }
            }
        } else {
            items(currentInst) { inst ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            viewModel.selectInstallation(inst.id)
                            viewModel.navigateTo(ScreenDestination.EaSettings(inst.id))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = inst.eaProductName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryDark
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusBadge(
                                    statusText = inst.runtimeStatus.name,
                                    color = when (inst.runtimeStatus) {
                                        EaRuntimeStatus.RUNNING -> TradingGreen
                                        EaRuntimeStatus.PAUSED -> TradingAmber
                                        else -> TradingRed
                                    },
                                    backgroundColor = when (inst.runtimeStatus) {
                                        EaRuntimeStatus.RUNNING -> TradingGreenBg
                                        EaRuntimeStatus.PAUSED -> TradingAmberBg
                                        else -> TradingRedBg
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Magic: #${inst.magicNumber} • Symbol: ${inst.symbol} • TF: ${inst.timeframe} • Config: v${inst.currentConfigVersion}",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                val plSign = if (inst.totalProfitLossUsd >= 0) "+" else ""
                                Text(
                                    text = "$plSign$${String.format("%.2f", inst.totalProfitLossUsd)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (inst.totalProfitLossUsd >= 0) TradingGreen else TradingRed
                                )
                                Text(
                                    text = "${inst.openPositionsCount} trades",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Edit Inputs",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
