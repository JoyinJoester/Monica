package takagi.ru.monica.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.FieldValidation
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.R

/**
 * 密码详情对话框
 * 
 * 显示密码条目的完整信息，包括Phase 7新增的11个字段。
 * 
 * ## 功能特性
 * - 📝 基本信息区（固定显示）：网站、用户名、密码、备注、关联应用
 * - 📧 个人信息区（可折叠）：邮箱、手机号
 * - 🏠 地址信息区（可折叠）：详细地址、城市、省份、邮编、国家
 * - 💳 支付信息区（可折叠）：信用卡号、持卡人、有效期、CVV
 * 
 * ## 交互功能
 * - ✅ 一键复制：用户名、邮箱、手机号、密码、信用卡号、CVV
 * - ✅ 显示/隐藏：密码、CVV
 * - ✅ 智能折叠：无数据区块自动隐藏，有数据区块默认展开
 * - ✅ 格式化显示：手机号自动格式化、信用卡号掩码显示
 * 
 * ## 安全特性
 * - 🔐 密码默认隐藏（显示为 `••••••••••`）
 * - 🔐 CVV默认隐藏（显示为 `•••`）
 * - 🔐 信用卡号掩码显示（`•••• •••• •••• 1234`）
 * 
 * @param passwordEntry 要显示的密码条目
 * @param onDismiss 关闭对话框回调
 * @param onEdit 编辑按钮回调
 * @param onDelete 删除按钮回调
 * @param onAddPassword 添加密码按钮回调（用于创建多密码卡片）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailDialog(
    passwordEntry: PasswordEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddPassword: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(hasPersonalInfo(passwordEntry)) }
    var addressInfoExpanded by remember { mutableStateOf(hasAddressInfo(passwordEntry)) }
    var paymentInfoExpanded by remember { mutableStateOf(hasPaymentInfo(passwordEntry)) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                TopAppBar(
                    title = { 
                        Text(
                            text = passwordEntry.title,
                            maxLines = 1
                        )
                    },
                    actions = {
                        // 添加密码按钮
                        if (onAddPassword != null) {
                            IconButton(onClick = onAddPassword) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = context.getString(R.string.add_password),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // 编辑按钮
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.edit))
                        }
                        // 删除按钮
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = context.getString(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        // 关闭按钮
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 基本信息
                    InfoSection(title = context.getString(R.string.basic_info)) {
                        if (passwordEntry.website.isNotEmpty()) {
                            InfoItem(label = context.getString(R.string.website), value = passwordEntry.website)
                        }
                        if (passwordEntry.username.isNotEmpty()) {
                            InfoItemWithCopy(
                                label = context.getString(R.string.username),
                                value = passwordEntry.username,
                                context = context
                            )
                        }
                        PasswordItem(
                            label = context.getString(R.string.password),
                            value = passwordEntry.password,
                            context = context
                        )
                        if (passwordEntry.notes.isNotEmpty()) {
                            InfoItem(label = context.getString(R.string.notes), value = passwordEntry.notes)
                        }
                        if (passwordEntry.appName.isNotEmpty()) {
                            InfoItem(label = context.getString(R.string.linked_app), value = passwordEntry.appName)
                        }
                    }
                    
                    // 个人信息（如果有）
                    if (hasPersonalInfo(passwordEntry)) {
                        CollapsibleInfoSection(
                            title = context.getString(R.string.personal_info),
                            icon = MonicaIcons.General.person,
                            expanded = personalInfoExpanded,
                            onToggle = { personalInfoExpanded = !personalInfoExpanded },
                            context = context
                        ) {
                            if (passwordEntry.email.isNotEmpty()) {
                                InfoItemWithCopy(
                                    label = context.getString(R.string.email),
                                    value = passwordEntry.email,
                                    context = context
                                )
                            }
                            if (passwordEntry.phone.isNotEmpty()) {
                                InfoItemWithCopy(
                                    label = context.getString(R.string.phone),
                                    value = FieldValidation.formatPhone(passwordEntry.phone),
                                    context = context
                                )
                            }
                        }
                    }
                    
                    // 地址信息（如果有）
                    if (hasAddressInfo(passwordEntry)) {
                        CollapsibleInfoSection(
                            title = context.getString(R.string.address_info),
                            icon = Icons.Default.Home,
                            expanded = addressInfoExpanded,
                            onToggle = { addressInfoExpanded = !addressInfoExpanded },
                            context = context
                        ) {
                            if (passwordEntry.addressLine.isNotEmpty()) {
                                InfoItem(label = context.getString(R.string.address_line), value = passwordEntry.addressLine)
                            }
                            
                            // 城市和省份
                            if (passwordEntry.city.isNotEmpty() || passwordEntry.state.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (passwordEntry.city.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InfoItem(label = context.getString(R.string.city), value = passwordEntry.city)
                                        }
                                    }
                                    if (passwordEntry.state.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InfoItem(label = context.getString(R.string.state), value = passwordEntry.state)
                                        }
                                    }
                                }
                            }
                            
                            // 邮编和国家
                            if (passwordEntry.zipCode.isNotEmpty() || passwordEntry.country.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (passwordEntry.zipCode.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InfoItem(label = context.getString(R.string.zip_code), value = passwordEntry.zipCode)
                                        }
                                    }
                                    if (passwordEntry.country.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InfoItem(label = context.getString(R.string.country), value = passwordEntry.country)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 支付信息（如果有）
                    if (hasPaymentInfo(passwordEntry)) {
                        CollapsibleInfoSection(
                            title = context.getString(R.string.payment_info),
                            icon = MonicaIcons.Data.creditCard,
                            expanded = paymentInfoExpanded,
                            onToggle = { paymentInfoExpanded = !paymentInfoExpanded },
                            context = context
                        ) {
                            if (passwordEntry.creditCardNumber.isNotEmpty()) {
                                InfoItemWithCopy(
                                    label = context.getString(R.string.credit_card_number),
                                    value = FieldValidation.maskCreditCard(passwordEntry.creditCardNumber),
                                    copyValue = passwordEntry.creditCardNumber,
                                    context = context
                                )
                            }
                            if (passwordEntry.creditCardHolder.isNotEmpty()) {
                                InfoItem(label = context.getString(R.string.card_holder), value = passwordEntry.creditCardHolder)
                            }
                            
                            // 有效期和CVV
                            if (passwordEntry.creditCardExpiry.isNotEmpty() || passwordEntry.creditCardCVV.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (passwordEntry.creditCardExpiry.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            InfoItem(label = context.getString(R.string.expiry_date), value = passwordEntry.creditCardExpiry)
                                        }
                                    }
                                    if (passwordEntry.creditCardCVV.isNotEmpty()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            PasswordItem(
                                                label = context.getString(R.string.cvv),
                                                value = passwordEntry.creditCardCVV,
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Text(
                                    context.getString(R.string.credit_card_encrypted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    
                    // 底部间距
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 信息区块
 */
@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

/**
 * 可折叠信息区块
 */
@Composable
private fun CollapsibleInfoSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    context: Context,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) context.getString(R.string.collapse) else context.getString(R.string.expand)
                )
            }
            
            // 内容
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 带复制按钮的信息项
 */
@Composable
private fun InfoItemWithCopy(
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
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
                    Icons.Default.ContentCopy,
                    contentDescription = context.getString(R.string.copy),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 密码项（带显示/隐藏和复制）
 */
@Composable
private fun PasswordItem(
    label: String,
    value: String,
    context: Context
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val hasPasswordValue = value.isNotBlank()
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    !hasPasswordValue -> context.getString(R.string.permission_status_unavailable)
                    passwordVisible -> value
                    else -> "•".repeat(value.length.coerceAtMost(12))
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Row {
                // 显示/隐藏按钮
                if (hasPasswordValue) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible }
                    ) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) context.getString(R.string.hide) else context.getString(R.string.show),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // 复制按钮
                IconButton(
                    onClick = {
                        if (!hasPasswordValue) {
                            Toast.makeText(context, context.getString(R.string.permission_status_unavailable), Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = context.getString(R.string.copy),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

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
