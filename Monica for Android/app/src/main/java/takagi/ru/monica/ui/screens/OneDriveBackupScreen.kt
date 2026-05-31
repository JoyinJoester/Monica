package takagi.ru.monica.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.BackupPreferences
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.ui.components.SelectiveBackupCard
import takagi.ru.monica.utils.BackupFile
import takagi.ru.monica.utils.BackupRestoreApplier
import takagi.ru.monica.utils.OneDriveAccountSession
import takagi.ru.monica.utils.OneDriveAuthManager
import takagi.ru.monica.utils.OneDriveBackupConfig
import takagi.ru.monica.utils.OneDriveBackupHelper
import takagi.ru.monica.utils.OneDriveKeePassFileSource
import takagi.ru.monica.utils.RestoreResult
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.isOneDriveAuthTemporarilyUnavailable
import takagi.ru.monica.utils.toOneDriveUserMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private enum class OneDriveBackupConnectionState {
    NotConnected,
    Connecting,
    Connected,
    Failed,
}

private enum class OneDriveRestoreMode {
    MergeLocal,
    ReplaceLocal,
}

private const val ONEDRIVE_BACKUP_LOG_TAG = "OneDriveBackupScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneDriveBackupScreen(
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val backupHelper = remember { OneDriveBackupHelper(context) }
    val authManager = remember { OneDriveAuthManager(context) }
    val webDavHelper = remember { WebDavHelper(context) }

    var session by remember { mutableStateOf<OneDriveAccountSession?>(null) }
    var savedConfig by remember { mutableStateOf<OneDriveBackupConfig?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var browserEntries by remember { mutableStateOf<List<FileSourceEntry>>(emptyList()) }
    var backupList by remember { mutableStateOf<List<BackupFile>>(emptyList()) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var loadingEntries by remember { mutableStateOf(false) }
    var loadingBackups by remember { mutableStateOf(false) }
    var creatingBackup by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf(OneDriveBackupConnectionState.NotConnected) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<BackupFile?>(null) }
    var showRestoreDialogFor by remember { mutableStateOf<BackupFile?>(null) }
    var restoreMode by remember { mutableStateOf(OneDriveRestoreMode.MergeLocal) }
    var restoreGlobalDedup by remember { mutableStateOf(false) }
    var showDecryptPasswordDialogFor by remember { mutableStateOf<BackupFile?>(null) }
    var decryptPassword by remember { mutableStateOf("") }

    var encryptionEnabled by remember { mutableStateOf(false) }
    var encryptionPassword by remember { mutableStateOf("") }
    var encryptionPasswordVisible by remember { mutableStateOf(false) }
    var backupPreferences by remember { mutableStateOf(BackupPreferences()) }

    var passwordCount by remember { mutableStateOf(0) }
    var authenticatorCount by remember { mutableStateOf(0) }
    var documentCount by remember { mutableStateOf(0) }
    var bankCardCount by remember { mutableStateOf(0) }
    var noteCount by remember { mutableStateOf(0) }
    var trashCount by remember { mutableStateOf(0) }
    var localKeePassCount by remember { mutableStateOf(0) }
    var passkeyCount by remember { mutableStateOf(0) }
    val oneDriveReady = session != null && connectionState == OneDriveBackupConnectionState.Connected
    fun currentSessionBackupConfig(): OneDriveBackupConfig? {
        val activeSession = session ?: return null
        return savedConfig?.takeIf { config -> config.accountId == activeSession.accountId }
    }

    val savedConfigForCurrentSession = currentSessionBackupConfig()
    val backupReady = oneDriveReady && savedConfigForCurrentSession != null

    suspend fun refreshBackups() {
        if (currentSessionBackupConfig() == null) {
            Log.i(
                ONEDRIVE_BACKUP_LOG_TAG,
                "Skip refreshBackups: no config for current session, state=$connectionState, " +
                    "session=${session.debugRef()}, savedConfig=${savedConfig.debugRef()}"
            )
            backupList = emptyList()
            return
        }
        Log.d(
            ONEDRIVE_BACKUP_LOG_TAG,
            "Refreshing OneDrive backups, state=$connectionState, session=${session.debugRef()}"
        )
        loadingBackups = true
        backupHelper.listBackups().fold(
            onSuccess = {
                backupList = it
                Log.d(ONEDRIVE_BACKUP_LOG_TAG, "OneDrive backup list loaded, count=${it.size}")
            },
            onFailure = {
                backupList = emptyList()
                browserError = it.toOneDriveUserMessage(context.getString(R.string.keepass_onedrive_load_files_failed))
                connectionState = OneDriveBackupConnectionState.Failed
                Log.w(
                    ONEDRIVE_BACKUP_LOG_TAG,
                    "OneDrive backup list failed, temporaryAuth=${it.isOneDriveAuthTemporarilyUnavailable()}",
                    it
                )
            }
        )
        loadingBackups = false
    }

    suspend fun loadDirectory(targetPath: String) {
        val activeSession = session ?: return
        Log.d(
            ONEDRIVE_BACKUP_LOG_TAG,
            "Loading OneDrive directory, targetIsRoot=${targetPath.isBlank()}, session=${activeSession.debugRef()}"
        )
        loadingEntries = true
        browserError = null
        runCatching {
            backupHelper.listDirectory(activeSession.accountId, targetPath)
        }.onSuccess { entries ->
            browserEntries = entries.filter { it.isDirectory }
            currentPath = targetPath
            connectionState = OneDriveBackupConnectionState.Connected
            Log.d(
                ONEDRIVE_BACKUP_LOG_TAG,
                "OneDrive directory loaded, folders=${browserEntries.size}, targetIsRoot=${targetPath.isBlank()}"
            )
        }.onFailure { error ->
            browserEntries = emptyList()
            browserError = error.toOneDriveUserMessage(context.getString(R.string.keepass_onedrive_load_files_failed))
            connectionState = OneDriveBackupConnectionState.Failed
            Log.w(
                ONEDRIVE_BACKUP_LOG_TAG,
                "OneDrive directory load failed, temporaryAuth=${error.isOneDriveAuthTemporarilyUnavailable()}",
                error
            )
        }
        loadingEntries = false
    }

    suspend fun loadCounts() {
        val database = PasswordDatabase.getDatabase(context)
        passwordCount = passwordRepository.getLocalEntriesCount()
        authenticatorCount = secureItemRepository.getLocalItemCountByType(takagi.ru.monica.data.ItemType.TOTP)
        documentCount = secureItemRepository.getLocalItemCountByType(takagi.ru.monica.data.ItemType.DOCUMENT)
        bankCardCount = secureItemRepository.getLocalItemCountByType(takagi.ru.monica.data.ItemType.BANK_CARD)
        noteCount = secureItemRepository.getLocalItemCountByType(takagi.ru.monica.data.ItemType.NOTE)
        trashCount = passwordRepository.getLocalDeletedEntriesCount() + secureItemRepository.getLocalDeletedItemCount()
        passkeyCount = withContext(Dispatchers.IO) { database.passkeyDao().getAllPasskeysSync().size }
        localKeePassCount = withContext(Dispatchers.IO) { database.localKeePassDatabaseDao().getAllDatabasesSync().size }
    }

    fun saveCurrentFolderAsBackupDirectory() {
        val activeSession = session ?: return
        backupHelper.saveConfig(activeSession, currentPath)
        savedConfig = backupHelper.getConfig()
        Log.i(
            ONEDRIVE_BACKUP_LOG_TAG,
            "Saved OneDrive backup directory, pathIsRoot=${currentPath.isBlank()}, session=${activeSession.debugRef()}"
        )
        coroutineScope.launch {
            refreshBackups()
        }
        Toast.makeText(context, context.getString(R.string.onedrive_backup_directory_saved), Toast.LENGTH_SHORT).show()
    }

    suspend fun performRestore(backup: BackupFile, decryptPasswordValue: String?) {
        val downloadedFile = File(context.cacheDir, "restore_${backup.name}")
        val result = backupHelper.downloadBackup(backup, downloadedFile)
            .mapCatching {
                webDavHelper.restoreFromBackupFile(
                    backupFile = it,
                    decryptPassword = decryptPasswordValue,
                    overwrite = restoreMode == OneDriveRestoreMode.ReplaceLocal,
                    restoreMonicaConfig = true
                ).getOrThrow()
            }

        result.fold(
            onSuccess = { restoreResult ->
                val stats = BackupRestoreApplier.applyRestoreResult(
                    context = context,
                    restoreResult = restoreResult,
                    passwordRepository = passwordRepository,
                    secureItemRepository = secureItemRepository,
                    localOnlyDedup = when (restoreMode) {
                        OneDriveRestoreMode.MergeLocal -> !restoreGlobalDedup
                        OneDriveRestoreMode.ReplaceLocal -> true
                    },
                    logTag = "OneDriveBackup"
                )
                Toast.makeText(
                    context,
                    buildRestoreSummary(context, restoreResult, stats),
                    Toast.LENGTH_LONG
                ).show()
                refreshBackups()
            },
            onFailure = { error ->
                when (error) {
                    is WebDavHelper.PasswordRequiredException -> {
                        decryptPassword = decryptPasswordValue.orEmpty()
                        showDecryptPasswordDialogFor = backup
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.webdav_restore_failed, error.toOneDriveUserMessage(context.getString(R.string.import_data_unknown_error))),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
        downloadedFile.delete()
    }

    LaunchedEffect(Unit) {
        savedConfig = backupHelper.getConfig()
        backupPreferences = webDavHelper.getBackupPreferences().copy(includeWebDavConfig = false)
        val encryptionConfig = webDavHelper.getEncryptionConfig()
        encryptionEnabled = encryptionConfig.enabled
        encryptionPassword = encryptionConfig.password
        loadCounts()

        Log.d(
            ONEDRIVE_BACKUP_LOG_TAG,
            "Initializing OneDrive backup screen, savedConfig=${savedConfig.debugRef()}"
        )
        val configuredSessionResult = runCatching { backupHelper.getConfiguredSession() }
        val configuredSession = configuredSessionResult.getOrNull()
        val cachedSession = runCatching { authManager.getCachedSession() }.getOrNull()

        session = configuredSession ?: cachedSession
        if (configuredSessionResult.isFailure) {
            val error = configuredSessionResult.exceptionOrNull()
            Log.w(
                ONEDRIVE_BACKUP_LOG_TAG,
                "Configured OneDrive session refresh failed, " +
                    "temporaryAuth=${error?.isOneDriveAuthTemporarilyUnavailable() == true}, " +
                    "cachedSession=${cachedSession.debugRef()}",
                error
            )
            browserError = configuredSessionResult.exceptionOrNull()
                ?.toOneDriveUserMessage(context.getString(R.string.keepass_onedrive_load_files_failed))
            browserEntries = emptyList()
            backupList = emptyList()
            loadingEntries = false
            loadingBackups = false
            connectionState = OneDriveBackupConnectionState.Failed
            return@LaunchedEffect
        }

        Log.d(
            ONEDRIVE_BACKUP_LOG_TAG,
            "Initial OneDrive session resolved, configured=${configuredSession.debugRef()}, " +
                "cached=${cachedSession.debugRef()}, active=${session.debugRef()}, " +
                "configMatches=${currentSessionBackupConfig() != null}"
        )
        if (session != null) {
            connectionState = OneDriveBackupConnectionState.Connected
            val initialPath = currentSessionBackupConfig()?.folderPath.orEmpty()
            loadDirectory(initialPath)
        }
        if (session != null &&
            connectionState == OneDriveBackupConnectionState.Connected &&
            currentSessionBackupConfig() != null
        ) {
            refreshBackups()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onedrive_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedHeaderCard(
                title = stringResource(R.string.onedrive_backup_title),
                description = stringResource(R.string.onedrive_backup_description)
            )

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session?.displayName ?: stringResource(R.string.keepass_onedrive_not_connected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = session?.username?.ifBlank {
                                    stringResource(R.string.keepass_onedrive_sign_in_hint)
                                } ?: stringResource(R.string.keepass_onedrive_sign_in_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                Log.i(
                                    ONEDRIVE_BACKUP_LOG_TAG,
                                    "OneDrive sign-in/switch clicked, state=$connectionState, signingIn=$signingIn, " +
                                        "loadingEntries=$loadingEntries, loadingBackups=$loadingBackups, " +
                                        "session=${session.debugRef()}, savedConfig=${savedConfig.debugRef()}"
                                )
                                if (activity == null) {
                                    Log.w(ONEDRIVE_BACKUP_LOG_TAG, "OneDrive sign-in blocked: Activity missing")
                                    browserError = context.getString(R.string.keepass_onedrive_activity_missing)
                                    connectionState = OneDriveBackupConnectionState.Failed
                                    return@Button
                                }
                                connectionState = OneDriveBackupConnectionState.Connecting
                                browserError = null
                                signingIn = true
                                loadingEntries = false
                                loadingBackups = false
                                coroutineScope.launch {
                                    val signInResult = runCatching { authManager.signIn(activity) }
                                    signingIn = false
                                    signInResult
                                        .onSuccess { result ->
                                            session = result
                                            savedConfig = backupHelper.getConfig()
                                            connectionState = OneDriveBackupConnectionState.Connected
                                            backupList = emptyList()
                                            Log.i(
                                                ONEDRIVE_BACKUP_LOG_TAG,
                                                "OneDrive interactive sign-in succeeded, session=${result.debugRef()}, " +
                                                    "configMatches=${savedConfig?.accountId == result.accountId}"
                                            )
                                            val configuredPath = savedConfig
                                                ?.takeIf { it.accountId == result.accountId }
                                                ?.folderPath
                                                .orEmpty()
                                            loadDirectory(configuredPath)
                                        }
                                        .onFailure { error ->
                                            browserEntries = emptyList()
                                            backupList = emptyList()
                                            connectionState = OneDriveBackupConnectionState.Failed
                                            browserError = error.toOneDriveUserMessage(context.getString(R.string.keepass_onedrive_sign_in_failed))
                                            Log.w(
                                                ONEDRIVE_BACKUP_LOG_TAG,
                                                "OneDrive interactive sign-in failed, temporaryAuth=${error.isOneDriveAuthTemporarilyUnavailable()}",
                                                error
                                            )
                                        }
                                }
                            },
                            enabled = !signingIn
                        ) {
                            if (signingIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (session == null) stringResource(R.string.keepass_onedrive_sign_in_action)
                                else stringResource(R.string.keepass_onedrive_switch_account)
                            )
                        }
                    }

                    Surface(
                        color = when (connectionState) {
                            OneDriveBackupConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                            OneDriveBackupConnectionState.Failed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = when (connectionState) {
                                OneDriveBackupConnectionState.NotConnected -> stringResource(R.string.keepass_webdav_status_not_connected)
                                OneDriveBackupConnectionState.Connecting -> stringResource(R.string.keepass_webdav_status_connecting)
                                OneDriveBackupConnectionState.Connected -> stringResource(R.string.keepass_webdav_status_connected)
                                OneDriveBackupConnectionState.Failed -> stringResource(R.string.keepass_webdav_status_failed)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    browserError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (oneDriveReady) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onedrive_backup_browser_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.keepass_onedrive_current_path, currentPath.ifBlank { "/" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        loadDirectory(OneDriveKeePassFileSource.parentPathOf(currentPath))
                                    }
                                },
                                enabled = currentPath.isNotBlank() && !loadingEntries
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.go_back),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { showCreateFolderDialog = true },
                                enabled = !loadingEntries && !creatingFolder
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.keepass_webdav_create_folder_action),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { saveCurrentFolderAsBackupDirectory() },
                            enabled = !loadingEntries
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.onedrive_backup_use_current_folder))
                        }

                        if (loadingEntries) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (browserEntries.isEmpty()) {
                            Text(
                                text = stringResource(R.string.onedrive_backup_no_folders),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            browserEntries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch { loadDirectory(entry.path) }
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = stringResource(R.string.onedrive_backup_folder_item),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }

            savedConfig?.let { config ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.onedrive_backup_config_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = {
                                    backupHelper.clearConfig()
                                    savedConfig = null
                                    backupList = emptyList()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                            }
                        }
                        ConfigInfoRow(
                            label = stringResource(R.string.username),
                            value = config.displayName,
                            icon = Icons.Default.Person
                        )
                        ConfigInfoRow(
                            label = stringResource(R.string.onedrive_backup_directory_label),
                            value = config.folderPath.ifBlank { "/" },
                            icon = Icons.Default.Folder
                        )
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onedrive_backup_security_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.onedrive_backup_encryption_title))
                            Text(
                                text = stringResource(R.string.onedrive_backup_encryption_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = encryptionEnabled,
                            onCheckedChange = {
                                encryptionEnabled = it
                                webDavHelper.setEncryptionConfig(it, if (it) encryptionPassword else "")
                            }
                        )
                    }

                    if (encryptionEnabled) {
                        OutlinedTextField(
                            value = encryptionPassword,
                            onValueChange = {
                                encryptionPassword = it
                                webDavHelper.setEncryptionConfig(encryptionEnabled, it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.password)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = if (encryptionPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { encryptionPasswordVisible = !encryptionPasswordVisible }) {
                                    Text(if (encryptionPasswordVisible) stringResource(R.string.hide) else stringResource(R.string.show))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                        )
                    }
                }
            }

            SelectiveBackupCard(
                preferences = backupPreferences,
                onPreferencesChange = {
                    val normalized = it.copy(includeWebDavConfig = false)
                    backupPreferences = normalized
                    webDavHelper.saveBackupPreferences(normalized)
                },
                passwordCount = passwordCount,
                authenticatorCount = authenticatorCount,
                documentCount = documentCount,
                bankCardCount = bankCardCount,
                noteCount = noteCount,
                trashCount = trashCount,
                passkeyCount = passkeyCount,
                localKeePassCount = localKeePassCount,
                isWebDavConfigured = false
            )

            Button(
                onClick = {
                    if (currentSessionBackupConfig() == null) {
                        Log.w(
                            ONEDRIVE_BACKUP_LOG_TAG,
                            "Create backup blocked: no config for current session, " +
                                "state=$connectionState, session=${session.debugRef()}, savedConfig=${savedConfig.debugRef()}"
                        )
                        Toast.makeText(context, context.getString(R.string.onedrive_backup_directory_required), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!backupPreferences.hasAnyEnabled()) {
                        Toast.makeText(context, context.getString(R.string.backup_validation_error), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    creatingBackup = true
                    coroutineScope.launch {
                        try {
                            val allPasswords = passwordRepository.getAllPasswordEntries().first()
                            val securityManager = takagi.ru.monica.security.SecurityManager(context)
                            val decryptedPasswords = allPasswords.map { entry ->
                                runCatching { entry.copy(password = securityManager.decryptData(entry.password)) }.getOrElse { entry }
                            }
                            val allSecureItems = secureItemRepository.getAllItems().first()
                            val createResult = webDavHelper.createBackupZip(
                                passwords = decryptedPasswords,
                                secureItems = allSecureItems,
                                preferences = backupPreferences
                            )
                            createResult.fold(
                                onSuccess = { (file, report) ->
                                    backupHelper.uploadBackup(file, isPermanent = true).fold(
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                if (report.hasIssues()) report.getSummary() else context.getString(R.string.webdav_backup_success),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            refreshBackups()
                                        },
                                        onFailure = { error ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.webdav_backup_failed, error.toOneDriveUserMessage(context.getString(R.string.webdav_create_backup_failed))),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                    file.delete()
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_backup_failed, error.toOneDriveUserMessage(context.getString(R.string.webdav_create_backup_failed))),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        } finally {
                            creatingBackup = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = backupReady && !creatingBackup
            ) {
                if (creatingBackup) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (creatingBackup) stringResource(R.string.webdav_backup_in_progress) else stringResource(R.string.webdav_backup_now))
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_backup_list),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { coroutineScope.launch { refreshBackups() } },
                            enabled = backupReady && !loadingBackups
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }

                    if (loadingBackups) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (savedConfigForCurrentSession == null) {
                        Text(
                            text = stringResource(R.string.onedrive_backup_directory_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (backupList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.webdav_no_backups),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        backupList.forEachIndexed { index, backup ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            OneDriveBackupListItem(
                                backup = backup,
                                helper = backupHelper,
                                onRestore = { showRestoreDialogFor = backup },
                                onDelete = { showDeleteDialogFor = backup },
                                onStatusChanged = { coroutineScope.launch { refreshBackups() } }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.keepass_onedrive_create_folder_title)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val activeSession = session ?: return@TextButton
                        showCreateFolderDialog = false
                        creatingFolder = true
                        coroutineScope.launch {
                            runCatching {
                                backupHelper.createFolder(activeSession.accountId, currentPath, folderName)
                            }.onSuccess {
                                loadDirectory(currentPath)
                            }.onFailure { error ->
                                Toast.makeText(context, error.toOneDriveUserMessage(context.getString(R.string.webdav_operation_failed, "")), Toast.LENGTH_LONG).show()
                            }
                            creatingFolder = false
                        }
                    },
                    enabled = folderName.isNotBlank()
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showDeleteDialogFor?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text(stringResource(R.string.delete_backup)) },
            text = { Text(stringResource(R.string.delete_backup_confirm, backup.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialogFor = null
                        coroutineScope.launch {
                            backupHelper.deleteBackup(backup).fold(
                                onSuccess = {
                                    Toast.makeText(context, context.getString(R.string.backup_deleted), Toast.LENGTH_SHORT).show()
                                    refreshBackups()
                                },
                                onFailure = { error ->
                                    Toast.makeText(context, context.getString(R.string.delete_failed, error.toOneDriveUserMessage("")), Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showRestoreDialogFor?.let { backup ->
        AlertDialog(
            onDismissRequest = { showRestoreDialogFor = null },
            title = { Text(stringResource(R.string.webdav_restore_backup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(backup.name, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = restoreMode == OneDriveRestoreMode.MergeLocal,
                                onClick = { restoreMode = OneDriveRestoreMode.MergeLocal }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = restoreMode == OneDriveRestoreMode.MergeLocal, onClick = null)
                        Text(stringResource(R.string.webdav_restore_mode_merge_title))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = restoreMode == OneDriveRestoreMode.ReplaceLocal,
                                onClick = { restoreMode = OneDriveRestoreMode.ReplaceLocal }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = restoreMode == OneDriveRestoreMode.ReplaceLocal, onClick = null)
                        Text(stringResource(R.string.webdav_restore_mode_replace_title))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreGlobalDedup,
                            onCheckedChange = { restoreGlobalDedup = it },
                            enabled = restoreMode == OneDriveRestoreMode.MergeLocal
                        )
                        Text(stringResource(R.string.webdav_restore_mode_global_dedup))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialogFor = null
                        if (backup.isEncrypted()) {
                            decryptPassword = ""
                            showDecryptPasswordDialogFor = backup
                        } else {
                            coroutineScope.launch { performRestore(backup, null) }
                        }
                    }
                ) {
                    Text(stringResource(R.string.webdav_restore_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialogFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showDecryptPasswordDialogFor?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDecryptPasswordDialogFor = null },
            title = { Text(stringResource(R.string.webdav_enter_decrypt_password)) },
            text = {
                OutlinedTextField(
                    value = decryptPassword,
                    onValueChange = { decryptPassword = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDecryptPasswordDialogFor = null
                        coroutineScope.launch { performRestore(backup, decryptPassword) }
                    },
                    enabled = decryptPassword.isNotBlank()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecryptPasswordDialogFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ElevatedHeaderCard(title: String, description: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun OneDriveBackupListItem(
    backup: BackupFile,
    helper: OneDriveBackupHelper,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onStatusChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            val statusHints = buildList {
                if (backup.isPermanent) add(stringResource(R.string.webdav_tag_permanent))
                if (backup.isEncrypted()) add(stringResource(R.string.webdav_backup_encrypted))
            }
            Text(
                text = backup.name,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(dateFormat.format(backup.modified))
                    append(" • ")
                    append(helper.formatFileSize(backup.size))
                    if (statusHints.isNotEmpty()) {
                        append(" • ")
                        append(statusHints.joinToString(" · "))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.webdav_restore_action))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (backup.isPermanent) {
                                stringResource(R.string.webdav_unmark_permanent)
                            } else {
                                stringResource(R.string.webdav_mark_permanent)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (backup.isPermanent) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        coroutineScope.launch {
                            val result = if (backup.isPermanent) {
                                helper.unmarkPermanent(backup)
                            } else {
                                helper.markBackupAsPermanent(backup)
                            }
                            result.fold(
                                onSuccess = {
                                    onStatusChanged()
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_operation_failed, error.toOneDriveUserMessage("")),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun buildRestoreSummary(
    context: Context,
    restoreResult: RestoreResult,
    stats: takagi.ru.monica.utils.RestoreApplyStats
): String {
    val report = restoreResult.report
    if (report.hasIssues()) {
        return report.getSummary()
    }
    val summaryParts = mutableListOf<String>()
    summaryParts += context.getString(R.string.webdav_restore_summary_part_passwords, stats.passwordImported)
    summaryParts += context.getString(R.string.webdav_restore_summary_part_other_data, stats.secureItemImported)
    if (stats.passkeyImported > 0) {
        summaryParts += "通行密钥 ${stats.passkeyImported}"
    }
    return context.getString(
        R.string.webdav_restore_summary_success,
        summaryParts.joinToString(", ")
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun OneDriveAccountSession?.debugRef(): String {
    val accountId = this?.accountId?.takeIf { it.isNotBlank() } ?: return "none"
    return "account#${accountId.hashCode().toString(16)}"
}

private fun OneDriveBackupConfig?.debugRef(): String {
    val accountId = this?.accountId?.takeIf { it.isNotBlank() } ?: return "none"
    return "config#${accountId.hashCode().toString(16)}"
}
