package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.KeePassCipherAlgorithm
import takagi.ru.monica.data.KeePassDatabaseCreationOptions
import takagi.ru.monica.data.KeePassFormatVersion
import takagi.ru.monica.data.KeePassKdfAlgorithm
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.keepass.KeePassDxPasskeyCodec
import takagi.ru.monica.keepass.KeePassPasskeySyncCodec
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.passkey.PasskeyCredentialIdCodec
import takagi.ru.monica.security.SecurityManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class KeePassEntryData(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val monicaLocalId: Long?,
    val entryUuid: String?,
    val groupPath: String?,
    val groupUuid: String?,
    val isInRecycleBin: Boolean
)

data class KeePassGroupInfo(
    val name: String,
    val path: String,
    val uuid: String?,
    val depth: Int = 0,
    val displayPath: String = path
)

data class KeePassSecureItemData(
    val item: SecureItem,
    val sourceMonicaId: Long?,
    val isInRecycleBin: Boolean
)

data class KeePassDatabaseDiagnostics(
    val entryCount: Int,
    val creationOptions: KeePassDatabaseCreationOptions
)

data class KeePassRestoreTarget(
    val groupPath: String?,
    val groupUuid: String?
)

private data class EntryTraversalContext(
    val entry: Entry,
    val groupPath: String?,
    val groupUuid: UUID?,
    val isInRecycleBinByMeta: Boolean
)

private data class GroupTraversalContext(
    val pathKey: String?,
    val groupUuid: UUID,
    val isInRecycleBinByMeta: Boolean
)

private data class RemovedEntryContext(
    val entry: Entry,
    val previousParentUuid: UUID
)

