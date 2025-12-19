package takagi.ru.monica.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Have I Been Pwned API 密码检查器
 * 使用 k-Anonymity 模型保护隐私，不会发送完整密码到服务器
 * 
 * API 文档: https://haveibeenpwned.com/API/v3#PwnedPasswords
 */
object PwnedPasswordsChecker {
    private const val TAG = "PwnedPasswordsChecker"
    private const val API_URL = "https://api.pwnedpasswords.com/range/"
    
    /**
     * 检查密码是否泄露
     * @param password 要检查的密码
     * @return 泄露次数，0表示未泄露，-1表示检查失败
     */
    suspend fun checkPassword(password: String): Int = withContext(Dispatchers.IO) {
        try {
            // 1. 计算密码的 SHA-1 哈希值
            val hash = sha1(password).uppercase()
            
            // 2. 取前5位作为API请求前缀
            val prefix = hash.substring(0, 5)
            val suffix = hash.substring(5)
            
            // 3. 请求API获取所有前5位匹配的哈希后缀
            val url = URL(API_URL + prefix)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Monica-Password-Manager")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "API request failed with code: $responseCode")
                return@withContext -1
            }
            
            // 4. 解析响应，查找匹配的后缀
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val lines = response.split("\n")
            
            for (line in lines) {
                val parts = line.trim().split(":")
                if (parts.size == 2) {
                    val hashSuffix = parts[0]
                    val count = parts[1].toIntOrNull() ?: 0
                    
                    if (hashSuffix.equals(suffix, ignoreCase = true)) {
                        Log.d(TAG, "Password found in breach database: $count times")
                        return@withContext count
                    }
                }
            }
            
            // 未找到，密码安全
            Log.d(TAG, "Password not found in breach database")
            return@withContext 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking password: ${e.message}", e)
            return@withContext -1
        }
    }
    
    /**
     * 批量检查多个密码
     * @param passwords 密码列表
     * @param onProgress 进度回调 (当前索引, 总数)
     * @return Map<密码, 泄露次数>
     */
    suspend fun checkPasswordsBatch(
        passwords: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Int>()
        
        passwords.forEachIndexed { index, password ->
            if (password.isNotBlank()) {
                val count = checkPassword(password)
                results[password] = count
                onProgress(index + 1, passwords.size)
                
                // 添加延迟避免API限流 (建议间隔至少1.5秒)
                if (index < passwords.size - 1) {
                    kotlinx.coroutines.delay(1600)
                }
            }
        }
        
        return@withContext results
    }
    
    /**
     * 计算字符串的 SHA-1 哈希值
     */
    private fun sha1(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
