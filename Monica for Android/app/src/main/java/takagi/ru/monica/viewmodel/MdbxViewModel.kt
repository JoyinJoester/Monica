package takagi.ru.monica.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import takagi.ru.monica.attachments.data.AttachmentDao
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.LocalMdbxDatabaseDao
import takagi.ru.monica.data.MdbxRemoteSource
import takagi.ru.monica.data.MdbxRemoteSourceDao
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.MdbxStorageLocation
import takagi.ru.monica.data.MdbxSyncStatus
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.PasskeyDao
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.SecureItemDao
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.MdbxConflictResolution
import takagi.ru.monica.repository.MdbxConflictSummary
import takagi.ru.monica.repository.MdbxCommitDiff
import takagi.ru.monica.repository.MdbxDeltaSummary
import takagi.ru.monica.repository.MdbxApplyResult
import takagi.ru.monica.repository.MdbxBenchmarkResult
import takagi.ru.monica.repository.MdbxSnapshotSummary
import takagi.ru.monica.repository.MdbxStoredVaultEntry
import takagi.ru.monica.repository.MdbxSyncBundle
import takagi.ru.monica.repository.MdbxVaultCredential
import takagi.ru.monica.repository.MdbxVaultCrypto
import takagi.ru.monica.repository.MdbxVaultDiagnostics
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.WebDavKeePassFileSource
import takagi.ru.monica.utils.WebDavMdbxFileSource
import java.io.File
import java.text.Normalizer
import java.util.Date
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MdbxViewModel(
    application: Application,
    private val databaseDao: LocalMdbxDatabaseDao,
    private val remoteSourceDao: MdbxRemoteSourceDao,
    private val passwordEntryDao: PasswordEntryDao,
    private val secureItemDao: SecureItemDao,
    private val passkeyDao: PasskeyDao,
    private val attachmentDao: AttachmentDao,
    private val securityManager: SecurityManager
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val vaultStore = MdbxVaultStore(
        context.applicationContext,
        databaseDao,
        securityManager,
        remoteSourceDao,
        passwordEntryDao,
        secureItemDao
    )

    private val _allDatabasesLoaded = MutableStateFlow(false)
    val allDatabasesLoaded: StateFlow<Boolean> = _allDatabasesLoaded.asStateFlow()

    val allDatabases: StateFlow<List<LocalMdbxDatabase>> = databaseDao.getAllDatabases()
        .onEach { _allDatabasesLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    private val _conflictCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val conflictCounts: StateFlow<Map<Long, Int>> = _conflictCounts.asStateFlow()

    private val _vaultDiagnostics = MutableStateFlow<Map<Long, MdbxVaultDiagnostics>>(emptyMap())
    val vaultDiagnostics: StateFlow<Map<Long, MdbxVaultDiagnostics>> =
        _vaultDiagnostics.asStateFlow()

    private val _conflictDialogState =
        MutableStateFlow<MdbxConflictDialogState>(MdbxConflictDialogState.Hidden)
    val conflictDialogState: StateFlow<MdbxConflictDialogState> =
        _conflictDialogState.asStateFlow()

    private val _deltaDialogState =
        MutableStateFlow<MdbxDeltaDialogState>(MdbxDeltaDialogState.Hidden)
    val deltaDialogState: StateFlow<MdbxDeltaDialogState> =
        _deltaDialogState.asStateFlow()

    private val _advancedDialogState =
        MutableStateFlow<MdbxAdvancedDialogState>(MdbxAdvancedDialogState.Hidden)
    val advancedDialogState: StateFlow<MdbxAdvancedDialogState> =
        _advancedDialogState.asStateFlow()

    // --- WebDAV connection ---

    suspend fun testWebDavConnection(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val source = WebDavMdbxFileSource(serverUrl, username, password)
        source.testConnection()
    }

    suspend fun listWebDavDirectory(
        serverUrl: String,
        username: String,
        password: String,
        path: String? = null
    ): Result<List<FileSourceEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val source = WebDavMdbxFileSource(serverUrl, username, password)
            source.listDirectory(path)
        }
    }

    suspend fun readSelectedKeyFile(uri: Uri): Result<MdbxKeyFileSelection> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Unable to read selected MDBX key file")
                MdbxKeyFileSelection(
                    uri = uri.toString(),
                    name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "mdbx.key",
                    fingerprint = MdbxVaultCrypto.fingerprint(bytes),
                    bytes = bytes
                )
            }
        }

    suspend fun writeGeneratedKeyFile(targetUri: Uri): Result<MdbxKeyFileSelection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = MdbxVaultCrypto.generateKeyFileBytes()
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
                    output.write(bytes)
                } ?: throw IllegalArgumentException("Unable to write MDBX key file")
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        targetUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                MdbxKeyFileSelection(
                    uri = targetUri.toString(),
                    name = queryDisplayName(targetUri) ?: "monica-mdbx.key",
                    fingerprint = MdbxVaultCrypto.fingerprint(bytes),
                    bytes = bytes
                )
            }
        }

    // --- Vault lifecycle ---

    fun createLocalVault(
        name: String,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        description: String?,
        customDirectoryUri: Uri? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Creating local MDBX vault...")

            try {
                withContext(Dispatchers.IO) {
                    val displayName = name.trim().ifBlank {
                        throw IllegalArgumentException("Vault name cannot be empty")
                    }
                    val credential = buildCredential(unlockMethod, masterPassword, keyFile)
                    val customDirVault = customDirectoryUri?.let { uri ->
                        createVaultFileInCustomDir(uri, displayName, tigaMode.name, credential)
                    }
                    val localVaultFile = customDirVault?.localCopy ?: run {
                        vaultStore.createInitializedVaultFile(
                            displayName = displayName,
                            tigaMode = tigaMode.name,
                            unlockMethod = unlockMethod,
                            credential = credential
                        )
                    }
                    val storageLocation = if (customDirVault != null) {
                        MdbxStorageLocation.EXTERNAL
                    } else {
                        MdbxStorageLocation.INTERNAL
                    }
                    val sourceType = if (customDirVault != null) {
                        MdbxSourceType.LOCAL_EXTERNAL
                    } else {
                        MdbxSourceType.LOCAL_INTERNAL
                    }
                    val filePath = customDirVault?.externalUri?.toString() ?: localVaultFile.absolutePath
                    val encryptedMasterPassword =
                        masterPassword.takeIf { credential.requiresPassword() }
                            ?.let { securityManager.encryptData(normalizeMdbxPassword(it)) }
                    databaseDao.insertDatabase(
                        LocalMdbxDatabase(
                            name = displayName,
                            filePath = filePath,
                            storageLocation = storageLocation.name,
                            sourceType = sourceType.name,
                            sourceId = null,
                            tigaMode = tigaMode.name,
                            encryptedPassword = encryptedMasterPassword,
                            unlockMethod = unlockMethod.storedValue,
                            kdfProfile = "pbkdf2-sha256",
                            keyFileName = keyFile?.name,
                            keyFileUri = keyFile?.uri,
                            keyFileFingerprint = keyFile?.fingerprint,
                            description = description,
                            lastSyncedAt = null,
                            workingCopyPath = localVaultFile.absolutePath,
                            cacheCopyPath = localVaultFile.absolutePath,
                            isOfflineAvailable = true,
                            lastSyncStatus = MdbxSyncStatus.LOCAL_ONLY.name
                        )
                    )
                }

                _operationState.value = OperationState.Success(
                    "Local MDBX vault \"$name\" created"
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to create local vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun importLocalVault(
        sourceUri: Uri,
        name: String?,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        description: String?
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Opening local MDBX vault...")

            try {
                withContext(Dispatchers.IO) {
                    val sourceName = queryDisplayName(sourceUri) ?: "imported-${UUID.randomUUID()}.mdbx"
                    val displayName = name?.trim()?.takeIf { it.isNotBlank() }
                        ?: sourceName.removeSuffix(".mdbx")
                    val fileName = if (sourceName.endsWith(".mdbx", ignoreCase = true)) {
                        sourceName
                    } else {
                        "$displayName.mdbx"
                    }

                    // Verify source file is readable
                    val sourceBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        input.readBytes()
                    } ?: throw IllegalArgumentException("Unable to read selected MDBX file")

                    // Take persistent URI permissions (read + write)
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            sourceUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }.onFailure { error ->
                        android.util.Log.w("MdbxViewModel", "Persistable permission not granted for uri=$sourceUri", error)
                    }

                    // Write working copy and verify
                    val vaultDir = File(context.filesDir, "mdbx")
                    check(vaultDir.mkdirs() || vaultDir.exists()) {
                        "Unable to create MDBX directory"
                    }
                    val workingCopy = File(vaultDir, "${UUID.randomUUID()}-$fileName")
                    workingCopy.writeBytes(sourceBytes)
                    if (workingCopy.length() != sourceBytes.size.toLong()) {
                        workingCopy.delete()
                        throw IllegalArgumentException(
                            "File copy verification failed: source=${sourceBytes.size} bytes, copy=${workingCopy.length()} bytes"
                        )
                    }

                    // Validate and detect actual Tiga mode from existing vault
                    try {
                        vaultStore.validateExistingVaultFile(workingCopy)
                    } catch (e: Exception) {
                        workingCopy.delete()
                        throw e
                    }
                    val detectedMode = vaultStore.readTigaModeFromVaultFile(workingCopy)
                    val detectedUnlockMethod = vaultStore.readUnlockMethodFromVaultFile(workingCopy)
                    val credential = buildCredential(detectedUnlockMethod, masterPassword, keyFile)
                    vaultStore.validateVaultCredentialFile(workingCopy, credential)

                    val encryptedMasterPassword =
                        masterPassword.takeIf { credential.requiresPassword() }
                            ?.let { securityManager.encryptData(normalizeMdbxPassword(it)) }
                    databaseDao.insertDatabase(
                        LocalMdbxDatabase(
                            name = displayName,
                            filePath = sourceUri.toString(),
                            storageLocation = MdbxStorageLocation.EXTERNAL.name,
                            sourceType = MdbxSourceType.LOCAL_EXTERNAL.name,
                            sourceId = null,
                            tigaMode = detectedMode.name,
                            encryptedPassword = encryptedMasterPassword,
                            unlockMethod = detectedUnlockMethod.storedValue,
                            kdfProfile = "pbkdf2-sha256",
                            keyFileName = keyFile?.name,
                            keyFileUri = keyFile?.uri,
                            keyFileFingerprint = keyFile?.fingerprint,
                            description = description,
                            lastSyncedAt = System.currentTimeMillis(),
                            workingCopyPath = workingCopy.absolutePath,
                            cacheCopyPath = workingCopy.absolutePath,
                            isOfflineAvailable = true,
                            lastSyncStatus = MdbxSyncStatus.IN_SYNC.name
                        )
                    ).also { databaseId ->
                        importEntriesFromVault(databaseId)
                    }
                }

                _operationState.value = OperationState.Success("Local MDBX vault opened")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to open local vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun createWebDavVault(
        name: String,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        serverUrl: String,
        username: String,
        webDavPassword: String,
        remoteDirectoryPath: String?,
        description: String?
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Creating MDBX vault on WebDAV...")

            try {
                withContext(Dispatchers.IO) {
                    val normalizedDir = WebDavKeePassFileSource.normalizeOptionalRemotePath(
                        remoteDirectoryPath
                    )
                    val fileSource = WebDavMdbxFileSource(serverUrl, username, webDavPassword)

                    fileSource.testConnection().getOrThrow()

                    val displayName = name.trim().ifBlank {
                        throw IllegalArgumentException("Vault name cannot be empty")
                    }
                    val credential = buildCredential(unlockMethod, masterPassword, keyFile)
                    val remoteFileName = if (displayName.endsWith(".mdbx", ignoreCase = true)) {
                        displayName
                    } else {
                        "$displayName.mdbx"
                    }

                    val localVaultFile = vaultStore.createInitializedVaultFile(
                        displayName = displayName,
                        tigaMode = tigaMode.name,
                        unlockMethod = unlockMethod,
                        credential = credential
                    )

                    fileSource.writeFile(
                        parentPath = normalizedDir.ifBlank { null },
                        name = remoteFileName,
                        bytes = localVaultFile.readBytes()
                    )

                    val remotePath = WebDavKeePassFileSource.buildChildPath(
                        normalizedDir, remoteFileName
                    )

                    // Encrypt credentials
                    val encryptedUsername = securityManager.encryptData(username)
                    val encryptedPassword = securityManager.encryptData(webDavPassword)

                    // Create remote source record
                    val sourceId = remoteSourceDao.insertSource(
                        MdbxRemoteSource(
                            displayName = displayName,
                            remotePath = remotePath,
                            remoteParentPath = normalizedDir.ifBlank { null },
                            baseUrl = serverUrl.trim().trimEnd('/'),
                            usernameEncrypted = encryptedUsername,
                            passwordEncrypted = encryptedPassword
                        )
                    )

                    // Encrypt master password
                    val encryptedMasterPassword =
                        masterPassword.takeIf { credential.requiresPassword() }
                            ?.let { securityManager.encryptData(normalizeMdbxPassword(it)) }

                    // Create database record
                    databaseDao.insertDatabase(
                        LocalMdbxDatabase(
                            name = displayName,
                            filePath = remotePath,
                            storageLocation = MdbxStorageLocation.REMOTE_WEBDAV.name,
                            sourceType = MdbxSourceType.REMOTE_WEBDAV.name,
                            sourceId = sourceId,
                            tigaMode = tigaMode.name,
                            encryptedPassword = encryptedMasterPassword,
                            unlockMethod = unlockMethod.storedValue,
                            kdfProfile = "pbkdf2-sha256",
                            keyFileName = keyFile?.name,
                            keyFileUri = keyFile?.uri,
                            keyFileFingerprint = keyFile?.fingerprint,
                            description = description,
                            lastSyncedAt = System.currentTimeMillis(),
                            workingCopyPath = localVaultFile.absolutePath,
                            cacheCopyPath = localVaultFile.absolutePath,
                            isOfflineAvailable = true,
                            lastSyncStatus = MdbxSyncStatus.IN_SYNC.name
                        )
                    ).also { databaseId ->
                        importEntriesFromVault(databaseId)
                    }
                }

                _operationState.value = OperationState.Success(
                    "MDBX vault \"$name\" created on WebDAV"
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to create vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun connectToExistingWebDavVault(
        name: String,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        serverUrl: String,
        username: String,
        webDavPassword: String,
        remoteFilePath: String,
        description: String?
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Connecting to remote MDBX vault...")

            try {
                withContext(Dispatchers.IO) {
                    val displayName = name.trim().ifBlank {
                        throw IllegalArgumentException("Vault name cannot be empty")
                    }
                    val fileSource = WebDavMdbxFileSource(serverUrl, username, webDavPassword)
                    fileSource.testConnection().getOrThrow()

                    val remoteBytes = fileSource.readFile(remoteFilePath)

                    val vaultDir = File(context.filesDir, "mdbx")
                    check(vaultDir.mkdirs() || vaultDir.exists()) {
                        "Unable to create MDBX directory"
                    }
                    val localFile = File(vaultDir, "remote_${UUID.randomUUID()}.mdbx")
                    localFile.writeBytes(remoteBytes)

                    vaultStore.validateExistingVaultFile(localFile)
                    val detectedMode = vaultStore.readTigaModeFromVaultFile(localFile)
                    val detectedUnlockMethod = vaultStore.readUnlockMethodFromVaultFile(localFile)
                    val credential = buildCredential(detectedUnlockMethod, masterPassword, keyFile)
                    vaultStore.validateVaultCredentialFile(localFile, credential)

                    val remoteParentPath = WebDavKeePassFileSource.parentPathOf(remoteFilePath)

                    val encryptedUsername = securityManager.encryptData(username)
                    val encryptedPassword = securityManager.encryptData(webDavPassword)

                    val sourceId = remoteSourceDao.insertSource(
                        MdbxRemoteSource(
                            displayName = displayName,
                            remotePath = remoteFilePath,
                            remoteParentPath = remoteParentPath,
                            baseUrl = serverUrl.trim().trimEnd('/'),
                            usernameEncrypted = encryptedUsername,
                            passwordEncrypted = encryptedPassword
                        )
                    )

                    val encryptedMasterPassword =
                        masterPassword.takeIf { credential.requiresPassword() }
                            ?.let { securityManager.encryptData(normalizeMdbxPassword(it)) }
                    databaseDao.insertDatabase(
                        LocalMdbxDatabase(
                            name = displayName,
                            filePath = remoteFilePath,
                            storageLocation = MdbxStorageLocation.REMOTE_WEBDAV.name,
                            sourceType = MdbxSourceType.REMOTE_WEBDAV.name,
                            sourceId = sourceId,
                            tigaMode = detectedMode.name,
                            encryptedPassword = encryptedMasterPassword,
                            unlockMethod = detectedUnlockMethod.storedValue,
                            kdfProfile = "pbkdf2-sha256",
                            keyFileName = keyFile?.name,
                            keyFileUri = keyFile?.uri,
                            keyFileFingerprint = keyFile?.fingerprint,
                            description = description,
                            lastSyncedAt = System.currentTimeMillis(),
                            workingCopyPath = localFile.absolutePath,
                            cacheCopyPath = localFile.absolutePath,
                            isOfflineAvailable = true,
                            lastSyncStatus = MdbxSyncStatus.IN_SYNC.name
                        )
                    ).also { databaseId ->
                        importEntriesFromVault(databaseId)
                    }
                }

                _operationState.value = OperationState.Success(
                    "Connected to remote MDBX vault \"$name\""
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to connect to remote vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    /**
     * Push the working copy of an EXTERNAL vault back to its source URI,
     * so changes are visible in the user's synced folder.
     */
    fun syncExternalVault(databaseId: Long) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Syncing vault to external location...")
            try {
                withContext(Dispatchers.IO) {
                    refreshVaultFromSource(databaseId)
                    refreshSingleVaultState(databaseId)
                }
                _operationState.value = OperationState.Success("Vault synced to external location")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to sync vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun syncVault(databaseId: Long) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Refreshing MDBX vault...")
            try {
                withContext(Dispatchers.IO) {
                    refreshVaultFromSource(databaseId)
                    refreshSingleVaultState(databaseId)
                }
                _operationState.value = OperationState.Success("Vault refreshed")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to refresh vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun flushPendingVaultUploads() {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Uploading pending MDBX vault changes...")
            try {
                val flushedIds = withContext(Dispatchers.IO) {
                    databaseDao.getAllDatabasesSnapshot()
                        .filter { it.lastSyncStatus == MdbxSyncStatus.PENDING_UPLOAD.name }
                        .map { database ->
                            vaultStore.flushPendingWorkingCopy(database.id)
                            database.id
                        }
                }
                flushedIds.forEach { databaseId ->
                    refreshSingleVaultState(databaseId)
                }
                _operationState.value = OperationState.Success(
                    "Uploaded ${flushedIds.size} pending MDBX vault(s)"
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to upload pending MDBX vaults: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun showAdvancedTools(database: LocalMdbxDatabase) {
        viewModelScope.launch {
            val cachedDiagnostics = _vaultDiagnostics.value[database.id]
            _advancedDialogState.value = MdbxAdvancedDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                diagnostics = cachedDiagnostics,
                isLoading = cachedDiagnostics == null
            )
            val refreshedDiagnostic = withContext(Dispatchers.IO) {
                vaultStore.getVaultDiagnostics(database.id)
            }
            _vaultDiagnostics.value = _vaultDiagnostics.value + (database.id to refreshedDiagnostic)
            _conflictCounts.value =
                _conflictCounts.value + (database.id to refreshedDiagnostic.unresolvedConflictCount)
            val current = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
            if (current?.databaseId == database.id) {
                _advancedDialogState.value = current.copy(
                    diagnostics = refreshedDiagnostic,
                    isLoading = false
                )
            }
        }
    }

    fun exportSyncBundle(databaseId: Long, baseCommitId: String? = null) {
        viewModelScope.launch {
            val current = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
            _advancedDialogState.value = current?.copy(isLoading = true, message = null)
                ?: MdbxAdvancedDialogState.Hidden
            try {
                val bundle = withContext(Dispatchers.IO) {
                    vaultStore.exportSyncBundle(databaseId, baseCommitId)
                }
                val exportJson = syncBundleToExportJson(bundle)
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(
                        exportedBundleJson = exportJson,
                        lastExportedBundle = bundle,
                        isLoading = false,
                        message = "Exported ${bundle.commitCount} MDBX commit(s)"
                    )
                }
                _operationState.value = OperationState.Success(
                    "Exported ${bundle.commitCount} MDBX commit(s)"
                )
            } catch (e: Exception) {
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(isLoading = false)
                }
                _operationState.value = OperationState.Error(
                    "Failed to export MDBX sync bundle: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun importSyncBundleFromJson(databaseId: Long, bundleJson: String) {
        viewModelScope.launch {
            val current = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
            _advancedDialogState.value = current?.copy(isLoading = true, message = null)
                ?: MdbxAdvancedDialogState.Hidden
            try {
                val result = withContext(Dispatchers.IO) {
                    val bundle = parseSyncBundleExportJson(bundleJson)
                    val applyResult = vaultStore.importSyncBundle(databaseId, bundle)
                    importEntriesFromVault(databaseId)
                    applyResult
                }
                val refreshedDiagnostic = withContext(Dispatchers.IO) {
                    vaultStore.getVaultDiagnostics(databaseId)
                }
                _vaultDiagnostics.value =
                    _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
                _conflictCounts.value =
                    _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(
                        diagnostics = refreshedDiagnostic,
                        lastImportResult = result,
                        isLoading = false,
                        message = "Imported ${result.appliedObjectCount} object(s), ${result.conflictCount} conflict(s)"
                    )
                }
                _operationState.value = OperationState.Success(
                    "Imported MDBX bundle: ${result.appliedObjectCount} applied, ${result.conflictCount} conflict(s)"
                )
            } catch (e: Exception) {
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(isLoading = false)
                }
                _operationState.value = OperationState.Error(
                    "Failed to import MDBX sync bundle: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun flushPendingVaultUpload(databaseId: Long) {
        viewModelScope.launch {
            val current = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
            _advancedDialogState.value = current?.copy(isLoading = true, message = null)
                ?: MdbxAdvancedDialogState.Hidden
            _operationState.value = OperationState.Loading("Uploading pending MDBX vault changes...")
            try {
                withContext(Dispatchers.IO) {
                    vaultStore.flushPendingWorkingCopy(databaseId)
                }
                val refreshedDiagnostic = withContext(Dispatchers.IO) {
                    vaultStore.getVaultDiagnostics(databaseId)
                }
                _vaultDiagnostics.value =
                    _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
                _conflictCounts.value =
                    _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(
                        diagnostics = refreshedDiagnostic,
                        isLoading = false,
                        message = "Pending MDBX upload flushed"
                    )
                }
                _operationState.value = OperationState.Success("Pending MDBX upload flushed")
            } catch (e: Exception) {
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(isLoading = false)
                }
                _operationState.value = OperationState.Error(
                    "Failed to upload pending MDBX vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun runBenchmark(databaseId: Long, operationCount: Int = 10) {
        viewModelScope.launch {
            val current = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
            _advancedDialogState.value = current?.copy(isLoading = true, message = null)
                ?: MdbxAdvancedDialogState.Hidden
            try {
                val result = withContext(Dispatchers.IO) {
                    vaultStore.runBenchmark(
                        databaseId = databaseId,
                        operationCount = operationCount.coerceIn(1, 500)
                    )
                }
                val refreshedDiagnostic = withContext(Dispatchers.IO) {
                    vaultStore.getVaultDiagnostics(databaseId)
                }
                _vaultDiagnostics.value =
                    _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
                _conflictCounts.value =
                    _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(
                        diagnostics = refreshedDiagnostic,
                        lastBenchmarkResult = result,
                        isLoading = false,
                        message = "Benchmark: ${result.operationCount} commit(s) in ${result.elapsedMs} ms"
                    )
                }
                _operationState.value = OperationState.Success(
                    "MDBX benchmark finished in ${result.elapsedMs} ms"
                )
            } catch (e: Exception) {
                val latest = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (latest?.databaseId == databaseId) {
                    _advancedDialogState.value = latest.copy(isLoading = false)
                }
                _operationState.value = OperationState.Error(
                    "Failed to run MDBX benchmark: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    private suspend fun refreshSingleVaultState(databaseId: Long) {
        val diagnostic = vaultStore.getVaultDiagnostics(databaseId)
        _vaultDiagnostics.value = _vaultDiagnostics.value + (databaseId to diagnostic)
        _conflictCounts.value =
            _conflictCounts.value + (databaseId to diagnostic.unresolvedConflictCount)
    }

    private fun normalizeMdbxPassword(password: String): String =
        Normalizer.normalize(password, Normalizer.Form.NFC)

    fun deleteVault(databaseId: Long) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Deleting vault...")
            try {
                withContext(Dispatchers.IO) {
                    val database = databaseDao.getDatabaseById(databaseId)
                        ?: throw IllegalStateException("Vault not found")
                    val sourceId = database.sourceId
                    clearImportedEntries(databaseId)
                    databaseDao.deleteDatabaseById(databaseId)
                    if (sourceId != null) {
                        remoteSourceDao.deleteSourceById(sourceId)
                    }
                }
                _conflictCounts.value = _conflictCounts.value - databaseId
                _vaultDiagnostics.value = _vaultDiagnostics.value - databaseId
                if ((_conflictDialogState.value as? MdbxConflictDialogState.Visible)
                        ?.databaseId == databaseId
                ) {
                    _conflictDialogState.value = MdbxConflictDialogState.Hidden
                }
                if ((_advancedDialogState.value as? MdbxAdvancedDialogState.Visible)
                        ?.databaseId == databaseId
                ) {
                    _advancedDialogState.value = MdbxAdvancedDialogState.Hidden
                }
                _operationState.value = OperationState.Success("Vault deleted")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to delete vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun pruneMissingLocalVaults() {
        viewModelScope.launch {
            val removedIds = withContext(Dispatchers.IO) {
                databaseDao.getAllDatabasesSnapshot()
                    .filter { database ->
                        database.sourceTypeEnum != MdbxSourceType.REMOTE_WEBDAV &&
                            !database.hasAccessibleLocalSource()
                    }
                    .map { database ->
                        clearImportedEntries(database.id)
                        databaseDao.deleteDatabaseById(database.id)
                        database.id
                    }
            }
            if (removedIds.isNotEmpty()) {
                val removedSet = removedIds.toSet()
                _conflictCounts.value = _conflictCounts.value - removedSet
                _vaultDiagnostics.value = _vaultDiagnostics.value - removedSet
                val visibleConflict = _conflictDialogState.value as? MdbxConflictDialogState.Visible
                if (visibleConflict?.databaseId in removedSet) {
                    _conflictDialogState.value = MdbxConflictDialogState.Hidden
                }
                val visibleDelta = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                if (visibleDelta?.databaseId in removedSet) {
                    _deltaDialogState.value = MdbxDeltaDialogState.Hidden
                }
                val visibleAdvanced = _advancedDialogState.value as? MdbxAdvancedDialogState.Visible
                if (visibleAdvanced?.databaseId in removedSet) {
                    _advancedDialogState.value = MdbxAdvancedDialogState.Hidden
                }
            }
        }
    }

    fun refreshConflictCounts(databases: List<LocalMdbxDatabase>) {
        refreshVaultDiagnostics(databases)
    }

    fun refreshVaultDiagnostics(databases: List<LocalMdbxDatabase>) {
        viewModelScope.launch {
            val diagnostics = withContext(Dispatchers.IO) {
                databases.associate { database ->
                    database.id to vaultStore.getVaultDiagnostics(database.id)
                }
            }
            _vaultDiagnostics.value = diagnostics
            _conflictCounts.value = diagnostics.mapValues { (_, diagnostic) ->
                diagnostic.unresolvedConflictCount
            }
        }
    }

    fun showConflicts(database: LocalMdbxDatabase) {
        viewModelScope.launch {
            _conflictDialogState.value = MdbxConflictDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                isLoading = true
            )
            val conflicts = withContext(Dispatchers.IO) {
                vaultStore.listConflicts(database.id)
            }
            _conflictDialogState.value = MdbxConflictDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                conflicts = conflicts,
                isLoading = false
            )
        }
    }

    fun showDeltaHistory(database: LocalMdbxDatabase) {
        viewModelScope.launch {
            _deltaDialogState.value = MdbxDeltaDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                isLoading = true
            )
            val deltas = withContext(Dispatchers.IO) {
                vaultStore.listDeltaHistory(database.id)
            }
            val snapshots = withContext(Dispatchers.IO) {
                vaultStore.listSnapshots(database.id)
            }
            _deltaDialogState.value = MdbxDeltaDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                deltas = deltas,
                snapshots = snapshots,
                isLoading = false
            )
        }
    }

    fun showCommitDiff(databaseId: Long, commitId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                ?: return@launch
            _deltaDialogState.value = current.copy(
                selectedDiffCommitId = commitId,
                diffItems = emptyList(),
                isDiffLoading = true
            )
            val diffItems = withContext(Dispatchers.IO) {
                vaultStore.listCommitDiff(databaseId, commitId)
            }
            val latest = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                ?: return@launch
            _deltaDialogState.value = latest.copy(
                selectedDiffCommitId = commitId,
                diffItems = diffItems,
                isDiffLoading = false
            )
        }
    }

    fun revertCommit(databaseId: Long, commitId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                val revertedCount = withContext(Dispatchers.IO) {
                    val count = vaultStore.revertCommit(databaseId, commitId)
                    importEntriesFromVault(databaseId)
                    count
                }
                val refreshedDeltas = withContext(Dispatchers.IO) {
                    vaultStore.listDeltaHistory(databaseId)
                }
                val refreshedSnapshots = withContext(Dispatchers.IO) {
                    vaultStore.listSnapshots(databaseId)
                }
                val refreshedDiagnostic = withContext(Dispatchers.IO) {
                    vaultStore.getVaultDiagnostics(databaseId)
                }
                _vaultDiagnostics.value =
                    _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
                _conflictCounts.value =
                    _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
                _deltaDialogState.value = current?.copy(
                    deltas = refreshedDeltas,
                    snapshots = refreshedSnapshots,
                    selectedDiffCommitId = null,
                    diffItems = emptyList(),
                    isLoading = false,
                    isDiffLoading = false
                ) ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Success(
                    "Reverted $revertedCount MDBX object(s)"
                )
            } catch (e: Exception) {
                _deltaDialogState.value = current?.copy(isLoading = false, isDiffLoading = false)
                    ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to revert MDBX commit: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun createSnapshot(databaseId: Long, name: String, fullSnapshot: Boolean) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isSnapshotLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    vaultStore.createSnapshot(
                        databaseId = databaseId,
                        name = name,
                        fullSnapshot = fullSnapshot,
                        autoPrune = false
                    )
                }
                refreshDeltaDialogAfterSnapshotMutation(databaseId, current)
                _operationState.value = OperationState.Success(
                    "Created MDBX snapshot ${snapshot.name}"
                )
            } catch (e: Exception) {
                _deltaDialogState.value = current?.copy(isSnapshotLoading = false)
                    ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to create MDBX snapshot: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun deleteSnapshot(databaseId: Long, snapshotId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isSnapshotLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                withContext(Dispatchers.IO) {
                    vaultStore.deleteSnapshot(databaseId, snapshotId)
                }
                refreshDeltaDialogAfterSnapshotMutation(databaseId, current)
                _operationState.value = OperationState.Success("Deleted MDBX snapshot")
            } catch (e: Exception) {
                _deltaDialogState.value = current?.copy(isSnapshotLoading = false)
                    ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to delete MDBX snapshot: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun revertToSnapshot(databaseId: Long, snapshotId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isSnapshotLoading = true, isLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                val restoredCount = withContext(Dispatchers.IO) {
                    val count = vaultStore.revertToSnapshot(databaseId, snapshotId)
                    importEntriesFromVault(databaseId)
                    count
                }
                refreshDeltaDialogAfterSnapshotMutation(databaseId, current)
                _operationState.value = OperationState.Success(
                    "Restored $restoredCount MDBX object(s) from snapshot"
                )
            } catch (e: Exception) {
                _deltaDialogState.value = current?.copy(
                    isSnapshotLoading = false,
                    isLoading = false
                ) ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to restore MDBX snapshot: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun pruneAutomaticSnapshots(databaseId: Long) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isSnapshotLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    vaultStore.pruneAutomaticSnapshots(databaseId)
                }
                refreshDeltaDialogAfterSnapshotMutation(databaseId, current)
                _operationState.value = OperationState.Success(
                    "Pruned $deletedCount automatic MDBX snapshot(s)"
                )
            } catch (e: Exception) {
                _deltaDialogState.value = current?.copy(isSnapshotLoading = false)
                    ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to prune MDBX snapshots: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    private suspend fun refreshDeltaDialogAfterSnapshotMutation(
        databaseId: Long,
        previousState: MdbxDeltaDialogState.Visible?
    ) {
        val refreshedDeltas = withContext(Dispatchers.IO) {
            vaultStore.listDeltaHistory(databaseId)
        }
        val refreshedSnapshots = withContext(Dispatchers.IO) {
            vaultStore.listSnapshots(databaseId)
        }
        val refreshedDiagnostic = withContext(Dispatchers.IO) {
            vaultStore.getVaultDiagnostics(databaseId)
        }
        _vaultDiagnostics.value =
            _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
        _conflictCounts.value =
            _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
        _deltaDialogState.value = previousState?.copy(
            deltas = refreshedDeltas,
            snapshots = refreshedSnapshots,
            selectedDiffCommitId = null,
            diffItems = emptyList(),
            isLoading = false,
            isDiffLoading = false,
            isSnapshotLoading = false
        ) ?: MdbxDeltaDialogState.Hidden
    }

    fun resolveConflict(
        databaseId: Long,
        conflictId: String,
        resolution: MdbxConflictResolution
    ) {
        viewModelScope.launch {
            val current = _conflictDialogState.value as? MdbxConflictDialogState.Visible
            _conflictDialogState.value = current?.copy(isLoading = true)
                ?: MdbxConflictDialogState.Hidden
            try {
                withContext(Dispatchers.IO) {
                    vaultStore.resolveConflict(databaseId, conflictId, resolution)
                    importEntriesFromVault(databaseId)
                }
                val refreshedConflicts = withContext(Dispatchers.IO) {
                    vaultStore.listConflicts(databaseId)
                }
                val refreshedDiagnostic = withContext(Dispatchers.IO) {
                    vaultStore.getVaultDiagnostics(databaseId)
                }
                _vaultDiagnostics.value =
                    _vaultDiagnostics.value + (databaseId to refreshedDiagnostic)
                _conflictCounts.value =
                    _conflictCounts.value + (databaseId to refreshedDiagnostic.unresolvedConflictCount)
                _conflictDialogState.value = current?.copy(
                    conflicts = refreshedConflicts,
                    isLoading = false
                ) ?: MdbxConflictDialogState.Hidden
            } catch (e: Exception) {
                _conflictDialogState.value = current?.copy(isLoading = false)
                    ?: MdbxConflictDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to resolve conflict: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun dismissConflictDialog() {
        _conflictDialogState.value = MdbxConflictDialogState.Hidden
    }

    fun dismissDeltaDialog() {
        _deltaDialogState.value = MdbxDeltaDialogState.Hidden
    }

    fun dismissAdvancedTools() {
        _advancedDialogState.value = MdbxAdvancedDialogState.Hidden
    }

    fun setAsDefault(databaseId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                databaseDao.clearDefaultDatabase()
                databaseDao.setDefaultDatabase(databaseId)
            }
        }
    }

    fun clearOperationState() {
        _operationState.value = OperationState.Idle
    }

    private fun syncBundleToExportJson(bundle: MdbxSyncBundle): String =
        JSONObject()
            .put("format", "monica-mdbx-sync-bundle-export-v1")
            .put("bundle_id", bundle.bundleId)
            .put("base_commit_id", bundle.baseCommitId)
            .put("head_commit_id", bundle.headCommitId)
            .put("commit_count", bundle.commitCount)
            .put("payload_json", bundle.payloadJson)
            .put("payload_hash", bundle.payloadHash)
            .put("created_at", bundle.createdAt)
            .toString(2)

    private fun parseSyncBundleExportJson(rawJson: String): MdbxSyncBundle {
        val json = JSONObject(rawJson.trim())
        val format = json.optString("format")
        require(format == "monica-mdbx-sync-bundle-export-v1") {
            "Unsupported MDBX sync bundle export format"
        }
        val payloadJson = json.getString("payload_json")
        return MdbxSyncBundle(
            bundleId = json.getString("bundle_id"),
            baseCommitId = json.optString("base_commit_id").takeIf { it.isNotBlank() },
            headCommitId = json.getString("head_commit_id"),
            commitCount = json.optInt("commit_count"),
            payloadJson = payloadJson,
            payloadHash = json.getString("payload_hash"),
            createdAt = json.getString("created_at")
        )
    }

    private data class CustomDirectoryVault(
        val localCopy: File,
        val externalUri: Uri
    )

    private suspend fun createVaultFileInCustomDir(
        treeUri: Uri,
        displayName: String,
        tigaMode: String,
        credential: MdbxVaultCredential
    ): CustomDirectoryVault {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Cannot access selected directory")

        val fileName = if (displayName.endsWith(".mdbx", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.mdbx"
        }

        // Create the vault file locally first
        val localVaultFile = vaultStore.createInitializedVaultFile(
            displayName = displayName,
            tigaMode = tigaMode,
            unlockMethod = credential.unlockMethod,
            credential = credential
        )

        // Copy to user-selected directory via SAF
        val createdFile = documentFile.createFile("application/octet-stream", fileName)
            ?: throw IllegalArgumentException("Failed to create file in selected directory")
        context.contentResolver.openOutputStream(createdFile.uri)?.use { output ->
            localVaultFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot write to selected directory")

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        return CustomDirectoryVault(
            localCopy = localVaultFile,
            externalUri = createdFile.uri
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    private fun buildCredential(
        unlockMethod: MdbxUnlockMethod,
        masterPassword: String,
        keyFile: MdbxKeyFileSelection?
    ): MdbxVaultCredential =
        MdbxVaultCredential(
            unlockMethod = unlockMethod,
            password = masterPassword.takeIf {
                unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD ||
                    unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE
            }?.let(::normalizeMdbxPassword),
            keyFileBytes = keyFile?.bytes.takeIf {
                unlockMethod == MdbxUnlockMethod.KEY_FILE ||
                    unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE
            },
            keyFileName = keyFile?.name,
            keyFileFingerprint = keyFile?.fingerprint
        )

    private suspend fun importEntriesFromVault(databaseId: Long) {
        clearImportedEntries(databaseId)
        val entries = vaultStore.readStoredEntries(databaseId)
        val payloadByEntryId = mutableMapOf<String, JSONObject>()
        val importedPasswordIds = mutableMapOf<String, Long>()
        val importedSecureItemIds = mutableMapOf<String, Long>()

        entries.filterNot { it.deleted }.forEach { stored ->
            val payload = runCatching { JSONObject(stored.payloadJson) }.getOrNull()
                ?: return@forEach
            payloadByEntryId[stored.entryId] = payload
            if (stored.entryType == "login") {
                val passwordId = importPasswordEntry(databaseId, stored, payload)
                importedPasswordIds[stored.entryId] = passwordId
            }
        }

        entries.filterNot { it.deleted }.forEach { stored ->
            val payload = payloadByEntryId[stored.entryId] ?: return@forEach
            when (stored.entryType) {
                "note", "totp", "card", "document-ref" -> {
                    importSecureItem(databaseId, stored, payload, importedPasswordIds)
                        ?.let { secureItemId -> importedSecureItemIds[stored.entryId] = secureItemId }
                }
                "passkey" -> importPasskey(databaseId, stored, payload)
            }
        }
        restoreImportedBindings(payloadByEntryId, importedPasswordIds, importedSecureItemIds)
        importAttachmentsFromVault(databaseId, importedPasswordIds)
    }

    private suspend fun clearImportedEntries(databaseId: Long) {
        passwordEntryDao.deleteAllByMdbxDatabaseId(databaseId)
        secureItemDao.deleteAllByMdbxDatabaseId(databaseId)
        passkeyDao.deleteAllByMdbxDatabaseId(databaseId)
    }

    private fun LocalMdbxDatabase.hasAccessibleLocalSource(): Boolean {
        return when (sourceTypeEnum) {
            MdbxSourceType.LOCAL_INTERNAL -> {
                val activePath = workingCopyPath?.takeIf { it.isNotBlank() } ?: filePath
                File(activePath).isFile && File(activePath).canRead()
            }
            MdbxSourceType.LOCAL_EXTERNAL -> {
                runCatching {
                    val uri = Uri.parse(filePath)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.read(ByteArray(1))
                    } != null
                }.getOrDefault(false)
            }
            MdbxSourceType.REMOTE_WEBDAV -> true
        }
    }

    private suspend fun importPasswordEntry(
        databaseId: Long,
        stored: MdbxStoredVaultEntry,
        payload: JSONObject
    ): Long {
        val plainPassword = payload.optString("password_plain")
            .takeIf { it.isNotEmpty() }
            ?: payload.optString("password").takeIf { it.isNotEmpty() }?.let { value ->
                runCatching { securityManager.decryptData(value) }.getOrDefault(value)
            }
            ?: ""
        return passwordEntryDao.insertPasswordEntry(
            PasswordEntry(
                id = 0L,
                title = stored.title,
                website = payload.optString("website"),
                username = payload.optString("username"),
                password = securityManager.encryptData(plainPassword),
                notes = payload.optString("notes"),
                categoryId = null,
                mdbxDatabaseId = databaseId,
                replicaGroupId = stored.entryId,
                authenticatorKey = payload.optString("authenticator_key"),
                passkeyBindings = payload.optString("passkey_bindings"),
                loginType = payload.optString("login_type", "PASSWORD"),
                createdAt = Date(),
                updatedAt = Date()
            )
        )
    }

    private suspend fun importSecureItem(
        databaseId: Long,
        stored: MdbxStoredVaultEntry,
        payload: JSONObject,
        importedPasswordIds: Map<String, Long>
    ): Long? {
        val itemType = when (stored.entryType) {
            "note" -> ItemType.NOTE
            "totp" -> ItemType.TOTP
            "card" -> ItemType.BANK_CARD
            "document-ref" -> ItemType.DOCUMENT
            else -> return null
        }
        val itemData = if (itemType == ItemType.TOTP) {
            remapImportedTotpBinding(payload.optString("item_data"), payload, importedPasswordIds)
        } else {
            payload.optString("item_data")
        }
        return secureItemDao.insertItem(
            SecureItem(
                id = 0L,
                itemType = itemType,
                title = stored.title,
                notes = payload.optString("notes"),
                itemData = itemData,
                imagePaths = payload.optString("image_paths"),
                categoryId = null,
                mdbxDatabaseId = databaseId,
                replicaGroupId = stored.entryId,
                syncStatus = "NONE"
            )
        )
    }

    private fun remapImportedTotpBinding(
        itemData: String,
        payload: JSONObject,
        importedPasswordIds: Map<String, Long>
    ): String {
        val boundPasswordEntryId = payload.optString("bound_password_entry_id")
            .takeIf { it.isNotBlank() }
            ?: return itemData
        val localPasswordId = importedPasswordIds[boundPasswordEntryId] ?: return itemData
        return runCatching {
            Json.encodeToString(Json.decodeFromString<TotpData>(itemData).copy(boundPasswordId = localPasswordId))
        }.getOrDefault(itemData)
    }

    private suspend fun restoreImportedBindings(
        payloadByEntryId: Map<String, JSONObject>,
        importedPasswordIds: Map<String, Long>,
        importedSecureItemIds: Map<String, Long>
    ) {
        importedPasswordIds.forEach { (entryId, localPasswordId) ->
            val payload = payloadByEntryId[entryId] ?: return@forEach
            val boundNoteEntryId = payload.optString("bound_note_entry_id")
                .takeIf { it.isNotBlank() }
                ?: return@forEach
            val localNoteId = importedSecureItemIds[boundNoteEntryId] ?: return@forEach
            val password = passwordEntryDao.getPasswordEntryById(localPasswordId) ?: return@forEach
            passwordEntryDao.updatePasswordEntry(password.copy(boundNoteId = localNoteId))
        }
    }

    private suspend fun importPasskey(
        databaseId: Long,
        stored: MdbxStoredVaultEntry,
        payload: JSONObject
    ) {
        val credentialId = payload.optString("credential_id")
        if (credentialId.isBlank()) return
        passkeyDao.insert(
            PasskeyEntry(
                id = 0L,
                credentialId = credentialId,
                rpId = payload.optString("rp_id"),
                rpName = payload.optString("rp_name").ifBlank { stored.title },
                userId = payload.optString("user_id"),
                userName = payload.optString("user_name"),
                userDisplayName = payload.optString("user_display_name"),
                publicKeyAlgorithm = payload.optInt("public_key_algorithm", -7),
                publicKey = payload.optString("public_key"),
                privateKeyAlias = payload.optString("private_key_alias"),
                transports = payload.optString("transports", "internal"),
                aaguid = payload.optString("aaguid"),
                signCount = payload.optLong("sign_count", 0L),
                notes = payload.optString("notes"),
                passkeyMode = payload.optString("passkey_mode", PasskeyEntry.MODE_LEGACY),
                mdbxDatabaseId = databaseId
            )
        )
    }

    private suspend fun importAttachmentsFromVault(
        databaseId: Long,
        importedPasswordIds: Map<String, Long>
    ) {
        if (importedPasswordIds.isEmpty()) return
        val attachments = vaultStore.readStoredAttachments(databaseId)
        if (attachments.isEmpty()) return
        val dir = File(context.filesDir, "secure_attachments")
        dir.mkdirs()
        attachments.filterNot { it.deleted }.forEach { stored ->
            val entryId = stored.entryId ?: stored.projectId
            val parentPasswordId = importedPasswordIds[entryId] ?: return@forEach
            if (stored.wrappedCek.isNullOrBlank()) return@forEach
            val relativePath = "${UUID.randomUUID()}.enc"
            File(dir, relativePath).writeBytes(stored.blob)
            attachmentDao.insert(
                Attachment(
                    id = 0L,
                    parentPasswordId = parentPasswordId,
                    source = AttachmentSource.LOCAL.name,
                    fileName = stored.fileName,
                    mimeType = stored.mimeType.ifBlank { "application/octet-stream" },
                    sizeBytes = stored.originalSize,
                    sha256Hex = stored.contentHash,
                    wrappedCek = stored.wrappedCek,
                    localPath = relativePath,
                    bitwardenAttachmentId = null,
                    bitwardenUrl = null,
                    bitwardenFileKeyEnc = null,
                    keepassBinaryRef = null,
                    downloadState = AttachmentDownloadState.DOWNLOADED.name,
                    createdAt = stored.createdAtMillis,
                    updatedAt = stored.updatedAtMillis,
                    isDeleted = false,
                    deletedAt = null
                )
            )
        }
    }

    private suspend fun refreshVaultFromSource(databaseId: Long) {
        val database = databaseDao.getDatabaseById(databaseId)
            ?: throw IllegalStateException("Vault not found")
        val workingCopy = database.workingCopyPath?.let { File(it) }
            ?: File(database.filePath).takeIf { database.storageLocationEnum == MdbxStorageLocation.INTERNAL }
            ?: throw IllegalStateException("Working copy not found")

        when (database.sourceTypeEnum) {
            MdbxSourceType.LOCAL_INTERNAL -> {
                if (!workingCopy.exists()) {
                    throw IllegalStateException("Local working copy missing: ${workingCopy.absolutePath}")
                }
            }
            MdbxSourceType.LOCAL_EXTERNAL -> {
                val sourceUri = Uri.parse(database.filePath)
                val sourceBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot read external vault")
                workingCopy.parentFile?.mkdirs()
                if (!workingCopy.exists()) {
                    workingCopy.writeBytes(sourceBytes)
                    vaultStore.validateExistingVaultFile(workingCopy)
                } else {
                    val incomingCopy = writeIncomingTempCopy(databaseId, sourceBytes)
                    try {
                        vaultStore.applyIncomingVaultFile(databaseId, incomingCopy)
                    } finally {
                        incomingCopy.delete()
                    }
                }
            }
            MdbxSourceType.REMOTE_WEBDAV -> {
                val source = database.sourceId?.let { remoteSourceDao.getSourceById(it) }
                    ?: throw IllegalStateException("MDBX remote source not found")
                val sourceBytes = readRemoteVaultBytes(source)
                workingCopy.parentFile?.mkdirs()
                if (!workingCopy.exists()) {
                    workingCopy.writeBytes(sourceBytes)
                    vaultStore.validateExistingVaultFile(workingCopy)
                } else {
                    val incomingCopy = writeIncomingTempCopy(databaseId, sourceBytes)
                    try {
                        vaultStore.applyIncomingVaultFile(databaseId, incomingCopy)
                    } finally {
                        incomingCopy.delete()
                    }
                }
            }
        }

        importEntriesFromVault(databaseId)
        databaseDao.updateDatabase(
            database.copy(
                lastSyncedAt = System.currentTimeMillis(),
                lastSyncStatus = MdbxSyncStatus.IN_SYNC.name,
                lastSyncError = null,
                workingCopyPath = workingCopy.absolutePath,
                cacheCopyPath = workingCopy.absolutePath,
                isOfflineAvailable = true
            )
        )
    }

    private fun writeIncomingTempCopy(databaseId: Long, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "mdbx-incoming").apply { mkdirs() }
        return File(dir, "incoming-$databaseId-${UUID.randomUUID()}.mdbx").apply {
            writeBytes(bytes)
        }
    }

    private suspend fun readRemoteVaultBytes(source: MdbxRemoteSource): ByteArray {
        val baseUrl = source.baseUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("MDBX remote source base URL missing")
        val username = source.usernameEncrypted?.let { securityManager.decryptData(it) }
            ?: throw IllegalStateException("MDBX remote username missing")
        val password = source.passwordEncrypted?.let { securityManager.decryptData(it) }
            ?: throw IllegalStateException("MDBX remote password missing")
        val remotePath = source.remotePath.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("MDBX remote path missing")
        val fileSource = WebDavMdbxFileSource(baseUrl, username, password)
        return fileSource.readFile(remotePath)
    }

    // ---

    sealed class OperationState {
        data object Idle : OperationState()
        data class Loading(val message: String) : OperationState()
        data class Success(val message: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }

    sealed class MdbxConflictDialogState {
        data object Hidden : MdbxConflictDialogState()
        data class Visible(
            val databaseId: Long,
            val databaseName: String,
            val conflicts: List<MdbxConflictSummary> = emptyList(),
            val isLoading: Boolean = false
        ) : MdbxConflictDialogState()
    }

    sealed class MdbxDeltaDialogState {
        data object Hidden : MdbxDeltaDialogState()
        data class Visible(
            val databaseId: Long,
            val databaseName: String,
            val deltas: List<MdbxDeltaSummary> = emptyList(),
            val snapshots: List<MdbxSnapshotSummary> = emptyList(),
            val isLoading: Boolean = false,
            val selectedDiffCommitId: String? = null,
            val diffItems: List<MdbxCommitDiff> = emptyList(),
            val isDiffLoading: Boolean = false,
            val isSnapshotLoading: Boolean = false
        ) : MdbxDeltaDialogState()
    }

    sealed class MdbxAdvancedDialogState {
        data object Hidden : MdbxAdvancedDialogState()
        data class Visible(
            val databaseId: Long,
            val databaseName: String,
            val diagnostics: MdbxVaultDiagnostics? = null,
            val exportedBundleJson: String? = null,
            val lastExportedBundle: MdbxSyncBundle? = null,
            val lastImportResult: MdbxApplyResult? = null,
            val lastBenchmarkResult: MdbxBenchmarkResult? = null,
            val message: String? = null,
            val isLoading: Boolean = false
        ) : MdbxAdvancedDialogState()
    }
}

data class MdbxKeyFileSelection(
    val uri: String?,
    val name: String,
    val fingerprint: String,
    val bytes: ByteArray
) {
    val shortFingerprint: String
        get() = fingerprint.take(12)
}
