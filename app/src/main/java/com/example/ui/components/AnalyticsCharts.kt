package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.CategorySlice
import com.example.data.analytics.TimeSeriesPoint
import com.example.ui.theme.*
import java.util.Locale

val ChartColorPalette = listOf(
    Emerald500,
    Amber500,
    InfoBlue,
    PurpleAccent,
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
    Color(0xFF84CC16)
)

/**
 * Interactive Donut / Pie Chart.
 * Displays slice percentage, category name, ₹ amounts, and interactive tap/hover detail card.
 */
@Composable
fun InteractiveDonutChart(
    title: String,
    slices: List<CategorySlice>,
    isDonut: Boolean = true,
    emptyMessage: String = "No transaction data for this period",
    onCategoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val totalAmount = remember(slices) { slices.sumOf { it.amount } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chart_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                )
                if (totalAmount > 0) {
                    Text(
                        text = "Total: ${formatCurrency(totalAmount)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald400
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (slices.isEmpty() || totalAmount == 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
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
                                .pointerInput(slices) {
                                    detectTapGestures { offset ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = offset.x - center.x
                                        val dy = offset.y - center.y
                                        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        if (angle < 0) angle += 360f

                                        // Find which slice covers this angle
                                        var currentAngle = -90f
                                        if (currentAngle < 0) currentAngle += 360f

                                        var accumulated = 0f
                                        var found = false
                                        for (i in slices.indices) {
                                            val sweep = (slices[i].amount / totalAmount * 360f).toFloat()
                                            // Angle relative to -90 deg
                                            val normalizedTap = (angle + 90f) % 360f
                                            if (normalizedTap >= accumulated && normalizedTap <= accumulated + sweep) {
                                                selectedIndex = if (selectedIndex == i) null else i
                                                found = true
                                                onCategoryClick?.invoke(slices[i].category)
                                                break
                                            }
                                            accumulated += sweep
                                        }
                                        if (!found) selectedIndex = null
                                    }
                                }
                        ) {
                            var startAngle = -90f
                            val strokeWidth = if (isDonut) 32.dp.toPx() else 0f

                            slices.forEachIndexed { index, slice ->
                                val sweepAngle = (slice.amount / totalAmount * 360f).toFloat()
                                val color = ChartColorPalette[index % ChartColorPalette.size]
                                val isSelected = selectedIndex == index

                                if (isDonut) {
                                    drawArc(
                                        color = if (isSelected) color.copy(alpha = 1f) else color.copy(alpha = 0.85f),
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle - if (slices.size > 1) 2f else 0f,
                                        useCenter = false,
                                        style = Stroke(
                                            width = if (isSelected) strokeWidth * 1.2f else strokeWidth,
                                            cap = StrokeCap.Round
                                        )
                                    )
                                } else {
                                    drawArc(
                                        color = if (isSelected) color else color.copy(alpha = 0.85f),
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = true
                                    )
                                }
                                startAngle += sweepAngle
                            }
                        }

                        // Center content of Donut
                        if (isDonut) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val activeSlice = selectedIndex?.let { slices.getOrNull(it) }
                                if (activeSlice != null) {
                                    Text(
                                        text = "${String.format(Locale.ENGLISH, "%.1f", activeSlice.percentage)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = activeSlice.category.take(12),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Emerald400,
                                        maxLines = 1
                                    )
                                } else {
                                    Text(
                                        text = "100%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${slices.size} Items",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Legends Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        slices.take(5).forEachIndexed { index, slice ->
                            val color = ChartColorPalette[index % ChartColorPalette.size]
                            val isSelected = selectedIndex == index

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFFE2E8F0) else Color.Transparent)
                                    .clickable {
                                        selectedIndex = if (selectedIndex == index) null else index
                                        onCategoryClick?.invoke(slice.category)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = slice.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = formatCurrency(slice.amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Hover Detail Card
            selectedIndex?.let { idx ->
                val slice = slices.getOrNull(idx)
                if (slice != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = slice.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${String.format(Locale.ENGLISH, "%.1f", slice.percentage)}% of total • ${slice.count} records",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatCurrency(slice.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Dual Bar / Comparison Chart.
 * Compares Sales vs Purchases or Income vs Expenses across time points.
 */
@Composable
fun InteractiveBarChart(
    title: String,
    points: List<TimeSeriesPoint>,
    series1Label: String = "Sales",
    series2Label: String = "Purchases",
    series1Color: Color = Emerald500,
    series2Color: Color = Amber500,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val maxVal = remember(points) {
        maxOf(1.0, points.maxOfOrNull { maxOf(it.sales, it.purchase, it.income, it.expense) } ?: 1.0)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bar_chart_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(series1Color))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(series1Label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(series2Color))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(series2Label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bars Container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                points.forEachIndexed { index, pt ->
                    val val1 = if (series1Label == "Sales") pt.sales else pt.income
                    val val2 = if (series2Label == "Purchases") pt.purchase else pt.expense

                    val height1Percent = (val1 / maxVal).coerceIn(0.04, 1.0).toFloat()
                    val height2Percent = (val2 / maxVal).coerceIn(0.04, 1.0).toFloat()

                    val isSelected = selectedPointIndex == index

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedPointIndex = if (selectedPointIndex == index) null else index
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            // Bar 1
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(height1Percent)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) series1Color else series1Color.copy(alpha = 0.8f))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Bar 2
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(height2Percent)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) series2Color else series2Color.copy(alpha = 0.8f))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Interactive Point Details
            selectedPointIndex?.let { idx ->
                val pt = points.getOrNull(idx)
                if (pt != null) {
                    val val1 = if (series1Label == "Sales") pt.sales else pt.income
                    val val2 = if (series2Label == "Purchases") pt.purchase else pt.expense
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Period: ${pt.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(series1Label, style = MaterialTheme.typography.labelSmall, color = series1Color)
                                    Text(formatCurrency(val1), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(series2Label, style = MaterialTheme.typography.labelSmall, color = series2Color)
                                    Text(formatCurrency(val2), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Line Chart for Trend Analysis (Cash Flow, Net Profit).
 */
@Composable
fun InteractiveTrendLineChart(
    title: String,
    points: List<TimeSeriesPoint>,
    valueExtractor: (TimeSeriesPoint) -> Double,
    lineColor: Color = Emerald400,
    emptyMessage: String = "No trend data recorded",
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxVal = remember(points) {
        val vals = points.map(valueExtractor)
        maxOf(1.0, vals.maxOrNull() ?: 1.0)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trend_chart_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryLight
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(points) {
                                detectTapGestures { offset ->
                                    val step = size.width / (points.size - 1).coerceAtLeast(1)
                                    val idx = ((offset.x + step / 2) / step).toInt().coerceIn(0, points.size - 1)
                                    selectedIndex = if (selectedIndex == idx) null else idx
                                }
                            }
                    ) {
                        val path = Path()
                        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

                        points.forEachIndexed { index, pt ->
                            val v = valueExtractor(pt)
                            val normY = size.height - (v / maxVal * (size.height - 30.dp.toPx())).toFloat() - 15.dp.toPx()
                            val x = index * stepX

                            if (index == 0) path.moveTo(x, normY) else path.lineTo(x, normY)

                            // Point circle
                            val isSelected = selectedIndex == index
                            drawCircle(
                                color = if (isSelected) Amber500 else lineColor,
                                radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                                center = Offset(x, normY)
                            )
                        }

                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // X-Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEachIndexed { idx, pt ->
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedIndex == idx) Amber500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Detail Card
            selectedIndex?.let { idx ->
                val pt = points.getOrNull(idx)
                if (pt != null) {
                    val value = valueExtractor(pt)
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pt.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(formatCurrency(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = lineColor)
                        }
                    }
                }
            }
        }
    }
}
