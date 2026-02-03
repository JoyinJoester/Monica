package takagi.ru.monica.bitwarden.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.bitwarden.service.BitwardenAuthService
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel

/**
 * Bitwarden 登录界面
 * 
 * 支持：
 * - 官方服务器和自托管服务器
 * - 邮箱 + 主密码登录
 * - 两步验证（TOTP、Email、Authenticator 等）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitwardenLoginScreen(
    viewModel: BitwardenViewModel,
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val loginState by viewModel.loginState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // 表单状态
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var masterPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    
    // 两步验证状态
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var twoFactorCode by remember { mutableStateOf("") }
    var selectedTwoFactorMethod by remember { mutableStateOf(0) }
    var availableTwoFactorMethods by remember { mutableStateOf<List<Int>>(emptyList()) }
    
    // 监听事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BitwardenViewModel.BitwardenEvent.ShowTwoFactorDialog -> {
                    availableTwoFactorMethods = event.methods
                    selectedTwoFactorMethod = event.methods.firstOrNull() ?: 0
                    showTwoFactorDialog = true
                }
                is BitwardenViewModel.BitwardenEvent.NavigateToVault -> {
                    onLoginSuccess()
                }
                else -> {}
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录 Bitwarden") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo 和标题
                Spacer(modifier = Modifier.height(24.dp))
                
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "连接到 Bitwarden",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    text = "登录后可同步您的 Bitwarden 密码库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 邮箱输入
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱地址") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 主密码输入
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = { Text("主密码") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (email.isNotBlank() && masterPassword.isNotBlank()) {
                                viewModel.login(serverUrl.takeIf { it.isNotBlank() }, email, masterPassword)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 高级选项展开按钮
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("高级选项")
                }
                
                // 高级选项（自托管服务器）
                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("服务器 URL（可选）") },
                            placeholder = { Text("https://vault.bitwarden.com") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Cloud, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = "留空则使用官方 Bitwarden 服务器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 登录按钮
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.login(serverUrl.takeIf { it.isNotBlank() }, email, masterPassword)
                    },
                    enabled = loginState !is BitwardenViewModel.LoginState.Loading 
                            && email.isNotBlank() 
                            && masterPassword.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (loginState is BitwardenViewModel.LoginState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录")
                    }
                }
                
                // 错误信息
                if (loginState is BitwardenViewModel.LoginState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = (loginState as BitwardenViewModel.LoginState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 安全提示
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "安全说明",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "您的主密码不会被存储。Monica 使用与 Bitwarden 相同的加密标准来保护您的数据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // 两步验证对话框
    if (showTwoFactorDialog) {
        TwoFactorDialog(
            availableMethods = availableTwoFactorMethods,
            selectedMethod = selectedTwoFactorMethod,
            onMethodSelected = { selectedTwoFactorMethod = it },
            code = twoFactorCode,
            onCodeChange = { twoFactorCode = it },
            onConfirm = {
                showTwoFactorDialog = false
                viewModel.loginWithTwoFactor(
                    serverUrl.takeIf { it.isNotBlank() },
                    email,
                    masterPassword,
                    twoFactorCode,
                    selectedTwoFactorMethod
                )
                twoFactorCode = ""
            },
            onDismiss = {
                showTwoFactorDialog = false
                twoFactorCode = ""
                viewModel.resetLoginState()
            }
        )
    }
}

/**
 * 两步验证对话框
 */
@Composable
fun TwoFactorDialog(
    availableMethods: List<Int>,
    selectedMethod: Int,
    onMethodSelected: (Int) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("两步验证")
        },
        text = {
            Column {
                val isNewDevice = selectedMethod == BitwardenAuthService.TWO_FACTOR_EMAIL_NEW_DEVICE
                Text(
                    text = if (isNewDevice) "请输入邮箱中的新设备验证码" else "请输入您的验证码完成登录",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 验证方式选择（如果有多种）
                if (availableMethods.size > 1) {
                    Text(
                        text = "验证方式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    availableMethods.forEach { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedMethod == method,
                                onClick = { onMethodSelected(method) }
                            )
                            Text(
                                text = getTwoFactorMethodName(method),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(if (isNewDevice) "新设备验证码" else "验证码") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (code.isNotBlank()) onConfirm() }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = code.isNotBlank()
            ) {
                Text("验证")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 获取两步验证方式名称
 */
private fun getTwoFactorMethodName(method: Int): String {
    return when (method) {
        0 -> "验证器应用 (TOTP)"
        1 -> "邮箱验证码"
        2 -> "Duo Security"
        3 -> "YubiKey"
        4 -> "U2F 安全密钥"
        5 -> "记住设备"
        6 -> "WebAuthn"
        BitwardenAuthService.TWO_FACTOR_EMAIL_NEW_DEVICE -> "新设备邮箱验证"
        else -> "未知方式"
    }
}
