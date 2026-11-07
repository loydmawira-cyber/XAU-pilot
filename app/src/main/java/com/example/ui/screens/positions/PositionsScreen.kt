package com.example.ui.screens.positions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.core.model.*
import com.example.ui.XauPilotViewModel
import com.example.ui.components.CandlestickChartView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun PositionsScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val positions by viewModel.positions.collectAsState()
    val pendingOrders by viewModel.pendingOrders.collectAsState()
    val tradeHistory by viewModel.tradeHistory.collectAsState()
    val marketQuote by viewModel.marketQuote.collectAsState()
    val candlesticks by viewModel.candlesticks.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Open, 1: Pending, 2: History
    var showNewTradeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Title Bar with New Trade Action
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "POSITIONS & TERMINAL",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "Live Execution Monitoring & MT5 Order Book",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }

                Button(
                    onClick = { showNewTradeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_new_trade_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CharcoalBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Trade", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live Candlestick Chart View
        item {
            CandlestickChartView(
                candlesticks = candlesticks,
                marketQuote = marketQuote
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tab Selector: Open Positions, Pending Orders, Closed History
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf(
                    "Open (${positions.size})",
                    "Pending (${pendingOrders.size})",
                    "History (${tradeHistory.size})"
                ).forEachIndexed { index, title ->
                    val isSelected = (activeTab == index)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                color = if (isSelected) GoldPrimary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTab = index }
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CharcoalBackground else TextSecondaryDark
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tab Content
        when (activeTab) {
            0 -> {
                // Open Positions
                if (positions.isEmpty()) {
                    item {
                        Surface(
                            color = CharcoalSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No open positions", fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                                Text("Active EAs will automatically trigger entries when strategy conditions align.", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }
                } else {
                    items(positions) { pos ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (pos.direction == TradeDirection.BUY) TradingGreenBg else TradingRedBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${pos.direction.name} ${pos.lotSize}",
                                                color = if (pos.direction == TradeDirection.BUY) TradingGreen else TradingRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = pos.symbol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimaryDark
                                        )
                                    }

                                    val plSign = if (pos.floatingProfitLoss >= 0) "+" else ""
                                    Text(
                                        text = "$plSign$${String.format("%.2f", pos.floatingProfitLoss)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (pos.floatingProfitLoss >= 0) TradingGreen else TradingRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Open: ${String.format("%.2f", pos.openPrice)} → Now: ${String.format("%.2f", pos.currentPrice)}",
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                    Text(
                                        text = "SL: ${pos.stopLoss?.let { String.format("%.2f", it) } ?: "None"} • TP: ${pos.takeProfit?.let { String.format("%.2f", it) } ?: "None"}",
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "EA: ${pos.eaName} (Magic #${pos.magicNumber})",
                                        fontSize = 10.sp,
                                        color = GoldLight
                                    )
                                    Text(
                                        text = "Ticket: #${pos.ticket} • ${pos.openTimeUtc.takeLast(8)}",
                                        fontSize = 10.sp,
                                        color = TextTertiaryDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Swap: $${pos.swap} • Comm: $${pos.commission}",
                                        fontSize = 10.sp,
                                        color = TextTertiaryDark
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.closePosition(pos.ticket) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, TradingRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag("close_position_${pos.ticket}")
                                    ) {
                                        Text("Close Position", fontSize = 11.sp, color = TradingRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Pending Orders
                if (pendingOrders.isEmpty()) {
                    item {
                        Surface(
                            color = CharcoalSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No pending orders", fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                            }
                        }
                    }
                } else {
                    items(pendingOrders) { order ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = CharcoalSurfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = order.orderType.name.replace("_", " "),
                                                color = GoldLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${order.symbol} (${order.lotSize} lots)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimaryDark
                                        )
                                    }
                                    Text(
                                        text = "Target: ${String.format("%.2f", order.targetPrice)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimaryDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Current Market: ${String.format("%.2f", order.currentMarketPrice)} • Placed: ${order.placedTimeUtc}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // Closed Trade History
                items(tradeHistory) { trade ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CharcoalCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${trade.direction.name} ${trade.lotSize} ${trade.symbol}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimaryDark
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = trade.eaName,
                                        fontSize = 10.sp,
                                        color = GoldLight
                                    )
                                }

                                val plSign = if (trade.netProfitLoss >= 0) "+" else ""
                                Text(
                                    text = "$plSign$${String.format("%.2f", trade.netProfitLoss)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (trade.netProfitLoss >= 0) TradingGreen else TradingRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${String.format("%.2f", trade.openPrice)} → ${String.format("%.2f", trade.closePrice)}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                                Text(
                                    text = "Closed: ${trade.closeTimeUtc.takeLast(8)}",
                                    fontSize = 10.sp,
                                    color = TextTertiaryDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // New Trade Execution Dialog
    if (showNewTradeDialog) {
        val selectedAccountId by viewModel.selectedAccountId.collectAsState()
        val accounts by viewModel.accounts.collectAsState()
        val installations by viewModel.installations.collectAsState()

        val activeAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
        val accountInst = installations.filter { it.brokerAccountId == activeAccount?.id }

        var direction by remember { mutableStateOf(TradeDirection.BUY) }
        var lotSizeStr by remember { mutableStateOf("0.10") }
        var slStr by remember { mutableStateOf("") }
        var tpStr by remember { mutableStateOf("") }
        var selectedInst by remember { mutableStateOf(accountInst.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { showNewTradeDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute Gold (XAUUSD) Order", fontWeight = FontWeight.Bold, color = TextPrimaryDark, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Routes order through MT5 terminal bridge on ${activeAccount?.label ?: "Account"}.",
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Direction Selector (BUY / SELL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { direction = TradeDirection.BUY },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (direction == TradeDirection.BUY) TradingGreen else CharcoalSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_buy_direction")
                        ) {
                            Text(
                                "BUY @ ${String.format("%.2f", marketQuote.ask)}",
                                fontWeight = FontWeight.Bold,
                                color = if (direction == TradeDirection.BUY) CharcoalBackground else TextPrimaryDark,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { direction = TradeDirection.SELL },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (direction == TradeDirection.SELL) TradingRed else CharcoalSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_sell_direction")
                        ) {
                            Text(
                                "SELL @ ${String.format("%.2f", marketQuote.bid)}",
                                fontWeight = FontWeight.Bold,
                                color = if (direction == TradeDirection.SELL) CharcoalBackground else TextPrimaryDark,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume quick pills
                    Text("Volume (Lots)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldLight)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("0.01", "0.05", "0.10", "0.20").forEach { lot ->
                            val isSel = (lotSizeStr == lot)
                            Surface(
                                color = if (isSel) GoldPrimary else CharcoalSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else CharcoalCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { lotSizeStr = lot }
                            ) {
                                Text(
                                    text = lot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) CharcoalBackground else TextPrimaryDark,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lotSizeStr,
                        onValueChange = { lotSizeStr = it },
                        label = { Text("Custom Volume (lots)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = slStr,
                            onValueChange = { slStr = it },
                            label = { Text("Stop Loss (optional)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tpStr,
                            onValueChange = { tpStr = it },
                            label = { Text("Take Profit (optional)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lot = lotSizeStr.toDoubleOrNull() ?: 0.05
                        val sl = slStr.toDoubleOrNull()
                        val tp = tpStr.toDoubleOrNull()
                        val accId = activeAccount?.id ?: ""
                        val inst = selectedInst ?: accountInst.firstOrNull()

                        viewModel.placeOrder(
                            accountId = accId,
                            eaInstallationId = inst?.id ?: "",
                            eaName = inst?.eaProductName ?: "",
                            direction = direction,
                            lotSize = lot,
                            stopLoss = sl,
                            takeProfit = tp,
                            magicNumber = inst?.magicNumber ?: 0L
                        )
                        showNewTradeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (direction == TradeDirection.BUY) TradingGreen else TradingRed
                    ),
                    modifier = Modifier.testTag("submit_order_button")
                ) {
                    Text("EXECUTE NOW", fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewTradeDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }
}
