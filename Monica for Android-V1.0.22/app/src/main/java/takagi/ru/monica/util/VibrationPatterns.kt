package takagi.ru.monica.util

/**
 * 震动模式常量（参考 Aegis 实现）
 * 
 * 模式数组格式：[等待时间, 震动时间, 等待时间, 震动时间, ...]
 * 使用 VibrationEffect.createWaveform(pattern, -1) 播放
 */
object VibrationPatterns {
    /**
     * 验证码即将过期时的震动模式
     * 类似心跳节奏：短震-停顿-短震-长停顿-短震-停顿-短震-长停顿...
     * 总时长约 3 秒
     */
    val EXPIRING = longArrayOf(
        475, 20,    // 等待 475ms，震动 20ms
        5, 20,      // 等待 5ms，震动 20ms（双击效果）
        965, 20,    // 等待 965ms，震动 20ms
        5, 20,      // 等待 5ms，震动 20ms（双击效果）
        965, 20,    // 等待 965ms，震动 20ms
        5, 20,      // 等待 5ms，震动 20ms（双击效果）
        420         // 最后等待 420ms 结束
    )
    
    /**
     * 验证码刷新时的短震动
     */
    val REFRESH_CODE = longArrayOf(0, 100)
    
    /**
     * 每秒提醒的双击震动模式
     * 比单次100ms更有节奏感：短震-停顿-短震
     */
    val TICK = longArrayOf(0, 50, 40, 50)
    
    /**
     * 计算模式的总时长（毫秒）
     */
    fun getLengthInMillis(pattern: LongArray): Long {
        return pattern.sum()
    }
}
