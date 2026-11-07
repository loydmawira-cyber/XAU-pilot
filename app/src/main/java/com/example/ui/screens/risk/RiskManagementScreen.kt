package com.example.ui.screens.risk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.components.GoldButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*

@Composable
fun RiskManagementScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val riskProfiles by viewModel.riskProfiles.collectAsState()

    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val currentRisk = riskProfiles[currentAccount?.id]

    var maxDailyLoss by remember { mutableStateOf((currentRisk?.maxDailyLossAmount ?: 500.0).toString()) }
    var maxExposureLots by remember { mutableStateOf((currentRisk?.maxTotalExposureLots ?: 2.0).toString()) }
    var maxLotPerTrade by remember { mutableStateOf((currentRisk?.maxLotSizePerTrade ?: 0.50).toString()) }
    var maxOpenTrades by remember { mutableStateOf((currentRisk?.maxOpenPositions ?: 8).toString()) }
    var maxSpread by remember { mutableStateOf((currentRisk?.maxSpreadPoints ?: 35.0).toString()) }
    var minMarginLevel by remember { mutableStateOf((currentRisk?.minMarginLevelPercent ?: 200.0).toString()) }

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
                        text = "ACCOUNT RISK GOVERNANCE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "Global Safety Guards for ${currentAccount?.label ?: "All MT5 Accounts"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Account-wide Precedence Banner
        item {
            Surface(
                color = GoldContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Account-Wide Rules Take Absolute Precedence", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldLight)
                        Text(
                            text = "When multiple EAs run simultaneously on the same MT5 account, aggregate exposure is checked across all trades. No individual EA setting or preset can exceed these limits.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = TextPrimaryDark
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Editable Risk Parameters
        item {
            SectionHeader(
                title = "MAXIMUM LOSS & EXPOSURE LIMITS",
                subtitle = "Enforced server-side before EA orders reach broker"
            )

            listOf(
                Triple("Max Daily Loss Limit ($)", maxDailyLoss, "Halts new orders if closed loss today reaches this amount") to { v: String -> maxDailyLoss = v },
                Triple("Max Total Aggregate Exposure (Lots)", maxExposureLots, "Maximum combined open lot size across all active EAs") to { v: String -> maxExposureLots = v },
                Triple("Max Single Trade Lot Size", maxLotPerTrade, "Hard ceiling preventing any individual EA from sizing too large") to { v: String -> maxLotPerTrade = v },
                Triple("Max Open Concurrent Positions", maxOpenTrades, "Total basket positions allowed on this account") to { v: String -> maxOpenTrades = v },
                Triple("Maximum Spread Filter (Points)", maxSpread, "Orders rejected if broker spread spikes beyond this value") to { v: String -> maxSpread = v },
                Triple("Minimum Margin Level (%)", minMarginLevel, "Emergency block if free margin drops below safety threshold") to { v: String -> minMarginLevel = v }
            ).forEach { (fieldInfo, setter) ->
                val (label, value, desc) = fieldInfo
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                        Text(desc, fontSize = 10.sp, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = value,
                            onValueChange = setter,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = CharcoalCardBorder,
                                focusedContainerColor = CharcoalBackground,
                                unfocusedContainerColor = CharcoalBackground,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            GoldButton(
                text = "Save Account Risk Profile",
                onClick = {
                    val accId = currentAccount?.id ?: return@GoldButton
                    val profile = AccountRiskProfile(
                        accountId = accId,
                        maxDailyLossAmount = maxDailyLoss.toDoubleOrNull() ?: 500.0,
                        maxTotalExposureLots = maxExposureLots.toDoubleOrNull() ?: 2.0,
                        maxLotSizePerTrade = maxLotPerTrade.toDoubleOrNull() ?: 0.50,
                        maxOpenPositions = maxOpenTrades.toIntOrNull() ?: 8,
                        maxSpreadPoints = maxSpread.toDoubleOrNull() ?: 35.0,
                        minMarginLevelPercent = minMarginLevel.toDoubleOrNull() ?: 200.0
                    )
                    viewModel.saveRiskProfile(profile)
                },
                icon = Icons.Default.Save,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_risk_profile_button")
            )
        }
    }
}
