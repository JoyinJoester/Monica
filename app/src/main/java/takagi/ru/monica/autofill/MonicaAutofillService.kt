package takagi.ru.monica.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.pm.PackageManager
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.service.autofill.InlinePresentation
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import takagi.ru.monica.R
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedStructure
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.FieldHint

/**
 * Monica 自动填充服务 (增强版)
 * 
 * 提供密码和表单的自动填充功能
 * 
 * v2.0 更新：
 * - 集成增强的字段解析器（支持15+种语言）
 * - 准确度评分系统
 * - WebView 检测
 * - 更准确的字段识别
 * 优化版本：增强性能、错误处理和用户体验
 */
class MonicaAutofillService : AutofillService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var passwordRepository: PasswordRepository
    private lateinit var autofillPreferences: AutofillPreferences
    private lateinit var packageManager: PackageManager
    
    // ✨ 增强的字段解析器（支持15+种语言）
    private val enhancedParserV2 = EnhancedAutofillStructureParserV2()
    
    // SMS Retriever Helper for OTP auto-read
    private var smsRetrieverHelper: SmsRetrieverHelper? = null
    
    // 缓存应用信息以提高性能
    private val appInfoCache = mutableMapOf<String, String>()
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // 初始化 Repository
            val database = PasswordDatabase.getDatabase(applicationContext)
            passwordRepository = PasswordRepository(database.passwordEntryDao())
            
            // 初始化配置
            autofillPreferences = AutofillPreferences(applicationContext)
            packageManager = applicationContext.packageManager
            
            // 初始化SMS Retriever Helper
            smsRetrieverHelper = SmsRetrieverHelper(applicationContext)
            
            android.util.Log.d("MonicaAutofill", "Service created successfully")
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error initializing service", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        appInfoCache.clear()
        
        // 停止SMS Retriever
        smsRetrieverHelper?.stopSmsRetriever()
        smsRetrieverHelper = null
        
        android.util.Log.d("MonicaAutofill", "Service destroyed")
    }
    
    /**
     * 处理填充请求
     * 当用户聚焦到可以自动填充的字段时调用
     */
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        android.util.Log.d("MonicaAutofill", "onFillRequest called")
        
        serviceScope.launch {
            try {
                // 设置超时以避免长时间阻塞
                val result = withTimeoutOrNull(5000) {
                    processFillRequest(request, cancellationSignal)
                }
                
                if (result != null) {
                    callback.onSuccess(result)
                } else {
                    android.util.Log.w("MonicaAutofill", "Fill request timed out")
                    callback.onSuccess(null)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MonicaAutofill", "Error in onFillRequest", e)
                callback.onFailure(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 处理填充请求的核心逻辑
     */
    private suspend fun processFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal
    ): FillResponse? {
        // 检查是否启用自动填充
        val isEnabled = autofillPreferences.isAutofillEnabled.first()
        if (!isEnabled) {
            android.util.Log.d("MonicaAutofill", "Autofill disabled")
            return null
        }
        
        // 检查取消信号
        if (cancellationSignal.isCanceled) {
            android.util.Log.d("MonicaAutofill", "Request cancelled")
            return null
        }
        
        // 检查是否支持内联建议 (Android 11+)
        val inlineRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request.inlineSuggestionsRequest
        } else {
            null
        }
        
        if (inlineRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.util.Log.d("MonicaAutofill", "Inline suggestions supported, max suggestions: ${inlineRequest.maxSuggestionCount}")
        }
        
        // 解析填充上下文
        val context = request.fillContexts.lastOrNull()
        if (context == null) {
            android.util.Log.d("MonicaAutofill", "No fill context")
            return null
        }
        
        val structure = context.structure
        
        // ✨ 使用增强的字段解析器 V2（占位符实现）
        val respectAutofillOff = true // 默认尊重 autofill="off" 属性
        val parsedStructure = enhancedParserV2.parse(structure, respectAutofillOff)
        
        // 📊 记录增强解析结果
        android.util.Log.d("MonicaAutofill", "=== Enhanced Parser V2 Results (Placeholder) ===")
        android.util.Log.d("MonicaAutofill", "Application: ${parsedStructure.applicationId}")
        android.util.Log.d("MonicaAutofill", "WebView: ${parsedStructure.webView}")
        if (parsedStructure.webView) {
            android.util.Log.d("MonicaAutofill", "  WebDomain: ${parsedStructure.webDomain}")
            android.util.Log.d("MonicaAutofill", "  WebScheme: ${parsedStructure.webScheme}")
        }
        android.util.Log.d("MonicaAutofill", "Total fields found: ${parsedStructure.items.size}")
        
        parsedStructure.items.forEach { item ->
            android.util.Log.d("MonicaAutofill", "  ✓ ${item.hint} (accuracy: ${item.accuracy}, focused: ${item.isFocused})")
        }
        
        // 保留传统解析器作为后备和兼容性
        val enhancedParser = EnhancedAutofillFieldParser(structure)
        val enhancedCollection = enhancedParser.parse()
        
        val parser = AutofillFieldParser(structure)
        val fieldCollection = parser.parse()
        
        // 检查是否有可填充的凭据字段
        val hasUsernameOrEmail = parsedStructure.items.any { 
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS 
        }
        val hasPassword = parsedStructure.items.any { 
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        
        if (!hasUsernameOrEmail && !hasPassword) {
            android.util.Log.d("MonicaAutofill", "No credential fields found in enhanced parser")
            // 后备检查
            if (!fieldCollection.hasCredentialFields() && !enhancedCollection.hasCredentialFields()) {
                android.util.Log.d("MonicaAutofill", "No credential fields found in any parser")
                return null
            }
        }
        
        // 获取标识符（优先使用 webDomain，然后是 packageName）
        val packageName = parsedStructure.applicationId ?: structure.activityComponent.packageName
        val webDomain = parsedStructure.webDomain ?: parser.extractWebDomain()
        val identifier = webDomain ?: packageName
        
        android.util.Log.d("MonicaAutofill", "Identifier: $identifier (package: $packageName, web: $webDomain)")
        
        // 查找匹配的密码
        val matchedPasswords = findMatchingPasswords(packageName, identifier)
        
        android.util.Log.d("MonicaAutofill", "Found ${matchedPasswords.size} matched passwords")
        
        if (matchedPasswords.isEmpty()) {
            return null
        }
        
        // 🚀 构建填充响应（优先使用增强的 ParsedStructure）
        return buildFillResponseEnhanced(
            passwords = matchedPasswords, 
            parsedStructure = parsedStructure,
            fieldCollection = fieldCollection,
            enhancedCollection = enhancedCollection,
            packageName = packageName, 
            inlineRequest = inlineRequest
        )
    }
    
    /**
     * 查找匹配的密码条目
     */
    private suspend fun findMatchingPasswords(packageName: String, identifier: String): List<PasswordEntry> {
        val matchStrategy = autofillPreferences.domainMatchStrategy.first()
        val allPasswords = passwordRepository.getAllPasswordEntries().first()
        
        // 智能匹配算法：优先级排序
        val exactMatches = mutableListOf<PasswordEntry>()
        val domainMatches = mutableListOf<PasswordEntry>()
        val fuzzyMatches = mutableListOf<PasswordEntry>()
        
        allPasswords.forEach { password ->
            when {
                // 最高优先级：精确包名匹配
                password.appPackageName.isNotBlank() && password.appPackageName == packageName -> {
                    exactMatches.add(password)
                }
                // 中等优先级：域名匹配
                password.website.isNotBlank() && 
                DomainMatcher.matches(password.website, identifier, matchStrategy) -> {
                    domainMatches.add(password)
                }
                // 低优先级：模糊匹配（标题包含应用名）
                password.title.contains(getAppName(packageName), ignoreCase = true) -> {
                    fuzzyMatches.add(password)
                }
            }
        }
        
        // 按优先级返回，限制数量以提高性能
        val result = (exactMatches + domainMatches + fuzzyMatches).take(10)
        
        // 按最近使用时间排序
        return result.sortedByDescending { it.updatedAt }
    }
    
    /**
     * 构建填充响应
     * 支持智能字段检测，根据字段类型提供不同的建议
     */
    private suspend fun buildFillResponse(
        passwords: List<PasswordEntry>,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection,
        packageName: String,
        inlineRequest: InlineSuggestionsRequest? = null
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 获取内联建议规格列表 (Android 11+)
        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineRequest != null) {
            inlineRequest.inlinePresentationSpecs
        } else {
            null
        }
        
        val maxInlineSuggestions = inlineRequest?.maxSuggestionCount ?: 0
        
        // 检查特殊字段类型
        val hasOTPField = enhancedCollection.hasOTPFields()
        val hasEmailField = enhancedCollection.emailField != null
        val hasPhoneField = enhancedCollection.phoneField != null
        
        // 如果检测到OTP字段，启动SMS Retriever自动读取
        if (hasOTPField) {
            android.util.Log.d("MonicaAutofill", "OTP field detected - starting SMS Retriever")
            startOTPAutoRead(enhancedCollection)
        }
        
        // 为每个匹配的密码创建数据集
        passwords.forEachIndexed { index, password ->
            val datasetBuilder = Dataset.Builder()
            
            // 创建RemoteViews显示 (传统下拉菜单)
            val presentation = createPresentationView(password, packageName, index, enhancedCollection)
            
            // 如果支持内联建议,并且没有超过最大数量,添加内联显示
            val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R 
                && inlineSpecs != null 
                && inlineSpecs.isNotEmpty()
                && index < maxInlineSuggestions 
                && index < inlineSpecs.size) {
                createInlinePresentation(password, packageName, inlineSpecs[index])
            } else {
                null
            }
            
            // 智能填充：根据检测到的字段类型填充数据
            
            // 1. 填充用户名字段（优先使用智能检测）
            val usernameField = enhancedCollection.usernameField ?: fieldCollection.usernameField
            usernameField?.let { usernameId ->
                val usernameValue = if (hasEmailField && enhancedCollection.emailField == usernameId) {
                    // Email字段验证
                    if (SmartFieldDetector.isValidEmail(password.username)) {
                        password.username
                    } else {
                        // 用户名不是有效Email，记录警告
                        android.util.Log.w("MonicaAutofill", "Username '${password.username}' is not a valid email")
                        password.username
                    }
                } else {
                    password.username
                }
                
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        usernameId,
                        AutofillValue.forText(usernameValue),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        usernameId,
                        AutofillValue.forText(usernameValue),
                        presentation as RemoteViews
                    )
                }
            }
            
            // 2. 填充Email字段（如果独立于用户名）
            if (hasEmailField && enhancedCollection.emailField != enhancedCollection.usernameField) {
                enhancedCollection.emailField?.let { emailId ->
                    // 验证Email格式
                    val emailValue = if (SmartFieldDetector.isValidEmail(password.username)) {
                        password.username
                    } else {
                        // 从密码条目中寻找Email字段（如果有扩展字段）
                        android.util.Log.w("MonicaAutofill", "No valid email found for password entry")
                        ""
                    }
                    
                    if (emailValue.isNotEmpty()) {
                        if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            @Suppress("NewApi")
                            datasetBuilder.setValue(
                                emailId,
                                AutofillValue.forText(emailValue),
                                presentation as RemoteViews,
                                inlinePresentation as InlinePresentation
                            )
                        } else {
                            datasetBuilder.setValue(
                                emailId,
                                AutofillValue.forText(emailValue),
                                presentation as RemoteViews
                            )
                        }
                    }
                }
            }
            
            // 3. 填充电话号码字段 (Phase 7)
            if (hasPhoneField && password.phone.isNotEmpty()) {
                enhancedCollection.phoneField?.let { phoneId ->
                    // 使用 FieldValidation 格式化电话号码
                    val formattedPhone = takagi.ru.monica.utils.FieldValidation.formatPhone(password.phone)
                    
                    if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        @Suppress("NewApi")
                        datasetBuilder.setValue(
                            phoneId,
                            AutofillValue.forText(password.phone),
                            presentation as RemoteViews,
                            inlinePresentation as InlinePresentation
                        )
                    } else {
                        datasetBuilder.setValue(
                            phoneId,
                            AutofillValue.forText(password.phone),
                            presentation as RemoteViews
                        )
                    }
                    android.util.Log.d("MonicaAutofill", "📱 Phone field filled: $formattedPhone")
                }
            }
            
            // 4. 填充密码字段
            val passwordField = enhancedCollection.passwordField ?: fieldCollection.passwordField
            passwordField?.let { passwordId ->
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        passwordId,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        passwordId,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews
                    )
                }
            }
            
            // Phase 7: 5. 填充地址字段
            if (enhancedCollection.hasAddressFields()) {
                // 地址行
                if (password.addressLine.isNotEmpty()) {
                    enhancedCollection.addressLineField?.let { addressId ->
                        datasetBuilder.setValue(
                            addressId,
                            AutofillValue.forText(password.addressLine),
                            presentation as RemoteViews
                        )
                        android.util.Log.d("MonicaAutofill", "🏠 Address line filled")
                    }
                }
                
                // 城市
                if (password.city.isNotEmpty()) {
                    enhancedCollection.cityField?.let { cityId ->
                        datasetBuilder.setValue(
                            cityId,
                            AutofillValue.forText(password.city),
                            presentation as RemoteViews
                        )
                    }
                }
                
                // 省份/州
                if (password.state.isNotEmpty()) {
                    enhancedCollection.stateField?.let { stateId ->
                        datasetBuilder.setValue(
                            stateId,
                            AutofillValue.forText(password.state),
                            presentation as RemoteViews
                        )
                    }
                }
                
                // 邮编
                if (password.zipCode.isNotEmpty()) {
                    enhancedCollection.zipField?.let { zipId ->
                        datasetBuilder.setValue(
                            zipId,
                            AutofillValue.forText(password.zipCode),
                            presentation as RemoteViews
                        )
                    }
                }
                
                // 国家
                if (password.country.isNotEmpty()) {
                    enhancedCollection.countryField?.let { countryId ->
                        datasetBuilder.setValue(
                            countryId,
                            AutofillValue.forText(password.country),
                            presentation as RemoteViews
                        )
                    }
                }
            }
            
            // Phase 7: 6. 填充信用卡字段
            if (enhancedCollection.hasPaymentFields()) {
                // 信用卡号 (掩码显示)
                if (password.creditCardNumber.isNotEmpty()) {
                    enhancedCollection.creditCardNumberField?.let { cardId ->
                        // TODO: 解密信用卡号
                        val cardNumber = password.creditCardNumber
                        datasetBuilder.setValue(
                            cardId,
                            AutofillValue.forText(cardNumber),
                            presentation as RemoteViews
                        )
                        android.util.Log.d("MonicaAutofill", "💳 Credit card number filled")
                    }
                }
                
                // 持卡人姓名
                if (password.creditCardHolder.isNotEmpty()) {
                    enhancedCollection.creditCardHolderField?.let { holderId ->
                        datasetBuilder.setValue(
                            holderId,
                            AutofillValue.forText(password.creditCardHolder),
                            presentation as RemoteViews
                        )
                    }
                }
                
                // 有效期
                if (password.creditCardExpiry.isNotEmpty()) {
                    enhancedCollection.creditCardExpirationField?.let { expiryId ->
                        datasetBuilder.setValue(
                            expiryId,
                            AutofillValue.forText(password.creditCardExpiry),
                            presentation as RemoteViews
                        )
                    }
                }
                
                // CVV (解密)
                if (password.creditCardCVV.isNotEmpty()) {
                    enhancedCollection.creditCardSecurityCodeField?.let { cvvId ->
                        // TODO: 解密CVV
                        val cvv = password.creditCardCVV
                        datasetBuilder.setValue(
                            cvvId,
                            AutofillValue.forText(cvv),
                            presentation as RemoteViews
                        )
                    }
                }
            }
            
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        // 添加保存信息（如果启用）
        val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
        if (requestSaveData) {
            val saveInfoBuilder = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                arrayOf(
                    fieldCollection.usernameField,
                    fieldCollection.passwordField
                ).filterNotNull().toTypedArray()
            )
            
            // 设置保存提示
            saveInfoBuilder.setDescription("保存到 Monica 密码管理器")
            
            responseBuilder.setSaveInfo(saveInfoBuilder.build())
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 🚀 构建填充响应（增强版）
     * 使用 EnhancedAutofillStructureParserV2 的解析结果
     * 
     * @param passwords 匹配的密码列表
     * @param parsedStructure 增强解析器 V2 的解析结果
     * @param fieldCollection 传统字段集合（后备）
     * @param enhancedCollection 增强字段集合（后备）
     * @param packageName 应用包名
     * @param inlineRequest 内联建议请求（Android 11+）
     * @return FillResponse 填充响应
     */
    private suspend fun buildFillResponseEnhanced(
        passwords: List<PasswordEntry>,
        parsedStructure: ParsedStructure,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection,
        packageName: String,
        inlineRequest: InlineSuggestionsRequest? = null
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 获取内联建议规格列表 (Android 11+)
        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineRequest != null) {
            inlineRequest.inlinePresentationSpecs
        } else {
            null
        }
        
        val maxInlineSuggestions = inlineRequest?.maxSuggestionCount ?: 0
        
        // 📊 分析解析结果
        val usernameItems = parsedStructure.items.filter { it.hint == FieldHint.USERNAME }
        val emailItems = parsedStructure.items.filter { it.hint == FieldHint.EMAIL_ADDRESS }
        val passwordItems = parsedStructure.items.filter { it.hint == FieldHint.PASSWORD }
        val newPasswordItems = parsedStructure.items.filter { it.hint == FieldHint.NEW_PASSWORD }
        val phoneItems = parsedStructure.items.filter { it.hint == FieldHint.PHONE_NUMBER }
        val otpItems = parsedStructure.items.filter { it.hint == FieldHint.OTP_CODE }
        
        android.util.Log.d("MonicaAutofill", "=== Field Distribution ===")
        android.util.Log.d("MonicaAutofill", "Username: ${usernameItems.size}, Email: ${emailItems.size}")
        android.util.Log.d("MonicaAutofill", "Password: ${passwordItems.size}, NewPassword: ${newPasswordItems.size}")
        android.util.Log.d("MonicaAutofill", "Phone: ${phoneItems.size}, OTP: ${otpItems.size}")
        
        // 如果检测到OTP字段，启动SMS Retriever自动读取
        if (otpItems.isNotEmpty()) {
            android.util.Log.d("MonicaAutofill", "OTP field detected - starting SMS Retriever")
            startOTPAutoRead(enhancedCollection)
        }
        
        // 为每个匹配的密码创建数据集
        passwords.forEachIndexed { index, password ->
            val datasetBuilder = Dataset.Builder()
            
            // 创建RemoteViews显示 (传统下拉菜单)
            val presentation = createPresentationView(password, packageName, index, enhancedCollection)
            
            // 如果支持内联建议,并且没有超过最大数量,添加内联显示
            val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R 
                && inlineSpecs != null 
                && inlineSpecs.isNotEmpty()
                && index < maxInlineSuggestions 
                && index < inlineSpecs.size) {
                createInlinePresentation(password, packageName, inlineSpecs[index])
            } else {
                null
            }
            
            // ✨ 智能填充：根据 ParsedStructure 中的字段类型填充数据
            
            // 1. 填充用户名字段（选择准确度最高的一个）
            val bestUsernameItem = usernameItems.maxByOrNull { it.accuracy.score }
            bestUsernameItem?.let { item ->
                val usernameValue = password.username
                
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(usernameValue),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(usernameValue),
                        presentation as RemoteViews
                    )
                }
                android.util.Log.d("MonicaAutofill", "✓ Username filled (accuracy: ${item.accuracy})")
            }
            
            // 2. 填充Email字段（如果独立于用户名字段）
            val bestEmailItem = emailItems.maxByOrNull { it.accuracy.score }
            if (bestEmailItem != null && bestEmailItem.id != bestUsernameItem?.id) {
                // 验证Email格式
                val emailValue = if (SmartFieldDetector.isValidEmail(password.username)) {
                    password.username
                } else {
                    ""
                }
                
                if (emailValue.isNotEmpty()) {
                    if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        @Suppress("NewApi")
                        datasetBuilder.setValue(
                            bestEmailItem.id,
                            AutofillValue.forText(emailValue),
                            presentation as RemoteViews,
                            inlinePresentation as InlinePresentation
                        )
                    } else {
                        datasetBuilder.setValue(
                            bestEmailItem.id,
                            AutofillValue.forText(emailValue),
                            presentation as RemoteViews
                        )
                    }
                    android.util.Log.d("MonicaAutofill", "✓ Email filled (accuracy: ${bestEmailItem.accuracy})")
                }
            }
            
            // 3. 填充电话号码字段
            val bestPhoneItem = phoneItems.maxByOrNull { it.accuracy.score }
            if (bestPhoneItem != null && password.phone.isNotEmpty()) {
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        bestPhoneItem.id,
                        AutofillValue.forText(password.phone),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        bestPhoneItem.id,
                        AutofillValue.forText(password.phone),
                        presentation as RemoteViews
                    )
                }
                android.util.Log.d("MonicaAutofill", "✓ Phone filled (accuracy: ${bestPhoneItem.accuracy})")
            }
            
            // 4. 填充密码字段（选择准确度最高的一个）
            val bestPasswordItem = passwordItems.maxByOrNull { it.accuracy.score }
            bestPasswordItem?.let { item ->
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews
                    )
                }
                android.util.Log.d("MonicaAutofill", "✓ Password filled (accuracy: ${item.accuracy})")
            }
            
            // 5. 填充新密码字段（用于注册/修改密码场景）
            newPasswordItems.forEach { item ->
                if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("NewApi")
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews,
                        inlinePresentation as InlinePresentation
                    )
                } else {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews
                    )
                }
                android.util.Log.d("MonicaAutofill", "✓ New password filled (accuracy: ${item.accuracy})")
            }
            
            // 6. 填充信用卡字段
            val creditCardItems = parsedStructure.items.filter { 
                it.hint == FieldHint.CREDIT_CARD_NUMBER || 
                it.hint == FieldHint.CREDIT_CARD_EXPIRATION_DATE ||
                it.hint == FieldHint.CREDIT_CARD_SECURITY_CODE
            }
            
            creditCardItems.forEach { item ->
                val value = when (item.hint) {
                    FieldHint.CREDIT_CARD_NUMBER -> password.creditCardNumber
                    FieldHint.CREDIT_CARD_EXPIRATION_DATE -> password.creditCardExpiry
                    FieldHint.CREDIT_CARD_SECURITY_CODE -> password.creditCardCVV
                    else -> ""
                }
                
                if (value.isNotEmpty()) {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(value),
                        presentation as RemoteViews
                    )
                    android.util.Log.d("MonicaAutofill", "✓ Credit card field filled: ${item.hint}")
                }
            }
            
            // 7. 填充地址字段
            val addressItems = parsedStructure.items.filter { 
                it.hint == FieldHint.POSTAL_ADDRESS || it.hint == FieldHint.POSTAL_CODE
            }
            
            addressItems.forEach { item ->
                val value = when (item.hint) {
                    FieldHint.POSTAL_ADDRESS -> password.addressLine
                    FieldHint.POSTAL_CODE -> password.zipCode
                    else -> ""
                }
                
                if (value.isNotEmpty()) {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(value),
                        presentation as RemoteViews
                    )
                    android.util.Log.d("MonicaAutofill", "✓ Address field filled: ${item.hint}")
                }
            }
            
            // 8. 填充姓名字段
            val nameItems = parsedStructure.items.filter { it.hint == FieldHint.PERSON_NAME }
            nameItems.forEach { item ->
                if (password.creditCardHolder.isNotEmpty()) {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(password.creditCardHolder),
                        presentation as RemoteViews
                    )
                    android.util.Log.d("MonicaAutofill", "✓ Name field filled")
                }
            }
            
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        // 添加保存信息（如果启用）
        val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
        if (requestSaveData) {
            // 收集所有用户名和密码字段的ID
            val saveFieldIds = mutableListOf<AutofillId>()
            usernameItems.forEach { saveFieldIds.add(it.id) }
            emailItems.forEach { saveFieldIds.add(it.id) }
            passwordItems.forEach { saveFieldIds.add(it.id) }
            newPasswordItems.forEach { saveFieldIds.add(it.id) }
            
            if (saveFieldIds.isNotEmpty()) {
                val saveInfoBuilder = SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    saveFieldIds.toTypedArray()
                )
                
                // 设置保存提示
                saveInfoBuilder.setDescription("保存到 Monica 密码管理器")
                
                responseBuilder.setSaveInfo(saveInfoBuilder.build())
            }
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 创建展示视图
     * 支持智能字段类型显示
     */
    private fun createPresentationView(
        password: PasswordEntry,
        packageName: String,
        index: Int,
        enhancedCollection: EnhancedAutofillFieldCollection
    ): RemoteViews {
        val presentation = RemoteViews(this.packageName, R.layout.autofill_dataset_item)
        
        // 设置标题
        val displayTitle = if (password.title.isNotBlank()) {
            password.title
        } else {
            getAppName(packageName)
        }
        presentation.setTextViewText(R.id.text_title, displayTitle)
        
        // 设置用户名或副标题（根据智能字段检测结果）
        val displayUsername = when {
            // Phase 7: 地址字段优先级
            enhancedCollection.hasAddressFields() && password.addressLine.isNotEmpty() -> {
                "🏠 ${password.city.ifEmpty { "地址信息" }}"
            }
            // Phase 7: 信用卡字段优先级
            enhancedCollection.hasPaymentFields() && password.creditCardNumber.isNotEmpty() -> {
                val masked = takagi.ru.monica.utils.FieldValidation.maskCreditCard(password.creditCardNumber)
                "💳 $masked"
            }
            // Phase 7: 电话字段 - 显示格式化的电话号码
            enhancedCollection.phoneField != null && password.phone.isNotEmpty() -> {
                val masked = takagi.ru.monica.utils.FieldValidation.maskPhone(password.phone)
                "📱 $masked"
            }
            enhancedCollection.emailField != null && password.username.isNotBlank() -> {
                // Email字段 - 显示Email地址
                if (SmartFieldDetector.isValidEmail(password.username)) {
                    "📧 ${password.username}"
                } else {
                    password.username
                }
            }
            enhancedCollection.phoneField != null -> {
                // 电话字段 - 显示电话图标
                "📱 电话号码填充"
            }
            enhancedCollection.hasOTPFields() -> {
                // OTP字段 - 提示等待SMS
                "🔐 等待验证码..."
            }
            password.username.isNotBlank() -> {
                password.username
            }
            else -> {
                "无用户名"
            }
        }
        presentation.setTextViewText(R.id.text_username, displayUsername)
        
        // 设置图标（如果有应用包名）
        if (password.appPackageName.isNotBlank()) {
            try {
                val appIcon = packageManager.getApplicationIcon(password.appPackageName)
                presentation.setImageViewBitmap(R.id.icon_app, 
                    android.graphics.drawable.BitmapDrawable(resources, 
                        (appIcon as android.graphics.drawable.BitmapDrawable).bitmap).bitmap)
            } catch (e: Exception) {
                // 使用默认图标
                presentation.setImageViewResource(R.id.icon_app, R.drawable.ic_key)
            }
        } else {
            presentation.setImageViewResource(R.id.icon_app, R.drawable.ic_web)
        }
        
        return presentation
    }
    
    /**
     * 获取应用名称（带缓存）
     */
    private fun getAppName(packageName: String): String {
        return appInfoCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.split(".").lastOrNull() ?: packageName
            }
        }
    }
    
    /**
     * 创建内联展示 (Android 11+)
     * 在输入框下方直接显示密码建议
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createInlinePresentation(
        password: PasswordEntry,
        callingPackage: String,
        inlineSpec: InlinePresentationSpec
    ): InlinePresentation? {
        try {
            // 检查是否支持 UiVersions.INLINE_UI_VERSION_1
            if (!UiVersions.getVersions(inlineSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                android.util.Log.w("MonicaAutofill", "Inline UI version 1 not supported")
                return null
            }
            
            // 获取应用图标
            val appIcon = try {
                val appPackageName = password.appPackageName.ifBlank { callingPackage }
                if (appPackageName.isNotBlank()) {
                    try {
                        val drawable = packageManager.getApplicationIcon(appPackageName)
                        // 将Drawable转换为Icon
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            Icon.createWithBitmap(drawable.bitmap)
                        } else {
                            Icon.createWithResource(this, R.mipmap.ic_launcher)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MonicaAutofill", "Failed to load app icon", e)
                        Icon.createWithResource(this, R.mipmap.ic_launcher)
                    }
                } else {
                    Icon.createWithResource(this, R.mipmap.ic_launcher)
                }
            } catch (e: Exception) {
                android.util.Log.w("MonicaAutofill", "Failed to create icon", e)
                Icon.createWithResource(this, R.mipmap.ic_launcher)
            }
            
            // 构建显示文本
            val username = password.username.ifBlank { "（无用户名）" }
            val subtitle = when {
                password.appName.isNotBlank() -> password.appName
                password.website.isNotBlank() -> password.website
                password.title.isNotBlank() -> password.title
                else -> getAppName(callingPackage)
            }
            
            // 使用 InlineSuggestionUi 构建内联UI
            val inlineUi = InlineSuggestionUi.newContentBuilder(
                PendingIntent.getActivity(
                    this,
                    0,
                    android.content.Intent(),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).apply {
                setTitle(username)
                setSubtitle(subtitle)
                setStartIcon(appIcon)
                setContentDescription("自动填充: $username")
            }.build()
            
            // 将 InlineSuggestionUi 转换为 Slice
            val slice = inlineUi.slice
            
            // 创建 InlinePresentation
            return InlinePresentation(slice, inlineSpec, false)
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error creating inline presentation", e)
            return null
        }
    }
    
    /**
     * 处理保存请求
     * 当用户提交表单时调用，可以保存新的密码或更新现有密码
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        android.util.Log.d("MonicaAutofill", "onSaveRequest called")
        
        serviceScope.launch {
            try {
                val result = withTimeoutOrNull(3000) {
                    processSaveRequest(request)
                }
                
                if (result == true) {
                    callback.onSuccess()
                } else {
                    android.util.Log.w("MonicaAutofill", "Save request failed or timed out")
                    callback.onSuccess() // 即使失败也返回成功，避免系统重试
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MonicaAutofill", "Error in onSaveRequest", e)
                callback.onFailure(e.message ?: "保存失败")
            }
        }
    }
    
    /**
     * 处理保存请求的核心逻辑
     */
    private suspend fun processSaveRequest(request: SaveRequest): Boolean {
        val context = request.fillContexts.lastOrNull() ?: return false
        
        val structure = context.structure
        val parser = AutofillFieldParser(structure)
        val fieldCollection = parser.parse()
        
        // 提取用户名和密码
        val username = fieldCollection.usernameValue ?: ""
        val password = fieldCollection.passwordValue ?: ""
        
        if (username.isBlank() && password.isBlank()) {
            android.util.Log.d("MonicaAutofill", "No credentials to save")
            return false
        }
        
        // 获取包名或域名
        val packageName = structure.activityComponent.packageName
        val webDomain = parser.extractWebDomain()
        val website = webDomain ?: ""
        
        android.util.Log.d("MonicaAutofill", "Save request - username: $username, website: $website, package: $packageName")
        
        // 检查是否启用保存功能
        val requestSaveEnabled = autofillPreferences.isRequestSaveDataEnabled.first()
        if (!requestSaveEnabled) {
            android.util.Log.d("MonicaAutofill", "Save request disabled")
            return false
        }
        
        // 检查是否已存在相同的密码
        val existingPasswords = passwordRepository.getAllPasswordEntries().first()
        val isDuplicate = existingPasswords.any { entry ->
            (entry.appPackageName == packageName || entry.website == website) &&
            entry.username == username &&
            entry.password == password
        }
        
        if (isDuplicate) {
            android.util.Log.d("MonicaAutofill", "Duplicate password, skipping save")
            return true
        }
        
        // 启动保存Activity
        val saveIntent = android.content.Intent(applicationContext, AutofillSaveTransparentActivity::class.java).apply {
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, username)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, password)
            putExtra(AutofillSaveTransparentActivity.EXTRA_WEBSITE, website)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        startActivity(saveIntent)
        return true
    }
    
    override fun onConnected() {
        super.onConnected()
        android.util.Log.d("MonicaAutofill", "Service connected")
    }
    
    override fun onDisconnected() {
        super.onDisconnected()
        android.util.Log.d("MonicaAutofill", "Service disconnected")
    }
    
    /**
     * 启动OTP自动读取
     * 使用SMS Retriever API监听短信，自动提取验证码
     * 
     * @param enhancedCollection 增强字段集合，包含OTP字段信息
     */
    private fun startOTPAutoRead(enhancedCollection: EnhancedAutofillFieldCollection) {
        val helper = smsRetrieverHelper
        if (helper == null) {
            android.util.Log.w("MonicaAutofill", "SMS Retriever Helper not initialized")
            return
        }
        
        // 检查SMS Retriever是否可用
        if (!helper.isSmsRetrieverAvailable()) {
            android.util.Log.w("MonicaAutofill", "SMS Retriever API not available on this device")
            return
        }
        
        // 获取OTP字段ID
        val otpFieldId = enhancedCollection.otpField ?: enhancedCollection.smsCodeField
        if (otpFieldId == null) {
            android.util.Log.w("MonicaAutofill", "No OTP field found in enhanced collection")
            return
        }
        
        android.util.Log.d("MonicaAutofill", "Starting OTP auto-read for field: $otpFieldId")
        
        // 启动SMS监听
        val success = helper.startSmsRetriever { otp ->
            android.util.Log.d("MonicaAutofill", "OTP received: $otp")
            
            // 验证OTP格式
            if (OtpExtractor.isValidOTP(otp)) {
                // 自动填充OTP
                fillOTPField(otpFieldId, otp)
            } else {
                android.util.Log.w("MonicaAutofill", "Invalid OTP format: $otp")
            }
        }
        
        if (success) {
            android.util.Log.d("MonicaAutofill", "OTP auto-read started successfully")
        } else {
            android.util.Log.e("MonicaAutofill", "Failed to start OTP auto-read")
        }
    }
    
    /**
     * 填充OTP字段
     * 
     * @param otpFieldId OTP字段的AutofillId
     * @param otp 验证码
     */
    private fun fillOTPField(otpFieldId: AutofillId, otp: String) {
        try {
            android.util.Log.d("MonicaAutofill", "Attempting to fill OTP field with: $otp")
            
            // 创建填充响应
            val fillResponse = FillResponse.Builder()
            val dataset = Dataset.Builder()
            
            // 创建简单的RemoteViews显示
            val presentation = RemoteViews(this.packageName, android.R.layout.simple_list_item_1)
            presentation.setTextViewText(android.R.id.text1, "验证码: ${OtpExtractor.formatOTP(otp)}")
            
            // 设置OTP值
            dataset.setValue(
                otpFieldId,
                AutofillValue.forText(otp),
                presentation
            )
            
            fillResponse.addDataset(dataset.build())
            
            android.util.Log.d("MonicaAutofill", "OTP fill response created successfully")
            
            // Note: 这里我们创建了填充响应，但实际填充需要通过FillCallback
            // 由于SMS Retriever是异步的，我们可能需要使用其他机制来触发填充
            // 这是一个简化版本，实际应用中可能需要更复杂的实现
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error filling OTP field", e)
        }
    }
}

