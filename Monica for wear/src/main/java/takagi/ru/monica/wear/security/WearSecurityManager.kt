package takagi.ru.monica.wear.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Wear版安全管理器
 * 负责PIN码的安全存储和验证
 */
class WearSecurityManager(context: Context) {
    
    private val prefs: SharedPreferences
    
    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val PREFS_NAME = "wear_security_prefs"
        private const val PIN_HASH_KEY = "pin_hash"
        private const val PIN_SALT_KEY = "pin_salt"
        private const val PIN_FORMAT_VERSION_KEY = "pin_format_version"
        private const val CURRENT_PIN_FORMAT_VERSION = 2
    }
    
    /**
     * 验证PIN码
     */
    fun verifyPin(inputPin: String): Boolean {
        val storedHash = prefs.getString(PIN_HASH_KEY, null) ?: return false
        val salt = prefs.getString(PIN_SALT_KEY, null) ?: return false
        val normalizedPin = normalizePin(inputPin)
        val inputHash = hashPin(normalizedPin, salt)
        return inputHash == storedHash
    }
    
    /**
     * 设置PIN码
     */
    fun setPin(pin: String) {
        val normalizedPin = normalizePin(pin)
        val salt = generateSalt()
        val hash = hashPin(normalizedPin, salt)
        prefs.edit()
            .putString(PIN_HASH_KEY, hash)
            .putString(PIN_SALT_KEY, salt)
            .putInt(PIN_FORMAT_VERSION_KEY, CURRENT_PIN_FORMAT_VERSION)
            .apply()
    }
    
    /**
     * 检查是否已设置PIN码
     */
    fun isPinSet(): Boolean {
        return prefs.contains(PIN_HASH_KEY)
    }
    
    /**
     * 检查是否已设置锁定
     */
    fun isLockSet(): Boolean {
        return isPinSet()
    }
    
    /**
     * 修改PIN码
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) {
            return false
        }
        setPin(newPin)
        return true
    }

    /**
     * 将旧版 PIN 存储标记为新版输入格式。
     * 旧版和新版都使用同一套哈希存储，因此只需要补齐版本元数据。
     */
    fun migrateLegacyPinFormatIfNeeded() {
        val hasPin = prefs.contains(PIN_HASH_KEY) && prefs.contains(PIN_SALT_KEY)
        val version = prefs.getInt(PIN_FORMAT_VERSION_KEY, 0)
        if (hasPin && version < CURRENT_PIN_FORMAT_VERSION) {
            prefs.edit()
                .putInt(PIN_FORMAT_VERSION_KEY, CURRENT_PIN_FORMAT_VERSION)
                .apply()
        }
    }
    
    /**
     * 清除PIN码
     */
    fun clearPin() {
        prefs.edit()
            .remove(PIN_HASH_KEY)
            .remove(PIN_SALT_KEY)
            .apply()
    }
    
    /**
     * 使用PBKDF2对PIN码进行哈希
     */
    private fun hashPin(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 100000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun normalizePin(pin: String): String {
        return pin.filter { it.isDigit() }
    }
    
    /**
     * 生成随机盐值
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }
}
