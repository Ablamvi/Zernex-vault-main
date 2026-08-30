package com.zernex.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zernex.vault.ui.VaultViewModel
import com.zernex.vault.ui.screens.VaultRoot
import com.zernex.vault.ui.theme.ZernexVaultTheme

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Empêche captures d’écran dans le coffre
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            ZernexVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    VaultRoot(
                        state = state,
                        viewModel = viewModel,
                        onRequestBiometric = { showBiometricPrompt() },
                        canUseBiometric = canUseBiometric()
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Une rotation manuelle est une recréation de configuration : elle ne doit pas verrouiller le coffre.
        // Le verrouillage automatique reste actif lorsque l'application quitte réellement le premier plan.
        if (!isChangingConfigurations) {
            viewModel.onAppBackground()
        }
    }

    private fun canUseBiometric(): Boolean {
        val mgr = BiometricManager.from(this)
        return mgr.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        if (!canUseBiometric()) return
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockWithBiometric()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ZERNEX Vault")
            .setSubtitle("Déverrouille avec ton empreinte ou ton visage")
            .setNegativeButtonText("Utiliser le code")
            .build()
        prompt.authenticate(info)
    }
}
