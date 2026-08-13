package com.zernex.vault.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Chiffrement des fichiers du coffre (AES-256-GCM via Android Keystore)
 * + hachage sécurisé PIN / schéma / réponses de récupération.
 */
class CryptoManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, "vault").also { if (!it.exists()) it.mkdirs() }
    }

    fun vaultFile(id: String): File = File(vaultDir, "$id.bin")

    /** Écrit un flux dans un fichier chiffré du coffre. */
    fun encryptToVault(id: String, input: InputStream) {
        val file = vaultFile(id)
        if (file.exists()) file.delete()
        val encrypted = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encrypted.openFileOutput().use { out ->
            input.copyTo(out)
        }
    }

    /** Ouvre un flux déchiffré en lecture. */
    fun openDecrypted(id: String): InputStream {
        val file = vaultFile(id)
        val encrypted = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        return encrypted.openFileInput()
    }

    fun deleteVaultFile(id: String): Boolean = vaultFile(id).delete()

    fun vaultFileExists(id: String): Boolean = vaultFile(id).exists()

    companion object {
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH = 256

        fun generateSalt(): ByteArray {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            return salt
        }

        fun hashSecret(secret: String, salt: ByteArray): String {
            val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return Base64.encodeToString(hash, Base64.NO_WRAP)
        }

        fun saltToBase64(salt: ByteArray): String =
            Base64.encodeToString(salt, Base64.NO_WRAP)

        fun saltFromBase64(s: String): ByteArray =
            Base64.decode(s, Base64.NO_WRAP)

        fun verifySecret(secret: String, saltB64: String, hashB64: String): Boolean {
            val salt = saltFromBase64(saltB64)
            val computed = hashSecret(secret, salt)
            return MessageDigest.isEqual(
                computed.toByteArray(Charsets.UTF_8),
                hashB64.toByteArray(Charsets.UTF_8)
            )
        }

        /** Clé de récupération lisible : 4 blocs de 4 caractères hex. */
        fun generateRecoveryKey(): String {
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02X".format(it) }
            return hex.chunked(4).joinToString("-")
        }

        fun normalizeRecoveryKey(key: String): String =
            key.replace("-", "").replace(" ", "").uppercase()
    }
}
