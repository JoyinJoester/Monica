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

@Entity(
    tableName = "mdbx_remote_sources",
    indices = [
        Index(value = ["display_name"])
    ]
)
data class MdbxRemoteSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "remote_path")
    val remotePath: String,

    @ColumnInfo(name = "remote_parent_path")
    val remoteParentPath: String? = null,

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,

    @ColumnInfo(name = "username_encrypted")
    val usernameEncrypted: String? = null,

    @ColumnInfo(name = "password_encrypted")
    val passwordEncrypted: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MdbxRemoteSourceDao {

    @Query("SELECT * FROM mdbx_remote_sources ORDER BY updated_at DESC, id DESC")
    fun getAllSources(): Flow<List<MdbxRemoteSource>>

    @Query("SELECT * FROM mdbx_remote_sources WHERE id = :id")
    suspend fun getSourceById(id: Long): MdbxRemoteSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: MdbxRemoteSource): Long

    @Update
    suspend fun updateSource(source: MdbxRemoteSource)

    @Delete
    suspend fun deleteSource(source: MdbxRemoteSource)

    @Query("DELETE FROM mdbx_remote_sources WHERE id = :id")
    suspend fun deleteSourceById(id: Long)
}
