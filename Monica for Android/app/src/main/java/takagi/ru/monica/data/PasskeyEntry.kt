package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Passkey（通行密钥）实体
 * 
 * 存储基于 FIDO2/WebAuthn 标准的 Passkey 凭据信息。
 * 私钥存储在 Android Keystore 中，此表仅存储元数据和公钥。
 * 
 * @property credentialId Base64 编码的凭据 ID（由认证器生成）
 * @property rpId 依赖方 ID（通常是域名，如 google.com）
 * @property rpName 依赖方显示名称
 * @property userId 用户 ID（由依赖方提供，Base64 编码）
 * @property userName 用户名/邮箱
 * @property userDisplayName 用户显示名称
 * @property publicKeyAlgorithm COSE 算法标识（如 ES256 = -7, RS256 = -257）
 * @property publicKey 公钥（COSE 格式，Base64 编码）
 * @property privateKeyAlias Android Keystore 中的私钥别名
 * @property createdAt 创建时间戳
 * @property lastUsedAt 最后使用时间戳
 * @property useCount 使用次数
 * @property iconUrl 网站/应用图标 URL
 * @property isDiscoverable 是否为可发现凭据（Resident Key）
 * @property isUserVerificationRequired 是否需要用户验证（生物识别）
 * @property transports 支持的传输方式（internal, usb, nfc, ble）
 * @property aaguid 认证器 AAGUID
 * @property signCount 签名计数器（用于检测克隆攻击）
 * @property isBackedUp 是否已备份（用于 WebDAV 同步）
 * @property notes 用户备注
 */
@Entity(
    tableName = "passkeys",
    indices = [
        Index(value = ["rp_id"], name = "index_passkeys_rp_id"),
        Index(value = ["user_name"], name = "index_passkeys_user_name")
    ]
)
data class PasskeyEntry(
    @PrimaryKey
    @ColumnInfo(name = "credential_id")
    val credentialId: String,
    
    @ColumnInfo(name = "rp_id")
    val rpId: String,
    
    @ColumnInfo(name = "rp_name")
    val rpName: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "user_name")
    val userName: String,
    
    @ColumnInfo(name = "user_display_name")
    val userDisplayName: String,
    
    @ColumnInfo(name = "public_key_algorithm")
    val publicKeyAlgorithm: Int = -7, // ES256
    
    @ColumnInfo(name = "public_key")
    val publicKey: String,
    
    @ColumnInfo(name = "private_key_alias")
    val privateKeyAlias: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "use_count")
    val useCount: Int = 0,
    
    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null,
    
    @ColumnInfo(name = "is_discoverable")
    val isDiscoverable: Boolean = true,
    
    @ColumnInfo(name = "is_user_verification_required")
    val isUserVerificationRequired: Boolean = true,
    
    @ColumnInfo(name = "transports")
    val transports: String = "internal", // 逗号分隔的传输方式
    
    @ColumnInfo(name = "aaguid")
    val aaguid: String = "",
    
    @ColumnInfo(name = "sign_count")
    val signCount: Long = 0,
    
    @ColumnInfo(name = "is_backed_up")
    val isBackedUp: Boolean = false,
    
    @ColumnInfo(name = "notes")
    val notes: String = ""
) {
    /**
     * 获取传输方式列表
     */
    fun getTransportsList(): List<String> = transports.split(",").filter { it.isNotBlank() }
    
    /**
     * 获取算法名称
     */
    fun getAlgorithmName(): String = when (publicKeyAlgorithm) {
        -7 -> "ES256 (ECDSA P-256)"
        -257 -> "RS256 (RSA PKCS#1)"
        -37 -> "PS256 (RSA PSS)"
        -8 -> "EdDSA (Ed25519)"
        else -> "Unknown ($publicKeyAlgorithm)"
    }
    
    /**
     * 格式化最后使用时间
     */
    fun getLastUsedFormatted(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastUsedAt
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            diff < 2592000_000 -> "${diff / 86400_000} 天前"
            else -> "${diff / 2592000_000} 个月前"
        }
    }
    
    companion object {
        // COSE 算法常量
        const val ALGORITHM_ES256 = -7
        const val ALGORITHM_RS256 = -257
        const val ALGORITHM_PS256 = -37
        const val ALGORITHM_EDDSA = -8
        
        // 传输方式常量
        const val TRANSPORT_INTERNAL = "internal"
        const val TRANSPORT_USB = "usb"
        const val TRANSPORT_NFC = "nfc"
        const val TRANSPORT_BLE = "ble"
        const val TRANSPORT_HYBRID = "hybrid"
    }
}
