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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class KeePassSyncPhase {
    IDLE,
    COMPARING,
    DOWNLOADING,
    UPLOADING,
    CONFLICT,
    FAILED
}

@Entity(
    tableName = "keepass_remote_sync_states",
    foreignKeys = [
        ForeignKey(
            entity = LocalKeePassDatabase::class,
            parentColumns = ["id"],
            childColumns = ["database_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sync_phase"]),
        Index(value = ["last_failure_at"])
    ]
)
data class KeepassRemoteSyncState(
    @PrimaryKey
    @ColumnInfo(name = "database_id")
    val databaseId: Long,
    @ColumnInfo(name = "remote_version_token")
    val remoteVersionToken: String? = null,
    @ColumnInfo(name = "remote_etag")
    val remoteEtag: String? = null,
    @ColumnInfo(name = "remote_last_modified")
    val remoteLastModified: Long? = null,
    @ColumnInfo(name = "base_hash")
    val baseHash: String? = null,
    @ColumnInfo(name = "working_hash")
    val workingHash: String? = null,
    @ColumnInfo(name = "has_local_changes")
    val hasLocalChanges: Boolean = false,
    @ColumnInfo(name = "has_remote_changes")
    val hasRemoteChanges: Boolean = false,
    @ColumnInfo(name = "sync_phase")
    val syncPhase: KeePassSyncPhase = KeePassSyncPhase.IDLE,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "last_failure_at")
    val lastFailureAt: Long? = null,
    @ColumnInfo(name = "failure_code")
    val failureCode: String? = null,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String? = null,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)

@Dao
interface KeepassRemoteSyncStateDao {
    @Query("SELECT * FROM keepass_remote_sync_states ORDER BY database_id ASC")
    fun getAllStates(): Flow<List<KeepassRemoteSyncState>>

    @Query("SELECT * FROM keepass_remote_sync_states WHERE database_id = :databaseId")
    fun getStateFlow(databaseId: Long): Flow<KeepassRemoteSyncState?>

    @Query("SELECT * FROM keepass_remote_sync_states WHERE database_id = :databaseId")
    suspend fun getState(databaseId: Long): KeepassRemoteSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: KeepassRemoteSyncState)

    @Update
    suspend fun updateState(state: KeepassRemoteSyncState)

    @Query("DELETE FROM keepass_remote_sync_states WHERE database_id = :databaseId")
    suspend fun deleteState(databaseId: Long)
}
