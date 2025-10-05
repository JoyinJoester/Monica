package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onLoginSuccess: () -> Unit,
    onForgotPassword: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isConfirmingPassword by remember { mutableStateOf(false) }
    
    val isFirstTime = !viewModel.isMasterPasswordSet()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title
        Text(
            text = context.getString(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = when {
                isFirstTime && !isConfirmingPassword -> 
                    context.getString(R.string.setup_master_password)
                isFirstTime && isConfirmingPassword -> 
                    context.getString(R.string.confirm_master_password)
                else -> 
                    context.getString(R.string.enter_master_password)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        if (isFirstTime && !isConfirmingPassword) {
            Text(
                text = context.getString(R.string.password_min_6_digits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Master Password Field
        OutlinedTextField(
            value = if (isConfirmingPassword) confirmPassword else masterPassword,
            onValueChange = { input ->
                // 只允许数字输入
                if (input.all { it.isDigit() }) {
                    if (isConfirmingPassword) {
                        confirmPassword = input
                    } else {
                        masterPassword = input
                    }
                    errorMessage = ""
                } else if (input.isNotEmpty()) {
                    // 检测到非数字,显示错误
                    errorMessage = context.getString(R.string.error_password_must_be_numeric)
                }
            },
            label = { 
                Text(
                    if (isConfirmingPassword) 
                        context.getString(R.string.confirm_master_password)
                    else 
                        context.getString(R.string.master_password)
                ) 
            },
            visualTransformation = if ((isConfirmingPassword && confirmPasswordVisible) || 
                                      (!isConfirmingPassword && passwordVisible)) 
                VisualTransformation.None 
            else 
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { 
                        if (isConfirmingPassword) {
                            confirmPasswordVisible = !confirmPasswordVisible
                        } else {
                            passwordVisible = !passwordVisible
                        }
                    }
                ) {
                    Icon(
                        imageVector = if ((isConfirmingPassword && confirmPasswordVisible) || 
                                        (!isConfirmingPassword && passwordVisible)) 
                            Icons.Filled.Visibility 
                        else 
                            Icons.Filled.VisibilityOff,
                        contentDescription = if ((isConfirmingPassword && confirmPasswordVisible) || 
                                                (!isConfirmingPassword && passwordVisible)) 
                            context.getString(R.string.hide_password) 
                        else 
                            context.getString(R.string.show_password)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
        
        // Error Message
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Login/Setup Button
        Button(
            onClick = {
                handleLogin(
                    context = context,
                    viewModel = viewModel,
                    masterPassword = masterPassword,
                    confirmPassword = confirmPassword,
                    isFirstTime = isFirstTime,
                    isConfirmingPassword = isConfirmingPassword,
                    onSuccess = onLoginSuccess,
                    onError = { error -> errorMessage = error },
                    onNeedConfirm = { isConfirmingPassword = true },
                    onResetConfirm = { 
                        confirmPassword = ""
                        isConfirmingPassword = false
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (if (isConfirmingPassword) confirmPassword else masterPassword).isNotEmpty()
        ) {
            Text(
                text = when {
                    isFirstTime && !isConfirmingPassword -> context.getString(R.string.set_up_password)
                    isFirstTime && isConfirmingPassword -> context.getString(R.string.confirm)
                    else -> context.getString(R.string.unlock)
                }
            )
        }
        
        // Forgot password option
        if (!isFirstTime && onForgotPassword != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onForgotPassword
            ) {
                Text(
                    text = context.getString(R.string.forgot_password),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 处理登录逻辑
 */
private fun handleLogin(
    context: Context,
    viewModel: PasswordViewModel,
    masterPassword: String,
    confirmPassword: String,
    isFirstTime: Boolean,
    isConfirmingPassword: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onNeedConfirm: () -> Unit,
    onResetConfirm: () -> Unit
) {
    // 检查密码长度
    if (masterPassword.length < 6) {
        onError(context.getString(R.string.error_password_min_6_digits))
        return
    }
    
    if (isFirstTime) {
        // 首次设置密码
        if (!isConfirmingPassword) {
            // 第一次输入，要求确认
            onNeedConfirm()
        } else {
            // 确认密码
            if (masterPassword != confirmPassword) {
                onError(context.getString(R.string.error_passwords_not_match))
                onResetConfirm()
                return
            }
            viewModel.setMasterPassword(masterPassword)
            onSuccess()
        }
    } else {
        // 验证密码
        if (viewModel.authenticate(masterPassword)) {
            onSuccess()
        } else {
            onError(context.getString(R.string.error_invalid_password))
        }
    }
}
