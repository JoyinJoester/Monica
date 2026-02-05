package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "keepass_group_sync_configs",
    indices = [
        Index(value = ["keepassDatabaseId", "groupPath"], unique = true)
    ]
)
data class KeepassGroupSyncConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keepassDatabaseId: Long,
    val groupPath: String,
    val groupUuid: String? = null,
    @ColumnInfo(name = "bitwarden_vault_id", defaultValue = "NULL")
    val bitwardenVaultId: Long? = null,
    @ColumnInfo(name = "bitwarden_folder_id", defaultValue = "NULL")
    val bitwardenFolderId: String? = null,
    @ColumnInfo(name = "sync_item_types", defaultValue = "NULL")
    val syncItemTypes: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface KeepassGroupSyncConfigDao {

    @Query("SELECT * FROM keepass_group_sync_configs WHERE keepassDatabaseId = :databaseId ORDER BY groupPath ASC")
    fun getByDatabase(databaseId: Long): Flow<List<KeepassGroupSyncConfig>>

    @Query("SELECT * FROM keepass_group_sync_configs WHERE keepassDatabaseId = :databaseId ORDER BY groupPath ASC")
    suspend fun getByDatabaseSync(databaseId: Long): List<KeepassGroupSyncConfig>

    @Query("SELECT * FROM keepass_group_sync_configs WHERE keepassDatabaseId = :databaseId AND groupPath = :groupPath LIMIT 1")
    suspend fun getByPath(databaseId: Long, groupPath: String): KeepassGroupSyncConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: KeepassGroupSyncConfig): Long

    @Query("DELETE FROM keepass_group_sync_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM keepass_group_sync_configs WHERE keepassDatabaseId = :databaseId AND groupPath = :groupPath")
    suspend fun deleteByPath(databaseId: Long, groupPath: String)

    @Query("UPDATE keepass_group_sync_configs SET bitwarden_vault_id = NULL, bitwarden_folder_id = NULL, sync_item_types = NULL, updated_at = :updatedAt WHERE keepassDatabaseId = :databaseId AND groupPath = :groupPath")
    suspend fun unlink(databaseId: Long, groupPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE keepass_group_sync_configs SET sync_item_types = :syncTypes, updated_at = :updatedAt WHERE keepassDatabaseId = :databaseId AND groupPath = :groupPath")
    suspend fun updateSyncTypes(databaseId: Long, groupPath: String, syncTypes: String?, updatedAt: Long = System.currentTimeMillis())
}