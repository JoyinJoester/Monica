package takagi.ru.monica.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Password entry entity for Room database
 */
@Parcelize
@Entity(
    tableName = "password_entries",
    indices = [Index(value = ["isDeleted"])]
)
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val website: String,
    val username: String,
    val password: String, // This will be encrypted
    val notes: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0, // 排序顺序(用于拖动排序)
    val isGroupCover: Boolean = false, // 是否作为分组封面
    val appPackageName: String = "", // 关联的应用包名（用于自动填充匹配）
    val appName: String = "", // 关联的应用名称（用于显示）
    
    // Phase 7: 个人信息字段
    val email: String = "",
    val phone: String = "",
    
    // Phase 7: 地址信息字段
    val addressLine: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val country: String = "",
    
    // Phase 7: 支付信息字段 (加密存储)
    val creditCardNumber: String = "",      // 加密存储
    val creditCardHolder: String = "",
    val creditCardExpiry: String = "",       // 格式: MM/YY
    val creditCardCVV: String = "",           // 加密存储
    
    val categoryId: Long? = null, // 分类ID
    
    // 本地 KeePass 数据库归属
    @ColumnInfo(defaultValue = "NULL")
    val keepassDatabaseId: Long? = null, // 归属的 KeePass 数据库ID
    
    // 关联的验证器密钥 (TOTP Secret)
    val authenticatorKey: String = "",  // 用于存储绑定的TOTP验证器密钥

    // 绑定的通行密钥元数据（JSON）
    @ColumnInfo(name = "passkey_bindings", defaultValue = "")
    val passkeyBindings: String = "",
    
    // 第三方登录(SSO)字段
    @ColumnInfo(defaultValue = "PASSWORD")
    val loginType: String = "PASSWORD",  // 登录类型: PASSWORD 或 SSO
    @ColumnInfo(defaultValue = "")
    val ssoProvider: String = "",        // SSO提供商: GOOGLE, APPLE, FACEBOOK 等
    @ColumnInfo(defaultValue = "NULL")
    val ssoRefEntryId: Long? = null,     // 引用的账号条目ID
    
    // 回收站功能 - 软删除字段
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,      // 是否已删除（在回收站中）
    @ColumnInfo(defaultValue = "NULL")
    val deletedAt: java.util.Date? = null, // 删除时间（用于自动清空）
    
    // === Bitwarden 集成字段 ===
    // 当此条目来自 Bitwarden 时，以下字段有值
    @ColumnInfo(name = "bitwarden_vault_id", defaultValue = "NULL")
    val bitwardenVaultId: Long? = null,   // 归属的 Bitwarden Vault ID
    
    @ColumnInfo(name = "bitwarden_cipher_id", defaultValue = "NULL")
    val bitwardenCipherId: String? = null, // Bitwarden Cipher UUID
    
    @ColumnInfo(name = "bitwarden_folder_id", defaultValue = "NULL")
    val bitwardenFolderId: String? = null, // Bitwarden Folder UUID
    
    @ColumnInfo(name = "bitwarden_revision_date", defaultValue = "NULL")
    val bitwardenRevisionDate: String? = null, // 服务器版本号 (ISO 8601)
    
    @ColumnInfo(name = "bitwarden_cipher_type", defaultValue = "1")
    val bitwardenCipherType: Int = 1,     // Cipher 类型: 1=Login, 2=SecureNote, 3=Card, 4=Identity
    
    @ColumnInfo(name = "bitwarden_local_modified", defaultValue = "0")
    val bitwardenLocalModified: Boolean = false // 本地是否有未同步的修改
) : Parcelable {
    
    /**
     * 是否使用第三方登录
     */
    fun isSsoLogin(): Boolean = loginType == "SSO"
    
    /**
     * 获取SSO提供商枚举
     */
    fun getSsoProviderEnum(): SsoProvider? {
        return if (isSsoLogin() && ssoProvider.isNotEmpty()) {
            SsoProvider.fromName(ssoProvider)
        } else null
    }
    
    /**
     * 是否来自 Bitwarden
     */
    fun isBitwardenEntry(): Boolean = bitwardenVaultId != null && bitwardenCipherId != null
    
    /**
     * 是否有待同步的 Bitwarden 修改
     */
    fun hasPendingBitwardenSync(): Boolean = isBitwardenEntry() && bitwardenLocalModified
    
    /**
     * 是否来自 KeePass
     */
    fun isKeePassEntry(): Boolean = keepassDatabaseId != null
    
    /**
     * 是否为本地条目 (不关联任何外部数据库)
     */
    fun isLocalOnlyEntry(): Boolean = !isBitwardenEntry() && !isKeePassEntry()
}