package takagi.ru.monica.lansync

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DeviceSyncPreparationTest {
    @Test fun unknownFailureIdentifiesOperationWithoutRetainingSecrets() {
        val original = IllegalArgumentException("synthetic-private-material")
        val failure = runCatching { preparingDeviceSync("room_folder_42") { throw original } }.exceptionOrNull() as LanSyncException
        assertEquals("room_folder_42", failure.operation)
        assertEquals("IllegalArgumentException", failure.failureKind)
        assertNull(failure.cause)
        assertFalse(failure.stackTraceToString().contains("synthetic-private-material"))
    }
    @Test fun existingNativeOperationAndCancellationRemainIntact() {
        val known = LanSyncException("invalid_passkey_material", "passkey_public_key")
        assertSame(known, runCatching { preparingDeviceSync("room_passkeys") { throw known } }.exceptionOrNull())
        val cancellation = CancellationException("locked")
        assertSame(cancellation, runCatching { preparingDeviceSync("steam_accounts") { throw cancellation } }.exceptionOrNull())
    }
}
