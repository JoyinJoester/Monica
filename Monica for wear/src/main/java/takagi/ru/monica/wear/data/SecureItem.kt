package takagi.ru.monica.wear.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 统一的安全数据项实体 (Wear版本 - 仅支持TOTP)
 * 复用主应用的数据模型，但仅处理TOTP类型
 */
@Entity(tableName = "secure_items")
data class SecureItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 通用字段
    val itemType: ItemType,  // 数据类型 (Wear版本仅使用TOTP)
    val title: String,       // 标题/名称
    val notes: String = "",  // 备注
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,  // 排序顺序
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    
    // 类型特定数据(JSON格式存储)
    val itemData: String,    // 存储TOTP数据(JSON)
    
    // 图片附件路径(Wear版本不使用)
    val imagePaths: String = ""
)

/**
 * 数据项类型枚举
 */
enum class ItemType {
    PASSWORD,    // 密码 (Wear版本不使用)
    TOTP,        // 验证器 (Wear版本主要类型)
    BANK_CARD,   // 银行卡 (Wear版本不使用)
    DOCUMENT,    // 证件 (Wear版本不使用)
    NOTE         // 笔记 (Wear版本不使用)
}
