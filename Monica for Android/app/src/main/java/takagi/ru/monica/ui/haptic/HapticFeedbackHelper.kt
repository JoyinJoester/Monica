package takagi.ru.monica.ui.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Phase 9: 触觉反馈工具类
 * 
 * 提供统一的触觉反馈接口，提升用户交互体验
 * 支持不同强度的震动反馈
 * 
 * ## 反馈类型
 * - **轻量点击**: 按钮、开关、选择
 * - **长按**: 长按操作确认
 * - **成功**: 操作成功提示
 * - **警告**: 警告操作（如删除）
 * - **错误**: 错误操作提示
 * - **拒绝**: 操作被拒绝
 * 
 * ## 使用示例
 * ```kotlin
 * @Composable
 * fun MyButton() {
 *     val haptic = rememberHapticFeedback()
 *     
 *     Button(onClick = {
 *         haptic.performLightClick()
 *         // 执行操作
 *     }) {
 *         Text("点击")
 *     }
 * }
 * ```
 * 
 * ## 权限要求
 * 需要在 AndroidManifest.xml 中添加：
 * ```xml
 * <uses-permission android:name="android.permission.VIBRATE" />
 * ```
 */
class HapticFeedbackHelper(
    private val context: Context,
    private val view: View? = null
) {
    
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    /**
     * 检查设备是否支持触觉反馈
     */
    val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true
    
    // ==================== 标准触觉反馈 ====================
    
    /**
     * 轻量点击反馈
     * 
     * 适用场景：
     * - 按钮点击
     * - 开关切换
     * - 选项选择
     * - 列表项点击
     */
    fun performLightClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            performCustomVibration(10)  // 10ms 短震动
        }
    }
    
    /**
     * 标准点击反馈
     * 
     * 适用场景：
     * - 重要按钮点击
     * - 确认操作
     * - Tab 切换
     */
    fun performClick() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            ?: performCustomVibration(20)  // 20ms 震动
    }
    
    /**
     * 长按反馈
     * 
     * 适用场景：
     * - 长按操作
     * - 拖拽开始
     * - 上下文菜单打开
     */
    fun performLongPress() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            ?: performCustomVibration(50)  // 50ms 震动
    }
    
    // ==================== 状态反馈 ====================
    
    /**
     * 成功反馈
     * 
     * 适用场景：
     * - 操作成功
     * - 保存完成
     * - 验证通过
     * 
     * 震动模式：短-停-短 (表示肯定)
     */
    fun performSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            vibrator?.vibrate(effect)
        } else {
            performCustomVibration(
                pattern = longArrayOf(0, 30, 50, 30),  // 短-停-短
                amplitudes = intArrayOf(0, 100, 0, 100)
            )
        }
    }
    
    /**
     * 警告反馈
     * 
     * 适用场景：
     * - 删除确认
     * - 警告提示
     * - 危险操作
     * 
     * 震动模式：长震 (表示警告)
     */
    fun performWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            vibrator?.vibrate(effect)
        } else {
            performCustomVibration(80)  // 80ms 长震动
        }
    }
    
    /**
     * 错误反馈
     * 
     * 适用场景：
     * - 操作失败
     * - 验证错误
     * - 输入无效
     * 
     * 震动模式：短-短-短 (表示否定)
     */
    fun performError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            performCustomVibration(
                pattern = longArrayOf(0, 30, 30, 30, 30, 30),  // 短-短-短
                amplitudes = intArrayOf(0, 100, 0, 100, 0, 100)
            )
        }
    }
    
    /**
     * 拒绝反馈
     * 
     * 适用场景：
     * - 操作被拒绝
     * - 权限不足
     * - 限制触发
     */
    fun performReject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            performCustomVibration(100)  // 100ms 拒绝震动
        }
    }
    
    // ==================== 特殊反馈 ====================
    
    /**
     * 生物识别成功反馈
     * 
     * 适用场景：
     * - 指纹识别成功
     * - 面部识别成功
     * 
     * 震动模式：渐强震动
     */
    fun performBiometricSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator?.vibrate(effect)
        } else {
            performCustomVibration(
                pattern = longArrayOf(0, 20, 20, 40),
                amplitudes = intArrayOf(0, 80, 0, 255)
            )
        }
    }
    
    /**
     * 生物识别失败反馈
     * 
     * 适用场景：
     * - 指纹识别失败
     * - 面部识别失败
     * 
     * 震动模式：快速抖动
     */
    fun performBiometricError() {
        performCustomVibration(
            pattern = longArrayOf(0, 20, 20, 20, 20, 20),
            amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
        )
    }
    
    /**
     * 侧滑反馈
     * 
     * 适用场景：
     * - 列表项侧滑
     * - 页面滑动
     */
    fun performSwipe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            performCustomVibration(15)  // 15ms 轻微震动
        }
    }
    
    /**
     * 刷新反馈
     * 
     * 适用场景：
     * - 下拉刷新
     * - 数据重载
     */
    fun performRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            performCustomVibration(25)  // 25ms 震动
        }
    }
    
    // ==================== 自定义震动 ====================
    
    /**
     * 自定义震动（单次）
     * 
     * @param duration 震动时长（毫秒）
     * @param amplitude 震动强度（1-255），-1表示默认
     */
    private fun performCustomVibration(
        duration: Long,
        amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE
    ) {
        if (!isAvailable) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticFeedback", "Vibration failed", e)
        }
    }
    
    /**
     * 自定义震动（模式）
     * 
     * @param pattern 震动模式 [延迟, 震动, 停止, 震动, ...]
     * @param amplitudes 震动强度数组（需要与pattern长度匹配）
     */
    private fun performCustomVibration(
        pattern: LongArray,
        amplitudes: IntArray
    ) {
        if (!isAvailable) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticFeedback", "Pattern vibration failed", e)
        }
    }
}

/**
 * Compose 辅助函数：记住触觉反馈实例
 * 
 * @return HapticFeedbackHelper
 */
@Composable
fun rememberHapticFeedback(): HapticFeedbackHelper {
    val context = LocalContext.current
    val view = LocalView.current
    
    return remember(context, view) {
        HapticFeedbackHelper(context, view)
    }
}
