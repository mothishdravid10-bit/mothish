package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

data class PieSliceData(
    val name: String,
    val value: Double,
    val color: Color,
    val categoryId: String = ""
)

/**
 * Interactive Material 3 Pie or Donut Chart in Pure Jetpack Compose
 * Supports slice selection, hover/tap popouts, percentage calculation,
 * center text, legend, and detailed transaction inspection callback.
 */
@Composable
fun ZenoInteractivePieChart(
    title: String,
    slices: List<PieSliceData>,
    isDonut: Boolean = true,
    donutHoleFraction: Float = 0.58f,
    modifier: Modifier = Modifier,
    onSliceClick: ((PieSliceData) -> Unit)? = null
) {
    val nonZeroSlices = remember(slices) { slices.filter { it.value > 0.0 } }
    val totalValue = remember(nonZeroSlices) { nonZeroSlices.sumOf { it.value } }

    var selectedSliceIndex by remember(slices) { mutableStateOf<Int?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        modifier = modifier
            .fillMaxWidth()
            .testTag("chart_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (totalValue > 0) "Total: ${formatCurrency(totalValue)}" else "No transactions recorded yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedSliceIndex != null && selectedSliceIndex in nonZeroSlices.indices) {
                    val sel = nonZeroSlices[selectedSliceIndex!!]
                    TextButton(onClick = { selectedSliceIndex = null }) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = Emerald400)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (totalValue <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Navy700),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Awaiting transactions for $title",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Canvas Chart
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(nonZeroSlices) {
                                    detectTapGestures { tapOffset ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = tapOffset.x - center.x
                                        val dy = tapOffset.y - center.y
                                        val dist = sqrt(dx * dx + dy * dy)
                                        val radius = size.width / 2f

                                        val minR = if (isDonut) radius * (donutHoleFraction - 0.1f) else 0f
                                        if (dist in minR..radius * 1.15f) {
                                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            if (angle < 0) angle += 360f

                                            // Determine which slice was clicked
                                            var currentStart = 0f
                                            for (i in nonZeroSlices.indices) {
                                                val sweep = (nonZeroSlices[i].value / totalValue * 360.0).toFloat()
                                                if (angle in currentStart..(currentStart + sweep)) {
                                                    selectedSliceIndex = if (selectedSliceIndex == i) null else i
                                                    selectedSliceIndex?.let { idx ->
                                                        onSliceClick?.invoke(nonZeroSlices[idx])
                                                    }
                                                    break
                                                }
                                                currentStart += sweep
                                            }
                                        } else {
                                            selectedSliceIndex = null
                                        }
                                    }
                                }
                        ) {
                            val canvasRadius = size.width / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)

                            var currentStartAngle = 0f
                            nonZeroSlices.forEachIndexed { index, slice ->
                                val sweepAngle = (slice.value / totalValue * 360.0).toFloat()
                                val isSelected = selectedSliceIndex == index
                                val popout = if (isSelected) 8.dp.toPx() else 0f

                                // Calculate directional offset for selected popout slice
                                val midAngleRad = Math.toRadians((currentStartAngle + sweepAngle / 2.0))
                                val popX = (cos(midAngleRad) * popout).toFloat()
                                val popY = (sin(midAngleRad) * popout).toFloat()

                                val sliceRect = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx())
                                val topLeft = Offset(8.dp.toPx() + popX, 8.dp.toPx() + popY)

                                if (isDonut) {
                                    val strokeW = canvasRadius * (1f - donutHoleFraction)
                                    drawArc(
                                        color = slice.color,
                                        startAngle = currentStartAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = Offset(strokeW / 2f + popX + 4.dp.toPx(), strokeW / 2f + popY + 4.dp.toPx()),
                                        size = Size(size.width - strokeW - 8.dp.toPx(), size.height - strokeW - 8.dp.toPx()),
                                        style = Stroke(width = strokeW)
                                    )
                                } else {
                                    drawArc(
                                        color = slice.color,
                                        startAngle = currentStartAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = true,
                                        topLeft = topLeft,
                                        size = sliceRect,
                                        style = Fill
                                    )
                                }
                                currentStartAngle += sweepAngle
                            }
                        }

                        // Donut Center Text / Selected Slice Info
                        if (isDonut) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (selectedSliceIndex != null && selectedSliceIndex in nonZeroSlices.indices) {
                                    val sel = nonZeroSlices[selectedSliceIndex!!]
                                    val pct = (sel.value / totalValue * 100.0)
                                    Text(
                                        text = "${String.format("%.1f", pct)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = sel.color
                                    )
                                    Text(
                                        text = sel.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "${nonZeroSlices.size}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Categories",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Legend & Percentage Breakdown
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        nonZeroSlices.forEachIndexed { idx, item ->
                            val pct = if (totalValue > 0) (item.value / totalValue * 100.0) else 0.0
                            val isSelected = selectedSliceIndex == idx

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) item.color.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable {
                                        selectedSliceIndex = if (selectedSliceIndex == idx) null else idx
                                        selectedSliceIndex?.let { onSliceClick?.invoke(item) }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(item.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${formatCurrency(item.value)} (${String.format("%.0f", pct)}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) item.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Drill-Down Prompt
            if (selectedSliceIndex != null && selectedSliceIndex in nonZeroSlices.indices) {
                val sel = nonZeroSlices[selectedSliceIndex!!]
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSliceClick?.invoke(sel) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected: ${sel.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = sel.color
                            )
                            Text(
                                text = "Amount: ${formatCurrency(sel.value)} (${String.format("%.1f", sel.value / totalValue * 100.0)}% of total)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Tap to Inspect →",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400
                        )
                    }
                }
            }
        }
    }
}

