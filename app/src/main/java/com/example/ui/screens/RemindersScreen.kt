package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun RemindersScreen(
    reminders: List<PaymentReminder>,
    customers: List<Party>,
    suppliers: List<Party>,
    onAddReminder: (PaymentReminder) -> Unit,
    onSettleReminder: (PaymentReminder) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val pendingCount = remember(reminders) {
        reminders.count { it.status == ReminderStatus.PENDING || it.status == ReminderStatus.OVERDUE }
    }
    val totalPendingAmount = remember(reminders) {
        reminders.filter { it.status != ReminderStatus.SETTLED }.sumOf { it.amount }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("add_reminder_fab")
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = "New Reminder")
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
            // Reminders Summary Card
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
                    Column {
                        Text("Payment Reminders & Due Dates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("$pendingCount Pending follow-up(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = formatCurrency(totalPendingAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber500
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (reminders.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.NotificationsNone,
                    title = "No Payment Reminders",
                    description = "Add reminders to track customer receivables or supplier dues.",
                    actionButtonText = "+ Add First Reminder",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(reminders, key = { it.id }) { r ->
                        val isOverdue = r.dueDate < System.currentTimeMillis() && r.status != ReminderStatus.SETTLED
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
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        r.status == ReminderStatus.SETTLED -> Emerald400
                                                        isOverdue -> ErrorRed
                                                        else -> Amber500
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = r.partyName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = r.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(r.amount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (r.type == PartyType.CUSTOMER) Emerald400 else ErrorRed
                                        )
                                        StatusBadge(
                                            text = when {
                                                r.status == ReminderStatus.SETTLED -> "SETTLED"
                                                isOverdue -> "OVERDUE"
                                                else -> "PENDING"
                                            },
                                            isSuccess = r.status == ReminderStatus.SETTLED,
                                            isWarning = !isOverdue && r.status != ReminderStatus.SETTLED
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Due: ${formatDate(r.dueDate)} • ${r.type}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (r.status != ReminderStatus.SETTLED) {
                                        Button(
                                            onClick = { onSettleReminder(r.copy(status = ReminderStatus.SETTLED)) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Mark Settled", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            customers = customers,
            suppliers = suppliers,
            onDismiss = { showAddDialog = false },
            onConfirm = { newRem ->
                onAddReminder(newRem)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddReminderDialog(
    customers: List<Party>,
    suppliers: List<Party>,
    onDismiss: () -> Unit,
    onConfirm: (PaymentReminder) -> Unit
) {
    var partyType by remember { mutableStateOf(PartyType.CUSTOMER) }
    val parties = if (partyType == PartyType.CUSTOMER) customers else suppliers
    var selectedParty by remember { mutableStateOf(parties.firstOrNull()) }
    var title by remember { mutableStateOf("Pending payment collection") }
    var amountText by remember { mutableStateOf(selectedParty?.outstandingAmount?.takeIf { it > 0 }?.toString() ?: "10000") }
    var daysDue by remember { mutableStateOf("3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Payment Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = partyType == PartyType.CUSTOMER,
                        onClick = {
                            partyType = PartyType.CUSTOMER
                            selectedParty = customers.firstOrNull()
                        },
                        label = { Text("Customer") }
                    )
                    FilterChip(
                        selected = partyType == PartyType.SUPPLIER,
                        onClick = {
                            partyType = PartyType.SUPPLIER
                            selectedParty = suppliers.firstOrNull()
                        },
                        label = { Text("Supplier") }
                    )
                }

                Text("Select Party:", style = MaterialTheme.typography.labelSmall)
                parties.take(4).forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedParty?.id == p.id, onClick = {
                            selectedParty = p
                            if (p.outstandingAmount > 0) amountText = p.outstandingAmount.toString()
                        })
                        Text("${p.name} (Due: ₹${p.outstandingAmount})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Note / Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = daysDue,
                    onValueChange = { daysDue = it },
                    label = { Text("Due in (Days)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = selectedParty ?: return@Button
                    val amt = amountText.toDoubleOrNull() ?: return@Button
                    val days = daysDue.toLongOrNull() ?: 3L

                    onConfirm(
                        PaymentReminder(
                            title = title.trim(),
                            partyId = p.id,
                            partyName = p.name,
                            amount = amt,
                            dueDate = System.currentTimeMillis() + days * 86400000L,
                            type = partyType,
                            status = ReminderStatus.PENDING
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Save Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
