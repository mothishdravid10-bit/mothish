package com.example.data.auth

import com.example.BuildConfig
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

sealed class BackendUrlStatus {
    data class Valid(val normalizedUrl: String) : BackendUrlStatus()
    data class NotConfigured(val message: String) : BackendUrlStatus()
    data class Invalid(val message: String) : BackendUrlStatus()
}

/**
 * Centralized API base URL configuration and network error translator.
 * Permits emulator/localhost/private URLs in debug builds for local development,
 * while strictly requiring public HTTPS URLs in release builds.
 */
object BackendConfig {

    private val privateIpPattern = Regex(
        "^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3})$"
    )

    fun configuredBaseUrl(): String = BuildConfig.ZENO_API_BASE_URL.trim()

    fun isPrivateOrEmulatorHost(host: String): Boolean {
        val h = host.trim().removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
        if (h.isEmpty()) return true
        if (h in setOf("10.0.2.2", "10.0.3.2", "localhost", "127.0.0.1", "0.0.0.0", "::1")) {
            return true
        }
        return privateIpPattern.matches(h)
    }

    fun validateBaseUrl(
        rawUrl: String,
        isDebugBuild: Boolean = BuildConfig.DEBUG
    ): BackendUrlStatus {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() ||
            trimmed.contains("REPLACE_WITH", ignoreCase = true) ||
            trimmed.contains("YOUR_", ignoreCase = true) ||
            trimmed.contains("your-backend", ignoreCase = true) ||
            trimmed.contains("your-deployed-backend", ignoreCase = true) ||
            trimmed.contains("placeholder", ignoreCase = true)
        ) {
            return BackendUrlStatus.NotConfigured(
                "ZENO backend server URL is not configured. Running in Standalone Preview Mode."
            )
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return BackendUrlStatus.Invalid("Configured backend URL is malformed. Expected an https:// public endpoint.")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()

        if (host.isEmpty()) {
            return BackendUrlStatus.Invalid("Configured backend URL is missing a valid domain host.")
        }

        val isLocalOrPrivate = isPrivateOrEmulatorHost(host)

        if (!isDebugBuild) {
            if (isLocalOrPrivate) {
                return BackendUrlStatus.Invalid(
                    "Emulator or private backend URL ($host) cannot be reached in production. Please deploy the FastAPI backend and set ZENO_API_BASE_URL to your public HTTPS URL."
                )
            }
            if (scheme != "https") {
                return BackendUrlStatus.Invalid(
                    "Production backend connection requires HTTPS (found '$scheme')."
                )
            }
        } else {
            if (isLocalOrPrivate) {
                if (scheme !in setOf("http", "https")) {
                    return BackendUrlStatus.Invalid(
                        "Local development backend URL must use HTTP or HTTPS (found '$scheme')."
                    )
                }
            } else if (scheme != "https") {
                return BackendUrlStatus.Invalid(
                    "Public backend connection requires HTTPS (found '$scheme')."
                )
            }
        }

        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return BackendUrlStatus.Valid(normalized)
    }

    fun resolveValidBaseUrlOrNull(
        overrideUrl: String? = null,
        isDebugBuild: Boolean = BuildConfig.DEBUG
    ): String? {
        val candidate = overrideUrl?.trim() ?: configuredBaseUrl()
        return when (val status = validateBaseUrl(candidate, isDebugBuild)) {
            is BackendUrlStatus.Valid -> status.normalizedUrl
            else -> null
        }
    }

    fun getConfigurationErrorOrNull(
        overrideUrl: String? = null,
        isDebugBuild: Boolean = BuildConfig.DEBUG
    ): String? {
        val candidate = overrideUrl?.trim() ?: configuredBaseUrl()
        return when (val status = validateBaseUrl(candidate, isDebugBuild)) {
            is BackendUrlStatus.Valid -> null
            is BackendUrlStatus.NotConfigured -> status.message
            is BackendUrlStatus.Invalid -> status.message
        }
    }

    fun mapNetworkException(error: Throwable, actionLabel: String): String {
        return when (error) {
            is SocketTimeoutException ->
                "The ZENO server took too long to respond while trying to $actionLabel. Please check your connection and try again."
            is UnknownHostException ->
                "Unable to resolve the ZENO backend server. Please verify your internet connection and deployed backend URL."
            is ConnectException ->
                "ZENO backend server is currently offline or unreachable. Please verify the deployed FastAPI service is running."
            is SSLException ->
                "Secure HTTPS connection to the ZENO backend failed. Please verify the server's TLS certificate."
            is IOException ->
                "Network error while trying to $actionLabel. Please check your internet connection and retry."
            else ->
                "Unable to $actionLabel right now. Please try again shortly."
        }
    }
}
