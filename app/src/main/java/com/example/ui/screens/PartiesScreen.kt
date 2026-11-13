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
fun PartiesScreen(
    parties: List<Party>,
    initialType: PartyType = PartyType.CUSTOMER,
    onAddParty: (Party) -> Unit,
    onNavigateToVoucher: (Party) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingStatementParty by remember { mutableStateOf<Party?>(null) }

    val filteredParties = remember(parties, selectedType, searchQuery) {
        parties.filter { p ->
            p.type == selectedType &&
                    (searchQuery.isEmpty() || p.name.contains(searchQuery, ignoreCase = true) || p.gstin.contains(searchQuery, ignoreCase = true) || p.phone.contains(searchQuery))
        }
    }

    val totalOutstanding = remember(filteredParties) {
        filteredParties.sumOf { it.outstandingAmount }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("add_party_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add New Party")
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
            // Type Toggle Tabs
            TabRow(
                selectedTabIndex = if (selectedType == PartyType.CUSTOMER) 0 else 1,
                containerColor = SurfaceWhite,
                contentColor = Emerald400,
                divider = {}
            ) {
                Tab(
                    selected = selectedType == PartyType.CUSTOMER,
                    onClick = { selectedType = PartyType.CUSTOMER },
                    text = {
                        Text(
                            "Customers (Debtors)",
                            fontWeight = if (selectedType == PartyType.CUSTOMER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedType == PartyType.SUPPLIER,
                    onClick = { selectedType = PartyType.SUPPLIER },
                    text = {
                        Text(
                            "Suppliers (Creditors)",
                            fontWeight = if (selectedType == PartyType.SUPPLIER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name, GSTIN or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Outstanding Summary Header
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
                        text = if (selectedType == PartyType.CUSTOMER) "Total Receivables:" else "Total Payables:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(totalOutstanding),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedType == PartyType.CUSTOMER) Emerald400 else ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parties List
            if (filteredParties.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.PeopleOutline,
                    title = "No ${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }}s Found",
                    description = "Tap the + button to add a new ${selectedType.name.lowercase()}.",
                    actionButtonText = "+ Add ${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredParties, key = { it.id }) { party ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = party.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (party.gstin.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "GSTIN: ${party.gstin}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Emerald400,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(party.outstandingAmount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (party.outstandingAmount > 0) {
                                                if (party.type == PartyType.CUSTOMER) Emerald400 else ErrorRed
                                            } else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (party.outstandingAmount > 0) "Outstanding" else "Settled",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (party.phone.isNotBlank() || party.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = BorderLight)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${party.phone} • ${party.stateName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = { viewingStatementParty = party },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Statement", style = MaterialTheme.typography.labelSmall, color = InfoBlue)
                                            }
                                            FilledTonalButton(
                                                onClick = { onNavigateToVoucher(party) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Emerald600)
                                            ) {
                                                Text(
                                                    if (party.type == PartyType.CUSTOMER) "Receive ₹" else "Pay ₹",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
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
    }

    if (showAddDialog) {
        AddPartyDialog(
            defaultType = selectedType,
            onDismiss = { showAddDialog = false },
            onConfirm = { newParty ->
                onAddParty(newParty)
                showAddDialog = false
            }
        )
    }

    if (viewingStatementParty != null) {
        PartyStatementDialog(
            party = viewingStatementParty!!,
            onDismiss = { viewingStatementParty = null }
        )
    }
}

@Composable
fun AddPartyDialog(
    defaultType: PartyType,
    onDismiss: () -> Unit,
    onConfirm: (Party) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(defaultType) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var stateCode by remember { mutableStateOf("27") }
    var openingBalance by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${type.name.lowercase().replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Business / Person Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN (e.g. 27AAAAA0000A1Z5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // OutlinedTextField(
                //     value = stateCode,
                //     onValueChange = { stateCode = it },
                //     label = { Text("State Code (27)") },
                //     singleLine = true,
                //     modifier = Modifier.weight(1f)
                // )
                // }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Billing Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("Opening Outstanding Balance (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val ob = openingBalance.toDoubleOrNull() ?: 0.0
                        onConfirm(
                            Party(
                                name = name.trim(),
                                type = type,
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                gstin = gstin.trim(),
                                stateCode = "27",
                                stateName = "Maharashtra",
                                openingBalance = ob,
                                outstandingAmount = ob
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Save Party")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PartyStatementDialog(
    party: Party,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(party.name, fontWeight = FontWeight.Bold)
                Text("${party.type} Ledger Statement", style = MaterialTheme.typography.labelSmall, color = Emerald400)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("GSTIN: ${party.gstin.ifEmpty { "Unregistered" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Address: ${party.address.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Phone: ${party.phone.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Opening Balance:")
                    Text(formatCurrency(party.openingBalance), fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Outstanding:")
                    Text(formatCurrency(party.outstandingAmount), fontWeight = FontWeight.Bold, color = Emerald400)
                }
                Text("All posted invoices, bills and payments for this party are reflected in the General Ledger.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) {
                Text("Close")
            }
        }
    )
}
