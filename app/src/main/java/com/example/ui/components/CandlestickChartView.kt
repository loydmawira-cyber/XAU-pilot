package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Candlestick
import com.example.core.model.MarketQuote
import com.example.ui.theme.*

@Composable
fun CandlestickChartView(
    candlesticks: List<Candlestick>,
    marketQuote: MarketQuote,
    modifier: Modifier = Modifier
) {
    var selectedTimeframe by remember { mutableStateOf("M5") }
    val timeframes = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol & Current Price
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "XAUUSD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = GoldContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "GOLD SPOT",
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$${String.format("%.2f", marketQuote.bid)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+0.84%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TradingGreen
                        )
                    }
                    Text(
                        text = "Spread: ${marketQuote.spreadPoints} pts • Ask: ${String.format("%.2f", marketQuote.ask)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timeframe selector bar
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                timeframes.forEach { tf ->
                    val isSelected = (tf == selectedTimeframe)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(
                                color = if (isSelected) GoldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTimeframe = tf }
                    ) {
                        Text(
                            text = tf,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CharcoalBackground else TextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Candlestick Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(CharcoalBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, CharcoalCardBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (candlesticks.isEmpty()) return@Canvas

                    val w = size.width
                    val h = size.height

                    val minPrice = candlesticks.minOf { it.low } - 1.0
                    val maxPrice = candlesticks.maxOf { it.high } + 1.0
                    val priceRange = (maxPrice - minPrice).coerceAtLeast(1.0)

                    // Draw Horizontal Grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (h / gridLines) * i
                        drawLine(
                            color = CharcoalCardBorder.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // Candlestick rendering
                    val count = candlesticks.size
                    val candleSpacing = w / count
                    val candleWidth = (candleSpacing * 0.65f).coerceAtLeast(3f)

                    candlesticks.forEachIndexed { index, candle ->
                        val xCenter = (index * candleSpacing) + (candleSpacing / 2f)

                        val yHigh = h - ((candle.high - minPrice) / priceRange * h).toFloat()
                        val yLow = h - ((candle.low - minPrice) / priceRange * h).toFloat()
                        val yOpen = h - ((candle.open - minPrice) / priceRange * h).toFloat()
                        val yClose = h - ((candle.close - minPrice) / priceRange * h).toFloat()

                        val isBullish = candle.close >= candle.open
                        val color = if (isBullish) TradingGreen else TradingRed

                        // Draw Wick (High to Low)
                        drawLine(
                            color = color,
                            start = Offset(xCenter, yHigh),
                            end = Offset(xCenter, yLow),
                            strokeWidth = 2f
                        )

                        // Draw Candle Body (Open to Close)
                        val bodyTop = minOf(yOpen, yClose)
                        val bodyHeight = (kotlin.math.abs(yClose - yOpen)).coerceAtLeast(2f)

                        drawRect(
                            color = color,
                            topLeft = Offset(xCenter - (candleWidth / 2f), bodyTop),
                            size = Size(candleWidth, bodyHeight)
                        )
                    }

                    // Draw Current Price Dashed Line
                    val currentY = h - ((marketQuote.bid - minPrice) / priceRange * h).toFloat()
                    if (currentY in 0f..h) {
                        drawLine(
                            color = GoldLight,
                            start = Offset(0f, currentY),
                            end = Offset(w, currentY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Meta & Regulatory Disclosure
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Source: Awaiting connected market data",
                    fontSize = 10.sp,
                    color = TextTertiaryDark
                )
                Text(
                    text = when { marketQuote.isSimulated -> "● SIMULATED FEED"; marketQuote.bid > 0.0 && marketQuote.ask > 0.0 -> "● RECEIVED FEED"; else -> "● WAITING FOR FEED" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (marketQuote.isSimulated) TradingAmber else if (marketQuote.bid > 0.0 && marketQuote.ask > 0.0) TradingGreen else TextSecondaryDark
                )
            }
        }
    }
}
