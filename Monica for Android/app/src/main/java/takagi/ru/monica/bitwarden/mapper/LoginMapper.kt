package takagi.ru.monica.bitwarden.mapper

import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.PasswordEntry
import java.util.Date

/**
 * 密码/登录凭据映射器
 * 
 * Monica PasswordEntry <-> Bitwarden Login (Type 1)
 * 
 * 这是最常用的映射器，处理标准的用户名/密码登录凭据。
 * 支持：URI、TOTP、自定义字段等。
 */
class LoginMapper : BitwardenMapper<PasswordEntry> {
    
    override fun toCreateRequest(item: PasswordEntry, folderId: String?): CipherCreateRequest {
        return CipherCreateRequest(
            type = 1,  // Login
            name = item.title,
            notes = item.notes.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            login = CipherLoginApiData(
                uris = buildUriList(item),
                username = item.username.takeIf { it.isNotBlank() },
                password = item.password.takeIf { it.isNotBlank() },
                totp = item.authenticatorKey.takeIf { it.isNotBlank() }
            ),
            // 可选：添加自定义字段
            fields = buildCustomFields(item)
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): PasswordEntry {
        require(cipher.type == 1) { "LoginMapper only supports Login ciphers (type 1)" }
        
        val login = cipher.login
        
        // 从自定义字段中提取 Monica 特有数据
        val customFields = parseCustomFields(cipher.fields)
        
        return PasswordEntry(
            id = 0,
            title = cipher.name ?: "",
            website = extractMainUri(login?.uris),
            username = login?.username ?: "",
            password = login?.password ?: "",
            notes = cipher.notes ?: "",
            createdAt = Date(),
            updatedAt = Date(),
            isFavorite = cipher.favorite == true,
            authenticatorKey = login?.totp ?: "",
            // Monica 扩展字段从自定义字段恢复
            email = customFields["email"] ?: "",
            phone = customFields["phone"] ?: "",
            appPackageName = customFields["appPackageName"] ?: "",
            appName = customFields["appName"] ?: "",
            // Bitwarden 关联
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            bitwardenCipherType = 1,
            bitwardenLocalModified = false
        )
    }
    
    override fun hasDifference(item: PasswordEntry, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 1) return true
        
        val login = cipher.login
        
        return item.title != cipher.name ||
                item.username != (login?.username ?: "") ||
                item.password != (login?.password ?: "") ||
                item.notes != (cipher.notes ?: "") ||
                item.isFavorite != (cipher.favorite == true) ||
                item.authenticatorKey != (login?.totp ?: "") ||
                !matchUris(item, login?.uris)
    }
    
    override fun merge(
        local: PasswordEntry,
        remote: CipherApiResponse,
        preference: MergePreference
    ): PasswordEntry {
        return when (preference) {
            MergePreference.LOCAL -> local.copy(
                bitwardenRevisionDate = remote.revisionDate
            )
            MergePreference.REMOTE -> fromCipherResponse(remote, local.bitwardenVaultId ?: 0).copy(
                id = local.id,
                createdAt = local.createdAt,
                categoryId = local.categoryId,
                keepassDatabaseId = local.keepassDatabaseId,
                sortOrder = local.sortOrder,
                isGroupCover = local.isGroupCover,
                isDeleted = local.isDeleted,
                deletedAt = local.deletedAt,
                // 保留 Monica 扩展字段（如果远程没有）
                addressLine = local.addressLine,
                city = local.city,
                state = local.state,
                zipCode = local.zipCode,
                country = local.country,
                creditCardNumber = local.creditCardNumber,
                creditCardHolder = local.creditCardHolder,
                creditCardExpiry = local.creditCardExpiry,
                creditCardCVV = local.creditCardCVV
            )
            MergePreference.LATEST -> {
                val localTime = local.updatedAt.time
                val remoteTime = parseRevisionDate(remote.revisionDate)
                if (localTime > remoteTime) {
                    local
                } else {
                    merge(local, remote, MergePreference.REMOTE)
                }
            }
        }
    }
    
