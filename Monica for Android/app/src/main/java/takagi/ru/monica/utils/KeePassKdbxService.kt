package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
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
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.security.SecurityManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
    val uuid: String?
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
        const val WEBDAV_PATH_PREFIX = "webdav://"
        private const val KEEPASS_WEBDAV_PREFS_NAME = "keepass_webdav_config"
        private const val KEY_KEEPASS_USERNAME = "username"
        private const val KEY_KEEPASS_PASSWORD = "password"
        private const val FIELD_MONICA_LOCAL_ID = "MonicaLocalId"
        private const val FIELD_MONICA_ITEM_ID = "MonicaSecureItemId"
        private const val FIELD_MONICA_ITEM_TYPE = "MonicaItemType"
        private const val FIELD_MONICA_ITEM_DATA = "MonicaItemData"
        private const val FIELD_MONICA_IMAGE_PATHS = "MonicaImagePaths"
        private const val FIELD_MONICA_IS_FAVORITE = "MonicaIsFavorite"
        // kotpass decode 在并发下可能触发 native 崩溃，必须跨实例串行化。
        private val globalDecodeMutex = Mutex()

        fun toWebDavFilePath(remotePath: String): String {
            return WEBDAV_PATH_PREFIX + remotePath
        }
    }

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
            val keePassDatabase = decodeDatabase(bytes, credentials)
            val entries = mutableListOf<Entry>()
            collectEntries(keePassDatabase.content.group, entries)
            Result.success(entries.size)
        } catch (e: Exception) {
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
            val keePassDatabase = decodeDatabase(bytes, credentials)
            val entries = mutableListOf<Entry>()
            collectEntries(keePassDatabase.content.group, entries)
            Result.success(entries.size)
        } catch (e: Exception) {
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

            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            val parentSegments = parentPath
                ?.split("/")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val result = addGroupToPath(
                group = keePassDatabase.content.group,
                parentSegments = parentSegments,
                newGroupName = normalizedName,
                currentPath = ""
            )
            val updatedDatabase = keePassDatabase.modifyParentGroup { result.first }
            writeDatabase(database, credentials, updatedDatabase)
            Result.success(result.second)
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
            val pathSegments = groupPath.split("/")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }

            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            val result = renameGroupByPath(
                group = keePassDatabase.content.group,
                pathSegments = pathSegments,
                newName = normalizedName,
                currentPath = ""
            )
            val updatedDatabase = keePassDatabase.modifyParentGroup { result.first }
            writeDatabase(database, credentials, updatedDatabase)
            Result.success(result.second)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(
        databaseId: Long,
        groupPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pathSegments = groupPath.split("/")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }

            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            val result = removeGroupByPath(
                group = keePassDatabase.content.group,
                pathSegments = pathSegments
            )
            if (!result.second) {
                throw IllegalArgumentException("分组不存在: $groupPath")
            }

            val updatedDatabase = keePassDatabase.modifyParentGroup { result.first }
            writeDatabase(database, credentials, updatedDatabase)
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
                collectGroups(group, "", groups)
            }
            Result.success(groups.sortedBy { it.path })
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            var updatedDatabase = keePassDatabase
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
            writeDatabase(database, credentials, updatedDatabase)
            val allEntries = mutableListOf<Entry>()
            collectEntries(updatedDatabase.content.group, allEntries)
            dao.updateEntryCount(database.id, allEntries.size)
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            val plainPassword = resolvePassword(entry)
            val updateResult = updateEntry(keePassDatabase, entry, plainPassword)
            val updatedDatabase = if (updateResult.second) updateResult.first else {
                val newEntry = buildEntry(entry, plainPassword)
                keePassDatabase.modifyParentGroup {
                    copy(entries = this.entries + newEntry)
                }
            }
            writeDatabase(database, credentials, updatedDatabase)
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            var updatedDatabase = keePassDatabase
            var removedCount = 0
            entries.forEach { entry ->
                val result = removeEntry(updatedDatabase, entry)
                updatedDatabase = result.first
                removedCount += result.second
            }
            writeDatabase(database, credentials, updatedDatabase)
            val allEntries = mutableListOf<Entry>()
            collectEntries(updatedDatabase.content.group, allEntries)
            dao.updateEntryCount(database.id, allEntries.size)
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            var updatedDatabase = keePassDatabase
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
            writeDatabase(database, credentials, updatedDatabase)
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            val updateResult = updateSecureItemInternal(keePassDatabase, item)
            val updatedDatabase = if (updateResult.second) {
                updateResult.first
            } else {
                val newEntry = buildSecureItemEntry(item)
                keePassDatabase.modifyParentGroup {
                    copy(entries = this.entries + newEntry)
                }
            }
            writeDatabase(database, credentials, updatedDatabase)
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
            val (database, credentials, keePassDatabase) = loadDatabase(databaseId)
            var updatedDatabase = keePassDatabase
            var removedCount = 0
            items.forEach { item ->
                val result = removeSecureItem(updatedDatabase, item)
                updatedDatabase = result.first
                removedCount += result.second
            }
            writeDatabase(database, credentials, updatedDatabase)
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(e)
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
        val updater: (Entry) -> Entry = { existing ->
            existing.copy(fields = buildEntryFields(entry, plainPassword))
        }
        val result = updateEntryInGroup(keePassDatabase.content.group, matcher, updater)
        val updatedDatabase = if (result.second) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
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
        val title = getFieldValue(entry, "Title")
        val username = getFieldValue(entry, "UserName")
        val password = getFieldValue(entry, "Password")
        val url = getFieldValue(entry, "URL")
        val notes = getFieldValue(entry, "Notes")
        if (title.isEmpty() && username.isEmpty() && password.isEmpty() && url.isEmpty() && notes.isEmpty()) {
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
        if (typeRaw.isBlank()) return null
        val itemType = runCatching { ItemType.valueOf(typeRaw) }.getOrNull() ?: return null
        if (allowedTypes != null && itemType !in allowedTypes) return null

        val itemData = getFieldValue(entry, FIELD_MONICA_ITEM_DATA)
        if (itemData.isBlank()) return null

        val title = getFieldValue(entry, "Title")
        val notes = getFieldValue(entry, "Notes")
        val imagePaths = getFieldValue(entry, FIELD_MONICA_IMAGE_PATHS)
        val isFavorite = getFieldValue(entry, FIELD_MONICA_IS_FAVORITE).toBoolean()
        val sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID).toLongOrNull()
        val now = java.util.Date()

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

    private fun getFieldValue(entry: Entry, key: String): String {
        val value = entry.fields[key]
        return if (value == null) "" else value.content
    }

    private fun collectEntries(group: Group, entries: MutableList<Entry>) {
        entries.addAll(group.entries)
        group.groups.forEach { collectEntries(it, entries) }
    }

    private fun collectEntriesWithGroupPath(
        group: Group,
        currentPath: String?,
        entries: MutableList<Pair<Entry, String?>>
    ) {
        group.entries.forEach { entry ->
            entries.add(entry to currentPath)
        }
        group.groups.forEach { child ->
            val nextPath = if (currentPath.isNullOrBlank()) child.name else "$currentPath/${child.name}"
            collectEntriesWithGroupPath(child, nextPath, entries)
        }
    }

    private fun collectGroups(
        group: Group,
        parentPath: String,
        result: MutableList<KeePassGroupInfo>
    ) {
        val name = group.name.ifBlank { "(未命名)" }
        val currentPath = if (parentPath.isBlank()) name else "$parentPath/$name"
        result.add(
            KeePassGroupInfo(
                name = name,
                path = currentPath,
                uuid = group.uuid.toString()
            )
        )
        group.groups.forEach { child ->
            collectGroups(child, currentPath, result)
        }
    }

    private fun addGroupToPath(
        group: Group,
        parentSegments: List<String>,
        newGroupName: String,
        currentPath: String
    ): Pair<Group, KeePassGroupInfo> {
        if (parentSegments.isEmpty()) {
            val existing = group.groups.firstOrNull { it.name.equals(newGroupName, ignoreCase = true) }
            if (existing != null) {
                val existingPath = if (currentPath.isBlank()) existing.name else "$currentPath/${existing.name}"
                return group to KeePassGroupInfo(
                    name = existing.name,
                    path = existingPath,
                    uuid = existing.uuid.toString()
                )
            }

            val newGroup = Group(
                uuid = UUID.randomUUID(),
                name = newGroupName
            )
            val newPath = if (currentPath.isBlank()) newGroupName else "$currentPath/$newGroupName"
            return group.copy(groups = group.groups + newGroup) to KeePassGroupInfo(
                name = newGroupName,
                path = newPath,
                uuid = newGroup.uuid.toString()
            )
        }

        val nextSegment = parentSegments.first()
        val childIndex = group.groups.indexOfFirst { it.name == nextSegment }
        if (childIndex < 0) {
            throw IllegalArgumentException("父分组不存在: $nextSegment")
        }

        val child = group.groups[childIndex]
        val childPath = if (currentPath.isBlank()) child.name else "$currentPath/${child.name}"
        val childResult = addGroupToPath(
            group = child,
            parentSegments = parentSegments.drop(1),
            newGroupName = newGroupName,
            currentPath = childPath
        )

        val updatedGroups = group.groups.toMutableList()
        updatedGroups[childIndex] = childResult.first
        return group.copy(groups = updatedGroups) to childResult.second
    }

    private fun renameGroupByPath(
        group: Group,
        pathSegments: List<String>,
        newName: String,
        currentPath: String
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
            val newPath = if (currentPath.isBlank()) newName else "$currentPath/$newName"
            group.copy(groups = updatedGroups) to KeePassGroupInfo(
                name = newName,
                path = newPath,
                uuid = renamed.uuid.toString()
            )
        } else {
            val childPath = if (currentPath.isBlank()) child.name else "$currentPath/${child.name}"
            val childResult = renameGroupByPath(
                group = child,
                pathSegments = pathSegments.drop(1),
                newName = newName,
                currentPath = childPath
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

    private suspend fun loadDatabase(databaseId: Long): Triple<LocalKeePassDatabase, Credentials, KeePassDatabase> {
        val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
        val credentials = buildCredentials(database)
        val bytes = readDatabaseBytes(database)
        val keePassDatabase = decodeDatabase(bytes, credentials)
        return Triple(database, credentials, keePassDatabase)
    }

    private suspend fun decodeDatabase(bytes: ByteArray, credentials: Credentials): KeePassDatabase {
        return globalDecodeMutex.withLock {
            KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials)
        }
    }

    private fun buildCredentials(
        database: LocalKeePassDatabase,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Credentials {
        val encryptedDbPassword = database.encryptedPassword
        val kdbxPassword = passwordOverride ?: (encryptedDbPassword?.let { securityManager.decryptData(it) } ?: "")
        val keyFileBytes = keyFileUriOverride?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("无法读取密钥文件")
        } ?: database.keyFileUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("无法读取密钥文件")
        }
        return if (keyFileBytes != null) {
            Credentials.from(EncryptedValue.fromString(kdbxPassword), keyFileBytes)
        } else {
            Credentials.from(EncryptedValue.fromString(kdbxPassword))
        }
    }

    private fun buildCredentialsFromRaw(password: String, keyFileUri: Uri? = null): Credentials {
        val keyFileBytes = keyFileUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("无法读取密钥文件")
        }
        return if (keyFileBytes != null) {
            Credentials.from(EncryptedValue.fromString(password), keyFileBytes)
        } else {
            Credentials.from(EncryptedValue.fromString(password))
        }
    }

    private fun readDatabaseBytes(database: LocalKeePassDatabase): ByteArray {
        if (database.filePath.startsWith(WEBDAV_PATH_PREFIX)) {
            return readWebDavBytes(database.filePath.removePrefix(WEBDAV_PATH_PREFIX))
        }
        return if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
            val file = File(context.filesDir, database.filePath)
            if (!file.exists()) throw Exception("数据库文件不存在")
            file.readBytes()
        } else {
            val uri = Uri.parse(database.filePath)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("无法打开数据库文件")
        }
    }

    private suspend fun writeDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        keePassDatabase: KeePassDatabase
    ) {
        val bytes = encodeDatabase(keePassDatabase)
        decodeDatabase(bytes, credentials)
        if (database.filePath.startsWith(WEBDAV_PATH_PREFIX)) {
            writeWebDav(database.filePath.removePrefix(WEBDAV_PATH_PREFIX), bytes)
        } else if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
            writeInternal(database, bytes)
        } else {
            writeExternal(database, bytes)
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

    private fun readWebDavBytes(remotePath: String): ByteArray {
        val sardine = buildWebDavClient()
        return sardine.get(remotePath).use { it.readBytes() }
    }

    private fun writeWebDav(remotePath: String, bytes: ByteArray) {
        val sardine = buildWebDavClient()
        val parentPath = remotePath.substringBeforeLast('/', "")
        if (parentPath.isNotBlank()) {
            runCatching {
                if (!sardine.exists(parentPath)) {
                    sardine.createDirectory(parentPath)
                }
            }
        }
        sardine.put(remotePath, bytes, "application/octet-stream")
    }

    private fun buildWebDavClient(): Sardine {
        val prefs = context.getSharedPreferences(KEEPASS_WEBDAV_PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_KEEPASS_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_KEEPASS_PASSWORD, "") ?: ""
        if (username.isBlank() || password.isBlank()) {
            throw Exception("WebDAV 未配置或凭证已失效")
        }
        return OkHttpSardine().apply {
            setCredentials(username, password)
        }
    }
}
