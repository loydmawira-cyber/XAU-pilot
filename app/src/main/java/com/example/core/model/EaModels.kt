package com.example.core.model

enum class StrategyCategory(val label: String) {
    GOLD_SCALPING("Gold Scalping"),
    GOLD_TREND("Gold Trend Following"),
    GOLD_SWING("Gold Swing Trading"),
    GOLD_BREAKOUT("Gold Breakout"),
    GOLD_INTRADAY("Gold Intraday"),
    MULTI_STRATEGY("Multi-Strategy Hybrid")
}

data class EaProduct(
    val id: String,
    val productCode: String,
    val name: String,
    val description: String,
    val strategyCategory: StrategyCategory,
    val currentVersion: String,
    val supportedInstruments: List<String> = listOf("XAUUSD", "GOLD"),
    val supportedTimeframes: List<String> = listOf("M1", "M5", "M15", "H1"),
    val minDepositUsd: Double = 1000.0,
    val defaultMagicNumber: Long,
    val releaseNotes: String,
    val riskDisclosures: String,
    val documentationUrl: String,
    val monthlyPriceUsd: Double,
    val annualPriceUsd: Double,
    val lifetimePriceUsd: Double,
    val requiresVpsRecommended: Boolean = true,
    val isPublished: Boolean = true,
    val rating: Double = 4.9,
    val activeTradersCount: Int = 1420
)

enum class LicenseType {
    MONTHLY,
    ANNUAL,
    LIFETIME
}

enum class LicenseStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    SUSPENDED,
    REVOKED,
    CANCELLED
}

data class EaLicense(
    val id: String,
    val eaProductId: String,
    val eaProductName: String,
    val tenantId: String,
    val licenseKeyMasked: String,
    val licenseType: LicenseType,
    val status: LicenseStatus,
    val startDate: String,
    val expiryDate: String?, // Nullable for lifetime
    val maxAccounts: Int = 3,
    val maxInstallations: Int = 5,
    val currentActiveInstallations: Int = 1,
    val autoRenew: Boolean = true
)

enum class EaRuntimeStatus {
    RUNNING,
    PAUSED,
    STOPPED,
    DISCONNECTED,
    ERROR
}

enum class ParameterDataType {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    STRING,
    ENUM,
    TIME
}

enum class ParameterVisibility {
    STANDARD,
    ADVANCED
}

data class ParameterDefinition(
    val key: String,
    val label: String,
    val description: String,
    val type: ParameterDataType,
    val defaultValue: String,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val step: Double? = null,
    val unit: String = "",
    val category: String, // e.g. "Money Management", "Entry Strategy", "Trailing Stop"
    val visibility: ParameterVisibility = ParameterVisibility.STANDARD,
    val editable: Boolean = true,
    val requiresRestart: Boolean = false,
    val requiresNoOpenPositions: Boolean = false,
    val enumOptions: List<String> = emptyList()
)

enum class ConfigStatus {
    DRAFT,
    PENDING,
    DELIVERED,
    APPLIED,
    REJECTED,
    EXPIRED,
    FAILED,
    RESTART_REQUIRED,
    WAITING_FOR_OPEN_POSITIONS
}

data class ConfigAuditEntry(
    val version: Int,
    val parameterKey: String,
    val oldValue: String,
    val newValue: String,
    val submittedAtUtc: String,
    val appliedAtUtc: String?,
    val status: ConfigStatus,
    val rejectionReason: String? = null
)

data class EaPreset(
    val id: String,
    val eaProductId: String,
    val name: String,
    val description: String,
    val values: Map<String, String>,
    val isSystemDefault: Boolean = false
)

data class EaInstallation(
    val id: String,
    val tenantId: String,
    val eaProductId: String,
    val eaProductName: String,
    val brokerAccountId: String,
    val brokerAccountLabel: String,
    val magicNumber: Long,
    val symbol: String = "XAUUSD",
    val timeframe: String = "M5",
    val runtimeStatus: EaRuntimeStatus = EaRuntimeStatus.RUNNING,
    val currentConfigVersion: Int = 1,
    val pendingConfigVersion: Int? = null,
    val configStatus: ConfigStatus = ConfigStatus.APPLIED,
    val currentValues: Map<String, String>,
    val lastHeartbeatUtc: String,
    val hostingType: HostingType = HostingType.MANAGED_VPS,
    val vpsInstanceId: String? = null,
    val openPositionsCount: Int = 0,
    val totalProfitLossUsd: Double = 0.0
)
