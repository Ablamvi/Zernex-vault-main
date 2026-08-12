package com.zernex.vault.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zernex.vault.data.LockType
import com.zernex.vault.data.SecurePrefs
import com.zernex.vault.data.VaultCategory
import com.zernex.vault.data.VaultItem
import com.zernex.vault.data.VaultRepository
import com.zernex.vault.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    SETUP_WELCOME,
    SETUP_LOCK_TYPE,
    SETUP_PIN,
    SETUP_PATTERN,
    SETUP_RECOVERY_Q,
    SETUP_RECOVERY_KEY,
    LOCK,
    RECOVERY_CHOICE,
    RECOVERY_QUESTIONS,
    RECOVERY_KEY,
    RESET_LOCK,
    VAULT_HOME
}

data class VaultUiState(
    val screen: AppScreen = AppScreen.SETUP_WELCOME,
    val lockType: LockType = LockType.PIN,
    val items: List<VaultItem> = emptyList(),
    val selectedCategory: VaultCategory? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val recoveryKeyShown: String? = null,
    val isUnlocked: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutRemainingMs: Long = 0L
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurePrefs(application)
    private val repository = VaultRepository(application)
    private val _ui = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _ui.asStateFlow()

    // Setup temp
    private var pendingLockType = LockType.PIN
    private var pendingSecret = ""
    private var pendingQ1 = ""
    private var pendingA1 = ""
    private var pendingQ2 = ""
    private var pendingA2 = ""

    init {
        if (prefs.isSetupComplete) {
            _ui.update {
                it.copy(
                    screen = AppScreen.LOCK,
                    lockType = prefs.lockType,
                    failedAttempts = prefs.failedAttempts
                )
            }
            checkLockout()
        } else {
            _ui.update { it.copy(screen = AppScreen.SETUP_WELCOME) }
        }
        viewModelScope.launch {
            repository.items.collect { list ->
                _ui.update { it.copy(items = list) }
            }
        }
    }

    private fun checkLockout() {
        val until = prefs.lockoutUntil
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0) {
            _ui.update { it.copy(lockoutRemainingMs = remaining) }
        } else {
            prefs.lockoutUntil = 0
            _ui.update { it.copy(lockoutRemainingMs = 0) }
        }
    }

    // ——— Setup ———

    fun startSetup() {
        _ui.update { it.copy(screen = AppScreen.SETUP_LOCK_TYPE) }
    }

    fun chooseLockType(type: LockType) {
        pendingLockType = type
        _ui.update {
            it.copy(
                lockType = type,
                screen = if (type == LockType.PIN) AppScreen.SETUP_PIN else AppScreen.SETUP_PATTERN
            )
        }
    }

    fun submitSetupSecret(secret: String, confirm: String) {
        if (secret != confirm) {
            _ui.update { it.copy(error = "Les deux saisies ne correspondent pas") }
            return
        }
        if (pendingLockType == LockType.PIN && secret.length < 4) {
            _ui.update { it.copy(error = "PIN : minimum 4 chiffres") }
            return
        }
        if (pendingLockType == LockType.PATTERN && secret.length < 4) {
            _ui.update { it.copy(error = "Schéma : minimum 4 points") }
            return
        }
        pendingSecret = secret
        _ui.update { it.copy(error = null, screen = AppScreen.SETUP_RECOVERY_Q) }
    }

    fun submitRecoveryQuestions(q1: String, a1: String, q2: String, a2: String) {
        if (q1.isBlank() || a1.isBlank() || q2.isBlank() || a2.isBlank()) {
            _ui.update { it.copy(error = "Remplis toutes les questions et réponses") }
            return
        }
        pendingQ1 = q1
        pendingA1 = a1
        pendingQ2 = q2
        pendingA2 = a2
        val key = CryptoManager.generateRecoveryKey()
        _ui.update {
            it.copy(
                error = null,
                recoveryKeyShown = key,
                screen = AppScreen.SETUP_RECOVERY_KEY
            )
        }
    }

    fun finishSetup() {
        val key = _ui.value.recoveryKeyShown ?: return
        prefs.lockType = pendingLockType
        prefs.setLockSecret(pendingSecret)
        prefs.setRecoveryQuestions(pendingQ1, pendingA1, pendingQ2, pendingA2)
        prefs.setRecoveryKey(key)
        prefs.isSetupComplete = true
        prefs.failedAttempts = 0
        _ui.update {
            it.copy(
                screen = AppScreen.VAULT_HOME,
                isUnlocked = true,
                recoveryKeyShown = null,
                lockType = pendingLockType
            )
        }
        viewModelScope.launch { repository.loadIndex() }
    }

    // ——— Unlock ———

    fun unlock(secret: String) {
        checkLockout()
        if (_ui.value.lockoutRemainingMs > 0) {
            _ui.update { it.copy(error = "Trop d’essais. Réessaie plus tard.") }
            return
        }
        if (prefs.verifyLockSecret(secret)) {
            prefs.failedAttempts = 0
            _ui.update {
                it.copy(
                    isUnlocked = true,
                    screen = AppScreen.VAULT_HOME,
                    error = null,
                    failedAttempts = 0
                )
            }
            viewModelScope.launch { repository.loadIndex() }
        } else {
            val fails = prefs.failedAttempts + 1
            prefs.failedAttempts = fails
            if (fails >= 5) {
                val lockMs = when {
                    fails >= 10 -> 15 * 60_000L
                    fails >= 8 -> 5 * 60_000L
                    else -> 60_000L
                }
                prefs.lockoutUntil = System.currentTimeMillis() + lockMs
                _ui.update {
                    it.copy(
                        failedAttempts = fails,
                        lockoutRemainingMs = lockMs,
                        error = "Verrouillé temporairement"
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        failedAttempts = fails,
                        error = "Code incorrect ($fails/5)"
                    )
                }
            }
        }
    }

    fun lock() {
        _ui.update {
            it.copy(
                isUnlocked = false,
                screen = AppScreen.LOCK,
                error = null
            )
        }
    }

    // ——— Recovery ———

    fun openRecovery() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_CHOICE, error = null) }
    }

    fun openRecoveryQuestions() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_QUESTIONS) }
    }

    fun openRecoveryKey() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_KEY) }
    }

    fun verifyRecoveryAnswers(a1: String, a2: String) {
        if (prefs.verifyAnswers(a1, a2)) {
            _ui.update { it.copy(screen = AppScreen.RESET_LOCK, error = null) }
        } else {
            _ui.update { it.copy(error = "Réponses incorrectes") }
        }
    }

    fun verifyRecoveryKeyInput(key: String) {
        if (prefs.verifyRecoveryKey(key)) {
            _ui.update { it.copy(screen = AppScreen.RESET_LOCK, error = null) }
        } else {
            _ui.update { it.copy(error = "Clé de récupération invalide") }
        }
    }

    fun resetLock(newSecret: String, confirm: String, type: LockType) {
        if (newSecret != confirm) {
            _ui.update { it.copy(error = "Les deux saisies ne correspondent pas") }
            return
        }
        prefs.lockType = type
        prefs.setLockSecret(newSecret)
        prefs.failedAttempts = 0
        prefs.lockoutUntil = 0
        _ui.update {
            it.copy(
                lockType = type,
                screen = AppScreen.LOCK,
                error = null,
                message = "Verrouillage réinitialisé. Connecte-toi."
            )
        }
    }

    fun backToLock() {
        _ui.update { it.copy(screen = AppScreen.LOCK, error = null) }
    }

    // ——— Vault ———

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            uris.forEach { uri ->
                repository.importUri(uri)
            }
            _ui.update { it.copy(isLoading = false, message = "${uris.size} fichier(s) importé(s)") }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    fun setCategory(cat: VaultCategory?) {
        _ui.update { it.copy(selectedCategory = cat) }
    }

    fun setSearch(q: String) {
        _ui.update { it.copy(searchQuery = q) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }

    fun getQuestion1() = prefs.getQuestion1()
    fun getQuestion2() = prefs.getQuestion2()

    fun filteredItems(): List<VaultItem> {
        val state = _ui.value
        return state.items.filter { item ->
            val catOk = state.selectedCategory == null || item.category == state.selectedCategory
            val searchOk = state.searchQuery.isBlank() ||
                    item.displayName.contains(state.searchQuery, ignoreCase = true)
            catOk && searchOk
        }
    }
}
