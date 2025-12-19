package takagi.ru.monica.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.autofill.data.AutofillItem
import takagi.ru.monica.autofill.data.PaymentInfo
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.ui.theme.MonicaTheme

/**
 * 自动填充选择器Activity
 * 
 * 显示密码和账单信息列表,供用户选择要填充的项目
 * 使用Material Design 3设计规范,与应用主题保持一致
 */
class AutofillPickerActivity : ComponentActivity() {
    
    companion object {
        /** Intent Extra: 密码ID列表 (LongArray) */
        const val EXTRA_PASSWORD_IDS = "extra_password_ids"
        
        /** Intent Extra: 账单信息ID列表 (LongArray) */
        const val EXTRA_PAYMENT_IDS = "extra_payment_ids"
        
        /** Intent Extra: 应用包名 */
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        
        /** Intent Extra: 字段类型 (password/payment) */
        const val EXTRA_FIELD_TYPE = "extra_field_type"
        
        /** Intent Extra: 网站域名 */
        const val EXTRA_DOMAIN = "extra_domain"
        
        /** Result Extra: 选中的密码ID */
        const val RESULT_PASSWORD_ID = "result_password_id"
        
        /** Result Extra: 选中的账单信息ID */
        const val RESULT_PAYMENT_ID = "result_payment_id"
        
        /** Result Extra: 选择类型 (password/payment) */
        const val RESULT_SELECTION_TYPE = "result_selection_type"
        
        /** 选择类型: 密码 */
        const val SELECTION_TYPE_PASSWORD = "password"
        
        /** 选择类型: 账单信息 */
        const val SELECTION_TYPE_PAYMENT = "payment"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置透明背景和安全标志
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // 设置透明背景,让底层应用可见
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 从Intent获取数据
        val passwordIds = intent.getLongArrayExtra(EXTRA_PASSWORD_IDS) ?: longArrayOf()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val fieldType = intent.getStringExtra(EXTRA_FIELD_TYPE)
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        
        // 调试日志
        android.util.Log.d("AutofillPicker", "=== AutofillPickerActivity Started ===")
        android.util.Log.d("AutofillPicker", "Password IDs count: ${passwordIds.size}")
        android.util.Log.d("AutofillPicker", "Package: $packageName")
        android.util.Log.d("AutofillPicker", "Domain: $domain")
        android.util.Log.d("AutofillPicker", "Field type: $fieldType")
        
        // 初始化数据库
        val database = PasswordDatabase.getDatabase(applicationContext)
        val repository = PasswordRepository(database.passwordEntryDao())
        
        setContent {
            var passwords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }
            
            // 从数据库加载密码
            LaunchedEffect(Unit) {
                try {
                    val loadedPasswords = repository.getPasswordsByIds(passwordIds.toList())
                    passwords = loadedPasswords
                    android.util.Log.d("AutofillPicker", "Loaded ${loadedPasswords.size} passwords from database")
                } catch (e: Exception) {
                    android.util.Log.e("AutofillPicker", "Error loading passwords", e)
                } finally {
                    isLoading = false
                }
            }
            
            // 使用透明背景的主题
            MonicaTheme(
                darkTheme = isSystemInDarkTheme()
            ) {
                // 使用Box包裹,设置透明背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    takagi.ru.monica.autofill.ui.AutofillPickerScreen(
                        passwords = passwords,
                        paymentInfo = emptyList(),
                        packageName = packageName,
                        domain = domain,
                        fieldType = fieldType,
                        onItemSelected = { item -> handleSelection(item) },
                        onDismiss = { handleDismiss() }
                    )
                }
            }
        }
    }
    

    
    /**
     * 处理用户选择
     */
    private fun handleSelection(item: AutofillItem) {
        android.util.Log.d("AutofillPicker", "User selected item: $item")
        
        val resultIntent = Intent()
        
        when (item) {
            is AutofillItem.Password -> {
                android.util.Log.d("AutofillPicker", "Selected password ID: ${item.entry.id}")
                
                // 🔧 关键：直接返回 Dataset 而不是 FillResponse
                // Dataset 会立即填充，不会显示选择界面
                val dataset = createDatasetForPassword(item.entry)
                resultIntent.putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
            is AutofillItem.Payment -> {
                android.util.Log.d("AutofillPicker", "Selected payment ID: ${item.info.id}")
                // TODO: 实现账单信息填充
            }
        }
        
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
    
    /**
     * 为选中的密码创建 Dataset（立即填充）
     */
    private fun createDatasetForPassword(password: PasswordEntry): FillResponse {
        val autofillIds = intent.getParcelableArrayListExtra<AutofillId>("autofill_ids")
        val packageName = intent.getStringExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME)
        val domain = intent.getStringExtra(AutofillPickerActivity.EXTRA_DOMAIN)
        val allPasswordIds = intent.getLongArrayExtra(AutofillPickerActivity.EXTRA_PASSWORD_IDS) ?: longArrayOf()
        
        android.util.Log.d("AutofillPicker", "Creating FillResponse with selected password + manual selector")
        android.util.Log.d("AutofillPicker", "Autofill IDs count: ${autofillIds?.size}")
        android.util.Log.d("AutofillPicker", "All password IDs count: ${allPasswordIds.size}")
        
        val securityManager = takagi.ru.monica.security.SecurityManager(applicationContext)
        
        // 解密
        val decryptedUsername = if (password.username.contains("==") && password.username.length > 20) {
            securityManager.decryptData(password.username)
        } else {
            password.username
        }
        val decryptedPassword = securityManager.decryptData(password.password)
        
        android.util.Log.d("AutofillPicker", "Creating Dataset for selected: ${password.title}")
        
        // 创建 FillResponse.Builder
        val responseBuilder = FillResponse.Builder()
        
        // 1. 添加选中密码的 Dataset
        val selectedPresentation = RemoteViews(this.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, password.title.ifEmpty { decryptedUsername })
            setTextViewText(R.id.text_username, decryptedUsername)
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        val selectedDatasetBuilder = android.service.autofill.Dataset.Builder(selectedPresentation)
        
        // 填充字段
        if (!autofillIds.isNullOrEmpty()) {
            autofillIds.forEachIndexed { index, autofillId ->
                val value = if (index % 2 == 0) decryptedUsername else decryptedPassword
                selectedDatasetBuilder.setValue(autofillId, AutofillValue.forText(value))
            }
        }
        
        responseBuilder.addDataset(selectedDatasetBuilder.build())
        
        // 2. 重新添加"手动选择" Dataset，这样它不会消失！
        val pickerIntent = Intent(applicationContext, AutofillPickerActivity::class.java).apply {
            // 传递所有密码ID（从原始Intent获取）
            putExtra(AutofillPickerActivity.EXTRA_PASSWORD_IDS, allPasswordIds)
            putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AutofillPickerActivity.EXTRA_DOMAIN, domain)
            putParcelableArrayListExtra("autofill_ids", autofillIds)
            putExtra(AutofillPickerActivity.EXTRA_FIELD_TYPE, "password")
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            System.currentTimeMillis().toInt(),
            pickerIntent,
            flags
        )
        
        // 创建"手动选择" Dataset
        val manualPresentation = RemoteViews(this.packageName, R.layout.autofill_manual_card)
        val manualDatasetBuilder = android.service.autofill.Dataset.Builder(manualPresentation)
        
        autofillIds?.forEach { autofillId ->
            manualDatasetBuilder.setValue(autofillId, null, manualPresentation)
        }
        manualDatasetBuilder.setAuthentication(pendingIntent.intentSender)
        
        responseBuilder.addDataset(manualDatasetBuilder.build())
        
        android.util.Log.d("AutofillPicker", "✅ FillResponse created with 2 datasets (selected + manual)")
        
        return responseBuilder.build()
    }    
    /**
     * 处理取消/关闭
     */
    private fun handleDismiss() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
