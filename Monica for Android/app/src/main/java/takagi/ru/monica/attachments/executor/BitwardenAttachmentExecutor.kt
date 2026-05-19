package takagi.ru.monica.attachments.executor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import takagi.ru.monica.attachments.crypto.BitwardenAttachmentCrypto
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentError
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.bitwarden.api.AttachmentUploadRequest
import takagi.ru.monica.bitwarden.api.AttachmentUploadResponse
import takagi.ru.monica.bitwarden.api.BitwardenVaultApi
import takagi.ru.monica.bitwarden.api.CipherAttachmentApiData
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.UUID

/**
 * Bitwarden 附件读写执行器。
 *
 * 该 executor 是"薄"的：自身不查 Room，不决定 URL，只负责三件事：
 *
 * 1. `upload`：对给定 [InputStream] 按 Bitwarden 附件格式加密并上传到服务端
 *    （fileUploadType 0 → Azure PUT；fileUploadType 1 → Direct Multipart），
 *    返回可直接落库的 [Attachment]（包含本地缓存密文的 wrappedCek）。
 * 2. `download`：把远端附件密文流式拉到本地，用 Bitwarden 附件密钥解码后，
 *    再用 Monica 的 [AttachmentStorage] 格式重新加密存入 Local_Encrypted_Store。
 * 3. `remove`：调用 DELETE 接口。
 *
 * Upstream（Coordinator）要负责传入：
 * - 已经解包好的 `cipherOrUserKey`（用于包裹/解包附件密钥）；
 * - `accessToken`（用于 Authorization）；
 * - `vaultApi`、`httpClient`（由 `BitwardenApiManager` 提供）。
 *
 * 对应 requirements.md Requirement 5.3 / 5.4 / 5.6。
 */
