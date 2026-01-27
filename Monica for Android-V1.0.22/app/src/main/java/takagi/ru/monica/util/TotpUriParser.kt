package takagi.ru.monica.util

import android.net.Uri
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

/**
 * OTP URI 解析工具
 * 
 * 支持标准的 otpauth:// URI 格式
 * 支持类型: totp, hotp
 * 例如: otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
 * 例如: otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=0
 */
object TotpUriParser {
    
    /**
     * 解析 otpauth:// URI
     * 
     * @param uri OTP URI字符串 (支持 totp 和 hotp)
     * @return 解析结果，包含TotpData、账户名和标签
     */
    fun parseUri(uri: String): TotpParseResult? {
        try {
            // 检查URI格式
            val lowerUri = uri.lowercase()
            val isTotp = lowerUri.startsWith("otpauth://totp/")
            val isHotp = lowerUri.startsWith("otpauth://hotp/")
            
            if (!isTotp && !isHotp) {
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
            
            // HOTP特有参数
            val counter = parsedUri.getQueryParameter("counter")?.toLongOrNull() ?: 0L
            
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
            
            // 检测特殊服务提供商并设置相应的OTP类型
            val otpType = detectOtpType(isHotp, finalIssuer, parsedUri)
            
            // 根据检测到的类型调整参数
            val finalDigits = when (otpType) {
                OtpType.STEAM -> 5  // Steam固定5位
                else -> digits
            }
            
            val totpData = TotpData(
                secret = secret,
                issuer = finalIssuer,
                accountName = accountName,
                period = period,
                digits = finalDigits,
                algorithm = algorithm,
                otpType = otpType,
                counter = counter,
                pin = ""  // PIN码需要用户额外输入
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
     * 检测OTP类型
     * 根据URI类型、issuer名称和特殊参数判断
     */
    private fun detectOtpType(isHotp: Boolean, issuer: String, uri: Uri): OtpType {
        // 优先检查是否是HOTP
        if (isHotp) {
            return OtpType.HOTP
        }
        
        // 检查issuer中是否包含特殊服务提供商名称
        val issuerLower = issuer.lowercase()
        when {
            issuerLower.contains("steam") -> return OtpType.STEAM
            issuerLower.contains("yandex") -> return OtpType.YANDEX
        }
        
        // 检查是否有Steam特殊参数
        val encoder = uri.getQueryParameter("encoder")?.lowercase()
        if (encoder == "steam") {
            return OtpType.STEAM
        }
        
        // 默认为TOTP
        return OtpType.TOTP
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
     * 生成OTP URI
     * 
     * @param label 标签（通常是 "Issuer:AccountName"）
     * @param totpData OTP数据
     * @return otpauth:// URI字符串
     */
    fun generateUri(label: String, totpData: TotpData): String {
        val encodedLabel = Uri.encode(label)
        
        // 根据OTP类型选择authority
        val authority = when (totpData.otpType) {
            OtpType.HOTP -> "hotp"
            else -> "totp"
        }
        
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(authority)
            .appendPath(encodedLabel)
            .appendQueryParameter("secret", totpData.secret)
        
        if (totpData.issuer.isNotBlank()) {
            builder.appendQueryParameter("issuer", totpData.issuer)
        }
        
        // HOTP需要counter参数
        if (totpData.otpType == OtpType.HOTP) {
            builder.appendQueryParameter("counter", totpData.counter.toString())
        }
        
        if (totpData.period != 30 && totpData.otpType != OtpType.HOTP) {
            builder.appendQueryParameter("period", totpData.period.toString())
        }
        
        if (totpData.digits != 6) {
            builder.appendQueryParameter("digits", totpData.digits.toString())
        }
        
        if (totpData.algorithm != "SHA1") {
            builder.appendQueryParameter("algorithm", totpData.algorithm)
        }
        
        // Steam特殊标记
        if (totpData.otpType == OtpType.STEAM) {
            builder.appendQueryParameter("encoder", "steam")
        }
        
        return builder.build().toString()
    }
    
    /**
     * 生成OTP URI (兼容旧版本API)
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
    @Deprecated("使用 generateUri(label, totpData) 代替",
        ReplaceWith("generateUri(label, TotpData(secret, issuer, accountName, period, digits, algorithm))"))
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
