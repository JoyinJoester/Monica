package takagi.ru.monica.autofill.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.autofill.ui.components.*
import takagi.ru.monica.data.LocalKeePassDatabase
import java.security.SecureRandom

/**
 * 简化版内嵌添加密码内容
 * 
 * 参考软件内部 AddEditPasswordScreen 的设计风格：
 * - 存储位置选择器（仅 Monica 本地存储）
 * - 凭据卡片（标题、用户名、密码）
 * 
 * 使用 AutofillScaffold 包裹，自动适配深色主题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineAddPasswordContent(
    initialTitle: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    initialWebsite: String = "",
    initialAppPackageName: String? = null,
    initialAppName: String? = null,
    appIcon: Drawable? = null,
    keepassDatabases: List<LocalKeePassDatabase> = emptyList(),
    onSave: (PasswordEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedViaAutofill = stringResource(R.string.autofill_saved_via)
    val untitledText = stringResource(R.string.untitled)
    var title by remember { mutableStateOf(initialTitle.ifEmpty { 
        initialAppName ?: initialWebsite.extractDomainName() ?: "" 
    }) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var showVaultSelector by remember { mutableStateOf(false) }
    var keepassDatabaseId by remember { mutableStateOf<Long?>(null) }
    
    AutofillScaffold(
        packageName = initialAppPackageName,
        webDomain = initialWebsite.extractDomain(),
        appIcon = appIcon,
        appName = initialAppName,
        showClose = true,
        onClose = onCancel,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.add_password_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 存储位置选择器
            VaultSelectorCard(
                selectedDatabase = keepassDatabases.find { it.id == keepassDatabaseId },
                onClick = { showVaultSelector = true }
            )
            
            // 凭据卡片
            CredentialsCard(
                title = title,
                onTitleChange = { title = it },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                passwordVisible = passwordVisible,
                onPasswordVisibilityChange = { passwordVisible = it },
                onGeneratePassword = { showPasswordGenerator = true }
            )
            
            // 密码生成器对话框
            if (showPasswordGenerator) {
                PasswordGeneratorDialog(
                    onDismiss = { showPasswordGenerator = false },
                    onPasswordGenerated = { generatedPassword ->
                        password = generatedPassword
                        showPasswordGenerator = false
                    }
                )
            }
            
            // 存储位置选择对话框
            VaultSelector(
                keepassDatabases = keepassDatabases,
                selectedDatabaseId = keepassDatabaseId,
                onDatabaseSelected = { keepassDatabaseId = it },
                showBottomSheet = showVaultSelector,
                onDismissRequest = { showVaultSelector = false }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 保存按钮
            Button(
                onClick = {
                    isSaving = true
                    val entry = PasswordEntry(
                        title = title.ifEmpty { 
                            initialAppName ?: initialWebsite.extractDomainName() ?: untitledText
                        },
                        username = username,
                        password = password,
                        website = initialWebsite,
                        notes = savedViaAutofill,
                        appPackageName = initialAppPackageName ?: "",
                        appName = initialAppName ?: "",
                        keepassDatabaseId = keepassDatabaseId
                    )
                    onSave(entry)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isSaving && password.isNotEmpty(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// 移除本地组件定义，改用 Shared components
// Local components moved to components/PasswordFormComponents.kt
