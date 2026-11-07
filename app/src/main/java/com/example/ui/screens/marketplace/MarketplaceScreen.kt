package com.example.ui.screens.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.*
import com.example.ui.ScreenDestination
import com.example.ui.XauPilotViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun MarketplaceScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val eaProducts by viewModel.eaProducts.collectAsState()
    val licenses by viewModel.licenses.collectAsState()
    val isGuestMode by viewModel.isGuestMode.collectAsState()
    val performanceByProduct by viewModel.eaPerformance.collectAsState()
    val performanceLoading by viewModel.eaPerformanceLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<StrategyCategory?>(null) }
    var selectedProductForDetail by remember { mutableStateOf<EaProduct?>(null) }
    var selectedProductForPurchase by remember { mutableStateOf<EaProduct?>(null) }

    val filteredProducts = eaProducts.filter { ea ->
        val matchesSearch = ea.name.contains(searchQuery, ignoreCase = true) ||
                ea.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = (selectedCategory == null || ea.strategyCategory == selectedCategory)
        matchesSearch && matchesCategory
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                text = "EA MARKETPLACE",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
            Text(
                text = "Institutional MetaTrader 5 Expert Advisors for Gold (XAUUSD)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search EAs, strategies, versions...", color = TextSecondaryDark, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldLight) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryDark)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = CharcoalCardBorder,
                    focusedContainerColor = CharcoalSurface,
                    unfocusedContainerColor = CharcoalSurface,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("marketplace_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Category Filter Pills
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isAll = (selectedCategory == null)
                    Surface(
                        color = if (isAll) GoldPrimary else CharcoalSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isAll) GoldPrimary else CharcoalCardBorder),
                        modifier = Modifier.clickable { selectedCategory = null }
                    ) {
                        Text(
                            text = "All Strategies",
                            fontSize = 12.sp,
                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAll) CharcoalBackground else TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                items(StrategyCategory.values()) { cat ->
                    val isSelected = (selectedCategory == cat)
                    Surface(
                        color = if (isSelected) GoldPrimary else CharcoalSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else CharcoalCardBorder),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CharcoalBackground else TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // EA Product Cards
        items(filteredProducts) { ea ->
            val hasActiveLicense = licenses.any { it.eaProductId == ea.id && it.status == LicenseStatus.ACTIVE }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { selectedProductForDetail = ea }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
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
                                    .background(GoldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = ea.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = "${ea.productCode} • v${ea.currentVersion}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        if (hasActiveLicense) {
                            StatusBadge(
                                statusText = "LICENSED",
                                color = TradingGreen,
                                backgroundColor = TradingGreenBg,
                                icon = Icons.Default.Verified
                            )
                        } else {
                            Text(
                                text = "From $${ea.monthlyPriceUsd.toInt()}/mo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = ea.description,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Badges row: Category, Instrument, Timeframe
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = ea.strategyCategory.label,
                                fontSize = 10.sp,
                                color = GoldLight,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "TF: ${ea.supportedTimeframes.joinToString("/")}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Min $${ea.minDepositUsd.toInt()}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { selectedProductForDetail = ea },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Details & Docs", fontSize = 12.sp, color = TextPrimaryDark)
                        }

                        if (!hasActiveLicense) {
                            Button(
                                onClick = { if (isGuestMode) viewModel.leaveGuestMode() else selectedProductForPurchase = ea },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("buy_license_${ea.id}")
                            ) {
                                Text(if (isGuestMode) "Sign in to license" else "Get License", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalBackground)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.navigateTo(ScreenDestination.Accounts) },
                                colors = ButtonDefaults.buttonColors(containerColor = TradingGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Assign to MT5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Product Details Dialog
    LaunchedEffect(selectedProductForDetail?.id) {
        selectedProductForDetail?.id?.let(viewModel::loadPublicEaPerformance)
    }
    if (selectedProductForDetail != null) {
        val ea = selectedProductForDetail!!
        AlertDialog(
            onDismissRequest = { selectedProductForDetail = null },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = GoldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(ea.name, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Product Code: ${ea.productCode} • Version ${ea.currentVersion}", fontSize = 12.sp, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(ea.description, fontSize = 13.sp, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Live public performance is fetched from the connected owner MT5 account.
                    val performance = performanceByProduct[ea.id]
                    Text("OWNER MT5 PERFORMANCE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldLight)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (ea.id in performanceLoading && performance == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GoldPrimary)
                    } else if (performance == null) {
                        Text("Loading actual MT5 results…", fontSize = 11.sp, color = TextSecondaryDark)
                    } else {
                        val dataStatus = performance["data_status"]?.toString().orEmpty()
                        val statusMessage = performance["message"]?.toString()
                        Surface(color = CharcoalSurfaceVariant, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CharcoalCardBorder), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(dataStatus.replace('_', ' '), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldLight)
                                if (!statusMessage.isNullOrBlank()) Text(statusMessage, fontSize = 11.sp, color = TextSecondaryDark)
                                val stats = listOf(
                                    "Net profit" to performance["net_profit"]?.let { "$it ${performance["currency"] ?: ""}".trim() },
                                    "Net profit / starting balance" to (performance["net_profit_pct_of_initial_balance"] as? Number)?.let { "${it.toDouble()}%" },
                                    "Gross profit" to performance["gross_profit"]?.let { "$it ${performance["currency"] ?: ""}".trim() },
                                    "Gross loss" to performance["gross_loss"]?.let { "$it ${performance["currency"] ?: ""}".trim() },
                                    "Profit factor" to performance["profit_factor"],
                                    "Win rate" to (performance["win_rate_pct"] as? Number)?.let { "${it.toDouble()}%" },
                                    "Max drawdown" to (performance["max_equity_drawdown_pct"] as? Number)?.let { "${it.toDouble()}%" },
                                    "Closed trades" to performance["closed_trades"],
                                    "Track record" to (performance["track_record_days"] as? Number)?.let { "${it.toInt()} days" }
                                ).filter { it.second != null }
                                stats.forEach { (label, value) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label, fontSize = 11.sp, color = TextSecondaryDark)
                                        Text(value.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                                    }
                                }
                                val curve = performance["equity_curve"] as? List<*>
                                if (!curve.isNullOrEmpty()) {
                                    Text("Recent equity snapshots", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = GoldLight, modifier = Modifier.padding(top = 8.dp))
                                    curve.takeLast(5).forEach { point ->
                                        val sample = point as? Map<*, *>
                                        val equity = (sample?.get("equity") as? Number)?.toDouble()
                                        val balance = (sample?.get("balance") as? Number)?.toDouble()
                                        if (equity != null && balance != null) Text("Balance ${"%.2f".format(balance)} · Equity ${"%.2f".format(equity)} ${performance["currency"] ?: ""}", fontSize = 10.sp, color = TextSecondaryDark)
                                    }
                                }
                                val source = performance["source_label"]?.toString()
                                if (!source.isNullOrBlank()) Text(source, fontSize = 10.sp, color = TextSecondaryDark, modifier = Modifier.padding(top = 8.dp))
                                val scopeWarning = performance["account_scope_warning"]?.toString()
                                if (!scopeWarning.isNullOrBlank()) Text(scopeWarning, fontSize = 10.sp, color = TradingAmber, modifier = Modifier.padding(top = 6.dp))
                                Text("EA-reported MT5 results; not an independently audited broker statement. Past performance does not predict future results.", fontSize = 10.sp, color = TradingAmber, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Release Notes", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldLight)
                    Text(ea.releaseNotes, fontSize = 12.sp, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = TradingAmberBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TradingAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Risk Disclosure", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TradingAmber)
                            Text(ea.riskDisclosures, fontSize = 11.sp, color = TextSecondaryDark)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val eaToBuy = ea
                        selectedProductForDetail = null
                        if (isGuestMode) viewModel.leaveGuestMode() else selectedProductForPurchase = eaToBuy
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(if (isGuestMode) "Sign in to continue" else "Pricing Plans", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedProductForDetail = null }) {
                    Text("Close", color = TextSecondaryDark)
                }
            }
        )
    }

    // Purchase License Dialog
    if (selectedProductForPurchase != null) {
        val ea = selectedProductForPurchase!!
        var chosenType by remember { mutableStateOf(LicenseType.MONTHLY) }

        AlertDialog(
            onDismissRequest = { selectedProductForPurchase = null },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Select License: ${ea.name}", fontWeight = FontWeight.Bold, color = GoldLight)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Licenses are EA-specific and verified securely through MT5 bridge.", fontSize = 12.sp, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.height(14.dp))

                    listOf(
                        Triple(LicenseType.MONTHLY, "$${ea.monthlyPriceUsd.toInt()} / month", "Billed monthly • Up to 3 accounts"),
                        Triple(LicenseType.ANNUAL, "$${ea.annualPriceUsd.toInt()} / year", "Save 25% • Up to 5 accounts"),
                        Triple(LicenseType.LIFETIME, "$${ea.lifetimePriceUsd.toInt()} one-time", "Permanent license • Up to 10 accounts")
                    ).forEach { (type, price, desc) ->
                        val isSelected = (chosenType == type)
                        Surface(
                            color = if (isSelected) GoldContainer else CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else CharcoalCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { chosenType = type }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { chosenType = type },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(type.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) GoldLight else TextPrimaryDark)
                                        Text(price, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldPrimary)
                                    }
                                    Text(desc, fontSize = 11.sp, color = TextSecondaryDark)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purchaseLicense(ea.id, chosenType)
                        selectedProductForPurchase = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Confirm & Activate", fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedProductForPurchase = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }
}
