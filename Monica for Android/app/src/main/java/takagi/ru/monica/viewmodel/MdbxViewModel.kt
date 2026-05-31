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
import kotlinx.coroutines.Job
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
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDao
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
import takagi.ru.monica.mdbx.MdbxDiagLogger
import takagi.ru.monica.repository.MdbxConflictResolution
import takagi.ru.monica.repository.MdbxConflictSummary
import takagi.ru.monica.repository.MdbxCommitDiff
import takagi.ru.monica.repository.MdbxDeltaSummary
import takagi.ru.monica.repository.MdbxApplyResult
import takagi.ru.monica.repository.MdbxBenchmarkResult
import takagi.ru.monica.repository.MdbxSnapshotSummary
import takagi.ru.monica.repository.MdbxStoredVaultEntry
import takagi.ru.monica.repository.MdbxStructurePreview
import takagi.ru.monica.repository.MdbxSyncBundle
import takagi.ru.monica.repository.MdbxVaultCredential
import takagi.ru.monica.repository.MdbxVaultCrypto
import takagi.ru.monica.repository.MdbxVaultDiagnostics
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FileSourceEntry
import takagi.ru.monica.utils.WebDavKeePassFileSource
import takagi.ru.monica.utils.OneDriveAuthManager
import takagi.ru.monica.utils.OneDriveKeePassFileSource
import takagi.ru.monica.utils.OneDriveMdbxFileSource
import takagi.ru.monica.utils.WebDavMdbxFileSource
import java.io.File
import java.text.Normalizer
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis
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
    private val customFieldDao: CustomFieldDao,
    private val securityManager: SecurityManager
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val vaultStore = MdbxVaultStore(
        context.applicationContext,
        databaseDao,
        securityManager,
        remoteSourceDao,
        passwordEntryDao,
        secureItemDao,
        customFieldDao
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

    private val _pendingSyncCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val pendingSyncCounts: StateFlow<Map<Long, Int>> = _pendingSyncCounts.asStateFlow()

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

    private val autoSyncLastStartedAt = mutableMapOf<Long, Long>()
    private val activeVaultPrefs =
        context.applicationContext.getSharedPreferences(ACTIVE_VAULT_PREFS_NAME, Context.MODE_PRIVATE)
    private val _activeMdbxDatabaseId = MutableStateFlow(
        activeVaultPrefs.getLong(ACTIVE_VAULT_ID_KEY, NO_ACTIVE_VAULT_ID)
            .takeIf { it > 0L }
    )
    val activeMdbxDatabaseId: StateFlow<Long?> = _activeMdbxDatabaseId.asStateFlow()
    private var activePreloadJob: Job? = null
    private var activePreloadDatabaseId: Long? = null
    private val deltaHistoryCache = ConcurrentHashMap<Long, CachedDeltaHistory>()
    private val structurePreviewCache =
        ConcurrentHashMap<SnapshotStructureCacheKey, MdbxStructurePreview>()

    private companion object {
        const val ACTIVE_VAULT_PREFS_NAME = "mdbx_active_vault"
        const val ACTIVE_VAULT_ID_KEY = "last_active_mdbx_database_id"
        const val NO_ACTIVE_VAULT_ID = -1L
    }

    private data class CachedDeltaHistory(
        val deltas: List<MdbxDeltaSummary>,
        val snapshots: List<MdbxSnapshotSummary>
    )

    private data class SnapshotStructureCacheKey(
        val databaseId: Long,
        val snapshotId: String
    )

    fun activateMdbxDatabase(databaseId: Long) {
        if (_activeMdbxDatabaseId.value != databaseId) {
            _activeMdbxDatabaseId.value = databaseId
            activeVaultPrefs.edit().putLong(ACTIVE_VAULT_ID_KEY, databaseId).apply()
        }
        viewModelScope.launch(Dispatchers.IO) {
            databaseDao.updateLastAccessedTime(databaseId)
        }
        preloadActiveMdbxDatabase(databaseId)
    }

    fun forgetActiveMdbxDatabaseIf(databaseId: Long) {
        if (_activeMdbxDatabaseId.value == databaseId) {
            _activeMdbxDatabaseId.value = null
            activeVaultPrefs.edit().remove(ACTIVE_VAULT_ID_KEY).apply()
            activePreloadJob?.cancel()
            activePreloadJob = null
            activePreloadDatabaseId = null
        }
    }

    fun preloadActiveMdbxDatabase(databaseId: Long) {
        val runningJob = activePreloadJob
        if (runningJob?.isActive == true && activePreloadDatabaseId == databaseId) {
            return
        }
        activePreloadJob?.cancel()
        activePreloadDatabaseId = databaseId
        activePreloadJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            MdbxDiagLogger.append("[MDBX][activePreload] start databaseId=$databaseId")
            try {
                var deltas: List<MdbxDeltaSummary> = emptyList()
                var snapshots: List<MdbxSnapshotSummary> = emptyList()
                val diagnostic = withContext(Dispatchers.IO) {
                    val database = databaseDao.getDatabaseById(databaseId)
                        ?: return@withContext null
                    val diagnostic = vaultStore.getVaultDiagnostics(database.id)
                    deltas = vaultStore.listDeltaHistory(database.id)
                    snapshots = vaultStore.listSnapshots(database.id)
                    diagnostic
                }
                if (diagnostic == null) {
                    forgetActiveMdbxDatabaseIf(databaseId)
                    MdbxDiagLogger.append("[MDBX][activePreload] missing databaseId=$databaseId")
                    return@launch
                }
                if (_activeMdbxDatabaseId.value != databaseId) {
                    MdbxDiagLogger.append("[MDBX][activePreload] discarded databaseId=$databaseId active=${_activeMdbxDatabaseId.value ?: "-"}")
                    return@launch
                }
                applyVaultDiagnostic(databaseId, diagnostic)
                updateDeltaHistoryCache(databaseId, deltas, snapshots)
                MdbxDiagLogger.append(
                    "[MDBX][activePreload] success databaseId=$databaseId deltas=${deltas.size} snapshots=${snapshots.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
                )
            } catch (e: Exception) {
                MdbxDiagLogger.append(
                    "[MDBX][activePreload] failure databaseId=$databaseId error=${e::class.java.simpleName}:${e.message}"
                )
            } finally {
                if (activePreloadDatabaseId == databaseId) {
                    activePreloadDatabaseId = null
                }
            }
        }
    }

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
            val requestedName = name.trim()
            MdbxDiagLogger.append(
                "[MDBX][createLocalVault] start name=${requestedName.ifBlank { "<blank>" }} customDir=${customDirectoryUri != null} uri=${customDirectoryUri ?: "-"} unlock=${unlockMethod.name} tiga=${tigaMode.name}"
            )

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
                    MdbxDiagLogger.append(
                        "[MDBX][createLocalVault] inserted name=$displayName sourceType=${sourceType.name} storage=${storageLocation.name} filePath=$filePath workingCopy=${localVaultFile.absolutePath}"
                    )
                }

                _operationState.value = OperationState.Success(
                    "Local MDBX vault \"$name\" created"
                )
                MdbxDiagLogger.append(
                    "[MDBX][createLocalVault] success name=${requestedName.ifBlank { "<blank>" }}"
                )
            } catch (e: Exception) {
                MdbxDiagLogger.append(
                    "[MDBX][createLocalVault] failure name=${requestedName.ifBlank { "<blank>" }} error=${e::class.java.simpleName}:${e.message}"
                )
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

    data class OneDriveMdbxDirectoryListing(
        val currentPath: String,
        val entries: List<FileSourceEntry>
    )

    suspend fun listOneDriveMdbxDirectory(
        accountId: String,
        currentPath: String?
    ): Result<OneDriveMdbxDirectoryListing> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedPath = OneDriveKeePassFileSource.normalizeOptionalRemotePath(currentPath)
            val entries = OneDriveMdbxFileSource(context, accountId).listDirectory(normalizedPath)
            OneDriveMdbxDirectoryListing(
                currentPath = normalizedPath,
                entries = entries
            )
        }
    }

    fun createOneDriveVault(
        name: String,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        accountId: String,
        accountLabel: String,
        directoryPath: String?,
        description: String?
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Creating MDBX vault on OneDrive...")

            try {
                withContext(Dispatchers.IO) {
                    val normalizedDir = OneDriveKeePassFileSource.normalizeOptionalRemotePath(directoryPath)
                    val fileSource = OneDriveMdbxFileSource(context, accountId)

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

                    val remotePath = OneDriveKeePassFileSource.buildChildPath(normalizedDir, remoteFileName)

                    val encryptedAccountId = securityManager.encryptData(accountId)
                    val accessTokenSession = OneDriveAuthManager(context).acquireAccessToken(accountId)
                    val encryptedAccessToken = securityManager.encryptData(
                        accessTokenSession.accessToken ?: throw IllegalStateException("OneDrive access token unavailable")
                    )

                    val sourceId = remoteSourceDao.insertSource(
                        MdbxRemoteSource(
                            displayName = displayName,
                            remotePath = remotePath,
                            remoteParentPath = normalizedDir.ifBlank { null },
                            baseUrl = null,
                            usernameEncrypted = encryptedAccountId,
                            passwordEncrypted = encryptedAccessToken
                        )
                    )

                    val encryptedMasterPassword =
                        masterPassword.takeIf { credential.requiresPassword() }
                            ?.let { securityManager.encryptData(normalizeMdbxPassword(it)) }

                    databaseDao.insertDatabase(
                        LocalMdbxDatabase(
                            name = displayName,
                            filePath = remotePath,
                            storageLocation = MdbxStorageLocation.REMOTE_WEBDAV.name,
                            sourceType = MdbxSourceType.REMOTE_ONEDRIVE.name,
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
                    "MDBX vault \"$name\" created on OneDrive"
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to create vault on OneDrive: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun connectToOneDriveVault(
        name: String,
        masterPassword: String,
        unlockMethod: MdbxUnlockMethod,
        keyFile: MdbxKeyFileSelection?,
        tigaMode: MdbxTigaMode,
        accountId: String,
        accountLabel: String,
        remoteFilePath: String,
        description: String?
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Connecting to OneDrive MDBX vault...")

            try {
                withContext(Dispatchers.IO) {
                    val displayName = name.trim().ifBlank {
                        throw IllegalArgumentException("Vault name cannot be empty")
                    }
                    val fileSource = OneDriveMdbxFileSource(context, accountId)
                    fileSource.testConnection().getOrThrow()

                    val remoteBytes = fileSource.readFile(remoteFilePath)

                    val vaultDir = File(context.filesDir, "mdbx")
                    check(vaultDir.mkdirs() || vaultDir.exists()) {
                        "Unable to create MDBX directory"
                    }
                    val localFile = File(vaultDir, "onedrive_${UUID.randomUUID()}.mdbx")
                    localFile.writeBytes(remoteBytes)

                    vaultStore.validateExistingVaultFile(localFile)
                    val detectedMode = vaultStore.readTigaModeFromVaultFile(localFile)
                    val detectedUnlockMethod = vaultStore.readUnlockMethodFromVaultFile(localFile)
                    val credential = buildCredential(detectedUnlockMethod, masterPassword, keyFile)
                    vaultStore.validateVaultCredentialFile(localFile, credential)

                    val remoteParentPath = OneDriveKeePassFileSource.parentPathOf(remoteFilePath)

                    val encryptedAccountId = securityManager.encryptData(accountId)
                    val accessTokenSession = OneDriveAuthManager(context).acquireAccessToken(accountId)
                    val encryptedAccessToken = securityManager.encryptData(
                        accessTokenSession.accessToken ?: throw IllegalStateException("OneDrive access token unavailable")
                    )

                    val sourceId = remoteSourceDao.insertSource(
                        MdbxRemoteSource(
                            displayName = displayName,
                            remotePath = remoteFilePath,
                            remoteParentPath = remoteParentPath,
                            baseUrl = null,
                            usernameEncrypted = encryptedAccountId,
                            passwordEncrypted = encryptedAccessToken
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
                            sourceType = MdbxSourceType.REMOTE_ONEDRIVE.name,
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
                    "Connected to OneDrive MDBX vault \"$name\""
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to connect to OneDrive vault: ${e.message ?: "unknown error"}"
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
            _operationState.value = OperationState.Loading("Syncing MDBX vault...")
            try {
                withContext(Dispatchers.IO) {
                    refreshVaultFromSource(databaseId)
                    refreshSingleVaultState(databaseId)
                }
                _operationState.value = OperationState.Success("MDBX vault synced")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    "Failed to sync vault: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun autoSyncVisibleVault(databaseId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lastStarted = autoSyncLastStartedAt[databaseId] ?: 0L
            if (now - lastStarted < 15_000L) return@launch
            if (_operationState.value is OperationState.Loading) return@launch
            val shouldSync = withContext(Dispatchers.IO) {
                val database = databaseDao.getDatabaseById(databaseId) ?: return@withContext false
                database.lastSyncStatus != MdbxSyncStatus.PENDING_UPLOAD.name &&
                    database.sourceTypeEnum != MdbxSourceType.LOCAL_INTERNAL
            }
            if (!shouldSync) {
                refreshSingleVaultState(databaseId)
                return@launch
            }
            autoSyncLastStartedAt[databaseId] = now
            runCatching {
                withContext(Dispatchers.IO) {
                    refreshVaultFromSource(databaseId)
                    refreshSingleVaultState(databaseId)
                }
            }.onFailure {
                refreshSingleVaultState(databaseId)
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
            applyVaultDiagnostic(database.id, refreshedDiagnostic)
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
                applyVaultDiagnostic(databaseId, refreshedDiagnostic)
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
                applyVaultDiagnostic(databaseId, refreshedDiagnostic)
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
                applyVaultDiagnostic(databaseId, refreshedDiagnostic)
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
        applyVaultDiagnostic(databaseId, diagnostic)
    }

    private fun applyVaultDiagnostic(databaseId: Long, diagnostic: MdbxVaultDiagnostics) {
        _vaultDiagnostics.value = _vaultDiagnostics.value + (databaseId to diagnostic)
        _conflictCounts.value =
            _conflictCounts.value + (databaseId to diagnostic.unresolvedConflictCount)
        _pendingSyncCounts.value =
            _pendingSyncCounts.value + (databaseId to diagnostic.pendingSyncCount)
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
                invalidateMdbxViewCaches(databaseId)
                forgetActiveMdbxDatabaseIf(databaseId)
                _conflictCounts.value = _conflictCounts.value - databaseId
                _pendingSyncCounts.value = _pendingSyncCounts.value - databaseId
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
                        val shouldPrune =
                            database.sourceTypeEnum != MdbxSourceType.REMOTE_WEBDAV &&
                                !database.hasAccessibleLocalSource()
                        if (shouldPrune) {
                            MdbxDiagLogger.append(
                                "[MDBX][pruneMissingLocalVaults] removing id=${database.id} name=${database.name} sourceType=${database.sourceType} filePath=${database.filePath} workingCopy=${database.workingCopyPath ?: "-"}"
                            )
                        }
                        shouldPrune
                    }
                    .map { database ->
                        clearImportedEntries(database.id)
                        databaseDao.deleteDatabaseById(database.id)
                        database.id
                    }
            }
            if (removedIds.isNotEmpty()) {
                val removedSet = removedIds.toSet()
                invalidateMdbxViewCaches(removedSet)
                if (_activeMdbxDatabaseId.value in removedSet) {
                    _activeMdbxDatabaseId.value = null
                    activeVaultPrefs.edit().remove(ACTIVE_VAULT_ID_KEY).apply()
                    activePreloadJob?.cancel()
                    activePreloadJob = null
                }
                _conflictCounts.value = _conflictCounts.value - removedSet
                _pendingSyncCounts.value = _pendingSyncCounts.value - removedSet
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
            _pendingSyncCounts.value = diagnostics.mapValues { (_, diagnostic) ->
                diagnostic.pendingSyncCount
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
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            val sameDatabaseState = current?.takeIf { it.databaseId == database.id }
            val cached = deltaHistoryCache[database.id]
            _deltaDialogState.value = MdbxDeltaDialogState.Visible(
                databaseId = database.id,
                databaseName = database.name,
                deltas = sameDatabaseState?.deltas ?: cached?.deltas.orEmpty(),
                snapshots = sameDatabaseState?.snapshots ?: cached?.snapshots.orEmpty(),
                selectedDiffCommitId = null,
                diffItems = emptyList(),
                isDiffLoading = false,
                isSnapshotLoading = false,
                selectedStructureSnapshotId = null,
                structurePreview = null,
                isStructureLoading = false,
                isLoading = true
            )
            var deltas: List<MdbxDeltaSummary> = emptyList()
            var snapshots: List<MdbxSnapshotSummary> = emptyList()
            val deltaMs = withContext(Dispatchers.IO) {
                measureTimeMillis {
                    deltas = vaultStore.listDeltaHistory(database.id)
                }
            }
            val snapshotMs = withContext(Dispatchers.IO) {
                measureTimeMillis {
                    snapshots = vaultStore.listSnapshots(database.id)
                }
            }
            MdbxDiagLogger.append(
                "[MDBX][perf][showDeltaHistory] databaseId=${database.id} deltas=${deltas.size} snapshots=${snapshots.size} deltaMs=$deltaMs snapshotMs=$snapshotMs cached=${cached != null} keptVisible=${sameDatabaseState != null}"
            )
            updateDeltaHistoryCache(
                databaseId = database.id,
                deltas = deltas,
                snapshots = snapshots
            )
            val refreshedState = (_deltaDialogState.value as? MdbxDeltaDialogState.Visible)
                ?.takeIf { it.databaseId == database.id }
                ?.copy(
                    databaseName = database.name,
                    deltas = deltas,
                    snapshots = snapshots,
                    isLoading = false
                )
                ?.let { clearSelectedStructureIfInvalid(it, snapshots) }
            if (refreshedState != null) {
                _deltaDialogState.value = refreshedState
            }
        }
    }

    private fun invalidateMdbxViewCaches(databaseId: Long) {
        deltaHistoryCache.remove(databaseId)
        structurePreviewCache.keys.removeIf { it.databaseId == databaseId }
    }

    private fun invalidateMdbxViewCaches(databaseIds: Iterable<Long>) {
        databaseIds.forEach(::invalidateMdbxViewCaches)
    }

    private fun updateDeltaHistoryCache(
        databaseId: Long,
        deltas: List<MdbxDeltaSummary>,
        snapshots: List<MdbxSnapshotSummary>
    ) {
        deltaHistoryCache[databaseId] = CachedDeltaHistory(
            deltas = deltas,
            snapshots = snapshots
        )
    }

    private fun updateStructurePreviewCache(
        databaseId: Long,
        snapshotId: String,
        preview: MdbxStructurePreview
    ) {
        structurePreviewCache[SnapshotStructureCacheKey(databaseId, snapshotId)] = preview
    }

    private fun cachedStructurePreview(
        databaseId: Long,
        snapshotId: String
    ): MdbxStructurePreview? =
        structurePreviewCache[SnapshotStructureCacheKey(databaseId, snapshotId)]

    private fun clearSelectedStructureIfInvalid(
        state: MdbxDeltaDialogState.Visible,
        snapshots: List<MdbxSnapshotSummary>
    ): MdbxDeltaDialogState.Visible {
        val selectedSnapshotId = state.selectedStructureSnapshotId ?: return state
        return if (snapshots.any { it.snapshotId == selectedSnapshotId }) {
            state
        } else {
            state.copy(
                selectedStructureSnapshotId = null,
                structurePreview = null,
                isStructureLoading = false
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

    fun closeCommitDiff() {
        val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible ?: return
        _deltaDialogState.value = current.copy(
            selectedDiffCommitId = null,
            diffItems = emptyList(),
            isDiffLoading = false
        )
    }

    fun showSnapshotStructure(databaseId: Long, snapshotId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                ?: return@launch
            val cachedPreview = cachedStructurePreview(databaseId, snapshotId)
            _deltaDialogState.value = current.copy(
                selectedStructureSnapshotId = snapshotId,
                structurePreview = current.structurePreview
                    ?.takeIf { current.selectedStructureSnapshotId == snapshotId }
                    ?: cachedPreview,
                isStructureLoading = true,
                selectedDiffCommitId = null,
                diffItems = emptyList(),
                isDiffLoading = false
            )
            try {
                var loadedPreview: MdbxStructurePreview? = null
                val elapsedMs = withContext(Dispatchers.IO) {
                    measureTimeMillis {
                        loadedPreview = vaultStore.getSnapshotStructurePreview(databaseId, snapshotId)
                    }
                }
                val preview = loadedPreview
                    ?: throw IllegalStateException("MDBX snapshot structure did not load")
                MdbxDiagLogger.append(
                    "[MDBX][perf][showSnapshotStructure] databaseId=$databaseId snapshotId=${snapshotId.take(8)} currentNodes=${preview.currentNodes.size} snapshotNodes=${preview.snapshotNodes.size} elapsedMs=$elapsedMs cached=${cachedPreview != null}"
                )
                val latest = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                    ?: return@launch
                updateStructurePreviewCache(databaseId, snapshotId, preview)
                _deltaDialogState.value = latest.copy(
                    selectedStructureSnapshotId = snapshotId,
                    structurePreview = preview,
                    isStructureLoading = false
                )
            } catch (e: Exception) {
                val latest = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
                _deltaDialogState.value = latest?.copy(
                    selectedStructureSnapshotId = null,
                    structurePreview = null,
                    isStructureLoading = false
                ) ?: MdbxDeltaDialogState.Hidden
                _operationState.value = OperationState.Error(
                    "Failed to load MDBX snapshot structure: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun closeSnapshotStructure() {
        val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible ?: return
        _deltaDialogState.value = current.copy(
            selectedStructureSnapshotId = null,
            structurePreview = null,
            isStructureLoading = false
        )
    }

    fun revertCommit(databaseId: Long, commitId: String) {
        viewModelScope.launch {
            val current = _deltaDialogState.value as? MdbxDeltaDialogState.Visible
            _deltaDialogState.value = current?.copy(isLoading = true)
                ?: MdbxDeltaDialogState.Hidden
            try {
                invalidateMdbxViewCaches(databaseId)
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
                applyVaultDiagnostic(databaseId, refreshedDiagnostic)
                updateDeltaHistoryCache(databaseId, refreshedDeltas, refreshedSnapshots)
                val refreshedState = current?.copy(
                    deltas = refreshedDeltas,
                    snapshots = refreshedSnapshots,
                    selectedDiffCommitId = null,
                    diffItems = emptyList(),
                    isLoading = false,
                    isDiffLoading = false
                )?.let { clearSelectedStructureIfInvalid(it, refreshedSnapshots) }
                _deltaDialogState.value = refreshedState ?: MdbxDeltaDialogState.Hidden
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
                invalidateMdbxViewCaches(databaseId)
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
                invalidateMdbxViewCaches(databaseId)
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
                invalidateMdbxViewCaches(databaseId)
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
                invalidateMdbxViewCaches(databaseId)
                val deletedCount = withContext(Dispatchers.IO) {
                    vaultStore.pruneAutomaticSnapshots(databaseId, keepCount = 0)
                }
                refreshDeltaDialogAfterSnapshotMutation(databaseId, current)
                _operationState.value = OperationState.Success(
                    "已清理 $deletedCount 个自动快照"
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
        applyVaultDiagnostic(databaseId, refreshedDiagnostic)
        updateDeltaHistoryCache(databaseId, refreshedDeltas, refreshedSnapshots)
        val refreshedState = previousState?.copy(
            deltas = refreshedDeltas,
            snapshots = refreshedSnapshots,
            selectedDiffCommitId = null,
            diffItems = emptyList(),
            isLoading = false,
            isDiffLoading = false,
            isSnapshotLoading = false
        )?.let { clearSelectedStructureIfInvalid(it, refreshedSnapshots) }
        _deltaDialogState.value = refreshedState ?: MdbxDeltaDialogState.Hidden
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
                applyVaultDiagnostic(databaseId, refreshedDiagnostic)
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
        MdbxDiagLogger.append(
            "[MDBX][createVaultFileInCustomDir] start name=$displayName treeUri=$treeUri tiga=$tigaMode unlock=${credential.unlockMethod.name}"
        )
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

        MdbxDiagLogger.append(
            "[MDBX][createVaultFileInCustomDir] success name=$displayName externalUri=${createdFile.uri} localCopy=${localVaultFile.absolutePath}"
        )

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
        invalidateMdbxViewCaches(databaseId)
        var entries: List<MdbxStoredVaultEntry> = emptyList()
        val readMs = measureTimeMillis {
            entries = vaultStore.readStoredEntries(databaseId)
        }
        val payloadByEntryId = mutableMapOf<String, JSONObject>()
        val importedPasswordIds = mutableMapOf<String, Long>()
        val importedSecureItemIds = mutableMapOf<String, Long>()
        val existingPasswordsByEntryId = passwordEntryDao.getByMdbxDatabaseIdSync(databaseId)
            .dedupeMdbxPasswordRowsByEntryId()
            .mapNotNull { entry -> entry.replicaGroupId?.let { it to entry } }
            .toMap()
        val existingSecureItemsByEntryId = secureItemDao.getByMdbxDatabaseIdSync(databaseId)
            .dedupeMdbxSecureItemRowsByEntryId()
            .mapNotNull { item -> item.mdbxPrimaryImportEntryId()?.let { entryId -> entryId to item } }
            .toMap()
        val existingPasskeysByEntryId = passkeyDao.getByMdbxDatabaseId(databaseId)
            .mapNotNull { passkey ->
                passkey.credentialId.takeIf { it.isNotBlank() }?.let { credentialId ->
                    "passkey:$credentialId" to passkey
                }
            }
            .toMap()
        val activePasswordEntryIds = mutableSetOf<String>()
        val activeSecureItemEntryIds = mutableSetOf<String>()
        val activePasskeyEntryIds = mutableSetOf<String>()
        val reconcileMs = measureTimeMillis {
        }
        val importMs = measureTimeMillis {
            entries.filterNot { it.deleted }.forEach { stored ->
                val payload = runCatching { JSONObject(stored.payloadJson) }.getOrNull()
                    ?: return@forEach
                payloadByEntryId[stored.entryId] = payload
                if (stored.entryType == "login") {
                    activePasswordEntryIds += stored.entryId
                    val passwordId = importPasswordEntry(
                        databaseId = databaseId,
                        stored = stored,
                        payload = payload,
                        existing = existingPasswordsByEntryId[stored.entryId]
                    )
                    importedPasswordIds[stored.entryId] = passwordId
                }
            }

            entries.filterNot { it.deleted }.forEach { stored ->
                val payload = payloadByEntryId[stored.entryId] ?: return@forEach
                when (stored.entryType) {
                    "note", "totp", "card", "document-ref" -> {
                        activeSecureItemEntryIds += stored.entryId
                        importSecureItem(
                            databaseId = databaseId,
                            stored = stored,
                            payload = payload,
                            importedPasswordIds = importedPasswordIds,
                            existing = existingSecureItemsByEntryId[stored.entryId]
                        )
                            ?.let { secureItemId -> importedSecureItemIds[stored.entryId] = secureItemId }
                    }
                    "passkey" -> {
                        activePasskeyEntryIds += stored.entryId
                        importPasskey(
                            databaseId = databaseId,
                            stored = stored,
                            payload = payload,
                            existing = existingPasskeysByEntryId[stored.entryId]
                        )
                    }
                }
            }
            restoreImportedBindings(payloadByEntryId, importedPasswordIds, importedSecureItemIds)
            existingPasswordsByEntryId
                .filterKeys { it !in activePasswordEntryIds }
                .values
                .forEach { passwordEntryDao.deletePasswordEntryById(it.id) }
            existingSecureItemsByEntryId
                .filterKeys { it !in activeSecureItemEntryIds }
                .values
                .forEach { secureItemDao.deleteItemById(it.id) }
            existingPasskeysByEntryId
                .filterKeys { it !in activePasskeyEntryIds }
                .values
                .forEach { passkeyDao.deleteByRecordId(it.id) }
        }
        val attachmentMs = measureTimeMillis {
            importAttachmentsFromVault(databaseId, importedPasswordIds)
        }
        MdbxDiagLogger.append(
            "[MDBX][perf][importEntriesFromVault] databaseId=$databaseId entries=${entries.size} active=${entries.count { !it.deleted }} passwords=${importedPasswordIds.size} secureItems=${importedSecureItemIds.size} readMs=$readMs reconcileMs=$reconcileMs importMs=$importMs attachmentMs=$attachmentMs"
        )
    }

    private suspend fun clearImportedEntries(databaseId: Long) {
        passwordEntryDao.deleteAllByMdbxDatabaseId(databaseId)
        secureItemDao.deleteAllByMdbxDatabaseId(databaseId)
        passkeyDao.deleteAllByMdbxDatabaseId(databaseId)
    }

    private suspend fun List<PasswordEntry>.dedupeMdbxPasswordRowsByEntryId(): List<PasswordEntry> {
        if (isEmpty()) return this
        val keepIds = mutableSetOf<Long>()
        groupBy { it.replicaGroupId?.takeIf(String::isNotBlank) }.forEach { (entryId, rows) ->
            if (entryId == null) {
                keepIds += rows.map { it.id }
                return@forEach
            }
            val keeper = rows.maxByOrNull { it.updatedAt.time } ?: return@forEach
            keepIds += keeper.id
            rows.filterNot { it.id == keeper.id }.forEach { duplicate ->
                passwordEntryDao.deletePasswordEntryById(duplicate.id)
            }
        }
        return filter { it.id in keepIds }
    }

    private suspend fun List<SecureItem>.dedupeMdbxSecureItemRowsByEntryId(): List<SecureItem> {
        if (isEmpty()) return this
        val keepIds = mutableSetOf<Long>()
        groupBy { it.mdbxPrimaryImportEntryId() }.forEach { (entryId, rows) ->
            if (entryId == null) {
                keepIds += rows.map { it.id }
                return@forEach
            }
            val keeper = rows.maxByOrNull { it.updatedAt.time } ?: return@forEach
            keepIds += keeper.id
            rows.filterNot { it.id == keeper.id }.forEach { duplicate ->
                secureItemDao.deleteItemById(duplicate.id)
            }
        }
        return filter { it.id in keepIds }
    }

    private fun SecureItem.mdbxPrimaryImportEntryId(): String? =
        replicaGroupId?.takeIf(String::isNotBlank) ?: mdbxLegacyEntryId()

    private fun SecureItem.mdbxLegacyEntryId(): String? {
        val prefix = when (itemType) {
            ItemType.NOTE -> "note"
            ItemType.TOTP -> "totp"
            ItemType.BANK_CARD -> "card"
            ItemType.DOCUMENT -> "document-ref"
            ItemType.PASSWORD -> "password"
        }
        return id.takeIf { it > 0 }?.let { "$prefix:$it" }
    }

    private fun LocalMdbxDatabase.hasAccessibleLocalSource(): Boolean {
        return when (sourceTypeEnum) {
            MdbxSourceType.LOCAL_INTERNAL -> {
                val activePath = workingCopyPath?.takeIf { it.isNotBlank() } ?: filePath
                hasReadableFile(activePath)
            }
            MdbxSourceType.LOCAL_EXTERNAL -> {
                hasReadableDocumentUri(filePath) ||
                    hasReadableFile(workingCopyPath)
            }
            MdbxSourceType.REMOTE_WEBDAV -> true
            MdbxSourceType.REMOTE_ONEDRIVE -> true
        }
    }

    private fun hasReadableFile(path: String?): Boolean {
        val normalizedPath = path?.takeIf { it.isNotBlank() } ?: return false
        val file = File(normalizedPath)
        return file.isFile && file.canRead()
    }

    private fun hasReadableDocumentUri(uriString: String): Boolean {
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.read(ByteArray(1))
            } != null
        }.getOrDefault(false)
    }

    private suspend fun importPasswordEntry(
        databaseId: Long,
        stored: MdbxStoredVaultEntry,
        payload: JSONObject,
        existing: PasswordEntry?
    ): Long {
        val plainPassword = payload.optString("password_plain")
            .takeIf { it.isNotEmpty() }
            ?: payload.optString("password").takeIf { it.isNotEmpty() }?.let { value ->
                runCatching { securityManager.decryptData(value) }.getOrDefault(value)
            }
            ?: ""
        val entry = PasswordEntry(
            id = existing?.id ?: 0L,
            title = stored.title,
            website = payload.optString("website"),
            username = payload.optString("username"),
            password = securityManager.encryptData(plainPassword),
            notes = payload.optString("notes"),
            categoryId = existing?.categoryId,
            mdbxDatabaseId = databaseId,
            mdbxFolderId = payload.optMdbxFolderId(),
            replicaGroupId = stored.entryId,
            authenticatorKey = payload.optString("authenticator_key"),
            passkeyBindings = payload.optString("passkey_bindings"),
            loginType = payload.optString("login_type", "PASSWORD"),
            createdAt = existing?.createdAt ?: Date(),
            updatedAt = existing?.updatedAt ?: Date(),
            isFavorite = existing?.isFavorite ?: false,
            sortOrder = existing?.sortOrder ?: 0,
            isGroupCover = existing?.isGroupCover ?: false
        )
        val localPasswordId = if (existing != null) {
            passwordEntryDao.updatePasswordEntry(entry)
            existing.id
        } else {
            passwordEntryDao.insertPasswordEntry(entry)
        }

        restoreCustomFields(localPasswordId, payload)
        return localPasswordId
    }

    private suspend fun restoreCustomFields(entryId: Long, payload: JSONObject) {
        val fields = payload.optJSONArray("custom_fields")
            ?: payload.optJSONArray("customFields")
            ?: return
        val restored = buildList {
            for (index in 0 until fields.length()) {
                val item = fields.optJSONObject(index) ?: continue
                val title = item.optString("title")
                    .ifBlank { item.optString("label") }
                    .trim()
                if (title.isBlank()) continue
                add(
                    CustomField(
                        id = 0L,
                        entryId = entryId,
                        title = title,
                        value = item.optString("value"),
                        isProtected = item.optBoolean("is_protected", item.optBoolean("isProtected", false)),
                        sortOrder = if (item.has("sort_order")) {
                            item.optInt("sort_order", index)
                        } else {
                            item.optInt("sortOrder", index)
                        }
                    )
                )
            }
        }
        customFieldDao.replaceFieldsForEntry(entryId, restored)
    }

    private suspend fun importSecureItem(
        databaseId: Long,
        stored: MdbxStoredVaultEntry,
        payload: JSONObject,
        importedPasswordIds: Map<String, Long>,
        existing: SecureItem?
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
        val item = SecureItem(
            id = existing?.id ?: 0L,
            itemType = itemType,
            title = stored.title,
            notes = payload.optString("notes"),
            itemData = itemData,
            imagePaths = payload.optString("image_paths"),
            categoryId = existing?.categoryId,
            mdbxDatabaseId = databaseId,
            mdbxFolderId = payload.optMdbxFolderId(),
            replicaGroupId = stored.entryId,
            syncStatus = existing?.syncStatus ?: "NONE",
            createdAt = existing?.createdAt ?: Date(),
            updatedAt = existing?.updatedAt ?: Date(),
            isFavorite = existing?.isFavorite ?: false,
            sortOrder = existing?.sortOrder ?: 0
        )
        if (existing != null) {
            secureItemDao.updateItem(item)
            return existing.id
        }
        return secureItemDao.insertItem(item)
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
        payload: JSONObject,
        existing: PasskeyEntry?
    ) {
        val credentialId = payload.optString("credential_id")
        if (credentialId.isBlank()) return
        val passkey = PasskeyEntry(
            id = existing?.id ?: 0L,
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
            mdbxDatabaseId = databaseId,
            mdbxFolderId = payload.optMdbxFolderId(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = existing?.lastUsedAt ?: System.currentTimeMillis(),
            useCount = existing?.useCount ?: 0,
            iconUrl = existing?.iconUrl,
            isDiscoverable = existing?.isDiscoverable ?: true,
            isUserVerificationRequired = existing?.isUserVerificationRequired ?: true,
            isBackedUp = existing?.isBackedUp ?: false,
            boundPasswordId = existing?.boundPasswordId,
            categoryId = existing?.categoryId,
            syncStatus = existing?.syncStatus ?: "NONE"
        )
        if (existing != null) {
            passkeyDao.update(passkey)
        } else {
            passkeyDao.insert(passkey)
        }
    }

    private fun JSONObject.optMdbxFolderId(): String? {
        return optString("mdbx_folder_id")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("root", ignoreCase = true) }
    }

    private suspend fun importAttachmentsFromVault(
        databaseId: Long,
        importedPasswordIds: Map<String, Long>
    ) {
        if (importedPasswordIds.isEmpty()) return
        val attachments = vaultStore.readStoredAttachments(databaseId)
        importedPasswordIds.values.toSet().forEach { parentPasswordId ->
            attachmentDao.purgeByParent(parentPasswordId)
        }
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
            MdbxSourceType.REMOTE_ONEDRIVE -> {
                val source = database.sourceId?.let { remoteSourceDao.getSourceById(it) }
                    ?: throw IllegalStateException("MDBX OneDrive source not found")
                val sourceBytes = readOneDriveVaultBytes(source)
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

    private suspend fun readOneDriveVaultBytes(source: MdbxRemoteSource): ByteArray {
        val accountId = source.usernameEncrypted?.let { securityManager.decryptData(it) }
            ?: throw IllegalStateException("MDBX OneDrive account ID missing")
        val remotePath = source.remotePath.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("MDBX OneDrive remote path missing")
        val fileSource = OneDriveMdbxFileSource(context, accountId)
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
            val isSnapshotLoading: Boolean = false,
            val selectedStructureSnapshotId: String? = null,
            val structurePreview: MdbxStructurePreview? = null,
            val isStructureLoading: Boolean = false
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
