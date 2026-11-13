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
fun InventoryScreen(
    products: List<Product>,
    onAddProduct: (Product) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery) {
        products.filter { p ->
            searchQuery.isEmpty() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.codeSku.contains(searchQuery, ignoreCase = true) ||
                    p.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalValuation = remember(filteredProducts) {
        filteredProducts.sumOf { it.currentStock * it.purchasePrice }
    }
    val lowStockCount = remember(filteredProducts) {
        filteredProducts.count { it.currentStock <= it.minStockAlert }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Emerald500,
                contentColor = TextPrimaryLight,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
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
                placeholder = { Text("Search products by name, SKU or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Inventory Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Stock Valuation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatCurrency(totalValuation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Low Stock Alerts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$lowStockCount Item(s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) ErrorRed else Emerald400)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredProducts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Inventory,
                    title = "No Products in Catalog",
                    description = "Add stock inventory items with purchase and sale prices.",
                    actionButtonText = "+ Add New Product",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        val isLowStock = prod.currentStock <= prod.minStockAlert
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
                                            text = prod.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "SKU: ${prod.codeSku} • HSN: ${prod.hsnCode.ifEmpty { "N/A" }} • GST: ${prod.gstRate.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${prod.currentStock.toInt()} ${prod.unit}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLowStock) ErrorRed else Emerald400
                                        )
                                        StatusBadge(
                                            text = if (isLowStock) "LOW STOCK" else "IN STOCK",
                                            isSuccess = !isLowStock
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Cost: ${formatCurrency(prod.purchasePrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Sale Price: ${formatCurrency(prod.salesPrice)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Emerald400)
                                    Text("Val: ${formatCurrency(prod.currentStock * prod.purchasePrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newProd ->
                onAddProduct(newProd)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (Product) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var codeSku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electronics") }
    var purchasePrice by remember { mutableStateOf("") }
    var salesPrice by remember { mutableStateOf("") }
    var currentStock by remember { mutableStateOf("10") }
    var minStockAlert by remember { mutableStateOf("3") }
    var gstRate by remember { mutableStateOf(18.0) }
    var hsnCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Inventory Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeSku,
                        onValueChange = { codeSku = it },
                        label = { Text("SKU Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hsnCode,
                        onValueChange = { hsnCode = it },
                        label = { Text("HSN Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Cost Price (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = salesPrice,
                        onValueChange = { salesPrice = it },
                        label = { Text("Sale Price (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentStock,
                        onValueChange = { currentStock = it },
                        label = { Text("Initial Stock") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStockAlert,
                        onValueChange = { minStockAlert = it },
                        label = { Text("Low Alert Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("GST Tax Rate (%):", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { r ->
                        FilterChip(
                            selected = gstRate == r,
                            onClick = { gstRate = r },
                            label = { Text("${r.toInt()}%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val cost = purchasePrice.toDoubleOrNull() ?: 0.0
                        val sale = salesPrice.toDoubleOrNull() ?: cost
                        val stock = currentStock.toDoubleOrNull() ?: 0.0
                        val alert = minStockAlert.toDoubleOrNull() ?: 3.0
                        val sku = codeSku.ifEmpty { "PROD-${System.currentTimeMillis() % 1000}" }

                        onConfirm(
                            Product(
                                name = name.trim(),
                                codeSku = sku.trim(),
                                category = category.trim(),
                                purchasePrice = cost,
                                salesPrice = sale,
                                gstRate = gstRate,
                                hsnCode = hsnCode.trim(),
                                currentStock = stock,
                                minStockAlert = alert
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Save Product")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
