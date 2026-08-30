package com.zernex.vault.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.WindowManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import com.zernex.vault.data.SortMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zernex.vault.data.LockType
import com.zernex.vault.data.VaultCategory
import com.zernex.vault.data.VaultItem
import com.zernex.vault.ui.AppScreen
import com.zernex.vault.ui.VaultUiState
import com.zernex.vault.ui.VaultViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun VaultRoot(
    state: VaultUiState,
    viewModel: VaultViewModel,
    onRequestBiometric: () -> Unit,
    canUseBiometric: Boolean
) {
    // Partage automatique quand shareFile est prêt
    val context = LocalContext.current
    LaunchedEffect(state.shareFile) {
        val file = state.shareFile ?: return@LaunchedEffect
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = state.shareMime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exporter depuis ZERNEX Vault"))
        } catch (_: Exception) {
        } finally {
            viewModel.onShareDone()
        }
    }

    when (state.screen) {
        AppScreen.SETUP_WELCOME -> SetupWelcome(onStart = viewModel::startSetup)
        AppScreen.SETUP_LOCK_TYPE -> SetupLockType(
            onPin = { viewModel.chooseLockType(LockType.PIN) },
            onPattern = { viewModel.chooseLockType(LockType.PATTERN) }
        )
        AppScreen.SETUP_PIN -> SetupPinScreen(
            error = state.error,
            onSubmit = viewModel::submitSetupSecret
        )
        AppScreen.SETUP_PATTERN -> SetupPatternScreen(
            error = state.error,
            onSubmit = viewModel::submitSetupSecret
        )
        AppScreen.SETUP_RECOVERY_Q -> SetupRecoveryQuestions(
            error = state.error,
            onSubmit = viewModel::submitRecoveryQuestions
        )
        AppScreen.SETUP_RECOVERY_KEY -> SetupRecoveryKey(
            key = state.recoveryKeyShown.orEmpty(),
            onFinish = viewModel::finishSetup
        )
        AppScreen.LOCK -> LockScreen(
            lockType = state.lockType,
            error = state.error,
            lockoutMs = state.lockoutRemainingMs,
            biometricEnabled = state.biometricEnabled && canUseBiometric,
            onUnlock = viewModel::unlock,
            onBiometric = onRequestBiometric,
            onRecovery = viewModel::openRecovery
        )
        AppScreen.RECOVERY_CHOICE -> RecoveryChoiceScreen(
            onQuestions = viewModel::openRecoveryQuestions,
            onKey = viewModel::openRecoveryKey,
            onBack = viewModel::backToLock
        )
        AppScreen.RECOVERY_QUESTIONS -> RecoveryQuestionsScreen(
            q1 = viewModel.getQuestion1(),
            q2 = viewModel.getQuestion2(),
            error = state.error,
            onSubmit = viewModel::verifyRecoveryAnswers,
            onBack = viewModel::openRecovery
        )
        AppScreen.RECOVERY_KEY -> RecoveryKeyScreen(
            error = state.error,
            onSubmit = viewModel::verifyRecoveryKeyInput,
            onBack = viewModel::openRecovery
        )
        AppScreen.RESET_LOCK -> ResetLockScreen(
            error = state.error,
            onReset = viewModel::resetLock
        )
        AppScreen.VAULT_HOME -> VaultHomeScreen(
            state = state,
            items = viewModel.filteredItems(),
            onImportUris = viewModel::importFiles,
            onStartImport = { viewModel.setAwaitingExternalResult(true) },
            onMoveMode = viewModel::setMoveOnImport,
            onSort = viewModel::setSortMode,
            onGridMode = viewModel::setGridMode,
            thumbFile = viewModel::thumbnailPath,
            onDelete = viewModel::deleteItem,
            onOpen = viewModel::openItem,
            onShare = viewModel::prepareShare,
            onRestore = { item -> viewModel.restoreToDevice(item, removeFromVault = true) },
            onCategory = viewModel::setCategory,
            onSearch = viewModel::setSearch,
            onLock = viewModel::lock,
            onClearMessage = viewModel::clearMessage,
            onToggleFavorite = viewModel::toggleFavorite,
            onEnterSelection = viewModel::enterSelectionMode,
            onExitSelection = viewModel::exitSelectionMode,
            onToggleSelect = viewModel::toggleSelect,
            onSelectAll = viewModel::selectAllVisible,
            onDeleteSelected = viewModel::deleteSelected,
            onFavoritesOnly = viewModel::setFavoritesOnly,
            onOpenSettings = viewModel::openSettings,
            onCloseSettings = viewModel::closeSettings,
            onBiometric = viewModel::setBiometricEnabled,
            onAutoLock = viewModel::setAutoLockMs
        )
        AppScreen.PREVIEW -> PreviewScreen(
            item = state.previewItem,
            file = state.previewFile,
            onClose = viewModel::closePreview,
            onShare = { state.previewItem?.let { viewModel.prepareShare(it) } },
            onRestore = { state.previewItem?.let { viewModel.restoreToDevice(it, true) } },
            onDelete = {
                state.previewItem?.let { viewModel.deleteItem(it.id) }
                viewModel.closePreview()
            },
            onToggleFavorite = { state.previewItem?.let { viewModel.toggleFavorite(it.id) } },
            onRename = { id, name -> viewModel.renameItem(id, name) },
            onNextMedia = viewModel::openNextMedia,
            onPreviousMedia = viewModel::openPreviousMedia
        )
        AppScreen.SETTINGS -> SettingsScreen(
            state = state,
            onBack = viewModel::closeSettings,
            onBiometric = viewModel::setBiometricEnabled,
            onAutoLock = viewModel::setAutoLockMs,
            onMoveMode = viewModel::setMoveOnImport,
            onGridMode = viewModel::setGridMode,
            onSort = viewModel::setSortMode,
            onClearCache = viewModel::clearPreviewCache
        )
    }
}

