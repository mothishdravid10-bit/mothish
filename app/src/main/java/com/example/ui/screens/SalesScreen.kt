package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SalesScreen(
    invoices: List<SalesInvoice>,
    customers: List<Party>,
    products: List<Product>,
    settings: CompanySettings,
    onCreateInvoice: (SalesInvoice, List<SalesInvoiceItem>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedInvoiceForPrint by remember { mutableStateOf<SalesInvoice?>(null) }

    val filteredInvoices = remember(invoices, searchQuery) {
        invoices.filter { inv ->
            searchQuery.isEmpty() ||
                    inv.customerName.contains(searchQuery, ignoreCase = true) ||
                    inv.invoiceNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalSalesValue = remember(filteredInvoices) { filteredInvoices.sumOf { it.totalAmount } }
    val totalTaxCollected = remember(filteredInvoices) { filteredInvoices.sumOf { it.cgst + it.sgst + it.igst } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("create_invoice_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Sales Invoice")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search invoices by customer or #...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Invoiced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatCurrency(totalSalesValue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Output GST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatCurrency(totalTaxCollected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Amber500)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredInvoices.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Receipt,
                    title = "No Invoices Found",
                    description = "Create a GST-compliant sales invoice by tapping the + button.",
                    actionButtonText = "+ Create First Invoice",
                    onActionClick = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredInvoices, key = { it.id }) { inv ->
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = inv.invoiceNumber,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Emerald400
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            StatusBadge(
                                                text = inv.status.name,
                                                isSuccess = inv.status == InvoiceStatus.PAID
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = inv.customerName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatDate(inv.date)} • Mode: ${inv.paymentMode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(inv.totalAmount),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (inv.isInterstate) "IGST: ${formatCurrency(inv.igst)}" else "GST: ${formatCurrency(inv.cgst + inv.sgst)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (inv.isInterstate) "Inter-state Sale" else "Intra-state (CGST+SGST)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedButton(
                                        onClick = { selectedInvoiceForPrint = inv },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Print GST Invoice", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateInvoiceDialog(
            customers = customers,
            products = products,
            invoicePrefix = settings.invoicePrefix,
            onDismiss = { showCreateDialog = false },
            onConfirm = { inv, items ->
                onCreateInvoice(inv, items)
                showCreateDialog = false
            }
        )
    }

    if (selectedInvoiceForPrint != null) {
        PrintInvoiceDialog(
            invoice = selectedInvoiceForPrint!!,
            settings = settings,
            onDismiss = { selectedInvoiceForPrint = null }
        )
    }
}

@Composable
fun CreateInvoiceDialog(
    customers: List<Party>,
    products: List<Product>,
    invoicePrefix: String,
    onDismiss: () -> Unit,
    onConfirm: (SalesInvoice, List<SalesInvoiceItem>) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf(customers.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var quantityText by remember { mutableStateOf("1") }
    var unitRateText by remember { mutableStateOf(selectedProduct?.salesPrice?.toString() ?: "1000") }
    var isInterstate by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf("CREDIT") }

    val itemsList = remember { mutableStateListOf<SalesInvoiceItem>() }

    // Auto-update rate when product changes
    LaunchedEffect(selectedProduct) {
        selectedProduct?.let { p ->
            unitRateText = p.salesPrice.toString()
        }
    }

    val subtotal = itemsList.sumOf { it.taxableAmount }
    val totalTax = itemsList.sumOf { it.taxAmount }
    val cgst = if (!isInterstate) totalTax / 2.0 else 0.0
    val sgst = if (!isInterstate) totalTax / 2.0 else 0.0
    val igst = if (isInterstate) totalTax else 0.0
    val grandTotal = subtotal + totalTax

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New GST Sales Invoice", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Select Customer:", style = MaterialTheme.typography.labelMedium)
                    customers.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedCustomer?.id == c.id) Navy700 else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedCustomer?.id == c.id,
                                onClick = {
                                    selectedCustomer = c
                                    isInterstate = c.stateCode != "27" && c.stateCode.isNotBlank()
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("GSTIN: ${c.gstin.ifEmpty { "Unregistered" }} • State: ${c.stateName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Inter-state (IGST):", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isInterstate, onCheckedChange = { isInterstate = it })
                    }
                }

                item {
                    Text("Add Line Item:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Product selector chips
                            Text("Select Product:", style = MaterialTheme.typography.labelSmall)
                            products.take(4).forEach { p ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(selected = selectedProduct?.id == p.id, onClick = { selectedProduct = p })
                                    Text("${p.name} (₹${p.salesPrice})", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = quantityText,
                                    onValueChange = { quantityText = it },
                                    label = { Text("Qty") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = unitRateText,
                                    onValueChange = { unitRateText = it },
                                    label = { Text("Rate (₹)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f)
                                )
                            }

                            Button(
                                onClick = {
                                    val prod = selectedProduct ?: return@Button
                                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                                    val rate = unitRateText.toDoubleOrNull() ?: prod.salesPrice
                                    val taxable = qty * rate
                                    val tax = taxable * (prod.gstRate / 100.0)
                                    val itemTotal = taxable + tax

                                    itemsList.add(
                                        SalesInvoiceItem(
                                            invoiceId = 0,
                                            productId = prod.id,
                                            productName = prod.name,
                                            quantity = qty,
                                            unitRate = rate,
                                            gstRate = prod.gstRate,
                                            taxableAmount = taxable,
                                            taxAmount = tax,
                                            totalAmount = itemTotal
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Item to Invoice")
                            }
                        }
                    }
                }

                if (itemsList.isNotEmpty()) {
                    item {
                        Text("Invoice Items (${itemsList.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        itemsList.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("${item.quantity} x ₹${item.unitRate} + ${item.gstRate}% GST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatCurrency(item.totalAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Emerald400)
                                    IconButton(onClick = { itemsList.removeAt(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Payment Terms:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CREDIT", "CASH", "BANK", "UPI").forEach { mode ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxable Subtotal:", style = MaterialTheme.typography.bodySmall)
                                Text(formatCurrency(subtotal), style = MaterialTheme.typography.bodySmall)
                            }
                            if (isInterstate) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("IGST Output Tax:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(igst), style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CGST Output Tax:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(cgst), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SGST Output Tax:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(sgst), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Grand Total (₹):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formatCurrency(grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    if (itemsList.isEmpty()) return@Button

                    val invNum = "${invoicePrefix}${System.currentTimeMillis() % 10000}"
                    val invoice = SalesInvoice(
                        invoiceNumber = invNum,
                        customerId = cust.id,
                        customerName = cust.name,
                        date = System.currentTimeMillis(),
                        subtotal = subtotal,
                        cgst = cgst,
                        sgst = sgst,
                        igst = igst,
                        totalAmount = grandTotal,
                        isInterstate = isInterstate,
                        status = if (paymentMode == "CREDIT") InvoiceStatus.UNPAID else InvoiceStatus.PAID,
                        paymentMode = paymentMode
                    )
                    onConfirm(invoice, itemsList.toList())
                },
                enabled = selectedCustomer != null && itemsList.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Post & Save Invoice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PrintInvoiceDialog(
    invoice: SalesInvoice,
    settings: CompanySettings,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tax Invoice", fontWeight = FontWeight.Bold)
                StatusBadge(text = invoice.status.name, isSuccess = invoice.status == InvoiceStatus.PAID)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header (Supplier Details)
                Text(settings.companyName, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
                Text("GSTIN: ${settings.gstin}", color = Color.DarkGray, fontSize = 12.sp)
                Text(settings.address, color = Color.Gray, fontSize = 11.sp)
                HorizontalDivider(color = Color.LightGray)

                // Invoice Meta
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Invoice No: ${invoice.invoiceNumber}", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                        Text("Date: ${formatDate(invoice.date)}", color = Color.DarkGray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Billed To:", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                        Text(invoice.customerName, color = Color.DarkGray, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color.LightGray)

                // Financial Breakdown
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Taxable Subtotal:", color = Color.DarkGray, fontSize = 13.sp)
                    Text(formatCurrency(invoice.subtotal), color = Color.Black, fontSize = 13.sp)
                }
                if (invoice.isInterstate) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IGST:", color = Color.DarkGray, fontSize = 13.sp)
                        Text(formatCurrency(invoice.igst), color = Color.Black, fontSize = 13.sp)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CGST:", color = Color.DarkGray, fontSize = 13.sp)
                        Text(formatCurrency(invoice.cgst), color = Color.Black, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SGST:", color = Color.DarkGray, fontSize = 13.sp)
                        Text(formatCurrency(invoice.sgst), color = Color.Black, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Invoice Amount:", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                    Text(formatCurrency(invoice.totalAmount), fontWeight = FontWeight.Bold, color = Emerald600, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payment Mode: ${invoice.paymentMode}", color = Color.Gray, fontSize = 11.sp)
                    Text("Authorized Signatory", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) {
                Text("Print / Share PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
