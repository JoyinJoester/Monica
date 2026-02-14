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
                android.util.Log.d("AutofillSave", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.d("AutofillSave", "💾 开始密码保存流程")
                android.util.Log.d("AutofillSave", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: ""
                val appName = getAppName(requireContext(), packageName)
                
                // Step 1: 加密密码
                android.util.Log.d("AutofillSave", "1️⃣ 开始加密密码...")
                val securityManager = takagi.ru.monica.security.SecurityManager(requireContext())
                val encryptedPassword = securityManager.encryptData(password)
                android.util.Log.d("AutofillSave", "   ✅ 加密成功: ${encryptedPassword.length} bytes")
                
                android.util.Log.d("AutofillSave", "")
                android.util.Log.d("AutofillSave", "📱 密码信息:")
                android.util.Log.d("AutofillSave", "  - Username: $username")
                android.util.Log.d("AutofillSave", "  - Password: ${password.length} chars")
                android.util.Log.d("AutofillSave", "  - Website: $website")
                android.util.Log.d("AutofillSave", "  - PackageName: $packageName")
                android.util.Log.d("AutofillSave", "  - AppName: $appName")
                android.util.Log.d("AutofillSave", "")
                
                // Step 2: 检查现有密码
                android.util.Log.d("AutofillSave", "2️⃣ 检查现有密码...")
                val existingPasswords = passwordRepository.getAllPasswordEntries().first()
                android.util.Log.d("AutofillSave", "   📊 现有密码数量: ${existingPasswords.size}")
                android.util.Log.d("AutofillSave", "   🔓 无数量上限!")
                
                // 使用 PasswordSaveHelper 检测重复
                val saveData = PasswordSaveHelper.SaveData(
                    username = username,
                    password = password,
                    packageName = packageName,
                    webDomain = website.takeIf { it.isNotBlank() }
                )
                
                android.util.Log.d("AutofillSave", "")
                android.util.Log.d("AutofillSave", "🔍 SaveData:")
                android.util.Log.d("AutofillSave", "  - packageName: ${saveData.packageName}")
                android.util.Log.d("AutofillSave", "  - webDomain: ${saveData.webDomain}")
                android.util.Log.d("AutofillSave", "")
                
                // Step 3: 检查重复
                android.util.Log.d("AutofillSave", "3️⃣ 检查重复密码...")
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
                        android.util.Log.d("AutofillSave", "   📝 发现相同用户名,更新密码")
                        // 更新现有密码
                        val updated = PasswordSaveHelper.updatePasswordEntry(
                            duplicateCheck.existingEntry,
                            saveData,
                            encryptedPassword
                        )
                        android.util.Log.d("AutofillSave", "")
                        android.util.Log.d("AutofillSave", "4️⃣ 更新数据库...")
                        passwordRepository.updatePasswordEntry(updated)
                        android.util.Log.i("AutofillSave", "   ✅ 更新密码成功! ID=${updated.id}")
                        
                        // 记录更新操作到时间轴
                        takagi.ru.monica.utils.OperationLogger.logUpdate(
                            itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                            itemId = updated.id,
                            itemTitle = updated.title,
                            changes = listOf(takagi.ru.monica.utils.FieldChange("密码", "***", "***"))
                        )
                    }
                    is PasswordSaveHelper.DuplicateCheckResult.ExactDuplicate -> {
                        android.util.Log.i("AutofillSave", "   ⏭️  密码完全相同,跳过保存")
                    }
                    else -> {
                        android.util.Log.d("AutofillSave", "   ✨ 新密码,准备创建")
                        
                        // Step 4: 创建新密码条目
                        android.util.Log.d("AutofillSave", "")
                        android.util.Log.d("AutofillSave", "4️⃣ 创建新密码条目...")
                        val newEntry = PasswordSaveHelper.createNewPasswordEntry(
                            requireContext(),
                            saveData,
                            encryptedPassword
                        )
                        
                        android.util.Log.i("AutofillSave", "   💾 新密码条目:")
                        android.util.Log.i("AutofillSave", "      - Title: ${newEntry.title}")
                        android.util.Log.i("AutofillSave", "      - Username: ${newEntry.username}")
                        android.util.Log.i("AutofillSave", "      - Website: ${newEntry.website}")
                        android.util.Log.i("AutofillSave", "      - AppPackageName: ${newEntry.appPackageName}")
                        android.util.Log.i("AutofillSave", "      - AppName: ${newEntry.appName}")
                        
                        // Step 5: 插入数据库
                        android.util.Log.d("AutofillSave", "")
                        android.util.Log.d("AutofillSave", "5️⃣ 插入数据库...")
                        val newId = passwordRepository.insertPasswordEntry(newEntry)
                        android.util.Log.i("AutofillSave", "   ✅ 数据库插入成功! 新ID=$newId")
                        
                        // 记录创建操作到时间轴
                        takagi.ru.monica.utils.OperationLogger.logCreate(
                            itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                            itemId = newId,
                            itemTitle = newEntry.title
                        )
                        
                        // Step 6: 验证保存
                        android.util.Log.d("AutofillSave", "")
                        android.util.Log.d("AutofillSave", "6️⃣ 验证保存结果...")
                        val saved = passwordRepository.getPasswordEntryById(newId)
                        if (saved != null) {
                            android.util.Log.i("AutofillSave", "   ✅ 验证成功! 密码已正确保存到数据库")
                            android.util.Log.i("AutofillSave", "   📊 验证: Title=${saved.title}, Username=${saved.username}")
                        } else {
                            android.util.Log.e("AutofillSave", "   ❌ 验证失败! 数据库中找不到刚保存的密码!")
                        }
                        
                        android.util.Log.i("AutofillSave", "")
                        android.util.Log.i("AutofillSave", "✅✅✅ 保存新密码成功! ✅✅✅")
                    }
                }
                
                android.util.Log.d("AutofillSave", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.d("AutofillSave", "💚 密码保存流程完成")
                android.util.Log.d("AutofillSave", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                onSaveCallback?.invoke()
                dismiss()
            } catch (e: Exception) {
                android.util.Log.e("AutofillSave", "")
                android.util.Log.e("AutofillSave", "❌❌❌ 保存密码失败! ❌❌❌")
                android.util.Log.e("AutofillSave", "错误类型: ${e.javaClass.simpleName}")
                android.util.Log.e("AutofillSave", "错误信息: ${e.message}")
                android.util.Log.e("AutofillSave", "完整堆栈:", e)
                // TODO: 显示错误消息
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
    
    // 优先使用应用名称,其次使用website
    val defaultTitle = appName.ifBlank { website.takeIf { it.isNotBlank() } ?: packageName }
    
    var title by remember { mutableStateOf(defaultTitle) }
    var editedUsername by remember { mutableStateOf(username) }
    var editedPassword by remember { mutableStateOf(password) }
    var showAdvanced by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) } // 🔧 密码可见性状态
    
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
                    Row {
                        // 👁️ 显示/隐藏密码按钮
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) 
                                    stringResource(R.string.hide_password) 
                                else 
                                    stringResource(R.string.show_password),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 🔄 密码生成器按钮
                        IconButton(
                            onClick = {
                                editedPassword = generateStrongPassword()
                                passwordVisible = true // 生成后自动显示密码
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.generate_password),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                visualTransformation = if (passwordVisible) 
                    androidx.compose.ui.text.input.VisualTransformation.None 
                else 
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
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
                        android.util.Log.w("AutofillSave", "🔘🔘🔘 保存按钮被点击! 🔘🔘🔘")
                        android.util.Log.d("AutofillSave", "准备调用 onSave 回调...")
                        android.util.Log.d("AutofillSave", "  参数:")
                        android.util.Log.d("AutofillSave", "    - title: $title")
                        android.util.Log.d("AutofillSave", "    - username: $editedUsername")
                        android.util.Log.d("AutofillSave", "    - password: ${editedPassword.length} chars")
                        android.util.Log.d("AutofillSave", "    - website: $website")
                        
                        try {
                            onSave(title, editedUsername, editedPassword, website, context.getString(R.string.autofill_saved_via))
                            android.util.Log.d("AutofillSave", "✅ onSave 回调执行完成")
                        } catch (e: Exception) {
                            android.util.Log.e("AutofillSave", "❌ onSave 回调执行失败!", e)
                        }
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
