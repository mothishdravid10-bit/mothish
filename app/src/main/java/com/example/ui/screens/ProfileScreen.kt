package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
import com.example.data.model.CompanySettings
import com.example.data.model.User
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

/**
 * User Profile & Account Settings Screen.
 * Accessible exclusively when user clicks the profile icon in the top-right corner.
 * Does NOT open automatically after login or signup.
 */
@Composable
fun ProfileScreen(
    user: User,
    settings: CompanySettings,
    onBackClick: () -> Unit,
    onSaveProfile: (companyName: String, gstin: String, stateCode: String, stateName: String) -> Unit,
    onLogoutClick: () -> Unit
) {
    var editCompanyName by remember(user) { mutableStateOf(user.companyName) }
    var editGstin by remember(user) { mutableStateOf(user.gstin) }
    var editStateCode by remember(user) { mutableStateOf(user.stateCode) }
    var editStateName by remember(user) { mutableStateOf(user.stateName) }
    var selectedLanguage by remember { mutableStateOf("English (India)") }
    var isSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Back Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.testTag("profile_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard", tint = TextPrimaryLight)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "My Profile & Account Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryLight
            )
        }

        // Profile Avatar Card - Clean Bright White
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Emerald600),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(text = "VERIFIED", isSuccess = true)
                    }
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.companyName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Emerald600,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Section: Personal & Account Information
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Personal & Security Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                )

                HorizontalDivider(color = BorderLight)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Email Address:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Account Status:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                    Text("Active • Isolated Multi-tenant", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Emerald600)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Language Preference:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                    Text(selectedLanguage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                }
            }
        }

        // Section: Business & GST Information (Optional configuration)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Business & Indian GST Compliance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                )

                Text(
                    text = "Configure your registered business details for GST invoices and accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )

                OutlinedTextField(
                    value = editCompanyName,
                    onValueChange = { editCompanyName = it },
                    label = { Text("Company Legal Trade Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald600,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextPrimaryLight,
                        unfocusedTextColor = TextPrimaryLight
                    )
                )

                OutlinedTextField(
                    value = editGstin,
                    onValueChange = { editGstin = it.uppercase() },
                    label = { Text("GSTIN (Goods and Services Tax ID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald600,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextPrimaryLight,
                        unfocusedTextColor = TextPrimaryLight
                    )
                )

                // Row(
                //     modifier = Modifier.fillMaxWidth(),
                //     horizontalArrangement = Arrangement.spacedBy(8.dp)
                // ) {
                //     OutlinedTextField(
                //         value = editStateCode,
                //         onValueChange = { editStateCode = it },
                //         label = { Text("State Code (27 = MH)") },
                //         singleLine = true,
                //         modifier = Modifier.weight(1f),
                //         shape = RoundedCornerShape(10.dp),
                //         colors = OutlinedTextFieldDefaults.colors(
                //             focusedBorderColor = Emerald600,
                //             unfocusedBorderColor = BorderLight,
                //             focusedTextColor = TextPrimaryLight,
                //             unfocusedTextColor = TextPrimaryLight
                //         )
                //     )
                //     OutlinedTextField(
                //         value = editStateName,
                //         onValueChange = { editStateName = it },
                //         label = { Text("State Name") },
                //         singleLine = true,
                //         modifier = Modifier.weight(1.5f),
                //         shape = RoundedCornerShape(10.dp),
                //         colors = OutlinedTextFieldDefaults.colors(
                //             focusedBorderColor = Emerald600,
                //             unfocusedBorderColor = BorderLight,
                //             focusedTextColor = TextPrimaryLight,
                //             unfocusedTextColor = TextPrimaryLight
                //         )
                //     )
                // }


                Button(
                    onClick = {
                        onSaveProfile(editCompanyName, editGstin, "27", "Maharashtra")
                        isSaved = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_save_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update Business Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (isSaved) {
                    StatusBadge(text = "Business information successfully saved to your profile!", isSuccess = true)
                }
            }
        }

        // Logout Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Session Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                )

                Button(
                    onClick = onLogoutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_logout_btn")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out from ZENO AI", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}
