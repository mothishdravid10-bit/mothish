package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun VouchersScreen(
    vouchers: List<PaymentVoucher>,
    customers: List<Party>,
    suppliers: List<Party>,
    accounts: List<Account>,
    settings: CompanySettings,
    initialParty: Party? = null,
    onRecordVoucher: (PaymentVoucher) -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (initialParty?.type == PartyType.SUPPLIER) VoucherType.PAYMENT else VoucherType.RECEIPT) }
    var showDialog by remember { mutableStateOf(initialParty != null) }

    val filteredVouchers = remember(vouchers, selectedTab) {
        vouchers.filter { it.type == selectedTab }
    }

    val totalAmount = remember(filteredVouchers) { filteredVouchers.sumOf { it.amount } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("add_voucher_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Voucher")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            TabRow(
                selectedTabIndex = if (selectedTab == VoucherType.RECEIPT) 0 else 1,
                containerColor = SurfaceWhite,
                contentColor = Emerald400,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == VoucherType.RECEIPT,
                    onClick = { selectedTab = VoucherType.RECEIPT },
                    text = { Text("Receipts (Collections)", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == VoucherType.PAYMENT,
                    onClick = { selectedTab = VoucherType.PAYMENT },
                    text = { Text("Payments (Disbursements)", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == VoucherType.RECEIPT) "Total Collections:" else "Total Disbursements:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == VoucherType.RECEIPT) Emerald400 else ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredVouchers.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Payments,
                    title = "No ${selectedTab.name.lowercase().replaceFirstChar { it.uppercase() }}s Recorded",
                    description = "Record incoming customer payments or outgoing supplier disbursements.",
                    actionButtonText = "+ Record ${selectedTab.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    onActionClick = { showDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredVouchers, key = { it.id }) { v ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusBadge(
                                            text = v.voucherNumber,
                                            isSuccess = v.type == VoucherType.RECEIPT
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = v.partyName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatDate(v.date)} • Mode: ${v.paymentMode} (${v.accountName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (v.notes.isNotBlank()) {
                                        Text(
                                            text = v.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = formatCurrency(v.amount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (v.type == VoucherType.RECEIPT) Emerald400 else ErrorRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        RecordVoucherDialog(
            type = selectedTab,
            initialParty = initialParty,
            customers = customers,
            suppliers = suppliers,
            bankAccounts = accounts.filter { it.category == AccountCategory.ASSET && (it.subcategory == "Bank Accounts" || it.code in listOf("1010", "1020", "1030")) },
            prefix = if (selectedTab == VoucherType.RECEIPT) settings.receiptPrefix else settings.paymentPrefix,
            onDismiss = { showDialog = false },
            onConfirm = { voucher ->
                onRecordVoucher(voucher)
                showDialog = false
            }
        )
    }
}

@Composable
fun RecordVoucherDialog(
    type: VoucherType,
    initialParty: Party?,
    customers: List<Party>,
    suppliers: List<Party>,
    bankAccounts: List<Account>,
    prefix: String,
    onDismiss: () -> Unit,
    onConfirm: (PaymentVoucher) -> Unit
) {
    val parties = if (type == VoucherType.RECEIPT) customers else suppliers
    var selectedParty by remember { mutableStateOf(initialParty ?: parties.firstOrNull()) }
    var selectedAccount by remember { mutableStateOf(bankAccounts.firstOrNull()) }
    var amountText by remember { mutableStateOf(selectedParty?.outstandingAmount?.takeIf { it > 0 }?.toString() ?: "5000") }
    var paymentMode by remember { mutableStateOf("BANK") }
    var referenceText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record ${type.name.lowercase().replaceFirstChar { it.uppercase() }} Voucher", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (type == VoucherType.RECEIPT) "Received From Party:" else "Paid To Vendor:",
                    style = MaterialTheme.typography.labelMedium
                )
                parties.take(4).forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = selectedParty?.id == p.id, onClick = {
                            selectedParty = p
                            if (p.outstandingAmount > 0) amountText = p.outstandingAmount.toString()
                        })
                        Text("${p.name} (Due: ₹${p.outstandingAmount})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("Payment Channel (Debit/Credit Account):", style = MaterialTheme.typography.labelMedium)
                bankAccounts.forEach { acc ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = selectedAccount?.id == acc.id, onClick = { selectedAccount = acc })
                        Text("${acc.name} (Bal: ₹${acc.currentBalance})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Payment Mode:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BANK", "CASH", "UPI", "CHEQUE").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = referenceText,
                    onValueChange = { referenceText = it },
                    label = { Text("Reference / UTR / Cheque #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = selectedParty ?: return@Button
                    val acc = selectedAccount ?: return@Button
                    val amt = amountText.toDoubleOrNull() ?: return@Button
                    if (amt <= 0) return@Button

                    val vNum = "$prefix${System.currentTimeMillis() % 10000}"
                    onConfirm(
                        PaymentVoucher(
                            voucherNumber = vNum,
                            type = type,
                            partyId = p.id,
                            partyName = p.name,
                            accountId = acc.id,
                            accountName = acc.name,
                            amount = amt,
                            paymentMode = paymentMode,
                            date = System.currentTimeMillis(),
                            reference = referenceText.trim(),
                            notes = notesText.trim()
                        )
                    )
                },
                enabled = selectedParty != null && selectedAccount != null,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Post Voucher")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
