package takagi.ru.monica.utils

import android.util.Base64
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP（Time-based One-Time Password）工具类
 */
object TotpUtils {
    
    /**
     * 生成TOTP验证码
     * @param secret Base32编码的密钥
     * @param timeInSeconds 当前时间（秒）
     * @param period 时间周期（秒，默认30秒）
     * @param digits 验证码位数（默认6位）
     * @param algorithm 算法（默认SHA1）
     */
    fun generateTotp(
        secret: String,
        timeInSeconds: Long = System.currentTimeMillis() / 1000,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        try {
            val timeSlot = timeInSeconds / period
            val secretBytes = decodeBase32(secret)
            val hmac = generateHmac(secretBytes, timeSlot, algorithm)
            return truncateHmac(hmac, digits)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate TOTP", e)
        }
    }
    
    /**
     * 获取剩余时间（秒）
     */
    fun getRemainingSeconds(period: Int = 30): Int {
        val currentTime = System.currentTimeMillis() / 1000
        return period - (currentTime % period).toInt()
    }
    
    /**
     * 获取进度百分比（0-1）
     */
    fun getProgress(period: Int = 30): Float {
        val remaining = getRemainingSeconds(period)
        return (period - remaining).toFloat() / period
    }
    
    /**
     * 验证TOTP验证码
     */
    fun verifyTotp(
        secret: String,
        code: String,
        timeInSeconds: Long = System.currentTimeMillis() / 1000,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        windowSize: Int = 1 // 允许的时间窗口数量
    ): Boolean {
        val currentTimeSlot = timeInSeconds / period
        
        // 检查当前时间窗口和前后windowSize个窗口
        for (i in -windowSize..windowSize) {
            val testTimeSlot = currentTimeSlot + i
            val testTime = testTimeSlot * period
            val expectedCode = generateTotp(secret, testTime, period, digits, algorithm)
            if (expectedCode == code) {
                return true
            }
        }
        return false
    }
    
    /**
     * 从TOTP URI解析参数
     * 格式: otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
     */
    fun parseOtpAuthUri(uri: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        if (!uri.startsWith("otpauth://totp/")) {
            throw IllegalArgumentException("Invalid TOTP URI format")
        }
        
        try {
            val uriParts = uri.split("?")
            if (uriParts.size == 2) {
                val paramString = uriParts[1]
                paramString.split("&").forEach { param ->
                    val keyValue = param.split("=")
                    if (keyValue.size == 2) {
                        params[keyValue[0]] = java.net.URLDecoder.decode(keyValue[1], "UTF-8")
                    }
                }
            }
            
            // 解析label部分（可能包含issuer和account）
            val labelPart = uriParts[0].substring("otpauth://totp/".length)
            if (labelPart.contains(":")) {
                val parts = labelPart.split(":", limit = 2)
                params["issuer"] = params["issuer"] ?: parts[0]
                params["account"] = parts[1]
            } else {
                params["account"] = labelPart
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse TOTP URI", e)
        }
        
        return params
    }
    
    private fun decodeBase32(base32: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleanedInput = base32.replace("\\s".toRegex(), "").uppercase()
        
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        
        for (char in cleanedInput) {
            val value = base32Chars.indexOf(char)
            if (value < 0) continue
            
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            
            if (bitsLeft >= 8) {
                output.add((buffer shr (bitsLeft - 8)).toByte())
                bitsLeft -= 8
            }
        }
        
        return output.toByteArray()
    }
    
    private fun generateHmac(key: ByteArray, timeSlot: Long, algorithm: String): ByteArray {
        val algorithmName = when (algorithm.uppercase()) {
            "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
        
        val mac = Mac.getInstance(algorithmName)
        val keySpec = SecretKeySpec(key, algorithmName)
        mac.init(keySpec)
        
        val timeBytes = ByteArray(8)
        var time = timeSlot
        for (i in 7 downTo 0) {
            timeBytes[i] = (time and 0xff).toByte()
            time = time shr 8
        }
        
        return mac.doFinal(timeBytes)
    }
    
    private fun truncateHmac(hmac: ByteArray, digits: Int): String {
        val offset = (hmac[hmac.size - 1].toInt() and 0xf)
        
        val truncatedHash = ((hmac[offset].toInt() and 0x7f) shl 24) or
                ((hmac[offset + 1].toInt() and 0xff) shl 16) or
                ((hmac[offset + 2].toInt() and 0xff) shl 8) or
                (hmac[offset + 3].toInt() and 0xff)
        
        val modulus = 10.0.pow(digits).toInt()
        val code = truncatedHash % modulus
        
        return code.toString().padStart(digits, '0')
    }
}