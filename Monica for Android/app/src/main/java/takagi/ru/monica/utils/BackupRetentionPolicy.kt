package takagi.ru.monica.utils

object BackupRetentionPolicy {
    const val DEFAULT_RETENTION_DAYS: Long = 60L
    const val DEFAULT_MIN_TEMPORARY_BACKUPS_TO_KEEP: Int = 10

    fun expiredTemporaryBackupsToDelete(
        backups: List<BackupFile>,
        nowMillis: Long = System.currentTimeMillis(),
        retentionDays: Long = DEFAULT_RETENTION_DAYS,
        minTemporaryBackupsToKeep: Int = DEFAULT_MIN_TEMPORARY_BACKUPS_TO_KEEP
    ): List<BackupFile> {
        val cutoffMillis = nowMillis - retentionDays * 24L * 60L * 60L * 1000L
        return backups
            .filterNot(BackupFile::isPermanent)
            .sortedByDescending { it.modified.time }
            .drop(minTemporaryBackupsToKeep.coerceAtLeast(0))
            .filter { backup ->
                val modifiedMillis = backup.modified.time
                modifiedMillis > 0L && modifiedMillis < cutoffMillis
            }
    }
}
