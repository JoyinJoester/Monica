package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials as HttpCredentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.security.SecurityManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Date
import java.util.Locale
import java.util.UUID

data class KeePassEntryData(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val monicaLocalId: Long?,
    val groupPath: String?
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
    val sourceMonicaId: Long?
)

class KeePassKdbxService(
    private val context: Context,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "KeePassKdbxService"
        const val WEBDAV_PATH_PREFIX = "webdav://"
        // Keep unknown-source cache short, but keep known internal files effectively "always warm".
        private const val UNKNOWN_SOURCE_CACHE_TTL_MS = 60_000L
        // DX-like strategy: apply mutation in memory first, persist to file asynchronously.
        private const val ENABLE_ASYNC_MUTATION_COMMIT = true
        private const val ASYNC_PERSIST_MAX_RETRY = 2
        private const val ASYNC_PERSIST_RETRY_DELAY_MS = 250L
        // Disable post-write full decode verification for normal writes to reduce save latency.
        // The database is still encoded by the library and written atomically/with rollback paths.
        private const val ENABLE_POST_WRITE_DECODE_VERIFICATION = false
        private const val KEEPASS_WEBDAV_PREFS_NAME = "keepass_webdav_config"
        private const val KEY_KEEPASS_USERNAME = "username"
        private const val KEY_KEEPASS_PASSWORD = "password"
        private const val KEY_CONFLICT_PROTECTION_ENABLED = "conflict_protection_enabled"
        private const val KEY_CONFLICT_PROTECTION_MODE = "conflict_protection_mode"
        private const val CONFLICT_MODE_AUTO = "auto"
        private const val CONFLICT_MODE_STRICT = "strict"
        private const val FIELD_MONICA_LOCAL_ID = "MonicaLocalId"
        private const val FIELD_MONICA_ITEM_ID = "MonicaSecureItemId"
        private const val FIELD_MONICA_ITEM_TYPE = "MonicaItemType"
        private const val FIELD_MONICA_ITEM_DATA = "MonicaItemData"
        private const val FIELD_MONICA_IMAGE_PATHS = "MonicaImagePaths"
        private const val FIELD_MONICA_IS_FAVORITE = "MonicaIsFavorite"
        // kotpass decode 在并发下可能触发 native 崩溃，必须跨实例串行化。
        private val globalDecodeMutex = Mutex()
        // WebDAV 写入需要“读-改-写”原子化，避免同进程并发导致 ETag 冲突。
        private val globalMutationMutex = Mutex()
        // Global single-writer queue, shared across all service instances.
        private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val persistQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        @Volatile
        private var persistWorkerStarted = false

        @Synchronized
        private fun ensurePersistWorkerStarted() {
            if (persistWorkerStarted) return
            persistWorkerStarted = true
            persistScope.launch {
                for (task in persistQueue) {
                    runCatching { task() }
                        .onFailure { Log.e(TAG, "Async persist task failed", it) }
                }
            }
        }

        fun toWebDavFilePath(remotePath: String): String {
            return WEBDAV_PATH_PREFIX + remotePath
        }

        suspend fun <T> withGlobalDecodeLock(block: () -> T): T {
            return globalDecodeMutex.withLock { block() }
        }
    }
    
    init {
        ensurePersistWorkerStarted()
    }

    private fun ensureWebDavKeepassEnabled(): Nothing {
        throw UnsupportedOperationException("KeePass WebDAV has been removed")
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
        val forceOverwriteWebDav: Boolean = false,
        val afterWrite: (suspend (LocalKeePassDatabase, KeePassDatabase) -> Unit)? = null
    )

    private data class CredentialsResolution(
        val primary: Credentials,
        val legacyEmptyPasswordWithKeyFallback: Credentials? = null
    )

    private class WebDavHttpException(
        val statusCode: Int,
        message: String
    ) : Exception(message)

    private enum class ConflictProtectionMode {
        AUTO,
        STRICT
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }
    private val loadedDatabaseCache = mutableMapOf<Long, CachedLoadedDatabase>()

    suspend fun verifyDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
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
                sourceLabel = "databaseId=$databaseId"
            )
            val entries = mutableListOf<Pair<Entry, String?>>()
            collectEntriesWithGroupPath(keePassDatabase.content.group, null, entries)
            val count = entries.count { (entry, groupPath) -> entryToData(entry, groupPath) != null }
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "verifyDatabase failed (databaseId=$databaseId)", e)
            Result.failure(e)
        }
    }

    suspend fun verifyExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val credentials = buildCredentialsFromRaw(password = password, keyFileUri = keyFileUri)
            val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: throw Exception("无法打开数据库文件")
            val (keePassDatabase, _) = decodeDatabaseWithFallback(
                bytes = bytes,
                credentialsResolution = credentials,
                sourceLabel = "uri=$fileUri"
            )
            val entries = mutableListOf<Pair<Entry, String?>>()
            collectEntriesWithGroupPath(keePassDatabase.content.group, null, entries)
            val count = entries.count { (entry, groupPath) -> entryToData(entry, groupPath) != null }
            Result.success(count)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "verifyExternalDatabase failed (uri=$fileUri, keyFile=${keyFileUri != null})",
                e
            )
            Result.failure(e)
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
            Result.failure(e)
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
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun readPasswordEntries(databaseId: Long): Result<List<KeePassEntryData>> = withContext(Dispatchers.IO) {
        try {
            val (database, _, keePassDatabase) = loadDatabase(databaseId)
            val entries = mutableListOf<Pair<Entry, String?>>()
            collectEntriesWithGroupPath(keePassDatabase.content.group, null, entries)
            val data = entries.mapNotNull { (entry, groupPath) -> entryToData(entry, groupPath) }
            dao.updateEntryCount(database.id, data.size)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listGroups(databaseId: Long): Result<List<KeePassGroupInfo>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groups = mutableListOf<KeePassGroupInfo>()
            keePassDatabase.content.group.groups.forEach { group ->
                collectGroups(group, "", 0, groups)
            }
            Result.success(groups.sortedBy { it.displayPath })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdatePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(databaseId) { loaded ->
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
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun addPasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
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
            Result.failure(e)
        }
    }

    suspend fun deletePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(databaseId) { loaded ->
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
            Result.failure(e)
        }
    }

    suspend fun readSecureItems(
        databaseId: Long,
        allowedTypes: Set<ItemType>? = null
    ): Result<List<KeePassSecureItemData>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val entries = mutableListOf<Pair<Entry, String?>>()
            collectEntriesWithGroupPath(keePassDatabase.content.group, null, entries)
            val data = entries.mapNotNull { (entry, groupPath) ->
                entryToSecureItemData(entry, databaseId, groupPath, allowedTypes)
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdateSecureItems(
        databaseId: Long,
        items: List<SecureItem>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(databaseId) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                items.forEach { item ->
                    val updateResult = updateSecureItemInternal(updatedDatabase, item)
                    if (updateResult.second) {
                        updatedDatabase = updateResult.first
                    } else {
                        val newEntry = buildSecureItemEntry(item)
                        updatedDatabase = updatedDatabase.modifyParentGroup {
                            copy(entries = this.entries + newEntry)
                        }
                        addedCount++
                    }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = addedCount)
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(e)
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
                    loaded.keePassDatabase.modifyParentGroup {
                        copy(entries = this.entries + newEntry)
                    }
                }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSecureItems(
        databaseId: Long,
        items: List<SecureItem>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(databaseId) { loaded ->
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
            Result.failure(e)
        }
    }

    suspend fun forceOverwriteWebDavDatabaseFromLocal(
        databaseId: Long,
        passwordEntries: List<PasswordEntry>,
        secureItems: List<SecureItem>,
        resolvePassword: (PasswordEntry) -> String
    ): Result<Int> = withContext(Dispatchers.IO) {
        globalMutationMutex.withLock {
            try {
                val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
                if (!database.filePath.startsWith(WEBDAV_PATH_PREFIX)) {
                    throw Exception("仅支持 WebDAV 数据库")
                }

                val credentials = buildCredentials(database).primary
                val rootName = database.name.ifBlank { "Monica" }
                var rebuilt: KeePassDatabase = KeePassDatabase.Ver4x.create(
                    rootName = rootName,
                    meta = Meta(generator = "Monica Password Manager", name = rootName),
                    credentials = credentials
                )
                var rootGroup = rebuilt.content.group

                passwordEntries.forEach { entry ->
                    val plainPassword = resolvePassword(entry)
                    val newEntry = buildEntry(entry, plainPassword)
                    rootGroup = addEntryToGroupPath(rootGroup, entry.keepassGroupPath, newEntry)
                }

                secureItems.forEach { item ->
                    val newEntry = buildSecureItemEntry(item)
                    rootGroup = addEntryToGroupPath(rootGroup, item.keepassGroupPath, newEntry)
                }

                rebuilt = rebuilt.modifyParentGroup { rootGroup }
                writeDatabase(
                    database = database,
                    credentials = credentials,
                    keePassDatabase = rebuilt,
                    sourceEtag = null,
                    forceOverwriteWebDav = true
                )
                val total = passwordEntries.size + secureItems.size
                dao.updateEntryCount(database.id, total)
                Result.success(total)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildEntry(entry: PasswordEntry, plainPassword: String): Entry {
        return Entry(
            uuid = UUID.randomUUID(),
            fields = buildEntryFields(entry, plainPassword)
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

    private fun buildSecureItemEntry(item: SecureItem): Entry {
        return Entry(
            uuid = UUID.randomUUID(),
            fields = buildSecureItemFields(item)
        )
    }

    private fun buildSecureItemFields(item: SecureItem): EntryFields {
        val monicaId = if (item.id > 0) item.id.toString() else ""
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain(item.title),
            "UserName" to EntryValue.Plain(""),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
            "URL" to EntryValue.Plain(""),
            "Notes" to EntryValue.Plain(item.notes),
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
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, FIELD_MONICA_LOCAL_ID).toLongOrNull()
            if (monicaId != null && monicaId == entry.id) {
                true
            } else {
                matchByKey(existing, entry)
            }
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
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, FIELD_MONICA_ITEM_ID).toLongOrNull()
            if (monicaId != null && item.id > 0) {
                monicaId == item.id
            } else {
                matchSecureItemByKey(existing, item)
            }
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

    private fun removeEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry
    ): Pair<KeePassDatabase, Int> {
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, FIELD_MONICA_LOCAL_ID).toLongOrNull()
            if (monicaId != null && entry.id > 0) {
                monicaId == entry.id
            } else {
                matchByKey(existing, entry)
            }
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
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, FIELD_MONICA_ITEM_ID).toLongOrNull()
            if (monicaId != null && item.id > 0) {
                monicaId == item.id
            } else {
                matchSecureItemByKey(existing, item)
            }
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

    private fun matchByKey(entry: Entry, target: PasswordEntry): Boolean {
        val title = getFieldValue(entry, "Title")
        val username = getFieldValue(entry, "UserName")
        val url = getFieldValue(entry, "URL")
        return title.equals(target.title, true) &&
            username.equals(target.username, true) &&
            url.equals(target.website, true)
    }

    private fun matchSecureItemByKey(entry: Entry, target: SecureItem): Boolean {
        val title = getFieldValue(entry, "Title")
        val itemType = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE)
        return title.equals(target.title, true) &&
            itemType.equals(target.itemType.name, true)
    }

    private fun entryToData(entry: Entry, groupPath: String?): KeePassEntryData? {
        // Monica 安全项（TOTP/笔记/卡片等）会写入 MonicaItemType，不应进入密码列表。
        if (getFieldValue(entry, FIELD_MONICA_ITEM_TYPE).isNotBlank()) {
            return null
        }

        val title = getFieldValue(entry, "Title")
        val username = getFieldValue(entry, "UserName")
        val password = getFieldValue(entry, "Password")
        val url = getFieldValue(entry, "URL")
        val notes = getFieldValue(entry, "Notes")
        if (title.isEmpty() && username.isEmpty() && password.isEmpty() && url.isEmpty() && notes.isEmpty()) {
            return null
        }
        // 过滤备注型/空登录条目，避免空密码幽灵进入密码页。
        if (username.isBlank() && password.isBlank() && url.isBlank()) {
            return null
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_LOCAL_ID).toLongOrNull()
        return KeePassEntryData(
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            monicaLocalId = monicaId,
            groupPath = groupPath
        )
    }

    private fun entryToSecureItemData(
        entry: Entry,
        databaseId: Long,
        groupPath: String?,
        allowedTypes: Set<ItemType>?
    ): KeePassSecureItemData? {
        val typeRaw = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE)
        if (typeRaw.isNotBlank()) {
            val itemType = runCatching { ItemType.valueOf(typeRaw) }.getOrNull() ?: return null
            if (allowedTypes != null && itemType !in allowedTypes) return null

            val itemData = getFieldValue(entry, FIELD_MONICA_ITEM_DATA)
            if (itemData.isBlank()) return null

            val title = getFieldValue(entry, "Title")
            val notes = getFieldValue(entry, "Notes")
            val imagePaths = getFieldValue(entry, FIELD_MONICA_IMAGE_PATHS)
            val isFavorite = getFieldValue(entry, FIELD_MONICA_IS_FAVORITE).toBoolean()
            val sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID).toLongOrNull()
            val now = Date()

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
                    keepassGroupPath = groupPath
                ),
                sourceMonicaId = sourceMonicaId
            )
        }

        val allowTotp = allowedTypes == null || allowedTypes.contains(ItemType.TOTP)
        if (!allowTotp) return null

        val parsedTotp = parseStandardTotpFromEntry(entry) ?: return null
        val title = getFieldValue(entry, "Title")
        val notes = getFieldValue(entry, "Notes")
        val now = Date()
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
                keepassGroupPath = groupPath
            ),
            sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID).toLongOrNull()
        )
    }

    private fun getFieldValue(entry: Entry, key: String): String {
        val value = entry.fields[key]
        return if (value == null) "" else value.content
    }

    private fun getFieldValueIgnoreCase(entry: Entry, vararg keys: String): String {
        if (keys.isEmpty()) return ""
        val direct = keys.firstNotNullOfOrNull { key ->
            entry.fields[key]?.content?.takeIf { it.isNotBlank() }
        }
        if (direct != null) return direct
        return entry.fields.entries.firstOrNull { (fieldKey, _) ->
            keys.any { it.equals(fieldKey, ignoreCase = true) }
        }?.value?.content.orEmpty()
    }

    private fun parseStandardTotpFromEntry(entry: Entry): TotpData? {
        val otpField = getFieldValueIgnoreCase(entry, "otp")
        val seedField = getFieldValueIgnoreCase(entry, "TOTP Seed", "TOTPSeed")
        val settingsField = getFieldValueIgnoreCase(entry, "TOTP Settings", "TOTPSettings")
        if (otpField.isBlank() && seedField.isBlank()) return null

        val title = getFieldValue(entry, "Title")
        val username = getFieldValue(entry, "UserName")
        val url = getFieldValue(entry, "URL")

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

    private fun collectEntriesWithGroupPath(
        group: Group,
        currentPathKey: String?,
        entries: MutableList<Pair<Entry, String?>>
    ) {
        group.entries.forEach { entry ->
            entries.add(entry to currentPathKey)
        }
        group.groups.forEach { child ->
            val nextPathKey = buildKeePassPathKey(currentPathKey, child.name)
            collectEntriesWithGroupPath(child, nextPathKey, entries)
        }
    }

    private fun collectGroups(
        group: Group,
        parentPathKey: String,
        depth: Int,
        result: MutableList<KeePassGroupInfo>
    ) {
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
            collectGroups(child, currentPathKey, depth + 1, result)
        }
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
        retryOnConflict: Boolean = true,
        mutation: (LoadedDatabase) -> MutationPlan<T>
    ): T {
        return globalMutationMutex.withLock {
            if (ENABLE_ASYNC_MUTATION_COMMIT) {
                val loaded = getCachedLoadedDatabase(databaseId) ?: loadDatabase(databaseId)
                val plan = mutation(loaded)
                val latestLoaded = LoadedDatabase(
                    database = loaded.database,
                    credentials = loaded.credentials,
                    keePassDatabase = plan.updatedDatabase,
                    sourceEtag = loaded.sourceEtag,
                    sourceLastModified = loaded.sourceLastModified,
                    sourceSignature = loaded.sourceSignature
                )
                cacheLoadedDatabase(latestLoaded)
                plan.afterWrite?.invoke(loaded.database, plan.updatedDatabase)
                enqueueAsyncPersist(latestLoaded, plan.forceOverwriteWebDav)
                return@withLock plan.result
            } else {
                var lastConflict: Exception? = null
                val attempts = if (retryOnConflict) 2 else 1
                repeat(attempts) { attempt ->
                    try {
                        val loaded = getCachedLoadedDatabase(databaseId) ?: loadDatabase(databaseId)
                        val plan = mutation(loaded)
                        writeDatabase(
                            database = loaded.database,
                            credentials = loaded.credentials,
                            keePassDatabase = plan.updatedDatabase,
                            sourceEtag = loaded.sourceEtag,
                            sourceLastModified = loaded.sourceLastModified,
                            forceOverwriteWebDav = plan.forceOverwriteWebDav
                        )
                        plan.afterWrite?.invoke(loaded.database, plan.updatedDatabase)
                        return@withLock plan.result
                    } catch (e: Exception) {
                        val shouldRetry = retryOnConflict &&
                            attempt == 0 &&
                            isWebDavConflictError(e)
                        if (shouldRetry) {
                            lastConflict = e
                        } else {
                            invalidateLoadedDatabaseCache(databaseId)
                            throw e
                        }
                    }
                }
                throw (lastConflict ?: Exception("KeePass 写入失败"))
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
            sourceLabel = "databaseId=$databaseId"
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
        logFailure: Boolean = true
    ): KeePassDatabase {
        return withGlobalDecodeLock {
            try {
                KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials)
            } catch (t: Throwable) {
                if (logFailure) {
                    Log.e(
                        TAG,
                        "KDBX decode failed. ${databaseHeaderSummary(bytes)}",
                        t
                    )
                }
                throw t
            }
        }
    }

    private suspend fun decodeDatabaseWithFallback(
        bytes: ByteArray,
        credentialsResolution: CredentialsResolution,
        sourceLabel: String
    ): Pair<KeePassDatabase, Credentials> {
        val fallback = credentialsResolution.legacyEmptyPasswordWithKeyFallback
        if (fallback == null) {
            return decodeDatabase(bytes, credentialsResolution.primary) to credentialsResolution.primary
        }

        return try {
            decodeDatabase(
                bytes = bytes,
                credentials = credentialsResolution.primary,
                logFailure = false
            ) to credentialsResolution.primary
        } catch (primaryError: Throwable) {
            try {
                val database = decodeDatabase(bytes, fallback)
                Log.w(
                    TAG,
                    "KDBX decoded using legacy empty-password+keyfile fallback ($sourceLabel)"
                )
                database to fallback
            } catch (fallbackError: Throwable) {
                fallbackError.addSuppressed(primaryError)
                throw fallbackError
            }
        }
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
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("无法读取密钥文件")
    }

    private fun resolveCredentials(password: String, keyFileBytes: ByteArray?): CredentialsResolution {
        if (keyFileBytes == null) {
            return CredentialsResolution(
                primary = Credentials.from(EncryptedValue.fromString(password))
            )
        }

        return if (password.isBlank()) {
            CredentialsResolution(
                primary = Credentials.from(keyFileBytes),
                legacyEmptyPasswordWithKeyFallback = Credentials.from(
                    EncryptedValue.fromString(""),
                    keyFileBytes
                )
            )
        } else {
            CredentialsResolution(
                primary = Credentials.from(
                    EncryptedValue.fromString(password),
                    keyFileBytes
                )
            )
        }
    }

    private fun readDatabaseBytes(database: LocalKeePassDatabase): ByteArray {
        return readDatabaseSnapshot(database).bytes
    }

    private fun readDatabaseSnapshot(database: LocalKeePassDatabase): DatabaseSnapshot {
        if (database.filePath.startsWith(WEBDAV_PATH_PREFIX)) {
            ensureWebDavKeepassEnabled()
        }
        return if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
            val file = File(context.filesDir, database.filePath)
            if (!file.exists()) throw Exception("数据库文件不存在")
            val signature = DatabaseSourceSignature(
                sizeBytes = file.length(),
                lastModifiedEpochMs = file.lastModified()
            )
            DatabaseSnapshot(bytes = file.readBytes(), etag = null, lastModified = null, signature = signature)
        } else {
            val uri = Uri.parse(database.filePath)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.let { DatabaseSnapshot(bytes = it, etag = null, lastModified = null, signature = null) }
                ?: throw Exception("无法打开数据库文件")
        }
    }

    private suspend fun writeDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        keePassDatabase: KeePassDatabase,
        sourceEtag: String? = null,
        sourceLastModified: String? = null,
        forceOverwriteWebDav: Boolean = false
    ) {
        val bytes = encodeDatabase(keePassDatabase)
        if (ENABLE_POST_WRITE_DECODE_VERIFICATION) {
            decodeDatabase(bytes, credentials)
        }
        if (database.filePath.startsWith(WEBDAV_PATH_PREFIX)) {
            ensureWebDavKeepassEnabled()
        } else if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
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
        if (database.filePath.startsWith(WEBDAV_PATH_PREFIX)) return null
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

    private suspend fun enqueueAsyncPersist(
        loaded: LoadedDatabase,
        forceOverwriteWebDav: Boolean
    ) {
        val db = loaded.database
        val credentials = loaded.credentials
        val updated = loaded.keePassDatabase
        val etag = loaded.sourceEtag
        val lastModified = loaded.sourceLastModified
        persistQueue.send {
            var attempts = 0
            var lastError: Exception? = null
            while (attempts < ASYNC_PERSIST_MAX_RETRY) {
                attempts++
                try {
                    writeDatabase(
                        database = db,
                        credentials = credentials,
                        keePassDatabase = updated,
                        sourceEtag = etag,
                        sourceLastModified = lastModified,
                        forceOverwriteWebDav = forceOverwriteWebDav
                    )
                    return@send
                } catch (e: Exception) {
                    lastError = e
                    if (attempts < ASYNC_PERSIST_MAX_RETRY) {
                        delay(ASYNC_PERSIST_RETRY_DELAY_MS)
                    }
                }
            }
            Log.e(TAG, "Async persist failed for databaseId=${db.id}", lastError)
        }
    }

    private fun encodeDatabase(keePassDatabase: KeePassDatabase): ByteArray {
        return ByteArrayOutputStream().use { output ->
            keePassDatabase.encode(output)
            output.toByteArray()
        }
    }

    private fun writeInternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        val file = File(context.filesDir, database.filePath)
        val parent = file.parentFile ?: throw Exception("无效的文件路径")
        if (!parent.exists()) parent.mkdirs()
        val tempFile = File(parent, "${file.name}.tmp")
        val backupFile = File(parent, "${file.name}.bak")
        FileOutputStream(tempFile).use { it.write(bytes) }
        if (file.exists()) {
            if (backupFile.exists()) backupFile.delete()
            if (!file.renameTo(backupFile)) {
                backupFile.delete()
            }
        }
        val renamed = tempFile.renameTo(file)
        if (!renamed) {
            file.writeBytes(bytes)
            tempFile.delete()
        }
        if (backupFile.exists()) backupFile.delete()
    }

    private fun writeExternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        val uri = Uri.parse(database.filePath)
        val originalBytes = runCatching { readDatabaseBytes(database) }.getOrNull()
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw Exception("无法写入数据库文件")
        } catch (e: Exception) {
            if (originalBytes != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(originalBytes) }
                }
            }
            throw e
        }
    }

    private fun readWebDavSnapshot(remotePath: String): DatabaseSnapshot {
        val (username, password) = loadWebDavCredentials()
        val request = Request.Builder()
            .url(remotePath)
            .get()
            .header("Authorization", HttpCredentials.basic(username, password))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV 读取失败: HTTP ${response.code}")
            }
            val data = response.body?.bytes() ?: throw Exception("WebDAV 返回空内容")
            val etag = response.header("ETag")
            val lastModified = response.header("Last-Modified")
            return DatabaseSnapshot(bytes = data, etag = etag, lastModified = lastModified, signature = null)
        }
    }

    private fun writeWebDav(
        remotePath: String,
        bytes: ByteArray,
        expectedEtag: String?,
        expectedLastModified: String?,
        forceOverwrite: Boolean = false
    ) {
        val sardine = buildWebDavClient()
        val parentPath = remotePath.substringBeforeLast('/', "")
        if (parentPath.isNotBlank()) {
            runCatching {
                if (!sardine.exists(parentPath)) {
                    sardine.createDirectory(parentPath)
                }
            }
        }
        if (forceOverwrite) {
            sardine.put(remotePath, bytes, "application/octet-stream")
            return
        }

        val conflictMode = getConflictProtectionMode()
        if (!expectedEtag.isNullOrBlank()) {
            putWithConditionalHeaders(
                remotePath = remotePath,
                bytes = bytes,
                ifMatch = expectedEtag,
                ifUnmodifiedSince = null
            )
            return
        }

        if (conflictMode == ConflictProtectionMode.STRICT) {
            throw Exception("服务器未返回 ETag，严格模式下已阻止写入。可切换为自动模式后重试")
        }

        // AUTO 模式：ETag 缺失时尽力使用 Last-Modified 保护，失败则降级为直接写入。
        if (!expectedLastModified.isNullOrBlank()) {
            runCatching {
                putWithConditionalHeaders(
                    remotePath = remotePath,
                    bytes = bytes,
                    ifMatch = null,
                    ifUnmodifiedSince = expectedLastModified
                )
            }.onSuccess {
                return
            }.onFailure { error ->
                if (isWebDavConflictError(error)) throw error
                val httpStatus = (error as? WebDavHttpException)?.statusCode
                // 仅在服务器明确不支持条件头时才降级到直接写入。
                val serverDoesNotSupportHeader = httpStatus == 400 || httpStatus == 405 || httpStatus == 501
                if (!serverDoesNotSupportHeader) throw error
            }
        }

        sardine.put(remotePath, bytes, "application/octet-stream")
    }

    private fun putWithConditionalHeaders(
        remotePath: String,
        bytes: ByteArray,
        ifMatch: String?,
        ifUnmodifiedSince: String?
    ) {
        val (username, password) = loadWebDavCredentials()
        val request = Request.Builder()
            .url(remotePath)
            .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .header("Authorization", HttpCredentials.basic(username, password))
            .apply {
                if (!ifMatch.isNullOrBlank()) {
                    header("If-Match", ifMatch)
                }
                if (!ifUnmodifiedSince.isNullOrBlank()) {
                    header("If-Unmodified-Since", ifUnmodifiedSince)
                }
            }
            .build()
        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 412 || response.code == 428 -> {
                    throw Exception("远端数据库已变化，已阻止覆盖。请先刷新后重试")
                }
                !response.isSuccessful -> {
                    throw WebDavHttpException(
                        statusCode = response.code,
                        message = "WebDAV 写入失败: HTTP ${response.code}"
                    )
                }
                else -> Unit
            }
        }
    }

    private fun buildWebDavClient(): Sardine {
        val (username, password) = loadWebDavCredentials()
        return OkHttpSardine().apply {
            setCredentials(username, password)
        }
    }

    private fun loadWebDavCredentials(): Pair<String, String> {
        val prefs = context.getSharedPreferences(KEEPASS_WEBDAV_PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_KEEPASS_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_KEEPASS_PASSWORD, "") ?: ""
        if (username.isBlank() || password.isBlank()) {
            throw Exception("WebDAV 未配置或凭证已失效")
        }
        return username to password
    }

    private fun getConflictProtectionMode(): ConflictProtectionMode {
        val prefs = context.getSharedPreferences(KEEPASS_WEBDAV_PREFS_NAME, Context.MODE_PRIVATE)
        val rawMode = prefs.getString(KEY_CONFLICT_PROTECTION_MODE, null)?.lowercase(Locale.ROOT)
        if (!rawMode.isNullOrBlank()) {
            return if (rawMode == CONFLICT_MODE_STRICT) {
                ConflictProtectionMode.STRICT
            } else {
                ConflictProtectionMode.AUTO
            }
        }
        // Legacy boolean preference is ignored as default source.
        // Without an explicit mode selection, AUTO provides better
        // compatibility across WebDAV servers (closer to KeePassDX behavior).
        return ConflictProtectionMode.AUTO
    }

    private fun isWebDavConflictError(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("412") ||
            message.contains("428") ||
            message.contains("If-Match", ignoreCase = true) ||
            message.contains("If-Unmodified-Since", ignoreCase = true) ||
            message.contains("远端数据库已变化") ||
            message.contains("已阻止覆盖")
    }
}

