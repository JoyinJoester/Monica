package takagi.ru.monica.bitwarden.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.bitwarden.api.BitwardenVaultApi
import takagi.ru.monica.bitwarden.api.FolderApiResponse
import takagi.ru.monica.bitwarden.api.FolderCreateRequest
import takagi.ru.monica.bitwarden.api.FolderUpdateRequest
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault

/**
 * Folder 同步处理器
 * 负责 Monica Category 与 Bitwarden Folder 之间的双向同步
 */
class FolderSyncProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "FolderSyncProcessor"
    }
    
    private val database = PasswordDatabase.getDatabase(context)
    private val categoryDao = database.categoryDao()
    
    /**
     * 同步结果
     */
    data class FolderSyncResult(
        val success: Boolean,
        val foldersCreated: Int = 0,
        val foldersUpdated: Int = 0,
        val foldersDeleted: Int = 0,
        val categoriesLinked: Int = 0,
        val error: String? = null
    )
    
    /**
     * 从 Bitwarden 拉取 Folders 并与本地 Category 匹配关联
     */
    suspend fun syncFoldersFromServer(
        vault: BitwardenVault,
        cryptoKey: BitwardenCrypto.SymmetricCryptoKey,
        folders: List<FolderApiResponse>
    ): FolderSyncResult = withContext(Dispatchers.IO) {
        try {
            var categoriesLinked = 0
            
            for (folder in folders) {
                try {
                    // 解密文件夹名称
                    val decryptedName = folder.name?.let { 
                        BitwardenCrypto.decryptToString(it, cryptoKey)
                    } ?: continue
                    
                    // 查找是否已有关联
                    val existingCategory = categoryDao.getCategoryByBitwardenFolderId(folder.id)
                    
                    if (existingCategory != null) {
                        // 已关联，检查名称是否需要更新（如果需要的话）
                        Log.d(TAG, "Folder ${folder.id} already linked to category: ${existingCategory.name}")
                    } else {
                        // 尝试按名称匹配
                        val allCategories = categoryDao.getBitwardenLinkedCategoriesSync()
                        val matchingCategory = allCategories.find { category ->
                            category.name.equals(decryptedName, ignoreCase = true) && 
                            category.bitwardenFolderId == null 
                        }
                        
                        if (matchingCategory != null) {
                            // 自动关联同名分类
                            categoryDao.linkToBitwarden(
                                categoryId = matchingCategory.id,
                                vaultId = vault.id,
                                folderId = folder.id,
                                syncTypes = null // 继承 vault 设置
                            )
                            categoriesLinked++
                            Log.d(TAG, "Auto-linked category '${matchingCategory.name}' to folder: ${folder.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process folder ${folder.id}: ${e.message}")
                }
            }
            
            FolderSyncResult(
                success = true,
                categoriesLinked = categoriesLinked
            )
        } catch (e: Exception) {
            Log.e(TAG, "Folder sync failed", e)
            FolderSyncResult(
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * 将本地 Category 上传为 Bitwarden Folder
     */
    suspend fun uploadCategoryAsFolder(
        category: Category,
        vault: BitwardenVault,
        vaultApi: BitwardenVaultApi,
        cryptoKey: BitwardenCrypto.SymmetricCryptoKey
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 加密分类名称
            val encryptedName = BitwardenCrypto.encryptString(category.name, cryptoKey)
            
            // 创建 Folder 请求
            val request = FolderCreateRequest(name = encryptedName)
            
            // 调用 API 创建
            val response = vaultApi.createFolder(
                "Bearer ${vault.encryptedAccessToken}",
                request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val createdFolder = response.body()!!
                
                // 更新本地关联
                categoryDao.linkToBitwarden(
                    categoryId = category.id,
                    vaultId = vault.id,
                    folderId = createdFolder.id,
                    syncTypes = category.syncItemTypes
                )
                
                Log.d(TAG, "Created folder for category: ${category.name}, folderId: ${createdFolder.id}")
                Result.success(createdFolder.id)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to create folder: $error")
                Result.failure(Exception("Failed to create folder: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload category as folder failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新 Bitwarden Folder 名称
     */
    suspend fun updateFolderName(
        category: Category,
        vault: BitwardenVault,
        vaultApi: BitwardenVaultApi,
        cryptoKey: BitwardenCrypto.SymmetricCryptoKey
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val folderId = category.bitwardenFolderId 
                ?: return@withContext Result.failure(Exception("Category not linked to folder"))
            
            // 加密新名称
            val encryptedName = BitwardenCrypto.encryptString(category.name, cryptoKey)
            
            val request = FolderUpdateRequest(name = encryptedName)
            
            val response = vaultApi.updateFolder(
                "Bearer ${vault.encryptedAccessToken}",
                folderId,
                request
            )
            
            if (response.isSuccessful) {
                Log.d(TAG, "Updated folder name for: ${category.name}")
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Failed to update folder: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update folder name failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除 Bitwarden Folder
     */
    suspend fun deleteFolder(
        folderId: String,
        vault: BitwardenVault,
        vaultApi: BitwardenVaultApi
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = vaultApi.deleteFolder(
                "Bearer ${vault.encryptedAccessToken}",
                folderId
            )
            
            if (response.isSuccessful) {
                Log.d(TAG, "Deleted folder: $folderId")
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Failed to delete folder: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete folder failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 关联 Category 到已有的 Bitwarden Folder
     */
    suspend fun linkCategoryToFolder(
        categoryId: Long,
        vaultId: Long,
        folderId: String,
        syncTypes: List<String>? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val syncTypesJson = syncTypes?.let { types -> 
                "[${types.joinToString(",") { type -> "\"$type\"" }}]" 
            }
            
            categoryDao.linkToBitwarden(categoryId, vaultId, folderId, syncTypesJson)
            Log.d(TAG, "Linked category $categoryId to folder $folderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Link category to folder failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解除 Category 的 Bitwarden 关联
     */
    suspend fun unlinkCategory(categoryId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            categoryDao.unlinkFromBitwarden(categoryId)
            Log.d(TAG, "Unlinked category $categoryId from Bitwarden")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Unlink category failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取所有已关联的分类
     */
    suspend fun getLinkedCategories(): List<Category> = withContext(Dispatchers.IO) {
        categoryDao.getBitwardenLinkedCategoriesSync()
    }
    
    /**
     * 解析同步类型
     */
    fun parseSyncTypes(syncTypesJson: String?): List<String> {
        if (syncTypesJson.isNullOrBlank()) return emptyList()
        
        return try {
            // 简单解析 JSON 数组: ["PASSWORD", "TOTP"]
            syncTypesJson
                .trim('[', ']')
                .split(",")
                .map { str -> str.trim().trim('"') }
                .filter { str -> str.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse sync types: $syncTypesJson")
            emptyList()
        }
    }
}
