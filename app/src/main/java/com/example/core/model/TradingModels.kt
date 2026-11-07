package com.example.core.model

enum class TradeDirection {
    BUY,
    SELL
}

enum class OrderType {
    BUY_LIMIT,
    SELL_LIMIT,
    BUY_STOP,
    SELL_STOP
}

data class Position(
    val ticket: Long,
    val brokerAccountId: String,
    val eaInstallationId: String,
    val eaName: String,
    val symbol: String,
    val direction: TradeDirection,
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

data class PendingOrder(
    val ticket: Long,
    val brokerAccountId: String,
    val eaInstallationId: String,
    val eaName: String,
    val symbol: String,
    val orderType: OrderType,
    val lotSize: Double,
    val targetPrice: Double,
    val currentMarketPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val magicNumber: Long,
    val placedTimeUtc: String
)

data class ClosedTrade(
    val ticket: Long,
    val brokerAccountId: String,
    val eaName: String,
    val symbol: String,
    val direction: TradeDirection,
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

data class Candlestick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class MarketQuote(
    val symbol: String = "XAUUSD",
    val bid: Double,
    val ask: Double,
    val spreadPoints: Double,
    val high24h: Double,
    val low24h: Double,
    val change24hPercent: Double,
    val lastUpdatedUtc: String,
    val isDelayed: Boolean = false,
    val isSimulated: Boolean = false
)
