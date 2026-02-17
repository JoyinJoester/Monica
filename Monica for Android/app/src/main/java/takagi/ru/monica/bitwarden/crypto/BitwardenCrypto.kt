package takagi.ru.monica.bitwarden.crypto

import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bitwarden 加密核心模块
 * 
 * 实现 Bitwarden 的加密/解密逻辑，参考 Keyguard 项目
 * 
 * 密钥派生流程:
 * 1. 使用 PBKDF2-SHA256 或 Argon2id 从主密码派生 Master Key
 * 2. 使用 HKDF-SHA256 从 Master Key 派生 Master Password Hash (用于认证)
 * 3. 从 Master Key 派生 Stretched Master Key (64字节 = 32字节 encKey + 32字节 macKey)
 * 4. 使用 Stretched Master Key 解密 Protected Symmetric Key
 * 5. 使用 Symmetric Key 解密/加密 Cipher 数据
 * 
 * CipherString 格式:
 * - Type 0: AES-256-CBC (不含 MAC)
 * - Type 2: AES-256-CBC + HMAC-SHA256 (标准格式)
 * 
 * 安全注意:
 * - 所有密钥必须在使用后清零
 * - 内存中的敏感数据生命周期要尽量短
 */
object BitwardenCrypto {
    
    private const val TAG = "BitwardenCrypto"
    private const val MAX_CIPHER_STRING_LENGTH = 1024 * 1024
    private const val MAX_BASE64_PART_LENGTH = 1024 * 1024
    
    // 加密类型常量
    const val CIPHER_TYPE_AES_CBC = 0
    const val CIPHER_TYPE_AES_CBC_HMAC = 2
    
    // 密钥长度
    private const val AES_KEY_SIZE = 32   // 256 bits
    private const val MAC_KEY_SIZE = 32   // 256 bits
    private const val IV_SIZE = 16        // 128 bits
    private const val SEND_KEY_MATERIAL_SIZE = 16
    
    /**
     * 对称加密密钥 - 包含加密密钥和 MAC 密钥
     */
    data class SymmetricCryptoKey(
        val encKey: ByteArray,  // 32 字节 AES-256 密钥
        val macKey: ByteArray   // 32 字节 HMAC-SHA256 密钥
    ) {
        init {
            require(encKey.size == AES_KEY_SIZE) { "encKey must be 32 bytes" }
            require(macKey.size == MAC_KEY_SIZE) { "macKey must be 32 bytes" }
        }
        
        /**
         * 清除密钥材料
         */
        fun clear() {
            encKey.fill(0)
            macKey.fill(0)
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SymmetricCryptoKey) return false
            return encKey.contentEquals(other.encKey) && macKey.contentEquals(other.macKey)
        }
        
