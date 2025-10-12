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
        
        // 设置透明背景
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 获取传递的数据
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val website = intent.getStringExtra(EXTRA_WEBSITE) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        
        // 如果已经有BottomSheet在显示，先关闭
        if (savedInstanceState != null) {
            bottomSheet = supportFragmentManager.findFragmentByTag("save_bottom_sheet") as? AutofillSaveBottomSheet
        }
        
        // 显示底部弹窗（避免重复显示）
        if (bottomSheet == null) {
            bottomSheet = AutofillSaveBottomSheet.newInstance(
                username = username,
                password = password,
                website = website,
                packageName = packageName
            ).apply {
                setOnSaveListener {
                    // 保存成功，返回结果
                    setResult(RESULT_SAVED)
                    finish()
                }
                setOnDismissListener {
                    // 用户取消，关闭Activity
                    if (!isFinishing) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
            }
            
            bottomSheet?.show(supportFragmentManager, "save_bottom_sheet")
        }
    }
    
    override fun onDestroy() {
        bottomSheet = null
        super.onDestroy()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 按返回键时关闭BottomSheet和Activity
        bottomSheet?.dismiss()
        super.onBackPressed()
    }
}
