package takagi.ru.monica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassOperationAvailabilityTest {

    @Test
    fun localDatabasesAreAlwaysWritable() {
        val database = LocalKeePassDatabase(
            name = "Local",
            filePath = "local.kdbx",
            sourceType = KeePassDatabaseSourceType.LOCAL_DOCUMENT_URI,
            lastSyncStatus = KeePassSyncStatus.FAILED
        )

        assertTrue(database.writeOperationAvailability().canOperate)
    }

    @Test
    fun remoteDatabaseWithoutLocalCopyRequiresManualRefresh() {
        val database = remoteDatabase(
            workingCopyPath = null,
            cacheCopyPath = null,
            isOfflineAvailable = false,
            lastSyncStatus = KeePassSyncStatus.IN_SYNC
        )

        val availability = database.writeOperationAvailability()

        assertFalse(availability.canOperate)
        assertEquals(KeePassOperationBlockReason.NEEDS_REFRESH, availability.reason)
    }

    @Test
    fun remoteDatabaseAllowsWritableSyncedLocalCopies() {
        val inSync = remoteDatabase(lastSyncStatus = KeePassSyncStatus.IN_SYNC)
        val pendingUpload = remoteDatabase(lastSyncStatus = KeePassSyncStatus.PENDING_UPLOAD)

        assertTrue(inSync.writeOperationAvailability().canOperate)
        assertTrue(pendingUpload.writeOperationAvailability().canOperate)
    }

    @Test
    fun remoteDatabaseBlocksUnsafeSyncStates() {
        val blockedStates = mapOf(
            KeePassSyncStatus.LOCAL_ONLY to KeePassOperationBlockReason.NEEDS_REFRESH,
            KeePassSyncStatus.REMOTE_CHANGED to KeePassOperationBlockReason.NEEDS_REFRESH,
            KeePassSyncStatus.SYNCING to KeePassOperationBlockReason.SYNCING,
            KeePassSyncStatus.CONFLICT to KeePassOperationBlockReason.CONFLICT,
            KeePassSyncStatus.FAILED to KeePassOperationBlockReason.FAILED
        )

        blockedStates.forEach { (status, reason) ->
            val availability = remoteDatabase(lastSyncStatus = status).writeOperationAvailability()

            assertFalse("Expected $status to block KeePass writes", availability.canOperate)
            assertEquals(reason, availability.reason)
        }
    }

    private fun remoteDatabase(
        workingCopyPath: String? = "working.kdbx",
        cacheCopyPath: String? = null,
        isOfflineAvailable: Boolean = true,
        lastSyncStatus: KeePassSyncStatus
    ): LocalKeePassDatabase {
        return LocalKeePassDatabase(
            name = "Remote",
            filePath = "remote.kdbx",
            sourceType = KeePassDatabaseSourceType.REMOTE_ONEDRIVE,
            workingCopyPath = workingCopyPath,
            cacheCopyPath = cacheCopyPath,
            isOfflineAvailable = isOfflineAvailable,
            lastSyncStatus = lastSyncStatus
        )
    }
}
