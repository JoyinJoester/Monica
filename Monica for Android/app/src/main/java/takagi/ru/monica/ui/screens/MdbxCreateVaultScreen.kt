package takagi.ru.monica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.WebDavKeePassFileSource
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdbxCreateVaultScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val operationState by viewModel.operationState.collectAsState()

    // Storage fields
    var storageMode by remember { mutableStateOf(MdbxCreateStorageMode.LOCAL_CREATE) }
    var selectedLocalUri by remember { mutableStateOf<Uri?>(null) }
    var customDirectoryUri by remember { mutableStateOf<Uri?>(null) }

    // WebDAV fields
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var remoteDirectory by remember { mutableStateOf("") }
    var showWebDavPassword by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.NotTested) }

    // WebDAV directory browser
    var webDavCurrentPath by remember { mutableStateOf("") }
    var webDavEntries by remember { mutableStateOf<List<FileSourceEntry>>(emptyList()) }
    var webDavIsLoadingEntries by remember { mutableStateOf(false) }
    var selectedWebDavFile by remember { mutableStateOf<FileSourceEntry?>(null) }

    // Tiga mode
    var selectedTigaMode by remember { mutableStateOf(MdbxTigaMode.MULTI) }

    // Vault settings
    var vaultName by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showMasterPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var unlockMethod by remember { mutableStateOf(MdbxUnlockMethod.MASTER_PASSWORD) }
    var keyFile by remember { mutableStateOf<MdbxKeyFileSelection?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }
    val sourceFamily = storageMode.toSourceFamily()
    val actionMode = storageMode.toActionMode()
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

    val isStorageValid = when (storageMode) {
        MdbxCreateStorageMode.LOCAL_CREATE -> true
        MdbxCreateStorageMode.LOCAL_CUSTOM_DIR -> customDirectoryUri != null
        MdbxCreateStorageMode.LOCAL_OPEN -> selectedLocalUri != null
        MdbxCreateStorageMode.WEBDAV_CREATE -> serverUrl.isNotBlank() &&
            username.isNotBlank() && webDavPassword.isNotBlank() &&
            connectionState is ConnectionState.Connected
        MdbxCreateStorageMode.WEBDAV_OPEN -> serverUrl.isNotBlank() &&
            username.isNotBlank() && webDavPassword.isNotBlank() &&
            connectionState is ConnectionState.Connected &&
            selectedWebDavFile != null
    }
    val isFormValid = isStorageValid &&
        vaultName.isNotBlank() &&
        (!passwordRequired || (
            normalizedMasterPassword.isNotBlank() &&
                normalizedMasterPassword == normalizedConfirmPassword
            )) &&
        (!keyFileRequired || keyFile != null)

    val localFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedLocalUri = uri
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) customDirectoryUri = uri
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

    // Reset operation state after navigation
    LaunchedEffect(Unit) {
        viewModel.clearOperationState()
    }

    val loadWebDavDirectory: (String?) -> Unit = { path ->
        scope.launch {
            webDavIsLoadingEntries = true
            val result = viewModel.listWebDavDirectory(
                serverUrl = serverUrl,
                username = username,
                password = webDavPassword,
                path = path
            )
            result.onSuccess { entries ->
                webDavEntries = entries
                webDavCurrentPath = path ?: ""
            }.onFailure {
                webDavEntries = emptyList()
            }
            webDavIsLoadingEntries = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mdbx_create_vault_title)) },
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
                .padding(16.dp)
        ) {
            // === Section 1: Storage Configuration ===
            Text(
                stringResource(R.string.mdbx_storage_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MdbxCompactChoiceCard(
                    title = "本地文件夹/文件",
                    description = "设备文件或指定文件夹",
                    icon = Icons.Default.Folder,
                    selected = sourceFamily == MdbxSourceFamily.LOCAL,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        storageMode = when (actionMode) {
                            MdbxActionMode.CREATE -> MdbxCreateStorageMode.LOCAL_CREATE
                            MdbxActionMode.OPEN_EXISTING -> MdbxCreateStorageMode.LOCAL_OPEN
                        }
                    }
                )
                MdbxCompactChoiceCard(
                    title = "WebDAV",
                    description = "远程同步目录",
                    icon = Icons.Default.Cloud,
                    selected = sourceFamily == MdbxSourceFamily.WEBDAV,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        storageMode = when (actionMode) {
                            MdbxActionMode.CREATE -> MdbxCreateStorageMode.WEBDAV_CREATE
                            MdbxActionMode.OPEN_EXISTING -> MdbxCreateStorageMode.WEBDAV_OPEN
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MdbxCompactChoiceCard(
                    title = "新建",
                    description = if (sourceFamily == MdbxSourceFamily.LOCAL) {
                        "创建新的 .mdbx"
                    } else {
                        stringResource(R.string.mdbx_webdav_create_desc)
                    },
                    icon = Icons.Default.CreateNewFolder,
                    selected = actionMode == MdbxActionMode.CREATE,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        storageMode = when (sourceFamily) {
                            MdbxSourceFamily.LOCAL -> MdbxCreateStorageMode.LOCAL_CREATE
                            MdbxSourceFamily.WEBDAV -> MdbxCreateStorageMode.WEBDAV_CREATE
                        }
                    }
                )
                MdbxCompactChoiceCard(
                    title = "连接已有",
                    description = if (sourceFamily == MdbxSourceFamily.LOCAL) {
                        stringResource(R.string.mdbx_select_local_file)
                    } else {
                        stringResource(R.string.mdbx_webdav_open_desc)
                    },
                    icon = Icons.Default.FolderOpen,
                    selected = actionMode == MdbxActionMode.OPEN_EXISTING,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        storageMode = when (sourceFamily) {
                            MdbxSourceFamily.LOCAL -> MdbxCreateStorageMode.LOCAL_OPEN
                            MdbxSourceFamily.WEBDAV -> MdbxCreateStorageMode.WEBDAV_OPEN
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // --- Mode-specific sub-sections ---

            // LOCAL_CREATE / LOCAL_CUSTOM_DIR: local destination picker
            if (storageMode == MdbxCreateStorageMode.LOCAL_CREATE ||
                storageMode == MdbxCreateStorageMode.LOCAL_CUSTOM_DIR) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    storageMode = if (storageMode == MdbxCreateStorageMode.LOCAL_CUSTOM_DIR) {
                                        MdbxCreateStorageMode.LOCAL_CREATE
                                    } else {
                                        MdbxCreateStorageMode.LOCAL_CUSTOM_DIR
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = storageMode == MdbxCreateStorageMode.LOCAL_CUSTOM_DIR,
                                onCheckedChange = { checked ->
                                    storageMode = if (checked) {
                                        MdbxCreateStorageMode.LOCAL_CUSTOM_DIR
                                    } else {
                                        MdbxCreateStorageMode.LOCAL_CREATE
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "保存到指定本地文件夹",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    customDirectoryUri?.lastPathSegment
                                        ?: stringResource(R.string.mdbx_local_create_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (storageMode == MdbxCreateStorageMode.LOCAL_CUSTOM_DIR) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { directoryPickerLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.mdbx_select_directory))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // LOCAL_OPEN: file picker
            if (storageMode == MdbxCreateStorageMode.LOCAL_OPEN) {
                OutlinedButton(
                    onClick = { localFilePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mdbx_open_local_vault_button))
                }
                selectedLocalUri?.let { uri ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        uri.lastPathSegment.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // WEBDAV_CREATE / WEBDAV_OPEN: shared credentials
            if (storageMode == MdbxCreateStorageMode.WEBDAV_CREATE ||
                storageMode == MdbxCreateStorageMode.WEBDAV_OPEN) {

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        connectionState = ConnectionState.NotTested
                        selectedWebDavFile = null
                        webDavEntries = emptyList()
                    },
                    label = { Text(stringResource(R.string.mdbx_webdav_url)) },
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        connectionState = ConnectionState.NotTested
                        selectedWebDavFile = null
                        webDavEntries = emptyList()
                    },
                    label = { Text(stringResource(R.string.mdbx_webdav_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = webDavPassword,
                    onValueChange = {
                        webDavPassword = it
                        connectionState = ConnectionState.NotTested
                        selectedWebDavFile = null
                        webDavEntries = emptyList()
                    },
                    label = { Text(stringResource(R.string.mdbx_webdav_password)) },
                    visualTransformation = if (showWebDavPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showWebDavPassword = !showWebDavPassword }) {
                            Icon(
                                if (showWebDavPassword) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Test Connection button
                Button(
                    onClick = {
                        connectionState = ConnectionState.Testing
                        scope.launch {
                            val result = viewModel.testWebDavConnection(
                                serverUrl = serverUrl,
                                username = username,
                                password = webDavPassword
                            )
                            connectionState = if (result.isSuccess) {
                                ConnectionState.Connected
                            } else {
                                ConnectionState.Failed(
                                    result.exceptionOrNull()?.message ?: "Unknown error"
                                )
                            }
                        }
                    },
                    enabled = serverUrl.isNotBlank() && username.isNotBlank() &&
                        webDavPassword.isNotBlank() && connectionState !is ConnectionState.Testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (connectionState) {
                        is ConnectionState.Testing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.mdbx_test_connection))
                        }
                        is ConnectionState.Connected -> {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.mdbx_connection_success))
                        }
                        else -> Text(stringResource(R.string.mdbx_test_connection))
                    }
                }

                if (connectionState is ConnectionState.Connected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.mdbx_connection_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (connectionState is ConnectionState.Failed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${stringResource(R.string.mdbx_connection_failed)}: ${(connectionState as ConnectionState.Failed).error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // WEBDAV_CREATE: remote directory field
            if (storageMode == MdbxCreateStorageMode.WEBDAV_CREATE &&
                connectionState is ConnectionState.Connected) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = remoteDirectory,
                    onValueChange = { remoteDirectory = it },
                    label = { Text(stringResource(R.string.mdbx_webdav_directory)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // WEBDAV_OPEN: directory browser
            if (storageMode == MdbxCreateStorageMode.WEBDAV_OPEN &&
                connectionState is ConnectionState.Connected) {
                Spacer(modifier = Modifier.height(8.dp))

                // Load root directory on first connect
                LaunchedEffect(connectionState) {
                    if (connectionState is ConnectionState.Connected && webDavEntries.isEmpty()) {
                        loadWebDavDirectory("")
                    }
                }

                // Navigation bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.mdbx_select_remote_file),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        IconButton(
                            onClick = {
                                val parent = WebDavKeePassFileSource.parentPathOf(webDavCurrentPath)
                                loadWebDavDirectory(parent)
                                selectedWebDavFile = null
                            },
                            enabled = webDavCurrentPath.isNotBlank()
                        ) {
                            Icon(Icons.Default.ArrowUpward, stringResource(R.string.mdbx_webdav_parent_dir))
                        }
                        IconButton(onClick = { loadWebDavDirectory(webDavCurrentPath) }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.mdbx_webdav_refresh))
                        }
                    }
                }

                // Current path display
                if (webDavCurrentPath.isNotBlank()) {
                    Text(
                        "/$webDavCurrentPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Directory listing
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (webDavIsLoadingEntries) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (webDavEntries.isEmpty()) {
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
                            items(webDavEntries, key = { it.path }) { entry ->
                                val isMdbxFile = !entry.isDirectory &&
                                    entry.name.endsWith(".mdbx", ignoreCase = true)
                                val isSelected = selectedWebDavFile?.path == entry.path

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = entry.isDirectory || isMdbxFile) {
                                            if (entry.isDirectory) {
                                                loadWebDavDirectory(entry.path)
                                                selectedWebDavFile = null
                                            } else if (isMdbxFile) {
                                                selectedWebDavFile = entry
                                                if (vaultName.isBlank()) {
                                                    vaultName = entry.name.removeSuffix(".mdbx")
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            entry.isDirectory || isMdbxFile -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (entry.isDirectory) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected file info
                selectedWebDavFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${stringResource(R.string.mdbx_webdav_selected_file)}: ${file.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // === Section 2: Tiga Mode Selection (only for create modes)
            if (storageMode != MdbxCreateStorageMode.LOCAL_OPEN &&
                storageMode != MdbxCreateStorageMode.WEBDAV_OPEN) {
            Text(
                stringResource(R.string.mdbx_tiga_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            MdbxTigaModeCard(
                title = stringResource(R.string.mdbx_tiga_power_title),
                description = stringResource(R.string.mdbx_tiga_power_desc),
                icon = Icons.Default.Shield,
                selected = selectedTigaMode == MdbxTigaMode.POWER,
                onClick = { selectedTigaMode = MdbxTigaMode.POWER }
            )
            Spacer(modifier = Modifier.height(8.dp))

            MdbxTigaModeCard(
                title = stringResource(R.string.mdbx_tiga_multi_title),
                description = stringResource(R.string.mdbx_tiga_multi_desc),
                icon = Icons.Default.Security,
                selected = selectedTigaMode == MdbxTigaMode.MULTI,
                onClick = { selectedTigaMode = MdbxTigaMode.MULTI }
            )
            Spacer(modifier = Modifier.height(8.dp))

            MdbxTigaModeCard(
                title = stringResource(R.string.mdbx_tiga_sky_title),
                description = stringResource(R.string.mdbx_tiga_sky_desc),
                icon = Icons.Default.Bolt,
                selected = selectedTigaMode == MdbxTigaMode.SKY,
                onClick = { selectedTigaMode = MdbxTigaMode.SKY }
            )

            } // end Tiga mode selection (create modes only)

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // === Section 3: Vault Settings ===
            Text(
                stringResource(R.string.mdbx_vault_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = vaultName,
                onValueChange = { vaultName = it },
                label = { Text(stringResource(R.string.mdbx_vault_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it },
                label = { Text(stringResource(R.string.mdbx_master_password)) },
                visualTransformation = if (showMasterPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showMasterPassword = !showMasterPassword }) {
                        Icon(
                            if (showMasterPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                enabled = passwordRequired,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.mdbx_confirm_password)) },
                visualTransformation = if (showConfirmPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            if (showConfirmPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                isError = confirmPassword.isNotEmpty() && normalizedMasterPassword != normalizedConfirmPassword,
                supportingText = if (confirmPassword.isNotEmpty() && normalizedMasterPassword != normalizedConfirmPassword) {
                    { Text(stringResource(R.string.mdbx_password_mismatch)) }
                } else {
                    { Text("支持中文主密码；MDBX 会按 Unicode NFC 处理。") }
                },
                singleLine = true,
                enabled = passwordRequired,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "解锁方式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            MdbxUnlockMethodCard(
                title = "主密码",
                description = "只用主密码解锁",
                selected = unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD,
                onClick = { unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD }
            )
            Spacer(modifier = Modifier.height(8.dp))
            MdbxUnlockMethodCard(
                title = "密钥文件",
                description = "只用 MDBX key file 解锁",
                selected = unlockMethod == MdbxUnlockMethod.KEY_FILE,
                onClick = { unlockMethod = MdbxUnlockMethod.KEY_FILE }
            )
            Spacer(modifier = Modifier.height(8.dp))
            MdbxUnlockMethodCard(
                title = "主密码 + 密钥文件",
                description = "两者同时正确才可解锁",
                selected = unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE,
                onClick = { unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE }
            )

            if (keyFileRequired) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    keyFile?.name ?: "MDBX 密钥文件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    keyFile?.let { "SHA-256 ${it.shortFingerprint}..." }
                                        ?: "选择已有密钥文件，或生成新的 .key 文件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选择")
                            }
                            Button(
                                onClick = { keyFileCreateLauncher.launch("monica-mdbx.key") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("生成")
                            }
                        }
                        keyFileError?.let { error ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === Section 4: Create Button ===
            val createButtonLabel = when (storageMode) {
                MdbxCreateStorageMode.WEBDAV_OPEN -> stringResource(R.string.mdbx_connect_to_remote_vault)
                MdbxCreateStorageMode.LOCAL_OPEN -> stringResource(R.string.mdbx_open_vault_button)
                else -> stringResource(R.string.mdbx_create_vault_button)
            }
            Button(
                onClick = {
                    when (storageMode) {
                        MdbxCreateStorageMode.LOCAL_CREATE -> {
                            viewModel.createLocalVault(
                                name = vaultName,
                                masterPassword = masterPassword,
                                unlockMethod = unlockMethod,
                                keyFile = keyFile,
                                tigaMode = selectedTigaMode,
                                description = null
                            )
                        }
                        MdbxCreateStorageMode.LOCAL_CUSTOM_DIR -> {
                            customDirectoryUri?.let { uri ->
                                viewModel.createLocalVault(
                                    name = vaultName,
                                    masterPassword = masterPassword,
                                    unlockMethod = unlockMethod,
                                    keyFile = keyFile,
                                    tigaMode = selectedTigaMode,
                                    description = null,
                                    customDirectoryUri = uri
                                )
                            }
                        }
                        MdbxCreateStorageMode.LOCAL_OPEN -> {
                            selectedLocalUri?.let { uri ->
                                viewModel.importLocalVault(
                                    sourceUri = uri,
                                    name = vaultName,
                                    masterPassword = masterPassword,
                                    unlockMethod = unlockMethod,
                                    keyFile = keyFile,
                                    tigaMode = selectedTigaMode,
                                    description = null
                                )
                            }
                        }
                        MdbxCreateStorageMode.WEBDAV_CREATE -> {
                            viewModel.createWebDavVault(
                                name = vaultName,
                                masterPassword = masterPassword,
                                unlockMethod = unlockMethod,
                                keyFile = keyFile,
                                tigaMode = selectedTigaMode,
                                serverUrl = serverUrl,
                                username = username,
                                webDavPassword = webDavPassword,
                                remoteDirectoryPath = remoteDirectory.ifBlank { null },
                                description = null
                            )
                        }
                        MdbxCreateStorageMode.WEBDAV_OPEN -> {
                            selectedWebDavFile?.let { file ->
                                viewModel.connectToExistingWebDavVault(
                                    name = vaultName,
                                    masterPassword = masterPassword,
                                    unlockMethod = unlockMethod,
                                    keyFile = keyFile,
                                    tigaMode = selectedTigaMode,
                                    serverUrl = serverUrl,
                                    username = username,
                                    webDavPassword = webDavPassword,
                                    remoteFilePath = file.path,
                                    description = null
                                )
                            }
                        }
                    }
                },
                enabled = isFormValid && operationState !is MdbxViewModel.OperationState.Loading,
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
                    Text(createButtonLabel)
                }
            }

            // Operation feedback
            when (val state = operationState) {
                is MdbxViewModel.OperationState.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is MdbxViewModel.OperationState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MdbxStorageModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MdbxCompactChoiceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MdbxUnlockMethodCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Key,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MdbxTigaModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class MdbxCreateStorageMode {
    LOCAL_CREATE,
    LOCAL_CUSTOM_DIR,
    LOCAL_OPEN,
    WEBDAV_CREATE,
    WEBDAV_OPEN
}

private enum class MdbxSourceFamily {
    LOCAL,
    WEBDAV
}

private enum class MdbxActionMode {
    CREATE,
    OPEN_EXISTING
}

private fun MdbxCreateStorageMode.toSourceFamily(): MdbxSourceFamily = when (this) {
    MdbxCreateStorageMode.LOCAL_CREATE,
    MdbxCreateStorageMode.LOCAL_CUSTOM_DIR,
    MdbxCreateStorageMode.LOCAL_OPEN -> MdbxSourceFamily.LOCAL
    MdbxCreateStorageMode.WEBDAV_CREATE,
    MdbxCreateStorageMode.WEBDAV_OPEN -> MdbxSourceFamily.WEBDAV
}

private fun MdbxCreateStorageMode.toActionMode(): MdbxActionMode = when (this) {
    MdbxCreateStorageMode.LOCAL_CREATE,
    MdbxCreateStorageMode.LOCAL_CUSTOM_DIR,
    MdbxCreateStorageMode.WEBDAV_CREATE -> MdbxActionMode.CREATE
    MdbxCreateStorageMode.LOCAL_OPEN,
    MdbxCreateStorageMode.WEBDAV_OPEN -> MdbxActionMode.OPEN_EXISTING
}

private sealed class ConnectionState {
    data object NotTested : ConnectionState()
    data object Testing : ConnectionState()
    data object Connected : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}
