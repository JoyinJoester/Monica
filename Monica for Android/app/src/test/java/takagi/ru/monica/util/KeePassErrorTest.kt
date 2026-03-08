package takagi.ru.monica.util

import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import takagi.ru.monica.utils.KeePassErrorCode
import takagi.ru.monica.utils.KeePassOperationException
import takagi.ru.monica.utils.toKeePassOperationException
import java.io.IOException

class KeePassErrorTest {

    @Test
    fun invalidKey_mapsToInvalidCredential() {
        val ex = CryptoError.InvalidKey("Wrong key used for decryption.")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.INVALID_CREDENTIAL, ex.code)
    }

    @Test
    fun unsupportedVersion_mapsToFormatUnsupported() {
        val ex = FormatError.UnsupportedVersion("File version is not supported.")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.FORMAT_UNSUPPORTED, ex.code)
    }

    @Test
    fun securityException_mapsToPermissionDenied() {
        val ex = SecurityException("Permission denied")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.URI_PERMISSION_DENIED, ex.code)
    }

    @Test
    fun outOfMemory_mapsToKdfMemoryInsufficient() {
        val ex = OutOfMemoryError("Argon2 memory")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.KDF_MEMORY_INSUFFICIENT, ex.code)
    }

    @Test
    fun ioException_mapsToReadWriteFailed() {
        val ex = IOException("Disk error")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.IO_READ_WRITE_FAILED, ex.code)
    }

    @Test
    fun legacyKdbMessage_mapsToLegacyUnsupported() {
        val ex = IllegalStateException("legacy kdb file is not supported")
            .toKeePassOperationException()
        assertEquals(KeePassErrorCode.LEGACY_KDB_UNSUPPORTED, ex.code)
    }

    @Test
    fun mappedException_keepsOriginalInstance() {
        val original = KeePassOperationException(
            KeePassErrorCode.INVALID_CREDENTIAL,
            "数据库密码或密钥文件不正确"
        )
        val mapped = original.toKeePassOperationException()
        assertSame(original, mapped)
    }
}
