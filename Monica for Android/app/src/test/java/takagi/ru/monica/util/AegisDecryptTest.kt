package takagi.ru.monica.util

import android.util.Log
import com.lambdaworks.crypto.SCrypt
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Aegis解密测试类
 * 用于测试和验证Aegis加密文件的解密流程
 */
class AegisDecryptTest {
    
    companion object {
        private const val TAG = "AegisDecryptTest"
        private const val SCRYPT_N = 32768 // CPU/内存成本参数
        private const val SCRYPT_R = 8     // 块大小
        private const val SCRYPT_P = 1     // 并行化参数
        private const val SCRYPT_KEY_LENGTH = 32 // 密钥长度（字节）
        private const val GCM_TAG_LENGTH = 128 // GCM标签长度（位）
    }
    
    /**
     * 测试Aegis解密流程
     */
    fun testAegisDecrypt() {
        try {
            // 读取Aegis导出文件
            val jsonContent = """
            {
                "version": 1,
                "header": {
                    "slots": [
                        {
                            "type": 1,
                            "uuid": "8ba4d26d-2e6e-4f18-8d87-127812e48335",
                            "key": "71b12d02fd5ac5a814ce53eb904ad0a729ab9fe15f499d2b789aca09c0bab797",
                            "key_params": {
                                "nonce": "e4494339d17c0d9f33766d8f",
                                "tag": "5b62b2ad4c5d88819e37fb1755bec7ab"
                            },
                            "n": 32768,
                            "r": 8,
                            "p": 1,
                            "salt": "1c067320a3efbbab13b7fed54c883ae07755ef279f66d6fa5644d0df716046da",
                            "repaired": true,
                            "is_backup": false
                        }
                    ],
                    "params": {
                        "nonce": "3b76850a6cbd48d257a2c92c",
                        "tag": "9a6105cc61139809670eb62e78e8bc53"
                    }
                },
                "db": "7Ps1nGeLvuxGmu/Nk80UQj7nIAFEaDvQsD4tCsI6+EJye1zDZn7DCYmVVUbfLNau19t+C4AtnU6XMFKuhokGjFhWlJ+MISOGaR1Qadzs5lmQOCxa7Dcxlape7vFZ9Z6n4uAXCpbXqrS2e55gC8YHRgOmD5uLp8CBaQvgEf0n5SC3Ejm1rL5t/P0UYzof+EAtXYSmQ+iGGEyC3MGk+FjEsdlHFTntqhMdqw/6hNmKj3KfaIlXuAhy7HALqwWQJO7ud5KXvpM4hEtDITs6Obry9QjIAKRmrzctjIyX45AF+rgvxK5H8ppovi9kXhCcwMFa0JjVg8ZEVN/M45OjSLqZrbmvFZKoPWIXtmd8ufLeMHD3Hec/EhX4WpDUuCEPlU7XqPkHsGJ533ZQVe5duqTH7Lgn365qZJQPoqKH6m6cCzhx8RhaRvTr4w+r87/YNXl/a/Pjww2e5UB7SlOnzrKIWBq42yBLJ2deBFZrzlLFLjPNuZ0xL5Y87Rw8ylW83vpTR2h9PrpOjvRpxGbaGWL504rVo6VMKgKO0HAfD4Ggr45eyE7LVApYlXcmuGD31NrjvZyfLw5VSlym/vIwUl03hOyQ6AkpL0fUIYwuTlXW60KJF0CsXBVKy8TCcHjLjiPTgHvXyvVKAB6jhUYEbPS3+zb7LbE3GP+l04iEzAzotUjU6SHlTqRhOHZcEHiZ"
            }
            """.trimIndent()
            
            // 解析JSON
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonContent).jsonObject
            
            // 获取header信息
            val header = root["header"]?.jsonObject
                ?: throw Exception("无效的Aegis文件格式：缺少header")
            
            // 获取slots信息
            val slots = header["slots"] as? JsonArray
                ?: throw Exception("无效的Aegis文件格式：缺少slots")
            
            if (slots.isEmpty()) {
                throw Exception("无效的Aegis文件格式：slots为空")
            }
            
            // 获取第一个slot
            val slot = slots[0] as JsonObject
            val slotType = slot["type"]?.jsonPrimitive?.content?.toIntOrNull()
            if (slotType != 1) {
                throw Exception("不支持的slot类型: $slotType")
            }
            
            val salt = slot["salt"]?.jsonPrimitive?.content
                ?: throw Exception("无效的Aegis文件格式：缺少salt")
            
            val key = slot["key"]?.jsonPrimitive?.content
                ?: throw Exception("无效的Aegis文件格式：缺少key")
            
            val keyParams = slot["key_params"]?.jsonObject
                ?: throw Exception("无效的Aegis文件格式：缺少key_params")
            
            val nonce = keyParams["nonce"]?.jsonPrimitive?.content
                ?: throw Exception("无效的Aegis文件格式：缺少nonce")
            
            val tag = keyParams["tag"]?.jsonPrimitive?.content
                ?: throw Exception("无效的Aegis文件格式：缺少tag")
            
            // 获取加密的db数据
            val encryptedDb = root["db"]?.jsonPrimitive?.content
                ?: throw Exception("无效的Aegis文件格式：缺少db")
            
            Log.d(TAG, "开始解密流程...")
            Log.d(TAG, "Salt: $salt")
            Log.d(TAG, "Key: $key")
            Log.d(TAG, "Key nonce: $nonce")
            Log.d(TAG, "Key tag: $tag")
            Log.d(TAG, "Encrypted DB: ${encryptedDb.substring(0, Math.min(50, encryptedDb.length))}...")
            
            // 用户密码
            val password = "111111" // 使用测试密码
            
            // 1. 使用scrypt算法派生密钥
            Log.d(TAG, "步骤1: 使用scrypt算法派生密钥...")
            val saltBytes = hexStringToByteArray(salt)
            Log.d(TAG, "Salt bytes长度: ${saltBytes.size}")
            
            val derivedKey = SCrypt.scrypt(
                password.toByteArray(Charsets.UTF_8),
                saltBytes,
                SCRYPT_N,
                SCRYPT_R,
                SCRYPT_P,
                SCRYPT_KEY_LENGTH
            )
            Log.d(TAG, "派生密钥长度: ${derivedKey.size}")
            
            // 2. 使用派生的密钥解密主密钥
            Log.d(TAG, "步骤2: 使用派生的密钥解密主密钥...")
            val keyBytes = hexStringToByteArray(key)
            val nonceBytes = hexStringToByteArray(nonce)
            
            Log.d(TAG, "Key bytes长度: ${keyBytes.size}")
            Log.d(TAG, "Nonce bytes长度: ${nonceBytes.size}")
            
            // 使用AES-GCM解密
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonceBytes)
            val secretKey = SecretKeySpec(derivedKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedKey = cipher.doFinal(keyBytes)
            Log.d(TAG, "解密后的主密钥长度: ${decryptedKey.size}")
            
            // 3. 使用主密钥解密db字段
            Log.d(TAG, "步骤3: 使用主密钥解密db字段...")
            val dbParams = header["params"]?.jsonObject
            val dbNonce = dbParams?.get("nonce")?.jsonPrimitive?.content ?: nonce
            val dbTag = dbParams?.get("tag")?.jsonPrimitive?.content ?: tag
            val dbNonceBytes = hexStringToByteArray(dbNonce)
            
            Log.d(TAG, "DB nonce: $dbNonce")
            Log.d(TAG, "DB tag: $dbTag")
            Log.d(TAG, "DB nonce bytes长度: ${dbNonceBytes.size}")
            
            // 注意：这里需要使用Base64解码而不是十六进制解码
            val encryptedDbBytes = Base64.getDecoder().decode(encryptedDb)
            Log.d(TAG, "加密DB bytes长度: ${encryptedDbBytes.size}")
            
            // 使用AES-GCM解密db字段
            val dbCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val dbSpec = GCMParameterSpec(GCM_TAG_LENGTH, dbNonceBytes)
            val dbSecretKey = SecretKeySpec(decryptedKey, "AES")
            dbCipher.init(Cipher.DECRYPT_MODE, dbSecretKey, dbSpec)
            
            val decryptedDbData = dbCipher.doFinal(encryptedDbBytes)
            Log.d(TAG, "解密后的DB数据长度: ${decryptedDbData.size}")
            
            // 4. 解析解密后的JSON
            Log.d(TAG, "步骤4: 解析解密后的JSON...")
            val decryptedContent = String(decryptedDbData, Charsets.UTF_8)
            Log.d(TAG, "解密后的内容预览: ${decryptedContent.substring(0, Math.min(100, decryptedContent.length))}...")
            
            Log.d(TAG, "Aegis解密测试完成")
        } catch (e: Exception) {
            Log.e(TAG, "Aegis解密测试失败", e)
        }
    }
    
    /**
     * 将十六进制字符串转换为字节数组
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val hex = hexString.uppercase()
        val bytes = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return bytes
    }
}