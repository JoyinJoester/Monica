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
    
    private val settingsManager = SettingsManager(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isVerificationDisabled = false

    init {
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
    private val DATA_PREFIX_V2 = "V2|"
    
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
        private const val SECURITY_QUESTION_2_ID_KEY = "security_question_2_id"
        private const val SECURITY_QUESTION_2_ANSWER_KEY = "security_question_2_answer"
        private const val PBKDF2_ITERATIONS = 100000
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
        return result
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
            .apply()
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
    fun setSecurityQuestions(question1Id: Int, answer1: String, question2Id: Int, answer2: String) {
        val hashedAnswer1 = hashAnswer(answer1)
        val hashedAnswer2 = hashAnswer(answer2)
        
        sharedPreferences.edit()
            .putInt(SECURITY_QUESTION_1_ID_KEY, question1Id)
            .putString(SECURITY_QUESTION_1_ANSWER_KEY, hashedAnswer1)
            .putInt(SECURITY_QUESTION_2_ID_KEY, question2Id)
            .putString(SECURITY_QUESTION_2_ANSWER_KEY, hashedAnswer2)
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
            .remove(SECURITY_QUESTION_2_ID_KEY)
            .remove(SECURITY_QUESTION_2_ANSWER_KEY)
            .apply()
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

    /**
     * AES encryption for sensitive data (additional layer)
     * Automatically chooses between V2 (Secure KeyStore) and V1 (Legacy) based on biometric settings.
     */
    fun encryptData(data: String): String {
        // Decide whether to use Secure Key (V2) or Legacy Key (V1)
        if (isBiometricEnabled()) {
            return try {
                encryptDataV2(data)
            } catch (e: Exception) {
                android.util.Log.e("SecurityManager", "V2 Encryption failed, falling back to V1", e)
                // If V2 fails (e.g. auth required), we might fallback to V1 to avoid data loss during save,
                // BUT this defeats the security purpose. 
                // However, crashing is worse.
                // Given user requirement "reject key if biometric changed", throwing exception is correct for KeyPermanentlyInvalidatedException.
                if (e is android.security.keystore.KeyPermanentlyInvalidatedException || e is UserNotAuthenticatedException) {
                    throw e
                }
                encryptDataV1(data)
            }
        } else {
            return encryptDataV1(data)
        }
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
        return try {
            val combined = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
            if (combined.size <= 12) {
                // If the array is empty, it might be just an empty/invalid string that wasn't caught by isEmpty()
                if (combined.isNotEmpty()) {
                    android.util.Log.e("SecurityManager", "Decryption failed: payload too short (len=${combined.size})")
                }
                return encryptedData
            }

            // Extract IV and encrypted data
            val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keyBytes = masterKey.toString().toByteArray().copyOf(32)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            val decryptedBytes = cipher.doFinal(encrypted)
            String(decryptedBytes, kotlin.text.Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Decryption failed", e)
            // Fallback to original data if decryption fails
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
}
