package com.example.ui

import androidx.lifecycle.ViewModel
import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.core.network.ApiClient
import androidx.lifecycle.viewModelScope
import com.example.core.data.XauPilotRepository
import com.example.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.ByteArrayOutputStream

sealed class ScreenDestination {
    object Home : ScreenDestination()
    object Marketplace : ScreenDestination()
    data class EaDetail(val eaId: String) : ScreenDestination()
    object Accounts : ScreenDestination()
    object Positions : ScreenDestination()
    data class EaSettings(val installationId: String) : ScreenDestination()
    object VpsManagement : ScreenDestination()
    object RiskManagement : ScreenDestination()
    object Profile : ScreenDestination()
    object BridgeGuide : ScreenDestination()
    object PublisherWorkspace : ScreenDestination()
    object AdminConsole : ScreenDestination()
    object Support : ScreenDestination()
}

class XauPilotViewModel(
    private val repository: XauPilotRepository = XauPilotRepository.getInstance()
) : ViewModel() {

    private val _sessionRestoreComplete = MutableStateFlow(false)
    val sessionRestoreComplete: StateFlow<Boolean> = _sessionRestoreComplete.asStateFlow()

    // Start session restoration only after the StateFlow it updates is initialized.
    init {
        restoreAuthenticatedSession()
    }
    private val _isGuestMode = MutableStateFlow(false)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    fun enterGuestMode() { _isGuestMode.value = true }
    fun leaveGuestMode() { _isGuestMode.value = false }

    fun signOut() {
        viewModelScope.launch {
            try { ApiClient.apiService.logout() } catch (_: Exception) { }
            ApiClient.clearSession()
            repository.clearAuthenticatedSession()
            _isGuestMode.value = false
            screenBackStack.clear()
            _currentScreen.value = ScreenDestination.Home
        }
    }

    private fun restoreAuthenticatedSession() {
        viewModelScope.launch {
            val access = ApiClient.getAuthToken()
            if (access.isNullOrBlank()) {
                _sessionRestoreComplete.value = true
                return@launch
            }
            val refresh = ApiClient.getRefreshToken()
            try {
                var userResponse = ApiClient.apiService.getCurrentUser()
                var activeAccess = access
                if (!userResponse.isSuccessful && !refresh.isNullOrBlank()) {
                    val refreshed = ApiClient.apiService.refreshSession(mapOf("refresh_token" to refresh))
                    if (refreshed.isSuccessful && refreshed.body() != null) {
                        activeAccess = refreshed.body()!!.accessToken
                        ApiClient.setAuthToken(activeAccess)
                        ApiClient.setRefreshToken(refreshed.body()!!.refreshToken)
                        userResponse = ApiClient.apiService.getCurrentUser()
                    }
                }
                if (userResponse.isSuccessful && userResponse.body() != null) {
                    val currentRefresh = ApiClient.getRefreshToken().orEmpty()
                    repository.acceptAuthenticatedSession(AuthSession(activeAccess, currentRefresh, user = userResponse.body()!!))
                } else {
                    ApiClient.clearSession()
                }
            } catch (_: Exception) {
                // Keep credentials only when the backend is temporarily unreachable; do not fabricate a session.
            } finally {
                _sessionRestoreComplete.value = true
            }
        }
    }

    // Current navigation state
    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Backstack for custom navigation
    private val screenBackStack = mutableListOf<ScreenDestination>()

    // Exposed Flows from Repository
    val accounts = repository.accounts
    val selectedAccountId = repository.selectedAccountId
    val eaProducts = repository.eaProducts
    private val _eaPerformance = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
    val eaPerformance: StateFlow<Map<String, Map<String, Any?>>> = _eaPerformance.asStateFlow()
    private val _eaPerformanceLoading = MutableStateFlow<Set<String>>(emptySet())
    val eaPerformanceLoading: StateFlow<Set<String>> = _eaPerformanceLoading.asStateFlow()

    fun loadPublicEaPerformance(productId: String) {
        if (_eaPerformance.value.containsKey(productId) || _eaPerformanceLoading.value.contains(productId)) return
        viewModelScope.launch {
            _eaPerformanceLoading.update { it + productId }
            try {
                val response = ApiClient.apiService.getEaPublicPerformance(productId)
                if (response.isSuccessful && response.body() != null) {
                    _eaPerformance.update { it + (productId to response.body()!!) }
                } else {
                    _eaPerformance.update { it + (productId to mapOf("data_status" to "UNAVAILABLE", "message" to "Performance data is temporarily unavailable.")) }
                }
            } catch (_: Exception) {
                _eaPerformance.update { it + (productId to mapOf("data_status" to "UNAVAILABLE", "message" to "Connect to XAU Pilot to load live performance data.")) }
            } finally {
                _eaPerformanceLoading.update { it - productId }
            }
        }
    }
    val eaSubmissions = repository.eaSubmissions
    private val _publisherBusy = MutableStateFlow(false)
    val publisherBusy: StateFlow<Boolean> = _publisherBusy.asStateFlow()
    private val _publisherResult = MutableStateFlow<String?>(null)
    val publisherResult: StateFlow<String?> = _publisherResult.asStateFlow()
    private val _publisherProductId = MutableStateFlow<String?>(null)
    val publisherProductId: StateFlow<String?> = _publisherProductId.asStateFlow()
    private val _publisherVersionId = MutableStateFlow<String?>(null)
    private val _publisherExtraction = MutableStateFlow<Map<String, Any?>?>(null)
    val publisherExtraction: StateFlow<Map<String, Any?>?> = _publisherExtraction.asStateFlow()
    private val _publisherReviewManifest = MutableStateFlow<Map<String, Any?>?>(null)
    val publisherReviewManifest: StateFlow<Map<String, Any?>?> = _publisherReviewManifest.asStateFlow()

    fun preparePublisherReviewDraft() {
        val productId = _publisherProductId.value ?: return
        val versionId = _publisherVersionId.value ?: return
        viewModelScope.launch {
            _publisherBusy.value = true
            try {
                val response = ApiClient.apiService.prepareDeveloperEaReviewManifest(productId, versionId)
                if (!response.isSuccessful) error("Could not prepare review draft: ${response.code()} ${response.errorBody()?.string().orEmpty()}")
                _publisherReviewManifest.value = response.body()
                _publisherResult.value = "Review draft prepared. Every extracted input is hidden from customers until you choose its visibility."
            } catch (e: Exception) {
                _publisherResult.value = e.message ?: "Could not prepare review draft"
            } finally { _publisherBusy.value = false }
        }
    }

    fun updatePublisherReviewParameter(index: Int, field: String, value: Any) {
        val current = _publisherReviewManifest.value ?: return
        val oldParams = current["parameters"] as? List<*> ?: return
        if (index !in oldParams.indices) return
        val params = oldParams.mapIndexed { i, raw ->
            val map = (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }?.toMutableMap()
                ?: mutableMapOf()
            if (i == index) map[field] = value
            map
        }
        _publisherReviewManifest.value = current + ("parameters" to params)
    }

    fun savePublisherReviewDraft() {
        val productId = _publisherProductId.value ?: return
        val versionId = _publisherVersionId.value ?: return
        val userId = authSession.value?.user?.id ?: run {
            _publisherResult.value = "Sign in again before saving input settings."
            return
        }
        val current = _publisherReviewManifest.value ?: return
        val params = current["parameters"] as? List<*> ?: emptyList<Any>()
        val manifest = mapOf<String, Any>(
            "schema_version" to "1.0", "ea_product_id" to productId,
            "ea_version" to "1.0.0", "developer_id" to userId,
            "parameters" to params
        )
        viewModelScope.launch {
            _publisherBusy.value = true
            try {
                val response = ApiClient.apiService.saveDeveloperEaReviewManifest(productId, versionId, manifest)
                if (!response.isSuccessful) error("Could not save input review: ${response.code()} ${response.errorBody()?.string().orEmpty()}")
                _publisherResult.value = "Developer input review saved. Publishing remains blocked until all security and release checks pass."
            } catch (e: Exception) {
                _publisherResult.value = e.message ?: "Could not save input review"
            } finally { _publisherBusy.value = false }
        }
    }

    fun refreshPublisherExtraction() {
        val productId = _publisherProductId.value ?: return
        val versionId = _publisherVersionId.value ?: return
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getDeveloperEaExtraction(productId, versionId)
                if (response.isSuccessful) _publisherExtraction.value = response.body()
                else _publisherResult.value = "Could not refresh extraction status: ${response.code()}"
            } catch (e: Exception) {
                _publisherResult.value = e.message ?: "Could not refresh extraction status"
            }
        }
    }
    private val _publisherBridgeToken = MutableStateFlow<String?>(null)
    val publisherBridgeToken: StateFlow<String?> = _publisherBridgeToken.asStateFlow()

    fun connectPublisherPerformanceAccount(broker: String, server: String, accountNumber: String, environment: String, magicNumber: String) {
        viewModelScope.launch {
            _publisherBusy.value = true
            _publisherResult.value = null
            _publisherBridgeToken.value = null
            try {
                val productId = _publisherProductId.value ?: error("Upload your EA first to select the EA that will publish its performance.")
                require(broker.isNotBlank() && server.isNotBlank() && accountNumber.length >= 3) { "Enter broker, server and MT5 account number." }
                val magic = magicNumber.toLongOrNull()?.takeIf { it > 0 } ?: error("Magic number must be a positive integer.")
                val response = ApiClient.apiService.connectPublisherPerformanceAccount(productId, mapOf(
                    "label" to "Publisher MT5 account", "broker_name" to broker.trim(), "server_name" to server.trim(),
                    "account_number" to accountNumber.trim(), "environment" to environment, "magic_number" to magic,
                    "symbol" to "XAUUSD", "timeframe" to "M5"
                ))
                if (!response.isSuccessful) error("MT5 performance account setup failed: ${response.code()} ${response.errorBody()?.string().orEmpty()}")
                val body = response.body() ?: error("Backend returned an empty response")
                _publisherBridgeToken.value = body["ea_bridge_token"]?.toString()
                _publisherResult.value = "MT5 owner account connected. Installation ID: ${body["installation_id"]}. Copy the bridge token below and configure your bridge-enabled EA in MT5. The token is shown once."
            } catch (e: Exception) {
                _publisherResult.value = e.message ?: "Could not connect the MT5 account."
            } finally { _publisherBusy.value = false }
        }
    }

    fun uploadAndPublishDraft(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            _publisherBusy.value = true
            _publisherResult.value = null
            try {
                val user = authSession.value?.user ?: error("Please sign in before uploading an EA")
                val fileName = context.contentResolver.query(
                    fileUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "ea.ex5"
                require(fileName.lowercase(Locale.US).endsWith(".ex5")) { "Please select a compiled .ex5 EA file" }
                val eaName = fileName.substringBeforeLast('.').replace(Regex("[_-]+"), " ").trim()
                    .ifBlank { "New EA" }.take(120)
                val api = ApiClient.apiService
                val developer = api.registerDeveloper(mapOf(
                    "display_name" to user.fullName, "contact_email" to user.email
                ))
                if (!developer.isSuccessful && developer.code() != 409) {
                    error("Developer setup failed: ${developer.code()} ${developer.errorBody()?.string().orEmpty()}")
                }
                val code = eaName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    .ifBlank { "ea-package" }.take(28) + "-" + java.util.UUID.randomUUID().toString().take(8)
                val productResponse = api.createDeveloperEaProduct(mapOf(
                    "product_code" to code,
                    "name" to eaName,
                    "description" to "EA package uploaded by its developer. Marketplace details can be completed after automatic input discovery.",
                    "strategy_category" to "GOLD_TREND",
                    "risk_disclosures" to "Automated trading involves substantial risk. Test on a demo account before live use.",
                    "monthly_price_usd" to 0.0,
                    "annual_price_usd" to 0.0,
                    "lifetime_price_usd" to 0.0,
                    "min_deposit_usd" to 0.0
                ))
                if (!productResponse.isSuccessful) error("EA draft creation failed: ${productResponse.code()} ${productResponse.errorBody()?.string().orEmpty()}")
                val productId = productResponse.body()?.get("id")?.toString() ?: error("Backend did not return EA ID")
                _publisherProductId.value = productId
                val versionResponse = api.createDeveloperEaVersion(productId, mapOf(
                    "version" to "1.0.0", "release_notes" to "Initial upload"
                ))
                if (!versionResponse.isSuccessful) error("EA version setup failed: ${versionResponse.code()} ${versionResponse.errorBody()?.string().orEmpty()}")
                val versionId = versionResponse.body()?.get("id")?.toString() ?: error("Backend did not return version ID")
                _publisherVersionId.value = versionId
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 25 * 1024 * 1024) { "EA file exceeds the 25 MB upload limit" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } ?: error("Could not read the selected EA file")
                require(bytes.isNotEmpty()) { "EA file is empty" }
                val part = MultipartBody.Part.createFormData(
                    "file", fileName, bytes.toRequestBody("application/octet-stream".toMediaType())
                )
                val upload = api.uploadDeveloperEaBinary(productId, versionId, part)
                if (!upload.isSuccessful) error("EA upload failed: ${upload.code()} ${upload.errorBody()?.string().orEmpty()}")
                val scan = ((upload.body()?.get("binary") as? Map<*, *>)?.get("scan_status") as? String) ?: "UNKNOWN"
                refreshPublisherExtraction()
                _publisherResult.value = "EX5 uploaded and saved as a draft. File scan: $scan. Extraction status is shown below. Product ID: $productId"
            } catch (e: Exception) {
                _publisherResult.value = e.message ?: "EA upload failed"
            } finally {
                _publisherBusy.value = false
            }
        }
    }
    val licenses = repository.licenses
    val installations = repository.installations
    val selectedInstallationId = repository.selectedInstallationId
    val parameterDefinitions = repository.parameterDefinitions
    val presets = repository.presets
    val configAuditHistory = repository.configAuditHistory
    val riskProfiles = repository.riskProfiles
    val positions = repository.positions
    val pendingOrders = repository.pendingOrders
    val tradeHistory = repository.tradeHistory
    val marketQuote = repository.marketQuote
    val candlesticks = repository.candlesticks
    val vpsInstances = repository.vpsInstances
    val vpsLogs = repository.vpsLogs
    val notifications = repository.notifications
    val apiSettings = repository.apiSettings
    val authSession = repository.authSession
    private val _authUiError = MutableStateFlow<String?>(null)
    val authUiError: StateFlow<String?> = _authUiError.asStateFlow()

    fun authenticatePublisher(email: String, password: String, fullName: String?, createAccount: Boolean) {
        viewModelScope.launch {
            _publisherBusy.value = true
            _authUiError.value = null
            try {
                val payload = if (createAccount) mapOf("email" to email.trim(), "password" to password, "full_name" to (fullName ?: "").trim())
                    else mapOf("email" to email.trim(), "password" to password)
                val response = if (createAccount) ApiClient.apiService.register(payload) else ApiClient.apiService.login(payload)
                if (!response.isSuccessful) error("Authentication failed: ${response.code()} ${response.errorBody()?.string().orEmpty()}")
                val session = response.body() ?: error("Backend returned an empty authentication response")
                ApiClient.setAuthToken(session.accessToken)
                ApiClient.setRefreshToken(session.refreshToken)
                // Keep the authenticated session in the repository state.
                repository.acceptAuthenticatedSession(session)
            } catch (e: Exception) {
                _authUiError.value = e.message ?: "Authentication failed"
            } finally { _publisherBusy.value = false }
        }
    }
    val backendSyncStatus = repository.backendSyncStatus

    fun refreshBackend() {
        repository.syncFromBackend()
    }

    // Temporary draft values while in EA Settings screen
    private val _draftConfigValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftConfigValues: StateFlow<Map<String, String>> = _draftConfigValues.asStateFlow()

    // Status message / snackbar
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Settings Mode: Standard vs Advanced
    private val _isAdvancedMode = MutableStateFlow(false)
    val isAdvancedMode: StateFlow<Boolean> = _isAdvancedMode.asStateFlow()

    // Emergency Stop Dialog State
    private val _emergencyDialogVisible = MutableStateFlow(false)
    val emergencyDialogVisible: StateFlow<Boolean> = _emergencyDialogVisible.asStateFlow()

    private val _selectedEmergencyPolicy = MutableStateFlow(EmergencyStopPolicy.CLOSE_NEW_ENTRIES_ONLY)
    val selectedEmergencyPolicy: StateFlow<EmergencyStopPolicy> = _selectedEmergencyPolicy.asStateFlow()

    fun navigateTo(destination: ScreenDestination) {
        if (_currentScreen.value != destination) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = destination
        }
    }

    fun navigateBack(): Boolean {
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
            return true
        }
        return false
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun showUserMessage(msg: String) {
        _userMessage.value = msg
    }

    fun selectAccount(id: String) {
        repository.selectAccount(id)
    }

    fun selectInstallation(id: String) {
        repository.selectInstallation(id)
        val inst = installations.value.find { it.id == id }
        if (inst != null) {
            _draftConfigValues.value = inst.currentValues
        }
    }

    fun setAdvancedMode(isAdvanced: Boolean) {
        _isAdvancedMode.value = isAdvanced
    }

    fun updateDraftParam(key: String, value: String) {
        _draftConfigValues.update { it + (key to value) }
    }

    fun submitDraftConfiguration(installationId: String) {
        viewModelScope.launch {
            val success = repository.updateEaConfiguration(installationId, _draftConfigValues.value)
            if (success) {
                _userMessage.value = "Configuration queued for EA deployment (v+1)"
            } else {
                _userMessage.value = "Configuration rejected by Account Risk Rules!"
            }
        }
    }

    fun applyPreset(installationId: String, preset: EaPreset) {
        _draftConfigValues.value = preset.values
        repository.applyPreset(installationId, preset)
        _userMessage.value = "Applied preset '${preset.name}'"
    }

    fun saveCurrentAsPreset(eaProductId: String, name: String, desc: String) {
        repository.savePreset(eaProductId, name, desc, _draftConfigValues.value)
        _userMessage.value = "Saved preset '$name'"
    }

    // Remote Commands
    fun openEmergencyDialog() {
        _emergencyDialogVisible.value = true
    }

    fun dismissEmergencyDialog() {
        _emergencyDialogVisible.value = false
    }

    fun setEmergencyPolicy(policy: EmergencyStopPolicy) {
        _selectedEmergencyPolicy.value = policy
    }

    fun executeEmergencyStop(accountId: String) {
        viewModelScope.launch {
            val policy = EmergencyStopPolicy.CLOSE_ALL_MARKET_ORDERS
            _emergencyDialogVisible.value = false
            val success = repository.sendAccountCommand(accountId, CommandType.EMERGENCY_STOP, policy)
            _userMessage.value = if (success) {
                "Stop & Close All queued for every EA installation; awaiting MT5 acknowledgement"
            } else {
                "Emergency Stop was rejected or could not be delivered"
            }
        }
    }

    fun pauseAccount(accountId: String) {
        viewModelScope.launch {
            val success = repository.sendAccountCommand(accountId, CommandType.PAUSE)
            _userMessage.value = if (success) "Paused all EAs on Account" else "Pause command failed"
        }
    }

    fun resumeAccount(accountId: String) {
        viewModelScope.launch {
            val success = repository.sendAccountCommand(accountId, CommandType.RESUME)
            _userMessage.value = if (success) "Resumed active EAs on Account" else "Resume command failed"
        }
    }

    fun setInstallationStatus(installationId: String, status: EaRuntimeStatus) {
        viewModelScope.launch {
            repository.setInstallationStatus(installationId, status)
            _userMessage.value = "EA status changed to ${status.name}"
        }
    }

    // Add MT5 Account
    fun addAccount(label: String, broker: String, server: String, accountNum: String, env: AccountEnvironment, balance: Double) {
        runCatching { repository.addAccount(label, broker, server, accountNum, env, balance) }
            .onFailure { _userMessage.value = it.message ?: "MT5 account linking is not available yet" }
    }

    fun removeAccount(id: String) {
        repository.removeAccount(id)
        _userMessage.value = "Account disconnected and removed"
    }

    // Assign EA
    fun assignEa(ea: EaProduct, account: BrokerAccount, magicNumber: Long, tf: String) {
        runCatching { repository.assignEaToAccount(ea, account, magicNumber, tf) }
            .onFailure { _userMessage.value = it.message ?: "MT5 installation is not available yet" }
    }

    // Purchase License
    fun purchaseLicense(eaProductId: String, type: LicenseType) {
        runCatching { repository.purchaseLicense(eaProductId, type) }
            .onFailure { _userMessage.value = it.message ?: "Checkout is not configured yet" }
    }

    // VPS controls
    fun restartVps(vpsId: String) {
        repository.restartVps(vpsId)
        _userMessage.value = "Managed VPS controls are not enabled yet"
    }

    fun stopVps(vpsId: String) {
        repository.stopVps(vpsId)
        _userMessage.value = "Managed VPS controls are not enabled yet"
    }

    fun placeOrder(
        accountId: String,
        eaInstallationId: String,
        eaName: String,
        direction: TradeDirection,
        lotSize: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        magicNumber: Long
    ) {
        viewModelScope.launch {
            runCatching {
                repository.placeOrder(accountId, eaInstallationId, eaName, direction, lotSize, stopLoss, takeProfit, magicNumber)
            }.onFailure { _userMessage.value = it.message ?: "MT5 trading is not available yet" }
        }
    }

    fun closePosition(ticket: Long) {
        viewModelScope.launch {
            runCatching { repository.closePosition(ticket) }
                .onFailure { _userMessage.value = it.message ?: "MT5 position management is not available yet" }
        }
    }

    fun deletePreset(id: String) {
        repository.deletePreset(id)
        _userMessage.value = "Preset deleted"
    }

    fun saveRiskProfile(profile: AccountRiskProfile) {
        repository.saveRiskProfile(profile)
        _userMessage.value = "Account risk profile updated & enforced"
    }

    fun updateApiUrl(url: String) {
        repository.updateApiBaseUrl(url)
        _userMessage.value = "API endpoint updated"
    }
}
