package takagi.ru.monica.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import takagi.ru.monica.data.PredefinedSecurityQuestions
import takagi.ru.monica.utils.SettingsManager
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security manager for encryption and master password handling
 */
class SecurityManager(private val context: Context) {

    enum class VaultAccessState {
        ACCESSIBLE,
        REQUIRES_PASSWORD_REENTRY,
        LOCKED
    }

    private enum class WrapperMode {
        AUTH,
        COMPAT
    }

    private data class WrappedMdkBlob(
        val mode: WrapperMode,
        val payload: String
    )
    
    private val settingsManager = SettingsManager(context)
    private val logTag = "SecurityManager"
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isVerificationDisabled = false
    @Volatile
    private var mdkAuthUnavailableUntilMillis: Long = 0L
    @Volatile
    private var hasLoggedMdkAuthExpiredWarning = false
    @Volatile
    private var hasLoggedMdkFallbackEncryption = false

    private val mdkAuthCooldownMillis = 30_000L

    init {
        SecurityDiagLogger.initialize(context.applicationContext)
        scope.launch {
            settingsManager.settingsFlow.collect { settings ->
                isVerificationDisabled = settings.disablePasswordVerification
                android.util.Log.d("SecurityManager", "Updated cache: disablePasswordVerification = $isVerificationDisabled")
            }
        }
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Secure Data Key Alias and Prefix
    private val KEY_ALIAS_DATA = "monica_data_key_v2"
    private val KEY_ALIAS_DATA_COMPAT = "monica_data_key_v2_compat"
    private val DATA_PREFIX_V2 = "V2|"
    private val WRAPPER_PREFIX_AUTH = "AU|"
    private val WRAPPER_PREFIX_COMPAT = "CP|"
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "monica_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val MASTER_PASSWORD_HASH_KEY = "master_password_hash"
        private const val MASTER_PASSWORD_SALT_KEY = "master_password_salt"
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        private const val AUTO_LOCK_TIMEOUT_KEY = "auto_lock_timeout"
        private const val SECURITY_QUESTION_1_ID_KEY = "security_question_1_id"
        private const val SECURITY_QUESTION_1_ANSWER_KEY = "security_question_1_answer"
        private const val SECURITY_QUESTION_1_TEXT_KEY = "security_question_1_text"
        private const val SECURITY_QUESTION_2_ID_KEY = "security_question_2_id"
        private const val SECURITY_QUESTION_2_ANSWER_KEY = "security_question_2_answer"
        private const val SECURITY_QUESTION_2_TEXT_KEY = "security_question_2_text"
        private const val PBKDF2_ITERATIONS = 100000
        private const val MDK_PASSWORD_BLOB_KEY = "mdk_password_blob"
        private const val MDK_PASSWORD_SALT_KEY = "mdk_password_salt"
        private const val MDK_KEYSTORE_BLOB_KEY = "mdk_keystore_blob"
        private const val MDK_READY_KEY = "mdk_ready"
        
        // V2 Bitwarden 凭据存储键
        private const val BITWARDEN_ACCESS_TOKEN_KEY = "bitwarden_access_token"
        private const val BITWARDEN_REFRESH_TOKEN_KEY = "bitwarden_refresh_token"
        private const val BITWARDEN_TOKEN_EXPIRY_KEY = "bitwarden_token_expiry"
        private const val BITWARDEN_USER_EMAIL_KEY = "bitwarden_user_email"
        private const val BITWARDEN_USER_ID_KEY = "bitwarden_user_id"
        private const val BITWARDEN_MASTER_KEY_HASH_KEY = "bitwarden_master_key_hash"
        private const val BITWARDEN_SYMMETRIC_KEY_KEY = "bitwarden_symmetric_key"
        private const val BITWARDEN_PRIVATE_KEY_KEY = "bitwarden_private_key"
        private const val BITWARDEN_SERVER_URL_KEY = "bitwarden_server_url"
        private const val BITWARDEN_CONNECTED_KEY = "bitwarden_connected"

        @Volatile
        private var processCachedMdk: ByteArray? = null

        fun clearRuntimeUnlockCache() {
            processCachedMdk = null
        }
    }
    
