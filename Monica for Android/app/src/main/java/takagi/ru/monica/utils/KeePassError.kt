package takagi.ru.monica.utils

import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

enum class KeePassErrorCode {
    LEGACY_KDB_UNSUPPORTED,
    FORMAT_UNSUPPORTED,
    INVALID_CREDENTIAL,
    URI_PERMISSION_DENIED,
    KDF_MEMORY_INSUFFICIENT,
    IO_READ_WRITE_FAILED
}

class KeePassOperationException(
    val code: KeePassErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

fun Throwable.toKeePassOperationException(): KeePassOperationException {
    if (this is KeePassOperationException) return this

    val root = rootCause()
    val lowerMessage = (root.message ?: message ?: "").lowercase(Locale.ROOT)

    fun wrap(code: KeePassErrorCode, userMessage: String): KeePassOperationException {
        return KeePassOperationException(code = code, message = userMessage, cause = this)
    }

    if (root is SecurityException || lowerMessage.contains("permission denied") || lowerMessage.contains("eacces")) {
        return wrap(
            code = KeePassErrorCode.URI_PERMISSION_DENIED,
            userMessage = "文件权限不足，请重新授予数据库或密钥文件访问权限"
        )
    }

    if (root is OutOfMemoryError ||
        (lowerMessage.contains("argon2") && lowerMessage.contains("memory")) ||
        lowerMessage.contains("outofmemory")
    ) {
        return wrap(
            code = KeePassErrorCode.KDF_MEMORY_INSUFFICIENT,
            userMessage = "KDF 内存参数过高，设备内存不足，请降低内存占用或并行度"
        )
    }

    if (root is CryptoError.InvalidKey ||
        lowerMessage.contains("wrong key used for decryption") ||
        lowerMessage.contains("invalid credentials")
    ) {
        return wrap(
            code = KeePassErrorCode.INVALID_CREDENTIAL,
            userMessage = "数据库密码或密钥文件不正确"
        )
    }

    if (lowerMessage.contains("legacy kdb") ||
        lowerMessage.contains(".kdb (v1") ||
        lowerMessage.contains("keepass 1.x")
    ) {
        return wrap(
            code = KeePassErrorCode.LEGACY_KDB_UNSUPPORTED,
            userMessage = "检测到旧版 .kdb（KeePass 1.x）数据库，当前仅支持 .kdbx。请先在 KeePassDX/KeePassXC 中另存为 .kdbx 后再导入。"
        )
    }

    if (root is FormatError.UnsupportedVersion ||
        root is FormatError.UnknownFormat ||
        root is FormatError.InvalidHeader ||
        root is FormatError.InvalidContent ||
        root is FormatError.InvalidXml ||
        root is FormatError.FailedCompression ||
        lowerMessage.contains("unsupported cipher id") ||
        lowerMessage.contains("unsupported header field") ||
        lowerMessage.contains("unknown format")
    ) {
        return wrap(
            code = KeePassErrorCode.FORMAT_UNSUPPORTED,
            userMessage = "数据库格式不支持或文件已损坏"
        )
    }

    if (root is FileNotFoundException || root is IOException) {
        return wrap(
            code = KeePassErrorCode.IO_READ_WRITE_FAILED,
            userMessage = "读取或写入 KeePass 文件失败"
        )
    }

    return wrap(
        code = KeePassErrorCode.IO_READ_WRITE_FAILED,
        userMessage = root.message?.takeIf { it.isNotBlank() } ?: "KeePass 操作失败"
    )
}

fun Throwable.rootCause(): Throwable {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}
