package takagi.ru.monica.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import takagi.ru.monica.data.model.NoteData
import kotlinx.serialization.json.Json

import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditNoteScreen(
    noteId: Long,
    onNavigateBack: () -> Unit,
    viewModel: NoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val biometricHelper = remember { BiometricHelper(context) }
    val securityManager = remember { SecurityManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )

    var content by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var createdAt by remember { mutableStateOf(java.util.Date()) }
    var currentNote by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            val note = viewModel.getNoteById(noteId)
            note?.let {
                currentNote = it
                // 尝试从 itemData 解析 content，如果失败则使用 notes
                content = try {
                    val noteData = Json.decodeFromString<NoteData>(it.itemData)
                    noteData.content
                } catch (e: Exception) {
                    it.notes
                }
                isFavorite = it.isFavorite
                createdAt = it.createdAt
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == -1L) stringResource(R.string.new_note) else stringResource(R.string.edit_note)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (noteId != -1L) {
                        IconButton(onClick = {
                            showConfirmDelete = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = {
                        if (noteId == -1L) {
                            viewModel.addNote(content, isFavorite = isFavorite)
                        } else {
                            viewModel.updateNote(noteId, content, isFavorite = isFavorite, createdAt = createdAt)
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 内容输入框 - 卡片样式
            val configuration = LocalConfiguration.current
            val isImeVisible = WindowInsets.isImeVisible
            // 输入法显示时限制在屏幕的50%，收起时可以显示70%
            val maxHeight = if (isImeVisible) {
                (configuration.screenHeightDp * 0.5).dp
            } else {
                (configuration.screenHeightDp * 0.7).dp
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                TextField(
                    value = content,
                    onValueChange = { 
                        content = it
                        scope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = maxHeight)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            }
        }
    }

    // 删除确认对话框
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_note_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    showPasswordDialog = true
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    fun performDelete() {
        scope.launch {
            currentNote?.let { note ->
                viewModel.deleteNote(note)
            }
            showPasswordDialog = false
            masterPassword = ""
            onNavigateBack()
        }
    }

    // 主密码/生物识别验证对话框
    if (showPasswordDialog) {
        val activity = context as? FragmentActivity
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                masterPassword = ""
            },
            title = { Text(stringResource(R.string.verify_identity)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.verify_to_delete))
                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.enter_master_password_confirm)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()) {
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
                            Spacer(modifier = Modifier.width(6.dp))
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
}
