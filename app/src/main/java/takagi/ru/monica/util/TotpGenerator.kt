package takagi.ru.monica.util

import android.util.Base64
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP (Time-based One-Time Password) 算法实现
 * 符合 RFC 6238 标准
 */
object TotpGenerator {
    
    /**
     * 生成TOTP验证码
     * @param secret Base32编码的密钥
     * @param timeSeconds 当前时间（秒）
     * @param period 时间周期（默认30秒）
     * @param digits 验证码位数（默认6位）
     * @param algorithm HMAC算法（SHA1, SHA256, SHA512）
     * @return TOTP验证码
     */
    fun generateTotp(
        secret: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        try {
            // 计算时间步长
            val timeStep = timeSeconds / period
            
            // 解码Base32密钥
            val key = decodeBase32(secret)
            
            // 生成HMAC
            val hmac = generateHmac(key, timeStep, algorithm)
            
            // 截断HMAC生成验证码
            return truncateHmac(hmac, digits)
        } catch (e: Exception) {
            e.printStackTrace()
            return "000000"
        }
    }
    
    /**
     * 计算当前验证码的剩余有效时间（秒）
     */
    fun getRemainingSeconds(period: Int = 30): Int {
        val currentSeconds = System.currentTimeMillis() / 1000
        return period - (currentSeconds % period).toInt()
    }
    
    /**
     * 获取当前时间步长的进度（0.0 - 1.0）
     */
    fun getProgress(period: Int = 30): Float {
        val remaining = getRemainingSeconds(period)
        return 1.0f - (remaining.toFloat() / period)
    }
    
    /**
     * 生成HMAC
     */
    private fun generateHmac(key: ByteArray, counter: Long, algorithm: String): ByteArray {
        val algorithmName = "Hmac$algorithm"
        val mac = Mac.getInstance(algorithmName)
        val secretKey = SecretKeySpec(key, algorithmName)
        mac.init(secretKey)
        
        // 将counter转换为8字节数组（大端序）
        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(counter)
        
        return mac.doFinal(buffer.array())
    }
    
    /**
     * 截断HMAC生成验证码
     */
    private fun truncateHmac(hmac: ByteArray, digits: Int): String {
        // 动态截断
        val offset = (hmac[hmac.size - 1].toInt() and 0x0F)
        
        val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)
        
        val otp = binary % 10.0.pow(digits).toInt()
        
        // 格式化为指定位数的字符串（前导零）
        return String.format("%0${digits}d", otp)
    }
    
    /**
     * Base32解码
     */
    private fun decodeBase32(encoded: String): ByteArray {
        // 移除空格和分隔符
        val clean = encoded.replace(Regex("[\\s\\-]"), "").uppercase()
        
        // Base32字符集
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        
        for (char in clean) {
            val value = base32Chars.indexOf(char)
            if (value == -1) continue
            
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            
            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * 验证TOTP码是否有效（考虑时间窗口）
     * @param secret 密钥
     * @param code 用户输入的验证码
     * @param window 允许的时间窗口数量（默认1，即允许前后各1个时间段）
     */
    fun verifyTotp(
        secret: String,
        code: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        window: Int = 1
    ): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        
        for (i in -window..window) {
            val timeOffset = currentTime + (i * period)
            val expectedCode = generateTotp(secret, timeOffset, period, digits, algorithm)
            if (expectedCode == code) {
                return true
            }
        }
        
        return false
    }
}
