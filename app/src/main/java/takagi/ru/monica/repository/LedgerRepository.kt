package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.ledger.Asset
import takagi.ru.monica.data.ledger.AssetType
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerDao
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.data.model.BankCardData
import java.util.Date

/**
 * Repository for ledger related data (entries and categories).
 */
class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val secureItemRepository: SecureItemRepository
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
        // 如果是编辑现有条目，先恢复旧的余额
        if (entry.id > 0L) {
            ledgerDao.getEntryById(entry.id)?.let { oldEntry ->
                updateAssetBalanceForEntry(oldEntry.entry, revert = true)
            }
        }

        val entryId = if (entry.id == 0L) {
            ledgerDao.insertEntry(entry)
        } else {
            ledgerDao.updateEntry(entry)
            entry.id
        }
        
        // 更新资产余额
        updateAssetBalanceForEntry(entry, revert = false)
        
        // 清空旧标签信息，避免遗留数据
        ledgerDao.replaceEntryTags(entryId, emptyList())
        return entryId
    }

    suspend fun deleteEntry(entry: LedgerEntry) {
        // 删除时恢复资产余额
        updateAssetBalanceForEntry(entry, revert = true)
        
        ledgerDao.deleteEntry(entry)
        ledgerDao.deleteEntryTagCrossRefs(entry.id)
    }

    suspend fun upsertCategory(category: LedgerCategory): Long {
        return ledgerDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: LedgerCategory) {
        ledgerDao.deleteCategory(category)
    }

    // ===== 资产管理 =====
    fun observeAssets(): Flow<List<Asset>> = ledgerDao.observeAssets()

    fun observeTotalBalance(): Flow<Long?> = ledgerDao.observeTotalBalance()

    suspend fun getAssetById(id: Long): Asset? = ledgerDao.getAssetById(id)

    suspend fun upsertAsset(asset: Asset): Long {
        return if (asset.id == 0L) {
            ledgerDao.insertAsset(asset)
        } else {
            ledgerDao.updateAsset(asset)
            asset.id
        }
    }

    suspend fun deleteAsset(asset: Asset) {
        ledgerDao.deleteAsset(asset)
    }

    suspend fun updateAssetBalance(assetId: Long, amountInCents: Long) {
        ledgerDao.updateAssetBalance(assetId, amountInCents)
    }

    /**
     * 初始化默认资产
     */
    suspend fun initializeDefaultAssets() {
        // 首先清理重复的银行卡资产
        try {
            ledgerDao.deleteDuplicateBankCardAssets()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val existingAssets = ledgerDao.observeAssets().first()
        if (existingAssets.isEmpty()) {
            val defaultAssets = listOf(
                Asset(
                    name = "微信支付",
                    assetType = AssetType.WECHAT,
                    iconKey = "wechat",
                    colorHex = "#07A658",
                    sortOrder = 0
                ),
                Asset(
                    name = "支付宝",
                    assetType = AssetType.ALIPAY,
                    iconKey = "alipay",
                    colorHex = "#1296DB",
                    sortOrder = 1
                ),
                Asset(
                    name = "云闪付",
                    assetType = AssetType.UNIONPAY,
                    iconKey = "unionpay",
                    colorHex = "#D93026",
                    sortOrder = 2
                ),
                Asset(
                    name = "现金",
                    assetType = AssetType.CASH,
                    iconKey = "cash",
                    colorHex = "#F57C00",
                    sortOrder = 3
                )
            )
            defaultAssets.forEach { asset ->
                ledgerDao.insertAsset(asset)
            }
        }
    }

    /**
     * 根据账单条目更新对应资产的余额
     * @param entry 账单条目
     * @param revert 是否恢复余额（删除或编辑前恢复）
     */
    private suspend fun updateAssetBalanceForEntry(entry: LedgerEntry, revert: Boolean) {
        val paymentMethod = entry.paymentMethod ?: return
        
        // 根据支付方式查找对应的资产
        val asset = when {
            // 尝试作为资产类型查找
            paymentMethod.equals("wechat", ignoreCase = true) || 
            paymentMethod.equals("微信", ignoreCase = true) -> 
                ledgerDao.getAssetByType(AssetType.WECHAT)
            paymentMethod.equals("alipay", ignoreCase = true) || 
            paymentMethod.equals("支付宝", ignoreCase = true) -> 
                ledgerDao.getAssetByType(AssetType.ALIPAY)
            paymentMethod.equals("unionpay", ignoreCase = true) || 
            paymentMethod.equals("云闪付", ignoreCase = true) -> 
                ledgerDao.getAssetByType(AssetType.UNIONPAY)
            paymentMethod.equals("paypal", ignoreCase = true) -> 
                ledgerDao.getAssetByType(AssetType.PAYPAL)
            paymentMethod.equals("cash", ignoreCase = true) || 
            paymentMethod.equals("现金", ignoreCase = true) -> 
                ledgerDao.getAssetByType(AssetType.CASH)
            // 尝试作为银行卡ID查找
            paymentMethod.toLongOrNull() != null -> 
                ledgerDao.getAssetByBankCardId(paymentMethod.toLong())
            else -> null
        } ?: return

        // 计算余额变化量
        val delta = when (entry.type) {
            LedgerEntryType.INCOME -> if (revert) -entry.amountInCents else entry.amountInCents
            LedgerEntryType.EXPENSE -> if (revert) entry.amountInCents else -entry.amountInCents
            LedgerEntryType.TRANSFER -> 0L // 转账暂不处理资产余额
        }

        if (delta != 0L) {
            ledgerDao.updateAssetBalance(asset.id, delta)
        }
    }

    /**
     * 同步银行卡到资产管理
     * 从银行卡数据创建或更新对应的资产条目
     */
    suspend fun syncBankCardsToAssets() {
        try {
            // 获取所有银行卡
            val bankCards = secureItemRepository.getItemsByType(ItemType.BANK_CARD).first()
            val json = Json { ignoreUnknownKeys = true }
            
            // 获取所有已存在的银行卡资产,用于去重
            val existingBankCardAssets = ledgerDao.observeAssets().first()
                .filter { it.assetType == AssetType.BANK_CARD && it.linkedBankCardId != null }
            val existingBankCardIds = existingBankCardAssets.mapNotNull { it.linkedBankCardId }.toSet()
            
            bankCards.forEach { secureItem ->
                try {
                    // 解析银行卡数据
                    val bankCardData = json.decodeFromString<BankCardData>(secureItem.itemData)
                    
                    // 检查是否已存在对应的资产
                    val existingAsset = ledgerDao.getAssetByBankCardId(secureItem.id)
                    
                    if (existingAsset != null) {
                        // 更新现有资产的名称（银行卡名称可能变更）
                        ledgerDao.updateAsset(
                            existingAsset.copy(
                                name = "${bankCardData.bankName} (${secureItem.title})",
                                updatedAt = Date()
                            )
                        )
                    } else if (secureItem.id !in existingBankCardIds) {
                        // 只有在不存在关联的情况下才创建新资产（防止重复）
                        val newAsset = Asset(
                            name = "${bankCardData.bankName} (${secureItem.title})",
                            assetType = AssetType.BANK_CARD,
                            balanceInCents = 0L, // 银行卡余额从账单记录计算
                            iconKey = "bank_card",
                            colorHex = "#4CAF50",
                            linkedBankCardId = secureItem.id,
                            sortOrder = 100 + secureItem.sortOrder // 银行卡资产排在后面
                        )
                        ledgerDao.insertAsset(newAsset)
                    }
                } catch (e: Exception) {
                    // 忽略单个银行卡的解析错误
                    e.printStackTrace()
                }
            }
            
            // 清理已删除的银行卡对应的资产
            val bankCardIds = bankCards.map { it.id }.toSet()
            val allBankCardAssets = ledgerDao.observeAssets().first()
                .filter { it.assetType == AssetType.BANK_CARD && it.linkedBankCardId != null }
            
            allBankCardAssets.forEach { asset ->
                if (asset.linkedBankCardId !in bankCardIds) {
                    ledgerDao.deleteAsset(asset)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
