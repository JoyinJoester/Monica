package takagi.ru.monica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordPageAggregateStackDao {

    @Query(
        """
        SELECT * FROM password_page_aggregate_stack_entries
        ORDER BY stack_group_id ASC, stack_order ASC, item_key ASC
        """
    )
    fun observeAll(): Flow<List<PasswordPageAggregateStackEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PasswordPageAggregateStackEntry>)

    @Query(
        """
        SELECT DISTINCT stack_group_id
        FROM password_page_aggregate_stack_entries
        WHERE item_key IN (:itemKeys)
        """
    )
    suspend fun findStackGroupIdsByItemKeys(itemKeys: List<String>): List<String>

    @Query(
        """
        SELECT * FROM password_page_aggregate_stack_entries
        WHERE stack_group_id IN (:stackGroupIds)
        ORDER BY stack_group_id ASC, stack_order ASC, item_key ASC
        """
    )
    suspend fun getByStackGroupIds(stackGroupIds: List<String>): List<PasswordPageAggregateStackEntry>

    @Query(
        """
        SELECT * FROM password_page_aggregate_stack_entries
        ORDER BY stack_group_id ASC, stack_order ASC, item_key ASC
        """
    )
    suspend fun getAll(): List<PasswordPageAggregateStackEntry>

    @Query("DELETE FROM password_page_aggregate_stack_entries WHERE item_key IN (:itemKeys)")
    suspend fun deleteByItemKeys(itemKeys: List<String>)

    @Query("DELETE FROM password_page_aggregate_stack_entries WHERE stack_group_id = :stackGroupId")
    suspend fun deleteByStackGroupId(stackGroupId: String)

    @Transaction
    suspend fun replaceEntriesForKeys(
        itemKeys: List<String>,
        entries: List<PasswordPageAggregateStackEntry>
    ) {
        deleteByItemKeys(itemKeys)
        if (entries.isNotEmpty()) {
            upsertAll(entries)
        }
    }
}
