package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.ui.theme.MonicaTheme
import java.util.Date

/**
 * 自动填充保存密码Activity
 * 当用户提交表单时，显示对话框询问是否保存密码
 */
class AutofillSaveActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_WEBSITE = "extra_website"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
    
    private lateinit var passwordRepository: PasswordRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 Repository
        val database = PasswordDatabase.getDatabase(applicationContext)
        passwordRepository = PasswordRepository(database.passwordEntryDao())
        
        // 获取传递的数据
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val website = intent.getStringExtra(EXTRA_WEBSITE) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        
        setContent {
            MonicaTheme {
                SavePasswordDialog(
                    username = username,
                    password = password,
                    website = website,
                    packageName = packageName,
                    onSave = { title, updatedUsername, updatedPassword, updatedWebsite, note ->
                        savePassword(title, updatedUsername, updatedPassword, updatedWebsite, note)
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onNeverForThisSite = {
                        // TODO: 实现"从不为此网站保存"功能
                        setResult(RESULT_CANCELED)
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
        notes: String
    ) {
        lifecycleScope.launch {
            try {
                // 获取包名
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                
                // 获取应用名称
                val appName = try {
                    if (packageName.isNotBlank()) {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    ""
                }
                
                // 检查是否已存在相同的密码
                val existingPasswords = passwordRepository.getAllPasswordEntries().first()
                val existing = existingPasswords.firstOrNull { entry ->
                    // 优先匹配包名
                    if (packageName.isNotBlank() && entry.appPackageName == packageName && 
                        entry.username.equals(username, ignoreCase = true)) {
                        true
                    }
                    // 其次匹配website
                    else {
                        entry.website.equals(website, ignoreCase = true) && 
                        entry.username.equals(username, ignoreCase = true)
                    }
                }
                
                if (existing != null) {
                    // 更新现有密码
                    val updated = existing.copy(
                        password = password,
                        notes = notes,
                        appPackageName = packageName.ifBlank { existing.appPackageName },
                        appName = appName.ifBlank { existing.appName },
                        updatedAt = Date()
                    )
                    passwordRepository.updatePasswordEntry(updated)
                    
                    // 记录更新操作
                    takagi.ru.monica.utils.OperationLogger.logUpdate(
                        itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                        itemId = updated.id,
                        itemTitle = updated.title,
                        changes = listOf(takagi.ru.monica.utils.FieldChange("密码", "***", "***"))
                    )
                } else {
                    // 创建新密码条目
                    val newEntry = PasswordEntry(
                        title = title.ifBlank { appName.ifBlank { website } },
                        username = username,
                        password = password,
                        website = website,
                        notes = notes,
                        appPackageName = packageName,
                        appName = appName,
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                    val newId = passwordRepository.insertPasswordEntry(newEntry)
                    
                    // 记录创建操作
                    takagi.ru.monica.utils.OperationLogger.logCreate(
                        itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                        itemId = newId,
                        itemTitle = newEntry.title
                    )
                }
                
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("AutofillSave", "Error saving password", e)
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePasswordDialog(
    username: String,
    password: String,
    website: String,
    packageName: String,
    onSave: (title: String, username: String, password: String, website: String, notes: String) -> Unit,
    onCancel: () -> Unit,
    onNeverForThisSite: () -> Unit
) {
    val context = LocalContext.current
    val defaultNotes = stringResource(R.string.autofill_saved_via)
    
    // 获取应用名称
    val appName = remember(packageName) {
        try {
            if (packageName.isNotBlank()) {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    // 优先使用应用名称，其次使用website
    val defaultTitle = appName.ifBlank { website.takeIf { it.isNotBlank() } ?: packageName }
    
    var title by remember { mutableStateOf(defaultTitle) }
    var editedUsername by remember { mutableStateOf(username) }
    var editedPassword by remember { mutableStateOf(password) }
    var editedWebsite by remember { mutableStateOf(website) }
    var notes by remember { mutableStateOf(defaultNotes) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 标题区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.autofill_save_password),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.autofill_save_to_monica),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Divider()
                    
                    // 基本信息
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.autofill_title)) },
                        leadingIcon = {
                            Icon(Icons.Default.Title, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = editedUsername,
                        onValueChange = { editedUsername = it },
                        label = { Text(stringResource(R.string.autofill_username)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = editedPassword,
                        onValueChange = { editedPassword = it },
                        label = { Text(stringResource(R.string.autofill_password)) },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // 高级选项
                    TextButton(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (showAdvancedOptions) 
                                Icons.Default.ExpandLess 
                            else 
                                Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (showAdvancedOptions) 
                                stringResource(R.string.autofill_hide_advanced)
                            else 
                                stringResource(R.string.autofill_show_advanced)
                        )
                    }
                    
                    if (showAdvancedOptions) {
                        OutlinedTextField(
                            value = editedWebsite,
                            onValueChange = { editedWebsite = it },
                            label = { Text(stringResource(R.string.autofill_website_app)) },
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.autofill_notes)) },
                            leadingIcon = {
                                Icon(Icons.Default.Note, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    Divider()
                    
                    // 操作按钮
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 保存按钮
                        Button(
                            onClick = {
                                onSave(
                                    title,
                                    editedUsername,
                                    editedPassword,
                                    editedWebsite,
                                    notes
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.autofill_save_button), style = MaterialTheme.typography.titleMedium)
                        }
                        
                        // 取消按钮
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.autofill_cancel), style = MaterialTheme.typography.titleMedium)
                        }
                        
                        // 从不为此网站保存
                        TextButton(
                            onClick = onNeverForThisSite,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.autofill_never_for_site))
                        }
                    }
                }
            }
        }
    }
}
