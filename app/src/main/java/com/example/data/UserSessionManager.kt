package com.example.data

import android.content.Context
import android.content.SharedPreferences

class UserSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zeno_user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_COMPANY_NAME = "company_name"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_VERIFIED = "is_verified"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
    }

    fun saveUserSession(
        userId: Long,
        name: String,
        email: String,
        companyName: String,
        accessToken: String = "",
        isVerified: Boolean = true,
        authProvider: String = "EMAIL"
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putLong(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_COMPANY_NAME, companyName)
            putString(KEY_ACCESS_TOKEN, accessToken)
            putBoolean(KEY_IS_VERIFIED, isVerified)
            putString(KEY_AUTH_PROVIDER, authProvider)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "User") ?: "User"

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun getCompanyName(): String = prefs.getString(KEY_COMPANY_NAME, "My Enterprise") ?: "My Enterprise"

    fun getAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""

    fun isVerified(): Boolean = prefs.getBoolean(KEY_IS_VERIFIED, false)

    fun getAuthProvider(): String = prefs.getString(KEY_AUTH_PROVIDER, "EMAIL") ?: "EMAIL"

    fun logout() {
        prefs.edit().clear().apply()
    }
}
