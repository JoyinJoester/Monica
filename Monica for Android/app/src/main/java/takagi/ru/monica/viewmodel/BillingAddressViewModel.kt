package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.asMonicaLocalCopy
import takagi.ru.monica.data.hasOwnershipConflict
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.OperationLogger
import java.util.Date

data class ParsedBillingAddressItem(
    val item: SecureItem,
    val addressData: BillingAddressData
)

class BillingAddressViewModel(
    private val repository: SecureItemRepository,
    private val securityManager: SecurityManager? = null
) : ViewModel() {

    private val safeLogTitle = "账单地址"

    val allBillingAddresses: Flow<List<SecureItem>> =
        repository.getItemsByType(ItemType.BILLING_ADDRESS)

    val parsedBillingAddresses: StateFlow<List<ParsedBillingAddressItem>> = allBillingAddresses
        .map { items ->
            withContext(Dispatchers.Default) {
                items.map { item ->
                    ParsedBillingAddressItem(
                        item = item,
                        addressData = parseAddressData(item.itemData) ?: BillingAddressData()
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getAddressById(id: Long): SecureItem? = repository.getItemById(id)

    fun addAddress(
        title: String,
        addressData: BillingAddressData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        categoryId: Long? = null,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null,
        replicaGroupId: String? = null
    ) {
        viewModelScope.launch {
            val item = SecureItem(
                id = 0,
                itemType = ItemType.BILLING_ADDRESS,
                title = title,
                itemData = encodeAddressDataForLocalStorage(addressData),
                notes = notes,
                isFavorite = isFavorite,
                categoryId = categoryId,
                mdbxDatabaseId = mdbxDatabaseId,
                mdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null,
                replicaGroupId = replicaGroupId,
                imagePaths = imagePaths,
                createdAt = Date(),
                updatedAt = Date()
            )
            val newId = repository.insertItem(item)
            OperationLogger.logCreate(
                itemType = OperationLogItemType.BILLING_ADDRESS,
                itemId = newId,
                itemTitle = safeLogTitle
            )
        }
    }

    fun updateAddress(
        id: Long,
        title: String,
        addressData: BillingAddressData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        categoryId: Long? = null,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null,
        replicaGroupId: String? = null
    ) {
        viewModelScope.launch {
            val existingItem = repository.getItemById(id) ?: return@launch
            val oldData = parseAddressData(existingItem.itemData)
            val changes = buildList {
                if (existingItem.title != title) add(redactedChange("标题"))
                if (existingItem.notes != notes) add(redactedChange("备注"))
                if (oldData?.fullName != addressData.fullName) {
                    add(redactedChange("姓名"))
                }
                if (oldData?.company != addressData.company) {
                    add(redactedChange("公司"))
                }
                if (oldData?.streetAddress != addressData.streetAddress) {
                    add(redactedChange("街道地址"))
                }
                if (oldData?.apartment != addressData.apartment) {
                    add(redactedChange("公寓/单元"))
                }
                if (oldData?.city != addressData.city) {
                    add(redactedChange("城市"))
                }
                if (oldData?.stateProvince != addressData.stateProvince) {
                    add(redactedChange("省/州"))
                }
                if (oldData?.postalCode != addressData.postalCode) {
                    add(redactedChange("邮编"))
                }
                if (oldData?.country != addressData.country) {
                    add(redactedChange("国家"))
                }
                if (oldData?.email != addressData.email) {
                    add(redactedChange("邮箱"))
                }
                if (oldData?.phone != addressData.phone) {
                    add(redactedChange("电话"))
                }
                if (oldData?.isDefault != addressData.isDefault) {
                    add(redactedChange("默认状态"))
                }
                if (oldData?.customFields != addressData.customFields) {
                    add(redactedChange("自定义字段"))
                }
            }

            val updatedItem = existingItem.copy(
                title = title,
                itemData = encodeAddressDataForLocalStorage(addressData),
                notes = notes,
                isFavorite = isFavorite,
                categoryId = categoryId,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                keepassEntryUuid = null,
                keepassGroupUuid = null,
                bitwardenVaultId = null,
                bitwardenCipherId = null,
                bitwardenFolderId = null,
                bitwardenRevisionDate = null,
                bitwardenLocalModified = false,
                syncStatus = "NONE",
                mdbxDatabaseId = mdbxDatabaseId,
                mdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null,
                replicaGroupId = replicaGroupId ?: existingItem.replicaGroupId,
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            repository.updateItem(updatedItem)

            OperationLogger.logUpdate(
                itemType = OperationLogItemType.BILLING_ADDRESS,
                itemId = id,
                itemTitle = safeLogTitle,
                changes = changes.ifEmpty { listOf(redactedChange("更新")) }
            )
        }
    }

    fun deleteAddress(id: Long, softDelete: Boolean = true) {
        viewModelScope.launch {
            val item = repository.getItemById(id) ?: return@launch
            if (softDelete) {
                repository.softDeleteItem(item)
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.BILLING_ADDRESS,
                    itemId = id,
                    itemTitle = safeLogTitle,
                    detail = "移入回收站"
                )
            } else {
                repository.deleteItem(item)
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.BILLING_ADDRESS,
                    itemId = id,
                    itemTitle = safeLogTitle
                )
            }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            val item = repository.getItemById(id) ?: return@launch
            repository.updateItem(
                item.copy(
                    isFavorite = !item.isFavorite,
                    updatedAt = Date()
                )
            )
        }
    }

    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }

    suspend fun copyAddressToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Long? {
        if (item.itemType != ItemType.BILLING_ADDRESS || item.hasOwnershipConflict()) return null
        val localCopy = item.asMonicaLocalCopy(categoryId).copy(
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(localCopy)
    }

    suspend fun copyAddressToStorage(
        item: SecureItem,
        categoryId: Long?,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null
    ): Long? {
        if (item.itemType != ItemType.BILLING_ADDRESS) return null
        val addressData = parseAddressData(item.itemData) ?: return null
        val copy = SecureItem(
            id = 0,
            itemType = ItemType.BILLING_ADDRESS,
            title = item.title,
            notes = item.notes,
            isFavorite = item.isFavorite,
            itemData = encodeAddressDataForLocalStorage(addressData),
            imagePaths = item.imagePaths,
            categoryId = if (mdbxDatabaseId == null) categoryId else null,
            mdbxDatabaseId = mdbxDatabaseId,
            mdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null,
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(copy)
    }

    suspend fun moveAddressToStorage(
        id: Long,
        categoryId: Long?,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null
    ): Boolean {
        val existingItem = repository.getItemById(id) ?: return false
        if (existingItem.itemType != ItemType.BILLING_ADDRESS) return false
        val addressData = parseAddressData(existingItem.itemData) ?: return false
        val updatedItem = existingItem.copy(
            itemData = encodeAddressDataForLocalStorage(addressData),
            categoryId = if (mdbxDatabaseId == null) categoryId else null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "NONE",
            mdbxDatabaseId = mdbxDatabaseId,
            mdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null,
            updatedAt = Date()
        )
        repository.updateItem(updatedItem)
        return true
    }

    fun parseAddressData(jsonData: String): BillingAddressData? {
        return CardWalletDataCodec.parseBillingAddressData(
            raw = jsonData,
            decryptIfNeeded = ::decryptStoredSensitiveValue
        )
    }

    private fun encodeAddressDataForLocalStorage(addressData: BillingAddressData): String {
        return encodeStoredSensitiveValueForNewWrite(
            CardWalletDataCodec.encodeBillingAddressData(addressData)
        )
    }

    private fun decryptStoredSensitiveValue(value: String): String {
        return securityManager
            ?.let { manager -> runCatching { manager.decryptDataIfMonicaCiphertext(value) }.getOrDefault(value) }
            ?: value
    }

    private fun encodeStoredSensitiveValueForNewWrite(plainValue: String): String {
        if (plainValue.isBlank()) return plainValue
        return securityManager?.encryptDataLegacyCompat(plainValue) ?: plainValue
    }

    private fun redactedChange(fieldName: String): FieldChange =
        FieldChange(fieldName, "", "已更新")
}
