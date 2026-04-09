package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R
import takagi.ru.monica.security.MasterPasswordPolicy
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.utils.BiometricAuthHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private enum class PasswordVerificationBiometricAccessResult {
    PROCEED,
    REQUIRE_PASSWORD_REENTRY,
    LOCKED
}

/**
 * 统一的密码验证组件
 * 用于 LoginScreen 和 AutofillPickerActivityV2
 */
@Composable
fun PasswordVerificationContent(
    modifier: Modifier = Modifier,
    isFirstTime: Boolean = false,
    isConfirmingPassword: Boolean = false,
    disablePasswordVerification: Boolean = false,
    biometricEnabled: Boolean = false,
    persistVaultUnlockToSession: Boolean = true,
    onVerifyPassword: (String) -> Boolean,
    onSetPassword: (String) -> Unit = {},
    onSuccess: () -> Unit,
    onForgotPassword: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 内部状态，用于处理首次设置密码的确认流程
    var internalIsConfirming by remember { mutableStateOf(isConfirmingPassword) }
    
    // 生物识别帮助类
    val biometricHelper = remember { BiometricAuthHelper(context) }
    val securityManager = remember(context) { SecurityManager(context.applicationContext) }
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val isBiometricAvailable = remember { biometricHelper.isBiometricAvailable() }
    val canUseBiometric = !isFirstTime && isBiometricAvailable && biometricEnabled
    var autoBiometricTried by remember { mutableStateOf(false) }

    fun completeAuthentication() {
        if (persistVaultUnlockToSession) {
            securityManager.markVaultAuthenticated()
        }
        onSuccess()
    }

    fun canProceedAfterBiometricAuth(): PasswordVerificationBiometricAccessResult {
        if (!securityManager.isMasterPasswordSet()) return PasswordVerificationBiometricAccessResult.PROCEED
        val directUnlock = runCatching {
            securityManager.unlockVaultWithBiometric()
        }.getOrDefault(false)
        if (directUnlock) {
            android.util.Log.d("PasswordVerification", "Biometric post-check: direct unlock succeeded")
            return PasswordVerificationBiometricAccessResult.PROCEED
        }
        val autoLockMinutes = runCatching {
            runBlocking { settingsManager.settingsFlow.first().autoLockMinutes }
        }.getOrDefault(5)
        if (securityManager.canAccessVaultMaterialNow()) {
            android.util.Log.d("PasswordVerification", "Biometric post-check: vault material accessible after retry")
            return PasswordVerificationBiometricAccessResult.PROCEED
        }
        val vaultState = securityManager.getVaultAccessState(context.applicationContext, autoLockMinutes)
        if (vaultState == SecurityManager.VaultAccessState.REQUIRES_PASSWORD_REENTRY) {
            val rebuilt = securityManager.rebuildKeystoreWrapperFromRuntimeCacheIfNeeded()
            if (rebuilt) {
                android.util.Log.d("PasswordVerification", "Biometric post-check: wrapper rebuilt from runtime cache")
                return PasswordVerificationBiometricAccessResult.PROCEED
            }
            if (securityManager.isVaultRuntimeUnlocked()) {
                android.util.Log.w(
                    "PasswordVerification",
                    "Biometric post-check: wrapper rebuild failed but runtime MDK exists; allowing access to avoid lock loop"
                )
                return PasswordVerificationBiometricAccessResult.PROCEED
            }
        }
        val result = when (vaultState) {
            SecurityManager.VaultAccessState.ACCESSIBLE -> PasswordVerificationBiometricAccessResult.PROCEED
            SecurityManager.VaultAccessState.REQUIRES_PASSWORD_REENTRY -> PasswordVerificationBiometricAccessResult.REQUIRE_PASSWORD_REENTRY
            SecurityManager.VaultAccessState.LOCKED -> PasswordVerificationBiometricAccessResult.LOCKED
        }
        android.util.Log.w(
            "PasswordVerification",
            "Biometric post-check: direct unlock failed, result=$result, autoLockMinutes=$autoLockMinutes"
        )
        return result
    }

    fun updateBiometricFailureMessage(result: PasswordVerificationBiometricAccessResult) {
        errorMessage = when (result) {
            PasswordVerificationBiometricAccessResult.REQUIRE_PASSWORD_REENTRY -> {
                context.getString(R.string.biometric_requires_password_reentry)
            }
            PasswordVerificationBiometricAccessResult.LOCKED -> {
                context.getString(R.string.ime_unlock_required)
            }
            PasswordVerificationBiometricAccessResult.PROCEED -> {
                ""
            }
        }
    }
    
    // 自动触发生物识别
    LaunchedEffect(isFirstTime, isBiometricAvailable, biometricEnabled, activity) {
        if (!autoBiometricTried && canUseBiometric && activity != null) {
            autoBiometricTried = true
            biometricHelper.authenticate(
                activity = activity,
                onSuccess = {
                    when (val result = canProceedAfterBiometricAuth()) {
                        PasswordVerificationBiometricAccessResult.PROCEED -> completeAuthentication()
                        else -> updateBiometricFailureMessage(result)
                    }
                },
                onError = { _, errorMsg ->
                    errorMessage = context.getString(R.string.biometric_error, errorMsg)
                },
                onCancel = {
                    // 用户主动取消，不显示错误，停留在密码输入界面
                }
            )
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = when {
                isFirstTime && !internalIsConfirming -> 
                    stringResource(R.string.setup_master_password)
                isFirstTime && internalIsConfirming -> 
                    stringResource(R.string.confirm_master_password)
                else -> 
                    stringResource(R.string.enter_master_password)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        if (isFirstTime && !internalIsConfirming) {
            Text(
                text = stringResource(R.string.password_min_6_digits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        
        if (!isFirstTime && disablePasswordVerification) {
            Text(
                text = stringResource(R.string.developer_mode_password_disabled),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Master Password Field
        MasterPasswordTextField(
            value = if (internalIsConfirming) confirmPassword else masterPassword,
            onValueChange = { input ->
                if (internalIsConfirming) {
                    confirmPassword = input
                } else {
                    masterPassword = input
                }
                errorMessage = ""
            },
            onUnsupportedCharacterAttempt = {
                errorMessage = context.getString(R.string.error_password_contains_unsupported_characters)
            },
            label = { 
                Text(
                    if (internalIsConfirming) 
                        stringResource(R.string.confirm_master_password)
                    else 
                        stringResource(R.string.master_password)
                ) 
            },
            visible = if (internalIsConfirming) confirmPasswordVisible else passwordVisible,
            onVisibilityChange = { visible ->
                if (internalIsConfirming) {
                    confirmPasswordVisible = visible
                } else {
                    passwordVisible = visible
                }
            },
            modifier = Modifier.fillMaxWidth(),
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
                // 如果已存在主密码且关闭了密码验证,直接通过
                if (!isFirstTime && disablePasswordVerification) {
                    completeAuthentication()
                    return@Button
                }
                
                if (isFirstTime) {
                    // 首次设置密码
                    if (!internalIsConfirming) {
                        // 第一次输入，要求确认
                        internalIsConfirming = true
                    } else {
                        // 确认密码
                        if (!MasterPasswordPolicy.meetsMinLength(masterPassword)) {
                            errorMessage = context.getString(R.string.password_too_short)
                            confirmPassword = ""
                            internalIsConfirming = false
                            return@Button
                        }
                        if (masterPassword != confirmPassword) {
                            errorMessage = context.getString(R.string.error_passwords_not_match)
                            confirmPassword = ""
                            internalIsConfirming = false
                            return@Button
                        }
                        onSetPassword(masterPassword)
                        completeAuthentication()
                    }
                } else {
                    // 验证密码
                    if (onVerifyPassword(masterPassword)) {
                        completeAuthentication()
                    } else {
                        errorMessage = context.getString(R.string.error_invalid_password)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = disablePasswordVerification ||
                (if (internalIsConfirming) confirmPassword else masterPassword).isNotEmpty()
        ) {
            Text(
                text = when {
                    isFirstTime && !internalIsConfirming -> stringResource(R.string.set_up_password)
                    isFirstTime && internalIsConfirming -> stringResource(R.string.confirm)
                    else -> stringResource(R.string.unlock)
                }
            )
        }
        
        // 生物识别按钮（仅在非首次使用、生物识别可用且已启用时显示）
        if (canUseBiometric && activity != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    biometricHelper.authenticate(
                        activity = activity,
                        onSuccess = {
                            when (val result = canProceedAfterBiometricAuth()) {
                                PasswordVerificationBiometricAccessResult.PROCEED -> completeAuthentication()
                                else -> updateBiometricFailureMessage(result)
                            }
                        },
                        onError = { _, errorMsg ->
                            errorMessage = context.getString(R.string.biometric_error, errorMsg)
                        },
                        onCancel = {
                            // 用户取消
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.use_biometric),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.use_biometric))
            }
        }
        
        // Forgot password option
        if (!isFirstTime && onForgotPassword != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onForgotPassword
            ) {
                Text(
                    text = stringResource(R.string.forgot_password),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
