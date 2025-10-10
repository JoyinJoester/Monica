package takagi.ru.monica.data.ledger

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Transaction
    @Query("SELECT * FROM ledger_entries ORDER BY occurredAt DESC")
    fun observeAllEntries(): Flow<List<LedgerEntryWithRelations>>

    @Transaction
    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): LedgerEntryWithRelations?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Update
    suspend fun updateEntry(entry: LedgerEntry)

    @Delete
    suspend fun deleteEntry(entry: LedgerEntry)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: LedgerCategory): Long

    @Update
    suspend fun updateCategory(category: LedgerCategory)

    @Delete
    suspend fun deleteCategory(category: LedgerCategory)

    @Query("SELECT * FROM ledger_categories ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<LedgerCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: LedgerTag): Long

    @Update
    suspend fun updateTag(tag: LedgerTag)

    @Delete
    suspend fun deleteTag(tag: LedgerTag)

    @Query("SELECT * FROM ledger_tags ORDER BY name")
    fun observeTags(): Flow<List<LedgerTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryTagCrossRef(crossRef: LedgerEntryTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryTagCrossRefs(crossRefs: List<LedgerEntryTagCrossRef>)

    @Query("DELETE FROM ledger_entry_tag_cross_ref WHERE entryId = :entryId")
    suspend fun deleteEntryTagCrossRefs(entryId: Long)

    @Transaction
    suspend fun replaceEntryTags(entryId: Long, tagIds: List<Long>) {
        deleteEntryTagCrossRefs(entryId)
        if (tagIds.isNotEmpty()) {
            insertEntryTagCrossRefs(tagIds.map { LedgerEntryTagCrossRef(entryId = entryId, tagId = it) })
        }
    }

    // ===== 资产管理 =====
    @Query("SELECT * FROM assets WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: Long): Asset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset): Long

    @Update
    suspend fun updateAsset(asset: Asset)

    @Delete
    suspend fun deleteAsset(asset: Asset)

    @Query("UPDATE assets SET balanceInCents = balanceInCents + :amountInCents WHERE id = :assetId")
    suspend fun updateAssetBalance(assetId: Long, amountInCents: Long)

    @Query("SELECT SUM(balanceInCents) FROM assets WHERE isActive = 1")
    fun observeTotalBalance(): Flow<Long?>

    @Query("SELECT * FROM assets WHERE assetType = :assetType AND isActive = 1 LIMIT 1")
    suspend fun getAssetByType(assetType: AssetType): Asset?

    @Query("SELECT * FROM assets WHERE linkedBankCardId = :bankCardId AND isActive = 1 LIMIT 1")
    suspend fun getAssetByBankCardId(bankCardId: Long): Asset?
    
    @Query("""
        DELETE FROM assets 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM assets 
            WHERE linkedBankCardId IS NOT NULL 
            GROUP BY linkedBankCardId
        ) AND linkedBankCardId IS NOT NULL
    """)
    suspend fun deleteDuplicateBankCardAssets()
}
