package takagi.ru.monica.autofill

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.autofill.ui.AutofillScaffold
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.autofill.ui.components.*
import java.security.SecureRandom
import takagi.ru.monica.data.LocalKeePassDatabase
import java.util.Date

/**
 * 卡片风格的保存密码 Activity
 * 全屏界面，显示捕获的凭据并允许用户编辑保存
 */
class AutofillSaveTransparentActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_WEBSITE = "website"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val RESULT_SAVED = Activity.RESULT_FIRST_USER
    }
    
    private lateinit var passwordRepository: PasswordRepository
    private lateinit var securityManager: SecurityManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // 初始化
        val database = PasswordDatabase.getDatabase(applicationContext)
        passwordRepository = PasswordRepository(database.passwordEntryDao())
        securityManager = SecurityManager(applicationContext)
        
        // KeePass DAO
        val localKeePassDao = database.localKeePassDatabaseDao()
        
        // 获取传递的数据
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val website = intent.getStringExtra(EXTRA_WEBSITE) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        
        android.util.Log.d("AutofillSave", "收到保存请求: username=$username, password=${password.length}chars, package=$packageName")
        
        setContent {
            // 读取截图保护设置
            val settingsManager = takagi.ru.monica.utils.SettingsManager(applicationContext)
            val settings by settingsManager.settingsFlow.collectAsState(
                initial = takagi.ru.monica.data.AppSettings()
            )
            
            // 获取 KeePass 数据库列表
            val keepassDatabases by localKeePassDao.getAllDatabases().collectAsState(initial = emptyList())

            takagi.ru.monica.utils.ScreenshotProtection(enabled = settings.screenshotProtectionEnabled)
            
            MonicaTheme {
                KeyguardStyleSaveContent(
                    initialUsername = username,
                    initialPassword = password,
                    website = website,
                    packageName = packageName,
                    keepassDatabases = keepassDatabases,
                    onSave = { title, user, pass, dbId ->
                        savePassword(title, user, pass, website, packageName, dbId)
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onNeverSave = {
                        // TODO: 实现"从不保存"功能
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
    
    private fun savePassword(
        title: String,
        username: String,
        password: String,
        website: String,
        packageName: String,
        keepassDatabaseId: Long?
    ) {
        lifecycleScope.launch {
            try {
                // 获取应用名称
                val appName = try {
                    if (packageName.isNotBlank()) {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } else ""
                } catch (e: Exception) { "" }
                
                // 加密密码
                val encryptedPassword = securityManager.encryptData(password)
                
                // 检查重复
                val existingPasswords = passwordRepository.getAllPasswordEntries().first()
                val saveData = PasswordSaveHelper.SaveData(
                    username = username,
                    password = password,
                    packageName = packageName,
                    webDomain = website.takeIf { it.isNotBlank() },
                    keepassDatabaseId = keepassDatabaseId
                )
                
                when (
                    val duplicateCheck = PasswordSaveHelper.checkDuplicate(
                        saveData = saveData,
                        existingPasswords = existingPasswords,
                        resolvePassword = { entry ->
                            runCatching { securityManager.decryptData(entry.password) }
                                .getOrElse { entry.password }
                        }
                    )
                ) {
                    is PasswordSaveHelper.DuplicateCheckResult.SameUsernameDifferentPassword -> {
                        // 更新现有密码
                        val updated = PasswordSaveHelper.updatePasswordEntry(
                            duplicateCheck.existingEntry,
                            saveData,
                            encryptedPassword
                        )
                        passwordRepository.updatePasswordEntry(updated)
                        android.util.Log.i("AutofillSave", "更新密码成功: ${updated.title}")
                    }
                    is PasswordSaveHelper.DuplicateCheckResult.ExactDuplicate -> {
                        android.util.Log.i("AutofillSave", "密码完全相同，跳过")
                    }
                    else -> {
                        // 创建新密码
                        val newEntry = PasswordEntry(
                            title = title.ifBlank { appName.ifBlank { website } },
                            username = username,
                            password = encryptedPassword,
                            website = website,
                            notes = getString(R.string.autofill_saved_via),
                            appPackageName = packageName,
                            appName = appName,
                            keepassDatabaseId = keepassDatabaseId,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                        passwordRepository.insertPasswordEntry(newEntry)
                        android.util.Log.i("AutofillSave", "保存新密码成功: ${newEntry.title}")
                    }
                }
                
                setResult(RESULT_SAVED)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("AutofillSave", "保存失败", e)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }
}

/**
 * 卡片风格的保存密码内容
 * 与应用内部样式保持一致
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyguardStyleSaveContent(
    initialUsername: String,
    initialPassword: String,
    website: String,
    packageName: String,
    keepassDatabases: List<LocalKeePassDatabase>,
    onSave: (title: String, username: String, password: String, keepassDatabaseId: Long?) -> Unit,
    onCancel: () -> Unit,
    onNeverSave: () -> Unit
) {
    val context = LocalContext.current
    
    // 获取应用名称
    val appName = remember(packageName) {
        if (packageName.isNotBlank()) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var showVaultSelector by remember { mutableStateOf(false) }
    var keepassDatabaseId by remember { mutableStateOf<Long?>(null) }

    // 自动填充用户名（如主应用 AddEditPasswordScreen）
    LaunchedEffect(initialUsername) {
        if (username.isEmpty() && initialUsername.isNotEmpty()) {
            username = initialUsername
        }
    }
    
    // 自动生成标题
    val title = appName ?: website.extractDomainName() ?: packageName.substringAfterLast('.')
    
    AutofillScaffold(
        topBar = {
            // 简洁的顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.autofill_save_password),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) {
        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 存储位置选择器卡片
            VaultSelectorCard(
                selectedDatabase = keepassDatabases.find { it.id == keepassDatabaseId },
                onClick = { showVaultSelector = true }
            )

            // 凭据卡片（完全复用主应用逻辑）
            CredentialsCard(
                title = title,
                onTitleChange = {}, // 保存页面不允许编辑标题
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                passwordVisible = passwordVisible,
                onPasswordVisibilityChange = { passwordVisible = it },
                onGeneratePassword = { showPasswordGenerator = true }
            )

            // 密码生成器对话框（完全复用主应用逻辑）
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
            
            // 底部按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消按钮
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.autofill_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 保存按钮
                Button(
                    onClick = {
                        isSaving = true
                        onSave(title, username, password, keepassDatabaseId)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && password.isNotEmpty()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.save),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // "从不为此网站保存" 按钮
            TextButton(
                onClick = onNeverSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.autofill_never_for_site),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