class KeePassKdbxService(
    private val context: Context,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "KeePassKdbxService"
        // Keep unknown-source cache short, but keep known internal files effectively "always warm".
        private const val UNKNOWN_SOURCE_CACHE_TTL_MS = 60_000L
        // Disable post-write full decode verification for normal writes to reduce save latency.
        // The database is still encoded by the library and written atomically/with rollback paths.
        private const val ENABLE_POST_WRITE_DECODE_VERIFICATION = false
        private const val FIELD_MONICA_LOCAL_ID = "MonicaLocalId"
        private const val FIELD_MONICA_ITEM_ID = "MonicaSecureItemId"
        private const val FIELD_MONICA_ITEM_TYPE = "MonicaItemType"
        private const val FIELD_MONICA_ITEM_DATA = "MonicaItemData"
        private const val FIELD_MONICA_IMAGE_PATHS = "MonicaImagePaths"
        private const val FIELD_MONICA_IS_FAVORITE = "MonicaIsFavorite"
        private const val FIELD_MONICA_PASSKEY_CREDENTIAL_ID = "MonicaPasskeyCredentialId"
        private const val FIELD_MONICA_PASSKEY_DATA = "MonicaPasskeyData"
        private const val FIELD_MONICA_PASSKEY_MODE = "MonicaPasskeyMode"
        // kotpass decode 在并发下可能触发 native 崩溃，必须跨实例串行化。
        private val globalDecodeMutex = Mutex()
        // 部分设备/ABI 下 decode 在不同工作线程切换时更易触发 native 崩溃，固定到单线程执行更稳。
        private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "KeePassDecodeThread").apply { isDaemon = true }
        }
        private val decodeDispatcher = decodeExecutor.asCoroutineDispatcher()
        // 写入采用“读-改-写”原子化，避免同进程并发冲突。
        private val globalMutationMutex = Mutex()
        // 跨实例缓存失效信号：某实例更新数据库绑定后，其他实例的本地缓存应立即失效。
        private val externallyInvalidatedDatabaseIds = mutableSetOf<Long>()

        suspend fun <T> withGlobalDecodeLock(block: () -> T): T {
            return globalDecodeMutex.withLock { block() }
        }

        @Synchronized
        fun invalidateProcessCache(databaseId: Long) {
            externallyInvalidatedDatabaseIds += databaseId
        }

        @Synchronized
        private fun consumeProcessCacheInvalidation(databaseId: Long): Boolean {
            return externallyInvalidatedDatabaseIds.remove(databaseId)
        }

        fun inferCreationOptions(keePassDatabase: KeePassDatabase): KeePassDatabaseCreationOptions {
            val resolved = when (keePassDatabase) {
                is KeePassDatabase.Ver3x -> KeePassDatabaseCreationOptions(
                    formatVersion = KeePassFormatVersion.KDBX3,
                    cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                    kdfAlgorithm = KeePassKdfAlgorithm.AES_KDF,
                    transformRounds = keePassDatabase.header.transformRounds.toLong(),
                    memoryBytes = KeePassDatabaseCreationOptions.DEFAULT_ARGON_MEMORY_BYTES,
                    parallelism = 1
                )
                is KeePassDatabase.Ver4x -> {
                    val kdf = keePassDatabase.header.kdfParameters
                    when (kdf) {
                        is KdfParameters.Aes -> KeePassDatabaseCreationOptions(
                            formatVersion = KeePassFormatVersion.KDBX4,
                            cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                            kdfAlgorithm = KeePassKdfAlgorithm.AES_KDF,
                            transformRounds = kdf.rounds.toLong(),
                            memoryBytes = KeePassDatabaseCreationOptions.DEFAULT_ARGON_MEMORY_BYTES,
                            parallelism = 1
                        )
                        is KdfParameters.Argon2 -> KeePassDatabaseCreationOptions(
                            formatVersion = KeePassFormatVersion.KDBX4,
                            cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                            kdfAlgorithm = if (kdf.variant == KdfParameters.Argon2.Variant.Argon2id) {
                                KeePassKdfAlgorithm.ARGON2ID
                            } else {
                                KeePassKdfAlgorithm.ARGON2D
                            },
                            transformRounds = kdf.iterations.toLong(),
                            memoryBytes = kdf.memory.toLong(),
                            parallelism = kdf.parallelism.toInt()
                        )
                    }
                }
            }
            return resolved.normalized()
        }

        private fun resolveCipherAlgorithm(cipherId: UUID): KeePassCipherAlgorithm {
            return when (cipherId) {
                BaseCiphers.Aes.uuid -> KeePassCipherAlgorithm.AES
                BaseCiphers.ChaCha20.uuid -> KeePassCipherAlgorithm.CHACHA20
                TwofishCipher.uuid -> KeePassCipherAlgorithm.TWOFISH
                else -> KeePassCipherAlgorithm.AES
            }
        }
    }
    
    private data class LoadedDatabase(
        val database: LocalKeePassDatabase,
        val credentials: Credentials,
        val keePassDatabase: KeePassDatabase,
        val sourceEtag: String?,
        val sourceLastModified: String?,
        val sourceSignature: DatabaseSourceSignature?
    )

    private data class CachedLoadedDatabase(
        val loaded: LoadedDatabase,
        val cachedAtMs: Long
    )

    private data class DatabaseSourceSignature(
        val sizeBytes: Long,
        val lastModifiedEpochMs: Long
    )

    private data class DatabaseSnapshot(
        val bytes: ByteArray,
        val etag: String?,
        val lastModified: String?,
        val signature: DatabaseSourceSignature?
    )

    private data class MutationPlan<T>(
        val updatedDatabase: KeePassDatabase,
        val result: T,
        val afterWrite: (suspend (LocalKeePassDatabase, KeePassDatabase) -> Unit)? = null
    )

    private data class CredentialsResolution(
        val candidates: List<KeePassCredentialCandidate>
    )

    private val loadedDatabaseCache = mutableMapOf<Long, CachedLoadedDatabase>()

    suspend fun verifyDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val diagnostics = inspectDatabase(
                databaseId = databaseId,
                passwordOverride = passwordOverride,
                keyFileUriOverride = keyFileUriOverride
            ).getOrElse { throw it }
            Result.success(diagnostics.entryCount)
        } catch (e: Exception) {
            val mapped = normalizeError(e)
            val code = (mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED
            Log.e(TAG, "verifyDatabase failed (databaseId=$databaseId, code=$code)", mapped)
            Result.failure(mapped)
        }
    }

    suspend fun verifyExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val diagnostics = inspectExternalDatabase(
                fileUri = fileUri,
                password = password,
                keyFileUri = keyFileUri
            ).getOrElse { throw it }
            Result.success(diagnostics.entryCount)
        } catch (e: Exception) {
            val mapped = normalizeError(e)
            val code = (mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED
            Log.e(
                TAG,
                "verifyExternalDatabase failed (uri=$fileUri, keyFile=${keyFileUri != null}, code=$code)",
                mapped
            )
            Result.failure(mapped)
        }
    }

    suspend fun inspectDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
            val credentials = buildCredentials(
                database,
                passwordOverride = passwordOverride,
                keyFileUriOverride = keyFileUriOverride
            )
            val bytes = readDatabaseBytes(database)
            val (keePassDatabase, _) = decodeDatabaseWithFallback(
                bytes = bytes,
                credentialsResolution = credentials,
                sourceLabel = "databaseId=$databaseId",
                sourceName = database.filePath
            )
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val count = entries.count { context ->
                entryToData(
                    entry = context.entry,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    resolutionContext = resolutionContext
                ) != null
            }
            Result.success(
                KeePassDatabaseDiagnostics(
                    entryCount = count,
                    creationOptions = inferCreationOptions(keePassDatabase)
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun inspectExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> = withContext(Dispatchers.IO) {
        try {
            val credentials = buildCredentialsFromRaw(password = password, keyFileUri = keyFileUri)
            val bytes = readBytesFromUri(fileUri, "无法打开数据库文件")
            val (keePassDatabase, _) = decodeDatabaseWithFallback(
                bytes = bytes,
                credentialsResolution = credentials,
                sourceLabel = "uri=$fileUri",
                sourceName = fileUri.lastPathSegment ?: fileUri.toString()
            )
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val count = entries.count { context ->
                entryToData(
                    entry = context.entry,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    resolutionContext = resolutionContext
                ) != null
            }
            Result.success(
                KeePassDatabaseDiagnostics(
                    entryCount = count,
                    creationOptions = inferCreationOptions(keePassDatabase)
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun createGroup(
        databaseId: Long,
        groupName: String,
        parentPath: String? = null
    ): Result<KeePassGroupInfo> = withContext(Dispatchers.IO) {
        try {
            val normalizedName = groupName.trim()
            if (normalizedName.isBlank()) {
                throw IllegalArgumentException("分组名称不能为空")
            }
            val groupInfo = mutateDatabase(databaseId) { loaded ->
                val parentSegments = decodeKeePassPathSegments(parentPath)
                val result = addGroupToPath(
                    group = loaded.keePassDatabase.content.group,
                    parentSegments = parentSegments,
                    newGroupName = normalizedName,
                    currentPathKey = ""
                )
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = result.second
                )
            }
            Result.success(groupInfo)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun renameGroup(
        databaseId: Long,
        groupPath: String,
        newName: String
    ): Result<KeePassGroupInfo> = withContext(Dispatchers.IO) {
        try {
            val normalizedName = newName.trim()
            if (normalizedName.isBlank()) {
                throw IllegalArgumentException("分组名称不能为空")
            }
            val pathSegments = decodeKeePassPathSegments(groupPath)
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }
            val groupInfo = mutateDatabase(databaseId) { loaded ->
                val result = renameGroupByPath(
                    group = loaded.keePassDatabase.content.group,
                    pathSegments = pathSegments,
                    newName = normalizedName,
                    currentPathKey = ""
                )
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = result.second
                )
            }
            Result.success(groupInfo)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deleteGroup(
        databaseId: Long,
        groupPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pathSegments = decodeKeePassPathSegments(groupPath)
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }
            mutateDatabase(databaseId) { loaded ->
                val result = removeGroupByPath(
                    group = loaded.keePassDatabase.content.group,
                    pathSegments = pathSegments
                )
                if (!result.second) {
                    throw IllegalArgumentException("分组不存在: $groupPath")
                }
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = Unit
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readPasswordEntries(databaseId: Long): Result<List<KeePassEntryData>> = withContext(Dispatchers.IO) {
        try {
            val (database, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = entries.mapNotNull { context ->
                entryToData(
                    entry = context.entry,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    resolutionContext = resolutionContext
                )
            }
            dao.updateEntryCount(database.id, data.size)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun listGroups(
        databaseId: Long,
        includeRecycleBin: Boolean = false
    ): Result<List<KeePassGroupInfo>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
            val groups = mutableListOf<KeePassGroupInfo>()
            keePassDatabase.content.group.groups.forEach { group ->
                collectGroups(
                    group = group,
                    parentPathKey = "",
                    depth = 0,
                    result = groups,
                    recycleBinUuid = recycleBinUuid,
                    includeRecycleBin = includeRecycleBin,
                    parentInRecycleBin = false
                )
            }
            Result.success(groups.sortedBy { it.displayPath })
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdatePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                entries.forEach { entry ->
                    val plainPassword = resolvePassword(entry)
                    val updateResult = updateEntry(updatedDatabase, entry, plainPassword)
                    if (updateResult.second) {
                        updatedDatabase = updateResult.first
                    } else {
                        val newEntry = buildEntry(entry, plainPassword)
                        updatedDatabase = updatedDatabase.modifyParentGroup {
                            copy(entries = this.entries + newEntry)
                        }
                        addedCount++
                    }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = addedCount,
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updatePasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val plainPassword = resolvePassword(entry)
                val updateResult = updateEntry(loaded.keePassDatabase, entry, plainPassword)
                val updatedDatabase = if (updateResult.second) updateResult.first else {
                    val newEntry = buildEntry(entry, plainPassword)
                    loaded.keePassDatabase.modifyParentGroup {
                        copy(entries = this.entries + newEntry)
                    }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addPasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val plainPassword = resolvePassword(entry)
                val newEntry = buildEntry(entry, plainPassword)
                val updatedRoot = addEntryToGroupPath(
                    rootGroup = loaded.keePassDatabase.content.group,
                    groupPath = entry.keepassGroupPath,
                    entry = newEntry
                )
                val updatedDatabase = loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deletePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var removedCount = 0
                entries.forEach { entry ->
                    val result = removeEntry(updatedDatabase, entry)
                    updatedDatabase = result.first
                    removedCount += result.second
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = removedCount,
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun movePasswordEntriesToRecycleBin(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val movedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val recycleBinUuid = resolveRecycleBinUuid(loaded.keePassDatabase.content.meta)
                    ?: throw IllegalStateException("KeePass recycle bin unavailable")
                val resolutionContext = buildResolutionContext(loaded.keePassDatabase)
                val recyclePath = findGroupPathByUuid(
                    group = loaded.keePassDatabase.content.group,
                    currentPathKey = null,
                    targetUuid = recycleBinUuid
                ) ?: throw IllegalStateException("KeePass recycle bin path unavailable")

                var rootGroup = loaded.keePassDatabase.content.group
                val removedContexts = mutableListOf<RemovedEntryContext>()
                entries.forEach { entry ->
                    val matcher: (Entry) -> Boolean = { existing ->
                        matchesPasswordEntry(existing, entry, resolutionContext)
                    }
                    val removed = removeAndCollectEntriesInGroup(
                        group = rootGroup,
                        matcher = matcher,
                        inRecycleBin = false,
                        recycleBinUuid = recycleBinUuid,
                        removedEntries = removedContexts
                    )
                    rootGroup = removed.first
                }

                var movedCount = 0
                removedContexts.forEach { context ->
                    val entryWithPreviousParent = runCatching {
                        context.entry.copy(previousParentGroup = context.previousParentUuid)
                    }.getOrDefault(context.entry)
                    rootGroup = addEntryToGroupPath(
                        rootGroup = rootGroup,
                        groupPath = recyclePath,
                        entry = entryWithPreviousParent
                    )
                    movedCount++
                }

                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { rootGroup },
                    result = movedCount,
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(movedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readSecureItems(
        databaseId: Long,
        allowedTypes: Set<ItemType>? = null
    ): Result<List<KeePassSecureItemData>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = entries.mapNotNull { context ->
                entryToSecureItemData(
                    entry = context.entry,
                    databaseId = databaseId,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    allowedTypes = allowedTypes,
                    resolutionContext = resolutionContext
                )
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readPasskeyEntries(databaseId: Long): Result<List<PasskeyEntry>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, _) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = entries.mapNotNull { context ->
                entryToPasskey(
                    entry = context.entry,
                    databaseId = databaseId,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    resolutionContext = resolutionContext
                )
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdateSecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                items.forEach { item ->
                    val updateResult = updateSecureItemInternal(updatedDatabase, item)
                    if (updateResult.second) {
                        updatedDatabase = updateResult.first
                    } else {
                        val newEntry = buildSecureItemEntry(item)
                        val updatedRoot = addEntryToGroupPath(
                            rootGroup = updatedDatabase.content.group,
                            groupPath = item.keepassGroupPath,
                            entry = newEntry
                        )
                        updatedDatabase = updatedDatabase.modifyParentGroup { updatedRoot }
                        addedCount++
                    }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = addedCount)
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdatePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(databaseId = databaseId) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                passkeys.forEach { passkey ->
                    val updateResult = updatePasskeyInternal(updatedDatabase, passkey)
                    if (updateResult.second) {
                        updatedDatabase = updateResult.first
                    } else {
                        val newEntry = buildPasskeyEntry(passkey)
                        val updatedRoot = addEntryToGroupPath(
                            rootGroup = updatedDatabase.content.group,
                            groupPath = passkey.keepassGroupPath,
                            entry = newEntry
                        )
                        updatedDatabase = updatedDatabase.modifyParentGroup { updatedRoot }
                        addedCount++
                    }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = addedCount)
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updateSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val updateResult = updateSecureItemInternal(loaded.keePassDatabase, item)
                val updatedDatabase = if (updateResult.second) {
                    updateResult.first
                } else {
                    val newEntry = buildSecureItemEntry(item)
                    val updatedRoot = addEntryToGroupPath(
                        rootGroup = loaded.keePassDatabase.content.group,
                        groupPath = item.keepassGroupPath,
                        entry = newEntry
                    )
                    loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updatePasskey(
        databaseId: Long,
        passkey: PasskeyEntry
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val updateResult = updatePasskeyInternal(loaded.keePassDatabase, passkey)
                val updatedDatabase = if (updateResult.second) {
                    updateResult.first
                } else {
                    val newEntry = buildPasskeyEntry(passkey)
                    val updatedRoot = addEntryToGroupPath(
                        rootGroup = loaded.keePassDatabase.content.group,
                        groupPath = passkey.keepassGroupPath,
                        entry = newEntry
                    )
                    loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreTarget(
        databaseId: Long,
        preferredGroupPath: String?,
        preferredGroupUuid: String? = null
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val preferredUuid = parseUuid(preferredGroupUuid)
            if (preferredUuid != null) {
                val preferredContext = groupContextIndex[preferredUuid]
                if (preferredContext != null) {
                    val preferredInRecycle = preferredContext.isInRecycleBinByMeta
                    if (!preferredInRecycle) {
                        return@withContext Result.success(
                            KeePassRestoreTarget(
                                groupPath = preferredContext.pathKey,
                                groupUuid = preferredContext.groupUuid.toString()
                            )
                        )
                    }
                }
            }
            if (preferredGroupPath.isNullOrBlank()) {
                return@withContext Result.success(KeePassRestoreTarget(groupPath = null, groupUuid = null))
            }
            val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
            if (recycleBinUuid != null) {
                val inRecycleByMeta = isGroupPathInRecycleBinByMeta(
                    group = keePassDatabase.content.group,
                    currentPathKey = null,
                    targetPathKey = preferredGroupPath,
                    recycleBinUuid = recycleBinUuid,
                    parentInRecycleBin = false
                )
                if (inRecycleByMeta != null) {
                    val resolvedPath = if (inRecycleByMeta) null else preferredGroupPath
                    val resolvedUuid = resolvedPath?.let {
                        findGroupUuidByPath(
                            group = keePassDatabase.content.group,
                            currentPathKey = null,
                            targetPathKey = it
                        )?.toString()
                    }
                    return@withContext Result.success(
                        KeePassRestoreTarget(
                            groupPath = resolvedPath,
                            groupUuid = resolvedUuid
                        )
                    )
                }
            }
            if (isLikelyRecycleBinPath(preferredGroupPath)) {
                return@withContext Result.success(KeePassRestoreTarget(groupPath = null, groupUuid = null))
            }
            Result.success(
                KeePassRestoreTarget(
                    groupPath = preferredGroupPath,
                    groupUuid = findGroupUuidByPath(
                        group = keePassDatabase.content.group,
                        currentPathKey = null,
                        targetPathKey = preferredGroupPath
                    )?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreTargetForPassword(
        databaseId: Long,
        target: PasswordEntry
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val matched = entries.firstOrNull { context ->
                matchesPasswordEntry(context.entry, target, resolutionContext)
            }
                ?: return@withContext resolveRestoreTarget(
                    databaseId = databaseId,
                    preferredGroupPath = target.keepassGroupPath,
                    preferredGroupUuid = target.keepassGroupUuid
                )
            Result.success(
                resolveRestoreTargetFromEntryContext(
                    entryContext = matched,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    groupContextIndex = groupContextIndex
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreGroupPathForPassword(
        databaseId: Long,
        target: PasswordEntry
    ): Result<String?> = withContext(Dispatchers.IO) {
        resolveRestoreTargetForPassword(databaseId, target).map { it.groupPath }
    }

    suspend fun resolveRestoreTargetForSecureItem(
        databaseId: Long,
        target: SecureItem
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val matched = entries.firstOrNull { context ->
                matchesSecureItemEntry(context.entry, target, resolutionContext)
            }
                ?: return@withContext resolveRestoreTarget(
                    databaseId = databaseId,
                    preferredGroupPath = target.keepassGroupPath,
                    preferredGroupUuid = target.keepassGroupUuid
                )
            Result.success(
                resolveRestoreTargetFromEntryContext(
                    entryContext = matched,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    groupContextIndex = groupContextIndex
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreGroupPathForSecureItem(
        databaseId: Long,
        target: SecureItem
    ): Result<String?> = withContext(Dispatchers.IO) {
        resolveRestoreTargetForSecureItem(databaseId, target).map { it.groupPath }
    }

    suspend fun deleteSecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var removedCount = 0
                items.forEach { item ->
                    val result = removeSecureItem(updatedDatabase, item)
                    updatedDatabase = result.first
                    removedCount += result.second
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = removedCount)
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deletePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(databaseId = databaseId) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var removedCount = 0
                passkeys.forEach { passkey ->
                    val result = removePasskey(updatedDatabase, passkey)
                    updatedDatabase = result.first
                    removedCount += result.second
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = removedCount)
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun moveSecureItemsToRecycleBin(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val movedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val recycleBinUuid = resolveRecycleBinUuid(loaded.keePassDatabase.content.meta)
                    ?: throw IllegalStateException("KeePass recycle bin unavailable")
                val resolutionContext = buildResolutionContext(loaded.keePassDatabase)
                val recyclePath = findGroupPathByUuid(
                    group = loaded.keePassDatabase.content.group,
                    currentPathKey = null,
                    targetUuid = recycleBinUuid
                ) ?: throw IllegalStateException("KeePass recycle bin path unavailable")

                var rootGroup = loaded.keePassDatabase.content.group
                val removedContexts = mutableListOf<RemovedEntryContext>()
                items.forEach { item ->
                    val matcher: (Entry) -> Boolean = { existing ->
                        matchesSecureItemEntry(existing, item, resolutionContext)
                    }
                    val removed = removeAndCollectEntriesInGroup(
                        group = rootGroup,
                        matcher = matcher,
                        inRecycleBin = false,
                        recycleBinUuid = recycleBinUuid,
                        removedEntries = removedContexts
                    )
                    rootGroup = removed.first
                }

                var movedCount = 0
                removedContexts.forEach { context ->
                    val entryWithPreviousParent = runCatching {
                        context.entry.copy(previousParentGroup = context.previousParentUuid)
                    }.getOrDefault(context.entry)
                    rootGroup = addEntryToGroupPath(
                        rootGroup = rootGroup,
                        groupPath = recyclePath,
                        entry = entryWithPreviousParent
                    )
                    movedCount++
                }

                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { rootGroup },
                    result = movedCount
                )
            }
            Result.success(movedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    private fun buildEntry(entry: PasswordEntry, plainPassword: String): Entry {
        return Entry(
            uuid = parseUuid(entry.keepassEntryUuid) ?: UUID.randomUUID(),
            fields = buildEntryFields(entry, plainPassword)
        )
    }

    private fun buildPasskeyEntry(passkey: PasskeyEntry): Entry {
        return Entry(
            uuid = UUID.randomUUID(),
            fields = buildPasskeyFields(passkey)
        )
    }

    private fun buildEntryFields(entry: PasswordEntry, plainPassword: String): EntryFields {
        val monicaId = if (entry.id > 0) entry.id.toString() else ""
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain(entry.title),
            "UserName" to EntryValue.Plain(entry.username),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString(plainPassword)),
            "URL" to EntryValue.Plain(entry.website),
            "Notes" to EntryValue.Plain(entry.notes)
        )
        if (monicaId.isNotEmpty()) {
            pairs.add(FIELD_MONICA_LOCAL_ID to EntryValue.Plain(monicaId))
        }
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun buildPasskeyFields(
        passkey: PasskeyEntry,
        existingEntry: Entry? = null
    ): EntryFields {
        val readableTitle = passkey.rpName.ifBlank { passkey.rpId }.ifBlank { "Passkey" }
        val payload = KeePassPasskeySyncCodec.encode(passkey)
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain("$readableTitle [Passkey]"),
            "UserName" to EntryValue.Plain(passkey.userName.ifBlank { passkey.userDisplayName }),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
            "URL" to EntryValue.Plain(
                when {
                    passkey.rpId.isBlank() -> ""
                    "://" in passkey.rpId -> passkey.rpId
                    else -> "https://${passkey.rpId}"
                }
            ),
            "Notes" to EntryValue.Plain(passkey.notes),
            FIELD_MONICA_PASSKEY_CREDENTIAL_ID to EntryValue.Plain(passkey.credentialId),
            FIELD_MONICA_PASSKEY_MODE to EntryValue.Plain(PasskeyEntry.MODE_KEEPASS_COMPAT),
            FIELD_MONICA_PASSKEY_DATA to EntryValue.Encrypted(EncryptedValue.fromString(payload))
        )
        pairs += KeePassDxPasskeyCodec.buildCustomFieldPairs(passkey) { fieldName ->
            existingEntry?.let { getFieldValue(it, fieldName) }.orEmpty()
        }
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun buildSecureItemEntry(item: SecureItem): Entry {
        return Entry(
            uuid = parseUuid(item.keepassEntryUuid) ?: UUID.randomUUID(),
            fields = buildSecureItemFields(item)
        )
    }

    private fun buildSecureItemFields(item: SecureItem): EntryFields {
        val monicaId = if (item.id > 0) item.id.toString() else ""
        val noteForExternal = if (item.itemType == ItemType.NOTE) {
            val decoded = NoteContentCodec.decodeFromItem(item)
            NoteContentCodec.toExternalReadableContent(decoded.content)
        } else {
            item.notes
        }
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain(item.title),
            "UserName" to EntryValue.Plain(""),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
            "URL" to EntryValue.Plain(""),
            "Notes" to EntryValue.Plain(noteForExternal),
            FIELD_MONICA_ITEM_TYPE to EntryValue.Plain(item.itemType.name),
            FIELD_MONICA_ITEM_DATA to EntryValue.Encrypted(EncryptedValue.fromString(item.itemData)),
            FIELD_MONICA_IMAGE_PATHS to EntryValue.Plain(item.imagePaths),
            FIELD_MONICA_IS_FAVORITE to EntryValue.Plain(item.isFavorite.toString())
        )
        if (monicaId.isNotEmpty()) {
            pairs.add(FIELD_MONICA_ITEM_ID to EntryValue.Plain(monicaId))
        }
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun updateEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry,
        plainPassword: String
    ): Pair<KeePassDatabase, Boolean> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesPasswordEntry(existing, entry, resolutionContext)
        }
        val rootGroup = keePassDatabase.content.group
        val removeResult = removeEntryInGroup(rootGroup, matcher)
        val removedCount = removeResult.second
        if (removedCount <= 0) {
            return keePassDatabase to false
        }

        val newEntry = buildEntry(entry, plainPassword)
        val updatedRoot = addEntryToGroupPath(
            rootGroup = removeResult.first,
            groupPath = entry.keepassGroupPath,
            entry = newEntry
        )
        val updatedDatabase = keePassDatabase.modifyParentGroup { updatedRoot }
        return updatedDatabase to true
    }

    private fun updateSecureItemInternal(
        keePassDatabase: KeePassDatabase,
        item: SecureItem
    ): Pair<KeePassDatabase, Boolean> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesSecureItemEntry(existing, item, resolutionContext)
        }
        val updater: (Entry) -> Entry = { existing ->
            existing.copy(fields = buildSecureItemFields(item))
        }
        val result = updateEntryInGroup(keePassDatabase.content.group, matcher, updater)
        val updatedDatabase = if (result.second) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun updatePasskeyInternal(
        keePassDatabase: KeePassDatabase,
        passkey: PasskeyEntry
    ): Pair<KeePassDatabase, Boolean> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesPasskeyEntry(existing, passkey, resolutionContext)
        }
        val updater: (Entry) -> Entry = { existing ->
            existing.copy(fields = buildPasskeyFields(passkey, existing))
        }
        val result = updateEntryInGroup(keePassDatabase.content.group, matcher, updater)
        val updatedDatabase = if (result.second) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun upsertPasskey(
        keePassDatabase: KeePassDatabase,
        passkey: PasskeyEntry
    ): KeePassDatabase {
        val updateResult = updatePasskeyInternal(keePassDatabase, passkey)
        if (updateResult.second) return updateResult.first

        val newEntry = buildPasskeyEntry(passkey)
        val updatedRoot = addEntryToGroupPath(
            rootGroup = keePassDatabase.content.group,
            groupPath = passkey.keepassGroupPath,
            entry = newEntry
        )
        return keePassDatabase.modifyParentGroup { updatedRoot }
    }

    private fun removeEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry
    ): Pair<KeePassDatabase, Int> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesPasswordEntry(existing, entry, resolutionContext)
        }
        val result = removeEntryInGroup(keePassDatabase.content.group, matcher)
        val updatedDatabase = if (result.second > 0) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun removeSecureItem(
        keePassDatabase: KeePassDatabase,
        item: SecureItem
    ): Pair<KeePassDatabase, Int> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesSecureItemEntry(existing, item, resolutionContext)
        }
        val result = removeEntryInGroup(keePassDatabase.content.group, matcher)
        val updatedDatabase = if (result.second > 0) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun removePasskey(
        keePassDatabase: KeePassDatabase,
        passkey: PasskeyEntry
    ): Pair<KeePassDatabase, Int> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesPasskeyEntry(existing, passkey, resolutionContext)
        }
        val result = removeEntryInGroup(keePassDatabase.content.group, matcher)
        val updatedDatabase = if (result.second > 0) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun updateEntryInGroup(
        group: Group,
        matcher: (Entry) -> Boolean,
        updater: (Entry) -> Entry
    ): Pair<Group, Boolean> {
        var updated = false
        val newEntries = group.entries.map { entry ->
            if (!updated && matcher(entry)) {
                updated = true
                updater(entry)
            } else {
                entry
            }
        }
        val newGroups = group.groups.map { sub ->
            val result = updateEntryInGroup(sub, matcher, updater)
            if (result.second) {
                updated = true
            }
            result.first
        }
        return group.copy(entries = newEntries, groups = newGroups) to updated
    }

    private fun removeEntryInGroup(
        group: Group,
        matcher: (Entry) -> Boolean
    ): Pair<Group, Int> {
        val filteredEntries = group.entries.filterNot { matcher(it) }
        var removedCount = group.entries.size - filteredEntries.size
        val newGroups = group.groups.map { sub ->
            val result = removeEntryInGroup(sub, matcher)
            removedCount += result.second
            result.first
        }
        return group.copy(entries = filteredEntries, groups = newGroups) to removedCount
    }

    private fun removeAndCollectEntriesInGroup(
        group: Group,
        matcher: (Entry) -> Boolean,
        inRecycleBin: Boolean,
        recycleBinUuid: UUID?,
        removedEntries: MutableList<RemovedEntryContext>
    ): Pair<Group, Int> {
        val currentInRecycle = inRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        var removedCount = 0

        val keptEntries = mutableListOf<Entry>()
        group.entries.forEach { entry ->
            val shouldRemove = matcher(entry) && !currentInRecycle
            if (shouldRemove) {
                removedEntries += RemovedEntryContext(
                    entry = entry,
                    previousParentUuid = group.uuid
                )
                removedCount++
            } else {
                keptEntries += entry
            }
        }

        val newGroups = group.groups.map { sub ->
            val result = removeAndCollectEntriesInGroup(
                group = sub,
                matcher = matcher,
                inRecycleBin = currentInRecycle,
                recycleBinUuid = recycleBinUuid,
                removedEntries = removedEntries
            )
            removedCount += result.second
            result.first
        }

        return group.copy(entries = keptEntries, groups = newGroups) to removedCount
    }

    private fun addEntryToGroupPath(
        rootGroup: Group,
        groupPath: String?,
        entry: Entry
    ): Group {
        val segments = decodeKeePassPathSegments(groupPath)
        if (segments.isEmpty()) {
            return rootGroup.copy(entries = rootGroup.entries + entry)
        }
        return addEntryToGroupPathSegments(rootGroup, segments, entry)
    }

    private fun addEntryToGroupPathSegments(
        group: Group,
        segments: List<String>,
        entry: Entry
    ): Group {
        if (segments.isEmpty()) {
            return group.copy(entries = group.entries + entry)
        }

        val childName = segments.first()
        val childIndex = group.groups.indexOfFirst { it.name == childName }
        val childGroup = if (childIndex >= 0) {
            group.groups[childIndex]
        } else {
            Group(uuid = UUID.randomUUID(), name = childName)
        }

        val updatedChild = addEntryToGroupPathSegments(
            group = childGroup,
            segments = segments.drop(1),
            entry = entry
        )

        val updatedGroups = group.groups.toMutableList()
        if (childIndex >= 0) {
            updatedGroups[childIndex] = updatedChild
        } else {
            updatedGroups.add(updatedChild)
        }
        return group.copy(groups = updatedGroups)
    }

    private fun matchByKey(
        entry: Entry,
        target: PasswordEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val title = getFieldValue(entry, "Title", resolutionContext)
        val username = getFieldValue(entry, "UserName", resolutionContext)
        val url = getFieldValue(entry, "URL", resolutionContext)
        return title.equals(target.title, true) &&
            username.equals(target.username, true) &&
            url.equals(target.website, true)
    }

    private fun matchesPasswordEntry(
        entry: Entry,
        target: PasswordEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetUuid = parseUuid(target.keepassEntryUuid)
        if (targetUuid != null && entry.uuid == targetUuid) {
            return true
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_LOCAL_ID, resolutionContext).toLongOrNull()
        if (monicaId != null && target.id > 0 && monicaId == target.id) {
            return true
        }
        return matchByKey(entry, target, resolutionContext)
    }

    private fun matchSecureItemByKey(
        entry: Entry,
        target: SecureItem,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val title = getFieldValue(entry, "Title", resolutionContext)
        val itemType = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext)
        return title.equals(target.title, true) &&
            itemType.equals(target.itemType.name, true)
    }

    private fun matchesSecureItemEntry(
        entry: Entry,
        target: SecureItem,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetUuid = parseUuid(target.keepassEntryUuid)
        if (targetUuid != null && entry.uuid == targetUuid) {
            return true
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull()
        if (monicaId != null && target.id > 0 && monicaId == target.id) {
            return true
        }
        return matchSecureItemByKey(entry, target, resolutionContext)
    }

    private fun matchesPasskeyEntry(
        entry: Entry,
        target: PasskeyEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetCredentialId = PasskeyCredentialIdCodec.normalize(target.credentialId) ?: target.credentialId
        val credentialId = getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext)
        if (credentialId.isNotBlank()) {
            val normalized = PasskeyCredentialIdCodec.normalize(credentialId) ?: credentialId
            if (normalized == targetCredentialId) {
                return true
            }
        }

        val keepassDxCredentialId = getFieldValue(entry, KeePassDxPasskeyCodec.FIELD_CREDENTIAL_ID, resolutionContext)
        if (keepassDxCredentialId.isNotBlank()) {
            val normalized = PasskeyCredentialIdCodec.normalize(keepassDxCredentialId) ?: keepassDxCredentialId
            if (normalized == targetCredentialId) {
                return true
            }
        }

        val payload = getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext)
        val decoded = KeePassPasskeySyncCodec.decode(
            raw = payload,
            databaseId = target.keepassDatabaseId ?: -1L,
            groupPath = target.keepassGroupPath,
            groupUuid = null
        )
        val decodedCredentialId = PasskeyCredentialIdCodec.normalize(decoded?.credentialId) ?: decoded?.credentialId
        return decodedCredentialId == targetCredentialId
    }

    private fun entryToData(
        entry: Entry,
        groupPath: String?,
        groupUuid: UUID?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): KeePassEntryData? {
        // Monica 安全项（TOTP/笔记/卡片等）会写入 MonicaItemType，不应进入密码列表。
        if (getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext).isNotBlank()) {
            return null
        }
        if (isPasskeyEntry(entry, resolutionContext)) {
            return null
        }

        val title = getFieldValue(entry, "Title", resolutionContext)
        val username = getFieldValue(entry, "UserName", resolutionContext)
        val password = resolveEntryPassword(entry, resolutionContext)
        val url = getFieldValue(entry, "URL", resolutionContext)
        val notes = getFieldValue(entry, "Notes", resolutionContext)
        if (title.isEmpty() && username.isEmpty() && password.isEmpty() && url.isEmpty() && notes.isEmpty()) {
            return null
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_LOCAL_ID, resolutionContext).toLongOrNull()
        val inRecycleBin = resolveRecycleBinFlag(
            groupPath = groupPath,
            isInRecycleBinByMeta = isInRecycleBinByMeta,
            hasRecycleBinMeta = hasRecycleBinMeta
        )
        return KeePassEntryData(
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            monicaLocalId = monicaId,
            entryUuid = entry.uuid.toString(),
            groupPath = groupPath,
            groupUuid = groupUuid?.toString(),
            isInRecycleBin = inRecycleBin
        )
    }

    private fun entryToSecureItemData(
        entry: Entry,
        databaseId: Long,
        groupPath: String?,
        groupUuid: UUID?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean,
        allowedTypes: Set<ItemType>?,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): KeePassSecureItemData? {
        if (isPasskeyEntry(entry, resolutionContext)) {
            return null
        }
        val typeRaw = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext)
        if (typeRaw.isNotBlank()) {
            val itemType = runCatching { ItemType.valueOf(typeRaw) }.getOrNull() ?: return null
            if (allowedTypes != null && itemType !in allowedTypes) return null

            val itemData = getFieldValue(entry, FIELD_MONICA_ITEM_DATA, resolutionContext)
            if (itemData.isBlank()) return null

            val title = getFieldValue(entry, "Title", resolutionContext)
            val notes = getFieldValue(entry, "Notes", resolutionContext)
            val imagePaths = getFieldValue(entry, FIELD_MONICA_IMAGE_PATHS, resolutionContext)
            val isFavorite = getFieldValue(entry, FIELD_MONICA_IS_FAVORITE, resolutionContext).toBoolean()
            val sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull()
            val now = Date()
            val inRecycleBin = resolveRecycleBinFlag(
                groupPath = groupPath,
                isInRecycleBinByMeta = isInRecycleBinByMeta,
                hasRecycleBinMeta = hasRecycleBinMeta
            )

            return KeePassSecureItemData(
                item = SecureItem(
                    id = 0,
                    itemType = itemType,
                    title = title.ifBlank { "Untitled" },
                    notes = notes,
                    isFavorite = isFavorite,
                    createdAt = now,
                    updatedAt = now,
                    itemData = itemData,
                    imagePaths = imagePaths,
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = groupPath,
                    keepassEntryUuid = entry.uuid.toString(),
                    keepassGroupUuid = groupUuid?.toString(),
                    isDeleted = inRecycleBin,
                    deletedAt = if (inRecycleBin) now else null
                ),
                sourceMonicaId = sourceMonicaId,
                isInRecycleBin = inRecycleBin
            )
        }

        val allowTotp = allowedTypes == null || allowedTypes.contains(ItemType.TOTP)
        if (!allowTotp) return null

        val parsedTotp = parseStandardTotpFromEntry(entry, resolutionContext) ?: return null
        val title = getFieldValue(entry, "Title", resolutionContext)
        val notes = getFieldValue(entry, "Notes", resolutionContext)
        val now = Date()
        val inRecycleBin = resolveRecycleBinFlag(
            groupPath = groupPath,
            isInRecycleBinByMeta = isInRecycleBinByMeta,
            hasRecycleBinMeta = hasRecycleBinMeta
        )
        val fallbackTitle = parsedTotp.issuer.ifBlank { parsedTotp.accountName }.ifBlank { "Untitled" }

        return KeePassSecureItemData(
            item = SecureItem(
                id = 0,
                itemType = ItemType.TOTP,
                title = title.ifBlank { fallbackTitle },
                notes = notes,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                itemData = Json.encodeToString(TotpData.serializer(), parsedTotp),
                imagePaths = "",
                keepassDatabaseId = databaseId,
                keepassGroupPath = groupPath,
                keepassEntryUuid = entry.uuid.toString(),
                keepassGroupUuid = groupUuid?.toString(),
                isDeleted = inRecycleBin,
                deletedAt = if (inRecycleBin) now else null
            ),
            sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull(),
            isInRecycleBin = inRecycleBin
        )
    }

    private fun entryToPasskey(
        entry: Entry,
        databaseId: Long,
        groupPath: String?,
        groupUuid: UUID?,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): PasskeyEntry? {
        val title = getFieldValue(entry, "Title", resolutionContext)
        val notes = getFieldValue(entry, "Notes", resolutionContext)
        val rawCredentialId = getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext)
        val rawPayload = getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext)
        val decoded = if (rawCredentialId.isBlank() && rawPayload.isBlank()) {
            null
        } else {
            KeePassPasskeySyncCodec.decode(
                raw = rawPayload,
                databaseId = databaseId,
                groupPath = groupPath,
                groupUuid = groupUuid?.toString()
            )
        }

        val passkey = decoded?.copy(
            credentialId = rawCredentialId.ifBlank { decoded.credentialId },
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenCipherId = null,
            syncStatus = "NONE",
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        ) ?: KeePassDxPasskeyCodec.decode(
            getField = { key -> getFieldValue(entry, key, resolutionContext) },
            title = title,
            notes = notes,
            databaseId = databaseId,
            groupPath = groupPath,
            groupUuid = groupUuid?.toString()
        )

        return passkey?.copy(
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenCipherId = null,
            syncStatus = "NONE",
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        )
    }

    private fun isLikelyRecycleBinPath(groupPath: String?): Boolean {
        if (groupPath.isNullOrBlank()) return false
        val normalized = decodeKeePassPathForDisplay(groupPath)
            .lowercase(Locale.ROOT)
            .replace(" ", "")
        return normalized.contains("recyclebin") ||
            normalized.contains("trash") ||
            normalized.contains("回收站")
    }

    private fun resolveRecycleBinUuid(meta: Meta): UUID? {
        if (!meta.recycleBinEnabled) return null
        return meta.recycleBinUuid
    }

    private fun parseUuid(value: String?): UUID? {
        if (value.isNullOrBlank()) return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun resolveRecycleBinFlag(
        groupPath: String?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean
    ): Boolean {
        if (hasRecycleBinMeta) return isInRecycleBinByMeta
        return isLikelyRecycleBinPath(groupPath)
    }

    /**
     * 标准 Password 字段为空时，尝试从常见自定义受保护字段中提取密码。
     */
    private fun resolveEntryPassword(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String {
        fun isLikelyLabelValue(value: String, key: String? = null): Boolean {
            val normalized = value.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank()) return true
            val labelTokens = setOf("password", "pass", "pwd", "pin", "密码", "口令")
            if (normalized in labelTokens) return true
            if (key != null && normalized == key.trim().lowercase(Locale.ROOT)) return true
            return false
        }

        val standardPassword = getFieldValue(entry, "Password", resolutionContext)
        if (standardPassword.isNotBlank() && !isLikelyLabelValue(standardPassword, "Password")) {
            return standardPassword
        }
        var fallback = standardPassword.takeIf { it.isNotBlank() }

        val prioritizedKeys = listOf(
            "密码", "口令", "PIN", "Pin", "pin", "pwd", "PWD", "pass", "Pass", "password", "Password"
        )
        prioritizedKeys.forEach { key ->
            val value = getFieldValue(entry, key, resolutionContext)
            if (value.isBlank()) return@forEach
            if (!isLikelyLabelValue(value, key)) return value
            if (fallback.isNullOrBlank()) fallback = value
        }

        val standardFields = setOf(
            "Title", "UserName", "Password", "URL", "Notes",
            "otp", "TOTP Seed", "TOTP Settings",
            FIELD_MONICA_LOCAL_ID, FIELD_MONICA_ITEM_ID,
            FIELD_MONICA_ITEM_TYPE, FIELD_MONICA_ITEM_DATA,
            FIELD_MONICA_IMAGE_PATHS, FIELD_MONICA_IS_FAVORITE,
            FIELD_MONICA_PASSKEY_CREDENTIAL_ID, FIELD_MONICA_PASSKEY_DATA,
            FIELD_MONICA_PASSKEY_MODE,
            KeePassDxPasskeyCodec.FIELD_PASSKEY,
            KeePassDxPasskeyCodec.FIELD_USERNAME,
            KeePassDxPasskeyCodec.FIELD_PRIVATE_KEY,
            KeePassDxPasskeyCodec.FIELD_CREDENTIAL_ID,
            KeePassDxPasskeyCodec.FIELD_USER_HANDLE,
            KeePassDxPasskeyCodec.FIELD_RELYING_PARTY,
            KeePassDxPasskeyCodec.FIELD_FLAG_BE,
            KeePassDxPasskeyCodec.FIELD_FLAG_BS
        )
        entry.fields.forEach { (key, value) ->
            if (key in standardFields || key.startsWith("_etm_")) return@forEach
            if (value is EntryValue.Encrypted) {
                val content = KeePassFieldReferenceResolver.resolveValue(
                    rawValue = runCatching { value.content }.getOrDefault(""),
                    currentEntry = entry,
                    context = resolutionContext
                )
                if (content.isBlank()) return@forEach
                if (!isLikelyLabelValue(content, key)) return content
                if (fallback.isNullOrBlank()) fallback = content
            }
        }

        return fallback ?: ""
    }

    private fun getFieldValue(
        entry: Entry,
        key: String,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String {
        return KeePassFieldReferenceResolver.getFieldValue(entry, key, resolutionContext)
    }

    private fun getFieldValueIgnoreCase(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null,
        vararg keys: String
    ): String {
        return KeePassFieldReferenceResolver.getFieldValueIgnoreCase(
            entry = entry,
            context = resolutionContext,
            *keys
        )
    }

    private fun isPasskeyEntry(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        if (getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext).isNotBlank()) {
            return true
        }
        if (getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext).isNotBlank()) {
            return true
        }
        return KeePassDxPasskeyCodec.isPasskey { key ->
            getFieldValue(entry, key, resolutionContext)
        }
    }

    private fun parseStandardTotpFromEntry(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): TotpData? {
        val otpField = getFieldValueIgnoreCase(entry, resolutionContext, "otp")
        val seedField = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Seed", "TOTPSeed")
        val settingsField = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Settings", "TOTPSettings")
        if (otpField.isBlank() && seedField.isBlank()) return null

        val title = getFieldValue(entry, "Title", resolutionContext)
        val username = getFieldValue(entry, "UserName", resolutionContext)
        val url = getFieldValue(entry, "URL", resolutionContext)

        parseOtpAuthUri(otpField, title, username, url)?.let { return it }

        val seed = normalizeTotpSecret(
            when {
                seedField.isNotBlank() -> seedField
                otpField.isNotBlank() && !otpField.contains("://") -> otpField
                else -> ""
            }
        )
        if (seed.isBlank()) return null

        val settings = parseTotpSettings(settingsField)
        return TotpData(
            secret = seed,
            issuer = title,
            accountName = username,
            period = settings.period,
            digits = settings.digits,
            algorithm = settings.algorithm,
            otpType = settings.otpType,
            counter = settings.counter,
            link = url
        )
    }

    private data class TotpSettingsParsed(
        val period: Int = 30,
        val digits: Int = 6,
        val algorithm: String = "SHA1",
        val otpType: OtpType = OtpType.TOTP,
        val counter: Long = 0L
    )

    private fun parseTotpSettings(settings: String): TotpSettingsParsed {
        if (settings.isBlank()) return TotpSettingsParsed()

        var period = 30
        var digits = 6
        var algorithm = "SHA1"
        var otpType = OtpType.TOTP
        var counter = 0L

        val tokens = settings.split(";", ",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        tokens.forEach { token ->
            if (token.contains("=")) {
                val parts = token.split("=", limit = 2)
                val key = parts[0].trim().lowercase(Locale.ROOT)
                val value = parts.getOrNull(1)?.trim().orEmpty()
                when (key) {
                    "period", "step", "time_step" -> value.toIntOrNull()?.let { period = it }
                    "digits", "length" -> value.toIntOrNull()?.let { digits = it }
                    "algorithm", "algo", "digest" -> if (value.isNotBlank()) algorithm = value.uppercase(Locale.ROOT)
                    "counter" -> value.toLongOrNull()?.let {
                        counter = it
                        otpType = OtpType.HOTP
                    }
                    "type" -> if (value.equals("hotp", ignoreCase = true)) otpType = OtpType.HOTP
                }
            } else {
                token.toIntOrNull()?.let { number ->
                    when {
                        period == 30 -> period = number
                        digits == 6 -> digits = number
                    }
                }
                if (token.startsWith("SHA", ignoreCase = true)) {
                    algorithm = token.uppercase(Locale.ROOT)
                }
            }
        }

        return TotpSettingsParsed(
            period = period,
            digits = digits,
            algorithm = algorithm,
            otpType = otpType,
            counter = counter
        )
    }

    private fun parseOtpAuthUri(
        uri: String,
        fallbackIssuer: String,
        fallbackAccount: String,
        fallbackLink: String
    ): TotpData? {
        if (!uri.startsWith("otpauth://", ignoreCase = true)) return null
        return runCatching {
            val parsed = URI(uri)
            val typeRaw = parsed.host?.lowercase(Locale.ROOT).orEmpty()
            val otpType = if (typeRaw == "hotp") OtpType.HOTP else OtpType.TOTP

            val decodedLabel = URLDecoder.decode(parsed.path.trimStart('/'), "UTF-8")
            val (labelIssuer, labelAccount) = if (decodedLabel.contains(":")) {
                val parts = decodedLabel.split(":", limit = 2)
                parts[0] to parts[1]
            } else {
                "" to decodedLabel
            }

            val params = mutableMapOf<String, String>()
            parsed.query?.split("&")?.forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2) {
                    params[kv[0].lowercase(Locale.ROOT)] = URLDecoder.decode(kv[1], "UTF-8")
                }
            }

            val secret = normalizeTotpSecret(params["secret"].orEmpty())
            if (secret.isBlank()) return null

            val issuer = params["issuer"].orEmpty().ifBlank { labelIssuer }.ifBlank { fallbackIssuer }
            val account = labelAccount.ifBlank { fallbackAccount }
            val algorithm = params["algorithm"]?.uppercase(Locale.ROOT) ?: "SHA1"
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            val counter = params["counter"]?.toLongOrNull() ?: 0L

            TotpData(
                secret = secret,
                issuer = issuer,
                accountName = account,
                period = period,
                digits = digits,
                algorithm = algorithm,
                otpType = otpType,
                counter = counter,
                link = fallbackLink
            )
        }.getOrNull()
    }

    private fun normalizeTotpSecret(value: String): String {
        return value
            .replace(Regex("[\\s\\-]"), "")
            .uppercase(Locale.ROOT)
    }

    private fun collectEntries(group: Group, entries: MutableList<Entry>) {
        entries.addAll(group.entries)
        group.groups.forEach { collectEntries(it, entries) }
    }

    private fun buildResolutionContext(keePassDatabase: KeePassDatabase): KeePassEntryResolutionContext {
        val entries = mutableListOf<Entry>()
        collectEntries(keePassDatabase.content.group, entries)
        return KeePassFieldReferenceResolver.buildContext(entries)
    }

    private fun buildGroupTraversalContextIndex(
        keePassDatabase: KeePassDatabase
    ): Map<UUID, GroupTraversalContext> {
        val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
        val result = mutableMapOf<UUID, GroupTraversalContext>()
        collectGroupTraversalContext(
            group = keePassDatabase.content.group,
            currentPathKey = null,
            recycleBinUuid = recycleBinUuid,
            parentInRecycleBin = false,
            result = result
        )
        return result
    }

    private fun collectGroupTraversalContext(
        group: Group,
        currentPathKey: String?,
        recycleBinUuid: UUID?,
        parentInRecycleBin: Boolean,
        result: MutableMap<UUID, GroupTraversalContext>
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        result[group.uuid] = GroupTraversalContext(
            pathKey = currentPathKey,
            groupUuid = group.uuid,
            isInRecycleBinByMeta = inRecycleBin
        )
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            collectGroupTraversalContext(
                group = child,
                currentPathKey = childPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycleBin,
                result = result
            )
        }
    }

    private fun resolveRestoreTargetFromEntryContext(
        entryContext: EntryTraversalContext,
        hasRecycleBinMeta: Boolean,
        groupContextIndex: Map<UUID, GroupTraversalContext>
    ): KeePassRestoreTarget {
        val inRecycleBin = if (hasRecycleBinMeta) {
            entryContext.isInRecycleBinByMeta
        } else {
            isLikelyRecycleBinPath(entryContext.groupPath)
        }
        if (!inRecycleBin) {
            return KeePassRestoreTarget(
                groupPath = entryContext.groupPath,
                groupUuid = entryContext.groupUuid?.toString()
            )
        }

        val previousParentUuid = entryContext.entry.previousParentGroup
        if (previousParentUuid != null) {
            val previousParentContext = groupContextIndex[previousParentUuid]
            if (previousParentContext != null) {
                val previousInRecycleBin = if (hasRecycleBinMeta) {
                    previousParentContext.isInRecycleBinByMeta
                } else {
                    isLikelyRecycleBinPath(previousParentContext.pathKey)
                }
                if (!previousInRecycleBin) {
                    return KeePassRestoreTarget(
                        groupPath = previousParentContext.pathKey,
                        groupUuid = previousParentContext.groupUuid.toString()
                    )
                }
            }
        }

        return KeePassRestoreTarget(groupPath = null, groupUuid = null)
    }

    private fun isGroupPathInRecycleBinByMeta(
        group: Group,
        currentPathKey: String?,
        targetPathKey: String,
        recycleBinUuid: UUID,
        parentInRecycleBin: Boolean
    ): Boolean? {
        val inRecycle = parentInRecycleBin || group.uuid == recycleBinUuid
        if (currentPathKey == targetPathKey) {
            return inRecycle
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = isGroupPathInRecycleBinByMeta(
                group = child,
                currentPathKey = childPathKey,
                targetPathKey = targetPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycle
            )
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun collectEntryContexts(
        keePassDatabase: KeePassDatabase
    ): Pair<List<EntryTraversalContext>, Boolean> {
        val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
        val hasRecycleBinMeta = recycleBinUuid != null
        val entries = mutableListOf<EntryTraversalContext>()
        collectEntriesWithGroupPath(
            group = keePassDatabase.content.group,
            currentPathKey = null,
            recycleBinUuid = recycleBinUuid,
            parentInRecycleBin = false,
            entries = entries
        )
        return entries to hasRecycleBinMeta
    }

    private fun collectEntriesWithGroupPath(
        group: Group,
        currentPathKey: String?,
        recycleBinUuid: UUID?,
        parentInRecycleBin: Boolean,
        entries: MutableList<EntryTraversalContext>
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        group.entries.forEach { entry ->
            entries.add(
                EntryTraversalContext(
                    entry = entry,
                    groupPath = currentPathKey,
                    groupUuid = group.uuid,
                    isInRecycleBinByMeta = inRecycleBin
                )
            )
        }
        group.groups.forEach { child ->
            val nextPathKey = buildKeePassPathKey(currentPathKey, child.name)
            collectEntriesWithGroupPath(
                group = child,
                currentPathKey = nextPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycleBin,
                entries = entries
            )
        }
    }

    private fun collectGroups(
        group: Group,
        parentPathKey: String,
        depth: Int,
        result: MutableList<KeePassGroupInfo>,
        recycleBinUuid: UUID?,
        includeRecycleBin: Boolean,
        parentInRecycleBin: Boolean
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        if (!includeRecycleBin && inRecycleBin) {
            return
        }
        val name = group.name.ifBlank { "(未命名)" }
        val currentPathKey = buildKeePassPathKey(parentPathKey, name)
        val currentDisplayPath = decodeKeePassPathForDisplay(currentPathKey)
        result.add(
            KeePassGroupInfo(
                name = name,
                path = currentPathKey,
                uuid = group.uuid.toString(),
                depth = depth,
                displayPath = currentDisplayPath
            )
        )
        group.groups.forEach { child ->
            collectGroups(
                group = child,
                parentPathKey = currentPathKey,
                depth = depth + 1,
                result = result,
                recycleBinUuid = recycleBinUuid,
                includeRecycleBin = includeRecycleBin,
                parentInRecycleBin = inRecycleBin
            )
        }
    }

    private fun findGroupPathByUuid(
        group: Group,
        currentPathKey: String?,
        targetUuid: UUID
    ): String? {
        if (group.uuid == targetUuid) {
            return currentPathKey
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = findGroupPathByUuid(
                group = child,
                currentPathKey = childPathKey,
                targetUuid = targetUuid
            )
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun findGroupUuidByPath(
        group: Group,
        currentPathKey: String?,
        targetPathKey: String
    ): UUID? {
        if (currentPathKey == targetPathKey) {
            return group.uuid
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = findGroupUuidByPath(
                group = child,
                currentPathKey = childPathKey,
                targetPathKey = targetPathKey
            )
            if (childResult != null) return childResult
        }
        return null
    }

    private fun addGroupToPath(
        group: Group,
        parentSegments: List<String>,
        newGroupName: String,
        currentPathKey: String
    ): Pair<Group, KeePassGroupInfo> {
        if (parentSegments.isEmpty()) {
            val existing = group.groups.firstOrNull { it.name.equals(newGroupName, ignoreCase = true) }
            if (existing != null) {
                val existingPath = buildKeePassPathKey(currentPathKey, existing.name)
                return group to KeePassGroupInfo(
                    name = existing.name,
                    path = existingPath,
                    uuid = existing.uuid.toString(),
                    depth = decodeKeePassPathSegments(existingPath).size - 1,
                    displayPath = decodeKeePassPathForDisplay(existingPath)
                )
            }

            val newGroup = Group(
                uuid = UUID.randomUUID(),
                name = newGroupName
            )
            val newPath = buildKeePassPathKey(currentPathKey, newGroupName)
            return group.copy(groups = group.groups + newGroup) to KeePassGroupInfo(
                name = newGroupName,
                path = newPath,
                uuid = newGroup.uuid.toString(),
                depth = decodeKeePassPathSegments(newPath).size - 1,
                displayPath = decodeKeePassPathForDisplay(newPath)
            )
        }

        val nextSegment = parentSegments.first()
        val childIndex = group.groups.indexOfFirst { it.name == nextSegment }
        if (childIndex < 0) {
            throw IllegalArgumentException("父分组不存在: $nextSegment")
        }

        val child = group.groups[childIndex]
        val childPath = buildKeePassPathKey(currentPathKey, child.name)
        val childResult = addGroupToPath(
            group = child,
            parentSegments = parentSegments.drop(1),
            newGroupName = newGroupName,
            currentPathKey = childPath
        )

        val updatedGroups = group.groups.toMutableList()
        updatedGroups[childIndex] = childResult.first
        return group.copy(groups = updatedGroups) to childResult.second
    }

    private fun renameGroupByPath(
        group: Group,
        pathSegments: List<String>,
        newName: String,
        currentPathKey: String
    ): Pair<Group, KeePassGroupInfo> {
        val targetName = pathSegments.firstOrNull()
            ?: throw IllegalArgumentException("分组路径无效")
        val childIndex = group.groups.indexOfFirst { it.name == targetName }
        if (childIndex < 0) {
            throw IllegalArgumentException("分组不存在: $targetName")
        }

        val child = group.groups[childIndex]
        val updatedGroups = group.groups.toMutableList()

        return if (pathSegments.size == 1) {
            val conflict = group.groups.anyIndexed { index, sibling ->
                index != childIndex && sibling.name.equals(newName, ignoreCase = true)
            }
            if (conflict) {
                throw IllegalArgumentException("同级已存在同名分组")
            }

            val renamed = child.copy(name = newName)
            updatedGroups[childIndex] = renamed
            val newPath = buildKeePassPathKey(currentPathKey, newName)
            group.copy(groups = updatedGroups) to KeePassGroupInfo(
                name = newName,
                path = newPath,
                uuid = renamed.uuid.toString(),
                depth = decodeKeePassPathSegments(newPath).size - 1,
                displayPath = decodeKeePassPathForDisplay(newPath)
            )
        } else {
            val childPath = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = renameGroupByPath(
                group = child,
                pathSegments = pathSegments.drop(1),
                newName = newName,
                currentPathKey = childPath
            )
            updatedGroups[childIndex] = childResult.first
            group.copy(groups = updatedGroups) to childResult.second
        }
    }

    private fun removeGroupByPath(
        group: Group,
        pathSegments: List<String>
    ): Pair<Group, Boolean> {
        val targetName = pathSegments.firstOrNull() ?: return group to false
        val childIndex = group.groups.indexOfFirst { it.name == targetName }
        if (childIndex < 0) return group to false

        val updatedGroups = group.groups.toMutableList()
        return if (pathSegments.size == 1) {
            updatedGroups.removeAt(childIndex)
            group.copy(groups = updatedGroups) to true
        } else {
            val child = group.groups[childIndex]
            val childResult = removeGroupByPath(child, pathSegments.drop(1))
            if (!childResult.second) return group to false
            updatedGroups[childIndex] = childResult.first
            group.copy(groups = updatedGroups) to true
        }
    }

    private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
        for (index in indices) {
            if (predicate(index, this[index])) return true
        }
        return false
    }

    private suspend fun <T> mutateDatabase(
        databaseId: Long,
        forceSyncWrite: Boolean = false,
        mutation: (LoadedDatabase) -> MutationPlan<T>
    ): T {
        return globalMutationMutex.withLock {
            try {
                if (forceSyncWrite) {
                    invalidateLoadedDatabaseCache(databaseId)
                }
                val loaded = getCachedLoadedDatabase(databaseId) ?: loadDatabase(databaseId)
                val plan = mutation(loaded)
                writeDatabase(
                    database = loaded.database,
                    credentials = loaded.credentials,
                    keePassDatabase = plan.updatedDatabase,
                    sourceEtag = loaded.sourceEtag,
                    sourceLastModified = loaded.sourceLastModified
                )
                plan.afterWrite?.invoke(loaded.database, plan.updatedDatabase)
                return@withLock plan.result
            } catch (e: Exception) {
                invalidateLoadedDatabaseCache(databaseId)
                throw e
            }
        }
    }

    private suspend fun loadDatabase(databaseId: Long): LoadedDatabase {
        getCachedLoadedDatabase(databaseId)?.let { return it }
        val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
        val credentials = buildCredentials(database)
        val snapshot = readDatabaseSnapshot(database)
        val (keePassDatabase, resolvedCredentials) = decodeDatabaseWithFallback(
            bytes = snapshot.bytes,
            credentialsResolution = credentials,
            sourceLabel = "databaseId=$databaseId",
            sourceName = database.filePath
        )
        val loaded = LoadedDatabase(
            database = database,
            credentials = resolvedCredentials,
            keePassDatabase = keePassDatabase,
            sourceEtag = snapshot.etag,
            sourceLastModified = snapshot.lastModified,
            sourceSignature = snapshot.signature
        )
        cacheLoadedDatabase(loaded)
        return loaded
    }

    private suspend fun decodeDatabase(
        bytes: ByteArray,
        credentials: Credentials,
        sourceName: String? = null,
        logFailure: Boolean = true
    ): KeePassDatabase {
        return withContext(decodeDispatcher) {
            withGlobalDecodeLock {
                try {
                    KeePassFormatInspector.ensureKdbxSupported(bytes = bytes, sourceName = sourceName)
                    KeePassDatabase.decode(
                        ByteArrayInputStream(bytes),
                        credentials,
                        cipherProviders = KeePassCodecSupport.cipherProviders
                    )
                } catch (t: Throwable) {
                    val mapped = normalizeError(t)
                    if (logFailure) {
                        Log.e(
                            TAG,
                            "KDBX decode failed code=${(mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED}. ${databaseHeaderSummary(bytes)}",
                            mapped
                        )
                    }
                    throw mapped
                }
            }
        }
    }

    private suspend fun decodeDatabaseWithFallback(
        bytes: ByteArray,
        credentialsResolution: CredentialsResolution,
        sourceLabel: String,
        sourceName: String? = null
    ): Pair<KeePassDatabase, Credentials> {
        val candidates = credentialsResolution.candidates
        if (candidates.isEmpty()) {
            throw IllegalStateException("无可用凭据")
        }

        var lastError: Throwable? = null
        val attemptedLabels = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val isLast = index == candidates.lastIndex
            attemptedLabels += candidate.label
            try {
                val database = decodeDatabase(
                    bytes = bytes,
                    credentials = candidate.credentials,
                    sourceName = sourceName,
                    logFailure = isLast
                )
                if (index > 0) {
                    Log.w(
                        TAG,
                        "KDBX decoded using credential fallback ($sourceLabel, candidate=${candidate.label})"
                    )
                }
                return database to candidate.credentials
            } catch (error: Throwable) {
                val mapped = normalizeError(error)
                lastError = mapped
                val isInvalidCredential =
                    mapped is KeePassOperationException &&
                        mapped.code == KeePassErrorCode.INVALID_CREDENTIAL
                if (!isInvalidCredential || isLast) {
                    throw mapped
                }
            }
        }

        val allInvalidCredential = lastError is KeePassOperationException &&
            (lastError as KeePassOperationException).code == KeePassErrorCode.INVALID_CREDENTIAL
        if (allInvalidCredential) {
            throw KeePassOperationException(
                code = KeePassErrorCode.INVALID_CREDENTIAL,
                message = KeePassCredentialSupport.buildInvalidCredentialMessage(attemptedLabels),
                cause = lastError
            )
        }

        throw (lastError ?: KeePassOperationException(
            code = KeePassErrorCode.IO_READ_WRITE_FAILED,
            message = "KDBX 解码失败"
        ))
    }

    private fun databaseHeaderSummary(bytes: ByteArray): String {
        val headerLength = bytes.size.coerceAtMost(16)
        val headerHex = buildString {
            for (index in 0 until headerLength) {
                if (index > 0) append(' ')
                append(String.format(Locale.US, "%02X", bytes[index].toInt() and 0xFF))
            }
        }
        return "bytes=${bytes.size}, header[$headerLength]=$headerHex"
    }

    private fun buildCredentials(
        database: LocalKeePassDatabase,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): CredentialsResolution {
        val encryptedDbPassword = database.encryptedPassword
        val kdbxPassword = passwordOverride ?: (encryptedDbPassword?.let { securityManager.decryptData(it) } ?: "")
        val keyFileBytes = keyFileUriOverride?.let { uri ->
            readKeyFileBytes(uri)
        } ?: database.keyFileUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            readKeyFileBytes(Uri.parse(uriString))
        }
        return resolveCredentials(kdbxPassword, keyFileBytes)
    }

    private fun buildCredentialsFromRaw(password: String, keyFileUri: Uri? = null): CredentialsResolution {
        val keyFileBytes = keyFileUri?.let { uri ->
            readKeyFileBytes(uri)
        }
        return resolveCredentials(password, keyFileBytes)
    }

    private fun readKeyFileBytes(uri: Uri): ByteArray {
        return readBytesFromUri(uri, "无法读取密钥文件")
    }

    private fun readBytesFromUri(uri: Uri, missingMessage: String): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw FileNotFoundException(missingMessage)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun resolveCredentials(password: String, keyFileBytes: ByteArray?): CredentialsResolution {
        val candidates = KeePassCredentialSupport.buildCredentialCandidates(
            password = password,
            keyFileBytes = keyFileBytes
        )
        return CredentialsResolution(candidates = candidates)
    }

    private fun readDatabaseBytes(database: LocalKeePassDatabase): ByteArray {
        return readDatabaseSnapshot(database).bytes
    }

    private fun readDatabaseSnapshot(database: LocalKeePassDatabase): DatabaseSnapshot {
        return try {
            if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                val file = File(context.filesDir, database.filePath)
                if (!file.exists()) throw FileNotFoundException("数据库文件不存在")
                val signature = DatabaseSourceSignature(
                    sizeBytes = file.length(),
                    lastModifiedEpochMs = file.lastModified()
                )
                DatabaseSnapshot(
                    bytes = file.readBytes(),
                    etag = null,
                    lastModified = null,
                    signature = signature
                )
            } else {
                val uri = Uri.parse(database.filePath)
                val bytes = readBytesFromUri(uri, "无法打开数据库文件")
                DatabaseSnapshot(bytes = bytes, etag = null, lastModified = null, signature = null)
            }
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private suspend fun writeDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        keePassDatabase: KeePassDatabase,
        sourceEtag: String? = null,
        sourceLastModified: String? = null
    ) {
        val bytes = encodeDatabase(keePassDatabase)
        if (ENABLE_POST_WRITE_DECODE_VERIFICATION) {
            decodeDatabase(bytes, credentials)
        }
        if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
            writeInternal(database, bytes)
        } else {
            writeExternal(database, bytes)
        }
        val updatedSignature = currentSourceSignature(database)
        cacheLoadedDatabase(
            LoadedDatabase(
                database = database,
                credentials = credentials,
                keePassDatabase = keePassDatabase,
                sourceEtag = sourceEtag,
                sourceLastModified = sourceLastModified,
                sourceSignature = updatedSignature
            )
        )
    }

    private suspend fun getCachedLoadedDatabase(databaseId: Long): LoadedDatabase? {
        if (consumeProcessCacheInvalidation(databaseId)) {
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        val now = System.currentTimeMillis()
        val cached = synchronized(loadedDatabaseCache) { loadedDatabaseCache[databaseId] } ?: return null

        val latestDatabase = runCatching { dao.getDatabaseById(databaseId) }.getOrNull() ?: return null
        val previous = cached.loaded.database
        val configChanged =
            latestDatabase.filePath != previous.filePath ||
                latestDatabase.storageLocation != previous.storageLocation ||
                latestDatabase.keyFileUri != previous.keyFileUri ||
                latestDatabase.encryptedPassword != previous.encryptedPassword
        if (configChanged) {
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        // Internal storage: keep cache warm as long as underlying file signature is unchanged.
        val cachedSignature = cached.loaded.sourceSignature
        if (cachedSignature != null) {
            val currentSignature = currentSourceSignature(latestDatabase)
            if (currentSignature == null || currentSignature != cachedSignature) {
                invalidateLoadedDatabaseCache(databaseId)
                return null
            }
        } else if (now - cached.cachedAtMs > UNKNOWN_SOURCE_CACHE_TTL_MS) {
            // External URI source does not have cheap signature checks, so keep a bounded cache window.
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        return cached.loaded.copy(database = latestDatabase)
    }

    private fun currentSourceSignature(database: LocalKeePassDatabase): DatabaseSourceSignature? {
        if (database.storageLocation != KeePassStorageLocation.INTERNAL) return null
        val file = File(context.filesDir, database.filePath)
        if (!file.exists()) return null
        return DatabaseSourceSignature(
            sizeBytes = file.length(),
            lastModifiedEpochMs = file.lastModified()
        )
    }

    private fun cacheLoadedDatabase(loaded: LoadedDatabase) {
        synchronized(loadedDatabaseCache) {
            loadedDatabaseCache[loaded.database.id] = CachedLoadedDatabase(
                loaded = loaded,
                cachedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun invalidateLoadedDatabaseCache(databaseId: Long) {
        synchronized(loadedDatabaseCache) {
            loadedDatabaseCache.remove(databaseId)
        }
    }

    private fun encodeDatabase(keePassDatabase: KeePassDatabase): ByteArray {
        return ByteArrayOutputStream().use { output ->
            keePassDatabase.encode(output, cipherProviders = KeePassCodecSupport.cipherProviders)
            output.toByteArray()
        }
    }

    private fun writeInternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        try {
            val file = File(context.filesDir, database.filePath)
            val parent = file.parentFile ?: throw IOException("无效的文件路径")
            if (!parent.exists()) parent.mkdirs()
            val tempFile = File(parent, "${file.name}.tmp")
            val backupFile = File(parent, "${file.name}.bak")
            FileOutputStream(tempFile).use {
                it.write(bytes)
                it.flush()
                it.fd.sync()
            }
            if (file.exists()) {
                if (backupFile.exists()) backupFile.delete()
                if (!file.renameTo(backupFile)) {
                    backupFile.delete()
                }
            }
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                FileOutputStream(file).use {
                    it.write(bytes)
                    it.flush()
                    it.fd.sync()
                }
                tempFile.delete()
            }
            if (backupFile.exists()) backupFile.delete()
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun writeExternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        val uri = Uri.parse(database.filePath)
        val originalBytes = runCatching { readDatabaseBytes(database) }.getOrNull()
        try {
            writeExternalBytes(uri, bytes)
        } catch (e: Exception) {
            if (originalBytes != null) {
                runCatching {
                    writeExternalBytes(uri, originalBytes)
                }
            }
            throw normalizeError(e)
        }
    }

    private fun writeExternalBytes(uri: Uri, bytes: ByteArray) {
        openExternalFileDescriptor(uri)?.use { descriptor ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        } ?: throw IOException("无法写入数据库文件")
    }

    private fun openExternalFileDescriptor(uri: Uri): ParcelFileDescriptor? =
        try {
            context.contentResolver.openFileDescriptor(uri, "rwt")
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "openFileDescriptor rwt failed, retry with wt: $uri", e)
            context.contentResolver.openFileDescriptor(uri, "wt")
        }

    private fun openExternalOutputStream(uri: Uri) =
        try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "openOutputStream wt failed, retry with rwt: $uri", e)
            context.contentResolver.openOutputStream(uri, "rwt")
        }

    private fun normalizeError(throwable: Throwable): Throwable {
        if (throwable is KeePassOperationException || throwable is IllegalArgumentException) {
            return throwable
        }
        return throwable.toKeePassOperationException()
    }
}