/* ─────────── SETUP ─────────── */

@Composable
private fun SetupWelcome(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(
            "ZERNEX Vault",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Coffre-fort local pour photos, vidéos, documents.\nChiffré • Invisible hors de l’app • 100 % hors-ligne",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Configurer mon coffre", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SetupLockType(onPin: () -> Unit, onPattern: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Choisir le verrouillage", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(24.dp))
        LockTypeCard("Code PIN", "4 à 8 chiffres", Icons.Default.Pin, onPin)
        Spacer(Modifier.height(12.dp))
        LockTypeCard("Schéma", "Relie au moins 4 points", Icons.Default.Pattern, onPattern)
    }
}

@Composable
private fun LockTypeCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupPinScreen(error: String?, onSubmit: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Créer un code PIN", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
            label = { Text("Confirmer") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(pin, confirm) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = pin.length >= 4
        ) { Text("Continuer") }
    }
}

@Composable
private fun SetupPatternScreen(error: String?, onSubmit: (String, String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (step == 0) "Dessine ton schéma" else "Confirme le schéma",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text("Relie au moins 4 points", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PatternPad { seq ->
            if (seq.length < 4) return@PatternPad
            if (step == 0) {
                first = seq
                step = 1
            } else {
                onSubmit(first, seq)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SetupRecoveryQuestions(error: String?, onSubmit: (String, String, String, String) -> Unit) {
    var q1 by remember { mutableStateOf("") }
    var a1 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Questions de récupération", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text("En cas d’oubli du code", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(q1, { q1 = it }, label = { Text("Question 1") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(a1, { a1 = it }, label = { Text("Réponse 1") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(q2, { q2 = it }, label = { Text("Question 2") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(a2, { a2 = it }, label = { Text("Réponse 2") }, modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(q1, a1, q2, a2) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Continuer") }
    }
}

@Composable
private fun SetupRecoveryKey(key: String, onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Key, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Clé de récupération", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "Note cette clé dans un endroit sûr. Elle ne sera plus affichée.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(12.dp)) {
            Text(
                key,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("J’ai noté ma clé — Ouvrir le coffre") }
    }
}

/* ─────────── LOCK ─────────── */

@Composable
private fun LockScreen(
    lockType: LockType,
    error: String?,
    lockoutMs: Long,
    biometricEnabled: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onRecovery: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("ZERNEX Vault", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text("Coffre verrouillé", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        if (lockoutMs > 0) {
            Text(
                "Réessaie dans ${lockoutMs / 1000}s",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        } else if (lockType == LockType.PIN) {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 8 && it.all(Char::isDigit)) {
                        pin = it
                        if (it.length >= 4) {
                            // auto-submit option: user taps button
                        }
                    }
                },
                label = { Text("Code PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onUnlock(pin); pin = "" },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = pin.length >= 4
            ) { Text("Déverrouiller") }
        } else {
            PatternPad { seq -> if (seq.length >= 4) onUnlock(seq) }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        if (biometricEnabled) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onBiometric,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Empreinte / Face")
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRecovery) { Text("Code oublié ?") }
    }

    // Propose biométrie au démarrage de l’écran lock
    LaunchedEffect(Unit) {
        if (biometricEnabled && lockoutMs <= 0) {
            onBiometric()
        }
    }
}

/* ─────────── RECOVERY ─────────── */

@Composable
private fun RecoveryChoiceScreen(onQuestions: () -> Unit, onKey: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Récupérer l’accès", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(24.dp))
        LockTypeCard("Questions secrètes", "Réponds aux 2 questions", Icons.Default.Help, onQuestions)
        Spacer(Modifier.height(12.dp))
        LockTypeCard("Clé de récupération", "Entre la clé notée à l’installation", Icons.Default.Key, onKey)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Retour")
        }
    }
}