    /**
     * 构建 URI 列表
     */
    private fun buildUriList(item: PasswordEntry): List<CipherUriApiData>? {
        val uris = mutableListOf<CipherUriApiData>()
        
        // 网站 URL
        if (item.website.isNotBlank()) {
            val website = if (item.website.startsWith("http://") || 
                              item.website.startsWith("https://")) {
                item.website
            } else {
                "https://${item.website}"
            }
            uris.add(CipherUriApiData(uri = website))
        }
        
        // 应用包名 (Android 特有)
        if (item.appPackageName.isNotBlank()) {
            uris.add(CipherUriApiData(uri = "androidapp://${item.appPackageName}"))
        }
        
        return uris.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 构建自定义字段（用于存储 Monica 特有数据）
     */
    private fun buildCustomFields(item: PasswordEntry): List<CipherFieldApiData>? {
        val fields = mutableListOf<CipherFieldApiData>()
        
        // 只添加非空的 Monica 扩展字段
        if (item.email.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0, // Text
                name = "email",
                value = item.email
            ))
        }
        if (item.phone.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "phone",
                value = item.phone
            ))
        }
        if (item.appPackageName.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "appPackageName",
                value = item.appPackageName
            ))
        }
        if (item.appName.isNotBlank()) {
            fields.add(CipherFieldApiData(
                type = 0,
                name = "appName",
                value = item.appName
            ))
        }
        
        // 地址信息
        if (item.addressLine.isNotBlank() || item.city.isNotBlank()) {
            val address = listOfNotNull(
                item.addressLine.takeIf { it.isNotBlank() },
                item.city.takeIf { it.isNotBlank() },
                item.state.takeIf { it.isNotBlank() },
                item.zipCode.takeIf { it.isNotBlank() },
                item.country.takeIf { it.isNotBlank() }
            ).joinToString(", ")
            
            fields.add(CipherFieldApiData(
                type = 0,
                name = "address",
                value = address
            ))
        }
        
        return fields.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 从自定义字段解析数据
     */
    private fun parseCustomFields(fields: List<CipherFieldApiData>?): Map<String, String> {
        if (fields.isNullOrEmpty()) return emptyMap()
        
        return fields.associate { field ->
            (field.name ?: "") to (field.value ?: "")
        }
    }
    
    /**
     * 从 URI 列表提取主域名
     */
    private fun extractMainUri(uris: List<CipherUriApiData>?): String {
        if (uris.isNullOrEmpty()) return ""
        
        // 优先选择 https:// 开头的 URI
        return uris.firstOrNull { 
            it.uri?.startsWith("https://") == true 
        }?.uri
            ?: uris.firstOrNull { 
                it.uri?.startsWith("http://") == true 
            }?.uri
            ?: uris.firstOrNull { 
                !it.uri.isNullOrBlank() && !it.uri.startsWith("androidapp://") 
            }?.uri
            ?: ""
    }
    
    /**
     * 检查 URI 是否匹配
     */
    private fun matchUris(item: PasswordEntry, remoteUris: List<CipherUriApiData>?): Boolean {
        val localWebsite = item.website.lowercase().removePrefix("https://").removePrefix("http://")
        val localPackage = item.appPackageName
        
        if (remoteUris.isNullOrEmpty()) {
            return localWebsite.isBlank() && localPackage.isBlank()
        }
        
        val remoteWebsites = remoteUris.filter { !it.uri.isNullOrBlank() && !it.uri.startsWith("androidapp://") }
            .map { it.uri!!.lowercase().removePrefix("https://").removePrefix("http://") }
        val remotePackages = remoteUris.filter { it.uri?.startsWith("androidapp://") == true }
            .map { it.uri!!.removePrefix("androidapp://") }
        
        val websiteMatch = localWebsite.isBlank() || remoteWebsites.any { it.contains(localWebsite) || localWebsite.contains(it) }
        val packageMatch = localPackage.isBlank() || remotePackages.contains(localPackage)
        
        return websiteMatch && packageMatch
    }
    
    private fun parseRevisionDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0
        }
    }
}
