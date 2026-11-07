package com.example.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScreenDestination
import com.example.ui.XauPilotViewModel
import com.example.ui.components.GoldButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val authSession by viewModel.authSession.collectAsState()
    val licenses by viewModel.licenses.collectAsState()

    val user = authSession?.user
    var showSignOutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                text = "PROFILE & SETTINGS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
            Text(
                text = "Your account, licenses and personal settings",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // User Identity Card
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GoldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GoldLight, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(user?.fullName ?: "Trader Account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimaryDark)
                                Text(user?.email ?: "trader@xaupilot.com", fontSize = 12.sp, color = TextSecondaryDark)
                            }
                        }

                        StatusBadge(
                            statusText = user?.role?.name ?: "CUSTOMER",
                            color = GoldLight,
                            backgroundColor = GoldContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CharcoalBackground, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("MFA Security", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("ENABLED (TOTP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TradingGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Active Licenses", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("${licenses.size} Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // MQL5 Bridge Code & Docs Section
        item {
            SectionHeader(
                title = "MQL5 COMMUNICATION BRIDGE",
                subtitle = "XauPilotBridge.mqh module & integration instructions"
            )

            Surface(
                color = CharcoalSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(ScreenDestination.BridgeGuide) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = GoldLight, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("View XauPilotBridge.mqh Source", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            Text("Official MQL5 WebRequest interface v2.4.0", fontSize = 11.sp, color = TextSecondaryDark)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryDark)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionHeader(
                title = "PUBLISHER WORKSPACE",
                subtitle = "Submit, review, and publish compiled Expert Advisors"
            )
            Surface(
                color = CharcoalSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.navigateTo(ScreenDestination.PublisherWorkspace) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GoldLight, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Upload an EA for review", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                            Text("Approved products appear in the customer marketplace", fontSize = 11.sp, color = TextSecondaryDark)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryDark)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Regulatory & Financial Risk Disclosures
        item {
            SectionHeader(
                title = "LEGAL & RISK DISCLOSURES",
                subtitle = "Mandatory fintech & algorithmic trading transparency"
            )

            Surface(
                color = CharcoalSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Broker Independence & Custody:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GoldLight
                    )
                    Text(
                        text = "XAU Pilot does not operate as a financial broker and does not hold, custody, or manage customer trading funds. Customers trade entirely through their own authorized third-party broker accounts.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "2. Algorithmic Risk & Non-Guarantee:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GoldLight
                    )
                    Text(
                        text = "Automated trading in Gold (XAUUSD) carries significant risk of financial loss. Past performance, backtests, or preset labels (Conservative/Balanced/Aggressive) do not represent guaranteed returns or financial advice.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "3. MT5 Integration & Live Trading:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GoldLight
                    )
                    Text(
                        text = "MT5 account linking and trade execution are not included in this EA-upload test release. Live trading must remain disabled until MT5 integration, security testing, broker approvals and jurisdictional review are completed.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }

        if (authSession != null) {
            item {
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TradingRed.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TradingRed)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You will need to sign in again to access your account.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut()
                }) { Text("Sign out", color = TradingRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = GoldLight) }
            },
            containerColor = CharcoalSurface,
            titleContentColor = TextPrimaryDark,
            textContentColor = TextSecondaryDark
        )
    }
}
