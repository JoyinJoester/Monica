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
private data class OneDriveDriveItemDto(
    val id: String,
    val name: String,
    val size: Long? = null,
    @SerialName("eTag")
    val eTag: String? = null,
    @SerialName("cTag")
    val cTag: String? = null,
    @SerialName("lastModifiedDateTime")
    val lastModifiedDateTime: String? = null,
    val folder: FolderFacetDto? = null,
    val file: FileFacetDto? = null,
    @SerialName("parentReference")
    val parentReference: ParentReferenceDto? = null
)

@Serializable
private data class OneDriveChildrenResponseDto(
    val value: List<OneDriveDriveItemDto> = emptyList()
)

@Serializable
private data class FolderFacetDto(
    @SerialName("childCount")
    val childCount: Int? = null
)

@Serializable
private data class FileFacetDto(
    val mimeType: String? = null
)

@Serializable
private data class ParentReferenceDto(
    @SerialName("driveId")
    val driveId: String? = null,
    val path: String? = null
)

@Serializable
private data class OneDriveUploadSessionResponseDto(
    @SerialName("uploadUrl")
    val uploadUrl: String,
    @SerialName("expirationDateTime")
    val expirationDateTime: String? = null,
    @SerialName("nextExpectedRanges")
    val nextExpectedRanges: List<String> = emptyList()
)

@Serializable
private data class OneDriveUploadSessionRequestDto(
    val item: OneDriveUploadSessionItemDto = OneDriveUploadSessionItemDto()
)

@Serializable
private data class OneDriveUploadSessionItemDto(
    @SerialName("@microsoft.graph.conflictBehavior")
    val conflictBehavior: String = "replace"
)

