package takagi.ru.monica.bitwarden.crypto

import android.util.Log

/**
 * Bitwarden 加密验证测试
 * 
 * 使用已知的测试向量验证加密实现是否正确
 * 
 * 测试向量来源:
 * - Bitwarden 官方 TypeScript clients: web-crypto-function.service.spec.ts
 * - Keyguard 项目
 * 
 * 用法: 在登录失败时调用 runAllTests() 验证加密实现
 */
object BitwardenCryptoTest {
    
    private const val TAG = "BitwardenCryptoTest"
    
    /**
     * 运行所有加密测试
     * 
     * @return 测试报告字符串
     */
    fun runAllTests(): String {
        val results = StringBuilder()
        results.appendLine("========== Bitwarden Crypto Tests ==========")
        
        // 测试 1: PBKDF2-SHA256 基本功能
        results.appendLine()
        results.appendLine("Test 1: PBKDF2-SHA256 Basic")
        try {
            val passed = testPbkdf2Basic()
            results.appendLine("Result: ${if (passed) "PASS ✓" else "FAIL ✗"}")
        } catch (e: Exception) {
            results.appendLine("Result: ERROR - ${e.message}")
        }
        
        // 测试 2: Master Key 派生
        results.appendLine()
        results.appendLine("Test 2: Master Key Derivation")
        try {
            val passed = testMasterKeyDerivation()
            results.appendLine("Result: ${if (passed) "PASS ✓" else "FAIL ✗"}")
        } catch (e: Exception) {
            results.appendLine("Result: ERROR - ${e.message}")
        }
        
        // 测试 3: Password Hash 派生
        results.appendLine()
        results.appendLine("Test 3: Password Hash Derivation")
        try {
            val passed = testPasswordHashDerivation()
            results.appendLine("Result: ${if (passed) "PASS ✓" else "FAIL ✗"}")
        } catch (e: Exception) {
            results.appendLine("Result: ERROR - ${e.message}")
        }
        
        // 测试 4: HKDF 扩展
        results.appendLine()
        results.appendLine("Test 4: HKDF Expansion")
        try {
            val passed = testHkdfExpand()
            results.appendLine("Result: ${if (passed) "PASS ✓" else "FAIL ✗"}")
        } catch (e: Exception) {
            results.appendLine("Result: ERROR - ${e.message}")
        }
        
        // 测试 5: 完整流程模拟
        results.appendLine()
        results.appendLine("Test 5: Full Login Flow Simulation")
        try {
            val report = testFullLoginFlow()
            results.appendLine(report)
        } catch (e: Exception) {
            results.appendLine("Result: ERROR - ${e.message}")
            e.printStackTrace()
        }
        
        results.appendLine()
        results.appendLine("========== Tests Complete ==========")
        
        val report = results.toString()
        Log.d(TAG, report)
        return report
    }
    
    /**
     * 测试 PBKDF2-SHA256 基本功能
     * 
     * 使用 Bitwarden 官方测试向量 (5000 次迭代)
     * 来自 web-crypto-function.service.spec.ts
     * 
     * 测试向量:
     * - password: "password"
     * - salt: "user@example.com"
     * - iterations: 5000
     * - algorithm: SHA256
     * - 预期输出 (Base64): "pj9prw/OHPleXI6bRdmlaD+saJS4awrMiQsQiDjeu2I="
     */
    private fun testPbkdf2Basic(): Boolean {
        val password = "password"
        val email = "user@example.com"
        val iterations = 5000
        val expectedBase64 = "pj9prw/OHPleXI6bRdmlaD+saJS4awrMiQsQiDjeu2I="
        
        // 派生 Master Key (使用 PBKDF2)
        // 注意: Bitwarden 的 master key 派生使用 password 作为输入, email 作为 salt
        val masterKey = BitwardenCrypto.deriveMasterKeyPbkdf2(
            password = password,
            salt = email.lowercase(java.util.Locale.ENGLISH),
            iterations = iterations
        )
        
        // 转换为 Base64
        val resultBase64 = android.util.Base64.encodeToString(masterKey, android.util.Base64.NO_WRAP)
        
        Log.d(TAG, "PBKDF2 test:")
        Log.d(TAG, "  Password: $password")
        Log.d(TAG, "  Salt: $email")
        Log.d(TAG, "  Iterations: $iterations")
        Log.d(TAG, "  Expected: $expectedBase64")
        Log.d(TAG, "  Got:      $resultBase64")
        Log.d(TAG, "  Match: ${expectedBase64 == resultBase64}")
        
        return expectedBase64 == resultBase64
    }
    
