package takagi.ru.monica.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    onNavigateToAddNote: (Long?) -> Unit,
    securityManager: SecurityManager,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    
    // 防止重复点击
    var isNavigating by remember { mutableStateOf(false) }
    
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
            if (isSelectionMode) {
                TopAppBar(
                    title = { 
                        Text(
                            text = stringResource(R.string.selected_items, selectedNoteIds.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedNoteIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // 全选/取消全选
                        IconButton(onClick = {
                            selectedNoteIds = if (selectedNoteIds.size == notes.size) {
                                emptySet()
                            } else {
                                notes.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.select_all)
                            )
                        }
                        
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                // 搜索栏
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    active = false,
                    onActiveChange = { },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {}
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { 
                        if (!isNavigating) {
                            isNavigating = true
                            onNavigateToAddNote(null)
                            // 简单的防抖重置，实际导航后页面会销毁或重组，这里只是为了防止极快点击
                            // 更好的做法是在 ViewModel 中处理或使用 LaunchedEffect
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
                }
            }
        }
    ) { paddingValues ->
        // 重置导航状态
        LaunchedEffect(Unit) {
            isNavigating = false
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text("确定要删除选中的 ${selectedNoteIds.size} 条笔记吗？") },
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
                        Text("确定要删除选中的 ${selectedNoteIds.size} 条笔记吗？")
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
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp,
            modifier = modifier.fillMaxSize()
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    isSelected = selectedNoteIds.contains(note.id),
                    onClick = { onNoteClick(note.id) },
                    onLongClick = { onNoteLongClick(note.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: SecureItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 显示标题
            if (note.title.isNotEmpty()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // 显示内容摘要
            if (note.notes.isNotEmpty()) {
                Text(
                    text = note.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.outline
            )
        }
    }
}
