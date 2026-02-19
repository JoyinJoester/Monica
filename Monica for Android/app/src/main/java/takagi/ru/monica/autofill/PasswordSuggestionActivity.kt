package takagi.ru.monica.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.utils.PasswordStrengthCalculator
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordHistoryManager

/**
 * 密码建议对话框 Activity
 * 当用户在注册/修改密码时,Monica 检测到没有匹配的密码时显示此对话框
 * 提供智能生成的强密码建议
 */
class PasswordSuggestionActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_GENERATED_PASSWORD = "extra_generated_password"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_WEB_DOMAIN = "extra_web_domain"
        const val EXTRA_PASSWORD_FIELD_IDS = "extra_password_field_ids"
    }
    
    private var username: String = ""
    private var generatedPassword: String = ""
    private var packageName: String = ""
    private var webDomain: String? = null
    private var passwordFieldIds: ArrayList<android.view.autofill.AutofillId>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 配置窗口以保持在当前应用上方，不切换任务
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        
        // 获取传递的参数
        username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        generatedPassword = intent.getStringExtra(EXTRA_GENERATED_PASSWORD) ?: ""
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        // getParcelableArrayListExtra(String, Class) 需要 API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            passwordFieldIds = intent.getParcelableArrayListExtra(EXTRA_PASSWORD_FIELD_IDS, android.view.autofill.AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            passwordFieldIds = intent.getParcelableArrayListExtra(EXTRA_PASSWORD_FIELD_IDS)
        }
        
        AutofillLogger.i("SUGGESTION", "PasswordSuggestionActivity started for user: $username")
        AutofillLogger.i("SUGGESTION", "Package: $packageName, Domain: $webDomain")
        AutofillLogger.i("SUGGESTION", "Generated password length: ${generatedPassword.length}")
        
        setContent {
            MonicaTheme {
                PasswordSuggestionDialog(
                    username = username,
                    generatedPassword = generatedPassword,
                    packageName = packageName,
                    webDomain = webDomain,
                    onAccept = { password ->
                        acceptSuggestion(password)
                    },
                    onDismiss = {
                        dismissSuggestion()
                    }
                )
            }
        }
    }
    
    /**
     * 用户接受密码建议
     */
    private fun acceptSuggestion(password: String) {
        AutofillLogger.i("SUGGESTION", "User accepted password suggestion")
        AutofillLogger.i("SUGGESTION", "Password length: ${password.length}")
        AutofillLogger.i("SUGGESTION", "Password field IDs count: ${passwordFieldIds?.size ?: 0}")
        
        lifecycleScope.launch {
            try {
                // 保存到历史记录
                val historyManager = PasswordHistoryManager(applicationContext)
                historyManager.addHistory(
                    password = password,
                    packageName = packageName,
                    domain = webDomain ?: "",
                    username = username
                )
                AutofillLogger.i("SUGGESTION", "✅ Password saved to history")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && passwordFieldIds != null && passwordFieldIds!!.isNotEmpty()) {
                    // 🔐 构建包含建议密码的 Dataset
                    val datasetBuilder = android.service.autofill.Dataset.Builder()
                    
                    // 创建简单的 presentation (不会显示,仅用于满足 API 要求)
                    val presentation = android.widget.RemoteViews(this@PasswordSuggestionActivity.packageName, R.layout.autofill_suggestion_item)
                    presentation.setTextViewText(R.id.title, getString(R.string.password_suggestion_accept))
                    
                    // 为每个密码字段设置生成的密码值
                    passwordFieldIds!!.forEach { autofillId ->
                        try {
                            datasetBuilder.setValue(
                                autofillId,
                                android.view.autofill.AutofillValue.forText(password),
                                presentation
                            )
                            
                            AutofillLogger.d("SUGGESTION", "✓ Set password for autofillId: $autofillId")
                        } catch (e: Exception) {
                            AutofillLogger.e("SUGGESTION", "Failed to set value for autofill ID: $autofillId", e)
                        }
                    }
                    
                    // 构建 Dataset
                    val dataset = datasetBuilder.build()
                    
                    // 🎯 将 Dataset 作为认证结果返回
                    val replyIntent = Intent()
                    replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    
                    setResult(Activity.RESULT_OK, replyIntent)
                    
                    AutofillLogger.i("SUGGESTION", "✅ Password suggestion accepted and dataset returned")
                } else {
                    AutofillLogger.w("SUGGESTION", "⚠️ No autofill IDs available or Android version < O")
                    setResult(Activity.RESULT_CANCELED)
                }
                
            } catch (e: Exception) {
                AutofillLogger.e("SUGGESTION", "❌ Error accepting suggestion", e)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }
    
    /**
     * 用户拒绝密码建议
     */
    private fun dismissSuggestion() {
        AutofillLogger.i("SUGGESTION", "User dismissed password suggestion")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

/**
 * 密码建议对话框 UI 组件
 */
@Composable
fun PasswordSuggestionDialog(
    username: String,
    generatedPassword: String,
    packageName: String,
    webDomain: String?,
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf(generatedPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var showCopiedSnackbar by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    // 计算密码强度
    val passwordStrength = remember(currentPassword) {
        PasswordStrengthCalculator.calculateStrength(currentPassword)
    }
    
    // 提取应用/网站名称用于显示
    val displayName = remember(webDomain, packageName) {
        webDomain?.let { domain ->
            // 从域名提取友好名称 (例如: google.com -> Google)
            domain.removePrefix("www.")
                .substringBefore(".")
                .replaceFirstChar { it.uppercase() }
        } ?: packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 标题和图标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.password_suggestion_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.autofill_for_display_name, displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 分割线
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // 用户名显示
                if (username.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.autofill_username),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                // 生成的密码显示
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.autofill_suggested_strong_password),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showPassword) currentPassword else "•".repeat(currentPassword.length),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // 显示/隐藏密码按钮
                                    IconButton(
                                        onClick = { showPassword = !showPassword },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showPassword) {
                                                stringResource(R.string.hide_password)
                                            } else {
                                                stringResource(R.string.show_password)
                                            },
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    // 复制按钮
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(currentPassword))
                                            showCopiedSnackbar = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_password),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    // 重新生成按钮
                                    IconButton(
                                        onClick = {
                                            // 重新生成密码
                                            val generator = PasswordGenerator()
                                            currentPassword = generator.generatePassword(
                                                PasswordGenerator.PasswordOptions(
                                                    length = 16,
                                                    includeUppercase = true,
                                                    includeLowercase = true,
                                                    includeNumbers = true,
                                                    includeSymbols = true,
                                                    excludeSimilar = true
                                                )
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.regenerate),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 密码强度指示器
                            PasswordStrengthIndicator(strength = passwordStrength)
                        }
                    }
                }
                
                // 说明文字
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.autofill_save_for_next_fill),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2
                        )
                    }
                }
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.password_suggestion_decline),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    
                    // 使用密码按钮
                    Button(
                        onClick = { onAccept(currentPassword) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.password_suggestion_accept),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    // 复制成功提示
    LaunchedEffect(showCopiedSnackbar) {
        if (showCopiedSnackbar) {
            delay(2000)
            showCopiedSnackbar = false
        }
    }
    
    if (showCopiedSnackbar) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.password_suggestion_copied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

/**
 * 密码强度指示器组件
 */
@Composable
fun PasswordStrengthIndicator(strength: PasswordStrengthCalculator.PasswordStrength) {
    val weakText = stringResource(R.string.password_strength_weak)
    val fairText = stringResource(R.string.password_strength_moderate)
    val goodText = stringResource(R.string.password_strength_strong)
    val strongText = stringResource(R.string.password_strength_very_strong)
    val veryStrongText = stringResource(R.string.password_strength_very_strong)

    val strengthColor = when (strength) {
        PasswordStrengthCalculator.PasswordStrength.WEAK -> Color(0xFFEF5350)
        PasswordStrengthCalculator.PasswordStrength.FAIR -> Color(0xFFFF9800)
        PasswordStrengthCalculator.PasswordStrength.GOOD -> Color(0xFFFFC107)
        PasswordStrengthCalculator.PasswordStrength.STRONG -> Color(0xFF66BB6A)
        PasswordStrengthCalculator.PasswordStrength.VERY_STRONG -> Color(0xFF4CAF50)
    }
    
    val strengthText = when (strength) {
        PasswordStrengthCalculator.PasswordStrength.WEAK -> weakText
        PasswordStrengthCalculator.PasswordStrength.FAIR -> fairText
        PasswordStrengthCalculator.PasswordStrength.GOOD -> goodText
        PasswordStrengthCalculator.PasswordStrength.STRONG -> strongText
        PasswordStrengthCalculator.PasswordStrength.VERY_STRONG -> veryStrongText
    }
    
    val strengthProgress = when (strength) {
        PasswordStrengthCalculator.PasswordStrength.WEAK -> 0.2f
        PasswordStrengthCalculator.PasswordStrength.FAIR -> 0.4f
        PasswordStrengthCalculator.PasswordStrength.GOOD -> 0.6f
        PasswordStrengthCalculator.PasswordStrength.STRONG -> 0.8f
        PasswordStrengthCalculator.PasswordStrength.VERY_STRONG -> 1.0f
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.password_strength_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = strengthText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = strengthColor
            )
        }
        
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(strengthProgress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(strengthColor)
            )
        }
    }
}

