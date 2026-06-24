package com.badgr.orbreader.ui.account

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badgr.orbreader.OrbReaderApp
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.sync.CloudSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AccountUiState {
    object SignedOut                       : AccountUiState()
    object Loading                         : AccountUiState()
    data class SignedIn(val email: String) : AccountUiState()
    data class Error(val message: String)  : AccountUiState()
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val purchaseManager = (application as OrbReaderApp).purchaseManager

    private val _uiState = MutableStateFlow<AccountUiState>(
        if (CloudSyncManager.isSignedIn)
            AccountUiState.SignedIn(CloudSyncManager.currentUser?.email ?: "")
        else
            AccountUiState.SignedOut
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    val isPro: StateFlow<Boolean> = ProGate.isProFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProGate.isPro)

    /** Forwards the active SKU from billing — 'badgr_bolt_pro_lifetime' or 'badgr_bolt_pro_monthly'. */
    val activeSku: StateFlow<String?> = purchaseManager.activeSku
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // TD-007: expose email verification state to UI
    private val _isEmailVerified = MutableStateFlow(
        CloudSyncManager.currentUser?.isEmailVerified == true
    )
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _resendStatus = MutableStateFlow<String?>(null)
    val resendStatus: StateFlow<String?> = _resendStatus.asStateFlow()
    fun clearResendStatus() { _resendStatus.value = null }

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AccountUiState.Error("Email and password are required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AccountUiState.Loading
            try {
                val user = CloudSyncManager.signIn(email.sanitizedEmail(), password)
                _isEmailVerified.value = user.isEmailVerified
                _uiState.value = AccountUiState.SignedIn(user.email ?: "")
            } catch (e: Exception) {
                _uiState.value = AccountUiState.Error(
                    e.localizedMessage ?: "Sign-in failed. Check your credentials."
                )
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AccountUiState.Error("Email and password are required.")
            return
        }
        if (password.length < 6) {
            _uiState.value = AccountUiState.Error("Password must be at least 6 characters.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AccountUiState.Loading
            try {
                val user = CloudSyncManager.signUp(email.sanitizedEmail(), password)
                _isEmailVerified.value = user.isEmailVerified  // false on new signup
                _uiState.value = AccountUiState.SignedIn(user.email ?: "")
            } catch (e: Exception) {
                _uiState.value = AccountUiState.Error(
                    e.localizedMessage ?: "Sign-up failed. Email may already be in use."
                )
            }
        }
    }

    fun signOut() {
        CloudSyncManager.signOut()
        _isEmailVerified.value = false
        _uiState.value = AccountUiState.SignedOut
    }

    fun clearError() { _uiState.value = AccountUiState.SignedOut }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _resendStatus.value = "Enter your email address first."
            return
        }
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email.sanitizedEmail())
                    .await()
                _resendStatus.value = "Password reset email sent — check your inbox."
            } catch (e: Exception) {
                _resendStatus.value = "Could not send reset email. Check the address and try again."
            }
        }
    }

    // TD-007: reload Firebase user to pick up email verification without log-out/in
    fun refreshVerificationStatus() {
        viewModelScope.launch {
            try {
                CloudSyncManager.currentUser?.reload()?.await()
                _isEmailVerified.value = CloudSyncManager.currentUser?.isEmailVerified == true
            } catch (_: Exception) {}
        }
    }

    // TD-007: resend verification email
    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                CloudSyncManager.resendVerificationEmail()
            } catch (e: Exception) {
                // Non-fatal — UI already shows the banner, silently swallow
            }
        }
    }

    fun deleteAccount(password: String) {
        if (password.isBlank()) {
            _resendStatus.value = "Enter your password to delete this account."
            return
        }
        viewModelScope.launch {
            _isDeletingAccount.value = true
            try {
                CloudSyncManager.deleteAccount(password)
                _isEmailVerified.value = false
                _uiState.value = AccountUiState.SignedOut
                _resendStatus.value = "Account deleted."
            } catch (e: Exception) {
                _resendStatus.value = e.localizedMessage
                    ?: "Account deletion failed. Sign in again and retry."
            } finally {
                _isDeletingAccount.value = false
            }
        }
    }

    fun launchSubscription(activity: Activity) = purchaseManager.launchSubscriptionFlow(activity)
    fun launchLifetime(activity: Activity)      = purchaseManager.launchLifetimeFlow(activity)
}

private fun String.sanitizedEmail(): String =
    trim().filter { it.code in 0x20..0x7E }.lowercase()
