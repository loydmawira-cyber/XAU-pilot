package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.network.Mql5BridgeDefinition
import com.example.ui.XauPilotViewModel
import com.example.ui.components.GoldButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*

@Composable
fun BridgeGuideScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val code = Mql5BridgeDefinition.BRIDGE_MQH_SOURCE

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
                        text = "MQL5 BRIDGE MODULE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "XauPilotBridge.mqh • MetaTrader 5 Remote Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Integration Instructions
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.IntegrationInstructions, contentDescription = null, tint = GoldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installation Checklist", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Save this file into your MT5 terminal: MQL5/Include/XauPilotBridge.mqh", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("2. Open MT5: Tools -> Options -> Expert Advisors -> check 'Allow WebRequest for listed URL'", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("3. Add exact API base URL: https://api.xaupilot.com", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("4. Include in your EA: #include <XauPilotBridge.mqh>", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("5. In OnInit(): call XauPilot_Init(backend_url, inst_id, auth_token, magic_num)", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("6. In OnTimer(): call XauPilot_Heartbeat()", fontSize = 12.sp, color = TextSecondaryDark)
                    Text("7. In OnDeinit(): call XauPilot_Deinit()", fontSize = 12.sp, color = TextSecondaryDark)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Code Header & Copy Button
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "XauPilotBridge.mqh (v${Mql5BridgeDefinition.BRIDGE_VERSION})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = GoldLight
                )
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("XauPilotBridge.mqh", code)
                        clipboard.setPrimaryClip(clip)
                        viewModel.showUserMessage("XauPilotBridge.mqh copied to clipboard!")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("copy_bridge_code_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CharcoalBackground, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Monospace Source Code Container
        item {
            Surface(
                color = CharcoalBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = TextPrimaryDark
                    )
                }
            }
        }
    }
}