/**
 * 自动填充字段解析器
 * 增强版：更智能的字段识别
 */
private class AutofillFieldParser(private val structure: AssistStructure) {
    
    fun parse(): AutofillFieldCollection {
        val collection = AutofillFieldCollection()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, collection)
        }
        
        // 如果没有找到字段，尝试更宽松的匹配
        if (!collection.hasCredentialFields()) {
            parseWithFallback(collection)
        }
        
        return collection
    }
    
    private fun parseNode(node: AssistStructure.ViewNode, collection: AutofillFieldCollection) {
        // 检查autofill hints
        node.autofillHints?.forEach { hint ->
            when (hint) {
                android.view.View.AUTOFILL_HINT_USERNAME,
                android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS -> {
                    if (collection.usernameField == null) {
                        collection.usernameField = node.autofillId
                        collection.usernameValue = node.autofillValue?.textValue?.toString()
                    }
                }
                android.view.View.AUTOFILL_HINT_PASSWORD -> {
                    if (collection.passwordField == null) {
                        collection.passwordField = node.autofillId
                        collection.passwordValue = node.autofillValue?.textValue?.toString()
                    }
                }
            }
        }
        
        // 如果没有hint，尝试通过多种方式推断
        if (node.autofillHints.isNullOrEmpty() && node.autofillId != null) {
            val idEntry = node.idEntry?.lowercase() ?: ""
            val hint = node.hint?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val className = node.className ?: ""
            
            // 检查是否是输入字段
            val isInputField = className.contains("EditText") || 
                              className.contains("TextInputEditText") ||
                              node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT
            
            if (isInputField) {
                when {
                    // 用户名字段识别
                    isUsernameField(idEntry, hint, text) -> {
                        if (collection.usernameField == null) {
                            collection.usernameField = node.autofillId
                            collection.usernameValue = node.autofillValue?.textValue?.toString()
                        }
                    }
                    // 密码字段识别
                    isPasswordField(idEntry, hint, text, node) -> {
                        if (collection.passwordField == null) {
                            collection.passwordField = node.autofillId
                            collection.passwordValue = node.autofillValue?.textValue?.toString()
                        }
                    }
                }
            }
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), collection)
        }
    }
    
    /**
     * 判断是否是用户名字段
     */
    private fun isUsernameField(idEntry: String, hint: String, text: String): Boolean {
        val usernameKeywords = listOf(
            "user", "username", "email", "login", "account", "id",
            "用户", "账号", "邮箱", "登录"
        )
        
        return usernameKeywords.any { keyword ->
            idEntry.contains(keyword) || hint.contains(keyword) || text.contains(keyword)
        }
    }
    
    /**
     * 判断是否是密码字段
     */
    private fun isPasswordField(idEntry: String, hint: String, text: String, node: AssistStructure.ViewNode): Boolean {
        val passwordKeywords = listOf(
            "pass", "password", "pwd", "secret", "pin",
            "密码", "口令"
        )
        
        // 检查输入类型
        val isPasswordInput = node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
                             node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 ||
                             node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0
        
        return isPasswordInput || passwordKeywords.any { keyword ->
            idEntry.contains(keyword) || hint.contains(keyword) || text.contains(keyword)
        }
    }
    
    /**
     * 备用解析方法：更宽松的字段识别
     */
    private fun parseWithFallback(collection: AutofillFieldCollection) {
        // 如果标准方法失败，尝试查找所有文本输入字段
        val textFields = mutableListOf<AssistStructure.ViewNode>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectTextFields(windowNode.rootViewNode, textFields)
        }
        
        // 简单启发式：第一个文本字段可能是用户名，密码类型的字段是密码
        textFields.forEach { node ->
            val isPasswordInput = node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
            
            when {
                isPasswordInput && collection.passwordField == null -> {
                    collection.passwordField = node.autofillId
                    collection.passwordValue = node.autofillValue?.textValue?.toString()
                }
                !isPasswordInput && collection.usernameField == null -> {
                    collection.usernameField = node.autofillId
                    collection.usernameValue = node.autofillValue?.textValue?.toString()
                }
            }
        }
    }
    
    /**
     * 收集所有文本输入字段
     */
    private fun collectTextFields(node: AssistStructure.ViewNode, fields: MutableList<AssistStructure.ViewNode>) {
        if (node.autofillId != null && 
            node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT &&
            (node.className?.contains("EditText") == true || 
             node.className?.contains("TextInputEditText") == true)) {
            fields.add(node)
        }
        
        for (i in 0 until node.childCount) {
            collectTextFields(node.getChildAt(i), fields)
        }
    }
    
    fun extractWebDomain(): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = extractWebDomainFromNode(windowNode.rootViewNode)
            if (domain != null) {
                return domain
            }
        }
        return null
    }
    
    private fun extractWebDomainFromNode(node: AssistStructure.ViewNode): String? {
        // 检查webDomain
        node.webDomain?.let { return it }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val domain = extractWebDomainFromNode(node.getChildAt(i))
            if (domain != null) {
                return domain
            }
        }
        
        return null
    }
}

/**
 * 自动填充字段集合
 */
private data class AutofillFieldCollection(
    var usernameField: AutofillId? = null,
    var passwordField: AutofillId? = null,
    var usernameValue: String? = null,
    var passwordValue: String? = null
) {
    fun hasCredentialFields(): Boolean {
        return usernameField != null || passwordField != null
    }
}