    /**
     * 测试 Master Key 派生
     */
    private fun testMasterKeyDerivation(): Boolean {
        val password = "testpassword"
        val email = "test@example.com"
        val iterations = 600000
        
        // 派生 Master Key (PBKDF2)
        val masterKey = BitwardenCrypto.deriveMasterKeyPbkdf2(
            password = password,
            salt = email.lowercase(java.util.Locale.ENGLISH),
            iterations = iterations
        )
        
        Log.d(TAG, "Master Key length: ${masterKey.size}")
        Log.d(TAG, "Master Key (hex first 8): ${masterKey.take(8).joinToString("") { "%02x".format(it) }}")
        
        // 验证输出长度
        return masterKey.size == 32
    }
    
    /**
     * 测试 Password Hash 派生
     */
    private fun testPasswordHashDerivation(): Boolean {
        val password = "testpassword"
        
        // 创建一个假的 Master Key (32 字节)
        val masterKey = ByteArray(32) { it.toByte() }
        
        // 派生 Password Hash
        val passwordHash = BitwardenCrypto.deriveMasterPasswordHash(masterKey, password)
        
        Log.d(TAG, "Password Hash length: ${passwordHash.length}")
        Log.d(TAG, "Password Hash: $passwordHash")
        
        // Base64 编码的 32 字节应该是 44 字符 (带填充) 或更少 (不带填充)
        // 32 bytes → 43-44 Base64 chars
        return passwordHash.length in 40..50
    }
    
    /**
     * 测试 HKDF 扩展
     */
    private fun testHkdfExpand(): Boolean {
        // 创建一个假的 Master Key
        val masterKey = ByteArray(32) { it.toByte() }
        
        // 使用 HKDF 扩展
        val stretchedKey = BitwardenCrypto.stretchMasterKey(masterKey)
        
        Log.d(TAG, "Stretched encKey length: ${stretchedKey.encKey.size}")
        Log.d(TAG, "Stretched macKey length: ${stretchedKey.macKey.size}")
        
        // 验证输出长度
        return stretchedKey.encKey.size == 32 && stretchedKey.macKey.size == 32
    }
    
    /**
     * 测试完整登录流程
     * 
     * 模拟整个登录过程，打印所有中间值以便调试
     */
    private fun testFullLoginFlow(): String {
        val results = StringBuilder()
        
        val email = "test@bitwarden.com"
        val password = "testpassword123"
        val kdfIterations = 600000
        
        results.appendLine("Input:")
        results.appendLine("  Email: $email")
        results.appendLine("  Password: $password")
        results.appendLine("  KDF: PBKDF2-SHA256")
        results.appendLine("  Iterations: $kdfIterations")
        
        // 步骤 1: 小写化邮箱
        val emailLower = email.lowercase(java.util.Locale.ENGLISH)
        results.appendLine()
        results.appendLine("Step 1: Lowercase email")
        results.appendLine("  Result: $emailLower")
        
        // 步骤 2: 派生 Master Key
        val masterKey = BitwardenCrypto.deriveMasterKeyPbkdf2(
            password = password,
            salt = emailLower,
            iterations = kdfIterations
        )
        results.appendLine()
        results.appendLine("Step 2: Derive Master Key")
        results.appendLine("  Master Key (hex first 16): ${masterKey.take(16).joinToString("") { "%02x".format(it) }}")
        results.appendLine("  Master Key size: ${masterKey.size} bytes")
        
        // 步骤 3: 派生 Password Hash (发送到服务器)
        val passwordHash = BitwardenCrypto.deriveMasterPasswordHash(masterKey, password)
        results.appendLine()
        results.appendLine("Step 3: Derive Password Hash")
        results.appendLine("  Password Hash: $passwordHash")
        results.appendLine("  Password Hash length: ${passwordHash.length}")
        
        // 步骤 4: 生成 Auth-Email header
        val authEmail = android.util.Base64.encodeToString(
            email.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        ).replace('+', '-').replace('/', '_').replace("=", "")
        results.appendLine()
        results.appendLine("Step 4: Generate Auth-Email header")
        results.appendLine("  Auth-Email: $authEmail")
        
        // 步骤 5: 扩展 Master Key
        val stretchedKey = BitwardenCrypto.stretchMasterKey(masterKey)
        results.appendLine()
        results.appendLine("Step 5: Stretch Master Key (HKDF)")
        results.appendLine("  encKey (hex first 8): ${stretchedKey.encKey.take(8).joinToString("") { "%02x".format(it) }}")
        results.appendLine("  macKey (hex first 8): ${stretchedKey.macKey.take(8).joinToString("") { "%02x".format(it) }}")
        
        results.appendLine()
        results.appendLine("Login request would include:")
        results.appendLine("  username: $email")
        results.appendLine("  password: $passwordHash")
        results.appendLine("  Auth-Email header: $authEmail")
        
        return results.toString()
    }
    
    /**
     * 运行并打印测试结果
     * 
     * 方便从 UI 或 adb 调用
     */
    fun runAndPrint() {
        val report = runAllTests()
        println(report)
    }
}
