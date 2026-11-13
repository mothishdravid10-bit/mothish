package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun GstScreen(
    invoices: List<SalesInvoice>,
    bills: List<PurchaseBill>,
    settings: CompanySettings
) {
    // 1. Output Tax (Sales)
    val outwardTaxable = remember(invoices) { invoices.sumOf { it.subtotal } }
    val outputCgst = remember(invoices) { invoices.sumOf { it.cgst } }
    val outputSgst = remember(invoices) { invoices.sumOf { it.sgst } }
    val outputIgst = remember(invoices) { invoices.sumOf { it.igst } }
    val totalOutputTax = outputCgst + outputSgst + outputIgst

    // 2. Input Tax Credit (Purchases)
    val inwardTaxable = remember(bills) { bills.sumOf { it.subtotal } }
    val inputCgst = remember(bills) { bills.sumOf { it.cgst } }
    val inputSgst = remember(bills) { bills.sumOf { it.sgst } }
    val inputIgst = remember(bills) { bills.sumOf { it.igst } }
    val totalInputCredit = inputCgst + inputSgst + inputIgst

    // 3. Net GST Payable after set-off
    val netCgst = maxOf(0.0, outputCgst - inputCgst)
    val netSgst = maxOf(0.0, outputSgst - inputSgst)
    val netIgst = maxOf(0.0, outputIgst - inputIgst)
    val netTotalPayable = maxOf(0.0, totalOutputTax - totalInputCredit)

    // Interactive GST Calculator state
    var calcAmount by remember { mutableStateOf("10000") }
    var calcGstRate by remember { mutableStateOf(18.0) }
    var calcIsInclusive by remember { mutableStateOf(false) }

    val baseAmount = remember(calcAmount, calcGstRate, calcIsInclusive) {
        val amt = calcAmount.toDoubleOrNull() ?: 0.0
        if (calcIsInclusive) {
            amt / (1 + (calcGstRate / 100.0))
        } else {
            amt
        }
    }
    val taxAmount = remember(baseAmount, calcGstRate) {
        baseAmount * (calcGstRate / 100.0)
    }
    val totalCalculated = baseAmount + taxAmount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // GSTIN Profile Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Indian GST Compliance Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("GSTIN: ${settings.gstin} • State: ${settings.stateName} (${settings.stateCode})", style = MaterialTheme.typography.labelSmall, color = Emerald400)
                        }
                        StatusBadge(text = "GSTR-3B READY", isSuccess = true)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("NET GST PAYABLE (CASH/CREDIT)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCurrency(netTotalPayable), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (netTotalPayable > 0) Amber500 else Emerald400)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL ITC CLAIMABLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCurrency(totalInputCredit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                        }
                    }
                }
            }
        }

        // Tax Breakdown Cards
        item {
            Text("Tax Breakdown (CGST / SGST / IGST)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Component", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Output Liability", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Input Credit (ITC)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Net Payable", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = BorderLight)

                    // CGST
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CGST (Central)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(formatCurrency(outputCgst), style = MaterialTheme.typography.bodySmall)
                        Text(formatCurrency(inputCgst), style = MaterialTheme.typography.bodySmall, color = Emerald400)
                        Text(formatCurrency(netCgst), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Amber500)
                    }

                    // SGST
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SGST (State)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(formatCurrency(outputSgst), style = MaterialTheme.typography.bodySmall)
                        Text(formatCurrency(inputSgst), style = MaterialTheme.typography.bodySmall, color = Emerald400)
                        Text(formatCurrency(netSgst), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Amber500)
                    }

                    // IGST
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IGST (Inter-state)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(formatCurrency(outputIgst), style = MaterialTheme.typography.bodySmall)
                        Text(formatCurrency(inputIgst), style = MaterialTheme.typography.bodySmall, color = Emerald400)
                        Text(formatCurrency(netIgst), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Amber500)
                    }

                    HorizontalDivider(color = BorderLight)

                    // Total
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL TAX", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatCurrency(totalOutputTax), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatCurrency(totalInputCredit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                        Text(formatCurrency(netTotalPayable), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Amber500)
                    }
                }
            }
        }

        // Interactive GST Calculation Tool
        item {
            Text("GST Rate Calculator (0%, 5%, 12%, 18%, 28%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = calcAmount,
                        onValueChange = { calcAmount = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Inclusive of GST:", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = calcIsInclusive, onCheckedChange = { calcIsInclusive = it })
                    }

                    // Rate selection chips
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { rate ->
                            FilterChip(
                                selected = calcGstRate == rate,
                                onClick = { calcGstRate = rate },
                                label = { Text("${rate.toInt()}%") }
                            )
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    // Results
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable Base Amount:", style = MaterialTheme.typography.bodySmall)
                        Text(formatCurrency(baseAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GST Tax Amount (${calcGstRate.toInt()}%):", style = MaterialTheme.typography.bodySmall)
                        Text(formatCurrency(taxAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total Amount:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatCurrency(totalCalculated), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
            }
        }
    }
}
