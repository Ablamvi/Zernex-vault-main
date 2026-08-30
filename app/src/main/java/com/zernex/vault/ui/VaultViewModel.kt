package com.zernex.vault.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zernex.vault.data.ImportProgress
import com.zernex.vault.data.LockType
import com.zernex.vault.data.SecurePrefs
import com.zernex.vault.data.VaultCategory
import com.zernex.vault.data.SortMode
import com.zernex.vault.data.VaultItem
import com.zernex.vault.data.VaultRepository
import com.zernex.vault.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    VAULT_HOME,
    PREVIEW,
    SETTINGS
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
    val lockoutRemainingMs: Long = 0L,
    val biometricEnabled: Boolean = true,
    /** Empêche le lock auto pendant SAF / partage. */
    val awaitingExternalResult: Boolean = false,
    val importProgress: ImportProgress? = null,
    val previewItem: VaultItem? = null,
    val previewFile: File? = null,
    val shareFile: File? = null,
    val shareMime: String? = null,
    /** true = déplacer (supprimer l'original après import) */
    val moveOnImport: Boolean = true,
    val sortMode: SortMode = SortMode.DATE_DESC,
    val gridMode: Boolean = true,
    val vaultSizeLabel: String = "",
    /** Multi-sélection */
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** Filtre favoris uniquement */
    val favoritesOnly: Boolean = false,
    /** Auto-lock delay ms */
    val autoLockMs: Long = 0L,
    val showSettings: Boolean = false
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurePrefs(application)
    private val repository = VaultRepository(application)
    private val _ui = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _ui.asStateFlow()

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
                    failedAttempts = prefs.failedAttempts,
                    biometricEnabled = prefs.biometricEnabled,
                    gridMode = prefs.gridMode,
                    sortMode = prefs.sortMode,
                    autoLockMs = prefs.autoLockMs,
                    favoritesOnly = prefs.favoritesOnly,
                    moveOnImport = prefs.moveOnImport,
                    selectedCategory = prefs.selectedCategory
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
        _ui.update { it.copy(screen = AppScreen.SETUP_LOCK_TYPE, error = null) }
    }

    fun chooseLockType(type: LockType) {
        pendingLockType = type
        _ui.update {
            it.copy(
                lockType = type,
                error = null,
                screen = if (type == LockType.PIN) AppScreen.SETUP_PIN else AppScreen.SETUP_PATTERN
            )
        }
    }

    fun submitSetupSecret(secret: String, confirm: String) {
        if (secret != confirm) {
            _ui.update { it.copy(error = "Les deux saisies ne correspondent pas") }
            return
        }
        if (pendingLockType == LockType.PIN && (secret.length < 4 || secret.length > 8 || !secret.all { it.isDigit() })) {
            _ui.update { it.copy(error = "PIN : 4 à 8 chiffres") }
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
            it.copy(error = null, recoveryKeyShown = key, screen = AppScreen.SETUP_RECOVERY_KEY)
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
        prefs.biometricEnabled = true
        _ui.update {
            it.copy(
                screen = AppScreen.VAULT_HOME,
                isUnlocked = true,
                recoveryKeyShown = null,
                lockType = pendingLockType,
                biometricEnabled = true,
                error = null
            )
        }
        viewModelScope.launch {
            repository.loadIndex()
            repository.backfillThumbnails()
            refreshSize()
        }
    }

    // ——— Unlock / Lock ———

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
            viewModelScope.launch {
                repository.loadIndex()
                repository.backfillThumbnails()
                refreshSize()
            }
        } else {
            onFailedUnlock()
        }
    }

    /** Déverrouillage réussi via biométrie (empreinte / face). */
    fun unlockWithBiometric() {
        prefs.failedAttempts = 0
        _ui.update {
            it.copy(
                isUnlocked = true,
                screen = AppScreen.VAULT_HOME,
                error = null,
                failedAttempts = 0
            )
        }
        viewModelScope.launch {
            repository.loadIndex()
            repository.backfillThumbnails()
            refreshSize()
        }
    }

    private fun onFailedUnlock() {
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
                it.copy(failedAttempts = fails, error = "Code incorrect ($fails/5)")
            }
        }
    }

    fun lock() {
        repository.clearOpenCache()
        _ui.update {
            it.copy(
                isUnlocked = false,
                screen = AppScreen.LOCK,
                error = null,
                previewItem = null,
                previewFile = null,
                shareFile = null,
                shareMime = null,
                importProgress = null,
                selectionMode = false,
                selectedIds = emptySet(),
                showSettings = false
            )
        }
    }

    /**
     * Appelé depuis MainActivity.onStop.
     * Ne verrouille PAS si on attend un résultat externe (SAF import/export).
     */
    fun onAppBackground() {
        if (_ui.value.isUnlocked && !_ui.value.awaitingExternalResult) {
            lock()
        }
    }

    fun setAwaitingExternalResult(awaiting: Boolean) {
        _ui.update { it.copy(awaitingExternalResult = awaiting) }
    }

    // ——— Recovery ———

    fun openRecovery() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_CHOICE, error = null) }
    }

    fun openRecoveryQuestions() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_QUESTIONS, error = null) }
    }

    fun openRecoveryKey() {
        _ui.update { it.copy(screen = AppScreen.RECOVERY_KEY, error = null) }
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
            _ui.update { it.copy(error = "Clé de récupération incorrecte") }
        }
    }

    fun resetLock(newSecret: String, confirm: String, type: LockType) {
        if (newSecret != confirm) {
            _ui.update { it.copy(error = "Les deux saisies ne correspondent pas") }
            return
        }
        if (type == LockType.PIN && (newSecret.length < 4 || !newSecret.all { it.isDigit() })) {
            _ui.update { it.copy(error = "PIN : minimum 4 chiffres") }
            return
        }
        if (type == LockType.PATTERN && newSecret.length < 4) {
            _ui.update { it.copy(error = "Schéma : minimum 4 points") }
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

    // ——— Vault actions ———

    fun setMoveOnImport(move: Boolean) {
        prefs.moveOnImport = move
        _ui.update { it.copy(moveOnImport = move) }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) {
            setAwaitingExternalResult(false)
            return
        }
        val move = _ui.value.moveOnImport
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isLoading = true,
                    importProgress = ImportProgress(0, uris.size, ""),
                    error = null
                )
            }
            var ok = 0
            var deleted = 0
            uris.forEachIndexed { index, uri ->
                val name = uri.lastPathSegment ?: "fichier"
                _ui.update {
                    it.copy(importProgress = ImportProgress(index + 1, uris.size, name))
                }
                val (item, origDeleted) = repository.importUri(uri, deleteOriginal = move)
                if (item != null) {
                    ok++
                    if (origDeleted) deleted++
                }
            }
            setAwaitingExternalResult(false)
            val msg = when {
                ok == 0 -> "Aucun fichier importé"
                move && deleted > 0 -> "$ok fichier(s) déplacé(s) dans le coffre ($deleted retiré(s) du téléphone)"
                move && deleted == 0 -> "$ok fichier(s) dans le coffre. Originals non supprimés (permission système) — supprime-les manuellement de la galerie si besoin."
                else -> "$ok fichier(s) copié(s) dans le coffre (toujours visibles hors de l'app)"
            }
            refreshSize()
            _ui.update {
                it.copy(isLoading = false, importProgress = null, message = msg)
            }
        }
    }

    /** Remet le fichier dans Galerie/Téléchargements et optionnellement le retire du coffre. */
    fun restoreToDevice(item: VaultItem, removeFromVault: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val uri = repository.restoreToDevice(item, removeFromVault)
            _ui.update {
                it.copy(
                    isLoading = false,
                    message = if (uri != null) {
                        if (removeFromVault) "Fichier restauré sur le téléphone et retiré du coffre"
                        else "Fichier restauré sur le téléphone (copie)"
                    } else "Impossible de restaurer ce fichier",
                    screen = if (removeFromVault && it.previewItem?.id == item.id) AppScreen.VAULT_HOME else it.screen,
                    previewItem = if (removeFromVault) null else it.previewItem,
                    previewFile = if (removeFromVault) null else it.previewFile
                )
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
            _ui.update { it.copy(message = "Fichier supprimé") }
        }
    }

    fun openItem(item: VaultItem) {
        // Ne pas ouvrir pendant la sélection multiple
        if (_ui.value.selectionMode) {
            toggleSelect(item.id)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null, message = "Ouverture…") }
            val file = repository.decryptToCache(item.id, item.displayName)
            if (file != null && file.exists() && file.length() > 0) {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        previewItem = item,
                        previewFile = file,
                        screen = AppScreen.PREVIEW
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = "Impossible d’ouvrir « ${item.displayName} ». Réessaie ou réimporte le fichier."
                    )
                }
            }
        }
    }

    fun closePreview() {
        _ui.update {
            it.copy(
                screen = AppScreen.VAULT_HOME,
                previewItem = null,
                previewFile = null
            )
        }
    }

    private var adjacentNavigationJob: kotlinx.coroutines.Job? = null

    private fun openAdjacentMedia(step: Int) {
        val state = _ui.value
        val current = state.previewItem ?: return
        val visibleItems = filteredItems()
        val index = visibleItems.indexOfFirst { it.id == current.id }
        if (index < 0) return

        val targetIndex = index + step
        if (targetIndex !in visibleItems.indices) {
            _ui.update { it.copy(message = if (step > 0) "Dernier fichier" else "Premier fichier") }
            return
        }

        val target = visibleItems[targetIndex]
        if (adjacentNavigationJob?.isActive == true) return

        adjacentNavigationJob = viewModelScope.launch {
            val oldItem = _ui.value.previewItem
            _ui.update { it.copy(isLoading = true, error = null, message = null) }
            val targetFile = repository.decryptToCache(target.id, target.displayName)
            if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        previewItem = target,
                        previewFile = targetFile,
                        screen = AppScreen.PREVIEW
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = "Impossible d’ouvrir « ${target.displayName} ».",
                        previewItem = oldItem
                    )
                }
            }
        }
    }

    fun openNextMedia() = openAdjacentMedia(+1)

    fun openPreviousMedia() = openAdjacentMedia(-1)

    fun prepareShare(item: VaultItem) {
        viewModelScope.launch {
            setAwaitingExternalResult(true)
            _ui.update { it.copy(isLoading = true) }
            val file = repository.decryptToCache(item.id, item.displayName)
            _ui.update {
                it.copy(
                    isLoading = false,
                    shareFile = file,
                    shareMime = item.mimeType,
                    error = if (file == null) "Export impossible" else null
                )
            }
            if (file == null) setAwaitingExternalResult(false)
        }
    }

    fun onShareDone() {
        _ui.update { it.copy(shareFile = null, shareMime = null) }
        setAwaitingExternalResult(false)
    }

    fun setCategory(cat: VaultCategory?) {
        prefs.selectedCategory = cat
        _ui.update { it.copy(selectedCategory = cat) }
    }

    fun setSearch(q: String) {
        _ui.update { it.copy(searchQuery = q) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.biometricEnabled = enabled
        _ui.update { it.copy(biometricEnabled = enabled) }
    }

    fun getQuestion1() = prefs.getQuestion1()
    fun getQuestion2() = prefs.getQuestion2()

    fun filteredItems(): List<VaultItem> {
        val state = _ui.value
        val filtered = state.items.filter { item ->
            val catOk = state.selectedCategory == null || item.category == state.selectedCategory
            val searchOk = state.searchQuery.isBlank() ||
                item.displayName.contains(state.searchQuery, ignoreCase = true)
            val favOk = !state.favoritesOnly || item.favorite
            catOk && searchOk && favOk
        }
        return when (state.sortMode) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.addedAt }
            SortMode.DATE_ASC -> filtered.sortedBy { it.addedAt }
            SortMode.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            SortMode.FAVORITES -> filtered.sortedWith(
                compareByDescending<VaultItem> { it.favorite }.thenByDescending { it.addedAt }
            )
        }
    }

    fun setSortMode(mode: SortMode) {
        prefs.sortMode = mode
        _ui.update { it.copy(sortMode = mode) }
    }

    fun setGridMode(grid: Boolean) {
        prefs.gridMode = grid
        _ui.update { it.copy(gridMode = grid) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            val nowFav = repository.toggleFavorite(id)
            _ui.update {
                it.copy(message = if (nowFav) "Ajouté aux favoris" else "Retiré des favoris")
            }
        }
    }

    fun enterSelectionMode(initialId: String? = null) {
        _ui.update {
            it.copy(
                selectionMode = true,
                selectedIds = if (initialId != null) setOf(initialId) else emptySet()
            )
        }
    }

    fun exitSelectionMode() {
        _ui.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelect(id: String) {
        _ui.update { st ->
            val next = st.selectedIds.toMutableSet()
            if (id in next) next.remove(id) else next.add(id)
            st.copy(selectedIds = next, selectionMode = true)
        }
    }

    fun selectAllVisible() {
        val ids = filteredItems().map { it.id }.toSet()
        _ui.update { it.copy(selectedIds = ids, selectionMode = true) }
    }

    fun deleteSelected() {
        val ids = _ui.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(ids)
            refreshSize()
            _ui.update {
                it.copy(
                    selectionMode = false,
                    selectedIds = emptySet(),
                    message = "${ids.size} fichier(s) supprimé(s)"
                )
            }
        }
    }

    fun setFavoritesOnly(only: Boolean) {
        prefs.favoritesOnly = only
        _ui.update { it.copy(favoritesOnly = only) }
    }

    fun setAutoLockMs(ms: Long) {
        prefs.autoLockMs = ms
        _ui.update { it.copy(autoLockMs = ms) }
    }

    fun openSettings() {
        _ui.update { it.copy(screen = AppScreen.SETTINGS, showSettings = false) }
    }

    fun closeSettings() {
        _ui.update { it.copy(screen = AppScreen.VAULT_HOME, showSettings = false) }
    }

    fun renameItem(id: String, newName: String) {
        val name = newName.trim()
        if (name.isBlank()) {
            _ui.update { it.copy(error = "Nom invalide") }
            return
        }
        viewModelScope.launch {
            val ok = repository.renameItem(id, name)
            _ui.update {
                val preview = it.previewItem
                it.copy(
                    message = if (ok) "Renommé" else "Impossible de renommer",
                    previewItem = if (ok && preview?.id == id) preview.copy(displayName = name) else preview
                )
            }
        }
    }

    fun clearPreviewCache() {
        repository.clearOpenCache()
        _ui.update { it.copy(message = "Cache d’aperçu vidé") }
    }

    fun thumbnailPath(id: String): java.io.File? {
        val f = repository.thumbnailFile(id)
        return if (f.exists() && f.length() > 0) f else null
    }

    suspend fun ensureThumb(item: VaultItem): java.io.File? = repository.ensureThumbnail(item)

    private fun refreshSize() {
        val bytes = repository.totalBytes()
        val label = when {
            bytes < 1024 -> "$bytes o"
            bytes < 1024 * 1024 -> "%.1f Ko".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f Mo".format(bytes / (1024.0 * 1024))
            else -> "%.2f Go".format(bytes / (1024.0 * 1024 * 1024))
        }
        _ui.update { it.copy(vaultSizeLabel = label) }
    }
}

