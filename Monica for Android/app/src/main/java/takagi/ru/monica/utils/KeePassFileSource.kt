package takagi.ru.monica.utils

data class FileSourceStat(
    val versionToken: String? = null,
    val etag: String? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null,
    val isDirectory: Boolean = false,
    val displayName: String? = null
)

data class FileSourceEntry(
    val id: String? = null,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val versionToken: String? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null
)

data class FileSourceWriteResult(
    val versionToken: String? = null,
    val etag: String? = null,
    val lastModified: Long? = null
)

interface KeePassFileSource {
    suspend fun stat(): FileSourceStat
    suspend fun read(): ByteArray
    suspend fun write(
        bytes: ByteArray,
        expectedVersion: String? = null
    ): FileSourceWriteResult

    suspend fun listChildren(): List<FileSourceEntry>
    suspend fun createFile(name: String): FileSourceEntry
    suspend fun testConnection(): Result<Unit>
}
