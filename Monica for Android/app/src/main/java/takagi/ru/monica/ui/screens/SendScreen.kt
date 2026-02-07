package takagi.ru.monica.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.ui.components.ExpressiveTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SendScreen(
    modifier: Modifier = Modifier,
    onSendClick: (BitwardenSend) -> Unit = {},
    selectedSendId: String? = null,
    showTopBar: Boolean = true,
    bitwardenViewModel: BitwardenViewModel = viewModel()
) {
    val sends by bitwardenViewModel.sends.collectAsState()
    val activeVault by bitwardenViewModel.activeVault.collectAsState()
    val unlockState by bitwardenViewModel.unlockState.collectAsState()
    val sendState by bitwardenViewModel.sendState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val canCreateSend = activeVault != null && unlockState == BitwardenViewModel.UnlockState.Unlocked

    var deletingSend by remember { mutableStateOf<BitwardenSend?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val filteredSends = remember(sends, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sends
        } else {
            sends.filter { send ->
                send.name.contains(query, ignoreCase = true) ||
                    send.shareUrl.contains(query, ignoreCase = true) ||
                    (send.textContent?.contains(query, ignoreCase = true) == true) ||
                    (send.fileName?.contains(query, ignoreCase = true) == true) ||
                    send.notes.contains(query, ignoreCase = true)
            }
        }
    }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(activeVault?.id, unlockState) {
        if (canCreateSend) {
            bitwardenViewModel.loadSends(forceRemoteSync = true)
        }
    }

    LaunchedEffect(Unit) {
        bitwardenViewModel.events.collect { event ->
            when (event) {
                is BitwardenViewModel.BitwardenEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.ShowWarning -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (showTopBar) {
                ExpressiveTopBar(
                    title = "安全发送",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandedChange = { expanded ->
                        isSearchExpanded = expanded
                        if (!expanded) {
                            searchQuery = ""
                        }
                    },
                    searchHint = "搜索 Send",
                    actions = {
                        IconButton(
                            onClick = { bitwardenViewModel.loadSends(forceRemoteSync = true) },
                            enabled = canCreateSend &&
                                sendState !is BitwardenViewModel.SendState.Syncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                SendHeroCard(
                    sendCount = sends.size,
                    textCount = sends.count { it.isTextType },
                    fileCount = sends.count { it.isFileType }
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (sendState) {
                    is BitwardenViewModel.SendState.Syncing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is BitwardenViewModel.SendState.Warning -> {
                        StateBanner((sendState as BitwardenViewModel.SendState.Warning).message)
                    }
                    is BitwardenViewModel.SendState.Error -> {
                        StateBanner((sendState as BitwardenViewModel.SendState.Error).message)
                    }
                    else -> Unit
                }

                when {
                    activeVault == null -> {
                        EmptyStateCard(
                            title = "未连接 Bitwarden",
                            message = "请先在设置中登录并连接 Bitwarden，之后即可创建和管理 Send。"
                        )
                    }
                    unlockState != BitwardenViewModel.UnlockState.Unlocked -> {
                        EmptyStateCard(
                            title = "Vault 已锁定",
                            message = "请先在 Bitwarden 设置页解锁 Vault，然后即可使用 Send。"
                        )
                    }
                    sends.isEmpty() && sendState == BitwardenViewModel.SendState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    sends.isEmpty() -> {
                        EmptyStateCard(
                            title = "暂无 Send",
                            message = "点击右下角按钮创建第一个文本 Send，可设置访问次数、到期时间和访问密码。"
                        )
                    }
                    filteredSends.isEmpty() -> {
                        EmptyStateCard(
                            title = "未找到匹配 Send",
                            message = "换个关键词试试，或清空搜索后查看全部结果。"
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(
                                items = filteredSends,
                                key = { it.bitwardenSendId }
                            ) { send ->
                                SendItemCard(
                                    send = send,
                                    selected = selectedSendId == send.bitwardenSendId,
                                    onClick = { onSendClick(send) },
                                    onCopyLink = {
                                        clipboardManager.setText(AnnotatedString(send.shareUrl))
                                    },
                                    onOpenLink = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(send.shareUrl))
                                            context.startActivity(intent)
                                        }
                                    },
                                    onDelete = { deletingSend = send }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deletingSend != null) {
        val send = deletingSend ?: return
        AlertDialog(
            onDismissRequest = { deletingSend = null },
            title = { Text("删除 Send") },
            text = { Text("确定删除「${send.name}」吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        bitwardenViewModel.deleteSend(send.bitwardenSendId)
                        deletingSend = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSend = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SendHeroCard(
    sendCount: Int,
    textCount: Int,
    fileCount: Int
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "端到端加密分享",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "你的 Send 数据通过 Bitwarden 协议加密，链接可设置有效期、访问次数与访问密码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(label = "总计 $sendCount")
                    HeroChip(label = "文本 $textCount")
                    HeroChip(label = "文件 $fileCount")
                }
            }
        }
    }
}

@Composable
private fun HeroChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SendItemCard(
    send: BitwardenSend,
    selected: Boolean,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = send.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (send.isTextType) "文本" else "文件")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (send.isTextType) Icons.AutoMirrored.Filled.Send else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            val body = when {
                send.isTextType && !send.textContent.isNullOrBlank() -> send.textContent
                send.isFileType -> send.fileName ?: "文件 Send"
                else -> send.notes
            }
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (send.hasPassword) {
                    MetaTag(icon = Icons.Default.Key, label = "密码保护")
                }
                if (send.isTextHidden) {
                    MetaTag(icon = Icons.Default.VisibilityOff, label = "内容隐藏")
                }
                if (send.disabled) {
                    MetaTag(icon = Icons.Default.Lock, label = "已禁用")
                }
                MetaTag(icon = Icons.Default.Refresh, label = "访问 ${send.accessCount}")
                send.maxAccessCount?.let { max -> MetaTag(icon = Icons.AutoMirrored.Filled.Send, label = "上限 $max") }
                send.expirationDate?.let { exp ->
                    MetaTag(icon = Icons.Default.CloudOff, label = "过期 ${formatDate(exp)}")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCopyLink) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制链接")
                }
                TextButton(onClick = onOpenLink) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun MetaTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSendScreen(
    modifier: Modifier = Modifier,
    sendState: BitwardenViewModel.SendState,
    onNavigateBack: () -> Unit,
    onCreate: (
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        expireInDays: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var maxAccessCount by remember { mutableStateOf("") }
    var expireDaysText by remember { mutableStateOf("7") }
    var hideEmail by remember { mutableStateOf(false) }
    var hiddenText by remember { mutableStateOf(false) }

    val creating = sendState is BitwardenViewModel.SendState.Creating
    val canSave = !creating && title.isNotBlank() && text.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("创建 Send") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!canSave) return@FloatingActionButton
                    onCreate(
                        title.trim(),
                        text.trim(),
                        notes.takeIf { it.isNotBlank() }?.trim(),
                        password.takeIf { it.isNotBlank() }?.trim(),
                        maxAccessCount.toIntOrNull(),
                        hideEmail,
                        hiddenText,
                        expireDaysText.toIntOrNull()?.coerceIn(1, 30) ?: 7
                    )
                },
                containerColor = if (canSave) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSave) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = "保存")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "文本发送",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("发送内容") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注（可选）") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("访问密码（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxAccessCount,
                    onValueChange = { maxAccessCount = it.filter(Char::isDigit) },
                    label = { Text("最大访问次数") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = expireDaysText,
                    onValueChange = { expireDaysText = it.filter(Char::isDigit) },
                    label = { Text("有效天数(1-30)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐藏邮箱")
                    Text(
                        text = "收件方不会看到发送者邮箱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hideEmail,
                    onCheckedChange = { hideEmail = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐藏文本内容")
                    Text(
                        text = "首次查看后隐藏明文（Bitwarden 标准）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hiddenText,
                    onCheckedChange = { hiddenText = it }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StateBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun formatDate(raw: String): String {
    return try {
        val instant = Instant.parse(raw)
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        raw
    }
}
