package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Historical password snapshot for a password entry.
 *
 * The password value is stored encrypted, following the same rule as the
 * current password field on [PasswordEntry].
 */
@Entity(
    tableName = "password_history_entries",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entry_id"]),
        Index(value = ["entry_id", "last_used_at"])
    ]
)
data class PasswordHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    val password: String,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Date = Date()
)
