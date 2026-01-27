package takagi.ru.monica.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * 透明的Activity用于承载BottomSheet
 * 保持原应用界面可见，同时显示密码保存对话框
 */
class AutofillSaveTransparentActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_WEBSITE = "website"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val RESULT_SAVED = Activity.RESULT_FIRST_USER
    }
    
    private var bottomSheet: AutofillSaveBottomSheet? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.w("AutofillSaveActivity", "")
        android.util.Log.w("AutofillSaveActivity", "🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢")
        android.util.Log.w("AutofillSaveActivity", "🟢🟢  Activity 已被系统启动!  🟢🟢")
        android.util.Log.w("AutofillSaveActivity", "🟢🟢  IntentSender 生效!      🟢🟢")
        android.util.Log.w("AutofillSaveActivity", "🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢")
        android.util.Log.w("AutofillSaveActivity", "")
        
        // 设置透明背景
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 获取传递的数据
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val website = intent.getStringExtra(EXTRA_WEBSITE) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        
        android.util.Log.w("AutofillSaveActivity", "╔══════════════════════════════════════════╗")
        android.util.Log.w("AutofillSaveActivity", "║  AutofillSaveTransparentActivity 启动  ║")
        android.util.Log.w("AutofillSaveActivity", "╚══════════════════════════════════════════╝")
        android.util.Log.d("AutofillSaveActivity", "接收到的数据:")
        android.util.Log.d("AutofillSaveActivity", "  - Username: $username")
        android.util.Log.d("AutofillSaveActivity", "  - Password: ${password.length} chars")
        android.util.Log.d("AutofillSaveActivity", "  - Website: $website")
        android.util.Log.d("AutofillSaveActivity", "  - PackageName: $packageName")
        
        // 如果已经有BottomSheet在显示，先关闭
        if (savedInstanceState != null) {
            bottomSheet = supportFragmentManager.findFragmentByTag("save_bottom_sheet") as? AutofillSaveBottomSheet
        }
        
        // 显示底部弹窗（避免重复显示）
        if (bottomSheet == null) {
            android.util.Log.d("AutofillSaveActivity", "创建 BottomSheet...")
            
            bottomSheet = AutofillSaveBottomSheet.newInstance(
                username = username,
                password = password,
                website = website,
                packageName = packageName
            ).apply {
                setOnSaveListener {
                    android.util.Log.w("AutofillSaveActivity", "🎉🎉🎉 onSaveListener 回调触发! 🎉🎉🎉")
                    android.util.Log.d("AutofillSaveActivity", "设置 Activity 结果为 RESULT_SAVED")
                    // 保存成功，返回结果
                    setResult(RESULT_SAVED)
                    android.util.Log.d("AutofillSaveActivity", "准备关闭 Activity...")
                    finish()
                    android.util.Log.d("AutofillSaveActivity", "Activity.finish() 已调用")
                }
                setOnDismissListener {
                    android.util.Log.w("AutofillSaveActivity", "❌ onDismissListener 回调触发 (用户取消)")
                    // 用户取消，关闭Activity
                    if (!isFinishing) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
            }
            
            android.util.Log.d("AutofillSaveActivity", "显示 BottomSheet...")
            bottomSheet?.show(supportFragmentManager, "save_bottom_sheet")
            android.util.Log.d("AutofillSaveActivity", "✅ BottomSheet 已显示")
        } else {
            android.util.Log.w("AutofillSaveActivity", "⚠️ BottomSheet 已存在,跳过创建")
        }
    }
    
    override fun onDestroy() {
        android.util.Log.d("AutofillSaveActivity", "🔴 Activity.onDestroy() 被调用")
        bottomSheet = null
        super.onDestroy()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        android.util.Log.d("AutofillSaveActivity", "⬅️ 用户按下返回键")
        // 按返回键时关闭BottomSheet和Activity
        bottomSheet?.dismiss()
        super.onBackPressed()
    }
}
