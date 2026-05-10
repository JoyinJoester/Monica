package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WIFI 条目扩展元数据。
 *
 * 复用 [takagi.ru.monica.data.PasswordEntry]：
 *   - [takagi.ru.monica.data.PasswordEntry.title]      -> 条目显示标题（通常就是 SSID）
 *   - [takagi.ru.monica.data.PasswordEntry.password]   -> WPA/WEP/企业级 口令或凭据密码
 *   - [takagi.ru.monica.data.PasswordEntry.username]   -> 企业级 WIFI 的身份（Identity）
 *   - [takagi.ru.monica.data.PasswordEntry.loginType]  -> 取值 "WIFI"
 *   - [takagi.ru.monica.data.PasswordEntry.wifiMetadata] -> 本对象的 JSON 序列化
 *
 * 设计原则：本模型不是 Android 系统 `WifiConfiguration` 的一比一映射，而是
 * 以「给人看、能手抄」为目标，字段尽量扁平，避免解析压力。
 */
@Serializable
data class WifiData(
    val ssid: String = "",
    val hiddenNetwork: Boolean = false,
    val security: WifiSecurity = WifiSecurity.WPA2_WPA3,
    val eap: WifiEapSettings? = null,
    val macRandomization: WifiMacRandomization = WifiMacRandomization.DEFAULT,
    val proxy: WifiProxy = WifiProxy.None,
    val ip: WifiIp = WifiIp.Dhcp,
    val bssid: String = ""
) {
    fun toJson(): String = WifiDataJson.encodeToString(serializer(), this)

    companion object {
        fun fromJsonOrEmpty(raw: String?): WifiData {
            if (raw.isNullOrBlank()) return WifiData()
            return runCatching { WifiDataJson.decodeFromString(serializer(), raw) }
                .getOrDefault(WifiData())
        }
    }
}

/**
 * 常见 WIFI 安全性。命名对齐 Android Wi-Fi 设置里的措辞，便于用户识别。
 */
@Serializable
enum class WifiSecurity {
    NONE,           // 开放网络
    WEP,            // 已淘汰，仅兼容老路由
    WPA_WPA2,       // WPA/WPA2-Personal
    WPA2_WPA3,      // WPA2/WPA3-Personal（默认值，覆盖绝大多数家庭网络）
    WPA3,           // WPA3-Personal
    WPA2_ENTERPRISE,
    WPA3_ENTERPRISE
}

/** 企业级 WIFI 的扩展字段。仅当 [WifiSecurity] 为 *_ENTERPRISE 时才读写。 */
@Serializable
data class WifiEapSettings(
    val method: WifiEapMethod = WifiEapMethod.PEAP,
    val phase2: WifiEapPhase2 = WifiEapPhase2.MSCHAPV2,
    val anonymousIdentity: String = "",
    val caCertificate: String = "",
    val domain: String = ""
)

@Serializable
enum class WifiEapMethod { PEAP, TLS, TTLS, PWD, SIM, AKA, AKA_PRIME }

@Serializable
enum class WifiEapPhase2 { NONE, PAP, MSCHAP, MSCHAPV2, GTC, SIM, AKA, AKA_PRIME }

/** 隐私 - MAC 地址随机化策略。 */
@Serializable
enum class WifiMacRandomization {
    DEFAULT,    // 使用系统默认（通常是随机）
    RANDOMIZED, // 使用随机 MAC
    DEVICE_MAC  // 使用设备 MAC（Android 10+ 可选）
}

/** 代理设置。使用密封类避免 proxy=None 时写入无关字段。 */
@Serializable
sealed class WifiProxy {
    @Serializable
    data object None : WifiProxy()

    @Serializable
    data class Manual(
        val host: String = "",
        val port: Int = 0,
        val bypassList: String = ""
    ) : WifiProxy()

    @Serializable
    data class AutoConfig(
        val pacUrl: String = ""
    ) : WifiProxy()
}

/** IP 设置。 */
@Serializable
sealed class WifiIp {
    @Serializable
    data object Dhcp : WifiIp()

    @Serializable
    data class Static(
        val ipAddress: String = "",
        val gateway: String = "",
        val networkPrefixLength: Int = 24,
        val dns1: String = "",
        val dns2: String = ""
    ) : WifiIp()
}

private val WifiDataJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    prettyPrint = false
    classDiscriminator = "kind"
}
