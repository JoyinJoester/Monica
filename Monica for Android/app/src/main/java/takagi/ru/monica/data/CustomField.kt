package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 自定义字段实体
 * 
 * 每个密码条目可以拥有多个自定义字段，用于存储额外的键值对信息。
 * 使用外键关联到 PasswordEntry，支持级联删除。
 * 
 * @property id 自定义字段的唯一标识符
 * @property entryId 关联的密码条目ID
 * @property title 字段名称（如："安全问题"、"备用邮箱"）
 * @property value 字段值（支持任意文本，包括特殊字符和表情）
 * @property isProtected 是否为敏感数据（为true时UI默认隐藏内容，复制时标记为敏感剪贴板）
 * @property sortOrder 排序顺序（用于保持用户自定义的显示顺序）
 */
@Entity(
    tableName = "custom_fields",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE  // 删除密码条目时级联删除关联的自定义字段
        )
    ],
    indices = [
        Index(value = ["entry_id"])  // 为外键创建索引，提升查询性能
    ]
)
data class CustomField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    
    val title: String,
    
    val value: String,
    
    @ColumnInfo(name = "is_protected", defaultValue = "0")
    val isProtected: Boolean = false,
    
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0
) {
    /**
     * 检查字段是否有效（标题不为空）
     */
    fun isValid(): Boolean = title.isNotBlank()
    
    /**
     * 生成用于同步 Hash 的排序键
     * 确保顺序无关的 Hash 计算
     */
    fun toHashKey(): String = "$title:$value:$isProtected"
    
    companion object {
        /**
         * 创建一个新的自定义字段（用于 UI 编辑时的临时对象）
         */
        fun create(entryId: Long, title: String, value: String, isProtected: Boolean = false, sortOrder: Int = 0): CustomField {
            return CustomField(
                id = 0,  // 新建时ID为0，保存时自动生成
                entryId = entryId,
                title = title,
                value = value,
                isProtected = isProtected,
                sortOrder = sortOrder
            )
        }
        
        /**
         * 从 KeePass StringField 创建自定义字段
         */
        fun fromKeePassStringField(entryId: Long, key: String, value: String, isProtected: Boolean): CustomField {
            return CustomField(
                id = 0,
                entryId = entryId,
                title = key,
                value = value,
                isProtected = isProtected,
                sortOrder = 0
            )
        }
    }
}

/**
 * 用于 UI 编辑时的临时自定义字段状态
 * 不包含 entryId，便于在创建新条目时使用
 */
data class CustomFieldDraft(
    val id: Long = 0,  // 临时ID，仅用于 UI 区分
    val title: String = "",
    val value: String = "",
    val isProtected: Boolean = false
) {
    /**
     * 转换为 CustomField 实体
     */
    fun toCustomField(entryId: Long, sortOrder: Int): CustomField {
        return CustomField(
            id = 0,  // 保存时生成新ID
            entryId = entryId,
            title = title,
            value = value,
            isProtected = isProtected,
            sortOrder = sortOrder
        )
    }
    
    /**
     * 检查是否为有效字段（至少有标题）
     */
    fun isValid(): Boolean = title.isNotBlank()
    
    /**
     * 检查是否为空字段（标题和值都为空）
     */
    fun isEmpty(): Boolean = title.isBlank() && value.isBlank()
    
    companion object {
        private var tempIdCounter = -1L
        
        /**
         * 生成下一个临时ID（用于 UI 列表的 key）
         */
        fun nextTempId(): Long {
            return tempIdCounter--
        }
        
        /**
         * 从 CustomField 创建 Draft
         */
        fun fromCustomField(field: CustomField): CustomFieldDraft {
            return CustomFieldDraft(
                id = field.id,
                title = field.title,
                value = field.value,
                isProtected = field.isProtected
            )
        }
    }
}
