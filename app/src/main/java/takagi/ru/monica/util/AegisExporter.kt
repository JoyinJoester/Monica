package takagi.ru.monica.util

import com.lambdaworks.crypto.SCrypt
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.util.Base64

/**
 * Aegis格式导出器
 * 支持加密和未加密的Aegis JSON格式导出
 */
class AegisExporter {
    
    companion object {
        private const val SCRYPT_N = 32768 // CPU/内存成本参数
        private const val SCRYPT_R = 8     // 块大小
        private const val SCRYPT_P = 1     // 并行化参数
        private const val SCRYPT_KEY_LENGTH = 32 // 密钥长度（字节）
        private const val GCM_TAG_LENGTH = 128 // GCM标签长度（位）
        private const val AEGIS_VERSION = 1
    }
    
    /**
     * Aegis条目数据类
     */
    data class AegisEntry(
        val uuid: String,
        val name: String,
        val issuer: String,
        val note: String = "",
        val secret: String,
        val algorithm: String = "SHA1",
        val digits: Int = 6,
        val period: Int = 30
    )
    
    /**
     * 导出为未加密的Aegis JSON格式
     * @param entries TOTP条目列表
     * @return JSON字符串
     */
    fun exportToUnencryptedAegisJson(entries: List<AegisEntry>): String {
        val root = buildJsonObject {
            put("version", AEGIS_VERSION)
            putJsonObject("header") {
                putJsonArray("slots") { }
                putJsonObject("params") {
                    put("nonce", "")
                    put("tag", "")
                }
            }
            putJsonObject("db") {
                put("version", 3)
                putJsonArray("entries") {
                    entries.forEach { entry ->
                        addJsonObject {
                            put("type", "totp")
                            put("uuid", entry.uuid)
                            put("name", entry.name)
                            put("issuer", entry.issuer)
                            if (entry.note.isNotEmpty()) {
                                put("note", entry.note)
                            }
                            putJsonObject("info") {
                                put("secret", entry.secret)
                                put("algo", entry.algorithm)
                                put("digits", entry.digits)
                                put("period", entry.period)
                            }
                        }
                    }
                }
            }
        }
        
        return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), root)
    }
    
    /**
     * 导出为加密的Aegis JSON格式
     * @param entries TOTP条目列表
     * @param password 加密密码
     * @return JSON字符串
     */
    fun exportToEncryptedAegisJson(entries: List<AegisEntry>, password: String): String {
        val secureRandom = SecureRandom()
        
        // 1. 生成随机主密钥（32字节）
        val masterKey = ByteArray(32)
        secureRandom.nextBytes(masterKey)
        
        // 2. 生成salt（32字节）
        val salt = ByteArray(32)
        secureRandom.nextBytes(salt)
        
        // 3. 使用scrypt派生密钥
        val derivedKey = SCrypt.scrypt(
            password.toByteArray(Charsets.UTF_8),
            salt,
            SCRYPT_N,
            SCRYPT_R,
            SCRYPT_P,
            SCRYPT_KEY_LENGTH
        )
        
        // 4. 生成slot的nonce和tag用于加密主密钥
        val slotNonce = ByteArray(12)
        secureRandom.nextBytes(slotNonce)
        
        // 5. 使用派生密钥加密主密钥
        val slotCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val slotSpec = GCMParameterSpec(GCM_TAG_LENGTH, slotNonce)
        val slotSecretKey = SecretKeySpec(derivedKey, "AES")
        slotCipher.init(Cipher.ENCRYPT_MODE, slotSecretKey, slotSpec)
        val encryptedMasterKey = slotCipher.doFinal(masterKey)
        
        // 分离密文和tag
        val slotCipherText = encryptedMasterKey.copyOfRange(0, encryptedMasterKey.size - 16)
        val slotTag = encryptedMasterKey.copyOfRange(encryptedMasterKey.size - 16, encryptedMasterKey.size)
        
        // 6. 创建db JSON内容
        val dbContent = buildJsonObject {
            put("version", 3)
            putJsonArray("entries") {
                entries.forEach { entry ->
                    addJsonObject {
                        put("type", "totp")
                        put("uuid", entry.uuid)
                        put("name", entry.name)
                        put("issuer", entry.issuer)
                        if (entry.note.isNotEmpty()) {
                            put("note", entry.note)
                        }
                        putJsonObject("info") {
                            put("secret", entry.secret)
                            put("algo", entry.algorithm)
                            put("digits", entry.digits)
                            put("period", entry.period)
                        }
                    }
                }
            }
        }
        
        val dbJson = Json.encodeToString(JsonElement.serializer(), dbContent)
        
        // 7. 生成db的nonce用于加密db内容
        val dbNonce = ByteArray(12)
        secureRandom.nextBytes(dbNonce)
        
        // 8. 使用主密钥加密db内容
        val dbCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val dbSpec = GCMParameterSpec(GCM_TAG_LENGTH, dbNonce)
        val dbSecretKey = SecretKeySpec(masterKey, "AES")
        dbCipher.init(Cipher.ENCRYPT_MODE, dbSecretKey, dbSpec)
        val encryptedDb = dbCipher.doFinal(dbJson.toByteArray(Charsets.UTF_8))
        
        // 分离密文和tag
        val dbCipherText = encryptedDb.copyOfRange(0, encryptedDb.size - 16)
        val dbTag = encryptedDb.copyOfRange(encryptedDb.size - 16, encryptedDb.size)
        
        // 9. 构建最终的JSON
        val root = buildJsonObject {
            put("version", AEGIS_VERSION)
            putJsonObject("header") {
                putJsonArray("slots") {
                    addJsonObject {
                        put("type", 1) // Password slot
                        put("uuid", java.util.UUID.randomUUID().toString())
                        put("key", byteArrayToHexString(slotCipherText))
                        put("key_params", buildJsonObject {
                            put("nonce", byteArrayToHexString(slotNonce))
                            put("tag", byteArrayToHexString(slotTag))
                        })
                        put("n", SCRYPT_N)
                        put("r", SCRYPT_R)
                        put("p", SCRYPT_P)
                        put("salt", byteArrayToHexString(salt))
                        put("repaired", false)
                    }
                }
                putJsonObject("params") {
                    put("nonce", byteArrayToHexString(dbNonce))
                    put("tag", byteArrayToHexString(dbTag))
                }
            }
            put("db", Base64.getEncoder().encodeToString(dbCipherText))
        }
        
        return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), root)
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
