package takagi.ru.monica.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.util.regex.Pattern

/**
 * SMS Retriever Helper for OTP Auto-Read
 * 
 * 使用Google Play Services的SMS Retriever API自动读取短信验证码
 * 无需SMS权限，用户体验更好
 * 
 * Features:
 * - 自动监听短信
 * - 正则提取OTP
 * - 支持多种验证码格式
 * - 超时自动清理
 */
class SmsRetrieverHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "SmsRetriever"
        
        // OTP正则模式 - 支持多种格式
        private val OTP_PATTERNS = listOf(
            Pattern.compile("(\\d{4})"),           // 4位纯数字
            Pattern.compile("(\\d{6})"),           // 6位纯数字
            Pattern.compile("(\\d{8})"),           // 8位纯数字
            Pattern.compile("(?:验证码|code|Code)[：:](\\d{4,8})"),  // 中文前缀
            Pattern.compile("(?:Your code is|Code:)\\s*(\\d{4,8})"), // 英文前缀
            Pattern.compile("(\\d{4})-?(\\d{4})"), // 带分隔符的8位
        )
        
        // SMS超时时间（5分钟）
        private const val SMS_TIMEOUT_MS = 5 * 60 * 1000L
    }
    
    private var smsReceiver: SmsBroadcastReceiver? = null
    private var otpListener: ((String) -> Unit)? = null
    
    /**
     * 启动SMS Retriever监听
     * 
     * @param onOtpReceived OTP接收回调
     * @return 是否成功启动
     */
    fun startSmsRetriever(onOtpReceived: (String) -> Unit): Boolean {
        try {
            otpListener = onOtpReceived
            
            // 注册广播接收器
            smsReceiver = SmsBroadcastReceiver().apply {
                otpCallback = { otp ->
                    Log.d(TAG, "OTP extracted: $otp")
                    onOtpReceived(otp)
                    stopSmsRetriever()
                }
            }
            
            val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    smsReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(smsReceiver, intentFilter)
            }
            
            // 启动SMS Retriever客户端
            val client = SmsRetriever.getClient(context)
            val task = client.startSmsRetriever()
            
            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
            }
            
            task.addOnFailureListener { e ->
                Log.e(TAG, "Failed to start SMS Retriever", e)
                stopSmsRetriever()
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SMS Retriever", e)
            return false
        }
    }
    
    /**
     * 停止SMS Retriever监听
     */
    fun stopSmsRetriever() {
        try {
            smsReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
                smsReceiver = null
            }
            otpListener = null
            Log.d(TAG, "SMS Retriever stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SMS Retriever", e)
        }
    }
    
    /**
     * SMS广播接收器
     */
    private inner class SmsBroadcastReceiver : BroadcastReceiver() {
        
        var otpCallback: ((String) -> Unit)? = null
        
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
                
                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        // 获取短信内容
                        val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: ""
                        Log.d(TAG, "SMS received: ${message.take(50)}...")
                        
                        // 提取OTP
                        val otp = extractOTP(message)
                        if (otp != null) {
                            otpCallback?.invoke(otp)
                        } else {
                            Log.w(TAG, "Failed to extract OTP from SMS")
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Log.w(TAG, "SMS Retriever timeout")
                    }
                    else -> {
                        Log.w(TAG, "SMS Retriever failed with status: ${status?.statusCode}")
                    }
                }
            }
        }
    }
    
    /**
     * 从短信中提取OTP验证码
     * 
     * 支持多种格式:
     * - 纯数字4/6/8位
     * - 带前缀的验证码
     * - 带分隔符的验证码
     * 
     * @param message 短信内容
     * @return 提取的OTP，失败返回null
     */
    private fun extractOTP(message: String): String? {
        // 尝试所有正则模式
        for (pattern in OTP_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                // 获取第一个捕获组（OTP数字）
                val otp = matcher.group(1) ?: continue
                
                // 验证OTP长度（4-8位）
                if (otp.length in 4..8) {
                    Log.d(TAG, "OTP extracted successfully: $otp (pattern: ${pattern.pattern()})")
                    return otp
                }
            }
        }
        
        // 备用方案：查找第一个4-8位连续数字
        val fallbackPattern = Pattern.compile("\\b(\\d{4,8})\\b")
        val fallbackMatcher = fallbackPattern.matcher(message)
        if (fallbackMatcher.find()) {
            val otp = fallbackMatcher.group(1)
            if (otp != null) {
                Log.d(TAG, "OTP extracted using fallback: $otp")
                return otp
            }
        }
        
        return null
    }
    
    /**
     * 检查设备是否支持SMS Retriever API
     */
    fun isSmsRetrieverAvailable(): Boolean {
        return try {
            val client = SmsRetriever.getClient(context)
            // 简单检查客户端是否可用
            client != null
        } catch (e: Exception) {
            Log.e(TAG, "SMS Retriever not available", e)
            false
        }
    }
}

/**
 * OTP验证码提取工具类
 * 提供静态方法用于从文本中提取OTP
 */
object OtpExtractor {
    
    private const val TAG = "OtpExtractor"
    
    // 常见OTP关键词
    private val OTP_KEYWORDS = setOf(
        "验证码", "code", "verification", "otp", "pin",
        "动态码", "校验码", "安全码", "确认码"
    )
    
    /**
     * 快速检查文本是否可能包含OTP
     */
    fun containsOTP(text: String): Boolean {
        val lowerText = text.lowercase()
        
        // 检查是否包含OTP关键词
        val hasKeyword = OTP_KEYWORDS.any { lowerText.contains(it) }
        
        // 检查是否包含4-8位数字
        val hasDigits = Pattern.compile("\\d{4,8}").matcher(text).find()
        
        return hasKeyword && hasDigits
    }
    
    /**
     * 验证OTP格式
     * 
     * @param otp OTP字符串
     * @return 是否为有效OTP
     */
    fun isValidOTP(otp: String): Boolean {
        // OTP必须是4-8位纯数字
        return otp.matches(Regex("^\\d{4,8}$"))
    }
    
    /**
     * 格式化OTP显示
     * 例如: "123456" -> "123 456"
     */
    fun formatOTP(otp: String): String {
        return when (otp.length) {
            4 -> otp  // 4位不分隔
            6 -> "${otp.substring(0, 3)} ${otp.substring(3)}"  // 6位分成3-3
            8 -> "${otp.substring(0, 4)} ${otp.substring(4)}"  // 8位分成4-4
            else -> otp
        }
    }
}

