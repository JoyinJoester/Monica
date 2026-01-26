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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
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
    val monicaLocalId: Long?
)

class KeePassKdbxService(
    private val context: Context,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) {
    suspend fun readPasswordEntries(databaseId: Long): Result<List<KeePassEntryData>> = withContext(Dispatchers.IO) {
        try {
            val (database, _, keePassDatabase) = loadDatabase(databaseId)
            val entries = mutableListOf<Entry>()
            collectEntries(keePassDatabase.content.group, entries)
            val data = entries.mapNotNull { entryToData(it) }
            dao.updateEntryCount(database.id, data.size)
            Result.success(data)
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
            pairs.add("MonicaLocalId" to EntryValue.Plain(monicaId))
        }
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun updateEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry,
        plainPassword: String
    ): Pair<KeePassDatabase, Boolean> {
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, "MonicaLocalId").toLongOrNull()
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

    private fun removeEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry
    ): Pair<KeePassDatabase, Int> {
        val matcher: (Entry) -> Boolean = { existing ->
            val monicaId = getFieldValue(existing, "MonicaLocalId").toLongOrNull()
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

    private fun entryToData(entry: Entry): KeePassEntryData? {
        val title = getFieldValue(entry, "Title")
        val username = getFieldValue(entry, "UserName")
        val password = getFieldValue(entry, "Password")
        val url = getFieldValue(entry, "URL")
        val notes = getFieldValue(entry, "Notes")
        if (title.isEmpty() && username.isEmpty() && password.isEmpty() && url.isEmpty() && notes.isEmpty()) {
            return null
        }
        val monicaId = getFieldValue(entry, "MonicaLocalId").toLongOrNull()
        return KeePassEntryData(
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            monicaLocalId = monicaId
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

    private suspend fun loadDatabase(databaseId: Long): Triple<LocalKeePassDatabase, Credentials, KeePassDatabase> {
        val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
        val credentials = buildCredentials(database)
        val bytes = readDatabaseBytes(database)
        val keePassDatabase = KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials)
        return Triple(database, credentials, keePassDatabase)
    }

    private fun buildCredentials(database: LocalKeePassDatabase): Credentials {
        val encryptedDbPassword = database.encryptedPassword ?: throw Exception("数据库密码未设置")
        val kdbxPassword = securityManager.decryptData(encryptedDbPassword)
        return Credentials.from(EncryptedValue.fromString(kdbxPassword))
    }

    private fun readDatabaseBytes(database: LocalKeePassDatabase): ByteArray {
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

    private fun writeDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        keePassDatabase: KeePassDatabase
    ) {
        val bytes = encodeDatabase(keePassDatabase)
        KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials)
        if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
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
}
