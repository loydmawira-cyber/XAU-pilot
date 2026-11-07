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

private data class CustomerInputDraft(
    val label: String = "",
    val key: String = "",
    val type: String = "decimal",
    val defaultValue: String = "",
    val minimum: String = "",
    val maximum: String = "",
    val step: String = "",
    val choices: String = "",
    val maxLength: String = "200",
    val description: String = "",
    val visible: Boolean = true,
    val editable: Boolean = true,
    val requiresRestart: Boolean = true
)

private fun buildCustomerInputsJson(inputs: List<CustomerInputDraft>): String {
    val array = org.json.JSONArray()
    val seenKeys = mutableSetOf<String>()
    inputs.forEachIndexed { index, input ->
        require(input.label.isNotBlank()) { "Input ${index + 1}: enter a customer-facing name." }
        require(input.key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}"))) {
            "Input ${index + 1}: enter a valid EA variable name (letters, numbers and underscores; cannot start with a number)."
        }
        require(seenKeys.add(input.key)) { "Input ${index + 1}: duplicate EA variable name '${input.key}'." }
        val item = org.json.JSONObject()
            .put("key", input.key)
            .put("label", input.label.trim())
            .put("type", input.type)
            .put("visible_to_customer", input.visible)
            .put("customer_editable", input.editable)
            .put("requires_restart", input.requiresRestart)
        if (input.description.isNotBlank()) item.put("description", input.description.trim())
        when (input.type) {
            "integer" -> {
                val default = input.defaultValue.toLongOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: default must be a whole number.")
                item.put("default", default)
                if (input.minimum.isNotBlank()) item.put("minimum", input.minimum.toLongOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: minimum must be a whole number."))
                if (input.maximum.isNotBlank()) item.put("maximum", input.maximum.toLongOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: maximum must be a whole number."))
                if (input.step.isNotBlank()) item.put("step", input.step.toLongOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: step must be a whole number."))
            }
            "decimal" -> {
                val default = input.defaultValue.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: default must be a number.")
                item.put("default", default)
                if (input.minimum.isNotBlank()) item.put("minimum", input.minimum.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: minimum must be a number."))
                if (input.maximum.isNotBlank()) item.put("maximum", input.maximum.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: maximum must be a number."))
                if (input.step.isNotBlank()) item.put("step", input.step.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: step must be a number."))
            }
            "boolean" -> {
                val value = input.defaultValue.trim().lowercase()
                require(value == "true" || value == "false") { "Input ${index + 1}: boolean default must be true or false." }
                item.put("default", value.toBoolean())
            }
            "string" -> {
                item.put("default", input.defaultValue)
                item.put("max_length", input.maxLength.toIntOrNull()
                    ?: throw IllegalArgumentException("Input ${index + 1}: maximum text length must be a whole number."))
            }
            "enum" -> {
                val choices = input.choices.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                require(choices.isNotEmpty()) { "Input ${index + 1}: enter at least one choice." }
                require(choices.contains(input.defaultValue)) { "Input ${index + 1}: default must match one of the choices." }
                item.put("default", input.defaultValue)
                item.put("choices", org.json.JSONArray(choices))
            }
            else -> throw IllegalArgumentException("Input ${index + 1}: unsupported input type.")
        }
        array.put(item)
    }
    return array.toString()
}

@Composable
fun PublisherWorkspaceScreen(viewModel: XauPilotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val busy by viewModel.publisherBusy.collectAsState()
    val result by viewModel.publisherResult.collectAsState()
    val createdProductId by viewModel.publisherProductId.collectAsState()
    val bridgeToken by viewModel.publisherBridgeToken.collectAsState()
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
    val customerInputs = remember { mutableStateListOf<CustomerInputDraft>() }
    var customerInputError by remember { mutableStateOf<String?>(null) }
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
                    Text("Customer Settings", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add the settings customers are allowed to change in your EA. Use the exact input variable name from your EA code. XAU Pilot builds the definitions automatically.",
                        color = TextSecondaryDark, fontSize = 12.sp
                    )
                    customerInputs.forEachIndexed { index, input ->
                        var typeMenuExpanded by remember(index) { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalBackground),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .25f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Input ${index + 1}", color = GoldLight, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { customerInputs.removeAt(index) }) { Text("Remove", color = TradingAmber) }
                                }
                                OutlinedTextField(
                                    value = input.label,
                                    onValueChange = { customerInputs[index] = input.copy(label = it) },
                                    label = { Text("Customer-facing name") },
                                    placeholder = { Text("e.g. Trade Lot Size") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = input.key,
                                    onValueChange = { customerInputs[index] = input.copy(key = it.trim()) },
                                    label = { Text("EA input variable name") },
                                    placeholder = { Text("e.g. LotSize") },
                                    supportingText = { Text("Must exactly match the EA input variable.") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Box {
                                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Input type: ${input.type}")
                                    }
                                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                                        listOf("decimal", "integer", "boolean", "string", "enum").forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                                onClick = {
                                                    customerInputs[index] = input.copy(type = option)
                                                    typeMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = input.defaultValue,
                                    onValueChange = { customerInputs[index] = input.copy(defaultValue = it) },
                                    label = { Text("Default value") },
                                    placeholder = { Text(if (input.type == "boolean") "true or false" else "Enter default value") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                if (input.type == "integer" || input.type == "decimal") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = input.minimum, onValueChange = { customerInputs[index] = input.copy(minimum = it) },
                                            label = { Text("Minimum") }, singleLine = true, modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = input.maximum, onValueChange = { customerInputs[index] = input.copy(maximum = it) },
                                            label = { Text("Maximum") }, singleLine = true, modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = input.step, onValueChange = { customerInputs[index] = input.copy(step = it) },
                                        label = { Text("Step (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (input.type == "enum") {
                                    OutlinedTextField(
                                        value = input.choices, onValueChange = { customerInputs[index] = input.copy(choices = it) },
                                        label = { Text("Choices (comma-separated)") },
                                        placeholder = { Text("Low, Medium, High") },
                                        supportingText = { Text("The default value must match one of these choices.") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (input.type == "string") {
                                    OutlinedTextField(
                                        value = input.maxLength, onValueChange = { customerInputs[index] = input.copy(maxLength = it) },
                                        label = { Text("Maximum text length") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                OutlinedTextField(
                                    value = input.description,
                                    onValueChange = { customerInputs[index] = input.copy(description = it) },
                                    label = { Text("Description (optional)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = input.visible, onCheckedChange = { customerInputs[index] = input.copy(visible = it) })
                                    Text("Visible to customers", color = TextPrimaryDark, fontSize = 13.sp)
                                }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = input.editable, onCheckedChange = { customerInputs[index] = input.copy(editable = it) })
                                    Text("Customers can edit this setting", color = TextPrimaryDark, fontSize = 13.sp)
                                }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = input.requiresRestart, onCheckedChange = { customerInputs[index] = input.copy(requiresRestart = it) })
                                    Text("Requires EA restart after change", color = TextPrimaryDark, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            customerInputs.add(CustomerInputDraft())
                            customerInputError = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_input_button")
                    ) {
                        Text("+ Add Customer Input")
                    }
                    customerInputError?.let { Text(it, color = TradingAmber, fontSize = 12.sp) }
                    if (customerInputs.isEmpty()) {
                        Text("No customer settings added yet. Tap Add Customer Input to create the first one.", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val price = monthlyPrice.toDoubleOrNull()
                            if (fileUri != null && name.isNotBlank() && version.matches(Regex("\\d+\\.\\d+\\.\\d+")) && description.length >= 10 && price != null && price >= 0) {
                                runCatching { buildCustomerInputsJson(customerInputs) }
                                    .onSuccess { json ->
                                        customerInputError = null
                                        viewModel.uploadAndPublishDraft(context, fileUri!!, name.trim(), version.trim(), description.trim(), category, price, json)
                                    }
                                    .onFailure { error ->
                                        customerInputError = error.message ?: "Please check the customer input definitions."
                                    }
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
