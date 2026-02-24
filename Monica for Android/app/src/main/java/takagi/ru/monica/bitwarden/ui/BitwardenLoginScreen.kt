package takagi.ru.monica.bitwarden.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import takagi.ru.monica.bitwarden.api.BitwardenApiFactory
import takagi.ru.monica.bitwarden.service.BitwardenAuthService
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel

private enum class BitwardenServerPreset(val label: String) {
    US("美国官方"),
    EU("欧洲官方"),
    SELF_HOSTED("自托管")
}

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
    var selectedServerPresetName by rememberSaveable { mutableStateOf(BitwardenServerPreset.US.name) }
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var masterPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val selectedServerPreset = runCatching {
        BitwardenServerPreset.valueOf(selectedServerPresetName)
    }.getOrElse { BitwardenServerPreset.US }
    
    // 两步验证状态
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var twoFactorCode by remember { mutableStateOf("") }
    var selectedTwoFactorMethod by remember { mutableStateOf(0) }
    var availableTwoFactorMethods by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaResponse by remember { mutableStateOf("") }
    var captchaMessage by remember { mutableStateOf("需要验证码，请输入 Captcha response") }
    var captchaForTwoFactor by remember { mutableStateOf(false) }
    var captchaSiteKey by remember { mutableStateOf<String?>(null) }
    var showCaptchaWebView by remember { mutableStateOf(false) }

    fun resolveServerUrlForLogin(): String? {
        return when (selectedServerPreset) {
            BitwardenServerPreset.US -> BitwardenApiFactory.OFFICIAL_VAULT_URL
            BitwardenServerPreset.EU -> BitwardenApiFactory.OFFICIAL_EU_VAULT_URL
            BitwardenServerPreset.SELF_HOSTED -> serverUrl.trim().takeIf { it.isNotBlank() }
        }
    }

    fun submitPrimaryLogin(captcha: String? = null) {
        val normalizedEmail = email.trim()
        val resolvedServerUrl = resolveServerUrlForLogin()
        if (normalizedEmail.isBlank() || masterPassword.isBlank()) return
        if (selectedServerPreset == BitwardenServerPreset.SELF_HOSTED && resolvedServerUrl.isNullOrBlank()) return
        viewModel.login(
            resolvedServerUrl,
            normalizedEmail,
            masterPassword,
            captchaResponse = captcha
        )
    }

    fun submitTwoFactorLogin(captcha: String? = null) {
        if (twoFactorCode.isBlank()) return
        viewModel.loginWithTwoFactor(
            twoFactorCode = twoFactorCode,
            twoFactorMethod = selectedTwoFactorMethod,
            captchaResponse = captcha
        )
    }
    
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
                is BitwardenViewModel.BitwardenEvent.ShowCaptchaDialog -> {
                    captchaForTwoFactor = event.forTwoFactor
                    captchaMessage = event.message
                    captchaSiteKey = event.siteKey
                    showCaptchaDialog = true
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
                    onValueChange = { email = it.trim() },
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
                            submitPrimaryLogin()
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = serverMenuExpanded,
                    onExpandedChange = { serverMenuExpanded = !serverMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedServerPreset.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("服务器") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = serverMenuExpanded,
                        onDismissRequest = { serverMenuExpanded = false }
                    ) {
                        BitwardenServerPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    selectedServerPresetName = preset.name
                                    serverMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = selectedServerPreset == BitwardenServerPreset.SELF_HOSTED,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it.trim() },
                            label = { Text("自托管服务器 URL") },
                            placeholder = { Text("https://vault.example.com") },
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
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 登录按钮
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        submitPrimaryLogin()
                    },
                    enabled = loginState !is BitwardenViewModel.LoginState.Loading 
                            && email.trim().isNotBlank() 
                            && masterPassword.isNotBlank()
                            && (selectedServerPreset != BitwardenServerPreset.SELF_HOSTED || serverUrl.trim().isNotBlank()),
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
                submitTwoFactorLogin()
            },
            onDismiss = {
                showTwoFactorDialog = false
                twoFactorCode = ""
                viewModel.resetLoginState()
            }
        )
    }

    if (showCaptchaDialog) {
        AlertDialog(
            onDismissRequest = {
                showCaptchaDialog = false
                captchaResponse = ""
            },
            icon = {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("需要 Captcha 验证") },
            text = {
                Column {
                    Text(
                        text = captchaMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!captchaSiteKey.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showCaptchaWebView = true }
                        ) {
                            Text("自动完成 Captcha（实验）")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = captchaResponse,
                        onValueChange = { captchaResponse = it },
                        label = { Text("Captcha response") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (captchaResponse.isNotBlank()) {
                                    if (captchaForTwoFactor) {
                                        submitTwoFactorLogin(captchaResponse)
                                    } else {
                                        submitPrimaryLogin(captchaResponse)
                                    }
                                    showCaptchaDialog = false
                                    captchaResponse = ""
                                }
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (captchaForTwoFactor) {
                            submitTwoFactorLogin(captchaResponse)
                        } else {
                            submitPrimaryLogin(captchaResponse)
                        }
                        showCaptchaDialog = false
                        captchaResponse = ""
                    },
                    enabled = captchaResponse.isNotBlank()
                ) {
                    Text("提交")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCaptchaDialog = false
                        captchaResponse = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showCaptchaWebView && !captchaSiteKey.isNullOrBlank()) {
        val effectiveVaultUrl = resolveServerUrlForLogin()
            ?: BitwardenApiFactory.OFFICIAL_VAULT_URL
        CaptchaWebViewDialog(
            siteKey = captchaSiteKey!!,
            baseUrl = effectiveVaultUrl,
            onToken = { token ->
                captchaResponse = token
                showCaptchaWebView = false
                showCaptchaDialog = false
                if (captchaForTwoFactor) {
                    submitTwoFactorLogin(token)
                } else {
                    submitPrimaryLogin(token)
                }
            },
            onError = { message ->
                captchaMessage = "自动 Captcha 失败：$message，请改为手动输入。"
                showCaptchaWebView = false
            },
            onDismiss = { showCaptchaWebView = false }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebViewDialog(
    siteKey: String,
    baseUrl: String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Captcha 验证") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val bridge = object {
                            @JavascriptInterface
                            fun onToken(token: String?) {
                                val value = token?.trim().orEmpty()
                                if (value.isNotBlank()) {
                                    onToken(value)
                                } else {
                                    onError("empty token")
                                }
                            }

                            @JavascriptInterface
                            fun onError(error: String?) {
                                onError(error ?: "unknown error")
                            }
                        }

                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            addJavascriptInterface(bridge, "CaptchaBridge")

                            val html = """
                                <!doctype html>
                                <html>
                                <head>
                                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                                  <script src="https://js.hcaptcha.com/1/api.js" async defer></script>
                                </head>
                                <body style="margin:0;padding:16px;font-family:sans-serif;">
                                  <div class="h-captcha"
                                       data-sitekey="$siteKey"
                                       data-callback="onCaptchaSuccess"
                                       data-error-callback="onCaptchaError"></div>
                                  <script>
                                    function onCaptchaSuccess(token) {
                                      CaptchaBridge.onToken(token);
                                    }
                                    function onCaptchaError() {
                                      CaptchaBridge.onError("widget error");
                                    }
                                  </script>
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL(
                                if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
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
        6 -> "组织 Duo"
        7 -> "WebAuthn"
        BitwardenAuthService.TWO_FACTOR_EMAIL_NEW_DEVICE -> "新设备邮箱验证"
        else -> "未知方式"
    }
}
