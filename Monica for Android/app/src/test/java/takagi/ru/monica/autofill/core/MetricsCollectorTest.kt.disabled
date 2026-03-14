package takagi.ru.monica.autofill.core

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

/**
 * MetricsCollector 单元测试
 * 
 * 测试性能监控系统:
 * - 指标记录
 * - 统计计算
 * - 性能跟踪
 */
class MetricsCollectorTest {
    
    @Before
    fun setup() {
        MetricsCollector.reset()
    }
    
    @Test
    fun `test record request`() {
        MetricsCollector.recordRequest("com.example.app", "example.com")
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(1, metrics.totalRequests)
        assertEquals(1, metrics.sourceApps["com.example.app"])
        assertEquals(1, metrics.sourceDomains["example.com"])
    }
    
    @Test
    fun `test record success`() {
        MetricsCollector.recordSuccess(100, 50, 30)
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(1, metrics.successfulFills)
        assertEquals(1, metrics.responseTimes.size)
        assertEquals(100L, metrics.responseTimes[0])
        assertEquals(50L, metrics.matchingTimes[0])
        assertEquals(30L, metrics.fillingTimes[0])
    }
    
    @Test
    fun `test record failure`() {
        MetricsCollector.recordFailure("DatabaseError")
        MetricsCollector.recordFailure("NetworkError")
        MetricsCollector.recordFailure("DatabaseError")
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(3, metrics.failedFills)
        assertEquals(2, metrics.errorTypes["DatabaseError"])
        assertEquals(1, metrics.errorTypes["NetworkError"])
    }
    
    @Test
    fun `test record cancellation`() {
        MetricsCollector.recordCancellation()
        MetricsCollector.recordCancellation()
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(2, metrics.cancelledRequests)
    }
    
    @Test
    fun `test success rate calculation`() {
        // 10次请求, 7次成功
        repeat(10) {
            MetricsCollector.recordRequest("com.example", null)
        }
        repeat(7) {
            MetricsCollector.recordSuccess(100)
        }
        repeat(3) {
            MetricsCollector.recordFailure()
        }
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(70.0, metrics.getSuccessRate(), 0.01)
    }
    
    @Test
    fun `test average response time`() {
        MetricsCollector.recordSuccess(100)
        MetricsCollector.recordSuccess(200)
        MetricsCollector.recordSuccess(300)
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(200L, metrics.getAverageResponseTime())
    }
    
    @Test
    fun `test percentile calculations`() {
        // 添加 100 个响应时间: 1ms 到 100ms
        repeat(100) { i ->
            MetricsCollector.recordSuccess((i + 1).toLong())
        }
        
        val metrics = MetricsCollector.getMetrics()
        
        // P95 应该约为 95ms
        val p95 = metrics.get95thPercentileResponseTime()
        assertTrue(p95 >= 90 && p95 <= 100)
        
        // P99 应该约为 99ms
        val p99 = metrics.get99thPercentileResponseTime()
        assertTrue(p99 >= 95 && p99 <= 100)
    }
    
    @Test
    fun `test min max response time`() {
        MetricsCollector.recordSuccess(50)
        MetricsCollector.recordSuccess(200)
        MetricsCollector.recordSuccess(100)
        MetricsCollector.recordSuccess(300)
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(50L, metrics.getMinResponseTime())
        assertEquals(300L, metrics.getMaxResponseTime())
    }
    
    @Test
    fun `test match type recording`() {
        MetricsCollector.recordExactMatch()
        MetricsCollector.recordExactMatch()
        MetricsCollector.recordFuzzyMatch()
        MetricsCollector.recordNoMatch()
        
        val metrics = MetricsCollector.getMetrics()
        assertEquals(2, metrics.exactMatches)
        assertEquals(1, metrics.fuzzyMatches)
        assertEquals(1, metrics.noMatches)
    }
    
