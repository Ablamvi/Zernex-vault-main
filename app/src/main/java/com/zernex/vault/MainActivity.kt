package com.zernex.vault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.zernex.vault.ui.VaultViewModel
import com.zernex.vault.ui.screens.VaultRoot
import com.zernex.vault.ui.theme.ZernexVaultTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZernexVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    VaultRoot(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Relock quand l'app passe en arrière-plan
        if (viewModel.uiState.value.isUnlocked) {
            viewModel.lock()
        }
    }
}
