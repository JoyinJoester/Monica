package takagi.ru.monica.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.util.VibrationPatterns
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val canCreateSend = activeVault != null && unlockState == BitwardenViewModel.UnlockState.Unlocked

    var deletingSend by remember { mutableStateOf<BitwardenSend?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
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
                            onClick = { bitwardenViewModel.loadSends(forceRemoteSync = true) },
                            enabled = canCreateSend &&
                                sendState !is BitwardenViewModel.SendState.Syncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
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
                            title = stringResource(R.string.send_empty_no_connection_title),
                            message = stringResource(R.string.send_empty_no_connection_message)
                        )
                    }
                    unlockState != BitwardenViewModel.UnlockState.Unlocked -> {
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .offset { IntOffset(0, currentOffset.toInt()) }
                                .pointerInput(isSearchExpanded, filteredSends.size) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            canTriggerPullToSearch =
                                                listState.firstVisibleItemIndex == 0 &&
                                                    listState.firstVisibleItemScrollOffset == 0
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (!isSearchExpanded && canTriggerPullToSearch) {
                                                if (dragAmount > 0f) {
                                                    val oldOffset = currentOffset
                                                    val newOffset = currentOffset + dragAmount * 0.5f
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
                                                    change.consume()
                                                } else if (currentOffset > 0f) {
                                                    currentOffset = (currentOffset + dragAmount).coerceAtLeast(0f)
                                                    change.consume()
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (currentOffset >= triggerDistance) {
                                                isSearchExpanded = true
                                            }
                                            hasVibrated = false
                                            canTriggerPullToSearch = false
                                            scope.launch {
                                                Animatable(currentOffset).animateTo(0f) {
                                                    currentOffset = value
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            hasVibrated = false
                                            canTriggerPullToSearch = false
                                            scope.launch {
                                                Animatable(currentOffset).animateTo(0f) {
                                                    currentOffset = value
                                                }
                                            }
                                        }
                                    )
                                },
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
            title = { Text(stringResource(R.string.send_delete_title)) },
            text = { Text(stringResource(R.string.send_delete_message, send.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        bitwardenViewModel.deleteSend(send.bitwardenSendId)
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
                        Text(
                            if (send.isTextType) {
                                stringResource(R.string.send_type_text)
                            } else {
                                stringResource(R.string.send_type_file)
                            }
                        )
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
                send.isFileType -> send.fileName ?: stringResource(R.string.send_file_fallback_name)
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

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = onCopyLink,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.send_copy_link),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onOpenLink,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.open_link),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.delete),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.send_text_send_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.send_content_label)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes_optional)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.send_hide_email))
                    Text(
                        text = stringResource(R.string.send_hide_email_desc),
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
                    Text(stringResource(R.string.send_hide_text))
                    Text(
                        text = stringResource(R.string.send_hide_text_desc),
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
