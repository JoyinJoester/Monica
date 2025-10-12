package takagi.ru.monica.autofill

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.ui.theme.MonicaTheme
import java.util.Date

/**
 * 底部弹出的密码保存对话框
 * 类似Google密码管理器的半屏保存体验
 * 保持原应用可见，提供更好的用户体验
 */
class AutofillSaveBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        const val ARG_USERNAME = "username"
        const val ARG_PASSWORD = "password"
        const val ARG_WEBSITE = "website"
        const val ARG_PACKAGE_NAME = "package_name"
        
        fun newInstance(
            username: String,
            password: String,
            website: String,
            packageName: String
        ): AutofillSaveBottomSheet {
            return AutofillSaveBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                    putString(ARG_PASSWORD, password)
                    putString(ARG_WEBSITE, website)
                    putString(ARG_PACKAGE_NAME, packageName)
                }
            }
        }
    }
    
    private lateinit var passwordRepository: PasswordRepository
    private var onSaveCallback: (() -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null
    
    fun setOnSaveListener(callback: () -> Unit) {
        onSaveCallback = callback
    }
    
    fun setOnDismissListener(callback: () -> Unit) {
        onDismissCallback = callback
    }
    
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化Repository
        val database = PasswordDatabase.getDatabase(requireContext())
        passwordRepository = PasswordRepository(database.passwordEntryDao())
        
        // 设置底部弹出样式
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_DayNight_BottomSheetDialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MonicaTheme {
                    SavePasswordBottomSheetContent(
                        username = arguments?.getString(ARG_USERNAME) ?: "",
                        password = arguments?.getString(ARG_PASSWORD) ?: "",
                        website = arguments?.getString(ARG_WEBSITE) ?: "",
                        packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: "",
                        onSave = { title, user, pass, site, notes ->
                            savePassword(title, user, pass, site, notes)
                        },
                        onDismiss = {
                            dismiss()
                        }
                    )
                }
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
                val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: ""
                val appName = getAppName(requireContext(), packageName)
                
                // 检查是否已存在
                val existingPasswords = passwordRepository.getAllPasswordEntries().first()
                val existing = existingPasswords.firstOrNull { entry ->
                    if (packageName.isNotBlank() && entry.appPackageName == packageName && 
                        entry.username.equals(username, ignoreCase = true)) {
                        true
                    } else {
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
                } else {
                    // 创建新密码
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
                    passwordRepository.insertPasswordEntry(newEntry)
                }
                
                onSaveCallback?.invoke()
                dismiss()
            } catch (e: Exception) {
                android.util.Log.e("AutofillSave", "Error saving password", e)
                dismiss()
            }
        }
    }
    
    private fun getAppName(context: Context, packageName: String): String {
        return try {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePasswordBottomSheetContent(
    username: String,
    password: String,
    website: String,
    packageName: String,
    onSave: (title: String, username: String, password: String, website: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 密码生成函数
    fun generateStrongPassword(): String {
        val passwordGenerator = takagi.ru.monica.utils.PasswordGenerator()
        return passwordGenerator.generatePassword(
            takagi.ru.monica.utils.PasswordGenerator.PasswordOptions(
                length = 16,
                includeUppercase = true,
                includeLowercase = true,
                includeNumbers = true,
                includeSymbols = true,
                excludeSimilar = true
            )
        )
    }
    
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
    var showAdvanced by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部拖动条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
            
            // 标题区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.autofill_save_password),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (appName.isNotBlank()) appName else website.ifBlank { packageName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            
            Divider()
            
            // 账号密码字段
            OutlinedTextField(
                value = editedUsername,
                onValueChange = { editedUsername = it },
                label = { Text(stringResource(R.string.autofill_username)) },
                leadingIcon = { 
                    Icon(Icons.Default.Person, contentDescription = null) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = editedPassword,
                onValueChange = { editedPassword = it },
                label = { Text(stringResource(R.string.autofill_password)) },
                leadingIcon = { 
                    Icon(Icons.Default.Lock, contentDescription = null) 
                },
                trailingIcon = {
                    // 密码生成器按钮
                    IconButton(
                        onClick = {
                            editedPassword = generateStrongPassword()
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.generate_password),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            // 高级选项（可折叠）
            if (showAdvanced) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.autofill_title)) },
                    leadingIcon = {
                        Icon(Icons.Default.Title, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            // 高级选项切换
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showAdvanced) 
                        stringResource(R.string.autofill_hide_advanced)
                    else 
                        stringResource(R.string.autofill_show_advanced)
                )
            }
            
            Divider()
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                
                Button(
                    onClick = {
                        onSave(title, editedUsername, editedPassword, website, "通过自动填充保存")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = editedUsername.isNotBlank() || editedPassword.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save))
                }
            }
            
            // 从不保存选项
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.autofill_never_for_site))
            }
        }
    }
}
