package takagi.ru.monica.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.*

/**
 * 安全分析界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAnalysisScreen(
    analysisData: SecurityAnalysisData,
    onStartAnalysis: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    // 检查是否从未分析过（所有数据为空且没有错误）
    val isInitialState = !analysisData.isAnalyzing && 
        analysisData.duplicatePasswords.isEmpty() && 
        analysisData.duplicateUrls.isEmpty() && 
        analysisData.compromisedPasswords.isEmpty() && 
        analysisData.no2FAAccounts.isEmpty() &&
        analysisData.error == null
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.security_analysis)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 显示分析进度
                analysisData.isAnalyzing -> {
                    AnalysisProgressView(analysisData.analysisProgress)
                }
                // 显示初始欢迎界面
                isInitialState -> {
                    InitialAnalysisView(onStartAnalysis = onStartAnalysis)
                }
                // 显示分析结果
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 统计卡片 - 精简显示
                        SecurityStatisticsCardsCompact(
                            duplicatePasswordsCount = analysisData.duplicatePasswords.sumOf { it.count },
                            duplicateUrlsCount = analysisData.duplicateUrls.sumOf { it.count },
                            compromisedPasswordsCount = analysisData.compromisedPasswords.size,
                            no2FAAccountsCount = analysisData.no2FAAccounts.filter { it.supports2FA }.size,
                            onStartAnalysis = onStartAnalysis
                        )
                        
                        // Tab 选择器
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(context.getString(R.string.duplicate_passwords)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(context.getString(R.string.duplicate_urls)) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text(context.getString(R.string.compromised_passwords)) }
                            )
                            Tab(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                text = { Text(context.getString(R.string.no_twofa)) }
                            )
                        }
                        
                        // 详细列表
                        when (selectedTab) {
                            0 -> DuplicatePasswordsList(analysisData.duplicatePasswords, onNavigateToPassword)
                            1 -> DuplicateUrlsList(analysisData.duplicateUrls, onNavigateToPassword)
                            2 -> CompromisedPasswordsList(analysisData.compromisedPasswords, onNavigateToPassword)
                            3 -> No2FAAccountsList(analysisData.no2FAAccounts, onNavigateToPassword)
                        }
                    }
                }
            }
            
            // 错误提示
            analysisData.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
fun InitialAnalysisView(onStartAnalysis: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 大图标
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 标题
        Text(
            text = context.getString(R.string.security_analysis),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 描述
        Text(
            text = context.getString(R.string.security_analysis_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 功能列表
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureItem(
                icon = Icons.Default.ContentCopy,
                text = context.getString(R.string.duplicate_passwords),
                color = Color(0xFFFF9800)
            )
            FeatureItem(
                icon = Icons.Default.Link,
                text = context.getString(R.string.duplicate_urls),
                color = Color(0xFF2196F3)
            )
            FeatureItem(
                icon = Icons.Default.Warning,
                text = context.getString(R.string.compromised_passwords),
                color = Color(0xFFF44336)
            )
            FeatureItem(
                icon = Icons.Default.Security,
                text = context.getString(R.string.no_twofa),
                color = Color(0xFF9C27B0)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 开始分析按钮
        Button(
            onClick = onStartAnalysis,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.getString(R.string.start_security_analysis),
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 提示文本
        Text(
            text = context.getString(R.string.analysis_may_take_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SecurityStatisticsCardsCompact(
    duplicatePasswordsCount: Int,
    duplicateUrlsCount: Int,
    compromisedPasswordsCount: Int,
    no2FAAccountsCount: Int,
    onStartAnalysis: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.security_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onStartAnalysis) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = context.getString(R.string.refresh),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactStatCard(
                    icon = Icons.Default.ContentCopy,
                    count = duplicatePasswordsCount,
                    label = context.getString(R.string.duplicate_short),
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    icon = Icons.Default.Link,
                    count = duplicateUrlsCount,
                    label = context.getString(R.string.duplicate_url_short),
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    icon = Icons.Default.Warning,
                    count = compromisedPasswordsCount,
                    label = context.getString(R.string.compromised_short),
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                CompactStatCard(
                    icon = Icons.Default.Security,
                    count = no2FAAccountsCount,
                    label = context.getString(R.string.no_2fa_short),
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CompactStatCard(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnalysisProgressView(progress: Int) {
    val context = LocalContext.current
    
    // 平滑的进度动画
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 600,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "progress"
    )
    
    // 呼吸动画 - 图标大小（放大缩小）
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1500,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // 背景圆环
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            
            // 进度圆环 - 使用动画进度
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            
            // 中心呼吸图标（只有缩放动画）
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(48.dp * scale),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 分析文本
        Text(
            text = context.getString(R.string.analyzing_security),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 进度百分比
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 进度阶段提示
        Text(
            text = getProgressMessage(progress, context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun getProgressMessage(progress: Int, context: android.content.Context): String {
    return when {
        progress < 25 -> context.getString(R.string.checking_duplicate_passwords)
        progress < 50 -> context.getString(R.string.checking_duplicate_urls)
        progress < 75 -> context.getString(R.string.checking_compromised_passwords)
        progress < 100 -> context.getString(R.string.checking_2fa_status)
        else -> context.getString(R.string.analysis_complete)
    }
}

@Composable
fun SecurityStatisticsCards(
    duplicatePasswordsCount: Int,
    duplicateUrlsCount: Int,
    compromisedPasswordsCount: Int,
    no2FAAccountsCount: Int
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.ContentCopy,
                title = context.getString(R.string.duplicate_passwords),
                count = duplicatePasswordsCount,
                color = if (duplicatePasswordsCount > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Link,
                title = context.getString(R.string.duplicate_urls),
                count = duplicateUrlsCount,
                color = if (duplicateUrlsCount > 0) Color(0xFF2196F3) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Warning,
                title = context.getString(R.string.compromised_passwords),
                count = compromisedPasswordsCount,
                color = if (compromisedPasswordsCount > 0) Color(0xFFF44336) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Security,
                title = context.getString(R.string.no_twofa),
                count = no2FAAccountsCount,
                color = if (no2FAAccountsCount > 0) Color(0xFF9C27B0) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DuplicatePasswordsList(
    groups: List<DuplicatePasswordGroup>,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    if (groups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = context.getString(R.string.no_duplicate_passwords)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                DuplicatePasswordCard(group, onNavigateToPassword)
            }
        }
    }
}

@Composable
fun DuplicatePasswordCard(
    group: DuplicatePasswordGroup,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.used_in_accounts, group.count),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "● ● ● ● ● ● ● ●",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                group.entries.forEach { entry ->
                    PasswordItemRow(entry, onNavigateToPassword)
                }
            }
        }
    }
}

@Composable
fun DuplicateUrlsList(
    groups: List<DuplicateUrlGroup>,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    if (groups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = context.getString(R.string.no_duplicate_urls)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                DuplicateUrlCard(group, onNavigateToPassword)
            }
        }
    }
}

@Composable
fun DuplicateUrlCard(
    group: DuplicateUrlGroup,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.url,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = context.getString(R.string.count_accounts, group.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                group.entries.forEach { entry ->
                    PasswordItemRow(entry, onNavigateToPassword)
                }
            }
        }
    }
}

@Composable
fun CompromisedPasswordsList(
    passwords: List<CompromisedPassword>,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    if (passwords.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = context.getString(R.string.no_compromised_passwords)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(passwords) { item ->
                CompromisedPasswordCard(item, onNavigateToPassword)
            }
        }
    }
}

@Composable
fun CompromisedPasswordCard(
    item: CompromisedPassword,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPassword(item.entry.id) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (item.entry.username.isNotBlank()) {
                    Text(
                        text = item.entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = context.getString(R.string.breached_times, item.breachCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun No2FAAccountsList(
    accounts: List<No2FAAccount>,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    if (accounts.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = context.getString(R.string.all_accounts_have_twofa)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                No2FAAccountCard(account, onNavigateToPassword)
            }
        }
    }
}

@Composable
fun No2FAAccountCard(
    account: No2FAAccount,
    onNavigateToPassword: (Long) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPassword(account.entry.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (account.supports2FA) 
                Color(0xFF9C27B0).copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = account.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (account.supports2FA) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.supports_twofa),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9C27B0)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PasswordItemRow(
    entry: PasswordEntry,
    onNavigateToPassword: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPassword(entry.id) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (entry.username.isNotBlank()) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
