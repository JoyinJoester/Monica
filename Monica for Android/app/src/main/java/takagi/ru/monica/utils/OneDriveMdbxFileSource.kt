package takagi.ru.monica.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OneDriveMdbxFileSource(
    private val context: Context,
    private val accountId: String
) : MdbxFileSource {

    private fun delegate(remotePath: String? = null) =
        OneDriveKeePassFileSource(context, accountId, remotePath = remotePath)

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { delegate().testConnection().getOrThrow() }
    }

    override suspend fun listDirectory(path: String?): List<FileSourceEntry> =
        withContext(Dispatchers.IO) {
            val normalizedPath = OneDriveKeePassFileSource.normalizeOptionalRemotePath(path)
            delegate().listDirectory(normalizedPath)
                .filter { it.isDirectory || it.name.endsWith(".mdbx", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<FileSourceEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
        }

    override suspend fun createDirectory(
        parentPath: String?,
        name: String
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        delegate().createDirectory(parentPath, name)
    }

    override suspend fun createPlaceholderFile(
        parentPath: String?,
        name: String
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        delegate().createFileInDirectory(parentPath, name, ByteArray(0))
    }

    override suspend fun writeFile(
        parentPath: String?,
        name: String,
        bytes: ByteArray
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedParentPath = OneDriveKeePassFileSource.normalizeOptionalRemotePath(parentPath)
        val targetPath = OneDriveKeePassFileSource.buildChildPath(normalizedParentPath, name)
        val writeResult = delegate(remotePath = targetPath).write(bytes, expectedVersion = null)
        FileSourceEntry(
            name = name,
            path = targetPath,
            isDirectory = false,
            versionToken = writeResult.versionToken,
            lastModified = writeResult.lastModified,
            sizeBytes = bytes.size.toLong()
        )
    }

    override suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        delegate(path).read()
    }
}
