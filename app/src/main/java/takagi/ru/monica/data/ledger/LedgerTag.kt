package takagi.ru.monica.data.ledger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记账标签
 */
@Entity(
    tableName = "ledger_tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class LedgerTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#4DD0E1"
)
