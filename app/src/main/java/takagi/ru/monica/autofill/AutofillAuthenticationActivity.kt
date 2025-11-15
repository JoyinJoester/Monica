package takagi.ru.monica.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricAuthHelper

/**
 * 自动填充身份验证Activity
 * 
 * 在用户选择密码后,显示生物识别验证或密码输入对话框
 * 验证成功后返回包含密码数据的Dataset
 */
class AutofillAuthenticationActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AutofillAuth"
        
        // Intent extras
        const val EXTRA_PASSWORD_ENTRY_ID = "password_entry_id"
        const val EXTRA_USERNAME_VALUE = "username_value"
        const val EXTRA_PASSWORD_VALUE = "password_value"
        const val EXTRA_AUTOFILL_IDS = "autofill_ids"
        const val EXTRA_FIELD_TYPES = "field_types"  // "username" 或 "password"
    }
    
    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private lateinit var securityManager: SecurityManager
    
    // 自动填充数据
    private var passwordEntryId: Long = -1
    private var usernameValue: String? = null
    private var passwordValue: String? = null
    private var autofillIds: ArrayList<AutofillId>? = null
    private var fieldTypes: ArrayList<String>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "AutofillAuthenticationActivity created")
        
        biometricAuthHelper = BiometricAuthHelper(this)
        securityManager = SecurityManager(this)
        
        // 获取传递的数据
        passwordEntryId = intent.getLongExtra(EXTRA_PASSWORD_ENTRY_ID, -1)
        usernameValue = intent.getStringExtra(EXTRA_USERNAME_VALUE)
        passwordValue = intent.getStringExtra(EXTRA_PASSWORD_VALUE)
        autofillIds = intent.getParcelableArrayListExtra(EXTRA_AUTOFILL_IDS)
        fieldTypes = intent.getStringArrayListExtra(EXTRA_FIELD_TYPES)
        
        Log.d(TAG, "Password entry ID: $passwordEntryId")
        Log.d(TAG, "Username: $usernameValue")
        Log.d(TAG, "Autofill IDs count: ${autofillIds?.size}")
        Log.d(TAG, "Field types: $fieldTypes")
        
        // 检查是否支持生物识别
        if (biometricAuthHelper.isBiometricAvailable()) {
            showBiometricAuthentication()
        } else {
            // 降级到密码验证
            showPasswordAuthentication()
        }
    }
    
    /**
     * 显示生物识别验证
     */
    private fun showBiometricAuthentication() {
        Log.d(TAG, "Showing biometric authentication")
        
        val executor = ContextCompat.getMainExecutor(this)
        
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric authentication succeeded")
                    onAuthenticationSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "Biometric authentication error: $errorCode - $errString")
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            // 用户点击"使用密码"
                            showPasswordAuthentication()
                        }
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            // 用户取消
                            onAuthenticationCancelled()
                        }
                        else -> {
                            // 其他错误,降级到密码验证
                            showPasswordAuthentication()
                        }
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Biometric authentication failed, can retry")
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.autofill_auth_title))
            .setSubtitle(getString(R.string.autofill_auth_subtitle))
            .setDescription(getString(R.string.autofill_auth_description))
            .setNegativeButtonText(getString(R.string.use_password))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * 显示密码验证对话框
     */
    private fun showPasswordAuthentication() {
        Log.d(TAG, "Showing password authentication")
        
        // TODO: 实现密码输入对话框
        // 暂时直接返回失败
        onAuthenticationFailed("密码验证暂未实现")
    }
    
    /**
     * 验证成功,返回填充数据
     */
    private fun onAuthenticationSuccess() {
        Log.d(TAG, "Authentication success, creating dataset")
        
        try {
            if (autofillIds == null || fieldTypes == null || autofillIds!!.size != fieldTypes!!.size) {
                Log.e(TAG, "Invalid autofill data")
                onAuthenticationFailed("无效的填充数据")
                return
            }
            
            // 创建 Dataset
            val datasetBuilder = Dataset.Builder()
            
            for (i in autofillIds!!.indices) {
                val autofillId = autofillIds!![i]
                val fieldType = fieldTypes!![i]
                
                val value = when (fieldType) {
                    "username" -> usernameValue
                    "password" -> passwordValue
                    else -> null
                }
                
                if (value != null) {
                    datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
                    Log.d(TAG, "Set value for field type: $fieldType")
                }
            }
            
            val dataset = datasetBuilder.build()
            
            // 返回结果
            val replyIntent = Intent().apply {
                putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
            
            setResult(Activity.RESULT_OK, replyIntent)
            Log.d(TAG, "Dataset created and result set")
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create dataset", e)
            onAuthenticationFailed("创建填充数据失败: ${e.message}")
        }
    }
    
    /**
     * 验证失败
     */
    private fun onAuthenticationFailed(message: String) {
        Log.d(TAG, "Authentication failed: $message")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
    
    /**
     * 用户取消验证
     */
    private fun onAuthenticationCancelled() {
        Log.d(TAG, "Authentication cancelled by user")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
