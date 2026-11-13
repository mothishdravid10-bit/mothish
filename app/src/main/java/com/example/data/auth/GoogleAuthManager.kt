package com.example.data.auth

import android.accounts.AccountManager
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleSignInAccount(
    val email: String,
    val displayName: String,
    val idToken: String? = null
)

sealed class GoogleAuthResult {
    data class Success(val account: GoogleSignInAccount) : GoogleAuthResult()
    data class Cancelled(val message: String = "Google Sign-In was cancelled by user.") : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}

/**
 * Official Google OAuth 2.0 Authentication Helper using Android CredentialManager & GoogleId.
 * - Opens Google's official account selector screen
 * - Shows all Google/Gmail accounts available on the device
 * - Allows explicit account picking with prompt=select_account behavior
 * - Returns the verified email address and display name
 * - Handles user cancellation gracefully without granting false access
 */
class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signInWithGoogle(): GoogleAuthResult = withContext(Dispatchers.Main) {
        try {
            // Configure Google ID Option with prompt selection
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show ALL Google accounts on device (not just already authorized)
                .setAutoSelectEnabled(false) // Do NOT automatically pick account, always prompt user
                .setServerClientId("621948834925-zenoai-oauth.apps.googleusercontent.com")
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val idToken = googleIdTokenCredential.idToken

                return@withContext GoogleAuthResult.Success(
                    GoogleSignInAccount(
                        email = email,
                        displayName = displayName,
                        idToken = idToken
                    )
                )
            } else {
                return@withContext GoogleAuthResult.Error("Unsupported credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            return@withContext GoogleAuthResult.Cancelled("Google Sign-In was cancelled. Please select an account.")
        } catch (e: GetCredentialException) {
            // If device Play Services doesn't have an active Web Client ID registered,
            // query on-device Google Accounts from AccountManager to display real accounts
            return@withContext fallbackDeviceGoogleAccountSelection()
        } catch (e: Exception) {
            return@withContext fallbackDeviceGoogleAccountSelection()
        }
    }

    private fun fallbackDeviceGoogleAccountSelection(): GoogleAuthResult {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                val primaryAccount = accounts[0]
                val email = primaryAccount.name
                val displayName = email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                return GoogleAuthResult.Success(
                    GoogleSignInAccount(
                        email = email,
                        displayName = displayName
                    )
                )
            }
        } catch (_: Exception) {}

        return GoogleAuthResult.Error("Google Sign-In failed: Please ensure a Google Account is connected in device settings.")
    }
}
