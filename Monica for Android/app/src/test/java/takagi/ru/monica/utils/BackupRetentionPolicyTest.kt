package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Date

class BackupRetentionPolicyTest {
    @Test
    fun cleanupKeepsNewestTemporaryBackupsEvenWhenAllAreOlderThanRetentionWindow() {
        val now = 1_800_000_000_000L
        val oldBackups = (1..12).map { index ->
            backup(
                name = "backup_$index.zip",
                modifiedMillis = now - (60L + index) * DAY_MILLIS
            )
        }

        val toDelete = BackupRetentionPolicy.expiredTemporaryBackupsToDelete(
            backups = oldBackups,
            nowMillis = now,
            minTemporaryBackupsToKeep = 10
        )

        assertEquals(listOf("backup_11.zip", "backup_12.zip"), toDelete.map { it.name })
    }

    @Test
    fun cleanupNeverDeletesPermanentOrUnknownTimestampBackups() {
        val now = 1_800_000_000_000L
        val permanent = backup("old_permanent.zip", now - 120L * DAY_MILLIS)
        val unknownTimestamp = backup("unknown.zip", 0L)
        val oldTemporary = backup("old.zip", now - 120L * DAY_MILLIS)

        val toDelete = BackupRetentionPolicy.expiredTemporaryBackupsToDelete(
            backups = listOf(permanent, unknownTimestamp, oldTemporary),
            nowMillis = now,
            minTemporaryBackupsToKeep = 0
        )

        assertEquals(listOf("old.zip"), toDelete.map { it.name })
        assertFalse(toDelete.any { it.name == permanent.name || it.name == unknownTimestamp.name })
    }

    private fun backup(name: String, modifiedMillis: Long): BackupFile {
        return BackupFile(
            name = name,
            path = "/$name",
            size = 1024L,
            modified = Date(modifiedMillis)
        )
    }

    private companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