data class MonthlyBarData(
    val monthLabel: String,
    val income: Double,
    val expense: Double,
    val sales: Double,
    val purchases: Double,
    val profit: Double,
    val cashFlow: Double
)

/**
 * Interactive Dual-Bar Chart & Comparison for Monthly Financials
 */
@Composable
fun ZenoMonthlyComparisonBarChart(
    title: String,
    data: List<MonthlyBarData>,
    modifier: Modifier = Modifier
) {
    var activeMetric by remember { mutableStateOf(0) } // 0: Income vs Expense, 1: Sales vs Purchases, 2: Profit & Cashflow

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Metric Switcher Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Inc/Exp", "Sales/Pur", "Profit").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = activeMetric == idx,
                            onClick = { activeMetric = idx },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500.copy(alpha = 0.25f),
                                selectedLabelColor = Emerald400
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val maxVal = remember(data, activeMetric) {
                data.maxOfOrNull {
                    when (activeMetric) {
                        0 -> max(it.income, it.expense)
                        1 -> max(it.sales, it.purchases)
                        else -> max(abs(it.profit), abs(it.cashFlow))
                    }
                }?.coerceAtLeast(100.0) ?: 100.0
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { item ->
                    val (val1, val2, color1, color2, label1, label2) = when (activeMetric) {
                        0 -> Tuple6(item.income, item.expense, Emerald500, ErrorRed, "Income", "Expense")
                        1 -> Tuple6(item.sales, item.purchases, InfoBlue, Amber500, "Sales", "Purchases")
                        else -> Tuple6(max(0.0, item.profit), max(0.0, item.cashFlow), Emerald400, PurpleAccent, "Profit", "CashFlow")
                    }

                    val bar1Fraction = (val1 / maxVal).coerceIn(0.04, 1.0).toFloat()
                    val bar2Fraction = (val2 / maxVal).coerceIn(0.04, 1.0).toFloat()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(140.dp)
                        ) {
                            // Bar 1
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(bar1Fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(color1)
                            )
                            // Bar 2
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(bar2Fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(color2)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Navy700)
            Spacer(modifier = Modifier.height(8.dp))

            // Chart Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (c1, c2, l1, l2) = when (activeMetric) {
                    0 -> Tuple4(Emerald500, ErrorRed, "Income", "Expenses")
                    1 -> Tuple4(InfoBlue, Amber500, "Sales", "Purchases")
                    else -> Tuple4(Emerald400, PurpleAccent, "Net Profit", "Cash Flow")
                }
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c1))
                Spacer(modifier = Modifier.width(6.dp))
                Text(l1, style = MaterialTheme.typography.labelSmall, color = Color.White)
                Spacer(modifier = Modifier.width(20.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c2))
                Spacer(modifier = Modifier.width(6.dp))
                Text(l2, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

data class Tuple6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Daily/Weekly Transaction Trend Line/Area Chart in Pure Compose
 */
@Composable
fun ZenoTrendLineChart(
    title: String,
    labels: List<String>,
    values: List<Double>,
    lineColor: Color = Emerald400,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                val sum = values.sum()
                Text("Total: ${formatCurrency(sum)}", style = MaterialTheme.typography.labelSmall, color = lineColor)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (values.isEmpty() || values.all { it == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No trend data recorded for this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val maxVal = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val stepX = width / (values.size - 1).coerceAtLeast(1)

                    val path = Path()
                    val fillPath = Path()

                    values.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = height - ((v / maxVal) * (height - 20.dp.toPx())).toFloat() - 10.dp.toPx()

                        if (idx == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    // Draw gradient underneath trend line
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )

                    // Draw main line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw points
                    values.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = height - ((v / maxVal) * (height - 20.dp.toPx())).toFloat() - 10.dp.toPx()
                        drawCircle(color = Navy900, radius = 5.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X-Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    labels.forEach { l ->
                        Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
