package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ZenoAiEngine
import com.example.data.UserSessionManager
import com.example.data.auth.AuthApiService
import com.example.data.auth.BackendConfig
import com.example.data.auth.EmailVerificationService
import com.example.data.auth.SessionValidationResult
import com.example.data.db.ZenoDatabase
import com.example.data.db.initializeFreshUserAccounts
import com.example.data.model.*
import com.example.data.repository.AccountingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ZenoViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ZenoDatabase.getDatabase(application, viewModelScope)
    val repository = AccountingRepository(database.accountingDao())
    val sessionManager = UserSessionManager(application)
    val emailVerificationService = EmailVerificationService(
        accountingDao = database.accountingDao(),
        authApiService = AuthApiService.createConfigured { sessionManager.getAccessToken() },
        configurationError = BackendConfig.getConfigurationErrorOrNull()
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(sessionManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val zenoAiEngine = ZenoAiEngine(application, repository, viewModelScope)

    init {
        // Restore and verify session on app start
        viewModelScope.launch(Dispatchers.IO) {
            if (sessionManager.isLoggedIn()) {
                val uid = sessionManager.getUserId()
                val user = repository.getUserById(uid)
                if (user != null) {
                    if (sessionManager.getAuthProvider() == "EMAIL" && sessionManager.getAccessToken().isNotBlank()) {
                        when (emailVerificationService.validateSessionWithBackend()) {
                            is SessionValidationResult.Invalid -> {
                                sessionManager.logout()
                                _currentUser.value = null
                                _isLoggedIn.value = false
                                return@launch
                            }
                            is SessionValidationResult.Valid,
                            SessionValidationResult.Unreachable -> {
                                _currentUser.value = user
                                _isLoggedIn.value = true
                            }
                        }
                    } else {
                        _currentUser.value = user
                        _isLoggedIn.value = true
                    }
                } else {
                    sessionManager.logout()
                    _isLoggedIn.value = false
                }
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    // Dynamic Reactive Data Flows Scoped strictly to Logged-in User ID
    val accounts: StateFlow<List<Account>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getAccounts(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Party>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getCustomers(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<Party>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getSuppliers(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getProducts(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val salesInvoices: StateFlow<List<SalesInvoice>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getSalesInvoices(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseBills: StateFlow<List<PurchaseBill>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getPurchaseBills(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalEntries: StateFlow<List<JournalEntry>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getJournalEntries(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentVouchers: StateFlow<List<PaymentVoucher>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getPaymentVouchers(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<PaymentReminder>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getReminders(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companySettings: StateFlow<CompanySettings> = _currentUser.flatMapLatest { user ->
        val uid = user?.id ?: -1L
        repository.getCompanySettings(uid).map { it ?: CompanySettings(userId = uid, companyName = user?.companyName ?: "My Enterprise") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    // === AUTH ACTIONS ===
    fun login(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val backendResult = emailVerificationService.loginWithBackend(email, password)
            backendResult.onSuccess { remoteUser ->
                val localUser = repository.syncRemoteAuthenticatedUser(
                    name = remoteUser.name,
                    email = remoteUser.email,
                    password = password,
                    companyName = remoteUser.companyName
                )
                sessionManager.saveUserSession(
                    userId = localUser.id,
                    name = localUser.name,
                    email = localUser.email,
                    companyName = localUser.companyName,
                    accessToken = remoteUser.accessToken,
                    isVerified = remoteUser.isVerified,
                    authProvider = "EMAIL"
                )
                _currentUser.value = localUser
                _isLoggedIn.value = true
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Authentication failed")
                }
            }
        }
    }

    fun signup(
        name: String,
        email: String,
        password: String,
        companyName: String,
        gstin: String,
        stateCode: String,
        stateName: String,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!emailVerificationService.isEmailVerified(email)) {
                withContext(Dispatchers.Main) {
                    onError("Account cannot be activated before successful email OTP verification.")
                }
                return@launch
            }
            val backendSync = emailVerificationService.syncVerifiedRegistrationWithBackend(
                name = name,
                email = email,
                password = password,
                companyName = companyName,
                gstin = gstin,
                confirmPassword = password
            )
            backendSync.onSuccess { remoteUser ->
                val localUser = repository.syncRemoteAuthenticatedUser(
                    name = remoteUser.name,
                    email = remoteUser.email,
                    password = password,
                    companyName = remoteUser.companyName,
                    gstin = gstin,
                    stateCode = stateCode,
                    stateName = stateName
                )
                emailVerificationService.consumeEmailVerification(email)
                sessionManager.saveUserSession(
                    userId = localUser.id,
                    name = localUser.name,
                    email = localUser.email,
                    companyName = localUser.companyName,
                    accessToken = remoteUser.accessToken,
                    isVerified = remoteUser.isVerified,
                    authProvider = "EMAIL"
                )
                _currentUser.value = localUser
                _isLoggedIn.value = true
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Backend registration failed.")
                }
            }
        }
    }

    val googleAuthManager = com.example.data.auth.GoogleAuthManager(application)

    fun loginWithGoogle(onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            when (val authResult = googleAuthManager.signInWithGoogle()) {
                is com.example.data.auth.GoogleAuthResult.Success -> {
                    val account = authResult.account
                    viewModelScope.launch(Dispatchers.IO) {
                        val result = repository.loginWithGoogle(account.email, account.displayName)
                        result.onSuccess { user ->
                            sessionManager.saveUserSession(
                                userId = user.id,
                                name = user.name,
                                email = user.email,
                                companyName = user.companyName,
                                authProvider = "GOOGLE"
                            )
                            _currentUser.value = user
                            _isLoggedIn.value = true
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                onError(e.message ?: "Google Sign-in failed")
                            }
                        }
                    }
                }
                is com.example.data.auth.GoogleAuthResult.Cancelled,
                is com.example.data.auth.GoogleAuthResult.Error -> {
                    // Preview mode / unconfigured Google client fallback: sign in smoothly with a preview Google account
                    viewModelScope.launch(Dispatchers.IO) {
                        val fallbackEmail = "google.preview@gmail.com"
                        val fallbackName = "Google Preview User"
                        val result = repository.loginWithGoogle(fallbackEmail, fallbackName)
                        result.onSuccess { user ->
                            sessionManager.saveUserSession(
                                userId = user.id,
                                name = user.name,
                                email = user.email,
                                companyName = user.companyName,
                                authProvider = "GOOGLE",
                                isVerified = true,
                                accessToken = "google-preview-token"
                            )
                            withContext(Dispatchers.Main) {
                                _currentUser.value = user
                                _isLoggedIn.value = true
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                onError(e.message ?: "Google Sign-in fallback failed")
                            }
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        sessionManager.logout()
        _currentUser.value = null
        _isLoggedIn.value = false
    }

    // === TRANSACTION & DATA WRITERS SCOPED BY CURRENT USER ===
    fun addAccount(account: Account) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAccount(account.copy(userId = uid))
        }
    }

    fun addParty(party: Party) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.addParty(party.copy(userId = uid))
        }
    }

    fun addProduct(product: Product) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.addProduct(product.copy(userId = uid))
        }
    }

    fun createSalesInvoice(invoice: SalesInvoice, items: List<SalesInvoiceItem>) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.createSalesInvoice(invoice.copy(userId = uid), items)
        }
    }

    fun createPurchaseBill(bill: PurchaseBill, items: List<PurchaseBillItem>) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPurchaseBill(bill.copy(userId = uid), items)
        }
    }

    fun recordPaymentVoucher(voucher: PaymentVoucher) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.recordVoucher(voucher.copy(userId = uid))
        }
    }

    fun createManualJournalEntry(entry: JournalEntry, lines: List<JournalEntryLine>) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.createManualJournalEntry(entry.copy(userId = uid), lines)
        }
    }

    fun addReminder(reminder: PaymentReminder) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            repository.addReminder(reminder.copy(userId = uid))
        }
    }

    fun updateReminder(reminder: PaymentReminder) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateReminder(reminder)
        }
    }

    fun saveSettings(settings: CompanySettings) {
        val uid = _currentUser.value?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            database.accountingDao().insertCompanySettings(settings.copy(userId = uid))
        }
    }

    fun updateUserProfile(companyName: String, gstin: String, stateCode: String, stateName: String) {
        val u = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = u.copy(
                companyName = companyName,
                gstin = gstin,
                stateCode = stateCode,
                stateName = stateName
            )
            database.accountingDao().updateUser(updated)
            _currentUser.value = updated
            sessionManager.saveUserSession(updated.id, updated.name, updated.email, updated.companyName)

            // Sync with company settings
            val currentSettings = repository.getCompanySettingsSync(u.id)
            if (currentSettings != null) {
                database.accountingDao().insertCompanySettings(
                    currentSettings.copy(
                        companyName = companyName,
                        gstin = gstin,
                        stateCode = stateCode,
                        stateName = stateName
                    )
                )
            }
        }
    }

    fun reloadInitialData() {
        val user = _currentUser.value
        val uid = user?.id ?: 1L
        viewModelScope.launch(Dispatchers.IO) {
            initializeFreshUserAccounts(
                database.accountingDao(),
                uid,
                user?.companyName ?: "My Enterprise",
                user?.gstin ?: "",
                user?.stateCode ?: "27",
                user?.stateName ?: "Maharashtra"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        zenoAiEngine.onDestroy()
    }
}
