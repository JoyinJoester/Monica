package takagi.ru.monica.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/**
 * V2 Send 页面 - 安全分享功能
 * 
 * 功能（对标 Bitwarden Send）：
 * - 创建临时安全分享链接
 * - 设置过期时间
 * - 设置访问密码
 * - 设置最大访问次数
 * - 支持文本和文件类型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2SendScreen(
    modifier: Modifier = Modifier,
    onCreateSend: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部应用栏
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.v2_send_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.v2_send_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = onCreateSend) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "创建 Send"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // 内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // 空状态
            V2SendEmptyState(onCreateSend = onCreateSend)
        }
    }
}

/**
 * V2 Send 空状态
 */
@Composable
private fun V2SendEmptyState(
    onCreateSend: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Text(
            text = "暂无 Send 分享",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "使用 Send 功能安全地分享文本或文件\n设置过期时间、密码和访问限制",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 功能介绍卡片
        SendFeatureCard(
            icon = Icons.Default.Timer,
            title = "自动过期",
            description = "设置分享的有效期限"
        )
        
        SendFeatureCard(
            icon = Icons.Default.Lock,
            title = "密码保护",
            description = "为分享设置访问密码"
        )
        
        SendFeatureCard(
            icon = Icons.Default.Visibility,
            title = "访问限制",
            description = "限制最大访问次数"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(onClick = onCreateSend) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("创建第一个 Send")
        }
    }
}

/**
 * Send 功能介绍卡片
 */
@Composable
private fun SendFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
