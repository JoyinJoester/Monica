package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

/**
 * 修改主密码页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ChangePasswordScreen(
    onNavigateBack: () -> Unit,
    onPasswordChanged: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    var sharedModifier: Modifier = Modifier
    if (false && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "reset_password_card"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }
    
    // 获取所有需要的字符串资源
    val errorCurrentEmpty = stringResource(R.string.change_password_error_current_empty)
    val errorNewEmpty = stringResource(R.string.change_password_error_new_empty)
    val errorNewTooShort = stringResource(R.string.change_password_error_new_too_short)
    val errorConfirmEmpty = stringResource(R.string.change_password_error_confirm_empty)
    val errorNotMatch = stringResource(R.string.change_password_error_not_match)
    val errorSameAsCurrent = stringResource(R.string.change_password_error_same_as_current)
    
    Scaffold(
        modifier = sharedModifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_password_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.return_text))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 警告卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column {
                        Text(
                            stringResource(R.string.change_password_warning_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.change_password_warning_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 当前密码
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { input ->
                    // 只允许数字输入
                    if (input.all { it.isDigit() }) {
                        currentPassword = input
                        errorMessage = ""
                    } else if (input.isNotEmpty()) {
                        // 检测到非数字,显示错误
                        errorMessage = context.getString(R.string.error_password_must_be_numeric)
                    }
                },
                label = { Text(stringResource(R.string.change_password_current)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                        Icon(
                            if (currentPasswordVisible) Icons.Default.Visibility 
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (currentPasswordVisible) 
                                stringResource(R.string.hide_password_desc) 
                            else 
                                stringResource(R.string.show_password_desc)
                        )
                    }
                },
                visualTransformation = if (currentPasswordVisible) 
                    VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage.isNotEmpty()
            )
            
            // 新密码
            OutlinedTextField(
                value = newPassword,
                onValueChange = { input ->
                    // 只允许数字输入
                    if (input.all { it.isDigit() }) {
                        newPassword = input
                        errorMessage = ""
                    } else if (input.isNotEmpty()) {
                        // 检测到非数字,显示错误
                        errorMessage = context.getString(R.string.error_password_must_be_numeric)
                    }
                },
                label = { Text(stringResource(R.string.change_password_new)) },
                leadingIcon = {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.Visibility 
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (newPasswordVisible) 
                                stringResource(R.string.hide_password_desc) 
                            else 
                                stringResource(R.string.show_password_desc)
                        )
                    }
                },
                visualTransformation = if (newPasswordVisible) 
                    VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.change_password_hint))
                }
            )
            
            // 确认新密码
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { input ->
                    // 只允许数字输入
                    if (input.all { it.isDigit() }) {
                        confirmPassword = input
                        errorMessage = ""
                    } else if (input.isNotEmpty()) {
                        // 检测到非数字,显示错误
                        errorMessage = context.getString(R.string.error_password_must_be_numeric)
                    }
                },
                label = { Text(stringResource(R.string.change_password_confirm)) },
                leadingIcon = {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.Visibility 
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) 
                                stringResource(R.string.hide_password_desc) 
                            else 
                                stringResource(R.string.show_password_desc)
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) 
                    VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage.isNotEmpty()
            )
            
            // 错误提示
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 修改密码按钮
            Button(
                onClick = {
                    when {
                        currentPassword.isEmpty() -> {
                            errorMessage = errorCurrentEmpty
                        }
                        newPassword.isEmpty() -> {
                            errorMessage = errorNewEmpty
                        }
                        confirmPassword.isEmpty() -> {
                            errorMessage = errorConfirmEmpty
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = errorNotMatch
                        }
                        newPassword.length < 4 -> {
                            errorMessage = errorNewTooShort
                        }
                        currentPassword == newPassword -> {
                            errorMessage = errorSameAsCurrent
                        }
                        else -> {
                            // 验证通过，执行修改
                            onPasswordChanged(currentPassword, newPassword)
                            showSuccessDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentPassword.isNotEmpty() && 
                         newPassword.isNotEmpty() && 
                         confirmPassword.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.change_password_button))
            }
        }
    }
    
    // 成功对话框
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.change_password_success_title)) },
            text = { Text(stringResource(R.string.change_password_success_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

