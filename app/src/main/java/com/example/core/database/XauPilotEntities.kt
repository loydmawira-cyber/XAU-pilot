package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "broker_accounts")
data class BrokerAccountEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val label: String,
    val brokerName: String,
    val serverName: String,
    val accountNumberMasked: String,
    val environment: String, // DEMO or LIVE
    val connectionStatus: String,
    val currency: String = "USD",
    val balance: Double,
    val equity: Double,
    val freeMargin: Double,
    val usedMargin: Double,
    val marginLevelPercent: Double,
    val leverage: Int = 500,
    val assignedEaCount: Int = 0,
    val lastHeartbeatUtc: String,
    val hostingType: String = "MANAGED_VPS"
)

@Entity(tableName = "ea_installations")
data class EaInstallationEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val eaProductId: String,
    val eaProductName: String,
    val brokerAccountId: String,
    val brokerAccountLabel: String,
    val magicNumber: Long,
    val symbol: String = "XAUUSD",
    val timeframe: String = "M5",
    val runtimeStatus: String = "RUNNING", // RUNNING, PAUSED, STOPPED
    val currentConfigVersion: Int = 1,
    val pendingConfigVersion: Int? = null,
    val configStatus: String = "APPLIED",
    val valuesJson: String, // Key-value map serialized as JSON
    val lastHeartbeatUtc: String,
    val hostingType: String = "MANAGED_VPS",
    val vpsInstanceId: String? = null,
    val openPositionsCount: Int = 0,
    val totalProfitLossUsd: Double = 0.0
)

@Entity(tableName = "ea_licenses")
data class EaLicenseEntity(
    @PrimaryKey val id: String,
    val eaProductId: String,
    val eaProductName: String,
    val tenantId: String,
    val licenseKeyMasked: String,
    val licenseType: String, // MONTHLY, ANNUAL, LIFETIME
    val status: String = "ACTIVE",
    val startDate: String,
    val expiryDate: String?,
    val maxAccounts: Int = 3,
    val maxInstallations: Int = 5,
    val currentActiveInstallations: Int = 1
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val ticket: Long,
    val brokerAccountId: String,
    val eaInstallationId: String,
    val eaName: String,
    val symbol: String = "XAUUSD",
    val direction: String, // BUY or SELL
    val lotSize: Double,
    val openPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val floatingProfitLoss: Double,
    val swap: Double = 0.0,
    val commission: Double = 0.0,
    val magicNumber: Long,
    val openTimeUtc: String
)

@Entity(tableName = "closed_trades")
data class ClosedTradeEntity(
    @PrimaryKey val ticket: Long,
    val brokerAccountId: String,
    val eaName: String,
    val symbol: String = "XAUUSD",
    val direction: String,
    val lotSize: Double,
    val openPrice: Double,
    val closePrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val netProfitLoss: Double,
    val commission: Double,
    val swap: Double,
    val openTimeUtc: String,
    val closeTimeUtc: String
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val eaProductId: String,
    val name: String,
    val description: String,
    val valuesJson: String,
    val isSystemDefault: Boolean = false
)

@Entity(tableName = "config_audit")
data class ConfigAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val installationId: String,
    val version: Int,
    val parameterKey: String,
    val oldValue: String,
    val newValue: String,
    val submittedAtUtc: String,
    val appliedAtUtc: String?,
    val status: String,
    val rejectionReason: String? = null
)

@Entity(tableName = "risk_profiles")
data class RiskProfileEntity(
    @PrimaryKey val accountId: String,
    val maxDailyLossAmount: Double = 500.0,
    val maxTotalExposureLots: Double = 2.0,
    val maxLotSizePerTrade: Double = 0.50,
    val maxOpenPositions: Int = 8,
    val maxSpreadPoints: Double = 35.0,
    val minMarginLevelPercent: Double = 200.0,
    val emergencyStopPolicy: String = "CLOSE_NEW_ENTRIES_ONLY"
)