class OneDriveKeePassFileSource(
    context: Context,
    private val accountIdentifier: String,
    private val driveId: String? = null,
    private val itemId: String? = null,
    private val remotePath: String? = null
) : KeePassFileSource {
    private val appContext = context.applicationContext
    private val authManager = OneDriveAuthManager(appContext)
    private val normalizedRemotePath = normalizeOptionalRemotePath(remotePath)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun stat(): FileSourceStat = withContext(Dispatchers.IO) {
        requireRemotePath()
        resolveFileItem().toStat()
    }

    override suspend fun read(): ByteArray = withContext(Dispatchers.IO) {
        requireRemotePath()
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        executeBytesRequest(
            relativeUrl = buildItemContentRelativeUrl(),
            accessToken = token
        )
    }

    override suspend fun write(
        bytes: ByteArray,
        expectedVersion: String?
    ): FileSourceWriteResult = withContext(Dispatchers.IO) {
        requireRemotePath()
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val headers = expectedVersion
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("If-Match" to it) }
            .orEmpty()
        if (bytes.size > LARGE_UPLOAD_THRESHOLD_BYTES) {
            return@withContext uploadLargeFile(
                accessToken = token,
                bytes = bytes,
                headers = headers
            )
        }
        val latest = executeJsonRequest(
            relativeUrl = buildItemContentRelativeUrl(),
            accessToken = token,
            method = "PUT",
            body = bytes,
            headers = headers,
            expectedStatusCodes = setOf(200, 201)
        )
        json.decodeFromString<OneDriveDriveItemDto>(latest).toWriteResult()
    }

    override suspend fun listChildren(): List<FileSourceEntry> = withContext(Dispatchers.IO) {
        val targetDirectory = when {
            normalizedRemotePath.isBlank() -> ""
            runCatching { stat() }.getOrNull()?.isDirectory == true -> normalizedRemotePath
            else -> parentPathOf(normalizedRemotePath)
        }
        listDirectory(targetDirectory)
    }

    override suspend fun createFile(name: String): FileSourceEntry = withContext(Dispatchers.IO) {
        val targetParent = parentPathOf(normalizedRemotePath)
        createFileInDirectory(targetParent, name)
    }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.acquireAccessToken(accountIdentifier).accessToken
                ?: throw IOException("OneDrive 访问令牌为空")
            val relativeUrl = if (normalizedRemotePath.isBlank()) {
                "${driveBaseRelativeUrl()}/root/children"
            } else {
                val item = resolveItemByPath(normalizedRemotePath)
                if (item.folder != null) {
                    buildChildrenRelativeUrl(normalizedRemotePath)
                } else {
                    val parent = parentPathOf(normalizedRemotePath)
                    if (parent.isBlank()) {
                        "${driveBaseRelativeUrl()}/root/children"
                    } else {
                        buildChildrenRelativeUrl(parent)
                    }
                }
            }
            executeJsonRequest(relativeUrl = relativeUrl, accessToken = token)
            Unit
        }
    }

    suspend fun listDirectory(directoryPath: String? = null): List<FileSourceEntry> = withContext(Dispatchers.IO) {
        val normalizedDirectoryPath = normalizeOptionalRemotePath(directoryPath)
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = buildChildrenRelativeUrl(normalizedDirectoryPath),
            accessToken = token
        )
        json.decodeFromString<OneDriveChildrenResponseDto>(payload)
            .value
            .map { item ->
                FileSourceEntry(
                    id = item.id,
                    name = item.name,
                    path = buildChildPath(normalizedDirectoryPath, item.name),
                    isDirectory = item.folder != null,
                    versionToken = item.eTag ?: item.cTag,
                    lastModified = item.lastModifiedDateTime?.toEpochMillis(),
                    sizeBytes = item.size
                )
            }
            .sortedWith(
                compareBy<FileSourceEntry> { !it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }

    suspend fun createDirectory(parentPath: String?, name: String): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedParentPath = normalizeOptionalRemotePath(parentPath)
        val targetPath = buildChildPath(normalizedParentPath, name)
        if (runCatching { resolveItemByPath(targetPath) }.getOrNull() != null) {
            throw IOException("同名目录已存在")
        }
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = resolveCreateFolderRelativeUrl(normalizedParentPath),
            accessToken = token,
            method = "POST",
            body = json.encodeToString(
                FolderCreateRequest.serializer(),
                FolderCreateRequest(
                    name = name.trim(),
                    folder = FolderCreateFacet(),
                    conflictBehavior = "fail"
                )
            ).encodeToByteArray(),
            contentType = "application/json; charset=utf-8",
            expectedStatusCodes = setOf(200, 201)
        )
        val item = json.decodeFromString<OneDriveDriveItemDto>(payload)
        FileSourceEntry(
            id = item.id,
            name = item.name,
            path = targetPath,
            isDirectory = true,
            versionToken = item.eTag ?: item.cTag,
            lastModified = item.lastModifiedDateTime?.toEpochMillis(),
            sizeBytes = item.size
        )
    }

    suspend fun createFileInDirectory(
        parentPath: String?,
        name: String,
        bytes: ByteArray = ByteArray(0)
    ): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedParentPath = normalizeOptionalRemotePath(parentPath)
        val targetPath = buildChildPath(normalizedParentPath, name)
        if (runCatching { resolveItemByPath(targetPath) }.getOrNull() != null) {
            throw IOException("同名文件已存在")
        }
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = buildPathContentRelativeUrl(
                path = targetPath,
                conflictBehavior = "fail"
            ),
            accessToken = token,
            method = "PUT",
            body = bytes,
            expectedStatusCodes = setOf(200, 201)
        )
        val item = json.decodeFromString<OneDriveDriveItemDto>(payload)
        return@withContext FileSourceEntry(
            id = item.id,
            name = item.name,
            path = targetPath,
            isDirectory = false,
            versionToken = item.eTag ?: item.cTag,
            lastModified = item.lastModifiedDateTime?.toEpochMillis(),
            sizeBytes = item.size ?: bytes.size.toLong()
        )
    }

    suspend fun deleteEntry(targetPath: String) = withContext(Dispatchers.IO) {
        val normalizedTargetPath = normalizeRemotePath(targetPath)
        val item = resolveItemByPath(normalizedTargetPath)
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        executeJsonRequest(
            relativeUrl = "${driveBaseRelativeUrl()}/items/${Uri.encode(item.id)}",
            accessToken = token,
            method = "DELETE",
            expectedStatusCodes = setOf(204)
        )
    }

    suspend fun renameEntry(targetPath: String, newName: String): FileSourceEntry = withContext(Dispatchers.IO) {
        val normalizedTargetPath = normalizeRemotePath(targetPath)
        val sanitizedName = newName.trim().trim('/').ifBlank {
            throw IllegalArgumentException("文件名不能为空")
        }
        require('/' !in sanitizedName) { "文件名不能包含路径分隔符" }
        val item = resolveItemByPath(normalizedTargetPath)
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = "${driveBaseRelativeUrl()}/items/${Uri.encode(item.id)}",
            accessToken = token,
            method = "PATCH",
            body = json.encodeToString(
                RenameItemRequest.serializer(),
                RenameItemRequest(name = sanitizedName)
            ).encodeToByteArray(),
            contentType = "application/json; charset=utf-8",
            expectedStatusCodes = setOf(200)
        )
        val updated = json.decodeFromString<OneDriveDriveItemDto>(payload)
        FileSourceEntry(
            id = updated.id,
            name = updated.name,
            path = buildChildPath(parentPathOf(normalizedTargetPath), updated.name),
            isDirectory = updated.folder != null,
            versionToken = updated.eTag ?: updated.cTag,
            lastModified = updated.lastModifiedDateTime?.toEpochMillis(),
            sizeBytes = updated.size
        )
    }

    private suspend fun resolveFileItem(): OneDriveDriveItemDto {
        return itemId?.takeIf { it.isNotBlank() }
            ?.let { resolveItemById(it) }
            ?: resolveItemByPath(normalizedRemotePath)
    }

    private suspend fun resolveItemById(resolvedItemId: String): OneDriveDriveItemDto {
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = "${driveBaseRelativeUrl()}/items/${Uri.encode(resolvedItemId)}",
            accessToken = token
        )
        return json.decodeFromString(payload)
    }

    private suspend fun resolveItemByPath(path: String): OneDriveDriveItemDto {
        if (path.isBlank()) {
            val token = authManager.acquireAccessToken(accountIdentifier).accessToken
                ?: throw IOException("OneDrive 访问令牌为空")
            val payload = executeJsonRequest(
                relativeUrl = "${driveBaseRelativeUrl()}/root",
                accessToken = token
            )
            return json.decodeFromString(payload)
        }
        val token = authManager.acquireAccessToken(accountIdentifier).accessToken
            ?: throw IOException("OneDrive 访问令牌为空")
        val payload = executeJsonRequest(
            relativeUrl = buildPathMetadataRelativeUrl(path),
            accessToken = token
        )
        return json.decodeFromString(payload)
    }

    private suspend fun uploadLargeFile(
        accessToken: String,
        bytes: ByteArray,
        headers: Map<String, String>
    ): FileSourceWriteResult {
        val sessionPayload = executeJsonRequest(
            relativeUrl = buildUploadSessionRelativeUrl(),
            accessToken = accessToken,
            method = "POST",
            body = json.encodeToString(
                OneDriveUploadSessionRequestDto.serializer(),
                OneDriveUploadSessionRequestDto()
            ).encodeToByteArray(),
            contentType = "application/json; charset=utf-8",
            headers = headers,
            expectedStatusCodes = setOf(200)
        )
        val session = json.decodeFromString<OneDriveUploadSessionResponseDto>(sessionPayload)
        var offset = 0
        while (offset < bytes.size) {
            val endExclusive = minOf(offset + UPLOAD_CHUNK_SIZE_BYTES, bytes.size)
            val chunk = bytes.copyOfRange(offset, endExclusive)
            val request = Request.Builder()
                .url(session.uploadUrl)
                .header("Content-Range", "bytes $offset-${endExclusive - 1}/${bytes.size}")
                .header("Content-Length", chunk.size.toString())
                .put(chunk.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            sharedHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when (response.code) {
                    200, 201 -> {
                        val item = json.decodeFromString<OneDriveDriveItemDto>(responseBody)
                        return item.toWriteResult()
                    }
                    202 -> {
                        offset = endExclusive
                    }
                    412 -> throw IOException("远端文件已变化，请先重新同步")
                    else -> throw IOException(
                        responseBody.ifBlank { "OneDrive 大文件上传失败: HTTP ${response.code}" }
                    )
                }
            }
        }
        throw IOException("OneDrive 大文件上传未返回最终结果")
    }

    private suspend fun executeJsonRequest(
        relativeUrl: String,
        accessToken: String,
        method: String = "GET",
        body: ByteArray? = null,
        contentType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        expectedStatusCodes: Set<Int> = setOf(200)
    ): String {
        val requestBody = body?.toRequestBody(contentType.toMediaType())
        val requestBuilder = Request.Builder()
            .url("$GRAPH_BASE_URL$relativeUrl")
            .header("Authorization", "Bearer $accessToken")
            .method(method, requestBody)
        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        val request = requestBuilder.build()
        sharedHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code !in expectedStatusCodes) {
                if (response.code == 412) {
                    throw IOException("远端文件已变化，请先重新同步")
                }
                throw IOException(
                    responseBody.ifBlank { "OneDrive 请求失败: HTTP ${response.code}" }
                )
            }
            return responseBody
        }
    }

    private suspend fun executeBytesRequest(
        relativeUrl: String,
        accessToken: String
    ): ByteArray {
        val request = Request.Builder()
            .url("$GRAPH_BASE_URL$relativeUrl")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        sharedHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty().ifBlank {
                    "OneDrive 下载失败: HTTP ${response.code}"
                })
            }
            return response.body?.bytes() ?: throw IOException("OneDrive 返回了空内容")
        }
    }

    private fun buildItemContentRelativeUrl(): String {
        return itemId?.takeIf { it.isNotBlank() }
            ?.let { "${driveBaseRelativeUrl()}/items/${Uri.encode(it)}/content" }
            ?: buildPathContentRelativeUrl(normalizedRemotePath)
    }

    private fun buildUploadSessionRelativeUrl(): String {
        return itemId?.takeIf { it.isNotBlank() }
            ?.let { "${driveBaseRelativeUrl()}/items/${Uri.encode(it)}/createUploadSession" }
            ?: "${driveBaseRelativeUrl()}/root:/${encodePath(normalizedRemotePath)}:/createUploadSession"
    }

    private fun buildChildrenRelativeUrl(path: String): String {
        return if (path.isBlank()) {
            "${driveBaseRelativeUrl()}/root/children"
        } else {
            "${driveBaseRelativeUrl()}/root:/${encodePath(path)}:/children"
        }
    }

    private suspend fun resolveCreateFolderRelativeUrl(parentPath: String): String {
        return if (parentPath.isBlank()) {
            "${driveBaseRelativeUrl()}/root/children"
        } else {
            val parentItem = resolveItemByPath(parentPath)
            "${driveBaseRelativeUrl()}/items/${Uri.encode(parentItem.id)}/children"
        }
    }

    private fun buildPathMetadataRelativeUrl(path: String): String {
        return "${driveBaseRelativeUrl()}/root:/${encodePath(path)}:"
    }

    private fun buildPathContentRelativeUrl(
        path: String,
        conflictBehavior: String? = null
    ): String {
        val base = "${driveBaseRelativeUrl()}/root:/${encodePath(path)}:/content"
        val behavior = conflictBehavior?.trim()?.takeIf { it.isNotBlank() } ?: return base
        return "$base?@microsoft.graph.conflictBehavior=${Uri.encode(behavior)}"
    }

    private fun driveBaseRelativeUrl(): String {
        return if (driveId.isNullOrBlank()) {
            "/me/drive"
        } else {
            "/drives/${Uri.encode(driveId)}"
        }
    }

    private fun requireRemotePath() {
        if (normalizedRemotePath.isBlank()) {
            throw IllegalStateException("未指定 OneDrive 远端文件路径")
        }
    }

    private fun encodePath(path: String): String {
        return normalizeRemotePath(path)
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { segment -> Uri.encode(segment) }
    }

    private fun OneDriveDriveItemDto.toStat(): FileSourceStat {
        return FileSourceStat(
            versionToken = eTag ?: cTag,
            etag = eTag,
            lastModified = lastModifiedDateTime?.toEpochMillis(),
            sizeBytes = size,
            remoteId = id,
            driveId = parentReference?.driveId,
            isDirectory = folder != null,
            displayName = name
        )
    }

    private fun OneDriveDriveItemDto.toWriteResult(): FileSourceWriteResult {
        return FileSourceWriteResult(
            versionToken = eTag ?: cTag,
            etag = eTag,
            lastModified = lastModifiedDateTime?.toEpochMillis(),
            remoteId = id,
            driveId = parentReference?.driveId
        )
    }

    companion object {
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private const val LARGE_UPLOAD_THRESHOLD_BYTES = 2 * 1024 * 1024
        private const val UPLOAD_CHUNK_SIZE_BYTES = 320 * 1024 * 16
        private val sharedHttpClient = OkHttpClient()

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

@Serializable
private data class FolderCreateRequest(
    val name: String,
    val folder: FolderCreateFacet,
    @SerialName("@microsoft.graph.conflictBehavior")
    val conflictBehavior: String
)

@Serializable
private class FolderCreateFacet

@Serializable
private data class RenameItemRequest(
    val name: String
)

private fun String.toEpochMillis(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

object OneDriveKeePassSupport {
    fun createFileSource(
        context: Context,
        source: KeepassRemoteSource
    ): OneDriveKeePassFileSource {
        val accountIdentifier = source.tokenRef?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("OneDrive 账户引用不能为空")
        return OneDriveKeePassFileSource(
            context = context,
            accountIdentifier = accountIdentifier,
            driveId = source.driveId,
            itemId = source.itemId,
            remotePath = source.remotePath
        )
    }

    fun buildLocalMirrorPaths(sourceId: Long, remotePath: String): KeePassLocalMirrorPaths {
        val fileName = displayNameFromRemotePath(remotePath)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "remote.kdbx" }
        val baseDir = "keepass_remote/onedrive_$sourceId"
        return KeePassLocalMirrorPaths(
            workingCopyPath = "$baseDir/working_$fileName",
            cacheCopyPath = "$baseDir/cache_$fileName"
        )
    }

    fun displayNameFromRemotePath(remotePath: String): String {
        val normalized = OneDriveKeePassFileSource.normalizeRemotePath(remotePath)
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
