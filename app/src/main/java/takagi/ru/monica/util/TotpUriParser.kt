package takagi.ru.monica.util

import android.net.Uri
import takagi.ru.monica.data.model.TotpData

/**
 * TOTP URI 解析工具
 * 
 * 支持标准的 otpauth:// URI 格式
 * 例如: otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
 */
object TotpUriParser {
    
    /**
     * 解析 otpauth:// URI
     * 
     * @param uri TOTP URI字符串
     * @return 解析结果，包含TotpData、账户名和标签
     */
    fun parseUri(uri: String): TotpParseResult? {
        try {
            if (!uri.startsWith("otpauth://totp/", ignoreCase = true)) {
                return null
            }
            
            val parsedUri = Uri.parse(uri)
            
            // 获取密钥（必需）
            val secret = parsedUri.getQueryParameter("secret") ?: return null
            
            // 获取可选参数
            val issuer = parsedUri.getQueryParameter("issuer") ?: ""
            val algorithm = parsedUri.getQueryParameter("algorithm") ?: "SHA1"
            val digits = parsedUri.getQueryParameter("digits")?.toIntOrNull() ?: 6
            val period = parsedUri.getQueryParameter("period")?.toIntOrNull() ?: 30
            
            // 解析路径以获取标签和账户名
            // 格式: otpauth://totp/Label?...
            // 或: otpauth://totp/Issuer:AccountName?...
            val path = parsedUri.path?.removePrefix("/") ?: ""
            val (label, accountName) = parseLabel(path)
            
            // 如果URI中没有issuer参数，尝试从label中提取
            val finalIssuer = if (issuer.isBlank() && label.contains(":")) {
                label.substringBefore(":")
            } else {
                issuer
            }
            
            val totpData = TotpData(
                secret = secret,
                issuer = finalIssuer,
                accountName = accountName,
                period = period,
                digits = digits,
                algorithm = algorithm
            )
            
            return TotpParseResult(
                totpData = totpData,
                label = label,
                accountName = accountName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 解析标签
     * 
     * 格式1: "Example:user@example.com" -> ("Example:user@example.com", "user@example.com")
     * 格式2: "user@example.com" -> ("user@example.com", "user@example.com")
     */
    private fun parseLabel(label: String): Pair<String, String> {
        val decodedLabel = Uri.decode(label)
        
        return if (decodedLabel.contains(":")) {
            val parts = decodedLabel.split(":", limit = 2)
            decodedLabel to parts[1]
        } else {
            decodedLabel to decodedLabel
        }
    }
    
    /**
     * 生成TOTP URI
     * 
     * @param label 标签（通常是 "Issuer:AccountName"）
     * @param secret Base32编码的密钥
     * @param issuer 发行者
     * @param accountName 账户名
     * @param period 时间周期
     * @param digits 验证码位数
     * @param algorithm 算法
     * @return otpauth:// URI字符串
     */
    fun generateUri(
        label: String,
        secret: String,
        issuer: String = "",
        accountName: String = "",
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        val encodedLabel = Uri.encode(label)
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority("totp")
            .appendPath(encodedLabel)
            .appendQueryParameter("secret", secret)
        
        if (issuer.isNotBlank()) {
            builder.appendQueryParameter("issuer", issuer)
        }
        
        if (period != 30) {
            builder.appendQueryParameter("period", period.toString())
        }
        
        if (digits != 6) {
            builder.appendQueryParameter("digits", digits.toString())
        }
        
        if (algorithm != "SHA1") {
            builder.appendQueryParameter("algorithm", algorithm)
        }
        
        return builder.build().toString()
    }
}

/**
 * TOTP URI 解析结果
 */
data class TotpParseResult(
    val totpData: TotpData,
    val label: String,
    val accountName: String
)
