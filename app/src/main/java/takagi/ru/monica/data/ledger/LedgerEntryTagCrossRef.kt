package takagi.ru.monica.data.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 记账条目与标签关联表
 */
@Entity(
    tableName = "ledger_entry_tag_cross_ref",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        ),
        ForeignKey(
            entity = LedgerTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class LedgerEntryTagCrossRef(
    val entryId: Long,
    val tagId: Long
)
