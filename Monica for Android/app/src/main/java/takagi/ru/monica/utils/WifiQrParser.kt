package takagi.ru.monica.utils

import takagi.ru.monica.data.model.WifiData
import takagi.ru.monica.data.model.WifiSecurity

/**
 * 解析 ZXing 约定的 Wi-Fi 网络二维码字符串（`WIFI:T:...;S:...;P:...;H:...;;`）。
 *
 * 与 [WifiQrPayload] 配对使用；两者的转义规则一致：`\`、`;`、`,`、`:`、`"`
 * 前面会有反斜杠转义，解析时需要还原。
 */
object WifiQrParser {

    data class Parsed(
        val ssid: String,
        val password: String,
        val security: WifiSecurity,
        val hidden: Boolean
    )

    /** 若输入不是 WIFI 标准字串，返回 null。 */
    fun parse(raw: String?): Parsed? {
        val trimmed = raw?.trim() ?: return null
        if (!trimmed.startsWith("WIFI:", ignoreCase = true)) return null
        val body = trimmed.removePrefix("WIFI:").removePrefix("wifi:")
        val tokens = splitUnescaped(body, ';')
        var ssid = ""
        var password = ""
        var authType = "WPA"
        var hidden = false
        for (token in tokens) {
            if (token.isEmpty()) continue
            val (keyRaw, valueRaw) = splitKeyValue(token) ?: continue
            val key = keyRaw.uppercase()
            val value = unescape(valueRaw)
            when (key) {
                "S" -> ssid = value
                "T" -> authType = value
                "P" -> password = value
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }
        if (ssid.isBlank()) return null
        val security = when (authType.uppercase()) {
            "NOPASS", "" -> WifiSecurity.NONE
            "WEP" -> WifiSecurity.WEP
            "WPA", "WPA2" -> WifiSecurity.WPA2_WPA3
            "WPA3", "SAE" -> WifiSecurity.WPA3
            else -> WifiSecurity.WPA2_WPA3
        }
        return Parsed(
            ssid = ssid,
            password = password,
            security = security,
            hidden = hidden
        )
    }

    fun toWifiData(parsed: Parsed): WifiData = WifiData(
        ssid = parsed.ssid,
        hiddenNetwork = parsed.hidden,
        security = parsed.security
    )

    private fun splitUnescaped(input: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                current.append(c)
                current.append(input[i + 1])
                i += 2
                continue
            }
            if (c == delimiter) {
                out.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun splitKeyValue(token: String): Pair<String, String>? {
        // key 是单字符，后跟 ':'，后面的值中 ':' 本身要求前置 '\\'.
        var i = 0
        while (i < token.length) {
            val c = token[i]
            if (c == '\\' && i + 1 < token.length) {
                i += 2
                continue
            }
            if (c == ':') {
                val key = token.substring(0, i)
                val value = token.substring(i + 1)
                return key to value
            }
            i++
        }
        return null
    }

    private fun unescape(value: String): String {
        if (value.indexOf('\\') < 0) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                sb.append(value[i + 1])
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
