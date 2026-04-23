package takagi.ru.monica.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

data class OneDriveBackupConfig(
    val accountId: String,
    val displayName: String,
    val username: String,
    val folderPath: String
)

class OneDriveBackupHelper(context: Context) {
    private val appContext = context.applicationContext
    private val authManager = OneDriveAuthManager(appContext)

    fun getConfig(): OneDriveBackupConfig? {
        val prefs = preferences()
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val folderPath = prefs.getString(KEY_FOLDER_PATH, null)?.takeIf { it.isNotBlank() } ?: return null
        return OneDriveBackupConfig(
            accountId = accountId,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null).orEmpty().ifBlank { "OneDrive" },
            username = prefs.getString(KEY_USERNAME, null).orEmpty(),
            folderPath = folderPath
        )
    }

    fun isConfigured(): Boolean = getConfig() != null

    fun saveConfig(session: OneDriveAccountSession, folderPath: String) {
        val normalizedFolderPath = OneDriveKeePassFileSource.normalizeOptionalRemotePath(folderPath)
        preferences().edit()
            .putString(KEY_ACCOUNT_ID, session.accountId)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_FOLDER_PATH, normalizedFolderPath)
            .apply()
    }

    fun clearConfig() {
        preferences().edit().clear().apply()
    }

    suspend fun getConfiguredSession(): OneDriveAccountSession? {
        val config = getConfig() ?: return null
        return runCatching { authManager.acquireAccessToken(config.accountId) }.getOrNull()
    }

    suspend fun listDirectory(accountId: String, currentPath: String?): List<FileSourceEntry> {
        return OneDriveKeePassFileSource(
            context = appContext,
            accountIdentifier = accountId
        ).listDirectory(currentPath)
    }

    suspend fun createFolder(accountId: String, currentPath: String?, name: String): FileSourceEntry {
        return OneDriveKeePassFileSource(
            context = appContext,
            accountIdentifier = accountId
        ).createDirectory(currentPath, name)
    }

    suspend fun listBackups(): Result<List<BackupFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).listDirectory(config.folderPath)
                .filter { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) }
                .map { entry ->
                    BackupFile(
                        name = entry.name,
                        path = entry.path,
                        size = entry.sizeBytes ?: 0L,
                        modified = Date(entry.lastModified ?: 0L)
                    )
                }
                .sortedByDescending { it.modified.time }
        }
    }

    suspend fun uploadBackup(file: File, isPermanent: Boolean): Result<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val targetName = if (isPermanent) {
                file.name.replace(".zip", "_permanent.zip")
            } else {
                file.name
            }
            val entry = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).createFileInDirectory(
                parentPath = config.folderPath,
                name = targetName,
                bytes = file.readBytes()
            )
            cleanupBackups()
            BackupFile(
                name = entry.name,
                path = entry.path,
                size = entry.sizeBytes ?: file.length(),
                modified = Date(entry.lastModified ?: System.currentTimeMillis())
            )
        }
    }

    suspend fun downloadBackup(backupFile: BackupFile, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val bytes = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId,
                remotePath = backupFile.path
            ).read()
            destFile.writeBytes(bytes)
            destFile
        }
    }

    suspend fun deleteBackup(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).deleteEntry(backupFile.path)
            true
        }
    }

    suspend fun markBackupAsPermanent(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (backupFile.isPermanent) return@runCatching true
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val newName = backupFile.name.replace(".zip", "_permanent.zip")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).renameEntry(backupFile.path, newName)
            true
        }
    }

    suspend fun unmarkPermanent(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!backupFile.isPermanent) return@runCatching true
            val config = getConfig() ?: throw IllegalStateException("尚未配置 OneDrive 备份目录")
            val newName = backupFile.name.replace("_permanent", "")
            OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            ).renameEntry(backupFile.path, newName)
            true
        }
    }

    suspend fun cleanupBackups(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backups = listBackups().getOrThrow()
            val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
            var deleted = 0
            backups.forEach { backup ->
                if (!backup.isPermanent && backup.modified.time < sixtyDaysAgo) {
                    deleteBackup(backup).getOrThrow()
                    deleted++
                }
            }
            deleted
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun preferences() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "onedrive_backup_config"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_FOLDER_PATH = "folder_path"
    }
}
