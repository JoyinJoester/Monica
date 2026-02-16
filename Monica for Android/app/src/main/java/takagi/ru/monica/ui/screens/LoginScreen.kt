package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.PasswordVerificationContent
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

@Composable
fun LoginScreen(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onLoginSuccess: () -> Unit,
    onForgotPassword: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val isFirstTime = !viewModel.isMasterPasswordSet()
    
    // 获取设置
    val settings = settingsViewModel?.settings?.collectAsState()?.value
    val disablePasswordVerification = settings?.disablePasswordVerification ?: false
    val biometricEnabled = settings?.biometricEnabled ?: false
    
    PasswordVerificationContent(
        isFirstTime = isFirstTime,
        disablePasswordVerification = disablePasswordVerification,
        biometricEnabled = biometricEnabled,
        onVerifyPassword = { password -> 
            viewModel.authenticate(password)
        },
        onSetPassword = { password ->
            viewModel.setMasterPassword(password)
        },
        onSuccess = {
            if (!isFirstTime) {
                // 如果是验证成功（非首次设置），可以显示一个提示
                // 但通常登录成功直接跳转，不需要Toast干扰，除非是生物识别
                // 这里为了保持原有逻辑，我们只在生物识别成功时显示Toast？
                // PasswordVerificationContent 内部如果不处理 Toast，这里也不容易区分是密码还是生物识别。
                // 不过通常登录成功不需要 Toast。
            }
            onLoginSuccess()
        },
        onForgotPassword = onForgotPassword
    )
}
