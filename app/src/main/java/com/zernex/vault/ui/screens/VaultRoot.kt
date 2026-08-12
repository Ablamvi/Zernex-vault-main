package com.zernex.vault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zernex.vault.data.LockType
import com.zernex.vault.data.VaultCategory
import com.zernex.vault.data.VaultItem
import com.zernex.vault.ui.AppScreen
import com.zernex.vault.ui.VaultUiState
import com.zernex.vault.ui.VaultViewModel
import kotlin.math.hypot

@Composable
fun VaultRoot(state: VaultUiState, viewModel: VaultViewModel) {
    when (state.screen) {
        AppScreen.SETUP_WELCOME -> SetupWelcome(onStart = viewModel::startSetup)
        AppScreen.SETUP_LOCK_TYPE -> SetupLockType(onChoose = viewModel::chooseLockType)
        AppScreen.SETUP_PIN -> SetupPinScreen(
            error = state.error,
            onSubmit = viewModel::submitSetupSecret,
            onClearError = viewModel::clearMessage
        )
        AppScreen.SETUP_PATTERN -> SetupPatternScreen(
            error = state.error,
            onSubmit = viewModel::submitSetupSecret,
            onClearError = viewModel::clearMessage
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
            state = state,
            onUnlock = viewModel::unlock,
            onRecovery = viewModel::openRecovery
        )
        AppScreen.RECOVERY_CHOICE -> RecoveryChoice(
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
            onImport = viewModel::importFiles,
            onDelete = viewModel::deleteItem,
            onCategory = viewModel::setCategory,
            onSearch = viewModel::setSearch,
            onLock = viewModel::lock,
            onClearMessage = viewModel::clearMessage
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
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "ZERNEX Vault",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Coffre-fort local pour photos, vidéos, documents et fichiers.\n" +
                "Chiffré • Invisible hors de l’app • 100 % hors-ligne",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Configurer mon coffre", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SetupLockType(onChoose: (LockType) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Choisis ton verrouillage",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PIN ou schéma — les deux sont chiffrés localement.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        LockTypeCard(
            title = "Code PIN",
            subtitle = "4 chiffres minimum",
            icon = Icons.Default.Pin,
            onClick = { onChoose(LockType.PIN) }
        )
        Spacer(Modifier.height(16.dp))
        LockTypeCard(
            title = "Schéma",
            subtitle = "Relie au moins 4 points",
            icon = Icons.Default.Pattern,
            onClick = { onChoose(LockType.PATTERN) }
        )
    }
}

@Composable
private fun LockTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupPinScreen(
    error: String?,
    onSubmit: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Crée ton PIN", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; onClearError() } },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
            label = { Text("Confirme le PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(pin, confirm) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = pin.length >= 4,
            shape = RoundedCornerShape(14.dp)
        ) { Text("Continuer") }
    }
}

@Composable
private fun SetupPatternScreen(
    error: String?,
    onSubmit: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) } // 0 = draw, 1 = confirm

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (step == 0) "Dessine ton schéma" else "Confirme ton schéma",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Relie au moins 4 points",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        PatternPad(
            onComplete = { seq ->
                onClearError()
                if (step == 0) {
                    if (seq.length >= 4) {
                        pattern = seq
                        step = 1
                    }
                } else {
                    confirm = seq
                    onSubmit(pattern, seq)
                    if (pattern != seq) step = 0
                }
            }
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SetupRecoveryQuestions(
    error: String?,
    onSubmit: (String, String, String, String) -> Unit
) {
    var q1 by remember { mutableStateOf("Nom de jeune fille de ta mère ?") }
    var a1 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf("Ville de naissance ?") }
    var a2 by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Récupération",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Si tu oublies ton code, ces questions + une clé te permettront de récupérer l’accès.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Ta clé de récupération",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Note-la dans un endroit sûr. Elle ne sera plus affichée.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                key,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.secondary
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
    state: VaultUiState,
    onUnlock: (String) -> Unit,
    onRecovery: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Coffre verrouillé", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        if (state.lockoutRemainingMs > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Réessaie dans ${state.lockoutRemainingMs / 1000}s",
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(24.dp))

        if (state.lockType == LockType.PIN) {
            var pin by remember { mutableStateOf("") }
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onUnlock(pin); pin = "" },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = pin.length >= 4 && state.lockoutRemainingMs <= 0,
                shape = RoundedCornerShape(14.dp)
            ) { Text("Déverrouiller") }
        } else {
            PatternPad(
                onComplete = { seq ->
                    if (seq.length >= 4) onUnlock(seq)
                }
            )
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        if (state.message != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.message, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRecovery) {
            Text("J’ai oublié mon code")
        }
    }
}