    /**
     * Hash the master password using PBKDF2
     */
    fun hashMasterPassword(password: String, salt: ByteArray? = null): Pair<String, ByteArray> {
        val actualSalt = salt ?: generateSalt()
        val spec = PBEKeySpec(password.toCharArray(), actualSalt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Pair(hash.joinToString("") { "%02x".format(it) }, actualSalt)
    }
    
    /**
     * Generate a random salt
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }
    
    /**
     * Verify the master password
     * 如果开发者设置中启用了"关闭密码验证",则直接返回true
     */
    fun verifyMasterPassword(inputPassword: String): Boolean {
        // 检查是否禁用密码验证(开发者选项) - 使用缓存值，避免 runBlocking 阻塞主线程
        android.util.Log.d("SecurityManager", "Password verification check: disabled = $isVerificationDisabled")
        
        // 如果禁用验证,直接返回true
        if (isVerificationDisabled) {
            android.util.Log.d("SecurityManager", "Password verification BYPASSED by developer settings")
            return true
        }
        
        android.util.Log.d("SecurityManager", "Performing normal password verification")
        val storedHash = sharedPreferences.getString(MASTER_PASSWORD_HASH_KEY, null) ?: return false
        val storedSalt = sharedPreferences.getString(MASTER_PASSWORD_SALT_KEY, null)?.let { saltStr ->
            saltStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } ?: return false
        
        val (computedHash, _) = hashMasterPassword(inputPassword, storedSalt)
        val result = computedHash == storedHash
        android.util.Log.d("SecurityManager", "Password verification result: $result")
        if (result) {
            try {
                ensureMdkInitializedWithPassword(inputPassword)
            } catch (e: Exception) {
                android.util.Log.w("SecurityManager", "MDK init failed: ${e.message}")
            }
        }
        return result
    }

    fun unlockVaultWithPassword(inputPassword: String): Boolean {
        if (!isMasterPasswordSet()) {
            return true
        }

        android.util.Log.d("SecurityManager", "Vault unlock requested. verificationDisabled=$isVerificationDisabled")

        return try {
            if (isVerificationDisabled) {
                ensureMdkInitializedWithPassword(inputPassword)
                true
            } else {
                val storedHash = sharedPreferences.getString(MASTER_PASSWORD_HASH_KEY, null) ?: return false
                val storedSalt = sharedPreferences.getString(MASTER_PASSWORD_SALT_KEY, null)?.let { saltStr ->
                    saltStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } ?: return false

                val (computedHash, _) = hashMasterPassword(inputPassword, storedSalt)
                if (computedHash != storedHash) {
                    return false
                }

                ensureMdkInitializedWithPassword(inputPassword)
                true
            }
        } catch (e: Exception) {
            android.util.Log.w("SecurityManager", "Vault password unlock failed: ${e.message}")
            false
        }
    }

    fun unlockVaultWithBiometric(): Boolean {
        if (!isMasterPasswordSet()) {
            return true
        }

        return try {
            ensureMdkKeystoreWrapper()
            val mdk = getMdkForCrypto()
            mdk != null && mdk.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.w("SecurityManager", "Vault biometric unlock failed: ${e.message}")
            false
        }
    }

    fun isVaultRuntimeUnlocked(): Boolean {
        return processCachedMdk?.isNotEmpty() == true
    }

    /**
     * Unified success hook for any authentication flow (password/biometric/app/IME/autofill).
     * Keeps session and keystore cooldown states in sync to avoid false locked prompts.
     */
    fun markVaultAuthenticated() {
        SessionManager.markUnlocked()
        mdkAuthUnavailableUntilMillis = 0L
        hasLoggedMdkAuthExpiredWarning = false
        hasLoggedMdkFallbackEncryption = false
    }

    fun forceVaultReauthentication(reason: String) {
        android.util.Log.w(logTag, "forceVaultReauthentication: $reason")
        SecurityDiagLogger.append("W/$logTag forceVaultReauthentication: $reason")
        SessionManager.markLocked()
        mdkAuthUnavailableUntilMillis = 0L
        hasLoggedMdkAuthExpiredWarning = false
        hasLoggedMdkFallbackEncryption = false
    }

    fun shouldForceVaultReauthenticationAfterDecryptFailure(error: Throwable?): Boolean {
        if (error == null || !isMasterPasswordSet()) return false
        if (error is android.security.keystore.KeyPermanentlyInvalidatedException) return true
        if (error is UserNotAuthenticatedException) return true
        if (error.message == "MDK not available") return true
        return !isVaultRuntimeUnlocked() && requiresPasswordReentryForWrapperRebuild()
    }

    fun handleVaultDecryptFailure(error: Throwable?): Boolean {
        if (!shouldForceVaultReauthenticationAfterDecryptFailure(error)) {
            return false
        }
        forceVaultReauthentication(
            "decrypt failure requires reauth: ${error?.javaClass?.simpleName ?: "unknown"}"
        )
        return true
    }

    fun canAccessVaultNowStrict(context: Context, autoLockMinutes: Int): Boolean {
        return canAccessVaultNow(context, autoLockMinutes, allowSessionOnlyFallback = false)
    }

    /**
     * App UI gate for Monica's main process.
     * Requires an active app session before any keystore-backed MDK recovery can restore access.
     */
    fun canRestoreMainAppSession(context: Context, autoLockMinutes: Int): Boolean {
        if (!isMasterPasswordSet()) {
            android.util.Log.d(logTag, "canRestoreMainAppSession: no master password set -> accessible")
            SecurityDiagLogger.append("D/$logTag canRestoreMainAppSession: no master password set -> accessible")
            return true
        }

        SessionManager.updateAutoLockTimeout(autoLockMinutes)
        val sessionActive = SessionManager.canSkipVerification(context)
        if (!sessionActive) {
            android.util.Log.d(logTag, "canRestoreMainAppSession: session inactive -> locked")
            SecurityDiagLogger.append("D/$logTag canRestoreMainAppSession: session inactive -> locked")
            return false
        }

        if (isVaultRuntimeUnlocked()) {
            android.util.Log.d(logTag, "canRestoreMainAppSession: runtime MDK cache present")
            SecurityDiagLogger.append("D/$logTag canRestoreMainAppSession: runtime MDK cache present")
            return true
        }

        if (isMdkReadable()) {
            android.util.Log.d(logTag, "canRestoreMainAppSession: MDK readable with active session")
            SecurityDiagLogger.append("D/$logTag canRestoreMainAppSession: MDK readable with active session")
            markVaultAuthenticated()
            return true
        }

        mdkAuthUnavailableUntilMillis = 0L
        val retriedAccessible = isMdkReadable()
        if (retriedAccessible) {
            android.util.Log.d(logTag, "canRestoreMainAppSession: MDK readable on retry with active session")
            SecurityDiagLogger.append("D/$logTag canRestoreMainAppSession: MDK readable on retry with active session")
            markVaultAuthenticated()
            return true
        }

        android.util.Log.w(logTag, "canRestoreMainAppSession: active session but MDK unavailable")
        SecurityDiagLogger.append("W/$logTag canRestoreMainAppSession: active session but MDK unavailable")
        return false
    }

    /**
     * Single-source vault accessibility check for IME/autofill style callers.
     * Requires both a valid session window and usable runtime key material.
     */
    fun canAccessVaultNow(
        context: Context,
        autoLockMinutes: Int,
        allowSessionOnlyFallback: Boolean = true
    ): Boolean {
        if (!isMasterPasswordSet()) {
            android.util.Log.d(logTag, "canAccessVaultNow: no master password set -> accessible")
            SecurityDiagLogger.append("D/$logTag canAccessVaultNow: no master password set -> accessible")
            return true
        }
        if (isVaultRuntimeUnlocked()) {
            android.util.Log.d(logTag, "canAccessVaultNow: runtime MDK cache present -> accessible")
            SecurityDiagLogger.append("D/$logTag canAccessVaultNow: runtime MDK cache present -> accessible")
            return true
        }
        val hasKeystoreWrapper = sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)

        val mdkAccessible = isMdkReadable()

        // Cross-process/cold-process recovery: if keystore-authenticated MDK is readable,
        // treat vault as unlocked and sync session state immediately.
        if (mdkAccessible) {
            android.util.Log.d(logTag, "canAccessVaultNow: MDK readable on first attempt")
            SecurityDiagLogger.append("D/$logTag canAccessVaultNow: MDK readable on first attempt")
            markVaultAuthenticated()
            return true
        }

        SessionManager.updateAutoLockTimeout(autoLockMinutes)
        val sessionActive = SessionManager.canSkipVerification(context)
        if (!sessionActive) {
            android.util.Log.d(logTag, "canAccessVaultNow: session inactive -> locked")
            SecurityDiagLogger.append("D/$logTag canAccessVaultNow: session inactive -> locked")
            return false
        }

        // Session is still valid but MDK read previously failed (often due a stale auth cooldown).
        // Force one immediate retry before reporting locked state.
        mdkAuthUnavailableUntilMillis = 0L
        val retriedAccessible = isMdkReadable()

        if (retriedAccessible) {
            android.util.Log.d(logTag, "canAccessVaultNow: MDK readable on retry")
            SecurityDiagLogger.append("D/$logTag canAccessVaultNow: MDK readable on retry")
            markVaultAuthenticated()
            return true
        }

        // Keyguard-like graceful fallback:
        // in clone/cold-process scenarios, biometric/session can be valid while keystore wrapper
        // is temporarily unavailable. Prefer allowing access over hard-blocking with "vault locked".
        val authCooldownActive = System.currentTimeMillis() < mdkAuthUnavailableUntilMillis
        if (allowSessionOnlyFallback && (!hasKeystoreWrapper || authCooldownActive)) {
            SessionManager.markUnlocked()
            android.util.Log.w(
                logTag,
                "Allowing session-only vault access fallback: wrapperPresent=$hasKeystoreWrapper, authCooldownActive=$authCooldownActive"
            )
            SecurityDiagLogger.append(
                "W/$logTag canAccessVaultNow: session-only fallback wrapperPresent=$hasKeystoreWrapper authCooldownActive=$authCooldownActive"
            )
            return true
        }

        android.util.Log.w(
            logTag,
            "canAccessVaultNow: locked after retry; wrapperPresent=$hasKeystoreWrapper, sessionActive=$sessionActive, authCooldownActive=$authCooldownActive"
        )
        SecurityDiagLogger.append(
            "W/$logTag canAccessVaultNow: locked after retry wrapperPresent=$hasKeystoreWrapper sessionActive=$sessionActive authCooldownActive=$authCooldownActive"
        )

        return false
    }

