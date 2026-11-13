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
fun PurchasesScreen(
    bills: List<PurchaseBill>,
    suppliers: List<Party>,
    products: List<Product>,
    settings: CompanySettings,
    onCreateBill: (PurchaseBill, List<PurchaseBillItem>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredBills = remember(bills, searchQuery) {
        bills.filter { b ->
            searchQuery.isEmpty() ||
                    b.supplierName.contains(searchQuery, ignoreCase = true) ||
                    b.billNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalPurchased = remember(filteredBills) { filteredBills.sumOf { it.totalAmount } }
    val totalInputTaxCredit = remember(filteredBills) { filteredBills.sumOf { it.cgst + it.sgst + it.igst } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("create_bill_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Purchase Bill")
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
                placeholder = { Text("Search bills by supplier or #...") },
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
                        Text("Total Purchases", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatCurrency(totalPurchased), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Amber500)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Input Tax Credit (ITC)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatCurrency(totalInputTaxCredit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredBills.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.ShoppingCart,
                    title = "No Purchase Bills",
                    description = "Record vendor purchases and claim Input Tax Credit.",
                    actionButtonText = "+ Record Purchase Bill",
                    onActionClick = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredBills, key = { it.id }) { bill ->
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
                                                text = bill.billNumber,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Amber500
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            StatusBadge(
                                                text = bill.status.name,
                                                isSuccess = bill.status == InvoiceStatus.PAID
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = bill.supplierName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatDate(bill.date)} • Mode: ${bill.paymentMode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(bill.totalAmount),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "ITC Claimable: ${formatCurrency(bill.cgst + bill.sgst + bill.igst)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Emerald400
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

    if (showCreateDialog) {
        CreateBillDialog(
            suppliers = suppliers,
            products = products,
            billPrefix = settings.billPrefix,
            onDismiss = { showCreateDialog = false },
            onConfirm = { bill, items ->
                onCreateBill(bill, items)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreateBillDialog(
    suppliers: List<Party>,
    products: List<Product>,
    billPrefix: String,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseBill, List<PurchaseBillItem>) -> Unit
) {
    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var quantityText by remember { mutableStateOf("1") }
    var unitRateText by remember { mutableStateOf(selectedProduct?.purchasePrice?.toString() ?: "500") }
    var isInterstate by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf("CREDIT") }

    val itemsList = remember { mutableStateListOf<PurchaseBillItem>() }

    LaunchedEffect(selectedProduct) {
        selectedProduct?.let { p ->
            unitRateText = p.purchasePrice.toString()
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
        title = { Text("Record Purchase Bill", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Select Supplier:", style = MaterialTheme.typography.labelMedium)
                    suppliers.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedSupplier?.id == s.id) Navy700 else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedSupplier?.id == s.id,
                                onClick = {
                                    selectedSupplier = s
                                    isInterstate = s.stateCode != "27" && s.stateCode.isNotBlank()
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("GSTIN: ${s.gstin.ifEmpty { "Unregistered" }} • State: ${s.stateName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Text("Add Inward Stock Item:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            products.take(4).forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    RadioButton(selected = selectedProduct?.id == p.id, onClick = { selectedProduct = p })
                                    Text("${p.name} (Cost: ₹${p.purchasePrice})", style = MaterialTheme.typography.bodySmall)
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
                                    label = { Text("Cost Rate (₹)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f)
                                )
                            }

                            Button(
                                onClick = {
                                    val prod = selectedProduct ?: return@Button
                                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                                    val rate = unitRateText.toDoubleOrNull() ?: prod.purchasePrice
                                    val taxable = qty * rate
                                    val tax = taxable * (prod.gstRate / 100.0)
                                    val itemTotal = taxable + tax

                                    itemsList.add(
                                        PurchaseBillItem(
                                            billId = 0,
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
                                Text("+ Add Item to Bill")
                            }
                        }
                    }
                }

                if (itemsList.isNotEmpty()) {
                    item {
                        Text("Bill Items (${itemsList.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        itemsList.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("${item.quantity} x ₹${item.unitRate} + ${item.gstRate}% ITC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatCurrency(item.totalAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Amber500)
                            }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Purchases Subtotal:", style = MaterialTheme.typography.bodySmall)
                                Text(formatCurrency(subtotal), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ITC Tax Credit:", style = MaterialTheme.typography.bodySmall)
                                Text(formatCurrency(totalTax), style = MaterialTheme.typography.bodySmall, color = Emerald400)
                            }
                            HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Bill Payable:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formatCurrency(grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Amber500)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val supp = selectedSupplier ?: return@Button
                    if (itemsList.isEmpty()) return@Button

                    val billNum = "${billPrefix}${System.currentTimeMillis() % 10000}"
                    val bill = PurchaseBill(
                        billNumber = billNum,
                        supplierId = supp.id,
                        supplierName = supp.name,
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
                    onConfirm(bill, itemsList.toList())
                },
                enabled = selectedSupplier != null && itemsList.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Post & Update Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
