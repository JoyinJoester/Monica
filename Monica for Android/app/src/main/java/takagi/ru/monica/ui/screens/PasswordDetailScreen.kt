package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.ui.components.ActionStrip
import takagi.ru.monica.ui.components.ActionStripItem
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.components.MasterPasswordDialog
import takagi.ru.monica.utils.FieldValidation
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.data.model.TotpData
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 密码详情页 (Password Detail Screen)
 * 
 * ## Material Design 3 动态主题支持
 * - 所有颜色均使用 MaterialTheme.colorScheme 语义化颜色
 * - 支持 Dynamic Color (Material You) - 根据用户壁纸自动适配
 * - 严禁硬编码任何 Hex 颜色值
 * 
 * ## UI 结构
 * - Scaffold 背景: MaterialTheme.colorScheme.surface
 * - 头部大图标: 居中显示密码/网站图标
 * - 2FA 卡片: primaryContainer + onPrimaryContainer
 * - 信息字段: surfaceContainerHigh + primary 标签
 * 
 * @param viewModel PasswordViewModel 实例
 * @param passwordId 密码条目 ID
 * @param onNavigateBack 返回导航回调
 * @param onEditPassword 编辑密码回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    viewModel: PasswordViewModel,
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onEditPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 密码条目状态
    var passwordEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Verification State
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var passwordVerificationError by remember { mutableStateOf(false) }
    
    // Helper function for deletion
    fun executeDeletion() {
        passwordEntry?.let { entry ->
            viewModel.deletePasswordEntry(entry)
            onNavigateBack()
        }
    }

    // Biometric Helper
    val biometricHelper = remember { BiometricHelper(context) }
    
    // Verification Logic calling Biometric or falling back to Password Dialog
    fun startVerificationForDeletion() {
        if (biometricHelper.isBiometricAvailable()) {
            (context as? FragmentActivity)?.let { activity ->
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity_to_delete),
                    onSuccess = { executeDeletion() },
                    onError = {
                        // If error is not user cancellation, show password dialog
                         showMasterPasswordDialog = true
                    },
                    onFailed = {
                        // Authentication failed (e.g. wrong finger), show password dialog
                        showMasterPasswordDialog = true
                    }
                )
            } ?: run {
                showMasterPasswordDialog = true
            }
        } else {
            showMasterPasswordDialog = true
        }
    }
    
    // 获取关联的TOTP数据
    val linkedTotp by viewModel.getLinkedTotpFlow(passwordId).collectAsState(initial = null)
    var totpCode by remember { mutableStateOf("") }
    var totpProgress by remember { mutableStateOf(1f) }
    
    // 定时更新TOTP验证码
    LaunchedEffect(linkedTotp) {
        if (linkedTotp != null) {
            while (isActive) {
                linkedTotp?.let { totp ->
                    totpCode = TotpGenerator.generateOtp(totp)
                    totpProgress = TotpGenerator.getProgress(totp.period)
                }
                delay(100)
            }
        }
    }
    
    
    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(true) }
    var addressInfoExpanded by remember { mutableStateOf(true) }
    var paymentInfoExpanded by remember { mutableStateOf(true) }
    
    // 密码可见性
    var passwordVisible by remember { mutableStateOf(false) }
    var cvvVisible by remember { mutableStateOf(false) }
    
    // 加载密码详情
    LaunchedEffect(passwordId) {
        viewModel.getPasswordEntryById(passwordId)?.let { entry ->
            passwordEntry = entry
            // 根据数据内容设置折叠状态
            personalInfoExpanded = hasPersonalInfo(entry)
            addressInfoExpanded = hasAddressInfo(entry)
            paymentInfoExpanded = hasPaymentInfo(entry)
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(passwordEntry?.title ?: stringResource(R.string.password_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MonicaIcons.Navigation.back,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {}, // Moved to ActionStrip
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ActionStrip(
                actions = listOf(
                    ActionStripItem(
                        icon = if (passwordEntry?.isFavorite == true) MonicaIcons.Status.favorite else MonicaIcons.Status.favoriteBorder,
                        contentDescription = stringResource(R.string.favorite),
                        onClick = {
                            passwordEntry?.let { entry ->
                                viewModel.toggleFavorite(entry.id, !entry.isFavorite)
                                passwordEntry = entry.copy(isFavorite = !entry.isFavorite)
                            }
                        },
                        tint = if (passwordEntry?.isFavorite == true) MaterialTheme.colorScheme.primary else null
                    ),
                    ActionStripItem(
                        icon = MonicaIcons.Action.edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = { onEditPassword(passwordId) }
                    ),
                    ActionStripItem(
                        icon = MonicaIcons.Action.delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = { showDeleteDialog = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                )
            )
        }
    ) { paddingValues ->
        passwordEntry?.let { entry ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================
                // 🎯 头部区域 - 居中大图标
                // ==========================================
                HeaderSection(entry = entry)
                
                // ==========================================
                // 🔐 基本信息卡片
                // ==========================================
                BasicInfoCard(
                    entry = entry,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                    context = context
                )
                
                // ==========================================
                // 🔑 2FA / TOTP 卡片 (如果有关联应用)
                // ==========================================
                // ==========================================
                // 🔑 2FA / TOTP 卡片 (如果有关联应用或TOTP)
                // ==========================================
                if (entry.appPackageName.isNotEmpty() || entry.appName.isNotEmpty() || linkedTotp != null) {
                    TotpCard(
                        entry = entry,
                        totpData = linkedTotp,
                        code = totpCode,
                        progress = totpProgress,
                        context = context
                    )
                }
                
                // ==========================================
                // 📧 个人信息区块
                // ==========================================
                if (hasPersonalInfo(entry)) {
                    CollapsibleSection(
                        title = stringResource(R.string.personal_info),
                        icon = MonicaIcons.General.person,
                        expanded = personalInfoExpanded,
                        onToggle = { personalInfoExpanded = !personalInfoExpanded }
                    ) {
                        PersonalInfoContent(entry = entry, context = context)
                    }
                }
                
                // ==========================================
                // 🏠 地址信息区块
                // ==========================================
                if (hasAddressInfo(entry)) {
                    CollapsibleSection(
                        title = stringResource(R.string.address_info),
                        icon = Icons.Default.Home,
                        expanded = addressInfoExpanded,
                        onToggle = { addressInfoExpanded = !addressInfoExpanded }
                    ) {
                        AddressInfoContent(entry = entry)
                    }
                }
                
                // ==========================================
                // 💳 支付信息区块
                // ==========================================
                if (hasPaymentInfo(entry)) {
                    CollapsibleSection(
                        title = stringResource(R.string.payment_info),
                        icon = MonicaIcons.Data.creditCard,
                        expanded = paymentInfoExpanded,
                        onToggle = { paymentInfoExpanded = !paymentInfoExpanded }
                    ) {
                        PaymentInfoContent(
                            entry = entry,
                            cvvVisible = cvvVisible,
                            onToggleCvvVisibility = { cvvVisible = !cvvVisible },
                            context = context
                        )
                    }
                }
                
                // ==========================================
                // 📝 备注区块
                // ==========================================
                if (entry.notes.isNotEmpty()) {
                    NotesCard(notes = entry.notes)
                }
                
                // 底部间距
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        startVerificationForDeletion()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_password_confirmation)) },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showMasterPasswordDialog) {
        MasterPasswordDialog(
            onDismiss = { 
                showMasterPasswordDialog = false 
                passwordVerificationError = false
            },
            onConfirm = { password ->
                if (viewModel.verifyMasterPassword(password)) {
                    showMasterPasswordDialog = false
                    executeDeletion()
                } else {
                    passwordVerificationError = true
                }
            },
            isError = passwordVerificationError
        )
    }
}

// ============================================
// 🎯 头部区域组件
// ============================================
@Composable
private fun HeaderSection(entry: PasswordEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 大图标容器
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MonicaIcons.Security.lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // 标题
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        // 网站
        if (entry.website.isNotEmpty()) {
            Text(
                text = entry.website,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================
// 🔐 基本信息卡片
// ============================================
@Composable
private fun BasicInfoCard(
    entry: PasswordEntry,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 用户名
            if (entry.username.isNotEmpty()) {
                InfoFieldWithCopy(
                    label = stringResource(R.string.username),
                    value = entry.username,
                    context = context
                )
            }
            
            // 密码
            if (entry.password.isNotEmpty()) {
                PasswordField(
                    label = stringResource(R.string.password),
                    value = entry.password,
                    visible = passwordVisible,
                    onToggleVisibility = onTogglePasswordVisibility,
                    context = context
                )
            }
        }
    }
}

// ============================================
// 🔑 2FA / TOTP 高亮卡片
// ============================================
// ============================================
// 🔑 2FA / TOTP 高亮卡片
// ============================================
@Composable
private fun TotpCard(
    entry: PasswordEntry,
    totpData: TotpData?,
    code: String,
    progress: Float,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = MonicaIcons.Security.key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (totpData != null) "动态验证码" else stringResource(R.string.linked_app),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // 如果只有TOTP数据，显示验证码
            if (totpData != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = code.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 4.sp
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("2FA Code", code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.copied, "验证码"), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = MonicaIcons.Action.copy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // 进度条
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                )
            }
            
            // 显示关联应用信息
            if (entry.appName.isNotEmpty() || entry.appPackageName.isNotEmpty()) {
                if (totpData != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
                
                Column {
                   if (entry.appName.isNotEmpty()) {
                        Text(
                            text = entry.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (entry.appPackageName.isNotEmpty()) {
                        Text(
                            text = entry.appPackageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// 📧 个人信息内容
// ============================================
@Composable
private fun PersonalInfoContent(
    entry: PasswordEntry,
    context: Context
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry.email.isNotEmpty()) {
            InfoFieldWithCopy(
                label = stringResource(R.string.email),
                value = entry.email,
                context = context
            )
        }
        
        if (entry.phone.isNotEmpty()) {
            InfoFieldWithCopy(
                label = stringResource(R.string.phone),
                value = FieldValidation.formatPhone(entry.phone),
                context = context
            )
        }
    }
}

// ============================================
// 🏠 地址信息内容
// ============================================
@Composable
private fun AddressInfoContent(entry: PasswordEntry) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry.addressLine.isNotEmpty()) {
            InfoField(
                label = stringResource(R.string.address_line),
                value = entry.addressLine
            )
        }
        
        // 城市和省份
        if (entry.city.isNotEmpty() || entry.state.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (entry.city.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoField(
                            label = stringResource(R.string.city),
                            value = entry.city
                        )
                    }
                }
                if (entry.state.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoField(
                            label = stringResource(R.string.state),
                            value = entry.state
                        )
                    }
                }
            }
        }
        
        // 邮编和国家
        if (entry.zipCode.isNotEmpty() || entry.country.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (entry.zipCode.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoField(
                            label = stringResource(R.string.zip_code),
                            value = entry.zipCode
                        )
                    }
                }
                if (entry.country.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoField(
                            label = stringResource(R.string.country),
                            value = entry.country
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// 💳 支付信息内容
// ============================================
@Composable
private fun PaymentInfoContent(
    entry: PasswordEntry,
    cvvVisible: Boolean,
    onToggleCvvVisibility: () -> Unit,
    context: Context
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry.creditCardNumber.isNotEmpty()) {
            InfoFieldWithCopy(
                label = stringResource(R.string.credit_card_number),
                value = FieldValidation.maskCreditCard(entry.creditCardNumber),
                copyValue = entry.creditCardNumber,
                context = context
            )
        }
        
        if (entry.creditCardHolder.isNotEmpty()) {
            InfoField(
                label = stringResource(R.string.card_holder),
                value = entry.creditCardHolder
            )
        }
        
        // 有效期和 CVV
        if (entry.creditCardExpiry.isNotEmpty() || entry.creditCardCVV.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (entry.creditCardExpiry.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoField(
                            label = stringResource(R.string.expiry_date),
                            value = entry.creditCardExpiry
                        )
                    }
                }
                if (entry.creditCardCVV.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        PasswordField(
                            label = stringResource(R.string.cvv),
                            value = entry.creditCardCVV,
                            visible = cvvVisible,
                            onToggleVisibility = onToggleCvvVisibility,
                            context = context
                        )
                    }
                }
            }
        }
        
        // 安全提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MonicaIcons.Security.lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(R.string.credit_card_encrypted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ============================================
// 📝 备注卡片
// ============================================
@Composable
private fun NotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.notes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ============================================
// 🔧 可折叠区块组件
// ============================================
@Composable
private fun CollapsibleSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) 
                        MonicaIcons.Navigation.expandLess 
                    else 
                        MonicaIcons.Navigation.expandMore,
                    contentDescription = if (expanded) 
                        stringResource(R.string.collapse) 
                    else 
                        stringResource(R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 内容区域 (带动画)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ============================================
// 🔧 信息字段组件
// ============================================
@Composable
private fun InfoField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoFieldWithCopy(
    label: String,
    value: String,
    copyValue: String = value,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(label, copyValue)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = MonicaIcons.Action.copy,
                    contentDescription = stringResource(R.string.copy),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (visible) value else "•".repeat(value.length.coerceAtMost(12)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row {
                // 显示/隐藏按钮
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (visible) 
                            MonicaIcons.Security.visibilityOff 
                        else 
                            MonicaIcons.Security.visibility,
                        contentDescription = if (visible) 
                            stringResource(R.string.hide) 
                        else 
                            stringResource(R.string.show),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 复制按钮
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = MonicaIcons.Action.copy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================
// 🔧 辅助函数
// ============================================

/**
 * 检查是否有个人信息
 */
private fun hasPersonalInfo(entry: PasswordEntry): Boolean {
    return entry.email.isNotEmpty() || entry.phone.isNotEmpty()
}

/**
 * 检查是否有地址信息
 */
private fun hasAddressInfo(entry: PasswordEntry): Boolean {
    return entry.addressLine.isNotEmpty() ||
           entry.city.isNotEmpty() ||
           entry.state.isNotEmpty() ||
           entry.zipCode.isNotEmpty() ||
           entry.country.isNotEmpty()
}

/**
 * 检查是否有支付信息
 */
private fun hasPaymentInfo(entry: PasswordEntry): Boolean {
    return entry.creditCardNumber.isNotEmpty() ||
           entry.creditCardHolder.isNotEmpty() ||
           entry.creditCardExpiry.isNotEmpty() ||
           entry.creditCardCVV.isNotEmpty()
}



