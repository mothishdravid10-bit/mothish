package com.example.data.auth

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ApiErrorResponse(
    @Json(name = "detail") val detail: String? = null
)

data class SendOtpRequest(
    @Json(name = "email") val email: String
)

data class SendOtpResponse(
    @Json(name = "status") val status: String,
    @Json(name = "email") val email: String,
    @Json(name = "message") val message: String,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int = 600,
    @Json(name = "cooldown_seconds") val cooldownSeconds: Int = 60
)

data class VerifyOtpRequest(
    @Json(name = "email") val email: String,
    @Json(name = "otp") val otp: String
)

data class VerifyOtpResponse(
    @Json(name = "status") val status: String,
    @Json(name = "email") val email: String,
    @Json(name = "message") val message: String
)

data class RegisterUserRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "confirm_password") val confirmPassword: String? = null,
    @Json(name = "company_name") val companyName: String = "My Enterprise",
    @Json(name = "gstin") val gstin: String = ""
)

data class LoginUserRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

data class AuthUserResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "company_name") val companyName: String,
    @Json(name = "is_verified") val isVerified: Boolean,
    @Json(name = "access_token") val accessToken: String = "",
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "expires_in_seconds") val expiresInSeconds: Long = 0L
)

data class StatusMessageResponse(
    @Json(name = "status") val status: String,
    @Json(name = "email") val email: String = "",
    @Json(name = "message") val message: String
)

data class HealthCheckResponse(
    @Json(name = "status") val status: String,
    @Json(name = "database") val database: String = "",
    @Json(name = "smtp_configured") val smtpConfigured: Boolean = false
)

data class ZenoChatRequest(
    @Json(name = "query") val query: String,
    @Json(name = "session_id") val sessionId: String = "android",
    @Json(name = "tz_offset_minutes") val tzOffsetMinutes: Int? = null,
    @Json(name = "reset_context") val resetContext: Boolean = false
)

data class ZenoChatDateRangeDto(
    @Json(name = "label") val label: String,
    @Json(name = "start") val start: String,
    @Json(name = "end") val end: String
)

data class ZenoChatTransactionDto(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String,
    @Json(name = "category") val category: String,
    @Json(name = "reference") val reference: String = "",
    @Json(name = "party") val party: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "payment_mode") val paymentMode: String = "CREDIT",
    @Json(name = "status") val status: String = "",
    @Json(name = "date") val date: String = ""
)

data class ZenoChatResponse(
    @Json(name = "intent") val intent: String,
    @Json(name = "response") val response: String,
    @Json(name = "date_range") val dateRange: ZenoChatDateRangeDto? = null,
    @Json(name = "summary") val summary: Map<String, Any?> = emptyMap(),
    @Json(name = "transactions") val transactions: List<ZenoChatTransactionDto> = emptyList(),
    @Json(name = "follow_up_question") val followUpQuestion: String? = null
)

interface AuthApiService {
    @GET("api/health")
    suspend fun health(): Response<HealthCheckResponse>

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterUserRequest): Response<AuthUserResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginUserRequest): Response<AuthUserResponse>

    @GET("api/auth/me")
    suspend fun currentUser(): Response<AuthUserResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: SendOtpRequest): Response<StatusMessageResponse>

    @POST("api/ai/zeno/chat")
    suspend fun zenoChat(@Body request: ZenoChatRequest): Response<ZenoChatResponse>

    companion object {
        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }

        private val errorAdapter by lazy {
            moshi.adapter(ApiErrorResponse::class.java)
        }

        fun createConfigured(tokenProvider: (() -> String?)? = null): AuthApiService? {
            val validUrl = BackendConfig.resolveValidBaseUrlOrNull() ?: return null
            return create(validUrl, tokenProvider)
        }

        fun create(
            baseUrl: String,
            tokenProvider: (() -> String?)? = null
        ): AuthApiService {
            val trimmed = baseUrl.trim()
            require(trimmed.isNotEmpty()) { "Backend baseUrl must not be blank." }
            val normalizedUrl = if (trimmed.endsWith("/")) trimmed else "$trimmed/"

            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)

            if (tokenProvider != null) {
                clientBuilder.addInterceptor { chain ->
                    val token = tokenProvider()?.trim().orEmpty()
                    val request = if (token.isNotEmpty()) {
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        chain.request()
                    }
                    chain.proceed(request)
                }
            }

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(clientBuilder.build())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(AuthApiService::class.java)
        }

        fun extractErrorDetail(errorBody: ResponseBody?, fallback: String): String {
            return try {
                val raw = errorBody?.string()?.trim().orEmpty()
                if (raw.isEmpty() || raw.startsWith("<")) return fallback
                val parsed = errorAdapter.fromJson(raw)?.detail?.trim()
                if (parsed.isNullOrEmpty() || parsed.length > 300) fallback else parsed
            } catch (_: Exception) {
                fallback
            }
        }
    }
}
