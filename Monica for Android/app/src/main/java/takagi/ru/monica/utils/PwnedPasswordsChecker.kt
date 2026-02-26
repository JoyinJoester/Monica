package takagi.ru.monica.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours

/**
 * Have I Been Pwned API 密码检查器
 * 使用 k-Anonymity 模型保护隐私，不会发送完整密码到服务器
 * 
 * API 文档: https://haveibeenpwned.com/API/v3#PwnedPasswords
 */
object PwnedPasswordsChecker {
    private const val TAG = "PwnedPasswordsChecker"
    private const val API_URL = "https://api.pwnedpasswords.com/range/"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_PARALLEL_REQUESTS = 6
    private val CACHE_TTL_MS = 24.hours.inWholeMilliseconds

    private val rangeCacheMutex = Mutex()
    private val rangeCache = mutableMapOf<String, CachedRangeResult>()
    
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

            // 3. 获取范围查询结果（带缓存）
            val rangeResult = getRangeResult(prefix)
            val count = rangeResult[suffix] ?: 0
            if (count > 0) {
                Log.d(TAG, "Password found in breach database: $count times")
            } else {
                Log.d(TAG, "Password not found in breach database")
            }
            return@withContext count
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
        val nonBlankPasswords = passwords.filter { it.isNotBlank() }
        if (nonBlankPasswords.isEmpty()) return@withContext emptyMap()

        val prepared = nonBlankPasswords
            .distinct()
            .associateWith { password ->
                val hash = sha1(password).uppercase()
                hash.substring(0, 5) to hash.substring(5)
            }

        val prefixes = prepared.values.map { it.first }.toSet()
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
        var completed = 0

        val prefixResults: Map<String, Map<String, Int>?> = coroutineScope {
            prefixes.map { prefix ->
                async {
                    semaphore.withPermit {
                        val result = runCatching { getRangeResult(prefix) }
                            .onFailure { error ->
                                Log.w(TAG, "Prefix query failed for $prefix: ${error.message}")
                            }
                            .getOrNull()
                        completed++
                        onProgress(completed, prefixes.size)
                        prefix to result
                    }
                }
            }.awaitAll().toMap()
        }

        return@withContext prepared.mapValues { (_, pair) ->
            val (prefix, suffix) = pair
            val prefixMap = prefixResults[prefix] ?: return@mapValues -1
            prefixMap[suffix] ?: 0
        }
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

    private suspend fun getRangeResult(prefix: String): Map<String, Int> {
        val now = System.currentTimeMillis()
        val cached = rangeCacheMutex.withLock {
            rangeCache[prefix]
        }
        if (cached != null && now - cached.fetchedAtMs <= CACHE_TTL_MS) {
            return cached.suffixCounts
        }

        val fetched = fetchRangeResult(prefix)
        rangeCacheMutex.withLock {
            rangeCache[prefix] = CachedRangeResult(
                fetchedAtMs = now,
                suffixCounts = fetched
            )
        }
        return fetched
    }

    private suspend fun fetchRangeResult(prefix: String): Map<String, Int> {
        val maxAttempts = 3
        var lastError: Throwable? = null

        repeat(maxAttempts) { attempt ->
            val url = URL(API_URL + prefix)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Monica-Password-Manager")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return connection.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence()
                            .mapNotNull { line ->
                                val parts = line.trim().split(":")
                                if (parts.size != 2) return@mapNotNull null
                                val suffix = parts[0].uppercase()
                                val count = parts[1].toIntOrNull() ?: 0
                                suffix to count
                            }
                            .toMap()
                    }
                }

                val error = IllegalStateException("API request failed with code=$responseCode for prefix=$prefix")
                lastError = error
                Log.w(TAG, "Attempt ${attempt + 1}/$maxAttempts failed: ${error.message}")
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Attempt ${attempt + 1}/$maxAttempts failed for prefix=$prefix: ${e.message}")
            } finally {
                connection.disconnect()
            }

            if (attempt < maxAttempts - 1) {
                delay((attempt + 1) * 400L)
            }
        }

        throw lastError ?: IllegalStateException("Failed to fetch pwned range for prefix=$prefix")
    }

    private data class CachedRangeResult(
        val fetchedAtMs: Long,
        val suffixCounts: Map<String, Int>
    )
}
