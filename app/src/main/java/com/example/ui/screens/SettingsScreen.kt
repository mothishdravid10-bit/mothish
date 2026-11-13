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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanySettings
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    settings: CompanySettings,
    onSaveSettings: (CompanySettings) -> Unit,
    onResetDemoData: () -> Unit
) {
    var companyName by remember(settings) { mutableStateOf(settings.companyName) }
    var gstin by remember(settings) { mutableStateOf(settings.gstin) }
    var address by remember(settings) { mutableStateOf(settings.address) }
    var phone by remember(settings) { mutableStateOf(settings.phone) }
    var email by remember(settings) { mutableStateOf(settings.email) }
    var financialYear by remember(settings) { mutableStateOf(settings.financialYear) }
    var stateCode by remember(settings) { mutableStateOf(settings.stateCode) }
    var invoicePrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
    var savedSuccess by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Enterprise Accounting Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("ZENO AI Engine • Version 1.0.0 (Build 2026)", style = MaterialTheme.typography.labelSmall, color = Emerald400)
                }
            }
        }

        // Company Information Form
        item {
            Text("Business Profile & Indian GST Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Legal Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase() },
                        label = { Text("GSTIN (Goods and Services Tax ID) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // OutlinedTextField(
                        //     value = stateCode,
                        //     onValueChange = { stateCode = it },
                        //     label = { Text("State Code (27 = MH)") },
                        //     singleLine = true,
                        //     modifier = Modifier.weight(1f)
                        // )
                        OutlinedTextField(
                            value = financialYear,
                            onValueChange = { financialYear = it },
                            label = { Text("Financial Year") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Principal Place of Business") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Billing Phone") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Billing Email") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = invoicePrefix,
                        onValueChange = { invoicePrefix = it },
                        label = { Text("Tax Invoice Prefix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val updated = settings.copy(
                                companyName = companyName.trim(),
                                gstin = gstin.trim(),
                                address = address.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                stateCode = "27",
                                financialYear = financialYear.trim(),
                                invoicePrefix = invoicePrefix.trim()
                            )
                            onSaveSettings(updated)
                            savedSuccess = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_settings_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Company Configuration", fontWeight = FontWeight.Bold)
                    }

                    if (savedSuccess) {
                        StatusBadge(text = "Settings successfully saved to database!", isSuccess = true)
                    }
                }
            }
        }

        // Database & System Maintenance
        item {
            Text("System & Data Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Currency & Standards:", style = MaterialTheme.typography.labelSmall)
                    Text("• Base Currency: Indian Rupee (INR ₹)\n• Accounting Model: Double-Entry Bookkeeping (Debit = Credit)\n• Database: SQLite Relational via Room Database with reactive flows\n• Assistant: ZENO Voice & Text AI", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onResetDemoData,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber500),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reload Standard Indian Chart of Accounts")
                    }
                }
            }
        }
    }
}
