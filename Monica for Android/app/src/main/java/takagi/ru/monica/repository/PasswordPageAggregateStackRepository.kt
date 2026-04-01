package takagi.ru.monica.repository

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.PasswordPageAggregateStackDao
import takagi.ru.monica.data.PasswordPageAggregateStackEntry

class PasswordPageAggregateStackRepository(
    private val dao: PasswordPageAggregateStackDao
) {

    fun observeAll(): Flow<List<PasswordPageAggregateStackEntry>> = dao.observeAll()

    suspend fun applyManualStack(itemKeys: List<String>): Int {
        val validKeys = itemKeys
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (validKeys.size < 2) return 0

        val impactedGroupIds = dao.findStackGroupIdsByItemKeys(validKeys)
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entries = validKeys.mapIndexed { index, key ->
            PasswordPageAggregateStackEntry(
                itemKey = key,
                stackGroupId = groupId,
                stackOrder = index,
                updatedAt = now
            )
        }
        dao.replaceEntriesForKeys(validKeys, entries)
        cleanupDegenerateGroups(impactedGroupIds)
        return validKeys.size
    }

    suspend fun clearManualStack(itemKeys: List<String>): Int {
        val validKeys = itemKeys
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (validKeys.isEmpty()) return 0
        val impactedGroupIds = dao.findStackGroupIdsByItemKeys(validKeys)
        dao.deleteByItemKeys(validKeys)
        cleanupDegenerateGroups(impactedGroupIds)
        return validKeys.size
    }

    suspend fun pruneDegenerateGroups(): Int {
        val groupIds = dao.getAll()
            .map(PasswordPageAggregateStackEntry::stackGroupId)
            .distinct()
        return cleanupDegenerateGroups(groupIds)
    }

    private suspend fun cleanupDegenerateGroups(stackGroupIds: List<String>): Int {
        val validGroupIds = stackGroupIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (validGroupIds.isEmpty()) return 0

        val remainingEntries = dao.getByStackGroupIds(validGroupIds)
        val existingGroupIds = remainingEntries
            .groupBy(PasswordPageAggregateStackEntry::stackGroupId)
            .filterValues { entries -> entries.size < 2 }
            .keys
        existingGroupIds.forEach { groupId ->
            dao.deleteByStackGroupId(groupId)
        }
        return existingGroupIds.size
    }
}