@Composable
private fun RecoveryQuestionsScreen(
    q1: String, q2: String, error: String?,
    onSubmit: (String, String) -> Unit, onBack: () -> Unit
) {
    var a1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Questions secrètes", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        Text(q1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(a1, { a1 = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Réponse") })
        Spacer(Modifier.height(16.dp))
        Text(q2, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(a2, { a2 = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Réponse") })
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(a1, a2) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Valider") }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Retour")
        }
    }
}

@Composable
private fun RecoveryKeyScreen(error: String?, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Clé de récupération", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("XXXX-XXXX-XXXX-XXXX") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(key) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Valider") }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Retour")
        }
    }
}

@Composable
private fun ResetLockScreen(error: String?, onReset: (String, String, LockType) -> Unit) {
    var type by remember { mutableStateOf(LockType.PIN) }
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Nouveau verrouillage", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == LockType.PIN, onClick = { type = LockType.PIN }, label = { Text("PIN") })
            FilterChip(selected = type == LockType.PATTERN, onClick = { type = LockType.PATTERN }, label = { Text("Schéma") })
        }
        Spacer(Modifier.height(16.dp))
        if (type == LockType.PIN) {
            OutlinedTextField(
                secret,
                { if (it.length <= 8 && it.all(Char::isDigit)) secret = it },
                label = { Text("Nouveau PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                confirm,
                { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                label = { Text("Confirmer") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onReset(secret, confirm, type) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enregistrer") }
        } else {
            Text("Dessine le nouveau schéma (2 fois)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            var first by remember { mutableStateOf("") }
            var step by remember { mutableIntStateOf(0) }
            PatternPad { seq ->
                if (seq.length < 4) return@PatternPad
                if (step == 0) {
                    first = seq
                    step = 1
                } else {
                    onReset(first, seq, type)
                }
            }
            Text(if (step == 0) "1 / 2" else "2 / 2 — confirme", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

/* ─────────── HOME ─────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultHomeScreen(
    state: VaultUiState,
    items: List<VaultItem>,
    onImportUris: (List<Uri>) -> Unit,
    onStartImport: () -> Unit,
    onMoveMode: (Boolean) -> Unit,
    onSort: (SortMode) -> Unit,
    onGridMode: (Boolean) -> Unit,
    thumbFile: (String) -> java.io.File?,
    onDelete: (String) -> Unit,
    onOpen: (VaultItem) -> Unit,
    onShare: (VaultItem) -> Unit,
    onRestore: (VaultItem) -> Unit,
    onCategory: (VaultCategory?) -> Unit,
    onSearch: (String) -> Unit,
    onLock: () -> Unit,
    onClearMessage: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEnterSelection: (String?) -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onAutoLock: (Long) -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onImportUris(uris)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${state.selectedIds.size} sélectionné(s)",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onExitSelection) {
                            Icon(Icons.Default.Close, "Annuler")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, "Tout sélectionner")
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, "Supprimer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "ZERNEX",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "VAULT",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "Réglages")
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, "Verrouiller")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onStartImport()
                        picker.launch(arrayOf("*/*"))
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Importer") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Barre de progression import
            state.importProgress?.let { progress ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Import en cours…", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.isLoading && state.importProgress == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )

            // Mode import : Déplacer (recommandé) ou Copier
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Import :", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = state.moveOnImport,
                    onClick = { onMoveMode(true) },
                    label = { Text("Déplacer (cacher)") }
                )
                FilterChip(
                    selected = !state.moveOnImport,
                    onClick = { onMoveMode(false) },
                    label = { Text("Copier") }
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null && !state.favoritesOnly,
                        onClick = {
                            onFavoritesOnly(false)
                            onCategory(null)
                        },
                        label = { Text("Tout") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.favoritesOnly,
                        onClick = { onFavoritesOnly(!state.favoritesOnly) },
                        label = { Text("★ Favoris") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                items(VaultCategory.entries.toList()) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat && !state.favoritesOnly,
                        onClick = {
                            onFavoritesOnly(false)
                            onCategory(cat)
                        },
                        label = { Text(catLabel(cat)) }
                    )
                }
            }

            // Tri + grille + taille (favoris = chip catégories, plus de chevauchement)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var sortMenu by remember { mutableStateOf(false) }
                val sortLabel = when (state.sortMode) {
                    SortMode.DATE_DESC -> "Plus récents"
                    SortMode.DATE_ASC -> "Plus anciens"
                    SortMode.NAME -> "Nom"
                    SortMode.SIZE -> "Taille"
                    SortMode.FAVORITES -> "Favoris d'abord"
                }
                Box {
                    TextButton(onClick = { sortMenu = true }) {
                        Text(sortLabel, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("Plus récents") }, onClick = { sortMenu = false; onSort(SortMode.DATE_DESC) })
                        DropdownMenuItem(text = { Text("Plus anciens") }, onClick = { sortMenu = false; onSort(SortMode.DATE_ASC) })
                        DropdownMenuItem(text = { Text("Nom") }, onClick = { sortMenu = false; onSort(SortMode.NAME) })
                        DropdownMenuItem(text = { Text("Taille") }, onClick = { sortMenu = false; onSort(SortMode.SIZE) })
                        DropdownMenuItem(text = { Text("Favoris d'abord") }, onClick = { sortMenu = false; onSort(SortMode.FAVORITES) })
                    }
                }
                Text(
                    if (state.vaultSizeLabel.isNotBlank()) "· ${state.vaultSizeLabel}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = { onGridMode(!state.gridMode) }) {
                    Icon(
                        if (state.gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (state.gridMode) "Vue liste" else "Vue grille"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            if (state.favoritesOnly) Icons.Default.StarBorder else Icons.Default.FolderOff,
                            null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (state.favoritesOnly) "Aucun favori" else "Coffre vide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.favoritesOnly)
                                "Appuie longuement sur un fichier puis sur l\'étoile pour l\'ajouter aux favoris."
                            else
                                "Tes photos, vidéos et documents restent chiffrés ici.\nAppuie sur Importer pour commencer.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val useGrid = state.gridMode && (
                    state.selectedCategory == null ||
                    state.selectedCategory == VaultCategory.IMAGE ||
                    state.selectedCategory == VaultCategory.VIDEO ||
                    items.any { it.isVisual }
                )
                if (useGrid && items.any { it.isVisual }) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(items, key = { it.id }) { item ->
                            val selected = item.id in state.selectedIds
                            VaultGridCell(
                                item = item,
                                thumb = if (item.isVisual) thumbFile(item.id) else null,
                                selected = selected,
                                selectionMode = state.selectionMode,
                                onOpen = {
                                    if (state.selectionMode) onToggleSelect(item.id)
                                    else onOpen(item)
                                },
                                onLongClick = {
                                    if (state.selectionMode) onToggleSelect(item.id)
                                    else onEnterSelection(item.id)
                                },
                                onToggleFavorite = { onToggleFavorite(item.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(items, key = { it.id }) { item ->
                            val selected = item.id in state.selectedIds
                            VaultItemRow(
                                item = item,
                                thumb = thumbFile(item.id),
                                selected = selected,
                                selectionMode = state.selectionMode,
                                onOpen = {
                                    if (state.selectionMode) onToggleSelect(item.id)
                                    else onOpen(item)
                                },
                                onLongClick = {
                                    if (state.selectionMode) onToggleSelect(item.id)
                                    else onEnterSelection(item.id)
                                },
                                onShare = { onShare(item) },
                                onRestore = { onRestore(item) },
                                onDelete = { onDelete(item.id) },
                                onToggleFavorite = { onToggleFavorite(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: VaultItem,
    thumb: java.io.File?,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onOpen: () -> Unit,
    onLongClick: () -> Unit = {},
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .pointerInput(item.id, selectionMode) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onOpen() },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (item.category == VaultCategory.VIDEO) {
                    Icon(
                        Icons.Default.PlayCircle,
                        null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(categoryIcon(item.category), null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${catLabel(item.category)} • ${item.sizeFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    "Favori",
                    tint = if (item.favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ouvrir") },
                        onClick = { menu = false; onOpen() },
                        leadingIcon = { Icon(Icons.Default.Visibility, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.favorite) "Retirer des favoris" else "Ajouter aux favoris") },
                        onClick = { menu = false; onToggleFavorite() },
                        leadingIcon = {
                            Icon(
                                if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Exporter / Partager") },
                        onClick = { menu = false; onShare() },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Restaurer sur le téléphone") },
                        onClick = { menu = false; onRestore() },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer du coffre") },
                        onClick = { menu = false; confirmDelete = true },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ?") },
            text = { Text("« ${item.displayName} » sera définitivement retiré du coffre.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
}



@Composable
private fun VaultGridCell(
    item: VaultItem,
    thumb: java.io.File?,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(item.id, selectionMode) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    categoryIcon(item.category),
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (item.category == VaultCategory.VIDEO) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(36.dp)
                )
            }
            if (selectionMode) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (item.favorite || !selectionMode) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favori",
                        tint = if (item.favorite) Color(0xFFFFC107)
                        else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            item.displayName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}


/* ─────────── PREVIEW ─────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewScreen(
    item: VaultItem?,
    file: File?,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onNextMedia: () -> Unit = {},
    onPreviousMedia: () -> Unit = {}
) {
    BackHandler(onBack = onClose)
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by remember(item?.id) { mutableStateOf(item?.displayName ?: "") }
    var detailsOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            if (controlsVisible) {
                TopAppBar(
                    title = {
                        Text(
                            item?.displayName ?: "Aperçu",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, "Retour")
                        }
                    },
                    actions = {
                        if (item != null) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    "Favori",
                                    tint = if (item.favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, "Options")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Restaurer sur le téléphone") },
                                    onClick = { menu = false; onRestore() },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Partager") },
                                    onClick = { menu = false; onShare() },
                                    leadingIcon = { Icon(Icons.Default.Share, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Renommer") },
                                    onClick = { menu = false; renameOpen = true },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Détails") },
                                    onClick = { menu = false; detailsOpen = true },
                                    leadingIcon = { Icon(Icons.Default.Info, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer") },
                                    onClick = { menu = false; onDelete() },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .pointerInput(item?.id, file?.absolutePath) {
                    // Observe gestures in the Initial pass so PlayerView, AsyncImage,
                    // transformable() and scrolling children cannot swallow navigation swipes.
                    awaitPointerEventScope {
                        while (true) {
                            var totalX = 0f
                            var totalY = 0f
                            var horizontalDecision = false
                            var verticalDecision = false
                            var cancelled = false

                            var event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size != 1) continue
                            var previous = event.changes[0].position

                            while (true) {
                                event = awaitPointerEvent(PointerEventPass.Initial)
                                val changes = event.changes
                                if (changes.size != 1) {
                                    cancelled = true
                                    break
                                }

                                val change = changes[0]
                                val current = change.position
                                val dx = current.x - previous.x
                                val dy = current.y - previous.y
                                previous = current
                                totalX += dx
                                totalY += dy

                                val absX = kotlin.math.abs(totalX)
                                val absY = kotlin.math.abs(totalY)

                                if (!horizontalDecision && !verticalDecision && (absX >= 24f || absY >= 24f)) {
                                    when {
                                        absX > absY * 1.25f -> horizontalDecision = true
                                        absY > absX * 1.25f -> verticalDecision = true
                                        else -> cancelled = true
                                    }
                                }

                                // Once horizontal intent is clear, consume it so image/video
                                // internals do not turn the same gesture into a pan/seek.
                                if (horizontalDecision) change.consume()

                                if (!change.pressed) break
                            }

                            if (!cancelled && item != null && file != null &&
                                horizontalDecision && !verticalDecision &&
                                kotlin.math.abs(totalX) >= 90f
                            ) {
                                if (totalX < 0f) onNextMedia() else onPreviousMedia()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (item == null || file == null) {
                Text("Fichier indisponible", color = Color.White)
            } else {
                val mime = item.mimeType
                val nameLower = item.displayName.lowercase()
                when {
                    mime.startsWith("image/") -> {
                        ZoomableImage(
                            file = file,
                            contentDescription = item.displayName,
                            onTap = { controlsVisible = !controlsVisible }
                        )
                    }
                    mime.startsWith("video/") || mime.startsWith("audio/") -> {
                        VaultMediaPlayer(
                            file = file,
                            itemId = item.id,
                            isAudioOnly = mime.startsWith("audio/"),
                            title = item.displayName
                        )
                    }
                    mime.startsWith("text/") || nameLower.endsWith(".txt") ||
                        nameLower.endsWith(".md") || nameLower.endsWith(".csv") ||
                        nameLower.endsWith(".json") || nameLower.endsWith(".log") -> {
                        TextDocumentPreview(file = file)
                    }
                    mime == "application/pdf" || nameLower.endsWith(".pdf") -> {
                        PdfPreview(file = file)
                    }
                    else -> {
                        DocumentFallback(
                            item = item,
                            onRestore = onRestore,
                            onShare = onShare
                        )
                    }
                }
            }
        }
    }

    if (renameOpen && item != null) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(item.id, renameValue)
                    renameOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Annuler") }
            }
        )
    }
    if (detailsOpen && item != null) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text("Détails") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nom : ${item.displayName}")
                    Text("Type : ${item.mimeType}")
                    Text("Catégorie : ${catLabel(item.category)}")
                    Text("Taille : ${item.sizeFormatted}")
                    Text("Ajouté : ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.addedAt))}")
                    Text("Favori : ${if (item.favorite) "Oui" else "Non"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsOpen = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    contentDescription: String?,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .transformable(state)
    ) {
        AsyncImage(
            model = file,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TextDocumentPreview(file: File) {
    val text = remember(file.absolutePath) {
        runCatching {
            file.inputStream().bufferedReader().use { it.readText().take(200_000) }
        }.getOrElse { "Impossible de lire ce fichier texte." }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PdfPreview(file: File) {
    val context = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, pageIndex) {
        with(kotlinx.coroutines.Dispatchers.IO) {
            // render on IO via withContext style
        }
        try {
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            pageCount = renderer.pageCount
            val page = renderer.openPage(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            val bmp = android.graphics.Bitmap.createBitmap(
                page.width.coerceAtLeast(1),
                page.height.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap = bmp
            error = null
        } catch (e: Exception) {
            error = "Aperçu PDF indisponible"
            bitmap = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            bitmap != null -> {
                AsyncImage(
                    model = bitmap,
                    contentDescription = "Page PDF",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                if (pageCount > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                            enabled = pageIndex > 0
                        ) { Icon(Icons.Default.ChevronLeft, "Précédent") }
                        Text(
                            "Page ${pageIndex + 1} / $pageCount",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                            enabled = pageIndex < pageCount - 1
                        ) { Icon(Icons.Default.ChevronRight, "Suivant") }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DocumentFallback(
    item: VaultItem,
    onRestore: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            categoryIcon(item.category),
            null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            item.displayName,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${item.sizeFormatted} · ${item.mimeType}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Aperçu non disponible pour ce format.\nRestaure le fichier pour l’ouvrir avec une autre application.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRestore,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhoneAndroid, null)
            Spacer(Modifier.width(8.dp))
            Text("Restaurer sur le téléphone")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onShare,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Partager")
        }
    }
}

@Composable
private fun VaultMediaPlayer(
    file: File,
    itemId: String,
    isAudioOnly: Boolean,
    title: String
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val playbackPrefs = remember {
        context.getSharedPreferences("vault_playback", Context.MODE_PRIVATE)
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var swipeHint by remember { mutableStateOf<String?>(null) }
    var swipeHintToken by remember { mutableStateOf(0) }

    // Gesture overlays
    var showBrightness by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableStateOf(0.5f) }
    var volumeLevel by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var seekHintToken by remember { mutableStateOf(0) }

    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val accent = MaterialTheme.colorScheme.primary

    val exoPlayer = remember(itemId, file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.fromFile(file)))
            val saved = playbackPrefs.getLong("pos_$itemId", 0L)
            prepare()
            if (saved > 0L) seekTo(saved)
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            setPlaybackSpeed(1f)
        }
    }

    DisposableEffect(exoPlayer, itemId) {
        // Keep screen on while this player is open
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerError = "Lecture impossible (codec ou fichier). Restaure pour ouvrir ailleurs."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 1_000L && dur > 0 && pos < dur - 2_000L) {
                playbackPrefs.edit().putLong("pos_$itemId", pos).apply()
            } else {
                playbackPrefs.edit().remove("pos_$itemId").apply()
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0) durationMs = exoPlayer.duration
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, locked, isAudioOnly) {
        if (controlsVisible && isPlaying && !locked && !isAudioOnly) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // Auto-hide seek hint quickly & reliably
    LaunchedEffect(seekHintToken) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }

    LaunchedEffect(swipeHintToken) {
        if (swipeHint != null) {
            delay(650)
            swipeHint = null
        }
    }


    fun seekBy(deltaMs: Long) {
        val dur = exoPlayer.duration.coerceAtLeast(0L)
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        exoPlayer.seekTo(target)
        controlsVisible = true
        seekHint = if (deltaMs < 0) "−10" else "+10"
        seekHintToken++
    }

    fun setSpeed(s: Float) {
        playbackSpeed = s
        exoPlayer.setPlaybackSpeed(s)
        speedMenu = false
        controlsVisible = true
    }

    fun toggleManualRotation() {
        val orientation = context.resources.configuration.orientation
        activity?.requestedOrientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        controlsVisible = true
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = (ms / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isAudioOnly) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AudioFile,
                    null,
                    Modifier.size(96.dp),
                    tint = accent
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = if (isAudioOnly)
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
            else
                Modifier.fillMaxSize()
        )

        // Gesture layer
        if (!isAudioOnly && playerError == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked) {
                        if (locked) {
                            detectTapGestures { controlsVisible = true }
                            return@pointerInput
                        }
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val w = size.width.toFloat()
                                when {
                                    // Côtés : ±10 s
                                    offset.x < w * 0.28f -> seekBy(-10_000L)
                                    offset.x > w * 0.72f -> seekBy(10_000L)
                                    // Centre : pause / reprise
                                    else -> {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            seekHint = "❚❚"
                                        } else {
                                            exoPlayer.play()
                                            seekHint = "▶"
                                        }
                                        seekHintToken++
                                        controlsVisible = true
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        // Anchored proportional gesture: startLevel + deltaY/height
                        var mode = 0 // 0=none 1=brightness 2=volume
                        var startY = 0f
                        var startLevel = 0f
                        var activated = false
                        var accum = 0f
                        val sensitivity = 1.15f
                        val zoneHFactor = 0.75f // effective travel = 75% of view height

                        fun applyBrightness(level: Float) {
                            val next = level.coerceIn(0.01f, 1f)
                            brightnessLevel = next
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                lp.screenBrightness = next
                                window.attributes = lp
                            }
                        }

                        fun applyVolume(level: Float) {
                            val next = level.coerceIn(0f, 1f)
                            volumeLevel = next
                            val steps = kotlin.math.round(next * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
                        }

                        detectDragGestures(
                            onDragStart = { offset ->
                                mode = 0
                                activated = false
                                accum = 0f
                                startY = offset.y
                                val w = size.width.toFloat()
                                when {
                                    offset.x < w * 0.38f -> {
                                        mode = 1
                                        val lp = activity?.window?.attributes
                                        val cur = lp?.screenBrightness ?: -1f
                                        startLevel = if (cur in 0f..1f) cur else brightnessLevel
                                    }
                                    offset.x > w * 0.62f -> {
                                        mode = 2
                                        startLevel = audioManager
                                            .getStreamVolume(AudioManager.STREAM_MUSIC)
                                            .toFloat() / maxVolume
                                    }
                                    else -> mode = 0
                                }
                            },
                            onDragEnd = {
                                showBrightness = false
                                showVolume = false
                                mode = 0
                                activated = false
                            },
                            onDragCancel = {
                                showBrightness = false
                                showVolume = false
                                mode = 0
                                activated = false
                            },
                            onDrag = { change, dragAmount ->
                                if (mode == 0) return@detectDragGestures
                                change.consume()
                                accum += kotlin.math.abs(dragAmount.y)
                                // Activate after small threshold to avoid accidental tweaks
                                if (!activated) {
                                    if (accum < 10f) return@detectDragGestures
                                    activated = true
                                    if (mode == 1) {
                                        showVolume = false
                                        showBrightness = true
                                    } else {
                                        showBrightness = false
                                        showVolume = true
                                    }
                                }
                                val h = (size.height.toFloat() * zoneHFactor).coerceAtLeast(1f)
                                // Finger up (smaller Y) => increase level
                                val delta = (startY - change.position.y) / h * sensitivity
                                val level = (startLevel + delta).coerceIn(0f, 1f)
                                if (mode == 1) applyBrightness(level) else applyVolume(level)
                            }
                        )
                    }
            )
        }

        // Brightness slider (left) — like reference image
        AnimatedVisibility(
            visible = showBrightness && !locked,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(280)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            VerticalLevelSlider(
                icon = { Icon(Icons.Default.WbSunny, null, tint = accent, modifier = Modifier.size(22.dp)) },
                level = brightnessLevel,
                accent = accent
            )
        }

        // Volume slider (right)
        AnimatedVisibility(
            visible = showVolume && !locked,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(280)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        ) {
            VerticalLevelSlider(
                icon = { Icon(Icons.Default.VolumeUp, null, tint = accent, modifier = Modifier.size(22.dp)) },
                level = volumeLevel,
                accent = accent
            )
        }

        AnimatedVisibility(
            visible = swipeHint != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.8f, animationSpec = tween(140)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(swipeHint ?: "", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ±10 seek hint with scale animation — auto clears via seekHintToken
        AnimatedVisibility(
            visible = seekHint != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.7f, animationSpec = tween(150)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.85f, animationSpec = tween(250)),
            modifier = Modifier.align(
                when (seekHint) {
                    "−10" -> Alignment.CenterStart
                    "+10" -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            ).padding(horizontal = 48.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    seekHint ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        playerError?.let { err ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(err, color = Color.White, textAlign = TextAlign.Center)
            }
        }

        // Bottom controls
        if ((controlsVisible || isAudioOnly) && playerError == null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE614141C))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (!locked) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                controlsVisible = true
                            }
                        },
                        enabled = !locked || true
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Lecture",
                            tint = accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Slider(
                            value = progress.coerceIn(0f, 1f),
                            onValueChange = { v ->
                                if (!locked && durationMs > 0) {
                                    exoPlayer.seekTo((v * durationMs).toLong())
                                    controlsVisible = true
                                }
                            },
                            enabled = !locked,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!locked) {
                        IconButton(onClick = { seekBy(-10_000L) }) {
                            Icon(Icons.Default.Replay10, "−10 s", tint = Color.White)
                        }
                        IconButton(onClick = { seekBy(10_000L) }) {
                            Icon(Icons.Default.Forward10, "+10 s", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { speedMenu = true }) {
                                Text(
                                    if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                speeds.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${s}x",
                                                fontWeight = if (s == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                                color = if (s == playbackSpeed) accent else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = { setSpeed(s) }
                                    )
                                }
                            }
                        }
                    }
                    if (!isAudioOnly) {
                        IconButton(onClick = { toggleManualRotation() }) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                "Rotation manuelle",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = {
                        locked = !locked
                        controlsVisible = true
                        showBrightness = false
                        showVolume = false
                    }) {
                        Icon(
                            if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            if (locked) "Déverrouiller" else "Verrouiller",
                            tint = if (locked) accent else Color.White
                        )
                    }
                }
                if (locked) {
                    Text(
                        "Gestes verrouillés",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalLevelSlider(
    icon: @Composable () -> Unit,
    level: Float,
    accent: Color
) {
    val frac = level.coerceIn(0.05f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(5.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // filled portion
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
            // thumb at top of filled portion
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-(150 * frac - 7)).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}



/* ─────────── SETTINGS PAGE ─────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: VaultUiState,
    onBack: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onAutoLock: (Long) -> Unit,
    onMoveMode: (Boolean) -> Unit,
    onGridMode: (Boolean) -> Unit,
    onSort: (SortMode) -> Unit,
    onClearCache: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSectionTitle("Sécurité")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Empreinte digitale",
                    subtitle = "Déverrouiller avec biométrie",
                    checked = state.biometricEnabled,
                    onCheckedChange = onBiometric
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text(
                    "Verrouillage automatique",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Text(
                    "Quand l’app passe en arrière-plan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(
                    0L to "Immédiat",
                    30_000L to "30 secondes",
                    60_000L to "1 minute",
                    300_000L to "5 minutes"
                )
                options.forEach { (ms, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAutoLock(ms) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.autoLockMs == ms,
                            onClick = { onAutoLock(ms) }
                        )
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            SettingsSectionTitle("Import")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Déplacer à l’import",
                    subtitle = "Retirer le fichier original du téléphone si possible",
                    checked = state.moveOnImport,
                    onCheckedChange = onMoveMode
                )
            }

            SettingsSectionTitle("Affichage")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Vue grille",
                    subtitle = "Sinon liste détaillée",
                    checked = state.gridMode,
                    onCheckedChange = onGridMode
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text(
                    "Tri par défaut",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                val sorts = listOf(
                    SortMode.DATE_DESC to "Plus récents",
                    SortMode.DATE_ASC to "Plus anciens",
                    SortMode.NAME to "Nom",
                    SortMode.SIZE to "Taille",
                    SortMode.FAVORITES to "Favoris d’abord"
                )
                sorts.forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSort(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.sortMode == mode,
                            onClick = { onSort(mode) }
                        )
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            SettingsSectionTitle("Coffre")
            SettingsCard {
                Text("Espace utilisé", fontWeight = FontWeight.Medium)
                Text(
                    state.vaultSizeLabel.ifBlank { "—" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Text(
                    "${state.items.size} fichier(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClearCache,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vider le cache d’aperçu")
                }
            }

            SettingsSectionTitle("À propos")
            SettingsCard {
                Text("ZERNEX Vault", fontWeight = FontWeight.Bold)
                Text(
                    "Version 3.2.0",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Coffre-fort local chiffré (AES-256-GCM). Tes fichiers restent sur cet appareil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/* ─────────── PATTERN PAD ─────────── */

@Composable
fun PatternPad(onComplete: (String) -> Unit) {
    val dots = (0..8).toList()
    var selected by remember { mutableStateOf(listOf<Int>()) }
    var currentTouch by remember { mutableStateOf<Offset?>(null) }
    val cell = 72.dp
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }

    fun nearestDot(offset: Offset): Int? {
        dots.forEach { i ->
            val cx = (i % 3) * cellPx + cellPx / 2
            val cy = (i / 3) * cellPx + cellPx / 2
            if (hypot((offset.x - cx).toDouble(), (offset.y - cy).toDouble()) < cellPx * 0.4f) return i
        }
        return null
    }

    Box(
        Modifier
            .size(cell * 3)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selected = emptyList()
                        nearestDot(offset)?.let { selected = listOf(it) }
                        currentTouch = offset
                    },
                    onDrag = { change, _ ->
                        currentTouch = change.position
                        nearestDot(change.position)?.let { idx ->
                            if (idx !in selected) selected = selected + idx
                        }
                    },
                    onDragEnd = {
                        currentTouch = null
                        if (selected.isNotEmpty()) {
                            onComplete(selected.joinToString(""))
                        }
                        selected = emptyList()
                    }
                )
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val pts = selected.map { i ->
                Offset((i % 3) * cellPx + cellPx / 2, (i / 3) * cellPx + cellPx / 2)
            }
            for (i in 0 until pts.size - 1) {
                drawLine(Color(0xFFFF6B35), pts[i], pts[i + 1], strokeWidth = 6f)
            }
            currentTouch?.let { touch ->
                pts.lastOrNull()?.let { last ->
                    drawLine(Color(0xFFFF6B35), last, touch, strokeWidth = 6f)
                }
            }
        }
        dots.forEach { i ->
            val row = i / 3
            val col = i % 3
            Box(
                Modifier.offset(x = cell * col, y = cell * row).size(cell),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(if (i in selected) 22.dp else 16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i in selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (i in selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                )
            }
        }
    }
}

/* ─────────── HELPERS ─────────── */

private fun catLabel(cat: VaultCategory) = when (cat) {
    VaultCategory.IMAGE -> "Images"
    VaultCategory.VIDEO -> "Vidéos"
    VaultCategory.AUDIO -> "Audio"
    VaultCategory.DOCUMENT -> "Documents"
    VaultCategory.OTHER -> "Autres"
}

private fun categoryIcon(cat: VaultCategory) = when (cat) {
    VaultCategory.IMAGE -> Icons.Default.Image
    VaultCategory.VIDEO -> Icons.Default.Videocam
    VaultCategory.AUDIO -> Icons.Default.AudioFile
    VaultCategory.DOCUMENT -> Icons.Default.Description
    VaultCategory.OTHER -> Icons.Default.InsertDriveFile
}
