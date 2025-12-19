package takagi.ru.monica.utils

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 防截屏工具类
 */
object ScreenshotProtectionUtil {
    
    /**
     * 启用防截屏保护
     */
    fun enableScreenshotProtection(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
    
    /**
     * 禁用防截屏保护
     */
    fun disableScreenshotProtection(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Compose组件：防截屏保护
 */
@Composable
fun ScreenshotProtection(
    enabled: Boolean
) {
    val context = LocalContext.current
    
    DisposableEffect(enabled) {
        val activity = context as? Activity
        if (activity != null) {
            if (enabled) {
                ScreenshotProtectionUtil.enableScreenshotProtection(activity)
            } else {
                ScreenshotProtectionUtil.disableScreenshotProtection(activity)
            }
        }
        
        onDispose {
            // 在组件销毁时恢复原始状态
            if (activity != null && enabled) {
                ScreenshotProtectionUtil.disableScreenshotProtection(activity)
            }
        }
    }
}