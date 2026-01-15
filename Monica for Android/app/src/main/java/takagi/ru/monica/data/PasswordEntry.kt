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
    
    // 关联的验证器密钥 (TOTP Secret)
    val authenticatorKey: String = "",  // 用于存储绑定的TOTP验证器密钥
    
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
    val deletedAt: java.util.Date? = null // 删除时间（用于自动清空）
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
}