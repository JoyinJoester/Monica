package takagi.ru.monica.utils

/**
 * File source abstraction for MDBX vault storage.
 *
 * FileSourceEntry is reused from KeePassFileSource.kt.
 */
interface MdbxFileSource {
    suspend fun testConnection(): Result<Unit>
    suspend fun listDirectory(path: String? = null): List<FileSourceEntry>
    suspend fun createDirectory(parentPath: String?, name: String): FileSourceEntry
    suspend fun createPlaceholderFile(parentPath: String?, name: String): FileSourceEntry
    suspend fun writeFile(parentPath: String?, name: String, bytes: ByteArray): FileSourceEntry
    suspend fun readFile(path: String): ByteArray
}
