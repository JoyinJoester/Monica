package takagi.ru.monica.bitwarden.mapper

import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.sync.SyncItemType
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

/**
 * Mapper 工厂类
 * 
 * 统一管理所有数据类型的 Mapper 实例
 * 根据 Monica 数据类型或 Bitwarden Cipher 类型获取对应的 Mapper
 */
object MapperFactory {
    
    // Mapper 单例
    private val loginMapper = LoginMapper()
    private val cardMapper = CardMapper()
    private val secureNoteMapper = SecureNoteMapper()
    private val identityMapper = IdentityMapper()
    private val totpMapper = TotpMapper()
    private val passkeyMapper = PasskeyMapper()
    
    /**
     * 根据 SecureItem 类型获取对应的 Mapper
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMapperForSecureItem(item: SecureItem): BitwardenMapper<T>? {
        return when (item.itemType) {
            ItemType.PASSWORD -> loginMapper as BitwardenMapper<T>  // 这种情况不太常见，但保持完整性
            ItemType.TOTP -> totpMapper as BitwardenMapper<T>
            ItemType.BANK_CARD -> cardMapper as BitwardenMapper<T>
            ItemType.NOTE -> secureNoteMapper as BitwardenMapper<T>
            ItemType.DOCUMENT -> identityMapper as BitwardenMapper<T>
        }
    }
    
    /**
     * 根据 Monica 同步类型获取 Mapper
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMapperForSyncType(syncType: SyncItemType): BitwardenMapper<T>? {
        return when (syncType) {
            SyncItemType.PASSWORD -> loginMapper as BitwardenMapper<T>
            SyncItemType.TOTP -> totpMapper as BitwardenMapper<T>
            SyncItemType.CARD -> cardMapper as BitwardenMapper<T>  // 使用 SyncItemType.CARD
            SyncItemType.NOTE -> secureNoteMapper as BitwardenMapper<T>
            SyncItemType.IDENTITY -> identityMapper as BitwardenMapper<T>  // 使用 SyncItemType.IDENTITY
            SyncItemType.PASSKEY -> passkeyMapper as BitwardenMapper<T>
            SyncItemType.FOLDER -> null  // Folder 使用 Folder API，不是 Cipher
        }
    }
    
    /**
     * 根据 Bitwarden Cipher 类型获取 Mapper
     * 
     * 注意：Type 1 (Login) 可能对应多种 Monica 类型
     * - 有密码 → PasswordEntry
     * - 只有 TOTP → SecureItem(TOTP)
     * - Passkey 标记 → PasskeyEntry
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMapperForCipher(cipher: CipherApiResponse): BitwardenMapper<T>? {
        return when (cipher.type) {
            1 -> {
                // Login 类型需要进一步判断
                when {
                    PasskeyMapper.isPasskeyCipher(cipher) -> passkeyMapper as BitwardenMapper<T>
                    TotpMapper.isStandaloneTotpCipher(cipher) -> totpMapper as BitwardenMapper<T>
                    else -> loginMapper as BitwardenMapper<T>
                }
            }
            2 -> secureNoteMapper as BitwardenMapper<T>  // Secure Note
            3 -> cardMapper as BitwardenMapper<T>  // Card
            4 -> identityMapper as BitwardenMapper<T>  // Identity
            else -> null
        }
    }
    
    /**
     * 根据 Bitwarden Cipher 判断对应的 Monica 类型
     */
    fun getMonicaTypeForCipher(cipher: CipherApiResponse): MonicaItemType {
        return when (cipher.type) {
            1 -> when {
                PasskeyMapper.isPasskeyCipher(cipher) -> MonicaItemType.PASSKEY
                TotpMapper.isStandaloneTotpCipher(cipher) -> MonicaItemType.TOTP
                else -> MonicaItemType.PASSWORD
            }
            2 -> MonicaItemType.NOTE
            3 -> MonicaItemType.CARD  // 修改为 CARD
            4 -> MonicaItemType.IDENTITY  // 修改为 IDENTITY
            else -> MonicaItemType.UNKNOWN
        }
    }
    
    /**
     * 获取 Bitwarden Cipher 类型编号
     */
    fun getBitwardenCipherType(syncType: SyncItemType): Int {
        return when (syncType) {
            SyncItemType.PASSWORD -> 1
            SyncItemType.TOTP -> 1  // TOTP 也是 Login 类型
            SyncItemType.PASSKEY -> 1  // Passkey 也用 Login 类型
            SyncItemType.NOTE -> 2
            SyncItemType.CARD -> 3  // 使用 SyncItemType.CARD
            SyncItemType.IDENTITY -> 4  // 使用 SyncItemType.IDENTITY
            SyncItemType.FOLDER -> 0  // Folder，不是 Cipher
        }
    }
    
    // 直接访问各 Mapper 的方法
    fun loginMapper() = loginMapper
    fun cardMapper() = cardMapper
    fun secureNoteMapper() = secureNoteMapper
    fun identityMapper() = identityMapper
    fun totpMapper() = totpMapper
    fun passkeyMapper() = passkeyMapper
}

/**
 * Monica 数据类型枚举
 */
enum class MonicaItemType {
    PASSWORD,
    TOTP,
    CARD,       // 银行卡
    NOTE,
    IDENTITY,   // 证件
    PASSKEY,
    FOLDER,     // 分类/文件夹
    UNKNOWN
}
