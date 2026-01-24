package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 本地 KeePass 数据库存储位置
 */
enum class KeePassStorageLocation {
    INTERNAL,  // 内部存储（Monica 应用目录）
    EXTERNAL   // 外部存储（用户选择的位置）
}

/**
 * 本地 KeePass 数据库信息
 */
@Entity(
    tableName = "local_keepass_databases",
    indices = [Index(value = ["storage_location"])]
)
data class LocalKeePassDatabase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 数据库显示名称 */
    val name: String,
    
    /** 文件路径（内部存储时为相对路径，外部存储时为 URI） */
    val filePath: String,
    
    /** 存储位置 */
    @ColumnInfo(name = "storage_location")
    val storageLocation: KeePassStorageLocation = KeePassStorageLocation.INTERNAL,
    
    /** 加密后的主密码（用于自动解锁） */
    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: String? = null,
    
    /** 创建时间 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 最后访问时间 */
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis(),
    
    /** 最后同步时间 */
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
    
    /** 是否为默认数据库 */
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    
    /** 数据库描述 */
    val description: String? = null,
    
    /** 条目数量（缓存） */
    @ColumnInfo(name = "entry_count")
    val entryCount: Int = 0,
    
    /** 排序顺序 */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 本地 KeePass 数据库 DAO
 */
@Dao
interface LocalKeePassDatabaseDao {
    
    @Query("SELECT * FROM local_keepass_databases ORDER BY sort_order ASC, created_at DESC")
    fun getAllDatabases(): Flow<List<LocalKeePassDatabase>>
    
    @Query("SELECT * FROM local_keepass_databases ORDER BY sort_order ASC, created_at DESC")
    fun getAllDatabasesSync(): List<LocalKeePassDatabase>
    
    @Query("SELECT * FROM local_keepass_databases WHERE id = :id")
    suspend fun getDatabaseById(id: Long): LocalKeePassDatabase?
    
    @Query("SELECT * FROM local_keepass_databases WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultDatabase(): LocalKeePassDatabase?
    
    @Query("SELECT * FROM local_keepass_databases WHERE storage_location = :location ORDER BY sort_order ASC")
    fun getDatabasesByLocation(location: KeePassStorageLocation): Flow<List<LocalKeePassDatabase>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatabase(database: LocalKeePassDatabase): Long
    
    @Update
    suspend fun updateDatabase(database: LocalKeePassDatabase)
    
    @Delete
    suspend fun deleteDatabase(database: LocalKeePassDatabase)
    
    @Query("DELETE FROM local_keepass_databases WHERE id = :id")
    suspend fun deleteDatabaseById(id: Long)
    
    @Query("UPDATE local_keepass_databases SET is_default = 0")
    suspend fun clearDefaultDatabase()
    
    @Query("UPDATE local_keepass_databases SET is_default = 1 WHERE id = :id")
    suspend fun setDefaultDatabase(id: Long)
    
    @Query("UPDATE local_keepass_databases SET last_accessed_at = :time WHERE id = :id")
    suspend fun updateLastAccessedTime(id: Long, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE local_keepass_databases SET entry_count = :count WHERE id = :id")
    suspend fun updateEntryCount(id: Long, count: Int)
    
    @Query("UPDATE local_keepass_databases SET storage_location = :location, filePath = :newPath WHERE id = :id")
    suspend fun updateStorageLocation(id: Long, location: KeePassStorageLocation, newPath: String)
}
