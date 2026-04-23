package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.data.KeepassRemoteSource
import java.io.IOException
import java.time.Instant
import java.util.Locale

@Serializable
private data class GoogleDriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val size: String? = null,
    val version: String? = null,
    val md5Checksum: String? = null,
    val modifiedTime: String? = null,
    val parents: List<String> = emptyList()
)

@Serializable
private data class GoogleDriveFileListResponseDto(
    val files: List<GoogleDriveFileDto> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
private data class GoogleDriveCreateMetadata(
    val name: String,
    val mimeType: String? = null,
    val parents: List<String>? = null
)

class GoogleDriveKeePassFileSource(
    context: Context,
    private val accountIdentifier: String,
    private val itemId: String? = null,
    private val remotePath: String? = null
) : KeePassFileSource {
    private val appContext = context.applicationContext
    private val authManager = GoogleDriveAuthManager(appContext)
    private val normalizedRemotePath = normalizeOptionalRemotePath(remotePath)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    override suspend fun stat(): FileSourceStat = withContext(Dispatchers.IO) {
        resolveFileItem().toStat()
    }

    override suspend fun read(): ByteArray = withContext(Dispatchers.IO) {
        val fileId = resolveFileItem().id
        executeBytesRequest(
            absoluteUrl = "$DRIVE_API_BASE/files/${Uri.encode(fileId)}?alt=media",
            accessToken = requireAccessToken()
        )
    }

    override suspend fun write(
        bytes: ByteArray,
        expectedVersion: String?
    ): FileSourceWriteResult = withContext(Dispatchers.IO) {
        val fileItem = resolveFileItem()
        if (expectedVersion != null) {
            val actual = fileItem.versionToken()
            if (actual != null && actual != expectedVersion) {
                throw IOException("远端文件已变化，请先重新同步")
            }
        }
        val payload = executeJsonRequest(
            absoluteUrl = "$DRIVE_UPLOAD_BASE/files/${Uri.encode(fileItem.id)}?uploadType=media&fields=$FILE_FIELDS",
            accessToken = requireAccessToken(),
            method = "PATCH",
            body = bytes,
            expectedStatusCodes = setOf(200)
        )
        json.decodeFromString<GoogleDriveFileDto>(payload).toWriteResult()
    }

    override suspend fun listChildren(): List<FileSourceEntry> = withContext(Dispatchers.IO) {
        val currentItem = runCatching { resolveFileItem() }.getOrNull()
        val targetId = when {
            currentItem == null -> null
            currentItem.isDirectory() -> currentItem.id
            else -> currentItem.parents.firstOrNull()
        }
        val targetPath = when {
            currentItem == null -> normalizedRemotePath
            currentItem.isDirectory() -> normalizedRemotePath
            else -> parentPathOf(normalizedRemotePath)
        }
        listDirectory(targetPath, targetId)
    }

    override suspend fun createFile(name: String): FileSourceEntry = withContext(Dispatchers.IO) {
        createFileInDirectory(
            parentPath = parentPathOf(normalizedRemotePath),
            name = name
        )
    }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (itemId != null || normalizedRemotePath.isNotBlank()) {
                val item = resolveFileItem()
                if (item.isDirectory()) {
                    listDirectory(
                        directoryPath = normalizedRemotePath,
                        directoryId = item.id
                    )
                } else {
                    stat()
                }
            } else {
                listDirectory("", null)
            }
            Unit
        }
    }

    suspend fun listDirectory(
        directoryPath: String? = null,
        directoryId: String? = null
    ): List<FileSourceEntry> = withContext(Dispatchers.IO) {
        val normalizedDirectoryPath = normalizeOptionalRemotePath(directoryPath)
        val resolvedDirectoryId = resolveDirectoryId(normalizedDirectoryPath, directoryId)
        val files = mutableListOf<GoogleDriveFileDto>()
        var pageToken: String? = null
        do {
            val encodedQ = Uri.encode("'$resolvedDirectoryId' in parents and trashed = false")
            val url = buildString {
                append("$DRIVE_API_BASE/files")
                append("?q=$encodedQ")
                append("&fields=nextPageToken,files($FILE_FIELDS)")
                append("&pageSize=1000")
                pageToken?.let { append("&pageToken=${Uri.encode(it)}") }
            }
            val payload = executeJsonRequest(
                absoluteUrl = url,
                accessToken = requireAccessToken()
            )
            val response = json.decodeFromString<GoogleDriveFileListResponseDto>(payload)
            files += response.files
            pageToken = response.nextPageToken
        } while (!pageToken.isNullOrBlank())

        files.map { item ->
            FileSourceEntry(
                id = item.id,
                name = item.name,
                path = buildChildPath(normalizedDirectoryPath, item.name),
                isDirectory = item.isDirectory(),
                versionToken = item.versionToken(),
                lastModified = item.modifiedTime?.toEpochMillis(),
                sizeBytes = item.size?.toLongOrNull()
            )
        }.sortedWith(
            compareBy<FileSourceEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    suspend fun createDirectory(
        parentPath: String?,
        name: String,
        parentId: String? = null
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedParentPath = normalizeOptionalRemotePath(parentPath)
        val resolvedParentId = resolveDirectoryId(normalizedParentPath, parentId)
        ensureChildDoesNotExist(resolvedParentId, name)
        val metadata = GoogleDriveCreateMetadata(
            name = name.trim(),
            mimeType = FOLDER_MIME_TYPE,
            parents = if (resolvedParentId == ROOT_ID) null else listOf(resolvedParentId)
        )
        val payload = executeJsonRequest(
            absoluteUrl = "$DRIVE_API_BASE/files?fields=$FILE_FIELDS",
            accessToken = requireAccessToken(),
            method = "POST",
            body = json.encodeToString(GoogleDriveCreateMetadata.serializer(), metadata).encodeToByteArray(),
            contentType = "application/json; charset=utf-8",
            expectedStatusCodes = setOf(200)
        )
        val item = json.decodeFromString<GoogleDriveFileDto>(payload)
        FileSourceEntry(
            id = item.id,
            name = item.name,
            path = buildChildPath(normalizedParentPath, item.name),
            isDirectory = true,
            versionToken = item.versionToken(),
            lastModified = item.modifiedTime?.toEpochMillis(),
            sizeBytes = item.size?.toLongOrNull()
        )
    }

    suspend fun createFileInDirectory(
        parentPath: String?,
        name: String,
        bytes: ByteArray = ByteArray(0),
        parentId: String? = null
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedParentPath = normalizeOptionalRemotePath(parentPath)
        val resolvedParentId = resolveDirectoryId(normalizedParentPath, parentId)
        ensureChildDoesNotExist(resolvedParentId, name)

        val metadata = GoogleDriveCreateMetadata(
            name = name.trim(),
            parents = if (resolvedParentId == ROOT_ID) null else listOf(resolvedParentId)
        )
        val boundary = "----MonicaGoogleDrive${System.currentTimeMillis()}"
        val metadataBytes = json.encodeToString(GoogleDriveCreateMetadata.serializer(), metadata).encodeToByteArray()
        val multipartBody = buildMultipartBody(boundary, metadataBytes, bytes)
        val payload = executeJsonRequest(
            absoluteUrl = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=$FILE_FIELDS",
            accessToken = requireAccessToken(),
            method = "POST",
            body = multipartBody,
            contentType = "multipart/related; boundary=$boundary",
            expectedStatusCodes = setOf(200)
        )
        val item = json.decodeFromString<GoogleDriveFileDto>(payload)
        FileSourceEntry(
            id = item.id,
            name = item.name,
            path = buildChildPath(normalizedParentPath, item.name),
            isDirectory = false,
            versionToken = item.versionToken(),
            lastModified = item.modifiedTime?.toEpochMillis(),
            sizeBytes = item.size?.toLongOrNull() ?: bytes.size.toLong()
        )
    }

    private suspend fun resolveFileItem(): GoogleDriveFileDto {
        return itemId?.takeIf { it.isNotBlank() }
            ?.let { resolveItemById(it) }
            ?: resolveItemByPath(normalizedRemotePath)
    }

    private suspend fun resolveItemById(resolvedItemId: String): GoogleDriveFileDto {
        val payload = executeJsonRequest(
            absoluteUrl = "$DRIVE_API_BASE/files/${Uri.encode(resolvedItemId)}?fields=$FILE_FIELDS",
            accessToken = requireAccessToken()
        )
        return json.decodeFromString(payload)
    }

    private suspend fun resolveItemByPath(path: String): GoogleDriveFileDto {
        if (path.isBlank()) {
            return GoogleDriveFileDto(
                id = ROOT_ID,
                name = ROOT_ID,
                mimeType = FOLDER_MIME_TYPE
            )
        }

        var currentParentId = ROOT_ID
        var currentPath = ""
        path.split('/')
            .filter { it.isNotBlank() }
            .forEachIndexed { index, segment ->
                val child = findChildByName(
                    parentId = currentParentId,
                    name = segment
                ) ?: throw IOException("Google Drive 路径不存在: $path")
                val isLast = index == path.split('/').filter { it.isNotBlank() }.lastIndex
                if (!isLast && !child.isDirectory()) {
                    throw IOException("Google Drive 路径无效: $currentPath/$segment 不是文件夹")
                }
                currentParentId = child.id
                currentPath = buildChildPath(currentPath, segment)
            }
        return resolveItemById(currentParentId)
    }

    private suspend fun findChildByName(
        parentId: String,
        name: String
    ): GoogleDriveFileDto? {
        val escapedName = escapeQueryValue(name.trim())
        val q = "'$parentId' in parents and name = '$escapedName' and trashed = false"
        val payload = executeJsonRequest(
            absoluteUrl = "$DRIVE_API_BASE/files?q=${Uri.encode(q)}&fields=files($FILE_FIELDS)&pageSize=10",
            accessToken = requireAccessToken()
        )
        return json.decodeFromString<GoogleDriveFileListResponseDto>(payload).files.firstOrNull()
    }

    private suspend fun ensureChildDoesNotExist(parentId: String, name: String) {
        if (findChildByName(parentId, name) != null) {
            throw IOException("同名文件或目录已存在")
        }
    }

    private suspend fun resolveDirectoryId(parentPath: String, parentId: String?): String {
        if (!parentId.isNullOrBlank()) {
            return parentId
        }
        if (parentPath.isBlank()) {
            return ROOT_ID
        }
        val item = resolveItemByPath(parentPath)
        if (!item.isDirectory()) {
            throw IOException("Google Drive 目标目录无效")
        }
        return item.id
    }

    private suspend fun requireAccessToken(): String {
        return authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("Google Drive 访问令牌为空")
    }

    private suspend fun executeJsonRequest(
        absoluteUrl: String,
        accessToken: String,
        method: String = "GET",
        body: ByteArray? = null,
        contentType: String = "application/octet-stream",
        expectedStatusCodes: Set<Int> = setOf(200)
    ): String {
        val requestBody = body?.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(absoluteUrl)
            .header("Authorization", "Bearer $accessToken")
            .method(method, requestBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code !in expectedStatusCodes) {
                if (response.code == 401) {
                    runCatching { authManager.clearAccessToken(accessToken) }
                }
                throw IOException(
                    responseBody.ifBlank { "Google Drive 请求失败: HTTP ${response.code}" }
                )
            }
            return responseBody
        }
    }

    private suspend fun executeBytesRequest(
        absoluteUrl: String,
        accessToken: String
    ): ByteArray {
        val request = Request.Builder()
            .url(absoluteUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    runCatching { authManager.clearAccessToken(accessToken) }
                }
                throw IOException(response.body?.string().orEmpty().ifBlank {
                    "Google Drive 下载失败: HTTP ${response.code}"
                })
            }
            return response.body?.bytes() ?: throw IOException("Google Drive 返回了空内容")
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        metadataBytes: ByteArray,
        fileBytes: ByteArray
    ): ByteArray {
        val lineBreak = "\r\n"
        val header = buildString {
            append("--").append(boundary).append(lineBreak)
            append("Content-Type: application/json; charset=UTF-8").append(lineBreak).append(lineBreak)
        }.encodeToByteArray()
        val middle = buildString {
            append(lineBreak)
            append("--").append(boundary).append(lineBreak)
            append("Content-Type: application/octet-stream").append(lineBreak).append(lineBreak)
        }.encodeToByteArray()
        val footer = buildString {
            append(lineBreak)
            append("--").append(boundary).append("--")
        }.encodeToByteArray()
        return ByteArray(header.size + metadataBytes.size + middle.size + fileBytes.size + footer.size).also { buffer ->
            var offset = 0
            header.copyInto(buffer, offset)
            offset += header.size
            metadataBytes.copyInto(buffer, offset)
            offset += metadataBytes.size
            middle.copyInto(buffer, offset)
            offset += middle.size
            fileBytes.copyInto(buffer, offset)
            offset += fileBytes.size
            footer.copyInto(buffer, offset)
        }
    }

    private fun GoogleDriveFileDto.isDirectory(): Boolean = mimeType == FOLDER_MIME_TYPE

    private fun GoogleDriveFileDto.versionToken(): String? = version ?: md5Checksum

    private fun GoogleDriveFileDto.toStat(): FileSourceStat {
        return FileSourceStat(
            versionToken = versionToken(),
            etag = md5Checksum,
            lastModified = modifiedTime?.toEpochMillis(),
            sizeBytes = size?.toLongOrNull(),
            isDirectory = isDirectory(),
            displayName = name
        )
    }

    private fun GoogleDriveFileDto.toWriteResult(): FileSourceWriteResult {
        return FileSourceWriteResult(
            versionToken = versionToken(),
            etag = md5Checksum,
            lastModified = modifiedTime?.toEpochMillis()
        )
    }

    private fun escapeQueryValue(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "\\'")
    }

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val FILE_FIELDS = "id,name,mimeType,modifiedTime,md5Checksum,size,version,parents"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val ROOT_ID = "root"

        fun normalizeRemotePath(remotePath: String): String {
            val normalized = remotePath
                .trim()
                .replace('\\', '/')
                .trimStart('/')
                .replace(Regex("/+"), "/")
            if (normalized.isBlank()) {
                throw IllegalArgumentException("远端文件路径不能为空")
            }
            return normalized
        }

        fun normalizeOptionalRemotePath(remotePath: String?): String {
            return remotePath
                ?.trim()
                ?.replace('\\', '/')
                ?.trim('/')
                ?.replace(Regex("/+"), "/")
                .orEmpty()
        }

        fun parentPathOf(remotePath: String): String {
            if (remotePath.isBlank()) {
                return ""
            }
            val normalized = normalizeRemotePath(remotePath)
            val index = normalized.lastIndexOf('/')
            return if (index <= 0) "" else normalized.substring(0, index)
        }

        fun buildChildPath(parentPath: String, name: String): String {
            val sanitizedName = name.trim().trim('/').ifBlank {
                throw IllegalArgumentException("文件名不能为空")
            }
            require('/' !in sanitizedName) { "文件名不能包含路径分隔符" }
            return if (parentPath.isBlank()) sanitizedName else "$parentPath/$sanitizedName"
        }
    }
}

