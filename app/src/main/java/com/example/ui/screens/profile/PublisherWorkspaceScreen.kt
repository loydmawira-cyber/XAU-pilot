package com.example.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val session by viewModel.authSession.collectAsState()
    val authError by viewModel.authUiError.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var description by remember { mutableStateOf("") }
    var monthlyPrice by remember { mutableStateOf("49") }
    var category by remember { mutableStateOf("GOLD_TREND") }
    var fileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var parametersJson by remember { mutableStateOf("[]") }
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
                    Text("Choose your .ex5 file. The app uploads it to the configured XAU Pilot backend. Uploading does not connect to MT5 or start trading.", fontSize = 12.sp, color = TextSecondaryDark)
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().testTag("publisher_upload_button")) {
                        Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(fileName ?: "Choose .ex5 file")
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("EA name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(version, { version = it }, label = { Text("Version (x.y.z)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("Description (at least 10 characters)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(monthlyPrice, { monthlyPrice = it }, label = { Text("Monthly price (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Strategy category", color = TextSecondaryDark, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GOLD_TREND", "GOLD_SCALPING", "GOLD_BREAKOUT").forEach { item ->
                            FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item.substringAfter("GOLD_")) })
                        }
                    }
                    Text("Developer-defined customer inputs (JSON array)", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = parametersJson, onValueChange = { parametersJson = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 5,
                        label = { Text("Input manifest parameters") },
                        supportingText = { Text("Use [] if this EA has no customer-adjustable inputs. Definitions must come from the EA developer.") }
                    )
                    Button(
                        onClick = {
                            val price = monthlyPrice.toDoubleOrNull()
                            if (fileUri != null && name.isNotBlank() && version.matches(Regex("\\d+\\.\\d+\\.\\d+")) && description.length >= 10 && price != null && price >= 0) {
                                viewModel.uploadAndPublishDraft(context, fileUri!!, name.trim(), version.trim(), description.trim(), category, price, parametersJson)
                            }
                        },
                        enabled = !busy && fileUri != null && name.isNotBlank() && version.matches(Regex("\\d+\\.\\d+\\.\\d+")) && description.length >= 10 && (monthlyPrice.toDoubleOrNull() ?: -1.0) >= 0,
                        modifier = Modifier.fillMaxWidth().testTag("submit_ea_review_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Upload and validate EA", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                    }
                    result?.let { Text(it, color = if (it.startsWith("EA and manifest uploaded")) TradingGreen else TradingAmber, fontSize = 12.sp) }
                }
            }
        }
        item {
            Text("Publishing rules", color = GoldLight, fontWeight = FontWeight.Bold)
            Text("A successful upload is not a publication. The backend must complete its configured malware scan and administrator review before the EA appears in the public marketplace. MT5 installation and trading are a later integration phase.", color = TextSecondaryDark, fontSize = 12.sp)
        }
        }
    }
}
