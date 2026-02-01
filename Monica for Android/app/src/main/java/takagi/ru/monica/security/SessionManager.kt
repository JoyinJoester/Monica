package takagi.ru.monica.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话管理器 - 统一管理应用解锁状态
 * 
 * 职责：
 * - 维护内存态解锁标志、时间戳、进程标识
 * - 暴露 canSkipVerification() 统一判断免验证条件
 * - 负责与 AutoLock 逻辑联动，超时自动清理会话
 * 
 * 安全窗规则：
 * - 仅在解锁后 N 分钟内允许免验证
 * - 屏幕锁定时必须重新验证
 * - 进程重启后必须重新验证
 */
object SessionManager {
    
    private const val TAG = "SessionManager"
    
    // 解锁状态
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()
    
    // 解锁时间戳（基于 SystemClock.elapsedRealtime，不受系统时间修改影响）
    private var unlockTimestamp: Long = 0L
    
    // 自动锁定超时（分钟），从 SettingsManager 同步
    private var autoLockMinutes: Int = 5
    
    // 进程标识（用于检测进程重启）
    private val processId: Int = android.os.Process.myPid()
    
    /**
     * 标记应用已解锁
     */
    fun markUnlocked() {
        _isUnlocked.value = true
        unlockTimestamp = SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "Session unlocked at $unlockTimestamp, PID=$processId")
    }
    
    /**
     * 标记应用已锁定
     */
    fun markLocked() {
        _isUnlocked.value = false
        unlockTimestamp = 0L
        android.util.Log.d(TAG, "Session locked, PID=$processId")
    }
    
    /**
     * 更新自动锁定超时配置
     */
    fun updateAutoLockTimeout(minutes: Int) {
        autoLockMinutes = minutes
        android.util.Log.d(TAG, "Auto-lock timeout updated to $minutes minutes")
    }
    
    /**
     * 检查是否可以跳过验证
     * 
     * 安全窗规则：
     * 1. 必须已解锁
     * 2. 未超过自动锁定时间
     * 3. 屏幕未锁定
     * 
     * @param context 上下文，用于检查屏幕锁定状态
     * @return true 如果可以跳过验证
     */
    fun canSkipVerification(context: Context): Boolean {
        // 检查是否已解锁
        if (!_isUnlocked.value) {
            android.util.Log.d(TAG, "canSkipVerification: false (not unlocked)")
            return false
        }
        
        // 检查是否超时
        val elapsedMinutes = (SystemClock.elapsedRealtime() - unlockTimestamp) / 60000
        if (elapsedMinutes >= autoLockMinutes) {
            android.util.Log.d(TAG, "canSkipVerification: false (session expired, elapsed=$elapsedMinutes min)")
            markLocked()
            return false
        }
        
        // 检查屏幕是否锁定
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            android.util.Log.d(TAG, "canSkipVerification: false (device locked)")
            markLocked()
            return false
        }
        
        android.util.Log.d(TAG, "canSkipVerification: true (unlocked, within timeout, screen unlocked)")
        return true
    }
    
    /**
     * 刷新会话时间戳（用户活动时调用）
     */
    fun refreshSession() {
        if (_isUnlocked.value) {
            unlockTimestamp = SystemClock.elapsedRealtime()
            android.util.Log.d(TAG, "Session refreshed at $unlockTimestamp")
        }
    }
    
    /**
     * 检查会话是否过期（不自动锁定，仅检查）
     */
    fun isSessionExpired(): Boolean {
        if (!_isUnlocked.value) return true
        val elapsedMinutes = (SystemClock.elapsedRealtime() - unlockTimestamp) / 60000
        return elapsedMinutes >= autoLockMinutes
    }
    
    /**
     * 获取剩余有效时间（分钟）
     */
    fun getRemainingMinutes(): Int {
        if (!_isUnlocked.value) return 0
        val elapsedMinutes = (SystemClock.elapsedRealtime() - unlockTimestamp) / 60000
        return maxOf(0, autoLockMinutes - elapsedMinutes.toInt())
    }
}
