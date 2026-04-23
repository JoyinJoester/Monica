package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class KeePassRemoteProviderType {
    WEBDAV,
    ONEDRIVE,
    GOOGLE_DRIVE
}

@Entity(
    tableName = "keepass_remote_sources",
    indices = [
        Index(value = ["provider_type"]),
        Index(value = ["display_name"])
    ]
)
data class KeepassRemoteSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "provider_type")
    val providerType: KeePassRemoteProviderType,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "remote_parent_path")
    val remoteParentPath: String? = null,
    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    @ColumnInfo(name = "drive_id")
    val driveId: String? = null,
    @ColumnInfo(name = "item_id")
    val itemId: String? = null,
    @ColumnInfo(name = "username_encrypted")
    val usernameEncrypted: String? = null,
    @ColumnInfo(name = "password_encrypted")
    val passwordEncrypted: String? = null,
    @ColumnInfo(name = "token_ref")
    val tokenRef: String? = null,
    @ColumnInfo(name = "allow_metered_network")
    val allowMeteredNetwork: Boolean = false,
    @ColumnInfo(name = "auto_sync_enabled")
    val autoSyncEnabled: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface KeepassRemoteSourceDao {
    @Query("SELECT * FROM keepass_remote_sources ORDER BY updated_at DESC, id DESC")
    fun getAllSources(): Flow<List<KeepassRemoteSource>>

    @Query("SELECT * FROM keepass_remote_sources ORDER BY updated_at DESC, id DESC")
    suspend fun getAllSourcesSync(): List<KeepassRemoteSource>

    @Query("SELECT * FROM keepass_remote_sources WHERE id = :id")
    suspend fun getSourceById(id: Long): KeepassRemoteSource?

    @Query("SELECT * FROM keepass_remote_sources WHERE provider_type = :providerType ORDER BY updated_at DESC, id DESC")
    fun getSourcesByProvider(providerType: KeePassRemoteProviderType): Flow<List<KeepassRemoteSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: KeepassRemoteSource): Long

    @Update
    suspend fun updateSource(source: KeepassRemoteSource)

    @Delete
    suspend fun deleteSource(source: KeepassRemoteSource)

    @Query("DELETE FROM keepass_remote_sources WHERE id = :id")
    suspend fun deleteSourceById(id: Long)
}
