package takagi.ru.monica.data.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记账分类
 */
@Entity(
    tableName = "ledger_categories",
    foreignKeys = [
        ForeignKey(
            entity = LedgerCategory::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["type"])
    ]
)
data class LedgerCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: LedgerEntryType? = null,
    val iconKey: String = "general",
    val colorHex: String = "#FFB74D",
    val sortOrder: Int = 0,
    val parentId: Long? = null
)
