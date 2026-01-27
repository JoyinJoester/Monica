package takagi.ru.monica.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Autofill保存拦截器
 * 
 * 由于 Android Autofill 框架的限制,我们无法完全移除系统的 SaveInfo 对话框。
 * 这个类提供了一个变通方案:
 * 
 * 1. 在用户选择填充密码后,注册一个监听器
 * 2. 监听应用的 Activity 生命周期变化
 * 3. 如果检测到 Activity 即将finish(),拦截并显示自定义 Bottom Sheet
 * 4. 用户在 Bottom Sheet 中确认保存
 * 
 * ⚠️ 注意: 这个方案需要 SYSTEM_ALERT_WINDOW 权限才能在其他应用上层显示
 */
class AutofillSaveInterceptor(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isMonitoring = false
    
    /**
     * 开始监听表单提交
     * 
     * @param packageName 目标应用包名
     * @param username 用户名
     * @param password 密码
     * @param webDomain 网站域名
     */
    fun startMonitoring(
        packageName: String,
        username: String,
        password: String,
        webDomain: String?
    ) {
        if (isMonitoring) {
            Log.w("SaveInterceptor", "Already monitoring, ignoring")
            return
        }
        
        isMonitoring = true
        Log.d("SaveInterceptor", "Started monitoring for package: $packageName")
        
        // 启动延迟监听(等待用户提交表单)
        scope.launch {
            // 等待5秒,如果Activity还没finish,说明用户可能提交了表单
            delay(5000)
            
            if (isMonitoring) {
                // 显示自定义 Bottom Sheet
                showCustomSavePrompt(packageName, username, password, webDomain)
            }
        }
    }
    
    /**
     * 停止监听
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.d("SaveInterceptor", "Stopped monitoring")
    }
    
    /**
     * 显示自定义保存提示
     */
    private fun showCustomSavePrompt(
        packageName: String,
        username: String,
        password: String,
        webDomain: String?
    ) {
        Log.d("SaveInterceptor", "Showing custom save prompt")
        
        val intent = Intent(context, AutofillSaveTransparentActivity::class.java).apply {
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, username)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, password)
            putExtra(AutofillSaveTransparentActivity.EXTRA_WEBSITE, webDomain ?: "")
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra("EXTRA_IS_UPDATE", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        context.startActivity(intent)
        stopMonitoring()
    }
}
