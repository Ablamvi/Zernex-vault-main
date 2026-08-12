package com.zernex.vault.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zernex.vault.security.CryptoManager

/**
 * Préférences chiffrées : type de verrou, hash PIN/schéma,
 * questions de récupération, hash de la clé de récupération.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "zernex_vault_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP, false)
        set(v) = prefs.edit().putBoolean(KEY_SETUP, v).apply()

    var lockType: LockType
        get() = LockType.valueOf(prefs.getString(KEY_LOCK_TYPE, LockType.PIN.name)!!)
        set(v) = prefs.edit().putString(KEY_LOCK_TYPE, v.name).apply()

    fun setLockSecret(secret: String) {
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashSecret(secret, salt)
        prefs.edit()
            .putString(KEY_SECRET_SALT, CryptoManager.saltToBase64(salt))
            .putString(KEY_SECRET_HASH, hash)
            .apply()
    }

    fun verifyLockSecret(secret: String): Boolean {
        val salt = prefs.getString(KEY_SECRET_SALT, null) ?: return false
        val hash = prefs.getString(KEY_SECRET_HASH, null) ?: return false
        return CryptoManager.verifySecret(secret, salt, hash)
    }

    fun setRecoveryQuestions(q1: String, a1: String, q2: String, a2: String) {
        val s1 = CryptoManager.generateSalt()
        val s2 = CryptoManager.generateSalt()
        prefs.edit()
            .putString(KEY_Q1, q1)
            .putString(KEY_A1_SALT, CryptoManager.saltToBase64(s1))
            .putString(KEY_A1_HASH, CryptoManager.hashSecret(a1.trim().lowercase(), s1))
            .putString(KEY_Q2, q2)
            .putString(KEY_A2_SALT, CryptoManager.saltToBase64(s2))
            .putString(KEY_A2_HASH, CryptoManager.hashSecret(a2.trim().lowercase(), s2))
            .apply()
    }

    fun getQuestion1(): String = prefs.getString(KEY_Q1, "") ?: ""
    fun getQuestion2(): String = prefs.getString(KEY_Q2, "") ?: ""

    fun verifyAnswers(a1: String, a2: String): Boolean {
        val s1 = prefs.getString(KEY_A1_SALT, null) ?: return false
        val h1 = prefs.getString(KEY_A1_HASH, null) ?: return false
        val s2 = prefs.getString(KEY_A2_SALT, null) ?: return false
        val h2 = prefs.getString(KEY_A2_HASH, null) ?: return false
        return CryptoManager.verifySecret(a1.trim().lowercase(), s1, h1) &&
                CryptoManager.verifySecret(a2.trim().lowercase(), s2, h2)
    }

    fun setRecoveryKey(rawKey: String) {
        val normalized = CryptoManager.normalizeRecoveryKey(rawKey)
        val salt = CryptoManager.generateSalt()
        prefs.edit()
            .putString(KEY_REC_SALT, CryptoManager.saltToBase64(salt))
            .putString(KEY_REC_HASH, CryptoManager.hashSecret(normalized, salt))
            .apply()
    }

    fun verifyRecoveryKey(rawKey: String): Boolean {
        val salt = prefs.getString(KEY_REC_SALT, null) ?: return false
        val hash = prefs.getString(KEY_REC_HASH, null) ?: return false
        val normalized = CryptoManager.normalizeRecoveryKey(rawKey)
        return CryptoManager.verifySecret(normalized, salt, hash)
    }

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILS, 0)
        set(v) = prefs.edit().putInt(KEY_FAILS, v).apply()

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT, 0L)
        set(v) = prefs.edit().putLong(KEY_LOCKOUT, v).apply()

    companion object {
        private const val KEY_SETUP = "setup_done"
        private const val KEY_LOCK_TYPE = "lock_type"
        private const val KEY_SECRET_SALT = "secret_salt"
        private const val KEY_SECRET_HASH = "secret_hash"
        private const val KEY_Q1 = "q1"
        private const val KEY_A1_SALT = "a1_salt"
        private const val KEY_A1_HASH = "a1_hash"
        private const val KEY_Q2 = "q2"
        private const val KEY_A2_SALT = "a2_salt"
        private const val KEY_A2_HASH = "a2_hash"
        private const val KEY_REC_SALT = "rec_salt"
        private const val KEY_REC_HASH = "rec_hash"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKOUT = "lockout_until"
    }
}
