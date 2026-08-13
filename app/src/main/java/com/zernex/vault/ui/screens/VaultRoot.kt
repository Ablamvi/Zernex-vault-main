package com.zernex.vault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
            onClearMessage = viewModel::clearMessage
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
            }
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
    onClearMessage: () -> Unit
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
            TopAppBar(
                title = {
                    Text("ZERNEX Vault", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, "Verrouiller")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
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
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
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
                        selected = state.selectedCategory == null,
                        onClick = { onCategory(null) },
                        label = { Text("Tout") }
                    )
                }
                items(VaultCategory.entries.toList()) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { onCategory(cat) },
                        label = { Text(catLabel(cat)) }
                    )
                }
            }

            // Tri + grille + taille coffre
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.vaultSizeLabel.isNotBlank()) "Coffre · ${state.vaultSizeLabel}" else "Coffre",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onGridMode(!state.gridMode) }) {
                    Icon(
                        if (state.gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Vue"
                    )
                }
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Default.Sort, "Trier")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("Plus récents") }, onClick = { sortMenu = false; onSort(SortMode.DATE_DESC) })
                        DropdownMenuItem(text = { Text("Plus anciens") }, onClick = { sortMenu = false; onSort(SortMode.DATE_ASC) })
                        DropdownMenuItem(text = { Text("Nom") }, onClick = { sortMenu = false; onSort(SortMode.NAME) })
                        DropdownMenuItem(text = { Text("Taille") }, onClick = { sortMenu = false; onSort(SortMode.SIZE) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Coffre vide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Importe photos, vidéos, documents…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
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
                            if (item.isVisual) {
                                VaultGridCell(
                                    item = item,
                                    thumb = thumbFile(item.id),
                                    onOpen = { onOpen(item) },
                                    onLongClick = { /* menu via open then actions */ }
                                )
                            } else {
                                // non-visuel en grille : carte compacte
                                VaultGridCell(
                                    item = item,
                                    thumb = null,
                                    onOpen = { onOpen(item) },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(items, key = { it.id }) { item ->
                            VaultItemRow(
                                item = item,
                                thumb = thumbFile(item.id),
                                onOpen = { onOpen(item) },
                                onShare = { onShare(item) },
                                onRestore = { onRestore(item) },
                                onDelete = { onDelete(item.id) }
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
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

/* ─────────── PREVIEW ─────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewScreen(
    item: VaultItem?,
    file: File?,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
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
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.PhoneAndroid, "Restaurer")
                    }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Partager") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (item == null || file == null) {
                Text("Fichier indisponible", color = Color.White)
            } else when {
                item.mimeType.startsWith("image/") -> {
                    AsyncImage(
                        model = file,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/") -> {
                    VaultMediaPlayer(
                        file = file,
                        isAudioOnly = item.mimeType.startsWith("audio/"),
                        title = item.displayName
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            categoryIcon(item.category),
                            null,
                            Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(item.displayName, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(item.sizeFormatted, color = Color.LightGray)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Document : utilise Restaurer ou Partager pour l’ouvrir avec une autre app.",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRestore, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.PhoneAndroid, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restaurer sur le téléphone")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onShare, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Partager")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultMediaPlayer(file: File, isAudioOnly: Boolean, title: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (isAudioOnly) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AudioFile,
                        null,
                        Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                }
            },
            modifier = if (isAudioOnly) Modifier.fillMaxWidth().height(120.dp) else Modifier.fillMaxSize()
        )
    }
}


@Composable
private fun VaultGridCell(
    item: VaultItem,
    thumb: java.io.File?,
    onOpen: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
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
                    Icons.Default.PlayCircleFilled,
                    null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(36.dp)
                )
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
