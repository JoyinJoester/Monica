package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerDao
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations

/**
 * Repository for ledger related data (entries and categories).
 */
class LedgerRepository(
    private val ledgerDao: LedgerDao
) {

    fun observeEntries(): Flow<List<LedgerEntryWithRelations>> = ledgerDao.observeAllEntries()

    fun observeCategories(): Flow<List<LedgerCategory>> = ledgerDao.observeCategories()

    /**
     * Initialize default categories if database is empty
     */
    suspend fun initializeDefaultCategories() {
        val existingCategories = ledgerDao.observeCategories().first()
        if (existingCategories.isEmpty()) {
            // Income categories (收入分类)
            val incomeCategories = listOf(
                LedgerCategory(name = "工资", type = LedgerEntryType.INCOME, iconKey = "salary"),
                LedgerCategory(name = "奖金", type = LedgerEntryType.INCOME, iconKey = "bonus"),
                LedgerCategory(name = "投资收益", type = LedgerEntryType.INCOME, iconKey = "investment"),
                LedgerCategory(name = "兼职", type = LedgerEntryType.INCOME, iconKey = "parttime"),
                LedgerCategory(name = "其他收入", type = LedgerEntryType.INCOME, iconKey = "other")
            )
            
            // Expense categories (支出分类)
            val expenseCategories = listOf(
                LedgerCategory(name = "餐饮", type = LedgerEntryType.EXPENSE, iconKey = "food"),
                LedgerCategory(name = "交通", type = LedgerEntryType.EXPENSE, iconKey = "transport"),
                LedgerCategory(name = "购物", type = LedgerEntryType.EXPENSE, iconKey = "shopping"),
                LedgerCategory(name = "娱乐", type = LedgerEntryType.EXPENSE, iconKey = "entertainment"),
                LedgerCategory(name = "住房", type = LedgerEntryType.EXPENSE, iconKey = "housing"),
                LedgerCategory(name = "医疗", type = LedgerEntryType.EXPENSE, iconKey = "medical"),
                LedgerCategory(name = "教育", type = LedgerEntryType.EXPENSE, iconKey = "education"),
                LedgerCategory(name = "通讯", type = LedgerEntryType.EXPENSE, iconKey = "communication"),
                LedgerCategory(name = "其他支出", type = LedgerEntryType.EXPENSE, iconKey = "other")
            )
            
            (incomeCategories + expenseCategories).forEach { category ->
                ledgerDao.insertCategory(category)
            }
        }
    }

    suspend fun upsertEntry(entry: LedgerEntry): Long {
        val entryId = if (entry.id == 0L) {
            ledgerDao.insertEntry(entry)
        } else {
            ledgerDao.updateEntry(entry)
            entry.id
        }
        // 清空旧标签信息，避免遗留数据
        ledgerDao.replaceEntryTags(entryId, emptyList())
        return entryId
    }

    suspend fun deleteEntry(entry: LedgerEntry) {
        ledgerDao.deleteEntry(entry)
        ledgerDao.deleteEntryTagCrossRefs(entry.id)
    }

    suspend fun upsertCategory(category: LedgerCategory): Long {
        return ledgerDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: LedgerCategory) {
        ledgerDao.deleteCategory(category)
    }
}
