package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.EmailOtpResult
import com.example.data.auth.EmailVerificationService
import com.example.data.auth.OtpEmailDispatcher
import com.example.data.auth.VerifyOtpResult
import com.example.notifications.VerificationNotificationHelper
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthOtpAndNotificationRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notificationPermissionDeniedStillAllowsOtpVerification() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(VerificationNotificationHelper.hasNotificationPermission(app))
        val posted = VerificationNotificationHelper.showVerificationUpdate(
            context = app,
            title = "ZENO AI Verification",
            message = "Verification code sent"
        )
        assertFalse(posted)

        val dao = FakeAccountingDao()
        var dispatchedOtp: String? = null
        val service = EmailVerificationService(
            accountingDao = dao,
            emailDispatcher = OtpEmailDispatcher { email, otp ->
                dispatchedOtp = otp
                Result.success("6-digit verification code sent to $email.")
            }
        )

        val sendRes = service.sendVerificationOtp("deniednotif@zenoai.in")
        assertTrue(sendRes is EmailOtpResult.OtpSent)
        val verifyRes = service.verifyOtp("deniednotif@zenoai.in", dispatchedOtp!!)
        assertTrue(verifyRes is VerifyOtpResult.Success)
        assertTrue(service.isEmailVerified("deniednotif@zenoai.in"))
    }

    @Test
    fun notificationPermissionGrantedPostsVerificationNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(VerificationNotificationHelper.hasNotificationPermission(app))
        val posted = VerificationNotificationHelper.showVerificationUpdate(
            context = app,
            title = "ZENO AI Verification",
            message = "Email verified successfully."
        )
        assertTrue(posted)

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = Shadows.shadowOf(manager).allNotifications
        assertTrue(notifications.isNotEmpty())
    }

    @Test
    fun authScreenRemovesOpenGmailAndCompletesOtpRegistrationFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val dao = FakeAccountingDao()
        var dispatchedOtp: String? = null
        val service = EmailVerificationService(
            accountingDao = dao,
            emailDispatcher = OtpEmailDispatcher { email, otp ->
                dispatchedOtp = otp
                Result.success("6-digit verification code sent to $email. Valid for 10 minutes.")
            }
        )

        var completedSignupEmail: String? = null

        composeRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    emailVerificationService = service,
                    onLogin = { _, _, _ -> },
                    onSignup = { _, email, _, _, _, _, _, _ ->
                        completedSignupEmail = email
                    },
                    onGoogleSignIn = {}
                )
            }
        }

        // Switch to Create Account tab (first match is the Tab; second match is the bottom link button)
        composeRule.onAllNodesWithText("Create Account")[0].performClick()

        // Fill registration fields
        composeRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Test Trader")
        composeRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("trader@zenoai.in")
        composeRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("secret123")
        composeRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("secret123")

        composeRule.onNodeWithTag("signup_continue_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            dispatchedOtp != null &&
                composeRule.onAllNodesWithTag("signup_otp_input").fetchSemanticsNodes().isNotEmpty()
        }

        val sentOtp = dispatchedOtp!!

        // Open Gmail button must not exist anywhere in the verification flow
        assertTrue(composeRule.onAllNodesWithTag("open_gmail_btn").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Open Gmail", substring = true).fetchSemanticsNodes().isEmpty())

        // Sent banner and OTP input are displayed; actual OTP is NOT shown on screen
        composeRule.onNodeWithTag("otp_sent_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("signup_otp_input").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(sentOtp, substring = true).fetchSemanticsNodes().isEmpty())

        // Resend button shows cooldown
        composeRule.onNodeWithTag("resend_otp_btn").assertIsDisplayed()

        // Invalid OTP should show error and NOT complete registration
        val wrongOtp = if (sentOtp == "000000") "111111" else "000000"
        composeRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextInput(wrongOtp)
        composeRule.onNodeWithTag("signup_verify_otp_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Invalid OTP code", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(completedSignupEmail)

        // Replace with valid OTP and complete verification + registration
        composeRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextReplacement(sentOtp)
        composeRule.onNodeWithTag("signup_verify_otp_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            completedSignupEmail == "trader@zenoai.in"
        }

        assertEquals("trader@zenoai.in", completedSignupEmail)
        composeRule.onNodeWithTag("otp_verified_banner").assertIsDisplayed()
    }
}
