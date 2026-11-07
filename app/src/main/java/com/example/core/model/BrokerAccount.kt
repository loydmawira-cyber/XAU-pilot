package com.example.core.model

enum class AccountEnvironment {
    DEMO,
    LIVE
}

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR,
    UNVERIFIED
}

data class BrokerAccount(
    val id: String,
    val tenantId: String,
    val label: String,
    val brokerName: String,
    val serverName: String,
    val accountNumberMasked: String, // e.g. "8899****21"
    val environment: AccountEnvironment,
    val connectionStatus: ConnectionStatus,
    val currency: String = "USD",
    val balance: Double,
    val equity: Double,
    val freeMargin: Double,
    val usedMargin: Double,
    val marginLevelPercent: Double,
    val leverage: Int = 500,
    val assignedEaCount: Int = 0,
    val lastHeartbeatUtc: String,
    val hostingType: HostingType = HostingType.MANAGED_VPS
)

enum class HostingType {
    CUSTOMER_HOSTED,
    MANAGED_VPS
}

data class AccountRiskProfile(
    val accountId: String,
    val maxDailyLossAmount: Double = 500.0,
    val maxDailyLossPercent: Double = 5.0,
    val maxTotalExposureLots: Double = 2.0,
    val maxOpenPositions: Int = 8,
    val maxLotSizePerTrade: Double = 0.50,
    val maxSpreadPoints: Double = 35.0,
    val minMarginLevelPercent: Double = 200.0,
    val allowedSymbols: List<String> = listOf("XAUUSD", "GOLD", "XAUUSD.m"),
    val emergencyStopPolicy: EmergencyStopPolicy = EmergencyStopPolicy.CLOSE_NEW_ENTRIES_ONLY,
    val dailyLossReachedToday: Boolean = false,
    val currentDailyLossAmount: Double = 0.0
)

enum class EmergencyStopPolicy {
    CLOSE_NEW_ENTRIES_ONLY,       // Recommended default: halts new entries, manages existing positions
    CLOSE_ALL_MARKET_ORDERS,      // Closes open positions, cancels pending orders
    CLOSE_PROFITABLE_ONLY,        // Secures winners, pauses EA
    FREEZE_ALL_EA_TIMERS          // Stops all MQL5 timer executions
}