private fun String.toEpochMillis(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

object GoogleDriveKeePassSupport {
    fun createFileSource(
        context: Context,
        source: KeepassRemoteSource
    ): GoogleDriveKeePassFileSource {
        val accountIdentifier = source.tokenRef?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Google Drive 账户引用不能为空")
        return GoogleDriveKeePassFileSource(
            context = context,
            accountIdentifier = accountIdentifier,
            itemId = source.itemId,
            remotePath = source.remotePath
        )
    }

    fun buildLocalMirrorPaths(sourceId: Long, remotePath: String): KeePassLocalMirrorPaths {
        val fileName = displayNameFromRemotePath(remotePath)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "remote.kdbx" }
        val baseDir = "keepass_remote/gdrive_$sourceId"
        return KeePassLocalMirrorPaths(
            workingCopyPath = "$baseDir/working_$fileName",
            cacheCopyPath = "$baseDir/cache_$fileName"
        )
    }

    fun displayNameFromRemotePath(remotePath: String): String {
        val normalized = GoogleDriveKeePassFileSource.normalizeRemotePath(remotePath)
        return normalized.substringAfterLast('/').ifBlank { "remote.kdbx" }
    }

    fun writeRelativeFile(
        context: Context,
        relativePath: String,
        bytes: ByteArray
    ) {
        WebDavKeePassSupport.writeRelativeFile(context, relativePath, bytes)
    }

    fun deleteRelativeFile(context: Context, relativePath: String?) {
        WebDavKeePassSupport.deleteRelativeFile(context, relativePath)
    }

    fun sha256Hex(bytes: ByteArray): String {
        return WebDavKeePassSupport.sha256Hex(bytes)
    }
}
