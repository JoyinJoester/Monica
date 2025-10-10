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
    // 注释掉这个有问题的关系映射，因为paymentMethod是String类型，而Asset.id是Long类型
    // @Relation(parentColumn = "paymentMethod", entityColumn = "id")
    // val asset: Asset?,  // 通过paymentMethod(存储assetId)关联到Asset
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