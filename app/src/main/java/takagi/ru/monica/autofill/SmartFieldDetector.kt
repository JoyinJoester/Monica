package takagi.ru.monica.autofill

import android.view.View
import android.view.autofill.AutofillId
import java.util.regex.Pattern

/**
 * 自动填充字段类型
 * 支持智能识别多种字段类型
 */
enum class AutofillFieldType {
    // 认证字段
    USERNAME,           // 用户名
    PASSWORD,           // 密码
    EMAIL,              // 邮箱
    PHONE,              // 电话号码
    
    // 验证字段
    OTP,                // 一次性验证码 (6位数字)
    SMS_CODE,           // 短信验证码
    
    // 个人信息
    PERSON_NAME,        // 姓名
    PERSON_NAME_GIVEN,  // 名
    PERSON_NAME_FAMILY, // 姓
    
    // 地址信息
    ADDRESS_LINE,       // 地址行
    ADDRESS_CITY,       // 城市
    ADDRESS_STATE,      // 省/州
    ADDRESS_ZIP,        // 邮编
    ADDRESS_COUNTRY,    // 国家
    
    // 支付信息
    CREDIT_CARD_NUMBER,         // 信用卡号
    CREDIT_CARD_HOLDER,         // Phase 7: 持卡人姓名
    CREDIT_CARD_EXPIRATION,     // 过期日期
    CREDIT_CARD_SECURITY_CODE,  // CVV
    
    // 其他
    UNKNOWN             // 未知类型
}

/**
 * 增强的字段集合
 * 支持多种字段类型
 */
data class EnhancedAutofillFieldCollection(
    // 认证字段
    var usernameField: AutofillId? = null,
    var passwordField: AutofillId? = null,
    var emailField: AutofillId? = null,
    var phoneField: AutofillId? = null,
    
    // 验证字段
    var otpField: AutofillId? = null,
    var smsCodeField: AutofillId? = null,
    
    // 个人信息
    var personNameField: AutofillId? = null,
    var givenNameField: AutofillId? = null,
    var familyNameField: AutofillId? = null,
    
    // 地址信息
    var addressLineField: AutofillId? = null,
    var cityField: AutofillId? = null,
    var stateField: AutofillId? = null,
    var zipField: AutofillId? = null,
    var countryField: AutofillId? = null,
    
    // 支付信息
    var creditCardNumberField: AutofillId? = null,
    var creditCardHolderField: AutofillId? = null,  // Phase 7: 持卡人姓名
    var creditCardExpirationField: AutofillId? = null,
    var creditCardSecurityCodeField: AutofillId? = null,
    
    // 字段值映射
    val fieldValues: MutableMap<AutofillId, String> = mutableMapOf(),
    
    // 字段类型映射
    val fieldTypes: MutableMap<AutofillId, AutofillFieldType> = mutableMapOf()
) {
    /**
     * 是否包含认证字段
     */
    fun hasCredentialFields(): Boolean {
        return usernameField != null || passwordField != null || emailField != null
    }
    
    /**
     * 是否包含验证码字段
     */
    fun hasOTPFields(): Boolean {
        return otpField != null || smsCodeField != null
    }
    
    /**
     * 是否包含个人信息字段
     */
    fun hasPersonalInfoFields(): Boolean {
        return personNameField != null || givenNameField != null || familyNameField != null
    }
    
    /**
     * 是否包含地址字段
     */
    fun hasAddressFields(): Boolean {
        return addressLineField != null || cityField != null || stateField != null
    }
    
    /**
     * 是否包含支付字段
     */
    fun hasPaymentFields(): Boolean {
        return creditCardNumberField != null || creditCardExpirationField != null
    }
    
    /**
     * 获取字段值
     */
    fun getFieldValue(autofillId: AutofillId?): String? {
        return autofillId?.let { fieldValues[it] }
    }
    
    /**
     * 设置字段值
     */
    fun setFieldValue(autofillId: AutofillId?, value: String?, type: AutofillFieldType) {
        if (autofillId != null && value != null) {
            fieldValues[autofillId] = value
            fieldTypes[autofillId] = type
        }
    }
}

