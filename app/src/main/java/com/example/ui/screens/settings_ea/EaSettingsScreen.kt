package com.example.ui.screens.settings_ea

import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.*
import com.example.ui.ScreenDestination
import com.example.ui.XauPilotViewModel
import com.example.ui.components.GoldButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun EaSettingsScreen(
    installationId: String,
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val installations by viewModel.installations.collectAsState()
    val parameterDefinitionsMap by viewModel.parameterDefinitions.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val auditHistory by viewModel.configAuditHistory.collectAsState()
    val isAdvancedMode by viewModel.isAdvancedMode.collectAsState()
    val draftValues by viewModel.draftConfigValues.collectAsState()

    val installation = installations.find { it.id == installationId } ?: installations.firstOrNull()

    // Load initial values if draft is empty
    LaunchedEffect(installationId) {
        if (installation != null && draftValues.isEmpty()) {
            viewModel.selectInstallation(installationId)
        }
    }

    if (installation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Installation not found", color = TextSecondaryDark)
        }
        return
    }

    val definitions = parameterDefinitionsMap[installation.eaProductId] ?: emptyList()
    val displayedDefinitions = definitions.filter { def ->
        if (isAdvancedMode) true
        else def.visibility == ParameterVisibility.STANDARD
    }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showAuditHistoryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Navigation & Title Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldLight)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = installation.eaProductName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "${installation.brokerAccountLabel} • Magic #${installation.magicNumber}",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                StatusBadge(
                    statusText = installation.runtimeStatus.name,
                    color = when (installation.runtimeStatus) {
                        EaRuntimeStatus.RUNNING -> TradingGreen
                        EaRuntimeStatus.PAUSED -> TradingAmber
                        else -> TradingRed
                    },
                    backgroundColor = when (installation.runtimeStatus) {
                        EaRuntimeStatus.RUNNING -> TradingGreenBg
                        EaRuntimeStatus.PAUSED -> TradingAmberBg
                        else -> TradingRedBg
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live Configuration Sync Status Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (installation.configStatus == ConfigStatus.APPLIED) Icons.Default.CheckCircle else Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (installation.configStatus == ConfigStatus.APPLIED) TradingGreen else TradingAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Version #${installation.currentConfigVersion} • ${installation.configStatus.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = "EA Bridge Heartbeat: ${installation.lastHeartbeatUtc.takeLast(8)} UTC",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        Text(
                            text = "Audit History",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showAuditHistoryDialog = true }
                                .padding(4.dp)
                        )
                    }

                    if (installation.pendingConfigVersion != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = TradingAmberBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = TradingAmber,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Transmitting version #${installation.pendingConfigVersion} to MT5 terminal...",
                                    fontSize = 11.sp,
                                    color = TradingAmber
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Mode Switcher: Standard Mode vs Advanced Mode
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            color = if (!isAdvancedMode) GoldPrimary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.setAdvancedMode(false) }
                        .testTag("standard_mode_tab")
                ) {
                    Text(
                        text = "Standard Mode",
                        fontWeight = if (!isAdvancedMode) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (!isAdvancedMode) CharcoalBackground else TextSecondaryDark
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            color = if (isAdvancedMode) GoldPrimary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.setAdvancedMode(true) }
                        .testTag("advanced_mode_tab")
                ) {
                    Text(
                        text = "Advanced Mode",
                        fontWeight = if (isAdvancedMode) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isAdvancedMode) CharcoalBackground else TextSecondaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Preset Pills
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CONFIGURATION PRESETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark
                )
                Text(
                    text = "+ Save Current",
                    fontSize = 11.sp,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showSavePresetDialog = true }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val availablePresets = presets.filter { it.eaProductId == installation.eaProductId }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availablePresets) { preset ->
                    Surface(
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CharcoalCardBorder),
                        modifier = Modifier.clickable { viewModel.applyPreset(installation.id, preset) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = GoldLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(preset.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                Text(preset.description.take(28) + "...", fontSize = 9.sp, color = TextSecondaryDark)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section Title
        item {
            SectionHeader(
                title = if (isAdvancedMode) "ALL STRATEGY INPUTS" else "CORE MONEY & RISK SETTINGS",
                subtitle = "Changes deploy to running MT5 EA runtime memory"
            )
        }

        // Group definitions by category
        val categories = displayedDefinitions.groupBy { it.category }
        categories.forEach { (catName, defs) ->
            item {
                Text(
                    text = catName.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
            }

            items(defs) { def ->
                val currentValue = draftValues[def.key] ?: def.defaultValue

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = def.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = def.description,
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            if (def.requiresRestart) {
                                Surface(
                                    color = TradingAmberBg,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "RESTART REQ",
                                        fontSize = 8.sp,
                                        color = TradingAmber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (def.type) {
                            ParameterDataType.BOOLEAN -> {
                                val isChecked = currentValue.toBooleanStrictOrNull() ?: false
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isChecked) "ENABLED" else "DISABLED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isChecked) TradingGreen else TextSecondaryDark
                                    )
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { viewModel.updateDraftParam(def.key, it.toString()) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = CharcoalBackground,
                                            checkedTrackColor = GoldPrimary
                                        )
                                    )
                                }
                            }

                            ParameterDataType.ENUM -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    def.enumOptions.forEach { opt ->
                                        val isSelected = (currentValue == opt)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                                .background(
                                                    color = if (isSelected) GoldPrimary else CharcoalSurfaceVariant,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.updateDraftParam(def.key, opt) }
                                        ) {
                                            Text(
                                                text = opt,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) CharcoalBackground else TextSecondaryDark
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (def.type == ParameterDataType.DECIMAL || def.type == ParameterDataType.INTEGER) {
                                        val stepVal = def.step ?: if (def.type == ParameterDataType.DECIMAL) 0.01 else 10.0
                                        IconButton(
                                            onClick = {
                                                val num = currentValue.toDoubleOrNull() ?: (def.minimum ?: 0.0)
                                                val next = (num - stepVal).coerceAtLeast(def.minimum ?: 0.0)
                                                val formatted = if (def.type == ParameterDataType.INTEGER) next.toInt().toString() else String.format(Locale.US, "%.2f", next)
                                                viewModel.updateDraftParam(def.key, formatted)
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CharcoalSurfaceVariant)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrement", tint = GoldLight)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = currentValue,
                                        onValueChange = { viewModel.updateDraftParam(def.key, it) },
                                        trailingIcon = {
                                            if (def.unit.isNotEmpty()) {
                                                Text(
                                                    text = def.unit,
                                                    fontSize = 11.sp,
                                                    color = GoldLight,
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = if (def.type == ParameterDataType.DECIMAL || def.type == ParameterDataType.INTEGER)
                                                KeyboardType.Decimal else KeyboardType.Text
                                        ),
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
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (def.type == ParameterDataType.DECIMAL || def.type == ParameterDataType.INTEGER) {
                                        val stepVal = def.step ?: if (def.type == ParameterDataType.DECIMAL) 0.01 else 10.0
                                        IconButton(
                                            onClick = {
                                                val num = currentValue.toDoubleOrNull() ?: (def.minimum ?: 0.0)
                                                val next = (num + stepVal).coerceAtMost(def.maximum ?: 1000000.0)
                                                val formatted = if (def.type == ParameterDataType.INTEGER) next.toInt().toString() else String.format(Locale.US, "%.2f", next)
                                                viewModel.updateDraftParam(def.key, formatted)
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CharcoalSurfaceVariant)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increment", tint = GoldLight)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Deployment Actions
        item {
            Spacer(modifier = Modifier.height(20.dp))
            GoldButton(
                text = "Apply & Deploy Configuration (v+1)",
                onClick = { viewModel.submitDraftConfiguration(installation.id) },
                icon = Icons.Default.CloudUpload,
                modifier = Modifier.fillMaxWidth(),
                testTag = "submit_configuration_button"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Pause / Resume EA control
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (installation.runtimeStatus == EaRuntimeStatus.RUNNING) {
                    OutlinedButton(
                        onClick = { viewModel.setInstallationStatus(installation.id, EaRuntimeStatus.PAUSED) },
                        border = BorderStroke(1.dp, TradingAmber.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = TradingAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause EA", color = TradingAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.setInstallationStatus(installation.id, EaRuntimeStatus.RUNNING) },
                        border = BorderStroke(1.dp, TradingGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TradingGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume EA", color = TradingGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.setInstallationStatus(installation.id, EaRuntimeStatus.STOPPED) },
                    border = BorderStroke(1.dp, TradingRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = TradingRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop EA", color = TradingRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    // Dialog: Save Preset
    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        var presetDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Save Configuration Preset", fontWeight = FontWeight.Bold, color = GoldLight) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Snapshot current parameters into a reusable preset.", fontSize = 12.sp, color = TextSecondaryDark)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset Name (e.g. London Volatility)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = presetDesc,
                        onValueChange = { presetDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentAsPreset(
                            installation.eaProductId,
                            presetName.ifBlank { "Custom Preset" },
                            presetDesc.ifBlank { "Custom user parameters" }
                        )
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Save Preset", fontWeight = FontWeight.Bold, color = CharcoalBackground)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    // Dialog: Configuration Audit Trail
    if (showAuditHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showAuditHistoryDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Configuration Audit Trail", fontWeight = FontWeight.Bold, color = GoldLight) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    items(auditHistory) { entry ->
                        Surface(
                            color = CharcoalSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CharcoalCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Version #${entry.version} • ${entry.parameterKey}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                                    StatusBadge(
                                        statusText = entry.status.name,
                                        color = if (entry.status == ConfigStatus.APPLIED) TradingGreen else TradingRed,
                                        backgroundColor = if (entry.status == ConfigStatus.APPLIED) TradingGreenBg else TradingRedBg
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Changed: '${entry.oldValue}' → '${entry.newValue}'",
                                    fontSize = 11.sp,
                                    color = GoldLight
                                )
                                Text(
                                    text = "Submitted: ${entry.submittedAtUtc}",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                                if (entry.rejectionReason != null) {
                                    Text(
                                        text = "Reason: ${entry.rejectionReason}",
                                        fontSize = 10.sp,
                                        color = TradingRed
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAuditHistoryDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                    Text("Close", color = CharcoalBackground, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
