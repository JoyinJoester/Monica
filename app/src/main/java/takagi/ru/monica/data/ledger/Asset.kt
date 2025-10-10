package takagi.ru.monica.data.ledger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 资产账户实体
 */
@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["assetType"]),
        Index(value = ["linkedBankCardId"], unique = true)
    ]
)
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val assetType: AssetType,
    val balanceInCents: Long = 0, // 余额（分）
    val currencyCode: String = "CNY",
    val iconKey: String = "wallet",
    val colorHex: String = "#4CAF50",
    val linkedBankCardId: Long? = null, // 关联的银行卡ID（如果是银行卡类型）
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * 资产类型
 */
enum class AssetType {
    WECHAT,      // 微信支付
    ALIPAY,      // 支付宝
    UNIONPAY,    // 云闪付
    PAYPAL,      // PayPal
    BANK_CARD,   // 银行卡
    CASH,        // 现金
    OTHER        // 其他
}
