package takagi.ru.monica.utils

import takagi.ru.monica.data.model.WifiData
import takagi.ru.monica.data.model.WifiSecurity

/**
 * 生成 ZXing 约定的 Wi-Fi 网络二维码字符串：
 *
 *     WIFI:T:<auth>;S:<ssid>;P:<password>;H:<true|false>;;
 *
 * 这段字符串被 Android 相机、iOS 相机、以及大多数第三方扫码器识别为"加入
 * Wi-Fi 网络"提示，用户扫到后会被系统主动引导连接。
 *
 * 规则要点：
 *  - `\`、`;`、`,`、`:`、`"` 必须转义成 `\x`
 *  - 企业级 Wi-Fi（EAP/PEAP）不在 ZXing 原始规范里；当前条目如果是企业级
 *    我们返回 null，调用方给用户提示"不支持二维码"
 *  - 开放网络写成 `T:nopass` + 省略 `P:`
 */
object WifiQrPayload {

    fun build(wifi: WifiData): String? {
        if (wifi.ssid.isBlank()) return null
        val authType = when (wifi.security) {
            WifiSecurity.NONE -> "nopass"
            WifiSecurity.WEP -> "WEP"
            WifiSecurity.WPA_WPA2,
            WifiSecurity.WPA2_WPA3,
            WifiSecurity.WPA3 -> "WPA"
            WifiSecurity.WPA2_ENTERPRISE,
            WifiSecurity.WPA3_ENTERPRISE -> return null // 超出 WIFI: 字符串规范
        }
        return buildPayload(
            ssid = wifi.ssid,
            authType = authType,
            password = if (wifi.security == WifiSecurity.NONE) "" else "",
            hidden = wifi.hiddenNetwork
        )
    }

    /**
     * 完整版：显式传入密码，调用方从 [takagi.ru.monica.data.PasswordEntry.password]
     * 解密后传进来，避免模型层耦合解密逻辑。
     */
    fun build(wifi: WifiData, password: String): String? {
        if (wifi.ssid.isBlank()) return null
        val authType = when (wifi.security) {
            WifiSecurity.NONE -> "nopass"
            WifiSecurity.WEP -> "WEP"
            WifiSecurity.WPA_WPA2,
            WifiSecurity.WPA2_WPA3,
            WifiSecurity.WPA3 -> "WPA"
            WifiSecurity.WPA2_ENTERPRISE,
            WifiSecurity.WPA3_ENTERPRISE -> return null
        }
        return buildPayload(
            ssid = wifi.ssid,
            authType = authType,
            password = if (authType == "nopass") "" else password,
            hidden = wifi.hiddenNetwork
        )
    }

    private fun buildPayload(
        ssid: String,
        authType: String,
        password: String,
        hidden: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("WIFI:")
        sb.append("T:").append(authType).append(';')
        sb.append("S:").append(escape(ssid)).append(';')
        if (authType != "nopass") {
            sb.append("P:").append(escape(password)).append(';')
        }
        if (hidden) {
            sb.append("H:true;")
        }
        sb.append(';') // 规范要求 trailing 双分号
        return sb.toString()
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return value
        val sb = StringBuilder(value.length + 4)
        value.forEach { c ->
            when (c) {
                '\\', ';', ',', ':', '"' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
