package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AssistantState
import com.example.ai.ZenoAiEngine
import com.example.ai.SupportedLanguage
import com.example.ui.components.AssistantQueryResultCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatCurrency
import com.example.ui.theme.*

@Composable
fun ZenoAiScreen(
    engine: ZenoAiEngine,
    currentUserId: Long,
    onNavigate: (String) -> Unit
) {
    val state by engine.state.collectAsState()
    val recognizedText by engine.recognizedText.collectAsState()
    val activeTx by engine.activeTransaction.collectAsState()
    val feedbackText by engine.feedbackText.collectAsState()
    val userLang by engine.userLanguagePreference.collectAsState()
    val speakerVerified by engine.speakerRecognitionEnabled.collectAsState()
    val isSaving by engine.isSaving.collectAsState()
    val saveError by engine.saveError.collectAsState()
    val saveSuccess by engine.saveSuccess.collectAsState()
    val latestQueryResult by engine.latestQueryResult.collectAsState()
    val conversationHistory by engine.conversationHistory.collectAsState()

    var showLanguageDropdown by remember { mutableStateOf(false) }
    var typedQuery by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale_1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha_1"
    )

    val submitTypedQuery = {
        val trimmed = typedQuery.trim()
        if (trimmed.isNotEmpty()) {
            typedQuery = ""
            engine.processSpeechInput(trimmed, currentUserId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header with Language Selector & Speaker Recognition Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (speakerVerified) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                        contentDescription = "Speaker Recognition",
                        tint = Emerald600,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (speakerVerified) "Speaker Verified" else "Voice Input",
                        style = MaterialTheme.typography.labelSmall,
                        color = Emerald600,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { showLanguageDropdown = true },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceWhite,
                            contentColor = TextPrimaryLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(14.dp), tint = Emerald600)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(userLang.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false },
                        modifier = Modifier.background(SurfaceWhite)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (lang == userLang) FontWeight.Bold else FontWeight.Normal,
                                        color = if (lang == userLang) Emerald600 else TextPrimaryLight
                                    )
                                },
                                onClick = {
                                    engine.setLanguagePreference(lang)
                                    showLanguageDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                AssistantState.LISTENING -> Amber500
                                AssistantState.PROCESSING -> InfoBlue
                                AssistantState.CONFIRMATION -> Emerald600
                                else -> Emerald600
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (state) {
                        AssistantState.LISTENING -> "LISTENING TO YOUR VOICE..."
                        AssistantState.PROCESSING -> "ANALYZING INTENT & ACCOUNTING BOOKS..."
                        AssistantState.CONFIRMATION -> "TRANSACTION CONFIRMATION"
                        else -> "MY ZENO – INTELLIGENT AI ACCOUNTANT"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (state) {
                        AssistantState.LISTENING -> Amber600
                        else -> Emerald600
                    },
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = feedbackText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("zeno_feedback_text")
            )
        }

        // Center Stage: Large Circular Microphone Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state == AssistantState.LISTENING) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale1)
                        .clip(CircleShape)
                        .background(Emerald500.copy(alpha = pulseAlpha1))
                )
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .scale(pulseScale1 * 0.9f)
                        .clip(CircleShape)
                        .background(Amber500.copy(alpha = pulseAlpha1 * 0.7f))
                )
            }

            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = when (state) {
                                AssistantState.LISTENING -> listOf(Amber500, Amber600)
                                AssistantState.PROCESSING -> listOf(InfoBlue, Color(0xFF1D4ED8))
                                else -> listOf(Emerald500, Emerald600)
                            }
                        )
                    )
                    .clickable {
                        if (state == AssistantState.LISTENING) {
                            engine.stopListening()
                        } else {
                            engine.startListening(currentUserId)
                        }
                    }
                    .testTag("zeno_ai_mic_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (state) {
                        AssistantState.LISTENING -> Icons.Default.GraphicEq
                        AssistantState.PROCESSING -> Icons.Default.Sync
                        AssistantState.CONFIRMATION -> Icons.Default.Check
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Assistant Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Quick Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Yesterday's transactions",
                "Current balance",
                "Profit & Loss",
                "Pending payments",
                "Create a transaction"
            ).forEach { suggestion ->
                AssistChip(
                    onClick = { engine.processSpeechInput(suggestion, currentUserId) },
                    label = { Text(suggestion, fontSize = 12.sp) }
                )
            }
        }

        // Conversational Ask / Follow-up Text Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = typedQuery,
                onValueChange = { typedQuery = it },
                placeholder = { Text("Ask ZENO or reply (e.g. Yesterday's expenses)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitTypedQuery() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("zeno_query_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald600,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )
            FilledIconButton(
                onClick = submitTypedQuery,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Emerald600),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("zeno_send_query_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Query to ZENO",
                    tint = Color.White
                )
            }
        }

        // Live Multilingual Recognized Transcript Display
        AnimatedVisibility(visible = recognizedText.isNotEmpty() && !isSaving && saveError == null && saveSuccess == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recognized Command",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryLight
                        )
                        StatusBadge(text = userLang.code.uppercase(), isSuccess = true)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$recognizedText\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryLight
                    )
                }
            }
        }

        // Database Query & Report Response Card
        AnimatedVisibility(visible = latestQueryResult != null && activeTx == null && !isSaving) {
            val result = latestQueryResult ?: return@AnimatedVisibility
            AssistantQueryResultCard(
                result = result,
                modifier = Modifier.testTag("zeno_assistant_response_card"),
                onSpeakClick = { engine.speak(result.spokenReply, userLang) },
                onDismissClick = { engine.clearQueryResults() },
                onOpenDashboard = { onNavigate("dashboard") }
            )
        }

        // Recent Conversation Context
        AnimatedVisibility(visible = conversationHistory.size > 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONVERSATION CONTEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600
                        )
                        TextButton(onClick = { engine.resetConversation() }) {
                            Text("Reset", fontSize = 11.sp, color = TextSecondaryLight)
                        }
                    }
                    conversationHistory.takeLast(6).forEach { msg ->
                        Text(
                            text = if (msg.isUser) "You: ${msg.text}" else "ZENO: ${msg.text}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (msg.isUser) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (msg.isUser) TextPrimaryLight else Emerald600
                        )
                    }
                }
            }
        }

        if (isSaving) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Emerald600)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Saving transaction to database...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (saveError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(saveError!!, modifier = Modifier.padding(16.dp), color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (saveSuccess != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(saveSuccess!!, modifier = Modifier.padding(16.dp), color = Color(0xFF15803D), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Transaction Confirmation Card (Double Entry + Indian GST + Canonical English Storage)
        AnimatedVisibility(visible = activeTx != null && !isSaving && saveError == null && saveSuccess == null) {
            val tx = activeTx ?: return@AnimatedVisibility
            var editAmount by remember(tx) { mutableStateOf(tx.taxableAmount.toString()) }
            var editPartyName by remember(tx) { mutableStateOf(tx.partyName) }
            var editInterstate by remember(tx) { mutableStateOf(tx.isInterstate) }
            var editGstRate by remember(tx) { mutableDoubleStateOf(tx.gstRate) }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("zeno_confirmation_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = tx.transactionType,
                                isSuccess = tx.transactionType in listOf("SALES", "RECEIPT")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verified Entry", style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
                        }
                        Text(
                            text = "Lang: ${tx.detectedLanguage.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald600,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (tx.canonicalEnglishSummary.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "CANONICAL ENGLISH DATABASE RECORD:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald600,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tx.canonicalEnglishSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimaryLight
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    OutlinedTextField(
                        value = editPartyName,
                        onValueChange = {
                            editPartyName = it
                            engine.updateActiveTransaction(tx.copy(partyName = it, isInterstate = editInterstate, gstRate = editGstRate))
                        },
                        label = { Text("Party / Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editAmount,
                            onValueChange = {
                                editAmount = it
                                val amt = it.toDoubleOrNull() ?: 0.0
                                engine.updateActiveTransaction(tx.copy(taxableAmount = amt, isInterstate = editInterstate, gstRate = editGstRate, partyName = editPartyName))
                            },
                            label = { Text("Taxable Amount (₹)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("GST Rate:", style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.0, 5.0, 12.0, 18.0, 28.0).take(3).forEach { r ->
                                    FilterChip(
                                        selected = editGstRate == r,
                                        onClick = {
                                            editGstRate = r
                                            val amt = editAmount.toDoubleOrNull() ?: tx.taxableAmount
                                            engine.updateActiveTransaction(tx.copy(gstRate = r, taxableAmount = amt, isInterstate = editInterstate, partyName = editPartyName))
                                        },
                                        label = { Text("${r.toInt()}%", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editInterstate) "Inter-state Sale (IGST)" else "Intra-state Sale (CGST + SGST)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryLight
                        )
                        Switch(
                            checked = editInterstate,
                            onCheckedChange = {
                                editInterstate = it
                                val amt = editAmount.toDoubleOrNull() ?: tx.taxableAmount
                                engine.updateActiveTransaction(tx.copy(isInterstate = it, taxableAmount = amt, gstRate = editGstRate, partyName = editPartyName))
                            }
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxable Value:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                Text(formatCurrency(tx.taxableAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                            }
                            if (tx.isInterstate) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("IGST (${tx.gstRate.toInt()}%):", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                    Text(formatCurrency(tx.igst), style = MaterialTheme.typography.bodySmall, color = Emerald600, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CGST (${(tx.gstRate / 2).toInt()}%):", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                    Text(formatCurrency(tx.cgst), style = MaterialTheme.typography.bodySmall, color = Emerald600, fontWeight = FontWeight.SemiBold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SGST (${(tx.gstRate / 2).toInt()}%):", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                    Text(formatCurrency(tx.sgst), style = MaterialTheme.typography.bodySmall, color = Emerald600, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Invoice Value:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                                Text(formatCurrency(tx.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Emerald600)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val amt = editAmount.toDoubleOrNull() ?: tx.taxableAmount
                                engine.confirmTransaction(
                                    tx.copy(
                                        taxableAmount = amt,
                                        partyName = editPartyName,
                                        isInterstate = editInterstate,
                                        gstRate = editGstRate
                                    ),
                                    currentUserId
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("zeno_confirm_post_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm & Post Entry", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = { engine.cancelTransaction() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                        ) {
                            Text("Cancel", color = TextSecondaryLight)
                        }
                    }
                }
            }
        }
    }
}
