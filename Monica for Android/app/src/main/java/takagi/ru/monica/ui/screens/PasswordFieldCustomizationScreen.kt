package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordFieldVisibility
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.SettingsViewModel

/**
 * 添加密码页面字段定制设置页面
 * 允许用户关闭不需要的字段卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordFieldCustomizationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val fieldVisibility = settings.passwordFieldVisibility

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "添加密码页面字段定制",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 说明卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "关闭的字段将不会在添加/编辑密码页面显示。\n\n注意：如果某条密码已有该字段的数据，即使关闭了开关，编辑该条目时仍会显示对应卡片。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 字段开关列表
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        FieldToggleItem(
                            icon = Icons.Default.VpnKey,
                            title = "安全验证",
                            subtitle = "TOTP验证码密钥",
                            checked = fieldVisibility.securityVerification,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("securityVerification", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Category,
                            title = "分类与备注",
                            subtitle = "密码分类和备注信息",
                            checked = fieldVisibility.categoryAndNotes,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("categoryAndNotes", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Apps,
                            title = "应用关联",
                            subtitle = "关联已安装的应用",
                            checked = fieldVisibility.appBinding,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("appBinding", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Person,
                            title = "个人信息",
                            subtitle = "邮箱、电话等联系方式",
                            checked = fieldVisibility.personalInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("personalInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.LocationOn,
                            title = "地址信息",
                            subtitle = "街道、城市、国家等",
                            checked = fieldVisibility.addressInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("addressInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.CreditCard,
                            title = "支付信息",
                            subtitle = "信用卡/银行卡信息",
                            checked = fieldVisibility.paymentInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("paymentInfo", it) 
                            }
                        )
                    }
                }
            }

            // 重置按钮
            item {
                OutlinedButton(
                    onClick = {
                        // 重置所有字段为默认开启
                        viewModel.updatePasswordFieldVisibility("securityVerification", true)
                        viewModel.updatePasswordFieldVisibility("categoryAndNotes", true)
                        viewModel.updatePasswordFieldVisibility("appBinding", true)
                        viewModel.updatePasswordFieldVisibility("personalInfo", true)
                        viewModel.updatePasswordFieldVisibility("addressInfo", true)
                        viewModel.updatePasswordFieldVisibility("paymentInfo", true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重置为默认")
                }
            }
        }
    }
}

@Composable
private fun FieldToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
