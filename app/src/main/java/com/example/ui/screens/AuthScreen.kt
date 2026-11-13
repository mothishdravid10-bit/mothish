package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.EmailVerificationService
import com.example.data.auth.EmailOtpResult
import com.example.data.auth.VerifyOtpResult
import com.example.notifications.VerificationNotificationHelper
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    MARATHI("mr", "Marathi", "मराठी"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    BENGALI("bn", "Bengali", "বাংলা")
}

@Composable
fun AuthScreen(
    emailVerificationService: EmailVerificationService,
    onLogin: (email: String, password: String, onError: (String) -> Unit) -> Unit,
    onSignup: (name: String, email: String, password: String, companyName: String, gstin: String, stateCode: String, stateName: String, onError: (String) -> Unit) -> Unit,
    onGoogleSignIn: (onError: (String) -> Unit) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Login, 1: Create Account
    var selectedLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    // Login Form State
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Signup Form State (Strictly NO State Code on Signup as requested)
    var signupName by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirmPassword by remember { mutableStateOf("") }
    var signupCompanyName by remember { mutableStateOf("") }
    var showSignupPassword by remember { mutableStateOf(false) }

    // Email OTP Verification State
    var isVerifyingEmailOtp by remember { mutableStateOf(false) }
    var enteredOtpCode by remember { mutableStateOf("") }
    var otpMessage by remember { mutableStateOf<String?>(null) }
    var verificationSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var notificationsDeniedNote by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotMessage by remember { mutableStateOf<String?>(null) }
    var forgotError by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsDeniedNote = !granted
        if (granted) {
            otpMessage?.let {
                VerificationNotificationHelper.showVerificationUpdate(
                    context = context,
                    title = "ZENO AI Verification",
                    message = it
                )
            }
        }
    }

    // Resend cooldown timer
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Language & Server Settings Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showServerSettingsDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald600),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald600.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Server Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box {
                    TextButton(
                        onClick = { showLanguageMenu = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Emerald600)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${selectedLanguage.displayName} (${selectedLanguage.nativeName})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.background(SurfaceWhite)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(lang.displayName, color = TextPrimaryLight, fontWeight = FontWeight.Medium)
                                        Text(lang.nativeName, color = Emerald600, fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    selectedLanguage = lang
                                    showLanguageMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // ZENO AI Brand Logo & Subtitle
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Emerald600)
                    .border(2.dp, Emerald300, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Z",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ZENO AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryLight,
                letterSpacing = 1.sp
            )

            Text(
                text = "Your Personal AI Accountant",
                style = MaterialTheme.typography.bodyMedium,
                color = Emerald600,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Double-Entry Bookkeeping • Zero-Latency • Indian GST",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryLight
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Container Card - Bright White with soft shadow and crisp border
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(20.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    // Tab Selector: Login vs Create Account
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = Emerald600,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                isVerifyingEmailOtp = false
                                errorMessage = null
                            },
                            text = {
                                Text(
                                    "Log In",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 0) Emerald600 else TextSecondaryLight
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                errorMessage = null
                            },
                            text = {
                                Text(
                                    "Create Account",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 1) Emerald600 else TextSecondaryLight
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Error Alert Display
                    AnimatedVisibility(visible = errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(errorMessage ?: "", color = ErrorRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (selectedTab == 0) {
                        // ============================================
                        // 1. PROFESSIONAL LOGIN SCREEN
                        // ============================================

                        // Continue with Google (Primary Button)
                        OutlinedButton(
                            onClick = {
                                onGoogleSignIn { err ->
                                    errorMessage = err
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_google_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceWhite,
                                contentColor = TextPrimaryLight
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Divider OR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
                            Text(
                                text = "  or Login with Email  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryLight
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Email Address field
                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = {
                                loginEmail = it
                                errorMessage = null
                            },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Emerald600) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald600,
                                unfocusedBorderColor = BorderLight,
                                focusedTextColor = TextPrimaryLight,
                                unfocusedTextColor = TextPrimaryLight,
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = SurfaceWhite
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password field
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = {
                                loginPassword = it
                                errorMessage = null
                            },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Emerald600) },
                            trailingIcon = {
                                IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                    Icon(
                                        imageVector = if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Password",
                                        tint = TextSecondaryLight
                                    )
                                }
                            },
                            visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald600,
                                unfocusedBorderColor = BorderLight,
                                focusedTextColor = TextPrimaryLight,
                                unfocusedTextColor = TextPrimaryLight,
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = SurfaceWhite
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                forgotEmail = loginEmail
                                showForgotPasswordDialog = true
                            }) {
                                Text("Forgot Password?", style = MaterialTheme.typography.labelSmall, color = Emerald600, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            enabled = !isLoggingIn,
                            onClick = {
                                val trimmedEmail = loginEmail.trim()
                                if (trimmedEmail.isBlank() || loginPassword.isBlank()) {
                                    errorMessage = "Please enter both Email Address and Password."
                                    return@Button
                                }
                                if (!emailVerificationService.isValidEmailFormat(trimmedEmail)) {
                                    errorMessage = "Please enter a valid email address."
                                    return@Button
                                }
                                errorMessage = null
                                isLoggingIn = true
                                onLogin(trimmedEmail, loginPassword) { err ->
                                    isLoggingIn = false
                                    errorMessage = err
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_submit_btn")
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing In...", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            } else {
                                Text("Log In to ZENO", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Don't have an account?", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                            TextButton(onClick = { selectedTab = 1 }) {
                                Text("Create Account", fontWeight = FontWeight.Bold, color = Emerald600)
                            }
                        }

                    } else {
                        // ============================================
                        // 2. CREATE ACCOUNT WITH EMAIL VERIFICATION
                        // ============================================

                        if (!isVerifyingEmailOtp) {
                            // Stage 1: Registration Details (Strictly Full Name, Email Address, Password, Confirm Password - NO State Code)
                            OutlinedTextField(
                                value = signupName,
                                onValueChange = {
                                    signupName = it
                                    errorMessage = null
                                },
                                label = { Text("Full Name *") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Emerald600) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("signup_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald600,
                                    unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Strictly labeled "Email Address" (NOT "Business Email")
                            OutlinedTextField(
                                value = signupEmail,
                                onValueChange = {
                                    signupEmail = it
                                    errorMessage = null
                                },
                                label = { Text("Email Address *") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Emerald600) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("signup_email_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald600,
                                    unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = signupPassword,
                                onValueChange = {
                                    signupPassword = it
                                    errorMessage = null
                                },
                                label = { Text("Password *") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Emerald600) },
                                trailingIcon = {
                                    IconButton(onClick = { showSignupPassword = !showSignupPassword }) {
                                        Icon(
                                            imageVector = if (showSignupPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Password",
                                            tint = TextSecondaryLight
                                        )
                                    }
                                },
                                visualTransformation = if (showSignupPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("signup_password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald600,
                                    unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = signupConfirmPassword,
                                onValueChange = {
                                    signupConfirmPassword = it
                                    errorMessage = null
                                },
                                label = { Text("Confirm Password *") },
                                leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Emerald600) },
                                visualTransformation = if (showSignupPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("signup_confirm_password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald600,
                                    unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = signupCompanyName,
                                onValueChange = { signupCompanyName = it },
                                label = { Text("Company / Trading Business Name (Optional)") },
                                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Emerald600) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald600,
                                    unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Continue button: Validates inputs and sends real OTP via backend email service
                            Button(
                                enabled = !isSendingOtp,
                                onClick = {
                                    if (signupName.isBlank() || signupEmail.isBlank() || signupPassword.isBlank() || signupConfirmPassword.isBlank()) {
                                        errorMessage = "Please fill in Full Name, Email Address, Password, and Confirm Password."
                                        return@Button
                                    }
                                    if (signupPassword != signupConfirmPassword) {
                                        errorMessage = "Passwords do not match. Please verify."
                                        return@Button
                                    }
                                    if (signupPassword.length < 6) {
                                        errorMessage = "Password must be at least 6 characters."
                                        return@Button
                                    }

                                    isSendingOtp = true
                                    errorMessage = null
                                    verificationSuccessMessage = null
                                    scope.launch {
                                        val result = emailVerificationService.sendVerificationOtp(signupEmail)
                                        isSendingOtp = false
                                        when (result) {
                                            is EmailOtpResult.OtpSent -> {
                                                errorMessage = null
                                                otpMessage = result.message
                                                enteredOtpCode = ""
                                                resendCooldown = 60
                                                isVerifyingEmailOtp = true
                                                if (VerificationNotificationHelper.hasNotificationPermission(context)) {
                                                    notificationsDeniedNote = false
                                                    VerificationNotificationHelper.showVerificationUpdate(
                                                        context = context,
                                                        title = "ZENO AI Verification Code Sent",
                                                        message = result.message
                                                    )
                                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    notificationsDeniedNote = true
                                                }
                                            }
                                            is EmailOtpResult.Error -> {
                                                errorMessage = result.message
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("signup_continue_btn")
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sending Verification Code...", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                } else {
                                    Text("Continue with Verification", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Already have an account?", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                TextButton(onClick = { selectedTab = 0 }) {
                                    Text("Log In", fontWeight = FontWeight.Bold, color = Emerald600)
                                }
                            }
                        } else {
                            // Stage 2: OTP Verification Screen
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Emerald500.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = Emerald600, modifier = Modifier.size(28.dp))
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Verify Your Email",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryLight
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "We sent a 6-digit verification code to:\n$signupEmail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                AnimatedVisibility(visible = otpMessage != null && verificationSuccessMessage == null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Emerald500.copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .padding(bottom = 12.dp)
                                            .testTag("otp_sent_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = otpMessage ?: "",
                                                color = Emerald600,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = verificationSuccessMessage != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Emerald600.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(bottom = 12.dp)
                                            .testTag("otp_verified_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = verificationSuccessMessage ?: "",
                                                color = Color(0xFF15803D),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (notificationsDeniedNote) {
                                    Text(
                                        text = "Notifications are disabled. Verification updates are shown here on screen.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryLight,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 10.dp).testTag("notifications_denied_note")
                                    )
                                }

                                OutlinedTextField(
                                    value = enteredOtpCode,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }
                                        if (digits.length <= 6) {
                                            enteredOtpCode = digits
                                            errorMessage = null
                                        }
                                    },
                                    label = { Text("Enter 6-Digit OTP") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("signup_otp_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Emerald600,
                                        unfocusedBorderColor = BorderLight,
                                        focusedTextColor = TextPrimaryLight,
                                        unfocusedTextColor = TextPrimaryLight,
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite
                                    )
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    enabled = !isVerifyingOtp,
                                    onClick = {
                                        if (enteredOtpCode.length != 6) {
                                            errorMessage = "Please enter the full 6-digit verification code."
                                            return@Button
                                        }

                                        isVerifyingOtp = true
                                        errorMessage = null
                                        scope.launch {
                                            val verifyResult = emailVerificationService.verifyOtp(signupEmail, enteredOtpCode)
                                            when (verifyResult) {
                                                is VerifyOtpResult.Success -> {
                                                    verificationSuccessMessage = "${verifyResult.message} Activating your account..."
                                                    VerificationNotificationHelper.showVerificationUpdate(
                                                        context = context,
                                                        title = "Email Verified",
                                                        message = "Your email $signupEmail has been verified. Activating your ZENO AI account.",
                                                        notificationId = 2002
                                                    )
                                                    onSignup(
                                                        signupName,
                                                        signupEmail,
                                                        signupPassword,
                                                        signupCompanyName.ifBlank { "My Enterprise" },
                                                        "", // GSTIN empty by default
                                                        "27", // Default internal compliance state
                                                        "Maharashtra"
                                                    ) { err ->
                                                        isVerifyingOtp = false
                                                        verificationSuccessMessage = null
                                                        errorMessage = err
                                                    }
                                                }
                                                is VerifyOtpResult.Failure -> {
                                                    isVerifyingOtp = false
                                                    errorMessage = verifyResult.message
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("signup_verify_otp_btn")
                                ) {
                                    if (isVerifyingOtp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verifying...", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verify & Complete Registration", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        isVerifyingEmailOtp = false
                                        enteredOtpCode = ""
                                        otpMessage = null
                                        verificationSuccessMessage = null
                                        errorMessage = null
                                    }) {
                                        Text("Change Email", color = TextSecondaryLight)
                                    }

                                    TextButton(
                                        enabled = resendCooldown == 0 && !isSendingOtp,
                                        modifier = Modifier.testTag("resend_otp_btn"),
                                        onClick = {
                                            isSendingOtp = true
                                            errorMessage = null
                                            scope.launch {
                                                val res = emailVerificationService.resendVerificationOtp(signupEmail)
                                                isSendingOtp = false
                                                when (res) {
                                                    is EmailOtpResult.OtpSent -> {
                                                        otpMessage = "New OTP sent: ${res.message}"
                                                        enteredOtpCode = ""
                                                        resendCooldown = 60
                                                        VerificationNotificationHelper.showVerificationUpdate(
                                                            context = context,
                                                            title = "ZENO AI New Verification Code",
                                                            message = res.message
                                                        )
                                                    }
                                                    is EmailOtpResult.Error -> {
                                                        errorMessage = res.message
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend OTP",
                                            color = if (resendCooldown == 0 && !isSendingOtp) Emerald600 else TextSecondaryLight,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Secure Google OAuth • Verified Email Accounts • Strict Isolation",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryLight,
                textAlign = TextAlign.Center
            )
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                forgotMessage = null
                forgotError = null
            },
            title = { Text("Reset Your Password", fontWeight = FontWeight.Bold, color = TextPrimaryLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your registered Email Address. We'll send a password reset verification code via the ZENO backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = {
                            forgotEmail = it
                            forgotError = null
                        },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald600,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight
                        )
                    )
                    if (forgotError != null) {
                        Text(forgotError ?: "", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                    if (forgotMessage != null) {
                        StatusBadge(text = forgotMessage ?: "", isSuccess = true)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSendingReset,
                    onClick = {
                        if (forgotMessage != null) {
                            showForgotPasswordDialog = false
                            forgotMessage = null
                            forgotError = null
                            return@Button
                        }
                        if (forgotEmail.isBlank()) {
                            forgotError = "Please enter your email address."
                            return@Button
                        }
                        isSendingReset = true
                        forgotError = null
                        scope.launch {
                            val res = emailVerificationService.requestPasswordReset(forgotEmail)
                            isSendingReset = false
                            res.onSuccess { msg ->
                                forgotMessage = msg
                            }.onFailure { err ->
                                forgotError = err.message ?: "Unable to send reset code."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text(
                        text = when {
                            isSendingReset -> "Sending..."
                            forgotMessage != null -> "Done"
                            else -> "Send Reset Code"
                        },
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotPasswordDialog = false
                    forgotMessage = null
                    forgotError = null
                }) {
                    Text("Close", color = TextSecondaryLight)
                }
            },
            containerColor = SurfaceWhite
        )
    }

    // Server Settings Dialog
    if (showServerSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            title = { Text("ZENO Backend Server Settings", fontWeight = FontWeight.Bold, color = TextPrimaryLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose whether to run in Standalone Preview Mode (100% offline local database) or connect to a live FastAPI backend over HTTPS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = { Text("Custom FastAPI URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald600,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight
                        )
                    )
                    Text(
                        text = "Leave blank to use Standalone Preview Mode (no configuration needed).",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryLight
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showServerSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Save & Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerSettingsDialog = false }) {
                    Text("Cancel", color = TextSecondaryLight)
                }
            },
            containerColor = SurfaceWhite
        )
    }
}
