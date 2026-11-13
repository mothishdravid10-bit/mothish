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
import kotlin.math.abs

data class JournalDraftLine(
    val account: Account,
    val isDebit: Boolean,
    val amount: Double,
    val narration: String = ""
)

@Composable
fun JournalScreen(
    journalEntries: List<JournalEntry>,
    accounts: List<Account>,
    onCreateJournalEntry: (JournalEntry, List<JournalEntryLine>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredEntries = remember(journalEntries, searchQuery) {
        journalEntries.filter { j ->
            searchQuery.isEmpty() ||
                    j.entryNumber.contains(searchQuery, ignoreCase = true) ||
                    j.narration.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("create_journal_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Journal Entry")
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by voucher # or narration...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredEntries.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.MenuBook,
                    title = "No Journal Entries",
                    description = "Record custom double-entry balanced transactions.",
                    actionButtonText = "+ Create Journal Entry",
                    onActionClick = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.entryNumber,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Emerald400
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        StatusBadge(
                                            text = entry.sourceModule,
                                            isSuccess = true
                                        )
                                    }
                                    Text(
                                        text = formatDate(entry.date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = entry.narration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "Dr: ${formatCurrency(entry.totalDebit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Emerald400
                                        )
                                        Text(
                                            text = "Cr: ${formatCurrency(entry.totalCredit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Emerald400
                                        )
                                    }
                                    StatusBadge(text = "BALANCED", isSuccess = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateJournalDialog(
            accounts = accounts,
            onDismiss = { showCreateDialog = false },
            onConfirm = { entry, lines ->
                onCreateJournalEntry(entry, lines)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreateJournalDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (JournalEntry, List<JournalEntryLine>) -> Unit
) {
    var narration by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var isDebit by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("1000") }

    val draftLines = remember { mutableStateListOf<JournalDraftLine>() }

    val totalDebit = draftLines.filter { it.isDebit }.sumOf { it.amount }
    val totalCredit = draftLines.filter { !it.isDebit }.sumOf { it.amount }
    val difference = abs(totalDebit - totalCredit)
    val isBalanced = draftLines.isNotEmpty() && difference < 0.01

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Double-Entry Journal", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = narration,
                        onValueChange = { narration = it },
                        label = { Text("Narration / Purpose *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add Journal Line:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            // Quick account selector
                            Text("Account:", style = MaterialTheme.typography.labelSmall)
                            accounts.take(5).forEach { acc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedAccount?.id == acc.id, onClick = { selectedAccount = acc })
                                    Text("${acc.code} - ${acc.name}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isDebit,
                                    onClick = { isDebit = true },
                                    label = { Text("DEBIT (Dr)") }
                                )
                                FilterChip(
                                    selected = !isDebit,
                                    onClick = { isDebit = false },
                                    label = { Text("CREDIT (Cr)") }
                                )
                            }

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount (₹)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    val acc = selectedAccount ?: return@Button
                                    val amt = amountText.toDoubleOrNull() ?: return@Button
                                    if (amt <= 0) return@Button

                                    draftLines.add(JournalDraftLine(account = acc, isDebit = isDebit, amount = amt))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Line")
                            }
                        }
                    }
                }

                if (draftLines.isNotEmpty()) {
                    item {
                        Text("Draft Lines (${draftLines.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        draftLines.forEachIndexed { idx, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (line.isDebit) "Dr" else "Cr"} ${line.account.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatCurrency(line.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (line.isDebit) Emerald400 else Amber500
                                )
                                IconButton(onClick = { draftLines.removeAt(idx) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Balancing Verification Box
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isBalanced) Navy800 else Navy900),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Debits:", style = MaterialTheme.typography.bodySmall)
                                Text(formatCurrency(totalDebit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Credits:", style = MaterialTheme.typography.bodySmall)
                                Text(formatCurrency(totalCredit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Balance Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                StatusBadge(
                                    text = if (isBalanced) "BALANCED" else "UNBALANCED (Diff: ${formatCurrency(difference)})",
                                    isSuccess = isBalanced
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isBalanced && narration.isNotBlank()) {
                        val entryNum = "JRN-${System.currentTimeMillis() % 100000}"
                        val entry = JournalEntry(
                            entryNumber = entryNum,
                            date = System.currentTimeMillis(),
                            reference = reference.trim(),
                            narration = narration.trim(),
                            totalDebit = totalDebit,
                            totalCredit = totalCredit,
                            isAutoGenerated = false,
                            sourceModule = "MANUAL"
                        )
                        val lines = draftLines.map {
                            JournalEntryLine(
                                journalEntryId = 0,
                                accountId = it.account.id,
                                accountName = it.account.name,
                                isDebit = it.isDebit,
                                amount = it.amount,
                                description = narration
                            )
                        }
                        onConfirm(entry, lines)
                    }
                },
                enabled = isBalanced && narration.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Post Balanced Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
