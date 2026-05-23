package takagi.ru.monica.utils

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.webdav.WebDavCredentials
import takagi.ru.monica.webdav.WebDavGateway
import java.io.IOException

class WebDavMdbxFileSource(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) : MdbxFileSource {

    private val normalizedServerUrl = serverUrl.trim().trimEnd('/')
    private val credentials = WebDavCredentials(username, password)
    private val sardine: OkHttpSardine by lazy { WebDavGateway.buildClient(credentials) }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sardine.list(normalizedServerUrl, 0)
        }.map { Unit }
    }

    override suspend fun listDirectory(path: String?): List<FileSourceEntry> =
        withContext(Dispatchers.IO) {
            val targetUrl = WebDavKeePassFileSource.buildRemoteUrl(
                normalizedServerUrl, path
            )
            val resources: List<DavResource> = sardine.list(targetUrl)
            resources.filter { it.name != "." && it.name != ".." }.map { resource ->
                FileSourceEntry(
                    name = resource.name,
                    path = WebDavKeePassFileSource.buildChildPath(
                        WebDavKeePassFileSource.normalizeOptionalRemotePath(path), resource.name
                    ),
                    isDirectory = resource.isDirectory,
                    lastModified = resource.modified?.time,
                    sizeBytes = resource.contentLength
                )
            }.sortedWith(
                compareByDescending<FileSourceEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
            )
        }

    override suspend fun createDirectory(
        parentPath: String?,
        name: String
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val sanitizedName = name.trim().trim('/').ifBlank {
            throw IOException("目录名不能为空")
        }
        val normalizedParent = WebDavKeePassFileSource.normalizeOptionalRemotePath(parentPath)
        val targetPath = if (normalizedParent.isBlank()) sanitizedName
        else "$normalizedParent/$sanitizedName"
        val targetUrl = WebDavKeePassFileSource.buildRemoteUrl(normalizedServerUrl, targetPath)

        if (webDavPathExists(targetUrl)) {
            throw IOException("目录已存在: $sanitizedName")
        }

        sardine.createDirectory(targetUrl)
        FileSourceEntry(
            name = sanitizedName,
            path = targetPath,
            isDirectory = true
        )
    }

    override suspend fun createPlaceholderFile(
        parentPath: String?,
        name: String
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        writeFile(parentPath, name, ByteArray(0))
    }

    override suspend fun writeFile(
        parentPath: String?,
        name: String,
        bytes: ByteArray
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val sanitizedName = name.trim().trim('/').ifBlank {
            throw IOException("文件名不能为空")
        }
        val normalizedParent = WebDavKeePassFileSource.normalizeOptionalRemotePath(parentPath)
        val targetPath = if (normalizedParent.isBlank()) sanitizedName
        else "$normalizedParent/$sanitizedName"
        val targetUrl = WebDavKeePassFileSource.buildRemoteUrl(normalizedServerUrl, targetPath)

        if (webDavPathExists(targetUrl)) {
            throw IOException("文件已存在: $sanitizedName")
        }

        // Auto-create intermediate directories
        if (normalizedParent.isNotBlank()) {
            ensureDirectoryPathExists(normalizedParent)
        }

        sardine.put(targetUrl, bytes, "application/octet-stream")
        FileSourceEntry(
            name = sanitizedName,
            path = targetPath,
            isDirectory = false,
            sizeBytes = bytes.size.toLong()
        )
    }

    override suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        val targetUrl = WebDavKeePassFileSource.buildRemoteUrl(normalizedServerUrl, path)
        sardine.get(targetUrl).use { input -> input.readBytes() }
    }

    suspend fun overwriteFile(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val normalizedPath = WebDavKeePassFileSource.normalizeOptionalRemotePath(path)
        val targetUrl = WebDavKeePassFileSource.buildRemoteUrl(normalizedServerUrl, normalizedPath)
        sardine.put(targetUrl, bytes, "application/octet-stream")
    }

    /**
     * Recursively create intermediate directories on WebDAV server.
     * Skips directories that already exist.
     */
    private fun ensureDirectoryPathExists(path: String) {
        val segments = path.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return

        var accumulatedPath = ""
        for (segment in segments) {
            accumulatedPath = if (accumulatedPath.isEmpty()) segment else "$accumulatedPath/$segment"
            val dirUrl = WebDavKeePassFileSource.buildRemoteUrl(normalizedServerUrl, accumulatedPath)
            try {
                sardine.createDirectory(dirUrl)
            } catch (_: IOException) {
                // Directory may already exist, try listing to confirm
                try {
                    sardine.list(dirUrl, 0)
                } catch (_: IOException) {
                    throw IOException("无法创建远程目录: $accumulatedPath")
                }
            }
        }
    }

    private fun webDavPathExists(targetUrl: String): Boolean {
        return try {
            sardine.list(targetUrl, 1)
            true
        } catch (_: IOException) {
            false
        }
    }
}
