package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 密码页聚合卡片的手动堆叠元数据。
 *
 * 这张表只服务密码页聚合视图，不改变各模块自身的数据模型。
 */
@Entity(
    tableName = "password_page_aggregate_stack_entries",
    indices = [
        Index(value = ["stack_group_id"], name = "index_password_page_aggregate_stack_entries_group")
    ]
)
data class PasswordPageAggregateStackEntry(
    @PrimaryKey
    @ColumnInfo(name = "item_key")
    val itemKey: String,

    @ColumnInfo(name = "stack_group_id")
    val stackGroupId: String,

    @ColumnInfo(name = "stack_order", defaultValue = "0")
    val stackOrder: Int = 0,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
