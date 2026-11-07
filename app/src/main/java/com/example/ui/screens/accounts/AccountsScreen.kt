package com.example.ui.screens.accounts

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
fun AccountsScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    val installations by viewModel.installations.collectAsState()
    val eaProducts by viewModel.eaProducts.collectAsState()
    val licenses by viewModel.licenses.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAssignEaDialog by remember { mutableStateOf(false) }
    var selectedAccountForAssignment by remember { mutableStateOf<BrokerAccount?>(null) }
    var showInstallationGuideDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Title Bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "MT5 ACCOUNTS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "MT5 account linking is planned for the next integration phase",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }

                Button(
                    onClick = { showAddAccountDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("add_mt5_account_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CharcoalBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add MT5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Quick Guides Row
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showInstallationGuideDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Customer-Hosted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("Setup Guide & WebRequest", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                    }
                }

                Surface(
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.navigateTo(ScreenDestination.VpsManagement) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = TradingBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Managed VPS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("London LD4 Active", fontSize = 10.sp, color = TextSecondaryDark)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Accounts List
        items(accounts) { acc ->
            val accInstallations = installations.filter { it.brokerAccountId == acc.id }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row: Label & Status Badge
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
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = acc.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimaryDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (acc.environment == AccountEnvironment.DEMO) TradingAmberBg else TradingGreenBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = acc.environment.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (acc.environment == AccountEnvironment.DEMO) TradingAmber else TradingGreen,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${acc.brokerName} • ${acc.serverName} • Acc #${acc.accountNumberMasked}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        StatusBadge(
                            statusText = acc.connectionStatus.name,
                            color = when (acc.connectionStatus) {
                                ConnectionStatus.CONNECTED -> TradingGreen
                                ConnectionStatus.CONNECTING -> TradingAmber
                                else -> TradingRed
                            },
                            backgroundColor = when (acc.connectionStatus) {
                                ConnectionStatus.CONNECTED -> TradingGreenBg
                                ConnectionStatus.CONNECTING -> TradingAmberBg
                                else -> TradingRedBg
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Financial metrics row
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CharcoalBackground, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Balance", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("$${String.format("%,.2f", acc.balance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }
                        Column {
                            Text("Equity", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("$${String.format("%,.2f", acc.equity)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                        }
                        Column {
                            Text("Free Margin", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("$${String.format("%,.2f", acc.freeMargin)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Margin Level", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("${String.format("%.1f", acc.marginLevelPercent)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TradingGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Assigned EAs section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Assigned Expert Advisors (${accInstallations.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                        Text(
                            text = "+ Assign EA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldPrimary,
                            modifier = Modifier
                                .clickable {
                                    selectedAccountForAssignment = acc
                                    showAssignEaDialog = true
                                }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (accInstallations.isEmpty()) {
                        Text(
                            text = "No EAs running on this account. Tap '+ Assign EA' to deploy.",
                            fontSize = 11.sp,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        accInstallations.forEach { inst ->
                            Surface(
                                color = CharcoalSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CharcoalCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        viewModel.selectInstallation(inst.id)
                                        viewModel.navigateTo(ScreenDestination.EaSettings(inst.id))
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = inst.eaProductName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = TextPrimaryDark
                                        )
                                        Text(
                                            text = "Magic #${inst.magicNumber} • ${inst.symbol} ${inst.timeframe} • Config v${inst.currentConfigVersion}",
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusBadge(
                                            statusText = inst.runtimeStatus.name,
                                            color = if (inst.runtimeStatus == EaRuntimeStatus.RUNNING) TradingGreen else TradingAmber,
                                            backgroundColor = if (inst.runtimeStatus == EaRuntimeStatus.RUNNING) TradingGreenBg else TradingAmberBg
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Tune, contentDescription = "Edit Inputs", tint = GoldLight, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Account quick actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(ScreenDestination.RiskManagement) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Risk Profile", fontSize = 11.sp, color = TextPrimaryDark)
                        }

                        OutlinedButton(
                            onClick = { viewModel.removeAccount(acc.id) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TradingRed.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disconnect", fontSize = 11.sp, color = TradingRed)
                        }
                    }
                }
            }
        }
    }

    // MT5 linking is intentionally unavailable in the EA-upload test release.
    if (showAddAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            containerColor = CharcoalSurface,
            title = { Text("MT5 connection coming later", color = GoldLight, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This release is for real user registration and EA publishing tests. It does not collect broker credentials or create placeholder accounts. MT5 account linking will be added in the next integration phase.",
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(onClick = { showAddAccountDialog = false }) {
                    Text("Understood", color = CharcoalBackground)
                }
            }
        )
    }

    // Dialog: EA Assignment Wizard
    if (showAssignEaDialog && selectedAccountForAssignment != null) {
        val targetAccount = selectedAccountForAssignment!!
        var chosenEa by remember { mutableStateOf(eaProducts.firstOrNull()) }
        var chosenTf by remember { mutableStateOf("M5") }
        var magicNumberText by remember { mutableStateOf((chosenEa?.defaultMagicNumber ?: 108821).toString()) }

        // Check if magic number already used on this account!
        val existingMagics = installations
            .filter { it.brokerAccountId == targetAccount.id }
            .map { it.magicNumber }
        val magicConflict = magicNumberText.toLongOrNull()?.let { it in existingMagics } ?: false

        AlertDialog(
            onDismissRequest = { showAssignEaDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Assign EA to ${targetAccount.label}", fontWeight = FontWeight.Bold, color = GoldLight)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select an Expert Advisor to deploy on this MT5 terminal.", fontSize = 12.sp, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Select Expert Advisor", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimaryDark)
                    eaProducts.forEach { prod ->
                        val isSelected = (chosenEa?.id == prod.id)
                        Surface(
                            color = if (isSelected) GoldContainer else CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else CharcoalCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    chosenEa = prod
                                    magicNumberText = prod.defaultMagicNumber.toString()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                RadioButton(selected = isSelected, onClick = { chosenEa = prod }, colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) GoldLight else TextPrimaryDark)
                                    Text(prod.strategyCategory.label, fontSize = 11.sp, color = TextSecondaryDark)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = magicNumberText,
                        onValueChange = { magicNumberText = it },
                        label = { Text("Magic Number (Must be unique per EA)") },
                        isError = magicConflict,
                        supportingText = {
                            if (magicConflict) Text("Conflict: This magic number is already active on this account!", color = TradingRed)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chosenTf,
                        onValueChange = { chosenTf = it },
                        label = { Text("Chart Timeframe (e.g. M1, M5, M15, H1)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ea = chosenEa ?: return@Button
                        val magic = magicNumberText.toLongOrNull() ?: ea.defaultMagicNumber
                        viewModel.assignEa(ea, targetAccount, magic, chosenTf)
                        showAssignEaDialog = false
                    },
                    enabled = !magicConflict && chosenEa != null,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Deploy & Configure", fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAssignEaDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    // Dialog: Installation Guide Modal
    if (showInstallationGuideDialog) {
        AlertDialog(
            onDismissRequest = { showInstallationGuideDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Customer-Hosted MT5 Setup Guide", fontWeight = FontWeight.Bold, color = GoldLight)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "1. Download 'XauPilotBridge.mqh' and place inside your MT5 folder: MQL5/Include/.",
                        "2. Place the authorized EA compiled file inside MQL5/Experts/.",
                        "3. In MT5 Terminal, navigate to Tools -> Options -> Expert Advisors.",
                        "4. Check 'Allow WebRequest for listed URL' and add: https://api.xaupilot.com.",
                        "5. Attach the EA to an XAUUSD chart and input your Installation ID & Token.",
                        "6. The EA will immediately transmit its first heartbeat and synchronize with this mobile app."
                    ).forEach { step ->
                        Text(text = step, fontSize = 12.sp, color = TextPrimaryDark, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallationGuideDialog = false
                        viewModel.navigateTo(ScreenDestination.BridgeGuide)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("View MQL5 Code", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInstallationGuideDialog = false }) {
                    Text("Close", color = TextSecondaryDark)
                }
            }
        )
    }
}