/**
 * 智能字段检测器
 * 使用启发式算法和正则表达式识别字段类型
 */
object SmartFieldDetector {
    
    // Email 正则表达式
    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )
    
    // 手机号正则表达式 (中国)
    private val PHONE_PATTERN_CN = Pattern.compile(
        "^1[3-9]\\d{9}$"
    )
    
    // 手机号正则表达式 (国际)
    private val PHONE_PATTERN_INTL = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    )
    
    // OTP 验证码正则 (4-8位数字)
    private val OTP_PATTERN = Pattern.compile(
        "^\\d{4,8}$"
    )
    
    // 信用卡号正则 (13-19位数字)
    private val CREDIT_CARD_PATTERN = Pattern.compile(
        "^\\d{13,19}$"
    )
    
    // CVV 正则 (3-4位数字)
    private val CVV_PATTERN = Pattern.compile(
        "^\\d{3,4}$"
    )
    
    // 邮编正则 (中国6位数字)
    private val ZIP_CODE_CN_PATTERN = Pattern.compile(
        "^\\d{6}$"
    )
    
    /**
     * 检测字段类型
     * 
     * @param autofillHints Android官方提示
     * @param idEntry 字段ID
     * @param hint 输入提示
     * @param text 字段文本
     * @param inputType 输入类型
     * @return 检测到的字段类型
     */
    fun detectFieldType(
        autofillHints: Array<String>?,
        idEntry: String?,
        hint: String?,
        text: String?,
        inputType: Int
    ): AutofillFieldType {
        
        // 1. 优先检查官方 autofill hints
        autofillHints?.forEach { officialHint ->
            when (officialHint) {
                View.AUTOFILL_HINT_USERNAME -> return AutofillFieldType.USERNAME
                View.AUTOFILL_HINT_PASSWORD -> return AutofillFieldType.PASSWORD
                View.AUTOFILL_HINT_EMAIL_ADDRESS -> return AutofillFieldType.EMAIL
                View.AUTOFILL_HINT_PHONE -> return AutofillFieldType.PHONE
                View.AUTOFILL_HINT_NAME -> return AutofillFieldType.PERSON_NAME
                View.AUTOFILL_HINT_POSTAL_ADDRESS -> return AutofillFieldType.ADDRESS_LINE
                View.AUTOFILL_HINT_POSTAL_CODE -> return AutofillFieldType.ADDRESS_ZIP
                View.AUTOFILL_HINT_CREDIT_CARD_NUMBER -> return AutofillFieldType.CREDIT_CARD_NUMBER
                View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE -> return AutofillFieldType.CREDIT_CARD_EXPIRATION
                View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE -> return AutofillFieldType.CREDIT_CARD_SECURITY_CODE
                // Phase 7: 支持信用卡持卡人姓名 (API 26+)
                "creditCardExpirationMonth" -> return AutofillFieldType.CREDIT_CARD_EXPIRATION
                "creditCardExpirationYear" -> return AutofillFieldType.CREDIT_CARD_EXPIRATION
            }
        }
        
        // 2. 检查输入类型
        val typeFromInputType = detectFromInputType(inputType)
        if (typeFromInputType != AutofillFieldType.UNKNOWN) {
            return typeFromInputType
        }
        
        // 3. 使用关键词匹配
        val id = idEntry?.lowercase() ?: ""
        val hintText = hint?.lowercase() ?: ""
        val labelText = text?.lowercase() ?: ""
        val combined = "$id $hintText $labelText"
        
        // Email 检测
        if (matchesKeywords(combined, EMAIL_KEYWORDS)) {
            return AutofillFieldType.EMAIL
        }
        
        // 电话号码检测
        if (matchesKeywords(combined, PHONE_KEYWORDS)) {
            return AutofillFieldType.PHONE
        }
        
        // OTP/验证码检测
        if (matchesKeywords(combined, OTP_KEYWORDS)) {
            return AutofillFieldType.OTP
        }
        
        // 用户名检测
        if (matchesKeywords(combined, USERNAME_KEYWORDS)) {
            return AutofillFieldType.USERNAME
        }
        
        // 密码检测
        if (matchesKeywords(combined, PASSWORD_KEYWORDS)) {
            return AutofillFieldType.PASSWORD
        }
        
        // 姓名检测
        if (matchesKeywords(combined, NAME_KEYWORDS)) {
            return AutofillFieldType.PERSON_NAME
        }
        
        // 地址检测
        if (matchesKeywords(combined, ADDRESS_KEYWORDS)) {
            return AutofillFieldType.ADDRESS_LINE
        }
        
        // 城市检测
        if (matchesKeywords(combined, CITY_KEYWORDS)) {
            return AutofillFieldType.ADDRESS_CITY
        }
        
        // 省份/州检测 (Phase 7)
        if (matchesKeywords(combined, STATE_KEYWORDS)) {
            return AutofillFieldType.ADDRESS_STATE
        }
        
        // 国家检测 (Phase 7)
        if (matchesKeywords(combined, COUNTRY_KEYWORDS)) {
            return AutofillFieldType.ADDRESS_COUNTRY
        }
        
        // 邮编检测
        if (matchesKeywords(combined, ZIP_KEYWORDS)) {
            return AutofillFieldType.ADDRESS_ZIP
        }
        
        // 信用卡号检测
        if (matchesKeywords(combined, CREDIT_CARD_KEYWORDS)) {
            return AutofillFieldType.CREDIT_CARD_NUMBER
        }
        
        // 持卡人姓名检测 (Phase 7)
        if (matchesKeywords(combined, CARD_HOLDER_KEYWORDS)) {
            return AutofillFieldType.CREDIT_CARD_HOLDER
        }
        
        // 信用卡有效期检测 (Phase 7)
        if (matchesKeywords(combined, CARD_EXPIRY_KEYWORDS)) {
            return AutofillFieldType.CREDIT_CARD_EXPIRATION
        }
        
        // CVV检测
        if (matchesKeywords(combined, CVV_KEYWORDS)) {
            return AutofillFieldType.CREDIT_CARD_SECURITY_CODE
        }
        
        return AutofillFieldType.UNKNOWN
    }
    
    /**
     * 从输入类型检测字段类型
     */
    private fun detectFromInputType(inputType: Int): AutofillFieldType {
        // 密码类型
        if (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0) {
            return AutofillFieldType.PASSWORD
        }
        
        // Email类型
        if (inputType and android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != 0 ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != 0) {
            return AutofillFieldType.EMAIL
        }
        
        // 电话类型
        if (inputType and android.text.InputType.TYPE_CLASS_PHONE != 0) {
            return AutofillFieldType.PHONE
        }
        
        // 数字类型 (可能是验证码)
        if (inputType and android.text.InputType.TYPE_CLASS_NUMBER != 0) {
            // 需要进一步通过关键词判断
            return AutofillFieldType.UNKNOWN
        }
        
        return AutofillFieldType.UNKNOWN
    }
    
    /**
     * 验证Email格式
     */
    fun isValidEmail(email: String): Boolean {
        return EMAIL_PATTERN.matcher(email).matches()
    }
    
    /**
     * 验证手机号格式
     */
    fun isValidPhone(phone: String): Boolean {
        val cleanPhone = phone.replace(Regex("[\\s-()]"), "")
        return PHONE_PATTERN_CN.matcher(cleanPhone).matches() ||
               PHONE_PATTERN_INTL.matcher(cleanPhone).matches()
    }
    
    /**
     * 验证OTP格式
     */
    fun isValidOTP(otp: String): Boolean {
        return OTP_PATTERN.matcher(otp).matches()
    }
    
    /**
     * 验证信用卡号格式 (Luhn算法)
     */
    fun isValidCreditCard(cardNumber: String): Boolean {
        val cleanNumber = cardNumber.replace(Regex("[\\s-]"), "")
        if (!CREDIT_CARD_PATTERN.matcher(cleanNumber).matches()) {
            return false
        }
        
        // Luhn算法验证
        var sum = 0
        var alternate = false
        for (i in cleanNumber.length - 1 downTo 0) {
            var n = cleanNumber[i].toString().toInt()
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = n % 10 + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
    
    /**
     * 验证CVV格式
     */
    fun isValidCVV(cvv: String): Boolean {
        return CVV_PATTERN.matcher(cvv).matches()
    }
    
    /**
     * 验证邮编格式
     */
    fun isValidZipCode(zipCode: String): Boolean {
        return ZIP_CODE_CN_PATTERN.matcher(zipCode).matches()
    }
    
    /**
     * 关键词匹配
     */
    private fun matchesKeywords(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword) }
    }
    
    // ==================== 关键词定义 ====================
    
    private val EMAIL_KEYWORDS = listOf(
        "email", "e-mail", "mail", "邮箱", "邮件", "电子邮件"
    )
    
    private val PHONE_KEYWORDS = listOf(
        "phone", "mobile", "tel", "telephone", "cell", "contact",
        "电话", "手机", "联系方式", "号码"
    )
    
    private val OTP_KEYWORDS = listOf(
        "otp", "code", "verification", "verify", "sms", "captcha", "token",
        "验证码", "校验码", "动态码", "短信码", "确认码"
    )
    
    private val USERNAME_KEYWORDS = listOf(
        "user", "username", "userid", "login", "account", "id",
        "用户", "用户名", "账号", "登录名", "账户"
    )
    
    private val PASSWORD_KEYWORDS = listOf(
        "pass", "password", "pwd", "secret", "pin", "passphrase",
        "密码", "口令", "通行码"
    )
    
    private val NAME_KEYWORDS = listOf(
        "name", "fullname", "full_name",
        "姓名", "名字", "全名"
    )
    
    private val ADDRESS_KEYWORDS = listOf(
        "address", "street", "addr", "location",
        "地址", "详细地址", "街道"
    )
    
    private val CITY_KEYWORDS = listOf(
        "city", "town", "municipality",
        "城市", "市", "地级市"
    )
    
    // Phase 7: 省份/州关键词
    private val STATE_KEYWORDS = listOf(
        "state", "province", "region", "prefecture",
        "省", "省份", "州", "自治区", "特别行政区"
    )
    
    // Phase 7: 国家关键词
    private val COUNTRY_KEYWORDS = listOf(
        "country", "nation", "nationality",
        "国家", "国籍"
    )
    
    private val ZIP_KEYWORDS = listOf(
        "zip", "zipcode", "postal", "postcode", "postalcode",
        "邮编", "邮政编码"
    )
    
    private val CREDIT_CARD_KEYWORDS = listOf(
        "card", "cardnumber", "card_number", "credit", "debit", "payment",
        "卡号", "信用卡", "银行卡", "付款"
    )
    
    // Phase 7: 持卡人姓名关键词
    private val CARD_HOLDER_KEYWORDS = listOf(
        "cardholder", "card_holder", "cardname", "card_name", "name_on_card",
        "持卡人", "持卡人姓名", "卡主", "姓名"
    )
    
    // Phase 7: 信用卡有效期关键词
    private val CARD_EXPIRY_KEYWORDS = listOf(
        "expiry", "expiration", "exp", "expire", "valid", "validity",
        "有效期", "到期", "失效日期", "截止日期"
    )
    
    private val CVV_KEYWORDS = listOf(
        "cvv", "cvc", "cid", "security", "securitycode", "verification",
        "安全码", "验证码", "cvv码"
    )
}
