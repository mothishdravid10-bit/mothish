package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AssistantQueryResult
import com.example.ai.ZenoIntent
import com.example.ui.theme.*

@Composable
fun AssistantQueryResultCard(
    result: AssistantQueryResult,
    modifier: Modifier = Modifier,
    onSpeakClick: (() -> Unit)? = null,
    onDismissClick: (() -> Unit)? = null,
    onOpenDashboard: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, Emerald300.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Emerald600,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = result.effectiveIntent.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Emerald600
                    )
                    if (result.dateRange != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge(text = result.dateRange.label.uppercase(), isSuccess = true)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSpeakClick != null) {
                        IconButton(
                            onClick = onSpeakClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("zeno_tts_replay_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak Assistant Reply",
                                tint = Emerald600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onDismissClick != null) {
                        IconButton(
                            onClick = onDismissClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Query Results",
                                tint = TextSecondaryLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = result.displayReply,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryLight
            )

            // Summary metrics for period/report intents
            when {
                result.cashBalance != null && result.bankBalance != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip("Cash", formatCurrency(result.cashBalance), Emerald600, Modifier.weight(1f))
                        SummaryChip("Bank", formatCurrency(result.bankBalance), InfoBlue, Modifier.weight(1f))
                        SummaryChip("Total", formatCurrency(result.netAmount), TextPrimaryLight, Modifier.weight(1f))
                    }
                }

                result.totalReceivables != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip("Receivable", formatCurrency(result.totalReceivables), Emerald600, Modifier.weight(1f))
                        SummaryChip("Payable", formatCurrency(result.totalPayables ?: 0.0), ErrorRed, Modifier.weight(1f))
                    }
                }

                result.effectiveIntent in listOf(
                    ZenoIntent.VIEW_TRANSACTIONS,
                    ZenoIntent.VIEW_INCOME,
                    ZenoIntent.VIEW_EXPENSES,
                    ZenoIntent.PROFIT_LOSS
                ) -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip("Income", formatCurrency(result.totalIncome), Emerald600, Modifier.weight(1f))
                        SummaryChip("Expenses", formatCurrency(result.totalExpenses), Amber600, Modifier.weight(1f))
                        SummaryChip(
                            label = if (result.netAmount >= 0) "Net +" else "Net -",
                            value = formatCurrency(kotlin.math.abs(result.netAmount)),
                            color = if (result.netAmount >= 0) Emerald600 else ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (result.transactions.isNotEmpty()) {
                HorizontalDivider(color = BorderLight)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.transactions.take(8).forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.partyOrTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dateStr = if (tx.dateMillis > 0 && tx.type !in listOf("RECEIVABLE", "PAYABLE")) {
                                    " • ${formatDate(tx.dateMillis)}"
                                } else ""
                                Text(
                                    text = "${tx.type} • ${tx.paymentMode}$dateStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryLight
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatCurrency(tx.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.category == "INCOME") Emerald600 else if (tx.category == "EXPENSE") ErrorRed else TextPrimaryLight
                            )
                        }
                    }
                    if (result.transactions.size > 8) {
                        Text(
                            text = "+ ${result.transactions.size - 8} more record(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryLight
                        )
                    }
                }
            } else if (result.accountsSummary.isNotEmpty()) {
                HorizontalDivider(color = BorderLight)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.accountsSummary.take(6).forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${acc.code} • ${acc.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryLight,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatCurrency(acc.currentBalance),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                        }
                    }
                }
            }

            if (onOpenDashboard != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenDashboard) {
                        Text("View on Dashboard", color = Emerald600, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