    fun getVaultAccessState(context: Context, autoLockMinutes: Int): VaultAccessState {
        if (canAccessVaultNowStrict(context, autoLockMinutes)) {
            return VaultAccessState.ACCESSIBLE
        }
        return if (requiresPasswordReentryForWrapperRebuild()) {
            VaultAccessState.REQUIRES_PASSWORD_REENTRY
        } else {
            VaultAccessState.LOCKED
        }
    }

    fun requiresPasswordReentryForWrapperRebuild(): Boolean {
        if (!isMasterPasswordSet()) return false
        if (isVaultRuntimeUnlocked()) return false
        val ready = sharedPreferences.getBoolean(MDK_READY_KEY, false)
        if (!ready) return false
        val hasPasswordBlob = sharedPreferences.contains(MDK_PASSWORD_BLOB_KEY)
        if (!hasPasswordBlob) return false
        val hasKeystoreBlob = sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)
        val keystoreAliasMissing = hasKeystoreBlob && !hasSecureKeyAlias()
        val requires = !hasKeystoreBlob || keystoreAliasMissing
        if (requires) {
            android.util.Log.w(
                logTag,
                "requiresPasswordReentryForWrapperRebuild=true: ready=$ready, hasPasswordBlob=$hasPasswordBlob, hasKeystoreBlob=$hasKeystoreBlob, keystoreAliasMissing=$keystoreAliasMissing"
            )
            SecurityDiagLogger.append(
                "W/$logTag requiresPasswordReentryForWrapperRebuild=true ready=$ready hasPasswordBlob=$hasPasswordBlob hasKeystoreBlob=$hasKeystoreBlob keystoreAliasMissing=$keystoreAliasMissing"
            )
        }
        return requires
    }

    fun rebuildKeystoreWrapperFromRuntimeCacheIfNeeded(): Boolean {
        if (sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)) {
            android.util.Log.d(logTag, "rebuildKeystoreWrapperFromRuntimeCacheIfNeeded: wrapper already exists")
            SecurityDiagLogger.append("D/$logTag rebuildWrapper: wrapper already exists")
            return true
        }
        val mdk = processCachedMdk
        if (mdk == null || mdk.isEmpty()) {
            android.util.Log.w(logTag, "rebuildKeystoreWrapperFromRuntimeCacheIfNeeded: runtime MDK cache missing")
            SecurityDiagLogger.append("W/$logTag rebuildWrapper: runtime MDK cache missing")
            return false
        }
        val persisted = persistKeystoreWrappedMdk(mdk)
        android.util.Log.d(logTag, "rebuildKeystoreWrapperFromRuntimeCacheIfNeeded: persisted=$persisted")
        SecurityDiagLogger.append("D/$logTag rebuildWrapper: persisted=$persisted")
        return persisted
    }
    
    /**
     * Set the master password
     */
    fun setMasterPassword(password: String) {
        val (hashedPassword, salt) = hashMasterPassword(password)
        sharedPreferences.edit()
            .putString(MASTER_PASSWORD_HASH_KEY, hashedPassword)
            .putString(MASTER_PASSWORD_SALT_KEY, salt.joinToString("") { "%02x".format(it) })
            .apply()
        try {
            ensureMdkInitializedWithPassword(password, true)
        } catch (e: Exception) {
            android.util.Log.w("SecurityManager", "MDK init on setMasterPassword failed: ${e.message}")
        }
    }
    
    /**
     * Check if master password is set
     */
    fun isMasterPasswordSet(): Boolean {
        return sharedPreferences.contains(MASTER_PASSWORD_HASH_KEY)
    }
    
    /**
     * Reset master password - requires current password verification
     */
    fun resetMasterPassword(currentPassword: String, newPassword: String): Boolean {
        // Verify current password first
        if (!verifyMasterPassword(currentPassword)) {
            return false
        }
        
        // Set new password
        setMasterPassword(newPassword)
        return true
    }
    
    /**
     * Clear all security data (for complete reset scenarios)
     */
    fun clearSecurityData() {
        sharedPreferences.edit()
            .remove(MASTER_PASSWORD_HASH_KEY)
            .remove(MASTER_PASSWORD_SALT_KEY)
            .remove(BIOMETRIC_ENABLED_KEY)
            .remove(AUTO_LOCK_TIMEOUT_KEY)
            .remove(SECURITY_QUESTION_1_ID_KEY)
            .remove(SECURITY_QUESTION_1_ANSWER_KEY)
            .remove(SECURITY_QUESTION_2_ID_KEY)
            .remove(SECURITY_QUESTION_2_ANSWER_KEY)
            .remove(MDK_PASSWORD_BLOB_KEY)
            .remove(MDK_PASSWORD_SALT_KEY)
            .remove(MDK_KEYSTORE_BLOB_KEY)
            .remove(MDK_READY_KEY)
            // V2 Bitwarden 凭据
            .remove(BITWARDEN_ACCESS_TOKEN_KEY)
            .remove(BITWARDEN_REFRESH_TOKEN_KEY)
            .remove(BITWARDEN_TOKEN_EXPIRY_KEY)
            .remove(BITWARDEN_USER_EMAIL_KEY)
            .remove(BITWARDEN_USER_ID_KEY)
            .remove(BITWARDEN_MASTER_KEY_HASH_KEY)
            .remove(BITWARDEN_SYMMETRIC_KEY_KEY)
            .remove(BITWARDEN_PRIVATE_KEY_KEY)
            .remove(BITWARDEN_SERVER_URL_KEY)
            .remove(BITWARDEN_CONNECTED_KEY)
            .apply()
        clearRuntimeUnlockCache()
    }
    
    /**
     * Biometric settings
     */
    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }
    
    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }
    
    /**
     * Auto-lock timeout settings (in minutes)
     */
    fun setAutoLockTimeout(minutes: Int) {
        sharedPreferences.edit().putInt(AUTO_LOCK_TIMEOUT_KEY, minutes).apply()
    }
    
    fun getAutoLockTimeout(): Int {
        return sharedPreferences.getInt(AUTO_LOCK_TIMEOUT_KEY, 5) // Default 5 minutes
    }
    
    /**
     * Security Questions Management
     */
    fun setSecurityQuestions(
        question1Id: Int,
        answer1: String,
        question2Id: Int,
        answer2: String,
        question1Text: String? = null,
        question2Text: String? = null
    ) {
        val hashedAnswer1 = hashAnswer(answer1)
        val hashedAnswer2 = hashAnswer(answer2)
        
        sharedPreferences.edit()
            .putInt(SECURITY_QUESTION_1_ID_KEY, question1Id)
            .putString(SECURITY_QUESTION_1_ANSWER_KEY, hashedAnswer1)
            .putString(
                SECURITY_QUESTION_1_TEXT_KEY,
                if (PredefinedSecurityQuestions.isCustomQuestion(question1Id)) question1Text else null
            )
            .putInt(SECURITY_QUESTION_2_ID_KEY, question2Id)
            .putString(SECURITY_QUESTION_2_ANSWER_KEY, hashedAnswer2)
            .putString(
                SECURITY_QUESTION_2_TEXT_KEY,
                if (PredefinedSecurityQuestions.isCustomQuestion(question2Id)) question2Text else null
            )
            .apply()
    }
    
    fun areSecurityQuestionsSet(): Boolean {
        return sharedPreferences.contains(SECURITY_QUESTION_1_ID_KEY) &&
                sharedPreferences.contains(SECURITY_QUESTION_2_ID_KEY)
    }
    
    fun getSecurityQuestion1Id(): Int {
        return sharedPreferences.getInt(SECURITY_QUESTION_1_ID_KEY, -1)
    }
    
    fun getSecurityQuestion2Id(): Int {
        return sharedPreferences.getInt(SECURITY_QUESTION_2_ID_KEY, -1)
    }

    fun getSecurityQuestion1Text(isZh: Boolean = false): String? {
        return resolveSecurityQuestionText(
            id = getSecurityQuestion1Id(),
            customText = sharedPreferences.getString(SECURITY_QUESTION_1_TEXT_KEY, null),
            isZh = isZh
        )
    }

    fun getSecurityQuestion2Text(isZh: Boolean = false): String? {
        return resolveSecurityQuestionText(
            id = getSecurityQuestion2Id(),
            customText = sharedPreferences.getString(SECURITY_QUESTION_2_TEXT_KEY, null),
            isZh = isZh
        )
    }
    
    fun verifySecurityAnswers(answer1: String, answer2: String): Boolean {
        val storedAnswer1 = sharedPreferences.getString(SECURITY_QUESTION_1_ANSWER_KEY, null) ?: return false
        val storedAnswer2 = sharedPreferences.getString(SECURITY_QUESTION_2_ANSWER_KEY, null) ?: return false
        
        val hashedAnswer1 = hashAnswer(answer1)
        val hashedAnswer2 = hashAnswer(answer2)
        
        return hashedAnswer1 == storedAnswer1 && hashedAnswer2 == storedAnswer2
    }
    
    private fun hashAnswer(answer: String): String {
        val cleanAnswer = answer.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(cleanAnswer.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }
    
    fun clearSecurityQuestions() {
        sharedPreferences.edit()
            .remove(SECURITY_QUESTION_1_ID_KEY)
            .remove(SECURITY_QUESTION_1_ANSWER_KEY)
            .remove(SECURITY_QUESTION_1_TEXT_KEY)
            .remove(SECURITY_QUESTION_2_ID_KEY)
            .remove(SECURITY_QUESTION_2_ANSWER_KEY)
            .remove(SECURITY_QUESTION_2_TEXT_KEY)
            .apply()
    }

    private fun resolveSecurityQuestionText(
        id: Int,
        customText: String?,
        isZh: Boolean
    ): String? {
        return if (PredefinedSecurityQuestions.isCustomQuestion(id)) {
            customText
        } else {
            PredefinedSecurityQuestions.getQuestionById(id, isZh)?.questionText
        }
    }
    
    /**
     * Get or create a secure key from Android KeyStore
     * This key requires user authentication (biometric) to be used.
     */
    private fun getOrGenerateSecureKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        if (keyStore.containsAlias(KEY_ALIAS_DATA)) {
            val entry = keyStore.getEntry(KEY_ALIAS_DATA, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }
        
        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS_DATA,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationValidityDurationSeconds(300) // Allow use for 5 minutes after authentication
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true) // Key becomes permanently invalid if new biometric is enrolled
        }
        
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun getOrGenerateCompatSecureKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS_DATA_COMPAT)) {
            val entry = keyStore.getEntry(KEY_ALIAS_DATA_COMPAT, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS_DATA_COMPAT,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun hasSecureKeyAlias(alias: String = KEY_ALIAS_DATA): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(alias)
        }.getOrDefault(false)
    }

    private fun parseWrappedMdkBlob(rawBlob: String): WrappedMdkBlob {
        return when {
            rawBlob.startsWith(WRAPPER_PREFIX_COMPAT) -> WrappedMdkBlob(
                mode = WrapperMode.COMPAT,
                payload = rawBlob.removePrefix(WRAPPER_PREFIX_COMPAT)
            )
            rawBlob.startsWith(WRAPPER_PREFIX_AUTH) -> WrappedMdkBlob(
                mode = WrapperMode.AUTH,
                payload = rawBlob.removePrefix(WRAPPER_PREFIX_AUTH)
            )
            else -> WrappedMdkBlob(
                mode = WrapperMode.AUTH,
                payload = rawBlob
            )
        }
    }

    private fun hasRequiredAliasForStoredWrapper(): Boolean {
        val rawBlob = sharedPreferences.getString(MDK_KEYSTORE_BLOB_KEY, null) ?: return true
        val wrapper = parseWrappedMdkBlob(rawBlob)
        return when (wrapper.mode) {
            WrapperMode.AUTH -> hasSecureKeyAlias(KEY_ALIAS_DATA)
            WrapperMode.COMPAT -> hasSecureKeyAlias(KEY_ALIAS_DATA_COMPAT)
        }
    }

    private fun isMdkReadable(): Boolean {
        return try {
            val mdk = getMdkForCrypto()
            mdk != null && mdk.isNotEmpty()
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun clearKeystoreWrappedMdk(reason: String) {
        android.util.Log.w("SecurityManager", "Clearing stale MDK keystore wrapper: $reason")
        sharedPreferences.edit().remove(MDK_KEYSTORE_BLOB_KEY).apply()
    }

    private fun persistKeystoreWrappedMdk(mdk: ByteArray): Boolean {
        if (mdk.isEmpty()) {
            android.util.Log.w(logTag, "persistKeystoreWrappedMdk: skip empty MDK")
            return false
        }
        return try {
            val ksKey = getOrGenerateSecureKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, ksKey)
            val iv = cipher.iv
            val enc = cipher.doFinal(mdk)
            val combined = iv + enc
            val blob = WRAPPER_PREFIX_AUTH + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            sharedPreferences.edit()
                .putString(MDK_KEYSTORE_BLOB_KEY, blob)
                .apply()
            android.util.Log.d(logTag, "persistKeystoreWrappedMdk: success")
            SecurityDiagLogger.append("D/$logTag persistKeystoreWrappedMdk: success")
            true
        } catch (e: UserNotAuthenticatedException) {
            android.util.Log.w(logTag, "persistKeystoreWrappedMdk: user not authenticated")
            SecurityDiagLogger.append("W/$logTag persistKeystoreWrappedMdk: user not authenticated")
            persistCompatKeystoreWrappedMdk(mdk)
        } catch (e: Exception) {
            android.util.Log.w(logTag, "persistKeystoreWrappedMdk failed: ${e.javaClass.simpleName}, message=${e.message}")
            SecurityDiagLogger.append("W/$logTag persistKeystoreWrappedMdk failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun persistCompatKeystoreWrappedMdk(mdk: ByteArray): Boolean {
        return try {
            val compatKey = getOrGenerateCompatSecureKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, compatKey)
            val iv = cipher.iv
            val enc = cipher.doFinal(mdk)
            val combined = iv + enc
            val blob = WRAPPER_PREFIX_COMPAT + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            sharedPreferences.edit()
                .putString(MDK_KEYSTORE_BLOB_KEY, blob)
                .apply()
            android.util.Log.w(logTag, "persistCompatKeystoreWrappedMdk: saved compatibility wrapper")
            SecurityDiagLogger.append("W/$logTag persistCompatKeystoreWrappedMdk: saved compatibility wrapper")
            true
        } catch (e: Exception) {
            android.util.Log.w(logTag, "persistCompatKeystoreWrappedMdk failed: ${e.javaClass.simpleName}, message=${e.message}")
            SecurityDiagLogger.append("W/$logTag persistCompatKeystoreWrappedMdk failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun generateRandom(bytes: Int): ByteArray {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return b
    }

    private fun deriveAesKeyFromPassword(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun aesGcmEncrypt(key: SecretKeySpec, data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val enc = cipher.doFinal(data)
        val combined = iv + enc
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    private fun aesGcmDecrypt(key: SecretKeySpec, combinedBase64: String): ByteArray {
        val combined = android.util.Base64.decode(combinedBase64, android.util.Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val enc = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(enc)
    }

    private fun ensureMdkInitializedWithPassword(password: String, forceUpdate: Boolean = false) {
        val hasPasswordBlob = sharedPreferences.contains(MDK_PASSWORD_BLOB_KEY)
        val hasKeystoreBlob = sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)
        android.util.Log.d(
            logTag,
            "ensureMdkInitializedWithPassword: forceUpdate=$forceUpdate, hasPasswordBlob=$hasPasswordBlob, hasKeystoreBlob=$hasKeystoreBlob"
        )
        if (hasKeystoreBlob && !hasRequiredAliasForStoredWrapper()) {
            clearKeystoreWrappedMdk("secure key alias missing; likely app clone or restored app data")
        }
        var mdk: ByteArray? = null
        if (!hasPasswordBlob && !hasKeystoreBlob) {
            mdk = generateRandom(32)
        }
        val salt = if (forceUpdate || !sharedPreferences.contains(MDK_PASSWORD_SALT_KEY)) {
            generateRandom(32)
        } else {
            val saltHex = sharedPreferences.getString(MDK_PASSWORD_SALT_KEY, null)
            saltHex?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: generateRandom(32)
        }
        val pwKey = deriveAesKeyFromPassword(password, salt)
        val actualMdk = if (hasPasswordBlob && !forceUpdate) {
            val blob = sharedPreferences.getString(MDK_PASSWORD_BLOB_KEY, null)
            if (blob != null) {
                aesGcmDecrypt(pwKey, blob)
            } else {
                mdk ?: getOrCreateMdkBytes()
            }
        } else {
            mdk ?: getOrCreateMdkBytes()
        }
        processCachedMdk = actualMdk
        if (!hasPasswordBlob || forceUpdate) {
            val blob = aesGcmEncrypt(pwKey, actualMdk)
            sharedPreferences.edit()
                .putString(MDK_PASSWORD_BLOB_KEY, blob)
                .putString(MDK_PASSWORD_SALT_KEY, salt.joinToString("") { "%02x".format(it) })
                .putBoolean(MDK_READY_KEY, true)
                .apply()
        } else {
            sharedPreferences.edit().putBoolean(MDK_READY_KEY, true).apply()
        }
        if (!sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)) {
            val persisted = persistKeystoreWrappedMdk(actualMdk)
            android.util.Log.d(
                logTag,
                "ensureMdkInitializedWithPassword: wrapper rebuild after password unlock success=$persisted"
            )
        }
    }

    private fun ensureMdkKeystoreWrapper() {
        if (sharedPreferences.contains(MDK_KEYSTORE_BLOB_KEY)) return
        val mdk = getOrCreateMdkBytes()
        if (mdk.isEmpty()) {
            android.util.Log.w("SecurityManager", "Cannot create MDK keystore wrapper without current MDK")
            return
        }
        persistKeystoreWrappedMdk(mdk)
    }

    private fun getOrCreateMdkBytes(): ByteArray {
        processCachedMdk?.let { cached ->
            if (cached.isNotEmpty()) {
                return cached.copyOf()
            }
        }
        val passwordBlob = sharedPreferences.getString(MDK_PASSWORD_BLOB_KEY, null)
        val keystoreBlob = sharedPreferences.getString(MDK_KEYSTORE_BLOB_KEY, null)
        if (passwordBlob == null && keystoreBlob == null) {
            return generateRandom(32)
        }
        if (keystoreBlob == null) {
            android.util.Log.w("SecurityManager", "MDK keystore wrapper missing and no cached MDK is available")
            return ByteArray(0)
        }
        return try {
            val wrapped = parseWrappedMdkBlob(keystoreBlob)
            val ksKey = when (wrapped.mode) {
                WrapperMode.AUTH -> getOrGenerateSecureKey()
                WrapperMode.COMPAT -> getOrGenerateCompatSecureKey()
            }
            val combined = android.util.Base64.decode(wrapped.payload, android.util.Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val enc = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, ksKey, spec)
            cipher.doFinal(enc)
        } catch (e: Exception) {
            android.util.Log.w("SecurityManager", "Get MDK by keystore failed: ${e.message}")
            ByteArray(0)
        }
    }

    private fun getMdkForCrypto(): ByteArray? {
        processCachedMdk?.let { return it }
        val now = System.currentTimeMillis()
        if (now < mdkAuthUnavailableUntilMillis) {
            return null
        }
        val ready = sharedPreferences.getBoolean(MDK_READY_KEY, false)
        if (!ready) return null
        val rawBlob = sharedPreferences.getString(MDK_KEYSTORE_BLOB_KEY, null) ?: return null
        val wrapped = parseWrappedMdkBlob(rawBlob)
        val usesCompatWrapper = wrapped.mode == WrapperMode.COMPAT
        if (!usesCompatWrapper && !hasSecureKeyAlias(KEY_ALIAS_DATA)) {
            clearKeystoreWrappedMdk("secure key alias missing while reading wrapper; likely app clone or restored app data")
            return null
        }
        return try {
            val ksKey = if (usesCompatWrapper) getOrGenerateCompatSecureKey() else getOrGenerateSecureKey()
            val combined = android.util.Base64.decode(wrapped.payload, android.util.Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val enc = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, ksKey, spec)
            val mdk = cipher.doFinal(enc)
            processCachedMdk = mdk
            mdkAuthUnavailableUntilMillis = 0L
            hasLoggedMdkAuthExpiredWarning = false
            hasLoggedMdkFallbackEncryption = false
            if (usesCompatWrapper) {
                android.util.Log.w(logTag, "getMdkForCrypto: using compatibility wrapper mode")
            }
            mdk
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException: 生物识别已更改，密钥永久失效
            // UserNotAuthenticatedException: 用户认证已过期，需要重新认证
            if (e is android.security.keystore.KeyPermanentlyInvalidatedException) {
                throw e  // 密钥永久失效，必须抛出让用户重新设置
            }
            if (e is UserNotAuthenticatedException) {
                if (!hasLoggedMdkAuthExpiredWarning) {
                    android.util.Log.w("SecurityManager", "User authentication expired, MDK not available")
                    hasLoggedMdkAuthExpiredWarning = true
                }
                // Cooldown to avoid hot-looping keystore access when auth is expired.
                mdkAuthUnavailableUntilMillis = System.currentTimeMillis() + mdkAuthCooldownMillis
                return null  // 认证过期，返回 null 让调用方降级处理
            }
            clearKeystoreWrappedMdk("keystore wrapper unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    private val DATA_PREFIX_MDK = "MDK|"

    /**
     * AES encryption for sensitive data (additional layer)
     * Automatically chooses between V2 (Secure KeyStore) and V1 (Legacy) based on biometric settings.
     * 
     * 安全策略：
     * - 优先使用 MDK 加密（最安全）
     * - 如果 MDK 不可用（认证过期），降级到 V1 加密
     * - 只有当密钥永久失效时才抛出异常
     */
    fun encryptData(data: String): String {
        // 尝试使用 MDK 加密
        val mdk = try {
            getMdkForCrypto()
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            // 密钥永久失效（生物识别已更改），需要用户重新设置
            android.util.Log.e("SecurityManager", "Key permanently invalidated", e)
            throw e
        }
        
        if (mdk != null && mdk.isNotEmpty()) {
            val key = SecretKeySpec(mdk, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val enc = cipher.doFinal(data.toByteArray())
            val combined = iv + enc
            return DATA_PREFIX_MDK + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        }
        
        // MDK 不可用，降级到 V1 加密
        // 注意：V1 使用主密钥派生，安全性较低但不需要生物识别认证
        if (!hasLoggedMdkFallbackEncryption) {
            android.util.Log.d("SecurityManager", "MDK not available, using V1 encryption")
            hasLoggedMdkFallbackEncryption = true
        }
        return encryptDataV1(data)
    }

    /**
     * Compatibility helper: force legacy V1 payload for scenarios where
     * immediate readability is required under unstable MDK auth state.
     */
    fun encryptDataLegacyCompat(data: String): String {
        return encryptDataV1(data)
    }

    private fun encryptDataV2(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrGenerateSecureKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        
        // Combine IV and encrypted data
        val combined = iv + encryptedBytes
        return DATA_PREFIX_V2 + android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
    }

    private fun encryptDataV1(data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keyGenerator = javax.crypto.KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            
            // Use the master key for encryption (Legacy)
            val keyBytes = masterKey.toString().toByteArray().copyOf(32)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            
            // Combine IV and encrypted data
            val combined = iv + encryptedBytes
            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Encryption failed", e)
            // Fallback to original data if encryption fails
            data
        }
    }
    
    fun decryptData(encryptedData: String): String {
        if (encryptedData.isEmpty()) {
            return ""
        }

        if (encryptedData.startsWith(DATA_PREFIX_MDK)) {
            val mdk = getMdkForCrypto()
            if (mdk == null || mdk.isEmpty()) throw Exception("MDK not available")
            val combined = android.util.Base64.decode(encryptedData.substring(DATA_PREFIX_MDK.length), android.util.Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val enc = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            val key = SecretKeySpec(mdk, "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val dec = cipher.doFinal(enc)
            return String(dec, kotlin.text.Charsets.UTF_8)
        }

        if (encryptedData.startsWith(DATA_PREFIX_V2)) {
            return try {
                decryptDataV2(encryptedData)
            } catch (e: Exception) {
                android.util.Log.e("SecurityManager", "V2 Decryption failed", e)
                throw e // Rethrow to let caller handle auth failure
            }
        }

        return decryptDataV1(encryptedData)
    }

    private fun decryptDataV2(encryptedData: String): String {
        val combined = android.util.Base64.decode(encryptedData.substring(DATA_PREFIX_V2.length), android.util.Base64.DEFAULT)
        
        // Extract IV and encrypted data
        val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
        val encrypted = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrGenerateSecureKey()
        
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        val decryptedBytes = cipher.doFinal(encrypted)
        return String(decryptedBytes, kotlin.text.Charsets.UTF_8)
    }

    private fun decryptDataV1(encryptedData: String): String {
        if (encryptedData.isEmpty()) return ""

        val combined = try {
            android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            // Not a Base64 payload, likely plain text.
            return encryptedData
        }

        // Legacy payload format is: 12-byte IV + ciphertext(>=0) + 16-byte GCM tag.
        // Empty plaintext is valid and yields exactly 28 bytes.
        if (combined.size < 28) {
            return encryptedData
        }

        return try {
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keyBytes = masterKey.toString().toByteArray().copyOf(32)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(encrypted)
            String(decryptedBytes, kotlin.text.Charsets.UTF_8)
        } catch (_: Exception) {
            // Fallback to original data if decryption fails.
            encryptedData
        }
    }
    
    /**
     * Generate secure random password
     */
    fun generateSecurePassword(length: Int = 16, includeSymbols: Boolean = true): String {
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        val charset = lowercase + uppercase + numbers + if (includeSymbols) symbols else ""
        val random = SecureRandom()
        
        return (1..length)
        .map { charset[random.nextInt(charset.length)] }
        .joinToString("")
    }
    
    /**
     * Validate password strength
     */
    fun validatePasswordStrength(password: String): PasswordStrength {
        val length = password.length
        val hasLowercase = password.any { it.isLowerCase() }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        
        val score = listOf(
            length >= 8,
            length >= 12,
            hasLowercase,
            hasUppercase,
            hasDigit,
            hasSymbol
        ).count { it }
        
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
    
    enum class PasswordStrength {
        WEAK, MEDIUM, STRONG
    }
    
    // ==================== V2 Bitwarden 凭据管理 ====================
    
    /**
     * Bitwarden 凭据数据类
     * 用于存储登录后的认证信息
     */
    data class BitwardenCredential(
        val accessToken: String,
        val refreshToken: String,
        val tokenExpiry: Long,          // 过期时间戳（毫秒）
        val userEmail: String,
        val userId: String,
        val serverUrl: String = "https://vault.bitwarden.com"  // 默认官方服务器
    )
    
    /**
     * Bitwarden 加密密钥数据类
     * 用于存储解密 Vault 数据所需的密钥
     */
    data class BitwardenCryptoKeys(
        val masterKeyHash: String,      // 主密码哈希（用于验证）
        val symmetricKey: String,       // 对称加密密钥（加密存储）
        val privateKey: String?         // RSA 私钥（加密存储，可选）
    )
    
    /**
     * 保存 Bitwarden 登录凭据
     * 使用 EncryptedSharedPreferences 安全存储
     * 
     * @param credential Bitwarden 凭据
     */
    fun saveBitwardenCredential(credential: BitwardenCredential) {
        // Access Token 和 Refresh Token 使用额外的 AES-GCM 加密层
        val encryptedAccessToken = encryptData(credential.accessToken)
        val encryptedRefreshToken = encryptData(credential.refreshToken)
        
        sharedPreferences.edit()
            .putString(BITWARDEN_ACCESS_TOKEN_KEY, encryptedAccessToken)
            .putString(BITWARDEN_REFRESH_TOKEN_KEY, encryptedRefreshToken)
            .putLong(BITWARDEN_TOKEN_EXPIRY_KEY, credential.tokenExpiry)
            .putString(BITWARDEN_USER_EMAIL_KEY, credential.userEmail)
            .putString(BITWARDEN_USER_ID_KEY, credential.userId)
            .putString(BITWARDEN_SERVER_URL_KEY, credential.serverUrl)
            .putBoolean(BITWARDEN_CONNECTED_KEY, true)
            .apply()
        
        android.util.Log.d("SecurityManager", "Bitwarden credential saved for user: ${credential.userEmail}")
    }
    
    /**
     * 获取 Bitwarden 登录凭据
     * 
     * @return BitwardenCredential 或 null（未登录）
     */
    fun getBitwardenCredential(): BitwardenCredential? {
        if (!isBitwardenConnected()) {
            return null
        }
        
        return try {
            val encryptedAccessToken = sharedPreferences.getString(BITWARDEN_ACCESS_TOKEN_KEY, null)
            val encryptedRefreshToken = sharedPreferences.getString(BITWARDEN_REFRESH_TOKEN_KEY, null)
            
            if (encryptedAccessToken == null || encryptedRefreshToken == null) {
                return null
            }
            
            BitwardenCredential(
                accessToken = decryptData(encryptedAccessToken),
                refreshToken = decryptData(encryptedRefreshToken),
                tokenExpiry = sharedPreferences.getLong(BITWARDEN_TOKEN_EXPIRY_KEY, 0L),
                userEmail = sharedPreferences.getString(BITWARDEN_USER_EMAIL_KEY, "") ?: "",
                userId = sharedPreferences.getString(BITWARDEN_USER_ID_KEY, "") ?: "",
                serverUrl = sharedPreferences.getString(BITWARDEN_SERVER_URL_KEY, "https://vault.bitwarden.com") ?: "https://vault.bitwarden.com"
            )
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Failed to get Bitwarden credential", e)
            null
        }
    }
    
    /**
     * 保存 Bitwarden 加密密钥
     * 这些密钥用于解密从服务器获取的 Vault 数据
     * 
     * @param keys Bitwarden 加密密钥
     */
    fun saveBitwardenCryptoKeys(keys: BitwardenCryptoKeys) {
        // 所有密钥都使用 AES-GCM 加密存储
        val encryptedSymmetricKey = encryptData(keys.symmetricKey)
        val encryptedPrivateKey = keys.privateKey?.let { encryptData(it) }
        
        sharedPreferences.edit()
            .putString(BITWARDEN_MASTER_KEY_HASH_KEY, keys.masterKeyHash)
            .putString(BITWARDEN_SYMMETRIC_KEY_KEY, encryptedSymmetricKey)
            .apply {
                if (encryptedPrivateKey != null) {
                    putString(BITWARDEN_PRIVATE_KEY_KEY, encryptedPrivateKey)
                }
            }
            .apply()
        
        android.util.Log.d("SecurityManager", "Bitwarden crypto keys saved")
    }
    
    /**
     * 获取 Bitwarden 加密密钥
     * 
     * @return BitwardenCryptoKeys 或 null
     */
    fun getBitwardenCryptoKeys(): BitwardenCryptoKeys? {
        return try {
            val masterKeyHash = sharedPreferences.getString(BITWARDEN_MASTER_KEY_HASH_KEY, null)
            val encryptedSymmetricKey = sharedPreferences.getString(BITWARDEN_SYMMETRIC_KEY_KEY, null)
            
            if (masterKeyHash == null || encryptedSymmetricKey == null) {
                return null
            }
            
            val encryptedPrivateKey = sharedPreferences.getString(BITWARDEN_PRIVATE_KEY_KEY, null)
            
            BitwardenCryptoKeys(
                masterKeyHash = masterKeyHash,
                symmetricKey = decryptData(encryptedSymmetricKey),
                privateKey = encryptedPrivateKey?.let { decryptData(it) }
            )
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Failed to get Bitwarden crypto keys", e)
            null
        }
    }
    
    /**
     * 检查是否已连接 Bitwarden
     * 
     * @return true 如果已保存凭据
     */
    fun isBitwardenConnected(): Boolean {
        return sharedPreferences.getBoolean(BITWARDEN_CONNECTED_KEY, false)
    }
    
    /**
     * 检查 Bitwarden Token 是否过期
     * 
     * @return true 如果已过期或未连接
     */
    fun isBitwardenTokenExpired(): Boolean {
        if (!isBitwardenConnected()) return true
        
        val expiry = sharedPreferences.getLong(BITWARDEN_TOKEN_EXPIRY_KEY, 0L)
        // 提前 5 分钟判断为过期，以便有时间刷新
        return System.currentTimeMillis() > (expiry - 5 * 60 * 1000)
    }
    
    /**
     * 更新 Bitwarden Access Token（Token 刷新后调用）
     * 
     * @param newAccessToken 新的 Access Token
     * @param newRefreshToken 新的 Refresh Token（可选）
     * @param newExpiry 新的过期时间
     */
    fun updateBitwardenTokens(
        newAccessToken: String,
        newRefreshToken: String? = null,
        newExpiry: Long
    ) {
        val encryptedAccessToken = encryptData(newAccessToken)
        
        sharedPreferences.edit().apply {
            putString(BITWARDEN_ACCESS_TOKEN_KEY, encryptedAccessToken)
            putLong(BITWARDEN_TOKEN_EXPIRY_KEY, newExpiry)
            
            if (newRefreshToken != null) {
                putString(BITWARDEN_REFRESH_TOKEN_KEY, encryptData(newRefreshToken))
            }
        }.apply()
        
        android.util.Log.d("SecurityManager", "Bitwarden tokens updated, new expiry: $newExpiry")
    }
    
    /**
     * 获取 Bitwarden 用户邮箱
     * 
     * @return 用户邮箱或 null
     */
    fun getBitwardenUserEmail(): String? {
        return sharedPreferences.getString(BITWARDEN_USER_EMAIL_KEY, null)
    }
    
    /**
     * 获取 Bitwarden 服务器 URL
     * 
     * @return 服务器 URL
     */
    fun getBitwardenServerUrl(): String {
        return sharedPreferences.getString(BITWARDEN_SERVER_URL_KEY, "https://vault.bitwarden.com") 
            ?: "https://vault.bitwarden.com"
    }
    
    /**
     * 清除 Bitwarden 凭据（登出时调用）
     * 同时清除所有相关的加密密钥
     */
    fun clearBitwardenCredential() {
        sharedPreferences.edit()
            .remove(BITWARDEN_ACCESS_TOKEN_KEY)
            .remove(BITWARDEN_REFRESH_TOKEN_KEY)
            .remove(BITWARDEN_TOKEN_EXPIRY_KEY)
            .remove(BITWARDEN_USER_EMAIL_KEY)
            .remove(BITWARDEN_USER_ID_KEY)
            .remove(BITWARDEN_MASTER_KEY_HASH_KEY)
            .remove(BITWARDEN_SYMMETRIC_KEY_KEY)
            .remove(BITWARDEN_PRIVATE_KEY_KEY)
            .remove(BITWARDEN_SERVER_URL_KEY)
            .remove(BITWARDEN_CONNECTED_KEY)
            .apply()
        
        android.util.Log.d("SecurityManager", "Bitwarden credential cleared")
    }
    
    /**
     * 验证 Bitwarden 主密码哈希
     * 用于解锁时验证用户输入的主密码是否正确
     * 
     * @param masterPasswordHash 用户输入的主密码生成的哈希
     * @return true 如果匹配
     */
    fun verifyBitwardenMasterPasswordHash(masterPasswordHash: String): Boolean {
        val storedHash = sharedPreferences.getString(BITWARDEN_MASTER_KEY_HASH_KEY, null)
        return storedHash != null && storedHash == masterPasswordHash
    }
}