/* ─────────── RECOVERY ─────────── */

@Composable
private fun RecoveryChoice(onQuestions: () -> Unit, onKey: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
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
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Clé de récupération", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.uppercase() },
            label = { Text("XXXX-XXXX-XXXX-XXXX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
        TextButton(onClick = onBack) { Text("Retour") }
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
                enabled = secret.length >= 4,
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enregistrer") }
        } else {
            Text("Dessine le nouveau schéma (2 fois)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            var step by remember { mutableIntStateOf(0) }
            var first by remember { mutableStateOf("") }
            PatternPad(onComplete = { seq ->
                if (seq.length < 4) return@PatternPad
                if (step == 0) {
                    first = seq
                    step = 1
                } else {
                    onReset(first, seq, type)
                }
            })
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

/* ─────────── VAULT HOME ─────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultHomeScreen(
    state: VaultUiState,
    items: List<VaultItem>,
    onImport: (List<Uri>) -> Unit,
    onDelete: (String) -> Unit,
    onCategory: (VaultCategory?) -> Unit,
    onSearch: (String) -> Unit,
    onLock: () -> Unit,
    onClearMessage: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ZERNEX Vault",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
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
                onClick = { picker.launch(arrayOf("*/*")) },
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

            if (state.message != null) {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(16.dp)
                )
                LaunchedEffect(state.message) {
                    kotlinx.coroutines.delay(2500)
                    onClearMessage()
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(Modifier = Modifier.fillMaxWidth())
            }

            Text(
                "${items.size} fichier${if (items.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

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
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(items, key = { it.id }) { item ->
                        VaultItemRow(item = item, onDelete = { onDelete(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(item: VaultItem, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(categoryIcon(item.category), null, tint = MaterialTheme.colorScheme.primary)
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
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ?") },
            text = { Text("« ${item.displayName} » sera définitivement retiré du coffre.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
}

private fun catLabel(c: VaultCategory) = when (c) {
    VaultCategory.IMAGE -> "Images"
    VaultCategory.VIDEO -> "Vidéos"
    VaultCategory.AUDIO -> "Audio"
    VaultCategory.DOCUMENT -> "Documents"
    VaultCategory.OTHER -> "Autres"
}

private fun categoryIcon(c: VaultCategory) = when (c) {
    VaultCategory.IMAGE -> Icons.Default.Image
    VaultCategory.VIDEO -> Icons.Default.Videocam
    VaultCategory.AUDIO -> Icons.Default.AudioFile
    VaultCategory.DOCUMENT -> Icons.Default.Description
    VaultCategory.OTHER -> Icons.Default.InsertDriveFile
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
            if (hypot(offset.x - cx, offset.y - cy) < cellPx * 0.4f) return i
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
        // Lignes
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val pts = selected.map { i ->
                Offset((i % 3) * cellPx + cellPx / 2, (i / 3) * cellPx + cellPx / 2)
            }
            for (i in 0 until pts.size - 1) {
                drawLine(
                    color = Color(0xFFFF6B35),
                    start = pts[i],
                    end = pts[i + 1],
                    strokeWidth = 6f
                )
            }
            currentTouch?.let { touch ->
                pts.lastOrNull()?.let { last ->
                    drawLine(Color(0xFFFF6B35), last, touch, strokeWidth = 6f)
                }
            }
        }
        // Points
        dots.forEach { i ->
            val row = i / 3
            val col = i % 3
            Box(
                Modifier
                    .offset(x = cell * col, y = cell * row)
                    .size(cell),
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
