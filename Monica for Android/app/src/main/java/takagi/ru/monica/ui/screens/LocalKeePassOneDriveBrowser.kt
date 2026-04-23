package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.KeePassCipherAlgorithm
import takagi.ru.monica.data.KeePassDatabaseCreationOptions
import takagi.ru.monica.data.KeePassFormatVersion
import takagi.ru.monica.data.KeePassKdfAlgorithm
import takagi.ru.monica.ui.components.OutlinedTextField
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.OneDriveAccountSession
import takagi.ru.monica.utils.OneDriveAuthManager
import takagi.ru.monica.utils.OneDriveKeePassFileSource
import takagi.ru.monica.viewmodel.LocalKeePassViewModel

private enum class KeepassOneDriveConnectionState {
    NotConnected,
    Connecting,
    Connected,
    Failed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepassOneDriveBrowserBottomSheet(
    viewModel: LocalKeePassViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { OneDriveAuthManager(context) }

    var session by remember { mutableStateOf<OneDriveAccountSession?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FileSourceEntry>>(emptyList()) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var isLoadingEntries by remember { mutableStateOf(false) }
    var selectedDatabaseEntry by remember { mutableStateOf<FileSourceEntry?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateDatabaseDialog by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf(KeepassOneDriveConnectionState.NotConnected) }

    fun loadDirectory(targetPath: String = currentPath) {
        val activeSession = session ?: return
        coroutineScope.launch {
            isLoadingEntries = true
            browserError = null
            val result = viewModel.listOneDriveDirectory(
                accountId = activeSession.accountId,
                currentPath = targetPath
            )
            result.fold(
                onSuccess = { listing ->
                    currentPath = listing.currentPath
                    entries = listing.entries
                    connectionState = KeepassOneDriveConnectionState.Connected
                },
                onFailure = { error ->
                    browserError = error.message ?: context.getString(R.string.keepass_onedrive_load_files_failed)
                    connectionState = KeepassOneDriveConnectionState.Failed
                }
            )
            isLoadingEntries = false
            isConnecting = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { authManager.getCachedSession() }
            .getOrNull()
            ?.let { cached ->
                session = cached
                currentPath = ""
                loadDirectory("")
            }
    }

    val currentPathLabel = if (currentPath.isBlank()) {
        stringResource(R.string.keepass_webdav_root_path)
    } else {
        "/$currentPath"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.keepass_onedrive_attach_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                stringResource(R.string.keepass_onedrive_browser_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session?.displayName ?: stringResource(R.string.keepass_onedrive_not_connected),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                session?.username?.ifBlank {
                                    stringResource(R.string.keepass_onedrive_sign_in_hint)
                                } ?: stringResource(R.string.keepass_onedrive_sign_in_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (activity == null) {
                                    browserError = context.getString(R.string.keepass_onedrive_activity_missing)
                                    connectionState = KeepassOneDriveConnectionState.Failed
                                    return@Button
                                }
                                connectionState = KeepassOneDriveConnectionState.Connecting
                                isConnecting = true
                                browserError = null
                                coroutineScope.launch {
                                    runCatching { authManager.signIn(activity) }
                                        .onSuccess { result ->
                                            session = result
                                            currentPath = ""
                                            loadDirectory("")
                                        }
                                        .onFailure { error ->
                                            isConnecting = false
                                            connectionState = KeepassOneDriveConnectionState.Failed
                                            browserError = error.message ?: context.getString(R.string.keepass_onedrive_sign_in_failed)
                                        }
                                }
                            },
                            enabled = !isConnecting && !isLoadingEntries
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (session == null) stringResource(R.string.keepass_onedrive_sign_in_action)
                                else stringResource(R.string.keepass_onedrive_switch_account)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.keepass_webdav_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when (connectionState) {
                            KeepassOneDriveConnectionState.NotConnected -> stringResource(R.string.keepass_webdav_status_not_connected)
                            KeepassOneDriveConnectionState.Connecting -> stringResource(R.string.keepass_webdav_status_connecting)
                            KeepassOneDriveConnectionState.Connected -> stringResource(R.string.keepass_webdav_status_connected)
                            KeepassOneDriveConnectionState.Failed -> stringResource(R.string.keepass_webdav_status_failed)
                        },
                        color = when (connectionState) {
                            KeepassOneDriveConnectionState.Connected -> MaterialTheme.colorScheme.primary
                            KeepassOneDriveConnectionState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        stringResource(R.string.keepass_onedrive_current_path, currentPathLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            browserError?.let { error ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            AnimatedVisibility(visible = session != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { loadDirectory(currentPath) },
                            enabled = !isLoadingEntries,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.refresh))
                        }
                        OutlinedButton(
                            onClick = { loadDirectory(OneDriveKeePassFileSource.parentPathOf(currentPath)) },
                            enabled = !isLoadingEntries,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.keepass_webdav_go_parent))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateFolderDialog = true },
                            enabled = !isLoadingEntries,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.keepass_webdav_create_folder_confirm))
                        }
                        Button(
                            onClick = { showCreateDatabaseDialog = true },
                            enabled = !isLoadingEntries,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.keepass_webdav_create_confirm))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (isLoadingEntries) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.keepass_remote_loading_files))
                                }
                            } else if (entries.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.keepass_onedrive_empty_directory),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                entries.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (entry.isDirectory) {
                                                    loadDirectory(entry.path)
                                                } else {
                                                    selectedDatabaseEntry = entry
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Link,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                if (entry.isDirectory) "/${entry.path}" else entry.path,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog && session != null) {
        CreateOneDriveFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { folderName ->
                coroutineScope.launch {
                    val result = viewModel.createOneDriveFolder(
                        accountId = session!!.accountId,
                        currentPath = currentPath,
                        folderName = folderName
                    )
                    result.fold(
                        onSuccess = { listing ->
                            currentPath = listing.currentPath
                            entries = listing.entries
                            showCreateFolderDialog = false
                        },
                        onFailure = { error ->
                            browserError = error.message ?: context.getString(R.string.keepass_webdav_create_folder_failed)
                        }
                    )
                }
            }
        )
    }

    selectedDatabaseEntry?.let { entry ->
        val activeSession = session
        if (activeSession != null) {
            AttachExistingOneDriveDatabaseDialog(
                entry = entry,
                onDismiss = { selectedDatabaseEntry = null },
                onAttach = { displayName, databasePassword, keyFileUri, description ->
                    viewModel.addOneDriveDatabase(
                        name = displayName,
                        accountId = activeSession.accountId,
                        accountLabel = activeSession.username.ifBlank { activeSession.displayName },
                        remotePath = entry.path,
                        databasePassword = databasePassword,
                        keyFileUri = keyFileUri,
                        description = description
                    )
                    selectedDatabaseEntry = null
                    onDismiss()
                }
            )
        }
    }

    if (showCreateDatabaseDialog && session != null) {
        CreateOneDriveDatabaseDialog(
            currentPath = currentPath,
            onDismiss = { showCreateDatabaseDialog = false },
            onGenerateKeyFile = { uri -> viewModel.generateKeyFile(uri) },
            onCreate = { name, password, keyFileUri, options, description ->
                viewModel.createOneDriveDatabase(
                    directoryPath = currentPath,
                    name = name,
                    accountId = session!!.accountId,
                    accountLabel = session!!.username.ifBlank { session!!.displayName },
                    databasePassword = password,
                    keyFileUri = keyFileUri,
                    creationOptions = options,
                    description = description
                )
                showCreateDatabaseDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
private fun CreateOneDriveFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keepass_onedrive_create_folder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.keepass_onedrive_create_folder_message))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(folderName.trim()) }, enabled = folderName.isNotBlank()) {
                Text(stringResource(R.string.keepass_webdav_create_folder_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AttachExistingOneDriveDatabaseDialog(
    entry: FileSourceEntry,
    onDismiss: () -> Unit,
    onAttach: (displayName: String, databasePassword: String, keyFileUri: Uri?, description: String?) -> Unit
) {
    var displayName by remember { mutableStateOf(entry.name.removeSuffix(".kdbx")) }
    var databasePassword by remember { mutableStateOf("") }
    var showDatabasePassword by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var keyFileUri by remember { mutableStateOf<Uri?>(null) }
    var keyFileName by remember { mutableStateOf("") }

    val keyFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            keyFileUri = it
            keyFileName = it.lastPathSegment?.substringAfterLast("/") ?: "keyfile"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keepass_onedrive_attach_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(entry.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.database_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = databasePassword,
                    onValueChange = { databasePassword = it },
                    label = { Text(stringResource(R.string.keepass_webdav_database_password)) },
                    placeholder = { Text(stringResource(R.string.keepass_webdav_database_password_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showDatabasePassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showDatabasePassword = !showDatabasePassword }) {
                            Icon(
                                if (showDatabasePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = keyFileName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.local_keepass_key_file_optional)) },
                    placeholder = { Text(stringResource(R.string.local_keepass_key_file_tap_to_select)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                    trailingIcon = {
                        IconButton(onClick = { keyFilePickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                    }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    placeholder = { Text(stringResource(R.string.description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAttach(displayName.trim(), databasePassword, keyFileUri, description.takeIf { it.isNotBlank() })
                },
                enabled = displayName.isNotBlank() && (databasePassword.isNotBlank() || keyFileUri != null)
            ) {
                Text(stringResource(R.string.keepass_onedrive_confirm_attach))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CreateOneDriveDatabaseDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onGenerateKeyFile: (Uri) -> Unit,
    onCreate: (
        name: String,
        password: String,
        keyFileUri: Uri?,
        options: KeePassDatabaseCreationOptions,
        description: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var useKeyFile by remember { mutableStateOf(false) }
    var keyFileUri by remember { mutableStateOf<Uri?>(null) }
    var keyFileName by remember { mutableStateOf("") }
    var formatVersion by remember { mutableStateOf(KeePassFormatVersion.KDBX4) }
    var cipherAlgorithm by remember { mutableStateOf(KeePassCipherAlgorithm.AES) }
    var kdfAlgorithm by remember { mutableStateOf(KeePassKdfAlgorithm.ARGON2D) }
    var transformRounds by remember { mutableStateOf("8") }
    var memoryMb by remember {
        mutableStateOf((KeePassDatabaseCreationOptions.DEFAULT_ARGON_MEMORY_BYTES / 1024L / 1024L).toString())
    }
    var parallelism by remember { mutableStateOf("2") }
    var showAdvancedCryptoOptions by remember { mutableStateOf(false) }

    val keyFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            keyFileUri = it
            keyFileName = it.lastPathSegment?.substringAfterLast("/") ?: "keyfile"
        }
    }
    val createKeyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri: Uri? ->
        uri?.let {
            onGenerateKeyFile(it)
            keyFileUri = it
            keyFileName = it.lastPathSegment?.substringAfterLast("/") ?: "new_keyfile.xml"
        }
    }

    val availableCipherOptions = remember(formatVersion) {
        if (formatVersion == KeePassFormatVersion.KDBX3) {
            listOf(KeePassCipherAlgorithm.AES, KeePassCipherAlgorithm.TWOFISH)
        } else {
            listOf(KeePassCipherAlgorithm.AES, KeePassCipherAlgorithm.CHACHA20, KeePassCipherAlgorithm.TWOFISH)
        }
    }
    val availableKdfOptions = remember(formatVersion) {
        if (formatVersion == KeePassFormatVersion.KDBX3) {
            listOf(KeePassKdfAlgorithm.AES_KDF)
        } else {
            listOf(KeePassKdfAlgorithm.ARGON2D, KeePassKdfAlgorithm.ARGON2ID, KeePassKdfAlgorithm.AES_KDF)
        }
    }

    LaunchedEffect(formatVersion) {
        if (cipherAlgorithm !in availableCipherOptions) cipherAlgorithm = availableCipherOptions.first()
        if (kdfAlgorithm !in availableKdfOptions) kdfAlgorithm = availableKdfOptions.first()
    }

    val roundsValue = transformRounds.toLongOrNull()
    val memoryMbValue = memoryMb.toLongOrNull()
    val parallelismValue = parallelism.toIntOrNull()
    val advancedOptionsValid = roundsValue != null && roundsValue > 0L &&
        (
            kdfAlgorithm == KeePassKdfAlgorithm.AES_KDF ||
                ((memoryMbValue != null && memoryMbValue > 0L) && (parallelismValue != null && parallelismValue > 0))
            )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keepass_onedrive_create_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(
                        R.string.keepass_webdav_create_target_path,
                        if (currentPath.isBlank()) stringResource(R.string.keepass_webdav_root_path) else "/$currentPath"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.keepass_webdav_create_name_label)) },
                    placeholder = { Text(stringResource(R.string.keepass_webdav_create_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.database_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { useKeyFile = !useKeyFile }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.local_keepass_use_key_file))
                            Text(
                                stringResource(R.string.local_keepass_use_key_file_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useKeyFile, onCheckedChange = { useKeyFile = it })
                    }
                }
                AnimatedVisibility(visible = useKeyFile) {
                    OutlinedTextField(
                        value = keyFileName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.local_keepass_key_file)) },
                        placeholder = { Text(stringResource(R.string.local_keepass_key_file_pick_or_generate)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { createKeyFileLauncher.launch("monica.key") }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                                IconButton(onClick = { keyFilePickerLauncher.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                }
                            }
                        }
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { showAdvancedCryptoOptions = !showAdvancedCryptoOptions }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.local_keepass_advanced_crypto_options),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (showAdvancedCryptoOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                AnimatedVisibility(visible = showAdvancedCryptoOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KeepassOneDriveOptionDropdown(
                            label = stringResource(R.string.local_keepass_kdbx_version),
                            selectedText = if (formatVersion == KeePassFormatVersion.KDBX3) {
                                stringResource(R.string.local_keepass_kdbx3)
                            } else {
                                stringResource(R.string.local_keepass_kdbx4)
                            },
                            options = KeePassFormatVersion.entries,
                            optionLabel = {
                                if (it == KeePassFormatVersion.KDBX3) stringResource(R.string.local_keepass_kdbx3)
                                else stringResource(R.string.local_keepass_kdbx4)
                            },
                            onSelected = { formatVersion = it }
                        )
                        KeepassOneDriveOptionDropdown(
                            label = stringResource(R.string.local_keepass_cipher_algorithm),
                            selectedText = keepassOneDriveCipherLabel(cipherAlgorithm),
                            options = availableCipherOptions,
                            optionLabel = { keepassOneDriveCipherLabel(it) },
                            onSelected = { cipherAlgorithm = it }
                        )
                        KeepassOneDriveOptionDropdown(
                            label = stringResource(R.string.local_keepass_kdf_algorithm),
                            selectedText = keepassOneDriveKdfLabel(kdfAlgorithm),
                            options = availableKdfOptions,
                            optionLabel = { keepassOneDriveKdfLabel(it) },
                            onSelected = { kdfAlgorithm = it }
                        )
                        OutlinedTextField(
                            value = transformRounds,
                            onValueChange = { transformRounds = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.local_keepass_transform_rounds)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        AnimatedVisibility(visible = kdfAlgorithm != KeePassKdfAlgorithm.AES_KDF) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = memoryMb,
                                    onValueChange = { memoryMb = it.filter(Char::isDigit) },
                                    label = { Text(stringResource(R.string.local_keepass_kdf_memory_mb)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = parallelism,
                                    onValueChange = { parallelism = it.filter(Char::isDigit) },
                                    label = { Text(stringResource(R.string.local_keepass_kdf_parallelism)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    placeholder = { Text(stringResource(R.string.description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val options = KeePassDatabaseCreationOptions(
                        formatVersion = formatVersion,
                        cipherAlgorithm = cipherAlgorithm,
                        kdfAlgorithm = kdfAlgorithm,
                        transformRounds = roundsValue ?: 8L,
                        memoryBytes = ((memoryMbValue ?: 32L) * 1024L * 1024L),
                        parallelism = parallelismValue ?: 2
                    ).normalized()
                    onCreate(name.trim(), password, if (useKeyFile) keyFileUri else null, options, description.takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank() &&
                    ((password.isNotBlank() && password == confirmPassword) || (useKeyFile && keyFileUri != null)) &&
                    advancedOptionsValid
            ) {
                Text(stringResource(R.string.keepass_onedrive_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> KeepassOneDriveOptionDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun keepassOneDriveCipherLabel(algorithm: KeePassCipherAlgorithm): String {
    return when (algorithm) {
        KeePassCipherAlgorithm.AES -> stringResource(R.string.local_keepass_cipher_aes)
        KeePassCipherAlgorithm.CHACHA20 -> stringResource(R.string.local_keepass_cipher_chacha20)
        KeePassCipherAlgorithm.TWOFISH -> stringResource(R.string.local_keepass_cipher_twofish)
    }
}

@Composable
private fun keepassOneDriveKdfLabel(algorithm: KeePassKdfAlgorithm): String {
    return when (algorithm) {
        KeePassKdfAlgorithm.AES_KDF -> stringResource(R.string.local_keepass_kdf_aes)
        KeePassKdfAlgorithm.ARGON2D -> stringResource(R.string.local_keepass_kdf_argon2d)
        KeePassKdfAlgorithm.ARGON2ID -> stringResource(R.string.local_keepass_kdf_argon2id)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
