package com.example.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.XauPilotViewModel
import com.example.ui.theme.*

@Composable
fun PublisherWorkspaceScreen(viewModel: XauPilotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val busy by viewModel.publisherBusy.collectAsState()
    val result by viewModel.publisherResult.collectAsState()
    val extraction by viewModel.publisherExtraction.collectAsState()
    val reviewManifest by viewModel.publisherReviewManifest.collectAsState()
    val createdProductId by viewModel.publisherProductId.collectAsState()
    val bridgeToken by viewModel.publisherBridgeToken.collectAsState()
    val session by viewModel.authSession.collectAsState()
    val authError by viewModel.authUiError.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(true) }
    var fileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var brokerName by remember { mutableStateOf("") }
    var brokerServer by remember { mutableStateOf("") }
    var mt5AccountNumber by remember { mutableStateOf("") }
    var mt5MagicNumber by remember { mutableStateOf("880001") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileUri = uri
        fileName = uri?.lastPathSegment?.substringAfterLast('/')
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CharcoalBackground).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row {
                IconButton(onClick = { viewModel.navigateBack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = GoldLight) }
                Column {
                    Text("PUBLISHER WORKSPACE", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GoldLight)
                    Text("Upload a real compiled EA package", fontSize = 12.sp, color = TextSecondaryDark)
                }
            }
        }
        if (session == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CharcoalSurface), border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .35f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Sign in to XAU Pilot", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        Text("Create a real account or sign in. Credentials are sent to your configured backend; no demo account is provided.", color = TextSecondaryDark, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = createAccount, onClick = { createAccount = true }, label = { Text("Create account") })
                            FilterChip(selected = !createAccount, onClick = { createAccount = false }, label = { Text("Sign in") })
                        }
                        if (createAccount) OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Button(onClick = { viewModel.authenticatePublisher(email, password, fullName, createAccount) }, enabled = !busy && email.contains("@") && password.length >= 8 && (!createAccount || fullName.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                            Text(if (createAccount) "Create account" else "Sign in")
                        }
                        authError?.let { Text(it, color = TradingAmber, fontSize = 12.sp) }
                    }
                }
            }
        } else {
        item {
            Text("Signed in as ${session!!.user.email}", color = TradingGreen, fontSize = 12.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalSurface), border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .35f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New EA release", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Text("Upload one compiled .ex5 file. XAU Pilot creates the draft automatically. Uploading does not connect to a live MT5 account or start trading.", fontSize = 12.sp, color = TextSecondaryDark)
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().testTag("publisher_upload_button")) {
                        Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(fileName ?: "Choose .ex5 file")
                    }
                    Text(
                        "XAU Pilot will create your EA draft from this file. No manifest or customer-input form is required.",
                        fontSize = 12.sp, color = TextSecondaryDark
                    )
                    Button(
                        onClick = { fileUri?.let { viewModel.uploadAndPublishDraft(context, it) } },
                        enabled = !busy && fileUri != null,
                        modifier = Modifier.fillMaxWidth().testTag("submit_ea_review_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Upload EA", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                    }
                    result?.let { Text(it, color = if (it.startsWith("EX5 uploaded")) TradingGreen else TradingAmber, fontSize = 12.sp) }
                    extraction?.let { job ->
                        Spacer(Modifier.height(4.dp))
                        Text("AUTOMATIC MT5 INPUT DISCOVERY", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Status: ${job["status"]?.toString() ?: "Unknown"}", color = if (job["status"] == "COMPLETE") TradingGreen else TradingAmber, fontWeight = FontWeight.SemiBold)
                        when (job["status"]?.toString()) {
                            "QUEUED" -> Text("Your EX5 is waiting for the secure scanner and isolated MT5 extraction worker.", color = TextSecondaryDark, fontSize = 12.sp)
                            "RUNNING" -> Text("The isolated MT5 worker is inspecting the uploaded EA. Refresh to check progress.", color = TextSecondaryDark, fontSize = 12.sp)
                            "COMPLETE" -> {
                                val inputs = job["inputs"] as? List<*>
                                Text("Extracted inputs: ${inputs?.size ?: 0}. These are not customer-visible until you review and approve them.", color = TextSecondaryDark, fontSize = 12.sp)
                                inputs.orEmpty().forEach { raw ->
                                    val item = raw as? Map<*, *> ?: return@forEach
                                    Text("• ${item["display_name"] ?: item["key"]} — ${item["type"] ?: "unknown"} (default: ${item["default"] ?: "not reported"})", color = TextPrimaryDark, fontSize = 11.sp)
                                }
                            }
                            "FAILED" -> Text("Extraction failed (${job["error_code"] ?: "unknown error"}). Upload a corrected build or contact support.", color = TradingAmber, fontSize = 12.sp)
                            else -> Text("Extraction has not been queued yet.", color = TextSecondaryDark, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { viewModel.refreshPublisherExtraction() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Refresh extraction status")
                        }
                        if (job["status"] == "COMPLETE" && reviewManifest == null) {
                            Button(onClick = { viewModel.preparePublisherReviewDraft() }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Prepare developer input review")
                            }
                        }
                        reviewManifest?.let { review ->
                            Spacer(Modifier.height(6.dp))
                            Text("DEVELOPER INPUT APPROVAL", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Every input starts hidden and non-editable. Choose what customers may see and edit. Do not enable a setting unless you understand its effect.", color = TextSecondaryDark, fontSize = 12.sp)
                            val reviewInputs = review["parameters"] as? List<*>
                            reviewInputs.orEmpty().forEachIndexed { index, raw ->
                                val parameter = raw as? Map<*, *> ?: return@forEachIndexed
                                Card(colors = CardDefaults.cardColors(containerColor = CharcoalBackground),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .25f))) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(parameter["label"]?.toString() ?: parameter["key"]?.toString().orEmpty(),
                                            color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                                        Text("Type: ${parameter["type"]} · Extracted default: ${parameter["default"]}",
                                            color = TextSecondaryDark, fontSize = 11.sp)
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = parameter["visible_to_customer"] == true,
                                                onCheckedChange = { viewModel.updatePublisherReviewParameter(index, "visible_to_customer", it) }
                                            )
                                            Text("Visible to customers", color = TextPrimaryDark, fontSize = 12.sp)
                                        }
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = parameter["customer_editable"] == true,
                                                enabled = parameter["visible_to_customer"] == true,
                                                onCheckedChange = { viewModel.updatePublisherReviewParameter(index, "customer_editable", it) }
                                            )
                                            Text("Customers may edit", color = TextPrimaryDark, fontSize = 12.sp)
                                        }
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = parameter["requires_restart"] == true,
                                                onCheckedChange = { viewModel.updatePublisherReviewParameter(index, "requires_restart", it) }
                                            )
                                            Text("Requires EA restart after change", color = TextPrimaryDark, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            if (reviewInputs.isNullOrEmpty()) {
                                Text("No supported inputs were extracted. Unresolved inputs must be inspected before this EA can be published.", color = TradingAmber, fontSize = 12.sp)
                            }
                            Button(onClick = { viewModel.savePublisherReviewDraft() }, enabled = !busy && !reviewInputs.isNullOrEmpty(),
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                                Text("Save developer input choices", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (createdProductId != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CharcoalSurface), border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .35f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Connect owner MT5 account for public performance", color = GoldLight, fontWeight = FontWeight.Bold)
                        Text("Run this EA on a dedicated MT5 account. Use an account that is not running other EAs or manual trades, otherwise account equity and drawdown figures can be affected.", color = TextSecondaryDark, fontSize = 11.sp)
                        OutlinedTextField(brokerName, { brokerName = it }, label = { Text("Broker name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(brokerServer, { brokerServer = it }, label = { Text("MT5 server") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(mt5AccountNumber, { mt5AccountNumber = it }, label = { Text("MT5 account number (no password)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(mt5MagicNumber, { mt5MagicNumber = it }, label = { Text("EA magic number") }, modifier = Modifier.fillMaxWidth())
                        Text("Performance tracking is DEMO-only in this release.", color = TradingAmber, fontSize = 11.sp)
                        Button(onClick = { viewModel.connectPublisherPerformanceAccount(brokerName, brokerServer, mt5AccountNumber, "DEMO", mt5MagicNumber) },
                            enabled = !busy && brokerName.isNotBlank() && brokerServer.isNotBlank() && mt5AccountNumber.length >= 3, modifier = Modifier.fillMaxWidth()) {
                            Text("Connect owner MT5 account")
                        }
                        bridgeToken?.let { token ->
                            Text("EA bridge token — copy this into the bridge-enabled EA settings. It will not be shown again:", color = TradingAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            SelectionContainer { Text(token, color = TextPrimaryDark, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        item {
            Text("Publishing rules", color = GoldLight, fontWeight = FontWeight.Bold)
            Text("A successful upload is not a publication. The backend must complete its configured malware scan and administrator review before the EA appears in the public marketplace. Owner performance tracking requires a bridge-compatible EA build on the dedicated demo MT5 account; customer installation and trading are separate permissions.", color = TextSecondaryDark, fontSize = 12.sp)
        }
        }
    }
}
