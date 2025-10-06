package takagi.ru.monica.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 统一的安全数据项实体
 * 支持多种类型：TOTP验证器、银行卡、证件
 */
@Entity(tableName = "secure_items")
data class SecureItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 通用字段
    val itemType: ItemType,  // 数据类型:TOTP、BANK_CARD、DOCUMENT
    val title: String,       // 标题/名称
    val notes: String = "",  // 备注
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,  // 排序顺序(用于拖动排序)
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    
    // 类型特定数据(JSON格式存储)
    val itemData: String,    // 存储不同类型的具体数据(JSON)
    
    // 图片附件路径(加密存储)
    val imagePaths: String = "" // JSON数组,存储图片文件路径
)

/**
 * 数据项类型枚举
 */
enum class ItemType {
    PASSWORD,    // 密码
    TOTP,        // 验证器
    BANK_CARD,   // 银行卡
    DOCUMENT     // 证件
}
