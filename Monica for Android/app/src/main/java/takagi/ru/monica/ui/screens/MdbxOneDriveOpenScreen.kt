package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.OneDriveAccountSession
import takagi.ru.monica.utils.OneDriveAuthManager
import takagi.ru.monica.utils.OneDriveKeePassFileSource
import takagi.ru.monica.utils.toOneDriveUserMessage
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdbxOneDriveOpenScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val operationState by viewModel.operationState.collectAsState()

    val authManager = remember { OneDriveAuthManager(context) }
    var session by remember { mutableStateOf<OneDriveAccountSession?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FileSourceEntry>>(emptyList()) }
    var isLoadingEntries by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileSourceEntry?>(null) }

    var vaultName by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var unlockMethod by remember { mutableStateOf(MdbxUnlockMethod.MASTER_PASSWORD) }
    var keyFile by remember { mutableStateOf<MdbxKeyFileSelection?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }

    val passwordRequired = unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD ||
        unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE
    val keyFileRequired = unlockMethod == MdbxUnlockMethod.KEY_FILE ||
        unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE

    val normalizedMasterPassword = remember(masterPassword) {
        Normalizer.normalize(masterPassword, Normalizer.Form.NFC)
    }
    val normalizedConfirmPassword = remember(confirmPassword) {
        Normalizer.normalize(confirmPassword, Normalizer.Form.NFC)
    }

    val keyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                keyFileError = null
                viewModel.readSelectedKeyFile(uri)
                    .onSuccess { keyFile = it }
                    .onFailure { keyFileError = it.message ?: "无法读取 MDBX 密钥文件" }
            }
        }
    }

    val keyFileCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                keyFileError = null
                viewModel.writeGeneratedKeyFile(uri)
                    .onSuccess { keyFile = it }
                    .onFailure { keyFileError = it.message ?: "无法生成 MDBX 密钥文件" }
            }
        }
    }

    fun loadDirectory(targetPath: String) {
        val activeSession = session ?: return
        scope.launch {
            isLoadingEntries = true
            viewModel.listOneDriveMdbxDirectory(
                accountId = activeSession.accountId,
                currentPath = targetPath
            ).fold(
                onSuccess = { listing ->
                    authError = null
                    currentPath = listing.currentPath
                    entries = listing.entries
                },
                onFailure = { error ->
                    authError = error.toOneDriveUserMessage("OneDrive 目录加载失败")
                }
            )
            isLoadingEntries = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.clearOperationState()
        runCatching { authManager.getCachedSession() }
            .getOrNull()
            ?.let { cached ->
                session = cached
                loadDirectory("")
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mdbx_connect_to_remote_vault)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Card: OneDrive Auth ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "OneDrive 连接",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (session != null) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    session!!.displayName.ifBlank { session!!.username },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { Text(session!!.username) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    } else {
                        Button(
                            onClick = {
                                if (activity == null) return@Button
                                isConnecting = true
                                authError = null
                                scope.launch {
                                    runCatching { authManager.signIn(activity) }
                                        .onSuccess { s ->
                                            session = s
                                            loadDirectory("")
                                        }
                                        .onFailure { e ->
                                            authError = e.toOneDriveUserMessage("OneDrive 登录失败")
                                        }
                                    isConnecting = false
                                }
                            },
                            enabled = !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.Cloud, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("登录 Microsoft 账户")
                        }
                        authError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // === Card: Browse Files (shown after auth) ===
            AnimatedVisibility(
                visible = session != null,
                enter = expandVertically() + fadeIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.mdbx_select_remote_file),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        val parent = OneDriveKeePassFileSource.parentPathOf(currentPath)
                                        loadDirectory(parent)
                                        selectedFile = null
                                    },
                                    enabled = currentPath.isNotBlank()
                                ) {
                                    Icon(Icons.Default.ArrowUpward, "上级目录")
                                }
                                IconButton(onClick = { loadDirectory(currentPath) }) {
                                    Icon(Icons.Default.Refresh, "刷新")
                                }
                            }
                        }

                        if (currentPath.isNotBlank()) {
                            Text(
                                "/$currentPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (isLoadingEntries) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else if (entries.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.mdbx_no_mdbx_files),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(entries, key = { it.path }) { entry ->
                                        val isMdbxFile = !entry.isDirectory &&
                                            entry.name.endsWith(".mdbx", ignoreCase = true)
                                        val isSelected = selectedFile?.path == entry.path

                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    entry.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        entry.isDirectory || isMdbxFile -> MaterialTheme.colorScheme.onSurface
                                                        else -> MaterialTheme.colorScheme.outlineVariant
                                                    }
                                                )
                                            },
                                            leadingContent = {
                                                Icon(
                                                    if (entry.isDirectory) Icons.Default.Folder
                                                    else Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        entry.isDirectory -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        isMdbxFile -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        else -> MaterialTheme.colorScheme.outlineVariant
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            trailingContent = {
                                                when {
                                                    entry.isDirectory -> Icon(
                                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    isSelected -> Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.clickable(
                                                enabled = entry.isDirectory || isMdbxFile
                                            ) {
                                                if (entry.isDirectory) {
                                                    loadDirectory(entry.path)
                                                    selectedFile = null
                                                } else if (isMdbxFile) {
                                                    selectedFile = entry
                                                    if (vaultName.isBlank()) {
                                                        vaultName = entry.name.removeSuffix(".mdbx")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        selectedFile?.let { file ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "${stringResource(R.string.mdbx_webdav_selected_file)}: ${file.name}",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }

            // === Vault settings (only after file selected) ===
            AnimatedVisibility(
                visible = selectedFile != null,
                enter = expandVertically() + fadeIn()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.mdbx_vault_settings),
                                style = MaterialTheme.typography.titleMedium
                            )
                            MdbxVaultNameField(
                                vaultName = vaultName,
                                onVaultNameChange = { vaultName = it }
                            )
                            MdbxPasswordFieldSection(
                                masterPassword = masterPassword,
                                onMasterPasswordChange = { masterPassword = it },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = { confirmPassword = it },
                                passwordRequired = passwordRequired
                            )
                        }
                    }

                    MdbxUnlockMethodSection(
                        unlockMethod = unlockMethod,
                        onUnlockMethodChange = { unlockMethod = it }
                    )

                    MdbxKeyFileSection(
                        keyFile = keyFile,
                        keyFileError = keyFileError,
                        keyFileRequired = keyFileRequired,
                        onPickKeyFile = { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                        onGenerateKeyFile = { keyFileCreateLauncher.launch("monica-mdbx.key") }
                    )
                }
            }

            // === Submit Button ===
            val isFormValid = session != null &&
                selectedFile != null &&
                vaultName.isNotBlank() &&
                (!passwordRequired || (
                    normalizedMasterPassword.isNotBlank() &&
                        normalizedMasterPassword == normalizedConfirmPassword
                    )) &&
                (!keyFileRequired || keyFile != null) &&
                operationState !is MdbxViewModel.OperationState.Loading

            Button(
                onClick = {
                    val s = session ?: return@Button
                    val file = selectedFile ?: return@Button
                    viewModel.connectToOneDriveVault(
                        name = vaultName,
                        masterPassword = masterPassword,
                        unlockMethod = unlockMethod,
                        keyFile = keyFile,
                        tigaMode = MdbxTigaMode.MULTI,
                        accountId = s.accountId,
                        accountLabel = s.displayName.ifBlank { s.username },
                        remoteFilePath = file.path,
                        description = null
                    )
                },
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (operationState is MdbxViewModel.OperationState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mdbx_creating_vault))
                } else {
                    Text(stringResource(R.string.mdbx_connect_to_remote_vault))
                }
            }

            MdbxOperationFeedback(operationState)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
