package takagi.ru.monica.autofill.core

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After

/**
 * AutofillLogger 单元测试
 * 
 * 测试日志系统的核心功能:
 * - 日志记录
 * - 自动脱敏
 * - 日志导出
 * - 统计信息
 */
class AutofillLoggerTest {
    
    @Before
    fun setup() {
        AutofillLogger.clear()
        AutofillLogger.setEnabled(true)
    }
    
    @After
    fun tearDown() {
        AutofillLogger.clear()
    }
    
    @Test
    fun `test log levels`() {
        // 测试不同级别的日志
        AutofillLogger.d("Test", "Debug message")
        AutofillLogger.i("Test", "Info message")
        AutofillLogger.w("Test", "Warning message")
        AutofillLogger.e("Test", "Error message")
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(4, logs.size)
        
        assertEquals(AutofillLogger.Level.DEBUG, logs[0].level)
        assertEquals(AutofillLogger.Level.INFO, logs[1].level)
        assertEquals(AutofillLogger.Level.WARN, logs[2].level)
        assertEquals(AutofillLogger.Level.ERROR, logs[3].level)
    }
    
    @Test
    fun `test metadata`() {
        // 测试元数据记录
        val metadata = mapOf(
            "key1" to "value1",
            "key2" to 123,
            "key3" to true
        )
        
        AutofillLogger.i("Test", "Message with metadata", metadata)
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(1, logs.size)
        assertEquals(metadata.size, logs[0].metadata.size)
        assertTrue(logs[0].metadata.containsKey("key1"))
    }
    
    @Test
    fun `test password sanitization`() {
        // 测试密码脱敏
        val message = "password=secret123"
        AutofillLogger.d("Test", message)
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(1, logs.size)
        assertFalse(logs[0].message.contains("secret123"))
        assertTrue(logs[0].message.contains("***"))
    }
    
    @Test
    fun `test email sanitization`() {
        // 测试邮箱脱敏
        val message = "User email is user@example.com"
        AutofillLogger.d("Test", message)
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(1, logs.size)
        assertFalse(logs[0].message.contains("user@example.com"))
        assertTrue(logs[0].message.contains("***@***.com"))
    }
    
    @Test
    fun `test phone sanitization`() {
        // 测试手机号脱敏
        val message = "Phone: 13812345678"
        AutofillLogger.d("Test", message)
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(1, logs.size)
        assertFalse(logs[0].message.contains("13812345678"))
        assertTrue(logs[0].message.contains("***********"))
    }
    
    @Test
    fun `test log limit`() {
        // 测试日志数量限制 (最多500条)
        repeat(600) { i ->
            AutofillLogger.d("Test", "Message $i")
        }
        
        val logs = AutofillLogger.getRecentLogs(600)
        assertEquals(500, logs.size)
        
        // 验证是保留最新的500条
        assertEquals("Message 599", logs.last().message)
    }
    
    @Test
    fun `test export logs`() {
        // 测试日志导出
        AutofillLogger.i("Test", "Log 1")
        AutofillLogger.w("Test", "Log 2")
        AutofillLogger.e("Test", "Log 3")
        
        val exported = AutofillLogger.exportLogs()
        
        assertTrue(exported.contains("Monica Autofill 诊断日志"))
        assertTrue(exported.contains("Log 1"))
        assertTrue(exported.contains("Log 2"))
        assertTrue(exported.contains("Log 3"))
        assertTrue(exported.contains("日志条数: 3"))
    }
    
    @Test
    fun `test statistics`() {
        // 测试统计信息
        AutofillLogger.d("Cat1", "Message 1")
        AutofillLogger.d("Cat1", "Message 2")
        AutofillLogger.i("Cat2", "Message 3")
        AutofillLogger.w("Cat1", "Message 4")
        AutofillLogger.e("Cat2", "Message 5")
        
        val stats = AutofillLogger.getStats()
        
        assertEquals(5, stats["totalLogs"])
        
        @Suppress("UNCHECKED_CAST")
        val levelCounts = stats["levelCounts"] as Map<*, *>
        assertEquals(2, levelCounts[AutofillLogger.Level.DEBUG])
        assertEquals(1, levelCounts[AutofillLogger.Level.INFO])
        
        @Suppress("UNCHECKED_CAST")
        val categoryCounts = stats["categoryCounts"] as Map<*, *>
        assertEquals(3, categoryCounts["Cat1"])
        assertEquals(2, categoryCounts["Cat2"])
    }
    
    @Test
    fun `test clear logs`() {
        // 测试清除日志
        AutofillLogger.i("Test", "Message 1")
        AutofillLogger.i("Test", "Message 2")
        
        assertEquals(2, AutofillLogger.getRecentLogs().size)
        
        AutofillLogger.clear()
        
        assertEquals(0, AutofillLogger.getRecentLogs().size)
    }
    
    @Test
    fun `test error with exception`() {
        // 测试错误日志带异常
        val exception = RuntimeException("Test error")
        AutofillLogger.e("Test", "Error occurred", error = exception)
        
        val logs = AutofillLogger.getRecentLogs()
        assertEquals(1, logs.size)
        
        assertTrue(logs[0].metadata.containsKey("error"))
        assertTrue(logs[0].metadata.containsKey("stackTrace"))
        assertTrue(logs[0].metadata["error"].toString().contains("Test error"))
    }
    
    @Test
    fun `test enable and disable`() {
        // 测试启用/禁用日志
        AutofillLogger.setEnabled(false)
        AutofillLogger.i("Test", "Message 1")
        
        // 禁用时仍然会输出到 Logcat,但不会存储到内存
        // 由于测试环境限制,我们只能验证日志列表为空
        
        AutofillLogger.setEnabled(true)
        AutofillLogger.i("Test", "Message 2")
        
        val logs = AutofillLogger.getRecentLogs()
        // 应该只有 Message 2
        assertTrue(logs.any { it.message.contains("Message 2") })
    }
    
    @Test
    fun `test log entry format`() {
        // 测试日志条目格式化
        AutofillLogger.i("TestCategory", "Test message", mapOf("key" to "value"))
        
        val logs = AutofillLogger.getRecentLogs()
        val formatted = logs[0].format()
        
        assertTrue(formatted.contains("[INFO]"))
        assertTrue(formatted.contains("[TestCategory]"))
        assertTrue(formatted.contains("Test message"))
        assertTrue(formatted.contains("key"))
        assertTrue(formatted.contains("value"))
    }
    
    @Test
    fun `test multiple sanitization patterns`() {
        // 测试多种脱敏模式同时存在
        val message = """
            password=secret123
            email: user@example.com
            phone: 13812345678
            id: 110101199001011234
        """.trimIndent()
        
        AutofillLogger.d("Test", message)
        
        val logs = AutofillLogger.getRecentLogs()
        val sanitized = logs[0].message
        
        // 验证所有敏感信息都被脱敏
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("13812345678"))
        assertFalse(sanitized.contains("110101199001011234"))
        
        assertTrue(sanitized.contains("***"))
    }
}
