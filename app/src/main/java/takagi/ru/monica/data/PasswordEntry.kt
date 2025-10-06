package takagi.ru.monica.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Password entry entity for Room database
 */
@Entity(tableName = "password_entries")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val website: String,
    val username: String,
    val password: String, // This will be encrypted
    val notes: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0, // 排序顺序(用于拖动排序)
    val isGroupCover: Boolean = false // 是否作为分组封面
)