package takagi.ru.monica.data.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Currency
import java.util.Date
import java.util.Locale
import takagi.ru.monica.data.SecureItem

/**
 * 记账条目实体
 */
@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = LedgerCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true
        ),
        ForeignKey(
            entity = SecureItem::class,
            parentColumns = ["id"],
            childColumns = ["linkedItemId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["linkedItemId"]),
        Index(value = ["occurredAt"])
    ]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amountInCents: Long,
    val currencyCode: String = Currency.getInstance(Locale.getDefault()).currencyCode,
    val type: LedgerEntryType,
    val categoryId: Long? = null,
    val linkedItemId: Long? = null,
    val occurredAt: Date,
    val note: String = "",
    val paymentMethod: String = "", // 支付方式: wechat/alipay/unionpay 或银行卡ID
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * 记账条目类型
 */
enum class LedgerEntryType {
    INCOME,
    EXPENSE,
    TRANSFER
}
