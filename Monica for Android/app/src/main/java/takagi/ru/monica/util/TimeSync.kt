package takagi.ru.monica.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 自动时间同步工具
 * 通过HTTP HEAD请求获取服务器时间，计算与本地时间的偏移
 */
object TimeSync {
    
    private const val TAG = "TimeSync"
    
    // 可靠的时间服务器列表
    private val TIME_SERVERS = listOf(
        "https://www.google.com",
        "https://www.cloudflare.com",
        "https://www.microsoft.com",
        "https://www.apple.com"
    )
    
    /**
     * 自动检测并计算时间偏移
     * @return 时间偏移（秒），正值表示本地时间慢于服务器时间
     */
    suspend fun detectTimeOffset(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 尝试多个服务器，取第一个成功的
            for (serverUrl in TIME_SERVERS) {
                try {
                    val offset = getTimeOffsetFromServer(serverUrl)
                    if (offset != null) {
                        Log.d(TAG, "Time offset detected: ${offset}s from $serverUrl")
                        return@withContext Result.success(offset)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get time from $serverUrl: ${e.message}")
                    continue
                }
            }
            
            Result.failure(Exception("无法连接到任何时间服务器"))
        } catch (e: Exception) {
            Log.e(TAG, "Time sync error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从指定服务器获取时间偏移
     * @param serverUrl 服务器URL
     * @return 时间偏移（秒），null表示失败
     */
    private fun getTimeOffsetFromServer(serverUrl: String): Int? {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(serverUrl)
            connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            // 记录请求发送时间
            val localTimeBefore = System.currentTimeMillis()
            
            connection.connect()
            
            // 记录响应接收时间
            val localTimeAfter = System.currentTimeMillis()
            
            // 从响应头获取服务器时间
            val serverTimeMillis = connection.date
            
            if (serverTimeMillis <= 0) {
                Log.w(TAG, "Server did not provide Date header")
                return null
            }
            
            // 计算网络延迟（往返时间的一半）
            val networkDelay = (localTimeAfter - localTimeBefore) / 2
            
            // 计算本地时间（考虑网络延迟）
            val localTimeAdjusted = localTimeBefore + networkDelay
            
            // 计算偏移（服务器时间 - 本地时间）
            val offsetMillis = serverTimeMillis - localTimeAdjusted
            val offsetSeconds = (offsetMillis / 1000).toInt()
            
            Log.d(TAG, """
                Time sync details:
                - Server: $serverUrl
                - Server time: $serverTimeMillis
                - Local time: $localTimeAdjusted
                - Network delay: ${networkDelay}ms
                - Offset: ${offsetSeconds}s (${offsetMillis}ms)
            """.trimIndent())
            
            return offsetSeconds
            
        } catch (e: Exception) {
            Log.w(TAG, "Error getting time from $serverUrl", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * 格式化时间偏移为可读字符串
     * @param offsetSeconds 偏移秒数
     * @return 格式化的字符串，如 "+2秒" 或 "-1秒"
     */
    fun formatOffset(offsetSeconds: Int): String {
        return when {
            offsetSeconds > 0 -> "+${offsetSeconds}秒"
            offsetSeconds < 0 -> "${offsetSeconds}秒"
            else -> "0秒（无偏移）"
        }
    }
}
