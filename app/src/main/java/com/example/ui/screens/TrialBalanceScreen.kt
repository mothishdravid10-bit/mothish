package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun TrialBalanceScreen(
    accounts: List<Account>
) {
    // Assets & Expenses generally have debit balances; Liabilities, Equity & Income credit balances
    val trialItems = remember(accounts) {
        accounts.map { acc ->
            val isDebitNature = acc.category == AccountCategory.ASSET || acc.category == AccountCategory.EXPENSE
            val bal = acc.currentBalance
            val dr = if (isDebitNature) maxOf(0.0, bal) else if (bal < 0) abs(bal) else 0.0
            val cr = if (!isDebitNature) maxOf(0.0, bal) else if (bal < 0) abs(bal) else 0.0
            TrialBalanceRow(
                code = acc.code,
                name = acc.name,
                category = acc.category,
                debit = dr,
                credit = cr
            )
        }
    }

    val totalDebit = remember(trialItems) { trialItems.sumOf { it.debit } }
    val totalCredit = remember(trialItems) { trialItems.sumOf { it.credit } }
    val isBalanced = remember(totalDebit, totalCredit) { abs(totalDebit - totalCredit) < 1.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trial Balance Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Financial Year 2026-2027",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(
                        text = if (isBalanced) "BOOKS BALANCED" else "TRIAL DIFFERENCE",
                        isSuccess = isBalanced
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("TOTAL DEBITS (Dr)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(totalDebit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL CREDITS (Cr)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(totalCredit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Table Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ACCOUNT PARTICULARS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.8f))
                Text("DEBIT (₹)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("CREDIT (₹)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Table Rows
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(trialItems, key = { it.code }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.8f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.code} • ${item.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = if (item.debit > 0) formatCurrency(item.debit) else "-",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (item.debit > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.debit > 0) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = if (item.credit > 0) formatCurrency(item.credit) else "-",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (item.credit > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.credit > 0) Amber500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

data class TrialBalanceRow(
    val code: String,
    val name: String,
    val category: AccountCategory,
    val debit: Double,
    val credit: Double
)
