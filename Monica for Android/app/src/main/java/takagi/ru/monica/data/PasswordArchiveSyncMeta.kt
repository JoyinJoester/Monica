package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Password archive sync metadata.
 *
 * Tracks how archive/unarchive should be adapted for each provider and where an
 * entry should be restored when archive is cancelled.
 */
@Entity(
    tableName = "password_archive_sync_meta",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["provider_type"]),
        Index(value = ["sync_status"])
    ]
)
data class PasswordArchiveSyncMeta(
    @PrimaryKey
    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "provider_type", defaultValue = PROVIDER_LOCAL)
    val providerType: String = PROVIDER_LOCAL,

    @ColumnInfo(name = "origin_keepass_database_id", defaultValue = "NULL")
    val originKeePassDatabaseId: Long? = null,

    @ColumnInfo(name = "origin_keepass_group_path", defaultValue = "NULL")
    val originKeePassGroupPath: String? = null,

    @ColumnInfo(name = "origin_bitwarden_folder_id", defaultValue = "NULL")
    val originBitwardenFolderId: String? = null,

    @ColumnInfo(name = "sync_status", defaultValue = STATUS_SYNCED)
    val syncStatus: String = STATUS_SYNCED,

    @ColumnInfo(name = "last_error", defaultValue = "NULL")
    val lastError: String? = null,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROVIDER_LOCAL = "LOCAL"
        const val PROVIDER_BITWARDEN_NATIVE = "BITWARDEN_NATIVE"
        const val PROVIDER_BITWARDEN_FOLDER = "BITWARDEN_FOLDER"
        const val PROVIDER_KEEPASS_GROUP = "KEEPASS_GROUP"

        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_FAILED = "FAILED"
    }
}

@Dao
interface PasswordArchiveSyncMetaDao {
    @Query("SELECT * FROM password_archive_sync_meta WHERE entry_id = :entryId LIMIT 1")
    suspend fun getByEntryId(entryId: Long): PasswordArchiveSyncMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PasswordArchiveSyncMeta)

    @Query("DELETE FROM password_archive_sync_meta WHERE entry_id = :entryId")
    suspend fun deleteByEntryId(entryId: Long)
}
