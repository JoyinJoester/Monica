package takagi.ru.monica.repository

import java.io.IOException
import android.database.sqlite.SQLiteDatabaseCorruptException
import uniffi.mdbx_ffi.MdbxFfiException

enum class Mdbx2FailureKind {
    DATABASE_NOT_FOUND,
    FILE_MISSING,
    UNSUPPORTED_SOURCE,
    CREDENTIAL_UNAVAILABLE,
    INVALID_CREDENTIAL,
    CORRUPT_VAULT,
    STORAGE_FAILURE
}

class Mdbx2OperationException(
    val kind: Mdbx2FailureKind,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

internal object Mdbx2ErrorMapper {
    fun databaseNotFound(): Mdbx2OperationException = Mdbx2OperationException(
        kind = Mdbx2FailureKind.DATABASE_NOT_FOUND,
        message = "MDBX2 database record is missing"
    )

    fun fileMissing(): Mdbx2OperationException = Mdbx2OperationException(
        kind = Mdbx2FailureKind.FILE_MISSING,
        message = "MDBX2 database file is missing"
    )

    fun unsupportedSource(): Mdbx2OperationException = Mdbx2OperationException(
        kind = Mdbx2FailureKind.UNSUPPORTED_SOURCE,
        message = "MDBX2 currently supports local databases only"
    )

    fun credentialUnavailable(cause: Throwable): Mdbx2OperationException = Mdbx2OperationException(
        kind = Mdbx2FailureKind.CREDENTIAL_UNAVAILABLE,
        message = "Saved MDBX2 database credentials cannot be read",
        cause = cause
    )

    fun openFailure(cause: Throwable): Mdbx2OperationException {
        if (cause is Mdbx2OperationException) return cause
        val detail = (cause as? MdbxFfiException.Storage)
            ?.detail
            .orEmpty()
            .lowercase()
        val kind = when {
            cause is SQLiteDatabaseCorruptException -> Mdbx2FailureKind.CORRUPT_VAULT

            detail.contains("incorrect credential") ||
                detail.contains("authentication failed") ||
                detail.contains("key is wrong") ||
                detail.contains("decryption failed") -> Mdbx2FailureKind.INVALID_CREDENTIAL

            detail.contains("file is not a database") ||
                detail.contains("database disk image is malformed") ||
                detail.contains("malformed database") ||
                detail.contains("schema creation failed") -> Mdbx2FailureKind.CORRUPT_VAULT

            else -> Mdbx2FailureKind.STORAGE_FAILURE
        }
        val message = when (kind) {
            Mdbx2FailureKind.INVALID_CREDENTIAL -> "Unable to unlock MDBX2 database"
            Mdbx2FailureKind.CORRUPT_VAULT -> "MDBX2 database file is damaged or incompatible"
            else -> "Unable to open MDBX2 database"
        }
        return Mdbx2OperationException(kind, message, cause)
    }

    fun createFailure(cause: Throwable): Mdbx2OperationException = Mdbx2OperationException(
        kind = Mdbx2FailureKind.STORAGE_FAILURE,
        message = "Unable to create MDBX2 database",
        cause = cause
    )
}
