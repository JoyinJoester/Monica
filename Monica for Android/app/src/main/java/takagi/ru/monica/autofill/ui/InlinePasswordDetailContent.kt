package takagi.ru.monica.autofill.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

/**
 * 内嵌密码详情内容
 * 
 * 在自动填充场景中使用的简化版密码详情页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlinePasswordDetailContent(
    password: PasswordEntry,
    securityManager: SecurityManager,
    onAutofill: () -> Unit,
    onAutofillAndSaveUri: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    
    // 解密数据
    val decryptedUsername = remember(password) {
        try {
            if (password.username.contains("==") && password.username.length > 20) {
                securityManager.decryptData(password.username)
            } else {
                password.username
            }
        } catch (e: Exception) {
            password.username
        }
    }
    
    val decryptedPassword = remember(password) {
        try {
            securityManager.decryptData(password.password)
        } catch (e: Exception) {
            "********"
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部返回按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "密码详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
        
        // 标题卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 首字母头像
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = password.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = password.title.ifEmpty { decryptedUsername },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (password.website.isNotEmpty()) {
                        Text(
                            text = password.website,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        // 用户名字段
        DetailField(
            label = "用户名",
            value = decryptedUsername,
            icon = Icons.Outlined.Person,
            onCopy = { copyToClipboard(context, "用户名", decryptedUsername) }
        )
        
        // 密码字段
        DetailField(
            label = "密码",
            value = decryptedPassword,
            icon = Icons.Outlined.Lock,
            isPassword = true,
            passwordVisible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible },
            onCopy = { copyToClipboard(context, "密码", decryptedPassword) }
        )
        
        // 网站字段
        if (password.website.isNotEmpty()) {
            DetailField(
                label = "网站",
                value = password.website,
                icon = Icons.Outlined.Language,
                onCopy = { copyToClipboard(context, "网站", password.website) }
            )
        }
        
        // App 信息
        if (!password.appPackageName.isNullOrEmpty()) {
            DetailField(
                label = "关联应用",
                value = password.appName ?: password.appPackageName,
                icon = Icons.Outlined.Android,
                onCopy = null
            )
        }
        
        // 备注
        if (password.notes.isNotEmpty()) {
            DetailField(
                label = "备注",
                value = password.notes,
                icon = Icons.Outlined.Notes,
                onCopy = { copyToClipboard(context, "备注", password.notes) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 操作按钮
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAutofill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("自动填充")
            }
            
            OutlinedButton(
                onClick = onAutofillAndSaveUri,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("自动填充并保存URI")
            }
        }
    }
}

/**
 * 详情字段组件
 */
@Composable
private fun DetailField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onCopy: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val displayValue = if (isPassword && !passwordVisible) {
                    "••••••••"
                } else {
                    value
                }
                
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (label == "备注") 5 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 密码可见性切换
            if (isPassword && onToggleVisibility != null) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "隐藏" else "显示"
                    )
                }
            }
            
            // 复制按钮
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制"
                    )
                }
            }
        }
    }
}

/**
 * 复制到剪贴板
 */
private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "${label}已复制", Toast.LENGTH_SHORT).show()
}
