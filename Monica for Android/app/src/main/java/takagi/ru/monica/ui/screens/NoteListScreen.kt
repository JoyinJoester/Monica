package takagi.ru.monica.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.bitwarden.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToAddNote: (Long?) -> Unit,
    securityManager: SecurityManager,
    onSelectionModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val settings by settingsViewModel.settings.collectAsState()
    val isGridLayout = settings.noteGridLayout
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    
    // 防止重复点击
    var isNavigating by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
        }
    }
    
    val context = LocalContext.current
    val biometricHelper = remember { BiometricHelper(context) }
    
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    
    // 过滤笔记
    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) {
            notes
        } else {
            notes.filter { 
                it.notes.contains(searchQuery, ignoreCase = true) || 
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 删除实际执行
    fun performDelete() {
        val notesToDelete = notes.filter { it.id in selectedNoteIds }
        viewModel.deleteNotes(notesToDelete)
        isSelectionMode = false
        selectedNoteIds = emptySet()
        showDeleteDialog = false
        showPasswordDialog = false
        masterPassword = ""
    }

    Scaffold(
        topBar = {
            // M3E 风格顶栏（保持与其他页面一致）
            ExpressiveTopBar(
                title = stringResource(R.string.nav_notes),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search),
                actions = {
                    // 布局切换按钮
                    IconButton(onClick = { settingsViewModel.updateNoteGridLayout(!isGridLayout) }) {
                        Icon(
                            imageVector = if (isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridLayout) "切换到列表" else "切换到网格",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 搜索按钮
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    NoteSelectionActionBar(
                        modifier = Modifier.wrapContentWidth(),
                        selectedCount = selectedNoteIds.size,
                        onExit = {
                            isSelectionMode = false
                            selectedNoteIds = emptySet()
                        },
                        onSelectAll = {
                            selectedNoteIds = if (selectedNoteIds.size == notes.size) {
                                emptySet()
                            } else {
                                notes.map { it.id }.toSet()
                            }
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }
        },
        floatingActionButton = {} // FAB moved to SwipeableAddFab in SimpleMainScreen
    ) { paddingValues ->
        // 重置导航状态
        LaunchedEffect(Unit) {
            isNavigating = false
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.notes_delete_selected_confirm, selectedNoteIds.size)) },
                confirmButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false
                    masterPassword = ""
                },
                title = { Text(stringResource(R.string.enter_master_password_confirm)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.notes_delete_selected_confirm, selectedNoteIds.size))
                        OutlinedTextField(
                            value = masterPassword,
                            onValueChange = { masterPassword = it },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.enter_master_password_confirm)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            TextButton(onClick = {
                                biometricHelper.authenticate(
                                    activity = activity,
                                    title = context.getString(R.string.verify_identity),
                                    subtitle = context.getString(R.string.verify_to_delete),
                                    onSuccess = { performDelete() },
                                    onError = { error ->
                                        android.widget.Toast.makeText(
                                            context,
                                            error,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailed = {}
                                )
                            }) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.use_biometric))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (securityManager.verifyMasterPassword(masterPassword)) {
                                performDelete()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.current_password_incorrect),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = masterPassword.isNotBlank()
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = false
                        masterPassword = ""
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        NoteListContent(
            notes = filteredNotes,
            isGridLayout = isGridLayout,
            isSelectionMode = isSelectionMode,
            selectedNoteIds = selectedNoteIds,
            onNoteClick = { noteId ->
                if (isSelectionMode) {
                    selectedNoteIds = if (selectedNoteIds.contains(noteId)) {
                        selectedNoteIds - noteId
                    } else {
                        selectedNoteIds + noteId
                    }
                    if (selectedNoteIds.isEmpty()) {
                        isSelectionMode = false
                    }
                } else {
                    onNavigateToAddNote(noteId)
                }
            },
            onNoteLongClick = { noteId ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    selectedNoteIds = setOf(noteId)
                }
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListContent(
    notes: List<SecureItem>,
    isGridLayout: Boolean,
    isSelectionMode: Boolean,
    selectedNoteIds: Set<Long>,
    onNoteClick: (Long) -> Unit,
    onNoteLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Note,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        if (isGridLayout) {
            // 瀑布流网格布局
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { note ->
                    ExpressiveNoteCard(
                        note = note,
                        isSelected = selectedNoteIds.contains(note.id),
                        isGridMode = true,
                        onClick = { onNoteClick(note.id) },
                        onLongClick = { onNoteLongClick(note.id) }
                    )
                }
            }
        } else {
            // 单列列表布局
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { note ->
                    ExpressiveNoteCard(
                        note = note,
                        isSelected = selectedNoteIds.contains(note.id),
                        isGridMode = false,
                        onClick = { onNoteClick(note.id) },
                        onLongClick = { onNoteLongClick(note.id) }
                    )
                }
                // 底部留白，防止被FAB遮挡
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun NoteSelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            NoteActionIcon(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            NoteActionIcon(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            NoteActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun NoteActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * M3E 风格的笔记卡片
 * 更具表达力的设计，包含图标、更好的排版和视觉层次
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveNoteCard(
    note: SecureItem,
    isSelected: Boolean,
    isGridMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val secondaryContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部：图标 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 笔记图标背景
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 标题
                Text(
                    text = note.title.ifEmpty { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isGridMode) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 内容预览
            if (note.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = note.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isGridMode) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    color = secondaryContentColor,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
            
            // 底部：日期 + 同步状态 + 安全标识
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryContentColor.copy(alpha = 0.8f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bitwarden 同步状态指示器
                    if (note.bitwardenVaultId != null) {
                        val syncStatus = when (note.syncStatus) {
                            "PENDING" -> SyncStatus.PENDING
                            "SYNCING" -> SyncStatus.SYNCING
                            "SYNCED" -> SyncStatus.SYNCED
                            "FAILED" -> SyncStatus.FAILED
                            "CONFLICT" -> SyncStatus.CONFLICT
                            else -> if (note.bitwardenLocalModified) SyncStatus.PENDING else SyncStatus.SYNCED
                        }
                        SyncStatusIcon(
                            status = syncStatus,
                            size = 14.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    // 安全标识小图标
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "加密存储",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// 保留旧的 NoteCard 以防其他地方引用（未来可删除）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Deprecated("Use ExpressiveNoteCard instead", ReplaceWith("ExpressiveNoteCard"))
fun NoteCard(
    note: SecureItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ExpressiveNoteCard(
        note = note,
        isSelected = isSelected,
        isGridMode = true,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
