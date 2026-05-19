package takagi.ru.monica.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.ui.components.OutlinedTextField
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.attachments.facade.AttachmentUriMetadata
import takagi.ru.monica.bitwarden.BitwardenVaultPremiumStore
import takagi.ru.monica.bitwarden.api.BitwardenApiFactory
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.bitwarden.sync.buildHeadline
import takagi.ru.monica.ui.common.pull.calculateDampedPullOffset
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.util.VibrationPatterns
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class SendCreateType {
    Text,
    File
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SendScreen(
    modifier: Modifier = Modifier,
    onSendClick: (BitwardenSend) -> Unit = {},
    selectedSendId: String? = null,
    showTopBar: Boolean = true,
    showStandaloneSettingsEntry: Boolean = false,
    onOpenStandaloneSettings: () -> Unit = {},
    bitwardenViewModel: BitwardenViewModel = viewModel(),
    onBitwardenEvent: ((BitwardenViewModel.BitwardenEvent) -> Boolean)? = null
) {
    val sends by bitwardenViewModel.sendsAcrossVaults.collectAsState()
    val activeVault by bitwardenViewModel.activeVault.collectAsState()
    val unlockState by bitwardenViewModel.unlockState.collectAsState()
    val unlockStateByVault by bitwardenViewModel.unlockStateByVault.collectAsState()
    val allVaults by bitwardenViewModel.vaults.collectAsState()
    val sendState by bitwardenViewModel.sendState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 多账号场景下：只要有任一已解锁 Vault 就允许操作；空账号 / 全锁定时禁用刷新按钮
    val anyVaultUnlocked = unlockStateByVault.any { it.value == BitwardenViewModel.UnlockState.Unlocked }
    val canCreateSend = activeVault != null && unlockState == BitwardenViewModel.UnlockState.Unlocked

    val vaultLookup = remember(allVaults) { allVaults.associateBy { it.id } }

    var deletingSend by remember { mutableStateOf<BitwardenSend?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showTopActionsMenu by remember { mutableStateOf(false) }
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val triggerDistance = with(LocalDensity.current) { 72.dp.toPx() }
    var hasVibrated by remember { mutableStateOf(false) }
    var canTriggerPullToSearch by remember { mutableStateOf(false) }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val filteredSends = remember(sends, searchQuery, vaultLookup) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sends
        } else {
            sends.filter { send ->
                val vaultLabel = vaultLookup[send.vaultId]?.let { v ->
                    v.displayName?.takeIf { it.isNotBlank() } ?: v.email
                }.orEmpty()
                send.name.contains(query, ignoreCase = true) ||
                    send.shareUrl.contains(query, ignoreCase = true) ||
                    (send.textContent?.contains(query, ignoreCase = true) == true) ||
                    (send.fileName?.contains(query, ignoreCase = true) == true) ||
                    send.notes.contains(query, ignoreCase = true) ||
                    vaultLabel.contains(query, ignoreCase = true)
            }
        }
    }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(activeVault?.id, unlockState, anyVaultUnlocked) {
        if (anyVaultUnlocked) {
            // 至少有一个已解锁账号就允许刷新视图：当前活跃账号触发自动同步，
            // 其它账号通过 sendsAcrossVaults 直接复用本地缓存。
            if (canCreateSend) {
                bitwardenViewModel.requestPageEnterAutoSync()
            }
            bitwardenViewModel.loadSends(forceRemoteSync = false)
        }
    }

    LaunchedEffect(bitwardenViewModel, onBitwardenEvent) {
        bitwardenViewModel.events.collect { event ->
            val consumedByParent = onBitwardenEvent?.invoke(event) == true
            if (consumedByParent) return@collect
            when (event) {
                is BitwardenViewModel.BitwardenEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.ShowWarning -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.SyncFinished ->
                    snackbarHostState.showSnackbar(event.summary.buildHeadline(context))
                is BitwardenViewModel.BitwardenEvent.SendCreated -> snackbarHostState.showSnackbar(event.message)
                is BitwardenViewModel.BitwardenEvent.SendDeleted -> snackbarHostState.showSnackbar(event.message)
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
                    title = stringResource(R.string.send_screen_title),
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandedChange = { expanded ->
                        isSearchExpanded = expanded
                        if (!expanded) {
                            searchQuery = ""
                        }
                    },
                    searchHint = stringResource(R.string.send_search_hint),
                    actions = {
                        IconButton(
                            onClick = { bitwardenViewModel.refreshAllUnlockedSends() },
                            enabled = anyVaultUnlocked &&
                                sendState !is BitwardenViewModel.SendState.Syncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        if (showStandaloneSettingsEntry) {
                            Box {
                                IconButton(onClick = { showTopActionsMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopActionsMenu,
                                    onDismissRequest = { showTopActionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.nav_settings)) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showTopActionsMenu = false
                                            onOpenStandaloneSettings()
                                        }
                                    )
                                }
                            }
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
                    // 没有任何已连接 Vault：保留原"未连接"提示
                    allVaults.isEmpty() -> {
                        EmptyStateCard(
                            title = stringResource(R.string.send_empty_no_connection_title),
                            message = stringResource(R.string.send_empty_no_connection_message)
                        )
                    }
                    // 有 Vault 但没有任何已解锁、并且本地也没有跨账号的 Send 缓存可用
                    !anyVaultUnlocked && sends.isEmpty() -> {
                        EmptyStateCard(
                            title = stringResource(R.string.send_empty_vault_locked_title),
                            message = stringResource(R.string.send_empty_vault_locked_message)
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
                            title = stringResource(R.string.send_empty_none_title),
                            message = stringResource(R.string.send_empty_none_message)
                        )
                    }
                    filteredSends.isEmpty() -> {
                        EmptyStateCard(
                            title = stringResource(R.string.send_empty_no_match_title),
                            message = stringResource(R.string.send_empty_no_match_message)
                        )
                    }
                    else -> {
                        // NestedScrollConnection 处理下拉搜索手势
                        val nestedScrollConnection = remember {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    if (currentOffset > 0 && available.y < 0) {
                                        val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                                        val consumed = currentOffset - newOffset
                                        currentOffset = newOffset
                                        return Offset(0f, -consumed)
                                    }
                                    return Offset.Zero
                                }

                                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                    if (!isSearchExpanded && available.y > 0 && canTriggerPullToSearch) {
                                        if (source == NestedScrollSource.UserInput) {
                                            val newOffset = calculateDampedPullOffset(
                                                currentOffset = currentOffset,
                                                dragDelta = available.y,
                                                maxDragDistance = triggerDistance * 1.6f
                                            )
                                            val oldOffset = currentOffset
                                            currentOffset = newOffset

                                            if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                                                hasVibrated = true
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(VibrationPatterns.TICK, -1))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator?.vibrate(20)
                                                }
                                            } else if (newOffset < triggerDistance) {
                                                hasVibrated = false
                                            }
                                            return available
                                        }
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    if (currentOffset >= triggerDistance) {
                                        isSearchExpanded = true
                                        hasVibrated = false
                                    }
                                    Animatable(currentOffset).animateTo(0f) {
                                        currentOffset = value
                                    }
                                    return super.onPreFling(available)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .offset { IntOffset(0, currentOffset.toInt()) }
                                .nestedScroll(nestedScrollConnection)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val isAtTop = listState.firstVisibleItemIndex == 0 &&
                                            listState.firstVisibleItemScrollOffset == 0
                                        canTriggerPullToSearch = isAtTop
                                    }
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(
                                items = filteredSends,
                                // 跨账号视图下，单独的 sendId 不再唯一（不同 vault 间会重复），
                                // 用 vaultId+sendId 复合键避免 LazyColumn key 冲突。
                                key = { "${it.vaultId}:${it.bitwardenSendId}" }
                            ) { send ->
                                val vault = vaultLookup[send.vaultId]
                                val vaultLabel = vault?.let {
                                    it.displayName?.takeIf { name -> name.isNotBlank() } ?: it.email
                                }.orEmpty()
                                val vaultServer = vault?.serverUrl.orEmpty()
                                SendItemCard(
                                    send = send,
                                    selected = selectedSendId == send.bitwardenSendId,
                                    vaultLabel = vaultLabel,
                                    vaultServerUrl = vaultServer,
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
            title = { Text(stringResource(R.string.send_delete_title)) },
            text = { Text(stringResource(R.string.send_delete_message, send.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        // 多账号场景下必须把 send 自身的 vaultId 传进去，否则会落到错误的 Vault
                        bitwardenViewModel.deleteSend(send.bitwardenSendId, send.vaultId)
                        deletingSend = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSend = null }) {
                    Text(stringResource(R.string.cancel))
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
                        text = stringResource(R.string.send_secure_share_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = stringResource(R.string.send_secure_share_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(label = stringResource(R.string.send_chip_total, sendCount))
                    HeroChip(label = stringResource(R.string.send_chip_text, textCount))
                    HeroChip(label = stringResource(R.string.send_chip_file, fileCount))
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
    /** 例如 "joyinsana@163.com" 或 displayName。空串时不展示账号信息。 */
    vaultLabel: String,
    /** 当前 vault 的 serverUrl，仅用于展开后的细节展示。空串时省略。 */
    vaultServerUrl: String,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "send_arrow_rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { expanded = !expanded }
            ),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (send.isTextType) Icons.AutoMirrored.Filled.Send else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = send.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val typeLabel = if (send.isTextType) {
                            stringResource(R.string.send_type_text)
                        } else {
                            stringResource(R.string.send_type_file)
                        }
                        
                        // 多账号场景下，副标题同时展示类型和所属账号，便于一眼区分两个 Vault 下同名 Send。
                        val subtitle = if (vaultLabel.isNotBlank()) {
                            "$typeLabel · $vaultLabel"
                        } else {
                            typeLabel
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (vaultLabel.isNotBlank()) {
                    // 展开后展示创建该 Send 的账号 / 服务器，回答"这条 Send 来自哪个账号"。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vaultLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vaultServerUrl.isNotBlank()) {
                                Text(
                                    text = vaultServerUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                val body = when {
                    send.isTextType && !send.textContent.isNullOrBlank() -> send.textContent
                    send.isFileType -> send.fileName ?: stringResource(R.string.send_file_fallback_name)
                    else -> send.notes
                }
                
                if (!body.isNullOrBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (send.hasPassword) {
                        MetaTag(icon = Icons.Default.Key, label = stringResource(R.string.send_tag_password_protected))
                    }
                    if (send.isTextHidden) {
                        MetaTag(icon = Icons.Default.VisibilityOff, label = stringResource(R.string.send_tag_hidden_content))
                    }
                    if (send.disabled) {
                        MetaTag(icon = Icons.Default.Lock, label = stringResource(R.string.send_tag_disabled))
                    }
                    MetaTag(icon = Icons.Default.Refresh, label = stringResource(R.string.send_tag_access_count, send.accessCount))
                    send.maxAccessCount?.let { max ->
                        MetaTag(icon = Icons.AutoMirrored.Filled.Send, label = stringResource(R.string.send_tag_limit, max))
                    }
                    send.expirationDate?.let { exp ->
                        MetaTag(icon = Icons.Default.CloudOff, label = stringResource(R.string.send_tag_expire, formatDate(exp)))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopyLink,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.send_copy_link),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onOpenLink,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.open_link),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete))
                }
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
    vaults: List<BitwardenVault> = emptyList(),
    activeVault: BitwardenVault? = null,
    unlockStateByVault: Map<Long, BitwardenViewModel.UnlockState> = emptyMap(),
    initialTitle: String = "",
    initialText: String = "",
    initialNotes: String = "",
    onNavigateBack: () -> Unit,
    onCreate: (
        vaultId: Long,
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        expireInDays: Int
    ) -> Unit,
    onCreateFile: (
        vaultId: Long,
        title: String,
        fileUri: Uri,
        fileName: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        expireInDays: Int
    ) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var text by remember(initialText) { mutableStateOf(initialText) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
    var password by remember { mutableStateOf("") }
    var maxAccessCount by remember { mutableStateOf("") }
    var expireDaysText by remember { mutableStateOf("7") }
    var hideEmail by remember { mutableStateOf(false) }
    var hiddenText by remember { mutableStateOf(false) }
    var sendType by remember { mutableStateOf(SendCreateType.Text) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileMeta by remember { mutableStateOf<AttachmentUriMetadata.Metadata?>(null) }
    var selectedVaultId by remember(activeVault?.id, vaults) {
        mutableStateOf(activeVault?.id ?: vaults.firstOrNull()?.id)
    }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedFileUri = uri
            selectedFileMeta = AttachmentUriMetadata.resolve(context, uri)
        }
    }

    val creating = sendState is BitwardenViewModel.SendState.Creating
    val availableVaults = remember(vaults, activeVault, unlockStateByVault) {
        val knownVaults = if (vaults.isNotEmpty()) vaults else listOfNotNull(activeVault)
        knownVaults.filter { vault ->
            unlockStateByVault[vault.id] == BitwardenViewModel.UnlockState.Unlocked ||
                (vault.id == activeVault?.id && unlockStateByVault[vault.id] == null)
        }
    }
    LaunchedEffect(availableVaults, selectedVaultId) {
        if (selectedVaultId == null || availableVaults.none { it.id == selectedVaultId }) {
            selectedVaultId = availableVaults.firstOrNull { it.id == activeVault?.id }?.id
                ?: availableVaults.firstOrNull()?.id
        }
    }
    val selectedVault = availableVaults.firstOrNull { it.id == selectedVaultId }
    val fileSendAllowed = remember(selectedVault?.id, selectedVault?.serverUrl) {
        val vault = selectedVault ?: return@remember false
        val official = BitwardenApiFactory.isOfficialServer(vault.serverUrl) ||
            BitwardenApiFactory.isOfficialEuServer(vault.serverUrl)
        !official || BitwardenVaultPremiumStore.isPremium(context, vault.id)
    }
    LaunchedEffect(selectedFileMeta?.fileName) {
        if (sendType == SendCreateType.File && title.isBlank()) {
            title = selectedFileMeta?.fileName.orEmpty()
        }
    }
    val canSave = !creating && selectedVault != null && title.isNotBlank() && when (sendType) {
        SendCreateType.Text -> text.isNotBlank()
        // fileSendAllowed 已经在文件选择按钮的 enabled 上做了门控，
        // 如果用户能选到文件说明已经允许。这里不再重复检查，避免
        // remember 缓存时序问题导致按钮灰色。
        SendCreateType.File -> selectedFileUri != null && selectedFileMeta != null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.send_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                    val targetVaultId = selectedVault?.id ?: return@FloatingActionButton
                    if (sendType == SendCreateType.File) {
                        val uri = selectedFileUri ?: return@FloatingActionButton
                        val meta = selectedFileMeta ?: return@FloatingActionButton
                        onCreateFile(
                            targetVaultId,
                            title.trim(),
                            uri,
                            meta.fileName,
                            notes.takeIf { it.isNotBlank() }?.trim(),
                            password.takeIf { it.isNotBlank() }?.trim(),
                            maxAccessCount.toIntOrNull(),
                            hideEmail,
                            expireDaysText.toIntOrNull()?.coerceIn(1, 30) ?: 7
                        )
                    } else {
                        onCreate(
                            targetVaultId,
                            title.trim(),
                            text.trim(),
                            notes.takeIf { it.isNotBlank() }?.trim(),
                            password.takeIf { it.isNotBlank() }?.trim(),
                            maxAccessCount.toIntOrNull(),
                            hideEmail,
                            hiddenText,
                            expireDaysText.toIntOrNull()?.coerceIn(1, 30) ?: 7
                        )
                    }
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
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SendFormSectionCard(title = stringResource(R.string.send_account_section_title)) {
                if (availableVaults.isEmpty()) {
                    StateBanner(stringResource(R.string.send_account_locked_hint))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        availableVaults.forEach { vault ->
                            SendVaultChoiceRow(
                                vault = vault,
                                selected = vault.id == selectedVaultId,
                                onClick = { selectedVaultId = vault.id }
                            )
                        }
                    }
                }
            }

            SendFormSectionCard(title = stringResource(R.string.send_type_section_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sendType == SendCreateType.Text,
                            onClick = { sendType = SendCreateType.Text },
                            label = { Text(stringResource(R.string.send_type_text)) },
                            leadingIcon = if (sendType == SendCreateType.Text) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = sendType == SendCreateType.File,
                            enabled = fileSendAllowed,
                            onClick = { sendType = SendCreateType.File },
                            label = { Text(stringResource(R.string.send_type_file)) },
                            leadingIcon = if (sendType == SendCreateType.File) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                    if (!fileSendAllowed) {
                        StateBanner(stringResource(R.string.send_file_premium_required_hint))
                    }
                }
            }

            SendFormSectionCard(
                title = if (sendType == SendCreateType.File) {
                    stringResource(R.string.send_file_send_heading)
                } else {
                    stringResource(R.string.send_text_send_heading)
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.title)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (sendType == SendCreateType.File) {
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            enabled = fileSendAllowed && !creating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedFileMeta?.fileName ?: stringResource(R.string.send_select_file))
                        }
                        selectedFileMeta?.let { meta ->
                            Text(
                                text = stringResource(R.string.send_selected_file_meta, meta.fileName, formatFileSize(meta.sizeBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.send_content_label)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.notes_optional)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SendFormSectionCard(title = stringResource(R.string.send_options_section_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.send_access_password_optional)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxAccessCount,
                            onValueChange = { maxAccessCount = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.send_max_access_count)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = expireDaysText,
                            onValueChange = { expireDaysText = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.send_valid_days)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SendSwitchRow(
                        title = stringResource(R.string.send_hide_email),
                        description = stringResource(R.string.send_hide_email_desc),
                        checked = hideEmail,
                        onCheckedChange = { hideEmail = it }
                    )

                    if (sendType == SendCreateType.Text) {
                        SendSwitchRow(
                            title = stringResource(R.string.send_hide_text),
                            description = stringResource(R.string.send_hide_text_desc),
                            checked = hiddenText,
                            onCheckedChange = { hiddenText = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
private fun SendFormSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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

@Composable
private fun SendVaultChoiceRow(
    vault: BitwardenVault,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vault.displayName?.takeIf { it.isNotBlank() } ?: vault.email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = vault.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SendSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes < 0) return "unknown size"
    if (sizeBytes < 1024) return "$sizeBytes B"
    val units = listOf("KB", "MB", "GB")
    var size = sizeBytes.toDouble() / 1024.0
    var index = 0
    while (size >= 1024.0 && index < units.lastIndex) {
        size /= 1024.0
        index += 1
    }
    return "%.1f %s".format(java.util.Locale.US, size, units[index])
}
