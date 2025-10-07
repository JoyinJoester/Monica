package takagi.ru.monica.data.ledger

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import takagi.ru.monica.data.SecureItem

data class LedgerEntryWithRelations(
    @Embedded val entry: LedgerEntry,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: LedgerCategory?,
    @Relation(parentColumn = "linkedItemId", entityColumn = "id")
    val linkedItem: SecureItem?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = LedgerEntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId"
        )
    )
    val tags: List<LedgerTag>
)