        override fun hashCode(): Int {
            var result = encKey.contentHashCode()
            result = 31 * result + macKey.contentHashCode()
            return result
        }
    }
    
    /**
     * 解析后的 CipherString
     */
    data class ParsedCipherString(
        val type: Int,
        val iv: ByteArray,
        val data: ByteArray,
        val mac: ByteArray?
    )
    
    // ========== 密钥派生 ==========
    
    /**
     * 使用 PBKDF2-SHA256 派生 Master Key
     * 
     * 参考 keyguard: 使用 BouncyCastle 直接处理字节数组
     * 
     * @param password 用户主密码
     * @param salt 盐值 (通常是小写的 email)
     * @param iterations 迭代次数 (默认 600000)
     * @return 32 字节的 Master Key
     */
    fun deriveMasterKeyPbkdf2(
        password: String,
        salt: String,
        iterations: Int = 600000
    ): ByteArray {
        // 使用 BouncyCastle 的 PKCS5S2ParametersGenerator
        // 与 keyguard 保持一致，直接使用字节数组
        // 注意: salt 应该已经被调用者小写化，这里不再重复处理
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)
        return pbkdf2Sha256(
            seed = passwordBytes,
            salt = saltBytes,
            iterations = iterations,
            length = 32
        )
    }
    
    /**
     * 使用 Argon2id 派生 Master Key
     * 
     * 参考 keyguard: salt 需要先做 SHA256 哈希
     * 
     * @param password 用户主密码
     * @param salt 盐值
     * @param iterations 迭代次数 (默认 3)
     * @param memory 内存大小 MB (默认 64)
     * @param parallelism 并行度 (默认 4)
     * @return 32 字节的 Master Key
     */
    fun deriveMasterKeyArgon2(
        password: String,
        salt: String,
        iterations: Int = 3,
        memory: Int = 64,
        parallelism: Int = 4
    ): ByteArray {
        // 使用 BouncyCastle 的 Argon2 实现
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        // 注意: salt 应该已经被调用者小写化，这里不再重复处理
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)
        
        // keyguard: val saltHash = hashSha256(salt)
        val saltHash = MessageDigest.getInstance("SHA-256").digest(saltBytes)
        
        val params = org.bouncycastle.crypto.params.Argon2Parameters.Builder(
            org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id
        )
            .withSalt(saltHash)  // 使用 SHA256 哈希后的 salt
            .withIterations(iterations)
            .withMemoryAsKB(memory * 1024)
            .withParallelism(parallelism)
            .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
            .build()
        
        val generator = org.bouncycastle.crypto.generators.Argon2BytesGenerator()
        generator.init(params)
        
        val hash = ByteArray(32)
        generator.generateBytes(passwordBytes, hash)
        
        return hash
    }
    
    /**
     * 从 Master Key 派生 Master Password Hash (用于服务器认证)
     * 
     * 使用 PBKDF2-SHA256，1 次迭代
     * 参考 keyguard 实现：seed = masterKey (原始字节), salt = password (字节)
     * 返回 标准 Base64 编码的结果 (不是 URL-safe!)
     */
    fun deriveMasterPasswordHash(
        masterKey: ByteArray,
        password: String
    ): String {
        // 使用 BouncyCastle 的 PKCS5S2ParametersGenerator
        // 这是正确的实现方式，直接使用原始字节作为 seed 和 salt
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val hash = pbkdf2Sha256(
            seed = masterKey,
            salt = passwordBytes,
            iterations = 1,
            length = 32
        )
        // keyguard 使用标准 Base64，不是 URL-safe
        val result = Base64.encodeToString(hash, Base64.NO_WRAP)
        
        // 调试日志 - 用于验证密钥派生是否正确
        Log.d(TAG, "deriveMasterPasswordHash: masterKey.size=${masterKey.size}, hash.size=${hash.size}, result.length=${result.length}")
        Log.d(TAG, "deriveMasterPasswordHash: masterKey(hex first 8)=${masterKey.take(8).joinToString("") { "%02x".format(it) }}")
        Log.d(TAG, "deriveMasterPasswordHash: hash(hex first 8)=${hash.take(8).joinToString("") { "%02x".format(it) }}")
        
        return result
    }
    
    /**
     * 使用 BouncyCastle 实现的 PBKDF2-SHA256
     * 直接接受原始字节数组，不需要转换为 char[]
     */
    private fun pbkdf2Sha256(
        seed: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int
    ): ByteArray {
        val generator = org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        generator.init(seed, salt, iterations)
        val params = generator.generateDerivedMacParameters(length * 8)
        return (params as org.bouncycastle.crypto.params.KeyParameter).key
    }
    
    /**
     * 使用标准 Android SecretKeyFactory 实现的 PBKDF2-SHA256
     * 用于对比验证 BouncyCastle 实现
     */
    fun pbkdf2Sha256Standard(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int
    ): ByteArray {
        // 将 ByteArray 转换为 CharArray（这是标准 API 的要求）
        val passwordChars = password.map { (it.toInt() and 0xFF).toChar() }.toCharArray()
        val spec = javax.crypto.spec.PBEKeySpec(passwordChars, salt, iterations, length * 8)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        return key.encoded
    }
    
    /**
     * 对比测试两种 PBKDF2 实现
     */
    fun comparePbkdf2Implementations(
        password: String,
        salt: String,
        iterations: Int
    ): String {
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)
        
        val bcResult = pbkdf2Sha256(passwordBytes, saltBytes, iterations, 32)
        val stdResult = pbkdf2Sha256Standard(passwordBytes, saltBytes, iterations, 32)
        
        val bcHex = bcResult.joinToString("") { "%02x".format(it) }
        val stdHex = stdResult.joinToString("") { "%02x".format(it) }
        
        return """
            BouncyCastle: $bcHex
            Standard API: $stdHex
            Match: ${bcHex == stdHex}
        """.trimIndent()
    }
    
    /**
     * 从 Master Key 扩展为 Stretched Key (64 字节)
     * 使用 HKDF-SHA256
     */
    fun stretchMasterKey(masterKey: ByteArray): SymmetricCryptoKey {
        // HKDF-Expand with info = "enc" for encryption key
        val encKey = hkdfExpand(masterKey, "enc".toByteArray(), 32)
        // HKDF-Expand with info = "mac" for MAC key
        val macKey = hkdfExpand(masterKey, "mac".toByteArray(), 32)
        
        return SymmetricCryptoKey(encKey, macKey)
    }

    /**
     * 生成 Bitwarden Send 的原始密钥材料（16字节）
     */
    fun generateSendKeyMaterial(): ByteArray {
        val bytes = ByteArray(SEND_KEY_MATERIAL_SIZE)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /**
     * 根据 Send 密钥材料派生 Send 对称密钥（enc+mac）
     */
    fun deriveSendKey(keyMaterial: ByteArray): SymmetricCryptoKey {
        require(keyMaterial.size == SEND_KEY_MATERIAL_SIZE) {
            "Send key material must be 16 bytes"
        }

        val fullKey = hkdf(
            seed = keyMaterial,
            salt = "bitwarden-send".toByteArray(StandardCharsets.UTF_8),
            info = "send".toByteArray(StandardCharsets.UTF_8),
            length = 64
        )
        val encKey = fullKey.copyOfRange(0, 32)
        val macKey = fullKey.copyOfRange(32, 64)
        return SymmetricCryptoKey(encKey, macKey)
    }

    /**
     * Send 访问密码哈希（PBKDF2-SHA256, 100000 iterations）
     */
    fun hashSendPassword(password: String, keyMaterial: ByteArray): String {
        val hash = pbkdf2Sha256(
            seed = password.toByteArray(StandardCharsets.UTF_8),
            salt = keyMaterial,
            iterations = 100_000,
            length = 32
        )
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * HKDF-Expand (SHA-256)
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        
        val hashLen = 32  // SHA-256 输出长度
        val n = (length + hashLen - 1) / hashLen
        
        val output = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        
        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            
            val copyLen = minOf(hashLen, length - pos)
            System.arraycopy(t, 0, output, pos, copyLen)
            pos += copyLen
        }
        
        return output
    }

    private fun hkdf(seed: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(seed)
        return hkdfExpand(prk = prk, info = info, length = length)
    }
    
    // ========== CipherString 解析 ==========
    
    /**
     * 解析 CipherString
     * 格式: type.iv|data|mac 或 type.iv|data
     */
    fun parseCipherString(cipherString: String): ParsedCipherString {
        require(cipherString.isNotBlank()) { "Cipher string is blank" }
        require(cipherString.length <= MAX_CIPHER_STRING_LENGTH) {
            "Cipher string too large: ${cipherString.length}"
        }
        val dotIndex = cipherString.indexOf('.')
        if (dotIndex == -1) {
            // 假设类型 0
            return parseCipherStringParts(0, cipherString)
        }
        
        val type = cipherString.substring(0, dotIndex).toIntOrNull() 
            ?: throw IllegalArgumentException("Invalid cipher type")
        val rest = cipherString.substring(dotIndex + 1)
        
        return parseCipherStringParts(type, rest)
    }
    
    private fun parseCipherStringParts(type: Int, data: String): ParsedCipherString {
        val parts = data.split('|')
        
        return when (type) {
            CIPHER_TYPE_AES_CBC -> {
                require(parts.size >= 2) { "AES-CBC requires at least iv|data" }
                ParsedCipherString(
                    type = type,
                    iv = decodeBase64Part(parts[0], "iv", type),
                    data = decodeBase64Part(parts[1], "data", type),
                    mac = null
                )
            }
            CIPHER_TYPE_AES_CBC_HMAC -> {
                require(parts.size >= 3) { "AES-CBC-HMAC requires iv|data|mac" }
                ParsedCipherString(
                    type = type,
                    iv = decodeBase64Part(parts[0], "iv", type),
                    data = decodeBase64Part(parts[1], "data", type),
                    mac = decodeBase64Part(parts[2], "mac", type)
                )
            }
            else -> throw IllegalArgumentException("Unsupported cipher type: $type")
        }
    }

    private fun decodeBase64Part(rawPart: String, partName: String, type: Int): ByteArray {
        val part = rawPart.trim()
        require(part.isNotEmpty()) { "Empty cipher part: $partName, type=$type" }
        require(part.length <= MAX_BASE64_PART_LENGTH) {
            "Cipher part too large: $partName, len=${part.length}, type=$type"
        }

        val normalized = part
            .replace('-', '+')
            .replace('_', '/')
        val padding = (4 - normalized.length % 4) % 4
        val padded = if (padding == 0) normalized else normalized + "=".repeat(padding)

        return try {
            org.bouncycastle.util.encoders.Base64.decode(padded)
        } catch (e: Throwable) {
            throw IllegalArgumentException(
                "Invalid base64 part: $partName, len=${part.length}, type=$type",
                e
            )
        }
    }
    
    // ========== 解密 ==========
    
    /**
     * 解密 CipherString
     * 
     * @param cipherString 加密字符串
     * @param key 对称密钥
     * @return 解密后的明文
     */
    fun decrypt(cipherString: String, key: SymmetricCryptoKey): ByteArray {
        val parsed = parseCipherString(cipherString)
        return decrypt(parsed, key)
    }
    
    /**
     * 解密 CipherString (字符串结果)
     */
    fun decryptToString(cipherString: String, key: SymmetricCryptoKey): String {
        val decrypted = decrypt(cipherString, key)
        return String(decrypted, StandardCharsets.UTF_8)
    }
    
    /**
     * 解密已解析的 CipherString
     */
    fun decrypt(parsed: ParsedCipherString, key: SymmetricCryptoKey): ByteArray {
        // 验证 MAC (如果有)
        if (parsed.mac != null) {
            val computedMac = computeMac(parsed.iv, parsed.data, key.macKey)
            if (!MessageDigest.isEqual(computedMac, parsed.mac)) {
                throw SecurityException("MAC verification failed")
            }
        }
        
        // AES-256-CBC 解密
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.encKey, "AES"),
            IvParameterSpec(parsed.iv)
        )
        
        return cipher.doFinal(parsed.data)
    }
    
    /**
     * 解密 Protected Symmetric Key (从登录响应中获取)
     * 
     * @param encryptedKey 加密的 protectedKey
     * @param masterKey 扩展后的主密钥
     * @return 解密后的对称密钥
     */
    fun decryptSymmetricKey(
        encryptedKey: String,
        masterKey: SymmetricCryptoKey
    ): SymmetricCryptoKey {
        val decrypted = decrypt(encryptedKey, masterKey)
        
        require(decrypted.size == 64) { 
            "Decrypted key should be 64 bytes, got ${decrypted.size}" 
        }
        
        return SymmetricCryptoKey(
            encKey = decrypted.copyOfRange(0, 32),
            macKey = decrypted.copyOfRange(32, 64)
        )
    }
    
    // ========== 加密 ==========
    
    /**
     * 加密为 CipherString (Type 2: AES-CBC-HMAC)
     * 
     * @param plaintext 明文
     * @param key 对称密钥
     * @return CipherString 格式的密文
     */
    fun encrypt(plaintext: ByteArray, key: SymmetricCryptoKey): String {
        // 生成随机 IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        // AES-256-CBC 加密
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.encKey, "AES"),
            IvParameterSpec(iv)
        )
        val encrypted = cipher.doFinal(plaintext)
        
        // 计算 MAC
        val mac = computeMac(iv, encrypted, key.macKey)
        
        // 构建 CipherString
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val macBase64 = Base64.encodeToString(mac, Base64.NO_WRAP)
        
        return "$CIPHER_TYPE_AES_CBC_HMAC.$ivBase64|$dataBase64|$macBase64"
    }
    
    /**
     * 加密字符串
     */
    fun encryptString(plaintext: String, key: SymmetricCryptoKey): String {
        return encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), key)
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 计算 HMAC-SHA256 MAC
     */
    private fun computeMac(iv: ByteArray, data: ByteArray, macKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(iv)
        mac.update(data)
        return mac.doFinal()
    }
    
    /**
     * 安全清除字节数组
     */
    fun clearBytes(vararg arrays: ByteArray) {
        arrays.forEach { it.fill(0) }
    }
}
