package takagi.ru.monica.autofill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.autofill.data.AutofillItem
import takagi.ru.monica.data.PasswordEntry

/**
 * 优化的密码列表组件
 * 
 * 改进:
 * - 更流畅的动画效果
 * - 更友好的空状态提示
 * - 优化的滚动性能
 * 
 * @param passwords 密码列表
 * @param onItemSelected 选择回调
 * @param onShowAllPasswords 显示所有密码的回调(可选)
 * @param modifier 修饰符
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PasswordList(
    passwords: List<PasswordEntry>,
    onItemSelected: (AutofillItem) -> Unit,
    modifier: Modifier = Modifier,
    onShowAllPasswords: (() -> Unit)? = null
) {
    if (passwords.isEmpty()) {
        // 美观的空状态
        EmptyPasswordState(
            modifier = modifier,
            onShowAllPasswords = onShowAllPasswords
        )
    } else {
        // 密码列表
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = passwords,
                key = { it.id }
            ) { password ->
                PasswordListItem(
                    password = password,
                    onItemClick = {
                        onItemSelected(AutofillItem.Password(password))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                )
            }
            
            // 底部留白
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 优化的空状态视图
 * 
 * 更友好的提示和图标
 * 
 * @param modifier 修饰符
 * @param onShowAllPasswords 显示所有密码的回调
 */
@Composable
fun EmptyPasswordState(
    modifier: Modifier = Modifier,
    onShowAllPasswords: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 友好的图标
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 主要提示
            Text(
                text = "未找到关联的密码",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 次要提示
            Text(
                text = if (onShowAllPasswords != null) {
                    "可以从所有密码中选择一个"
                } else {
                    "试试调整搜索条件\n或在主应用中添加新密码"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.4f)
            )
            
            // 如果提供了回调,显示按钮
            if (onShowAllPasswords != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onShowAllPasswords,
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择其他密码")
                }
            }
        }
    }
}

