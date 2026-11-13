package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserSessionManager
import com.example.data.auth.AuthApiService
import com.example.data.auth.BackendConfig
import com.example.data.auth.BackendUrlStatus
import com.example.data.auth.EmailOtpResult
import com.example.data.auth.EmailVerificationService
import com.example.data.auth.SessionValidationResult
import com.example.data.repository.AccountingRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BackendAuthIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createMockBackend(
        tokenProvider: () -> String? = { null },
        handler: (path: String, body: String, authHeader: String?) -> Pair<Int, String>
    ): AuthApiService {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val bodyStr = buffer.readUtf8()
            val authHeader = request.header("Authorization")
            val (code, payload) = handler(request.url.encodedPath, bodyStr, authHeader)
            val mediaType = if (payload.trimStart().startsWith("<")) "text/html" else "application/json"
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Mock")
                .body(payload.toResponseBody(mediaType.toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val tok = tokenProvider()?.trim().orEmpty()
                val req = if (tok.isNotEmpty()) {
                    chain.request().newBuilder().header("Authorization", "Bearer $tok").build()
                } else {
                    chain.request()
                }
                chain.proceed(req)
            }
            .addInterceptor(interceptor)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.zenoai-test.example.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApiService::class.java)
    }

    @Test
    fun backendConfigValidatesDebugAndReleaseUrlsAndPreservesErrorMapping() {
        // Placeholder & empty URLs must always be rejected (both debug and release)
        for (isDebug in listOf(true, false)) {
            assertTrue(BackendConfig.validateBaseUrl("", isDebugBuild = isDebug) is BackendUrlStatus.NotConfigured)
            assertTrue(BackendConfig.validateBaseUrl("https://REPLACE_WITH_URL", isDebugBuild = isDebug) is BackendUrlStatus.NotConfigured)
            assertTrue(BackendConfig.validateBaseUrl("https://your-deployed-backend.example.com/", isDebugBuild = isDebug) is BackendUrlStatus.NotConfigured)
            assertTrue(BackendConfig.validateBaseUrl("http://placeholder.local:8000/", isDebugBuild = isDebug) is BackendUrlStatus.NotConfigured)
        }

        // Debug builds accept http://10.0.2.2:8000/ and localhost/private local dev URLs
        val debugEmulator = BackendConfig.validateBaseUrl("http://10.0.2.2:8000/", isDebugBuild = true)
        assertTrue(debugEmulator is BackendUrlStatus.Valid)
        assertEquals("http://10.0.2.2:8000/", (debugEmulator as BackendUrlStatus.Valid).normalizedUrl)

        val debugEmulatorNoSlash = BackendConfig.validateBaseUrl("http://10.0.2.2:8000", isDebugBuild = true)
        assertTrue(debugEmulatorNoSlash is BackendUrlStatus.Valid)
        assertEquals("http://10.0.2.2:8000/", (debugEmulatorNoSlash as BackendUrlStatus.Valid).normalizedUrl)

        assertTrue(BackendConfig.validateBaseUrl("http://127.0.0.1:8000/", isDebugBuild = true) is BackendUrlStatus.Valid)
        assertTrue(BackendConfig.validateBaseUrl("http://localhost:8000/", isDebugBuild = true) is BackendUrlStatus.Valid)
        assertTrue(BackendConfig.validateBaseUrl("http://192.168.1.50:8000/", isDebugBuild = true) is BackendUrlStatus.Valid)
        // Debug builds still reject cleartext HTTP for non-local/public domains
        assertTrue(BackendConfig.validateBaseUrl("http://api.zenoai.example.com/", isDebugBuild = true) is BackendUrlStatus.Invalid)

        // Release builds reject 10.0.2.2, localhost, private IPs, and non-HTTPS URLs
        assertTrue(BackendConfig.validateBaseUrl("http://10.0.2.2:8000/", isDebugBuild = false) is BackendUrlStatus.Invalid)
        assertTrue(BackendConfig.validateBaseUrl("http://127.0.0.1:8000/", isDebugBuild = false) is BackendUrlStatus.Invalid)
        assertTrue(BackendConfig.validateBaseUrl("http://localhost:8000/", isDebugBuild = false) is BackendUrlStatus.Invalid)
        assertTrue(BackendConfig.validateBaseUrl("https://192.168.1.50:8000/", isDebugBuild = false) is BackendUrlStatus.Invalid)
        assertTrue(BackendConfig.validateBaseUrl("http://api.zenoai.example.com/", isDebugBuild = false) is BackendUrlStatus.Invalid)

        // Release builds accept valid public HTTPS URLs
        val validRelease = BackendConfig.validateBaseUrl("https://api.zenoai.example.com", isDebugBuild = false)
        assertTrue(validRelease is BackendUrlStatus.Valid)
        assertEquals("https://api.zenoai.example.com/", (validRelease as BackendUrlStatus.Valid).normalizedUrl)

        // Debug BuildConfig default resolves to emulator FastAPI URL http://10.0.2.2:8000/
        assertEquals("http://10.0.2.2:8000/", BuildConfig.ZENO_API_BASE_URL)
        assertEquals("http://10.0.2.2:8000/", BackendConfig.resolveValidBaseUrlOrNull())
        assertNull(BackendConfig.getConfigurationErrorOrNull())

        // Existing network exception mapping preserved
        assertTrue(BackendConfig.mapNetworkException(SocketTimeoutException(), "log in").contains("too long"))
        assertTrue(BackendConfig.mapNetworkException(UnknownHostException(), "log in").contains("resolve"))
        assertTrue(BackendConfig.mapNetworkException(ConnectException(), "log in").contains("offline"))
    }

    @Test
    fun debugNetworkSecurityConfigPermitsCleartextOverride() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val parser = app.resources.getXml(R.xml.network_security_config)
        var foundBaseConfig = false
        var cleartextPermitted: String? = null
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "base-config") {
                foundBaseConfig = true
                cleartextPermitted = parser.getAttributeValue(null, "cleartextTrafficPermitted")
            }
            eventType = parser.next()
        }
        assertTrue(foundBaseConfig)
        assertEquals("true", cleartextPermitted)
    }

    @Test
    fun backendLoginRegistrationResendAndSessionValidationFlow() = runBlocking {
        val dao = FakeAccountingDao()
        var currentToken = ""
        val mockApi = createMockBackend(tokenProvider = { currentToken }) { path, body, authHeader ->
            when {
                path.endsWith("/resend-verification") ->
                    200 to """{"status":"otp_sent","email":"mothish@zenoai.in","message":"6-digit verification code sent to mothish@zenoai.in.","expires_in_seconds":600,"cooldown_seconds":60}"""
                path.endsWith("/register") && body.contains("dup@zenoai.in") ->
                    409 to """{"detail":"An account with dup@zenoai.in already exists."}"""
                path.endsWith("/register") ->
                    200 to """{"id":42,"name":"Mothish","email":"mothish@zenoai.in","company_name":"Zeno Enterprise","is_verified":true,"access_token":"tok.abc.sig","token_type":"bearer","expires_in_seconds":604800}"""
                path.endsWith("/login") && body.contains("unverified@zenoai.in") ->
                    403 to """{"detail":"Your email is not verified yet. Please verify your email before logging in."}"""
                path.endsWith("/login") && body.contains("htmlfail@zenoai.in") ->
                    502 to """<html><body>502 Bad Gateway Traceback</body></html>"""
                path.endsWith("/login") && body.contains("wrongpass") ->
                    401 to """{"detail":"Invalid email address or password."}"""
                path.endsWith("/login") ->
                    200 to """{"id":42,"name":"Mothish","email":"mothish@zenoai.in","company_name":"Zeno Enterprise","is_verified":true,"access_token":"tok.login.sig","token_type":"bearer","expires_in_seconds":604800}"""
                path.endsWith("/auth/me") && authHeader == "Bearer tok.login.sig" ->
                    200 to """{"id":42,"name":"Mothish","email":"mothish@zenoai.in","company_name":"Zeno Enterprise","is_verified":true}"""
                path.endsWith("/auth/me") ->
                    401 to """{"detail":"Invalid authentication token."}"""
                path.endsWith("/forgot-password") ->
                    503 to """{"detail":"Backend SMTP email service is not configured."}"""
                else ->
                    404 to """{"detail":"Not found"}"""
            }
        }

        val service = EmailVerificationService(accountingDao = dao, authApiService = mockApi)

        // Resend verification endpoint
        val resendRes = service.resendVerificationOtp("mothish@zenoai.in")
        assertTrue(resendRes is EmailOtpResult.OtpSent)

        // Confirm password mismatch rejected before calling API
        val mismatchRes = service.syncVerifiedRegistrationWithBackend(
            name = "Mothish",
            email = "mothish@zenoai.in",
            password = "password123",
            companyName = "Zeno Enterprise",
            gstin = "",
            confirmPassword = "differentPassword"
        )
        assertTrue(mismatchRes.isFailure)
        assertTrue(mismatchRes.exceptionOrNull()?.message?.contains("do not match") == true)

        // Successful registration returns bearer access token
        val regRes = service.syncVerifiedRegistrationWithBackend(
            name = "Mothish",
            email = "mothish@zenoai.in",
            password = "password123",
            companyName = "Zeno Enterprise",
            gstin = ""
        )
        assertTrue(regRes.isSuccess)
        assertEquals("tok.abc.sig", regRes.getOrNull()?.accessToken)

        // Unverified login returns clear 403 message
        val unverifiedLogin = service.loginWithBackend("unverified@zenoai.in", "password123")
        assertTrue(unverifiedLogin.isFailure)
        assertTrue(unverifiedLogin.exceptionOrNull()?.message?.contains("not verified", ignoreCase = true) == true)

        // Raw HTML 502 never leaks raw HTML/traceback to UI
        val htmlErrLogin = service.loginWithBackend("htmlfail@zenoai.in", "password123")
        assertTrue(htmlErrLogin.isFailure)
        val sanitizedMsg = htmlErrLogin.exceptionOrNull()?.message.orEmpty()
        assertFalse(sanitizedMsg.contains("<html>"))
        assertFalse(sanitizedMsg.contains("Traceback"))

        // Valid login + authenticated /api/auth/me check + UserSessionManager + Room sync
        val validLogin = service.loginWithBackend("mothish@zenoai.in", "password123")
        assertTrue(validLogin.isSuccess)
        val loggedInDto = validLogin.getOrNull()!!
        currentToken = loggedInDto.accessToken

        val sessionCheck = service.validateSessionWithBackend()
        assertTrue(sessionCheck is SessionValidationResult.Valid)

        currentToken = "expired.token"
        assertTrue(service.validateSessionWithBackend() is SessionValidationResult.Invalid)

        val repo = AccountingRepository(dao)
        val localUser = repo.syncRemoteAuthenticatedUser(
            name = loggedInDto.name,
            email = loggedInDto.email,
            password = "password123",
            companyName = loggedInDto.companyName
        )
        assertEquals("mothish@zenoai.in", localUser.email)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val sessionManager = UserSessionManager(app)
        sessionManager.saveUserSession(
            userId = localUser.id,
            name = localUser.name,
            email = localUser.email,
            companyName = localUser.companyName,
            accessToken = loggedInDto.accessToken,
            isVerified = true,
            authProvider = "EMAIL"
        )
        assertTrue(sessionManager.isLoggedIn())
        assertEquals("tok.login.sig", sessionManager.getAccessToken())

        // Forgot password reports honest backend SMTP error instead of fake success
        val resetRes = service.requestPasswordReset("mothish@zenoai.in")
        assertTrue(resetRes.isFailure)
        assertTrue(resetRes.exceptionOrNull()?.message?.contains("SMTP", ignoreCase = true) == true)
    }

    @Test
    fun authScreenWorksSeamlesslyInStandaloneModeWhenBackendNotConfigured() {
        val dao = FakeAccountingDao()
        val standaloneService = EmailVerificationService(
            accountingDao = dao,
            authApiService = null
        )

        var loginSuccessful = false

        composeRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    emailVerificationService = standaloneService,
                    onLogin = { email, password, onError ->
                        runBlocking {
                            standaloneService.loginWithBackend(email, password).onSuccess {
                                loginSuccessful = true
                            }.onFailure {
                                onError(it.message ?: "Error")
                            }
                        }
                    },
                    onSignup = { _, _, _, _, _, _, _, _ -> },
                    onGoogleSignIn = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("mothish@zenoai.in")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("password123")
        composeRule.onNodeWithTag("login_submit_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(loginSuccessful)
    }
}
