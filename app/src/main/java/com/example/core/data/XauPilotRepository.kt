package com.example.core.data

import android.content.Context
import com.example.core.database.*
import com.example.core.model.*
import com.example.core.network.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class XauPilotRepository private constructor(context: Context? = null) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun currentUtc(): String = dateFormat.format(Date())

    // Database handle
    private val database: XauPilotDatabase? = context?.let { XauPilotDatabase.getInstance(it) }
    private val dao: XauPilotDao? = database?.xauPilotDao()

    // --- StateFlows ---
    private val _apiSettings = MutableStateFlow(ApiSettings(isDemoMode = false, customApiBaseUrl = ApiClient.getBaseUrl().removeSuffix("/")))
    val apiSettings: StateFlow<ApiSettings> = _apiSettings.asStateFlow()

    private val _authSession = MutableStateFlow<AuthSession?>(null)
    val authSession: StateFlow<AuthSession?> = _authSession.asStateFlow()

    fun acceptAuthenticatedSession(session: AuthSession) { _authSession.value = session }

    // Accounts
    private val _accounts = MutableStateFlow<List<BrokerAccount>>(emptyList())
    val accounts: StateFlow<List<BrokerAccount>> = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(_accounts.value.firstOrNull()?.id)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    // EA Marketplace Catalog
    private val _eaProducts = MutableStateFlow<List<EaProduct>>(emptyList())
    val eaProducts: StateFlow<List<EaProduct>> = _eaProducts.asStateFlow()

    // Publisher pipeline: upload -> review -> marketplace approval.
    private val _eaSubmissions = MutableStateFlow<List<EaSubmission>>(emptyList())
    val eaSubmissions: StateFlow<List<EaSubmission>> = _eaSubmissions.asStateFlow()

    // Licenses
    private val _licenses = MutableStateFlow<List<EaLicense>>(emptyList())
    val licenses: StateFlow<List<EaLicense>> = _licenses.asStateFlow()

    // Installations
    private val _installations = MutableStateFlow<List<EaInstallation>>(emptyList())
    val installations: StateFlow<List<EaInstallation>> = _installations.asStateFlow()

    private val _selectedInstallationId = MutableStateFlow<String?>(_installations.value.firstOrNull()?.id)
    val selectedInstallationId: StateFlow<String?> = _selectedInstallationId.asStateFlow()

    // Parameter Definitions
    private val _parameterDefinitions = MutableStateFlow<Map<String, List<ParameterDefinition>>>(emptyMap())
    val parameterDefinitions: StateFlow<Map<String, List<ParameterDefinition>>> = _parameterDefinitions.asStateFlow()

    // Presets
    private val _presets = MutableStateFlow<List<EaPreset>>(emptyList())
    val presets: StateFlow<List<EaPreset>> = _presets.asStateFlow()

    // Configuration Audit Trail
    private val _configAuditHistory = MutableStateFlow<List<ConfigAuditEntry>>(emptyList())
    val configAuditHistory: StateFlow<List<ConfigAuditEntry>> = _configAuditHistory.asStateFlow()

    // Risk Profiles
    private val _riskProfiles = MutableStateFlow<Map<String, AccountRiskProfile>>(emptyMap())
    val riskProfiles: StateFlow<Map<String, AccountRiskProfile>> = _riskProfiles.asStateFlow()

    // Trading data
    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(emptyList())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<ClosedTrade>>(emptyList())
    val tradeHistory: StateFlow<List<ClosedTrade>> = _tradeHistory.asStateFlow()

    // Live Market Quote
    private val _marketQuote = MutableStateFlow(
        MarketQuote(
            symbol = "XAUUSD",
            bid = 0.0,
            ask = 0.0,
            spreadPoints = 0.0,
            high24h = 0.0,
            low24h = 0.0,
            change24hPercent = 0.0,
            lastUpdatedUtc = currentUtc(),
            isSimulated = false
        )
    )
    val marketQuote: StateFlow<MarketQuote> = _marketQuote.asStateFlow()

    // Candlesticks
    private val _candlesticks = MutableStateFlow<List<Candlestick>>(emptyList())
    val candlesticks: StateFlow<List<Candlestick>> = _candlesticks.asStateFlow()

    // VPS
    private val _vpsInstances = MutableStateFlow<List<VpsInstance>>(emptyList())
    val vpsInstances: StateFlow<List<VpsInstance>> = _vpsInstances.asStateFlow()

    private val _vpsLogs = MutableStateFlow<List<VpsLogEntry>>(emptyList())
    val vpsLogs: StateFlow<List<VpsLogEntry>> = _vpsLogs.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // Backend Connection Status
    private val _backendSyncStatus = MutableStateFlow("NOT_CONFIGURED")
    val backendSyncStatus: StateFlow<String> = _backendSyncStatus.asStateFlow()

    init {
        initRoomSync()
        // No seeded market data or simulated ticks in the real-data build.
    }

    private fun initRoomSync() {
        if (dao == null) return

        scope.launch {
            // Deliberately do not seed local/demo trading records.

            launch {
                dao.getAllAccounts().collect { entities ->
                    _accounts.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllInstallations().collect { entities ->
                    _installations.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllLicenses().collect { entities ->
                    _licenses.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllPositions().collect { entities ->
                    _positions.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllClosedTrades().collect { entities ->
                    _tradeHistory.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllPresets().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _presets.value = entities.map { it.toModel() }
                    }
                }
            }
        }
    }

    // --- Mode & API Configuration ---

    fun selectAccount(accountId: String) {
        _selectedAccountId.value = accountId
        if (!_apiSettings.value.isDemoMode) {
            refreshAccountDataFromBackend(accountId)
        }
    }

    fun selectInstallation(installationId: String) {
        _selectedInstallationId.value = installationId
    }

    fun updateApiBaseUrl(url: String) {
        ApiClient.setBaseUrl(url)
        _apiSettings.update { it.copy(customApiBaseUrl = url, isDemoMode = false) }
        syncFromBackend()
    }

    // --- Production Backend Synchronizer ---
    fun syncFromBackend() {
        scope.launch {
            try {
                _backendSyncStatus.value = "CONNECTING"
                val accountsResp = ApiClient.apiService.getBrokerAccounts()
                if (accountsResp.isSuccessful && accountsResp.body() != null) {
                    val remoteAccounts = accountsResp.body()!!
                    _accounts.value = remoteAccounts
                    _backendSyncStatus.value = "CONNECTED"
                }

                val eaResp = ApiClient.apiService.getEaProducts()
                if (eaResp.isSuccessful && eaResp.body() != null) {
                    _eaProducts.value = eaResp.body()!!
                }

                val licResp = ApiClient.apiService.getLicenses()
                if (licResp.isSuccessful && licResp.body() != null) {
                    _licenses.value = licResp.body()!!
                }

                val instResp = ApiClient.apiService.getInstallations()
                if (instResp.isSuccessful && instResp.body() != null) {
                    _installations.value = instResp.body()!!
                }

                val vpsResp = ApiClient.apiService.getVpsInstances()
                if (vpsResp.isSuccessful && vpsResp.body() != null) {
                    _vpsInstances.value = vpsResp.body()!!
                }

                _selectedAccountId.value?.let { accId ->
                    refreshAccountDataFromBackend(accId)
                }
            } catch (e: Exception) {
                _backendSyncStatus.value = "ERROR: ${e.message}"
                addNotification("Sync Warning", "Could not reach remote engine: ${e.localizedMessage}", NotificationType.HEARTBEAT_ALERT)
            }
        }
    }

    private fun refreshAccountDataFromBackend(accountId: String) {
        scope.launch {
            try {
                val posResp = ApiClient.apiService.getPositions(accountId)
                if (posResp.isSuccessful && posResp.body() != null) {
                    _positions.value = posResp.body()!!
                }

                val tradesResp = ApiClient.apiService.getTradesHistory(accountId)
                if (tradesResp.isSuccessful && tradesResp.body() != null) {
                    _tradeHistory.value = tradesResp.body()!!
                }

                val riskResp = ApiClient.apiService.getAccountRiskProfile(accountId)
                if (riskResp.isSuccessful && riskResp.body() != null) {
                    _riskProfiles.update { it + (accountId to riskResp.body()!!) }
                }
            } catch (e: Exception) {
                // Silently record warning, do not crash UI
                android.util.Log.w("XauPilotRepository", "Account data sync failed: ${e.message}")
            }
        }
    }

    // --- Remote EA Configuration Management (NO FAKE 1.2S SIMULATION IN PROD) ---
    suspend fun updateEaConfiguration(installationId: String, newValues: Map<String, String>): Boolean {
        val installation = _installations.value.find { it.id == installationId } ?: return false
        val account = _accounts.value.find { it.id == installation.brokerAccountId }
        val risk = _riskProfiles.value[account?.id] ?: AccountRiskProfile(accountId = installation.brokerAccountId)

        // 1. Client-Side Account Risk Profile Boundary Check
        val requestedLot = newValues["lot_size"]?.toDoubleOrNull() ?: 0.01
        if (requestedLot > risk.maxLotSizePerTrade) {
            addAuditEntry(
                version = installation.currentConfigVersion + 1,
                paramKey = "lot_size",
                oldVal = installation.currentValues["lot_size"] ?: "0.01",
                newVal = requestedLot.toString(),
                status = ConfigStatus.REJECTED,
                reason = "Exceeds Account Max Lot Size Limit (${risk.maxLotSizePerTrade} lots)"
            )
            addNotification("Config Rejected", "Lot size $requestedLot exceeds account safety limit", NotificationType.CONFIG_REJECTED)
            return false
        }

        val nextVersion = installation.currentConfigVersion + 1

            // PRODUCTION LIVE MODE: True Backend & Bridge Confirmation Flow
            // NO fake timer! Stored in PENDING state until confirmed by remote MQL5 bridge
            try {
                val resp = ApiClient.apiService.updateConfiguration(installationId, newValues)
                if (resp.isSuccessful) {
                    _installations.update { list ->
                        list.map { inst ->
                            if (inst.id == installationId) {
                                inst.copy(
                                    pendingConfigVersion = nextVersion,
                                    configStatus = ConfigStatus.PENDING
                                )
                            } else inst
                        }
                    }
                    addAuditEntry(
                        version = nextVersion,
                        paramKey = "all",
                        oldVal = "v${installation.currentConfigVersion}",
                        newVal = "v$nextVersion",
                        status = ConfigStatus.PENDING,
                        reason = "Queued in Cloud. Awaiting EA WebRequest acknowledgement."
                    )
                    addNotification("Config Queued", "Version #$nextVersion queued for deployment", NotificationType.CONFIG_APPLIED)
                    return true
                } else {
                    val errBody = resp.errorBody()?.string() ?: "Rejected by server"
                    addAuditEntry(
                        version = nextVersion,
                        paramKey = "all",
                        oldVal = "",
                        newVal = "",
                        status = ConfigStatus.REJECTED,
                        reason = errBody
                    )
                    addNotification("Config Rejected", errBody, NotificationType.CONFIG_REJECTED)
                    return false
                }
            } catch (e: Exception) {
                addAuditEntry(
                    version = nextVersion,
                    paramKey = "all",
                    oldVal = "",
                    newVal = "",
                    status = ConfigStatus.FAILED,
                    reason = "Network transmission failed: ${e.localizedMessage}"
                )
                addNotification("Network Error", "Could not dispatch configuration: ${e.localizedMessage}", NotificationType.CONFIG_REJECTED)
                return false
            }
    }

    // Remote Account Control
    suspend fun sendAccountCommand(accountId: String, commandType: CommandType, emergencyPolicy: EmergencyStopPolicy? = null): Boolean {
        return try {
            val response = when (commandType) {
                CommandType.PAUSE -> ApiClient.apiService.pauseAccount(accountId)
                CommandType.RESUME -> ApiClient.apiService.resumeAccount(accountId)
                CommandType.STOP -> ApiClient.apiService.pauseAccount(accountId)
                CommandType.EMERGENCY_STOP -> ApiClient.apiService.emergencyStopAccount(
                    accountId, mapOf("policy" to (emergencyPolicy?.name ?: "CLOSE_NEW_ENTRIES_ONLY"))
                )
            }
            if (!response.isSuccessful) {
                addNotification("Command rejected", response.errorBody()?.string() ?: "Server rejected the command (${response.code()})", NotificationType.SYSTEM_SECURITY)
                false
            } else {
                // HTTP acceptance is not an EA acknowledgement. Do not change runtime status here.
                addNotification("Command queued", "${commandType.name} was accepted by the backend. Waiting for an MT5 EA acknowledgement.", NotificationType.SYSTEM_SECURITY)
                true
            }
        } catch (e: Exception) {
            addNotification("Command failed", "Backend request failed: ${e.localizedMessage}", NotificationType.SYSTEM_SECURITY)
            false
        }
    }

    suspend fun setInstallationStatus(installationId: String, newStatus: EaRuntimeStatus) {
        addNotification(
            "MT5 acknowledgement required",
            "Runtime status cannot be changed manually. The connected EA must report the confirmed state.",
            NotificationType.SYSTEM_SECURITY
        )
    }

    // Presets
    fun applyPreset(installationId: String, preset: EaPreset) {
        scope.launch {
            updateEaConfiguration(installationId, preset.values)
        }
    }

    fun savePreset(eaProductId: String, name: String, description: String, values: Map<String, String>) {
        val newPreset = EaPreset(
            id = "pre_${UUID.randomUUID().toString().take(8)}",
            eaProductId = eaProductId,
            name = name,
            description = description,
            values = values,
            isSystemDefault = false
        )
        _presets.update { it + newPreset }
        scope.launch { dao?.insertPreset(newPreset.toEntity()) }
    }

    fun deletePreset(id: String) {
        _presets.update { list -> list.filter { it.id != id } }
        scope.launch { dao?.deletePresetById(id) }
    }

    // Accounts
    fun addAccount(
        label: String, brokerName: String, serverName: String, accountNumber: String,
        environment: AccountEnvironment, initialBalance: Double
    ): BrokerAccount = throw IllegalStateException(
        "MT5 account linking is not enabled in this upload-test release. No local or simulated account was created."
    )

    fun removeAccount(accountId: String) {
        _accounts.update { list -> list.filter { it.id != accountId } }
        _installations.update { list -> list.filter { it.brokerAccountId != accountId } }
        _positions.update { list -> list.filter { it.brokerAccountId != accountId } }

        scope.launch {
            dao?.deleteAccountById(accountId)
            dao?.deleteInstallationsByAccount(accountId)
            dao?.deletePositionsByAccount(accountId)
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.deleteBrokerAccount(accountId)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote account deletion failed: ${e.message}")
                }
            }
        }
    }

    fun saveRiskProfile(profile: AccountRiskProfile) {
        _riskProfiles.update { it + (profile.accountId to profile) }
        scope.launch {
            dao?.insertRiskProfile(
                RiskProfileEntity(
                    accountId = profile.accountId,
                    maxDailyLossAmount = profile.maxDailyLossAmount,
                    maxTotalExposureLots = profile.maxTotalExposureLots,
                    maxLotSizePerTrade = profile.maxLotSizePerTrade,
                    maxOpenPositions = profile.maxOpenPositions,
                    maxSpreadPoints = profile.maxSpreadPoints,
                    minMarginLevelPercent = profile.minMarginLevelPercent,
                    emergencyStopPolicy = profile.emergencyStopPolicy.name
                )
            )
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.updateAccountRiskProfile(profile.accountId, profile)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote risk profile update failed: ${e.message}")
                }
            }
        }
    }

    // EA Assignment
    fun assignEaToAccount(
        eaProduct: EaProduct, account: BrokerAccount, magicNumber: Long, timeframe: String = "M5"
    ): EaInstallation = throw IllegalStateException(
        "EA installation is unavailable until the MT5 integration phase. No simulated installation was created."
    )

    // License Purchase
    fun purchaseLicense(eaProductId: String, licenseType: LicenseType): EaLicense = throw IllegalStateException(
        "Real payment and licence checkout are not configured. No licence or payment was created."
    )

    // Orders
    suspend fun placeOrder(
        accountId: String, eaInstallationId: String, eaName: String, direction: TradeDirection,
        lotSize: Double, stopLoss: Double?, takeProfit: Double?, magicNumber: Long
    ): Position = throw IllegalStateException(
        "Trading is disabled until a real MT5 connection is integrated. No order was sent or simulated."
    )

    suspend fun closePosition(ticket: Long): Boolean = throw IllegalStateException(
        "Position management requires a connected MT5 account. No local position was changed."
    )

    // VPS actions
    fun restartVps(vpsId: String) {
        addNotification("VPS unavailable", "Managed VPS control is not enabled in this release. No restart was requested.", NotificationType.VPS_STATUS)
    }

    fun stopVps(vpsId: String) {
        addNotification("VPS unavailable", "Managed VPS control is not enabled in this release. No VPS was stopped.", NotificationType.VPS_STATUS)
    }

    private fun addVpsLog(level: String, msg: String) {
        val entry = VpsLogEntry(timestampUtc = currentUtc(), level = level, message = msg)
        _vpsLogs.update { listOf(entry) + it.take(49) }
    }

    private fun addNotification(title: String, message: String, type: NotificationType) {
        val notif = AppNotification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = type,
            timestampUtc = currentUtc(),
            read = false
        )
        _notifications.update { listOf(notif) + it.take(49) }
    }

    private fun addAuditEntry(version: Int, paramKey: String, oldVal: String, newVal: String, status: ConfigStatus, reason: String?) {
        val entry = ConfigAuditEntry(
            version = version,
            parameterKey = paramKey,
            oldValue = oldVal,
            newValue = newVal,
            submittedAtUtc = currentUtc(),
            appliedAtUtc = if (status == ConfigStatus.APPLIED) currentUtc() else null,
            status = status,
            rejectionReason = reason
        )
        _configAuditHistory.update { listOf(entry) + it }
    }

    // --- Room Mappings ---
    private fun BrokerAccount.toEntity() = BrokerAccountEntity(
        id = id,
        tenantId = tenantId,
        label = label,
        brokerName = brokerName,
        serverName = serverName,
        accountNumberMasked = accountNumberMasked,
        environment = environment.name,
        connectionStatus = connectionStatus.name,
        currency = currency,
        balance = balance,
        equity = equity,
        freeMargin = freeMargin,
        usedMargin = usedMargin,
        marginLevelPercent = marginLevelPercent,
        leverage = leverage,
        assignedEaCount = assignedEaCount,
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = hostingType.name
    )

    private fun BrokerAccountEntity.toModel() = BrokerAccount(
        id = id,
        tenantId = tenantId,
        label = label,
        brokerName = brokerName,
        serverName = serverName,
        accountNumberMasked = accountNumberMasked,
        environment = if (environment == "LIVE") AccountEnvironment.LIVE else AccountEnvironment.DEMO,
        connectionStatus = ConnectionStatus.valueOf(connectionStatus),
        currency = currency,
        balance = balance,
        equity = equity,
        freeMargin = freeMargin,
        usedMargin = usedMargin,
        marginLevelPercent = marginLevelPercent,
        leverage = leverage,
        assignedEaCount = assignedEaCount,
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = HostingType.valueOf(hostingType)
    )

    private fun EaInstallation.toEntity() = EaInstallationEntity(
        id = id,
        tenantId = tenantId,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        brokerAccountId = brokerAccountId,
        brokerAccountLabel = brokerAccountLabel,
        magicNumber = magicNumber,
        symbol = symbol,
        timeframe = timeframe,
        runtimeStatus = runtimeStatus.name,
        currentConfigVersion = currentConfigVersion,
        pendingConfigVersion = pendingConfigVersion,
        configStatus = configStatus.name,
        valuesJson = mapToJson(currentValues),
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = hostingType.name,
        vpsInstanceId = vpsInstanceId,
        openPositionsCount = openPositionsCount,
        totalProfitLossUsd = totalProfitLossUsd
    )

    private fun EaInstallationEntity.toModel() = EaInstallation(
        id = id,
        tenantId = tenantId,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        brokerAccountId = brokerAccountId,
        brokerAccountLabel = brokerAccountLabel,
        magicNumber = magicNumber,
        symbol = symbol,
        timeframe = timeframe,
        runtimeStatus = EaRuntimeStatus.valueOf(runtimeStatus),
        currentConfigVersion = currentConfigVersion,
        pendingConfigVersion = pendingConfigVersion,
        configStatus = ConfigStatus.valueOf(configStatus),
        currentValues = jsonToMap(valuesJson),
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = HostingType.valueOf(hostingType),
        vpsInstanceId = vpsInstanceId,
        openPositionsCount = openPositionsCount,
        totalProfitLossUsd = totalProfitLossUsd
    )

    private fun EaLicense.toEntity() = EaLicenseEntity(
        id = id,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        tenantId = tenantId,
        licenseKeyMasked = licenseKeyMasked,
        licenseType = licenseType.name,
        status = status.name,
        startDate = startDate,
        expiryDate = expiryDate,
        maxAccounts = maxAccounts,
        maxInstallations = maxInstallations,
        currentActiveInstallations = currentActiveInstallations
    )

    private fun EaLicenseEntity.toModel() = EaLicense(
        id = id,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        tenantId = tenantId,
        licenseKeyMasked = licenseKeyMasked,
        licenseType = LicenseType.valueOf(licenseType),
        status = LicenseStatus.valueOf(status),
        startDate = startDate,
        expiryDate = expiryDate,
        maxAccounts = maxAccounts,
        maxInstallations = maxInstallations,
        currentActiveInstallations = currentActiveInstallations
    )

    private fun Position.toEntity() = PositionEntity(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaInstallationId = eaInstallationId,
        eaName = eaName,
        symbol = symbol,
        direction = direction.name,
        lotSize = lotSize,
        openPrice = openPrice,
        currentPrice = currentPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        floatingProfitLoss = floatingProfitLoss,
        swap = swap,
        commission = commission,
        magicNumber = magicNumber,
        openTimeUtc = openTimeUtc
    )

    private fun PositionEntity.toModel() = Position(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaInstallationId = eaInstallationId,
        eaName = eaName,
        symbol = symbol,
        direction = TradeDirection.valueOf(direction),
        lotSize = lotSize,
        openPrice = openPrice,
        currentPrice = currentPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        floatingProfitLoss = floatingProfitLoss,
        swap = swap,
        commission = commission,
        magicNumber = magicNumber,
        openTimeUtc = openTimeUtc
    )

    private fun ClosedTrade.toEntity() = ClosedTradeEntity(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaName = eaName,
        symbol = symbol,
        direction = direction.name,
        lotSize = lotSize,
        openPrice = openPrice,
        closePrice = closePrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        netProfitLoss = netProfitLoss,
        commission = commission,
        swap = swap,
        openTimeUtc = openTimeUtc,
        closeTimeUtc = closeTimeUtc
    )

    private fun ClosedTradeEntity.toModel() = ClosedTrade(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaName = eaName,
        symbol = symbol,
        direction = TradeDirection.valueOf(direction),
        lotSize = lotSize,
        openPrice = openPrice,
        closePrice = closePrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        netProfitLoss = netProfitLoss,
        commission = commission,
        swap = swap,
        openTimeUtc = openTimeUtc,
        closeTimeUtc = closeTimeUtc
    )

    private fun EaPreset.toEntity() = PresetEntity(
        id = id,
        eaProductId = eaProductId,
        name = name,
        description = description,
        valuesJson = mapToJson(values),
        isSystemDefault = isSystemDefault
    )

    private fun PresetEntity.toModel() = EaPreset(
        id = id,
        eaProductId = eaProductId,
        name = name,
        description = description,
        values = jsonToMap(valuesJson),
        isSystemDefault = isSystemDefault
    )

    private fun mapToJson(map: Map<String, String>): String {
        val entries = map.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
        return "{$entries}"
    }

    private fun jsonToMap(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        val result = mutableMapOf<String, String>()
        val pattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        pattern.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    // --- Backend-only data policy ---

    // No local seed builders are included in the production client. Account, EA,
    // licence, position, quote, VPS and notification data must come from the backend.

    companion object {
        @Volatile
        private var INSTANCE: XauPilotRepository? = null

        fun initialize(context: Context): XauPilotRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: XauPilotRepository(context).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context? = null): XauPilotRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: XauPilotRepository(context).also { INSTANCE = it }
            }
        }
    }
}