    @Test
    fun `test formatted stats output`() {
        MetricsCollector.recordRequest("com.example", "example.com")
        MetricsCollector.recordSuccess(150)
        MetricsCollector.recordExactMatch()
        
        val stats = MetricsCollector.getFormattedStats()
        
        assertTrue(stats.contains("总请求数: 1"))
        assertTrue(stats.contains("成功: 1"))
        assertTrue(stats.contains("精确匹配: 1"))
        assertTrue(stats.contains("平均响应时间"))
    }
    
    @Test
    fun `test reset metrics`() {
        MetricsCollector.recordRequest("com.example", null)
        MetricsCollector.recordSuccess(100)
        
        var metrics = MetricsCollector.getMetrics()
        assertEquals(1, metrics.totalRequests)
        
        MetricsCollector.reset()
        
        metrics = MetricsCollector.getMetrics()
        assertEquals(0, metrics.totalRequests)
        assertEquals(0, metrics.successfulFills)
    }
    
    @Test
    fun `test list size limit`() {
        // 测试响应时间列表不会无限增长
        repeat(1500) { i ->
            MetricsCollector.recordSuccess(i.toLong())
        }
        
        val metrics = MetricsCollector.getMetrics()
        // 应该限制在 1000 条
        assertEquals(1000, metrics.responseTimes.size)
    }
    
    @Test
    fun `test performance tracker basic`() = runBlocking {
        val tracker = PerformanceTracker("testOperation")
        
        delay(100) // 模拟操作耗时
        
        val duration = tracker.finish()
        
        // 应该接近 100ms (允许误差)
        assertTrue(duration >= 90 && duration <= 150)
    }
    
    @Test
    fun `test performance tracker pause resume`() = runBlocking {
        val tracker = PerformanceTracker("testOperation")
        
        delay(50)
        
        tracker.pause()
        delay(100) // 这段时间不应该计入
        tracker.resume()
        
        delay(50)
        
        val duration = tracker.finish()
        
        // 应该接近 100ms,不包括暂停的100ms
        assertTrue(duration >= 90 && duration <= 150)
    }
    
    @Test
    fun `test performance tracker elapsed time`() = runBlocking {
        val tracker = PerformanceTracker("testOperation")
        
        delay(50)
        
        val elapsed = tracker.getElapsedTime()
        
        // 应该接近 50ms
        assertTrue(elapsed >= 40 && elapsed <= 100)
    }
    
    @Test
    fun `test multiple apps tracking`() {
        MetricsCollector.recordRequest("com.app1", null)
        MetricsCollector.recordRequest("com.app1", null)
        MetricsCollector.recordRequest("com.app2", null)
        MetricsCollector.recordRequest("com.app3", null)
        MetricsCollector.recordRequest("com.app1", null)
        
        val metrics = MetricsCollector.getMetrics()
        
        assertEquals(3, metrics.sourceApps["com.app1"])
        assertEquals(1, metrics.sourceApps["com.app2"])
        assertEquals(1, metrics.sourceApps["com.app3"])
    }
    
    @Test
    fun `test multiple domains tracking`() {
        MetricsCollector.recordRequest("com.app", "google.com")
        MetricsCollector.recordRequest("com.app", "github.com")
        MetricsCollector.recordRequest("com.app", "google.com")
        
        val metrics = MetricsCollector.getMetrics()
        
        assertEquals(2, metrics.sourceDomains["google.com"])
        assertEquals(1, metrics.sourceDomains["github.com"])
    }
    
    @Test
    fun `test success rate edge cases`() {
        // 测试边界情况
        var metrics = MetricsCollector.getMetrics()
        assertEquals(0.0, metrics.getSuccessRate(), 0.01) // 无请求时
        
        MetricsCollector.recordRequest("com.app", null)
        MetricsCollector.recordSuccess(100)
        
        metrics = MetricsCollector.getMetrics()
        assertEquals(100.0, metrics.getSuccessRate(), 0.01) // 100%成功
        
        MetricsCollector.reset()
        repeat(5) { MetricsCollector.recordRequest("com.app", null) }
        repeat(5) { MetricsCollector.recordFailure() }
        
        metrics = MetricsCollector.getMetrics()
        assertEquals(0.0, metrics.getSuccessRate(), 0.01) // 0%成功
    }
}