class BitwardenAttachmentExecutor(
    private val context: Context,
    private val storage: AttachmentStorage,
    private val keyVault: AttachmentKeyVault
) {

    data class UploadContext(
        val vaultApi: BitwardenVaultApi,
        val httpClient: OkHttpClient,
        val accessToken: String,
        val cipherId: String,
        /** 用于包裹/解包附件密钥的 cipher 或 user key。 */
        val wrappingKey: SymmetricCryptoKey
    )

    /**
     * 把 [source] 的明文字节加密上传为 [cipherId] 的新附件，并在本地写一份缓存密文。
     *
     * 返回的 [Attachment] `id=0`，由调用方写 Room。上传失败时抛出 [AttachmentError]，
     * 本地临时文件会被清理。
     */
    suspend fun upload(
        parentPasswordId: Long,
        fileName: String,
        mimeType: String,
        source: InputStream,
        sizeBytes: Long,
        ctx: UploadContext
    ): Attachment = withContext(Dispatchers.IO) {
        // 1. 生成附件独立密钥并用 cipherKey 包裹
        val (attachmentKey, fileKeyEnc) =
            BitwardenAttachmentCrypto.generateAndWrapAttachmentKey(ctx.wrappingKey)

        val ciphertextTmp = File.createTempFile("bw_att_", ".bin", context.applicationContext.cacheDir)
        val plainResult = try {
            ciphertextTmp.outputStream().buffered().use { out ->
                BitwardenAttachmentCrypto.encryptStream(source, out, attachmentKey)
            }
        } catch (e: Throwable) {
            attachmentKey.clear()
            ciphertextTmp.delete()
            throw e
        }

        val encryptedFileName = BitwardenAttachmentCrypto.encryptStringForAttachment(fileName, ctx.wrappingKey)
        val ciphertextSize = ciphertextTmp.length()

        // 2. 请求上传 URL
        val uploadResp = try {
            ctx.vaultApi.createAttachmentUploadUrl(
                authorization = bearer(ctx.accessToken),
                cipherId = ctx.cipherId,
                request = AttachmentUploadRequest(
                    key = fileKeyEnc,
                    fileName = encryptedFileName,
                    fileSize = ciphertextSize.toString()
                )
            )
        } catch (e: IOException) {
            attachmentKey.clear()
            ciphertextTmp.delete()
            throw AttachmentError.NetworkError(null)
        }
        if (!uploadResp.isSuccessful) {
            attachmentKey.clear()
            ciphertextTmp.delete()
            throw AttachmentError.NetworkError(uploadResp.code())
        }
        val uploadMeta = uploadResp.body()
            ?: run {
                attachmentKey.clear()
                ciphertextTmp.delete()
                throw AttachmentError.NetworkError(uploadResp.code())
            }
        val attachmentId = resolveAttachmentId(uploadMeta)
            ?: run {
                attachmentKey.clear()
                ciphertextTmp.delete()
                throw AttachmentError.NetworkError(uploadResp.code())
            }

        // 3. 按 fileUploadType 分派上传方式
        try {
            when (uploadMeta.fileUploadType) {
                FILE_UPLOAD_TYPE_DIRECT -> uploadDirect(
                    ctx = ctx,
                    cipherId = ctx.cipherId,
                    attachmentId = attachmentId,
                    fileKeyEnc = fileKeyEnc,
                    ciphertextFile = ciphertextTmp
                )
                else -> {
                    val url = uploadMeta.url
                        ?: throw AttachmentError.NetworkError(null)
                    uploadAzure(
                        httpClient = ctx.httpClient,
                        url = url,
                        ciphertextFile = ciphertextTmp
                    )
                }
            }
        } catch (e: AttachmentError) {
            attachmentKey.clear()
            ciphertextTmp.delete()
            throw e
        } catch (e: IOException) {
            attachmentKey.clear()
            ciphertextTmp.delete()
            throw AttachmentError.NetworkError(null)
        }

        // 4. 用 Monica 的 Local_Encrypted_Store 保存一份本地缓存（便于 offline 预览）
        val localBlob = try {
            ciphertextTmp.inputStream().use { readBack ->
                // 上面已经写的是 Bitwarden 格式，为了让本地缓存与 LocalAttachmentExecutor 一致，
                // 这里重新用"明文 → Monica GCM 密文"路径。我们需要重新解密 Bitwarden 格式
                // 并通过 AttachmentStorage 加密写入。
                val plainPipe = File.createTempFile("bw_att_plain_", ".bin", context.applicationContext.cacheDir)
                try {
                    plainPipe.outputStream().buffered().use { plainOut ->
                        BitwardenAttachmentCrypto.decryptStream(readBack, plainOut, attachmentKey)
                    }
                    plainPipe.inputStream().use { plainIn ->
                        storage.writeEncrypted(plainIn)
                    }
                } finally {
                    plainPipe.delete()
                }
            }
        } finally {
            attachmentKey.clear()
            ciphertextTmp.delete()
        }

        val wrappedLocalCek = try {
            keyVault.wrap(localBlob.cek)
        } catch (e: Throwable) {
            runCatching { storage.delete(localBlob.relativePath) }
            throw AttachmentError.CryptoError
        } finally {
            localBlob.cek.fill(0)
        }

        val now = System.currentTimeMillis()
        Attachment(
            id = 0,
            parentPasswordId = parentPasswordId,
            source = AttachmentSource.BITWARDEN.name,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = plainResult.plainSizeBytes.takeIf { it > 0 } ?: sizeBytes,
            sha256Hex = plainResult.plainSha256Hex,
            wrappedCek = wrappedLocalCek,
            localPath = localBlob.relativePath,
            bitwardenAttachmentId = attachmentId,
            bitwardenUrl = uploadMeta.cipherResponse
                ?.attachments
                ?.firstOrNull { it.id == attachmentId }
                ?.url,
            bitwardenFileKeyEnc = fileKeyEnc,
            downloadState = AttachmentDownloadState.DOWNLOADED.name,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * 从 [remote] 取字节到本地 Local_Encrypted_Store，返回新 [Attachment] 副本（`id`/时间戳等
     * 由上层保留原记录，一般调用方只需要 `localPath`/`wrappedCek`/`sha256Hex`/`downloadState` 的更新）。
     */
    suspend fun download(
        existing: Attachment,
        remote: CipherAttachmentApiData,
        vaultApi: BitwardenVaultApi,
        httpClient: OkHttpClient,
        accessToken: String,
        cipherId: String,
        wrappingKey: SymmetricCryptoKey
    ): Attachment = withContext(Dispatchers.IO) {
        val fileKeyEnc = remote.key ?: existing.bitwardenFileKeyEnc
            ?: throw AttachmentError.CryptoError
        val attachmentKey = BitwardenAttachmentCrypto.unwrapAttachmentKey(fileKeyEnc, wrappingKey)

        val downloadUrl = remote.url ?: run {
            val info = try {
                vaultApi.getAttachmentDownload(
                    authorization = bearer(accessToken),
                    cipherId = cipherId,
                    attachmentId = remote.id
                )
            } catch (e: IOException) {
                attachmentKey.clear()
                throw AttachmentError.NetworkError(null)
            }
            if (!info.isSuccessful) {
                attachmentKey.clear()
                throw AttachmentError.NetworkError(info.code())
            }
            info.body()?.url ?: run {
                attachmentKey.clear()
                throw AttachmentError.NetworkError(info.code())
            }
        }

        val ciphertextTmp = File.createTempFile("bw_dl_", ".bin", context.applicationContext.cacheDir)
        try {
            val request = Request.Builder().url(downloadUrl).get().build()
            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw AttachmentError.NetworkError(null)
            }
            response.use { resp ->
                if (!resp.isSuccessful) throw AttachmentError.NetworkError(resp.code)
                val body = resp.body ?: throw AttachmentError.NetworkError(resp.code)
                body.byteStream().use { inStream ->
                    ciphertextTmp.outputStream().buffered().use { out ->
                        inStream.copyTo(out)
                    }
                }
            }

            // 解密为明文后，按 Monica 格式重新加密存入本地密文库
            val plainPipe = File.createTempFile("bw_dl_plain_", ".bin", context.applicationContext.cacheDir)
            val (blob, hash) = try {
                ciphertextTmp.inputStream().buffered().use { ctIn ->
                    plainPipe.outputStream().buffered().use { plainOut ->
                        BitwardenAttachmentCrypto.decryptStream(ctIn, plainOut, attachmentKey)
                    }
                }
                val stored = plainPipe.inputStream().use { storage.writeEncrypted(it) }
                stored to stored.sha256Hex
            } finally {
                plainPipe.delete()
            }

            val wrappedLocalCek = try {
                keyVault.wrap(blob.cek)
            } catch (e: Throwable) {
                runCatching { storage.delete(blob.relativePath) }
                throw AttachmentError.CryptoError
            } finally {
                blob.cek.fill(0)
            }

            existing.copy(
                localPath = blob.relativePath,
                wrappedCek = wrappedLocalCek,
                sha256Hex = hash,
                sizeBytes = blob.sizeBytes.takeIf { it > 0 } ?: existing.sizeBytes,
                bitwardenFileKeyEnc = fileKeyEnc,
                downloadState = AttachmentDownloadState.DOWNLOADED.name,
                updatedAt = System.currentTimeMillis()
            )
        } finally {
            attachmentKey.clear()
            ciphertextTmp.delete()
        }
    }

    /** 调用 DELETE 接口从服务端移除附件；成功返回 true，404 视为已不存在也返回 true。 */
    suspend fun remove(
        vaultApi: BitwardenVaultApi,
        accessToken: String,
        cipherId: String,
        bitwardenAttachmentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val resp = try {
            vaultApi.deleteAttachment(
                authorization = bearer(accessToken),
                cipherId = cipherId,
                attachmentId = bitwardenAttachmentId
            )
        } catch (e: IOException) {
            throw AttachmentError.NetworkError(null)
        }
        when (resp.code()) {
            HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NO_CONTENT -> true
            HttpURLConnection.HTTP_NOT_FOUND -> true
            else -> throw AttachmentError.NetworkError(resp.code())
        }
    }

    // ---------------------------------------------------------------- 辅助

    private fun resolveAttachmentId(meta: AttachmentUploadResponse): String? {
        meta.attachmentId?.takeIf { it.isNotBlank() }?.let { return it }
        val newOnes = meta.cipherResponse?.attachments ?: return null
        return newOnes.maxByOrNull { it.id }?.id
    }

    private suspend fun uploadAzure(
        httpClient: OkHttpClient,
        url: String,
        ciphertextFile: File
    ) = withContext(Dispatchers.IO) {
        val body = ciphertextFile.asRequestBody(OCTET.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("x-ms-blob-type", "BlockBlob")
            .build()
        val resp = httpClient.newCall(request).execute()
        resp.use {
            if (!it.isSuccessful) throw AttachmentError.NetworkError(it.code)
        }
    }

    private suspend fun uploadDirect(
        ctx: UploadContext,
        cipherId: String,
        attachmentId: String,
        fileKeyEnc: String,
        ciphertextFile: File
    ) = withContext(Dispatchers.IO) {
        val keyPart = MultipartBody.Part.createFormData("key", fileKeyEnc)
        val dataBody = ciphertextFile.asRequestBody(OCTET.toMediaTypeOrNull())
        val dataPart = MultipartBody.Part.createFormData(
            "data",
            "blob",
            dataBody
        )
        val resp = ctx.vaultApi.uploadAttachmentDirect(
            authorization = bearer(ctx.accessToken),
            cipherId = cipherId,
            attachmentId = attachmentId,
            key = keyPart,
            data = dataPart
        )
        if (!resp.isSuccessful) throw AttachmentError.NetworkError(resp.code())
    }

    private fun bearer(token: String): String = "Bearer $token"

    companion object {
        private const val FILE_UPLOAD_TYPE_DIRECT = 0
        private const val FILE_UPLOAD_TYPE_AZURE = 1
        private const val OCTET = "application/octet-stream"

        /** 便于调用方构造上传返回值时生成稳定的临时 attachmentId（若服务端没给）。 */
        fun placeholderAttachmentId(): String = UUID.randomUUID().toString()
    }
}
