package takagi.ru.monica.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
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
import takagi.ru.monica.autofill.di.AutofillDI
import takagi.ru.monica.autofill.engine.AutofillEngine
import takagi.ru.monica.autofill.data.AutofillRepository
import takagi.ru.monica.autofill.data.AutofillCache
import takagi.ru.monica.autofill.core.AutofillLogger
import org.koin.android.ext.android.inject
import takagi.ru.monica.autofill.core.AutofillDiagnostics
import takagi.ru.monica.autofill.core.ImprovedFieldParser
import takagi.ru.monica.autofill.core.EnhancedPasswordMatcher
import takagi.ru.monica.autofill.core.SafeResponseBuilder
import takagi.ru.monica.autofill.core.safeTextOrNull
import takagi.ru.monica.autofill.data.AutofillContext
import takagi.ru.monica.autofill.data.PasswordMatch
import takagi.ru.monica.utils.DeviceUtils
import takagi.ru.monica.utils.PermissionGuide

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
    
    // ✨ 增强的字段解析器（支持15+种语言）- Koin 注入
    private val enhancedParserV2: EnhancedAutofillStructureParserV2 by inject()
    
    // 🚀 新架构：自动填充引擎 - Koin 注入
    private val autofillEngine: AutofillEngine by inject()
    
    // 📦 数据仓库和缓存 - Koin 注入
    private val autofillRepository: AutofillRepository by inject()
    private val autofillCache: AutofillCache by inject()
    
    // SMS Retriever Helper for OTP auto-read
    private var smsRetrieverHelper: SmsRetrieverHelper? = null
    
    // 🔍 诊断系统
    private lateinit var diagnostics: AutofillDiagnostics
    
    // 缓存应用信息以提高性能
    private val appInfoCache = mutableMapOf<String, String>()
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            AutofillLogger.i("SERVICE", "MonicaAutofillService onCreate() - Initializing...")
            
            // 🎯 记录设备信息（用于品牌适配诊断）
            val deviceSummary = DeviceUtils.getDeviceSummary()
            AutofillLogger.i("SERVICE", "Device Summary:\n$deviceSummary")
            android.util.Log.d("MonicaAutofill", "Device Summary:\n$deviceSummary")
            
            // 初始化 Repository
            val database = PasswordDatabase.getDatabase(applicationContext)
            passwordRepository = PasswordRepository(database.passwordEntryDao())
            
            // 初始化配置
            autofillPreferences = AutofillPreferences(applicationContext)
            packageManager = applicationContext.packageManager
            
            // 初始化SMS Retriever Helper
            smsRetrieverHelper = SmsRetrieverHelper(applicationContext)
            
            // 🔍 初始化诊断系统
            diagnostics = AutofillDiagnostics(applicationContext)
            
            // 🚀 预初始化自动填充引擎
            autofillEngine
            
            AutofillLogger.i("SERVICE", "Service created successfully")
            android.util.Log.d("MonicaAutofill", "Service created successfully")
        } catch (e: Exception) {
            AutofillLogger.e("SERVICE", "Error initializing service", e)
            android.util.Log.e("MonicaAutofill", "Error initializing service", e)
        }
    }
    
    /**
     * 🔧 从结构中提取域名（Chrome专用）
     */
    private fun extractDomainFromStructure(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = extractDomainFromNode(windowNode.rootViewNode)
            if (domain != null) {
                android.util.Log.d("MonicaAutofill", "✓ Extracted domain from structure: $domain")
                return domain
            }
        }
        return null
    }
    
    /**
     * 🔧 递归提取域名
     */
    private fun extractDomainFromNode(node: AssistStructure.ViewNode): String? {
        // 检查 webDomain 属性
        node.webDomain?.let { 
            android.util.Log.d("MonicaAutofill", "✓ Found webDomain: $it")
            return it 
        }
        
        // 检查节点文本（可能是地址栏URL）
        node.text?.toString()?.let { text ->
            if (text.contains("://") || text.matches(Regex(".*\\.(com|org|net|edu|gov|cn|io|app).*"))) {
                val domain = extractDomainFromUrl(text)
                if (domain != null) {
                    android.util.Log.d("MonicaAutofill", "✓ Extracted from text: $domain")
                    return domain
                }
            }
        }
        
        // 检查 hint 文本
        node.hint?.let { hint ->
            if (hint.contains(".")) {
                val domain = extractDomainFromUrl(hint)
                if (domain != null) return domain
            }
        }
        
        // 递归子节点
        for (i in 0 until node.childCount) {
            node.getChildAt(i)?.let { child ->
                val domain = extractDomainFromNode(child)
                if (domain != null) return domain
            }
        }
        
        return null
    }
    
    /**
     * 🔧 从URL字符串提取域名
     */
    private fun extractDomainFromUrl(url: String): String? {
        return try {
            // 处理完整 URL
            if (url.contains("://")) {
                val urlPattern = Regex("https?://([^/:?#\\s]+)")
                val match = urlPattern.find(url)
                match?.groupValues?.get(1)
            } else {
                // 处理纯域名
                val domainPattern = Regex("([a-zA-Z0-9-]+\\.[a-zA-Z]{2,})")
                val match = domainPattern.find(url)
                match?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AutofillLogger.i("SERVICE", "MonicaAutofillService onDestroy() - Cleaning up...")
        
        serviceScope.cancel()
        appInfoCache.clear()
        
        // 停止SMS Retriever
        smsRetrieverHelper?.stopSmsRetriever()
        smsRetrieverHelper = null
        
        AutofillLogger.i("SERVICE", "Service destroyed")
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
        AutofillLogger.i("REQUEST", "onFillRequest called - Processing autofill request")
        android.util.Log.d("MonicaAutofill", "========================================")
        android.util.Log.d("MonicaAutofill", "=========  FILL REQUEST START  =========")
        android.util.Log.d("MonicaAutofill", "========================================")
        android.util.Log.d("MonicaAutofill", "Request flags: ${request.flags}")
        android.util.Log.d("MonicaAutofill", "Fill contexts count: ${request.fillContexts.size}")
        
        // 🔍 记录填充请求到诊断系统
        val context = request.fillContexts.lastOrNull()
        val packageName = context?.structure?.activityComponent?.packageName ?: "unknown"
        val hasInlineRequest = request.inlineSuggestionsRequest != null

        diagnostics.logFillRequest(
            packageName = packageName,
            flags = request.flags,
            contextCount = request.fillContexts.size,
            hasInlineRequest = hasInlineRequest
        )
        
        serviceScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                // 🎯 根据设备品牌设置动态超时时间
                val recommendedTimeout = DeviceUtils.getRecommendedAutofillTimeout()
                AutofillLogger.i("REQUEST", "Using device-specific timeout: ${recommendedTimeout}ms (Brand: ${DeviceUtils.getManufacturer()})")
                
                val result = withTimeoutOrNull(recommendedTimeout) {
                    processFillRequest(request, cancellationSignal)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                diagnostics.logRequestTime(processingTime)
                
                if (result != null) {
                    AutofillLogger.i("REQUEST", "Fill request completed successfully in ${processingTime}ms")
                    callback.onSuccess(result)
                } else {
                    // 🔄 国产ROM支持重试机制
                    if (DeviceUtils.getRecommendedRetryCount() > 1) {
                        AutofillLogger.w("REQUEST", "First attempt timed out, retrying...")
                        android.util.Log.w("MonicaAutofill", "Fill request timed out, retrying for Chinese ROM...")
                        
                        val retryResult = withTimeoutOrNull(recommendedTimeout) {
                            processFillRequest(request, cancellationSignal)
                        }
                        
                        val totalTime = System.currentTimeMillis() - startTime
                        diagnostics.logRequestTime(totalTime)
                        callback.onSuccess(retryResult)
                    } else {
                        AutofillLogger.w("REQUEST", "Fill request timed out after ${recommendedTimeout}ms")
                        android.util.Log.w("MonicaAutofill", "Fill request timed out")
                        callback.onSuccess(null)
                    }
                }
                
            } catch (e: Exception) {
                AutofillLogger.e("REQUEST", "Error in onFillRequest: ${e.message}", e)
                android.util.Log.e("MonicaAutofill", "Error in onFillRequest", e)
                diagnostics.logError("REQUEST", "Fill request failed: ${e.message}", e)
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
        AutofillLogger.d("PARSING", "Starting fill request processing")
        
        // 检查是否启用自动填充
        val isEnabled = autofillPreferences.isAutofillEnabled.first()
        if (!isEnabled) {
            AutofillLogger.w("REQUEST", "Autofill disabled in preferences")
            android.util.Log.d("MonicaAutofill", "Autofill disabled")
            return null
        }
        
        // 🔒 检查应用是否在黑名单中
        val fillContext = request.fillContexts.lastOrNull()
        if (fillContext != null) {
            val packageName = fillContext.structure.activityComponent.packageName
            if (autofillPreferences.isInBlacklist(packageName)) {
                AutofillLogger.w("REQUEST", "Package in blacklist: $packageName")
                android.util.Log.d("MonicaAutofill", "⛔ Package blocked by blacklist: $packageName")
                return null
            }
        }
        
        // 检查取消信号
        if (cancellationSignal.isCanceled) {
            AutofillLogger.w("REQUEST", "Request cancelled by system")
            android.util.Log.d("MonicaAutofill", "Request cancelled")
            return null
        }
        
        // 🎯 检查设备是否支持内联建议（考虑ROM兼容性）
        val deviceSupportsInline = DeviceUtils.supportsInlineSuggestions()
        val inlineRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && deviceSupportsInline) {
            request.inlineSuggestionsRequest
        } else {
            if (!deviceSupportsInline) {
                AutofillLogger.i("REQUEST", "Inline suggestions disabled for ${DeviceUtils.getROMType()} (compatibility)")
                android.util.Log.d("MonicaAutofill", "Inline suggestions not supported on this ROM: ${DeviceUtils.getROMType()}")
            }
            null
        }
        
        if (inlineRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AutofillLogger.d("REQUEST", "Inline suggestions supported, max: ${inlineRequest.maxSuggestionCount}")
            android.util.Log.d("MonicaAutofill", "Inline suggestions supported, max suggestions: ${inlineRequest.maxSuggestionCount}")
        }
        
        // 解析填充上下文
        val context = request.fillContexts.lastOrNull()
        if (context == null) {
            AutofillLogger.w("PARSING", "No fill context available")
            android.util.Log.d("MonicaAutofill", "No fill context")
            return null
        }
        
        val structure = context.structure
        
        // ✨ 使用改进的字段解析器（多层策略）
        // 可选：使用 ImprovedFieldParser 进行多层解析
        // val improvedParser = ImprovedFieldParser(structure)
        // val improvedResult = improvedParser.parse()
        // if (improvedParser.validateParseResult(improvedResult)) {
        //     // 使用改进的解析结果
        // }
        
        // ✨ 使用增强的字段解析器 V2
        val respectAutofillOff = autofillPreferences.isRespectAutofillDisabledEnabled.first()
        var parsedStructure = enhancedParserV2.parse(structure, respectAutofillOff)
        
        // 🔧 修复：检查并纠正字段顺序（如果密码框在用户名框之前）
        if (parsedStructure.items.size >= 2) {
            val usernameItem = parsedStructure.items.find { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS
            }
            val passwordItem = parsedStructure.items.find { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD 
            }
            
            if (usernameItem != null && passwordItem != null) {
                // 如果密码框的遍历索引小于用户名框，说明密码框在视觉/结构上位于前方
                // 这通常是识别错误（例如将账号框误认为密码框）
                if (passwordItem.traversalIndex < usernameItem.traversalIndex) {
                    AutofillLogger.w("PARSING", "⚠️ Detected Password field BEFORE Username field (Index: ${passwordItem.traversalIndex} < ${usernameItem.traversalIndex}). Swapping hints.")
                    android.util.Log.w("MonicaAutofill", "🔄 Swapping hints due to incorrect order")
                    
                    // 创建修正后的项列表
                    val correctedItems = parsedStructure.items.map { item ->
                        when (item.id) {
                            usernameItem.id -> item.copy(hint = EnhancedAutofillStructureParserV2.FieldHint.PASSWORD)
                            passwordItem.id -> item.copy(hint = EnhancedAutofillStructureParserV2.FieldHint.USERNAME) // 降级为 USERNAME 比较安全
                            else -> item
                        }
                    }
                    
                    // 更新结构
                    parsedStructure = parsedStructure.copy(items = correctedItems)
                }
            }
        }
        
        // 📊 记录增强解析结果
        AutofillLogger.d("PARSING", "Application: ${parsedStructure.applicationId}, WebView: ${parsedStructure.webView}")
        if (parsedStructure.webView) {
            AutofillLogger.d("PARSING", "WebDomain: ${parsedStructure.webDomain}, WebScheme: ${parsedStructure.webScheme}")
        }
        AutofillLogger.d("PARSING", "Total fields found: ${parsedStructure.items.size}")
        
        android.util.Log.d("MonicaAutofill", "=== Enhanced Parser V2 Results (Placeholder) ===")
        android.util.Log.d("MonicaAutofill", "Application: ${parsedStructure.applicationId}")
        android.util.Log.d("MonicaAutofill", "WebView: ${parsedStructure.webView}")
        if (parsedStructure.webView) {
            android.util.Log.d("MonicaAutofill", "  WebDomain: ${parsedStructure.webDomain}")
            android.util.Log.d("MonicaAutofill", "  WebScheme: ${parsedStructure.webScheme}")
        }
        android.util.Log.d("MonicaAutofill", "Total fields found: ${parsedStructure.items.size}")
        
        parsedStructure.items.forEach { item ->
            AutofillLogger.d("PARSING", "Field: ${item.hint} (accuracy: ${item.accuracy}, focused: ${item.isFocused})")
            android.util.Log.d("MonicaAutofill", "  ✓ ${item.hint} (accuracy: ${item.accuracy}, focused: ${item.isFocused})")
        }
        
        // 保留传统解析器作为后备
        val enhancedParser = EnhancedAutofillFieldParser(structure)
        val enhancedCollection = enhancedParser.parse()
        
        val parser = AutofillFieldParser(structure)
        val fieldCollection = parser.parse()
        
        // 🔍 记录字段解析结果到诊断系统
        val usernameFieldCount = parsedStructure.items.count { 
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS 
        }
        val passwordFieldCount = parsedStructure.items.count { 
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD 
        }
        val otherFieldCount = parsedStructure.items.size - usernameFieldCount - passwordFieldCount
        val avgAccuracy = if (parsedStructure.items.isNotEmpty()) {
            parsedStructure.items.map { it.accuracy.score }.average().toFloat()
        } else 0f
        
        diagnostics.logFieldParsing(
            totalFields = parsedStructure.items.size,
            usernameFields = usernameFieldCount,
            passwordFields = passwordFieldCount,
            otherFields = otherFieldCount,
            parserUsed = "EnhancedAutofillStructureParserV2",
            accuracy = avgAccuracy
        )
        
        // 检查是否有可填充的凭据字段
        val hasUsernameOrEmail = parsedStructure.items.any { 
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS 
        }
        val hasPassword = parsedStructure.items.any { 
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        
        if (!hasUsernameOrEmail && !hasPassword) {
            AutofillLogger.w("PARSING", "No credential fields found")
            android.util.Log.d("MonicaAutofill", "No credential fields found in enhanced parser")
            // 后备检查
            if (!fieldCollection.hasCredentialFields() && !enhancedCollection.hasCredentialFields()) {
                android.util.Log.d("MonicaAutofill", "No credential fields found in any parser")
                return null
            }
        }
        
        // 获取标识符 - 修复Chrome浏览器域名提取
        val packageName = parsedStructure.applicationId ?: structure.activityComponent.packageName
        
        // 🔧 Chrome特殊处理：从节点中提取域名
        var webDomain = parsedStructure.webDomain ?: parser.extractWebDomain()
        if (webDomain == null && (packageName == "com.android.chrome" || packageName.contains("browser"))) {
            // 尝试从结构中所有节点提取域名
            webDomain = extractDomainFromStructure(structure)
        }
        
        val identifier = webDomain ?: packageName
        
        AutofillLogger.d("MATCHING", "Package: $packageName, WebDomain: $webDomain, Identifier: $identifier")
        android.util.Log.d("MonicaAutofill", "Identifier: $identifier (package: $packageName, web: $webDomain)")
        
        // 🔍 可选：使用增强的密码匹配器
        // val matchStrategy = autofillPreferences.domainMatchStrategy.first()
        // val enhancedMatcher = EnhancedPasswordMatcher(matchStrategy)
        // val allPasswords = passwordRepository.getAllPasswordEntries().first()
        // val matchResult = enhancedMatcher.findMatches(packageName, structure, allPasswords)
        // if (matchResult.hasMatches()) {
        //     val matchedPasswords = matchResult.matches.map { it.entry }
        //     // 记录匹配详情
        //     val matchDetails = matchResult.matches.map { match ->
        //         AutofillDiagnostics.MatchDetail(
        //             passwordId = match.entry.id,
        //             passwordTitle = match.entry.title,
        //             matchType = match.matchType.name,
        //             score = match.score,
        //             matchedOn = webDomain ?: packageName,
        //             reason = match.reason
        //         )
        //     }
        //     diagnostics.logPasswordMatching(
        //         packageName = packageName,
        //         domain = webDomain,
        //         matchStrategy = matchResult.matchStrategy,
        //         totalPasswords = allPasswords.size,
        //         matchedPasswords = matchedPasswords.size,
        //         matchDetails = matchDetails
        //     )
        // }
        
        // 🚀 使用新引擎进行匹配（如果启用）
        val useNewEngine = autofillPreferences.useEnhancedMatching.first() ?: true
        
        val matchedPasswords = if (useNewEngine) {
            AutofillLogger.i("MATCHING", "Using new autofill engine for matching")
            try {
                // 构建 AutofillContext
                val autofillContext = AutofillContext(
                    packageName = packageName,
                    domain = webDomain,
                    webUrl = parsedStructure.webDomain,
                    isWebView = parsedStructure.webView,
                    detectedFields = parsedStructure.items.map { it.hint.name }
                )
                
                // 调用新引擎
                val result = autofillEngine.processRequest(autofillContext)
                
                if (result.isSuccess) {
                    AutofillLogger.i("MATCHING", "New engine found ${result.matches.size} matches in ${result.processingTimeMs}ms")
                    result.matches.map { match: PasswordMatch -> match.entry }
                } else {
                    AutofillLogger.w("MATCHING", "New engine failed: ${result.error}, falling back to legacy")
                    findMatchingPasswords(packageName, identifier)
                }
            } catch (e: Exception) {
                AutofillLogger.e("MATCHING", "New engine error, falling back to legacy", e)
                findMatchingPasswords(packageName, identifier)
            }
        } else {
            AutofillLogger.d("MATCHING", "Using legacy matching algorithm")
            findMatchingPasswords(packageName, identifier)
        }
        
        AutofillLogger.i("MATCHING", "Found ${matchedPasswords.size} matched passwords")
        android.util.Log.d("MonicaAutofill", "Found ${matchedPasswords.size} matched passwords")
        
        // 🔍 记录密码匹配结果到诊断系统
        val allPasswordsCount = passwordRepository.getAllPasswordEntries().first().size
        val matchStrategy = autofillPreferences.domainMatchStrategy.first().toString()
        diagnostics.logPasswordMatching(
            packageName = packageName,
            domain = webDomain,
            matchStrategy = matchStrategy,
            totalPasswords = allPasswordsCount,
            matchedPasswords = matchedPasswords.size
        )
        
        // 🎨 统一构建填充响应 - 整合密码建议和自动填充
        // 始终显示填充选项,即使没有匹配的密码也会显示"生成强密码"
        // 🎨 统一构建填充响应 - 整合密码建议和自动填充
        // 始终显示填充选项,即使没有匹配的密码也会显示"生成强密码"
        
        // 🔔 处理验证器通知和自动复制
        processOtpActions(matchedPasswords)
        
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
     * 处理 OTP 相关动作 (通知, 自动复制)
     */
    private suspend fun processOtpActions(passwords: List<PasswordEntry>) {
        if (passwords.isEmpty()) return
        
        val showNotification = autofillPreferences.isOtpNotificationEnabled.first()
        val autoCopy = autofillPreferences.isAutoCopyOtpEnabled.first()
        
        if (!showNotification && !autoCopy) return
        
        // 查找有 TOTP 密钥的条目
        val otpEntry = passwords.firstOrNull { it.authenticatorKey.isNotEmpty() } ?: return
        
        try {
            // 生成验证码
            val code = takagi.ru.monica.util.TotpGenerator.generateTotp(otpEntry.authenticatorKey)
            
            if (showNotification) {
                showOtpNotification(code, otpEntry.title)
            }
            
            if (autoCopy) {
                // 尝试复制到剪贴板
                try {
                     val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val clip = android.content.ClipData.newPlainText("OTP Code", code)
                     clipboard.setPrimaryClip(clip)
                     AutofillLogger.d("OTP", "Auto-copied OTP code to clipboard")
                } catch (e: Exception) {
                     AutofillLogger.e("OTP", "Failed to auto-copy OTP: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            AutofillLogger.e("OTP", "Error processing OTP actions", e)
        }
    }

    private fun showOtpNotification(code: String, label: String) {
        val channelId = "autofill_otp"
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(R.string.autofill_otp_notification_channel),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows 2FA codes during autofill"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Copy Intent
        val copyIntent = Intent(this, AutofillNotificationReceiver::class.java).apply {
            action = AutofillNotificationReceiver.ACTION_COPY_OTP
            putExtra(AutofillNotificationReceiver.EXTRA_OTP_CODE, code)
            putExtra("notification_id", 1001)
        }
        val copyPendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, copyIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists or use generic
            .setContentTitle("Code: $code")
            .setContentText(label)
            .setAutoCancel(true)
            .addAction(
                android.app.Notification.Action.Builder(
                    null, 
                    getString(R.string.autofill_otp_copy_action, code), 
                    copyPendingIntent
                ).build()
            )
            .build()
            
        notificationManager.notify(1001, notification)
        
        // Auto cancel logic - Using coroutine
        serviceScope.launch {
            val duration = autofillPreferences.otpNotificationDuration.first()
            kotlinx.coroutines.delay(duration * 1000L)
            notificationManager.cancel(1001)
        }
    }
    
    /**
     * 查找匹配的密码条目 - 修复Chrome域名匹配
     */
    private suspend fun findMatchingPasswords(packageName: String, identifier: String): List<PasswordEntry> {
        val matchStrategy = autofillPreferences.domainMatchStrategy.first()
        val allPasswords = passwordRepository.getAllPasswordEntries().first()
        
        android.util.Log.d("MonicaAutofill", "🔍 Matching: packageName=$packageName, identifier=$identifier")
        android.util.Log.d("MonicaAutofill", "📦 Total passwords in database: ${allPasswords.size}")
        
        // 🔍 调试:输出所有密码的实际内容
        allPasswords.forEachIndexed { index, pwd ->
            android.util.Log.d("MonicaAutofill", "密码 #$index: title='${pwd.title}', username='${pwd.username}', password='${pwd.password}' (长度=${pwd.password.length})")
        }
        
        // 智能匹配算法：优先级排序
        val exactMatches = mutableListOf<PasswordEntry>()
        val domainMatches = mutableListOf<PasswordEntry>()
        val fuzzyMatches = mutableListOf<PasswordEntry>()
        
        allPasswords.forEach { password ->
            android.util.Log.d("MonicaAutofill", "  - Checking: ${password.title} (website=${password.website}, package=${password.appPackageName})")
            
            when {
                // 最高优先级：精确包名匹配
                password.appPackageName.isNotBlank() && password.appPackageName == packageName -> {
                    exactMatches.add(password)
                    android.util.Log.d("MonicaAutofill", "    ✓ EXACT package match")
                }
                // 中等优先级：域名匹配
                password.website.isNotBlank() && 
                DomainMatcher.matches(password.website, identifier, matchStrategy) -> {
                    domainMatches.add(password)
                    android.util.Log.d("MonicaAutofill", "    ✓ DOMAIN match (${password.website} ~ $identifier)")
                }
                // 低优先级：模糊匹配（标题包含应用名）
                password.title.contains(getAppName(packageName), ignoreCase = true) -> {
                    fuzzyMatches.add(password)
                    android.util.Log.d("MonicaAutofill", "    ✓ FUZZY match")
                }
            }
        }
        
        android.util.Log.d("MonicaAutofill", "📊 Match results: exact=${exactMatches.size}, domain=${domainMatches.size}, fuzzy=${fuzzyMatches.size}")
        
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
        
        // 为每个匹配的密码创建数据集 - 最多显示3个
        val maxDirectShow = 3
        passwords.take(maxDirectShow).forEachIndexed { index, password ->
            val datasetBuilder = Dataset.Builder()
            var hasFilledField = false
            
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
                    hasFilledField = true
                } else {
                    datasetBuilder.setValue(
                        usernameId,
                        AutofillValue.forText(usernameValue),
                        presentation as RemoteViews
                    )
                    hasFilledField = true
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
                            hasFilledField = true
                        } else {
                            datasetBuilder.setValue(
                                emailId,
                                AutofillValue.forText(emailValue),
                                presentation as RemoteViews
                            )
                            hasFilledField = true
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
                        hasFilledField = true
                    } else {
                        datasetBuilder.setValue(
                            phoneId,
                            AutofillValue.forText(password.phone),
                            presentation as RemoteViews
                        )
                        hasFilledField = true
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
                    hasFilledField = true
                } else {
                    datasetBuilder.setValue(
                        passwordId,
                        AutofillValue.forText(password.password),
                        presentation as RemoteViews
                    )
                    hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
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
                        hasFilledField = true
                    }
                }
            }
            
            // 只有在至少填充了一个字段时才构建dataset
            if (hasFilledField) {
                try {
                    responseBuilder.addDataset(datasetBuilder.build())
                } catch (e: IllegalStateException) {
                    android.util.Log.w("MonicaAutofill", "⚠️ Skipping dataset for '${password.title}' - no fields filled")
                }
            } else {
                android.util.Log.w("MonicaAutofill", "⚠️ Skipping dataset for '${password.title}' - no fields filled")
            }
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 🔐 判断是否应该提供密码建议
     * 
     * 触发条件:
     * 1. 检测到 NEW_PASSWORD 字段 (明确的新密码场景)
     * 2. 或者: 同时有用户名字段和密码字段,且密码字段为空 (注册场景)
     * 
     * @param parsedStructure 解析的表单结构
     * @return 是否应该提供密码建议
     */
    private fun shouldSuggestPassword(parsedStructure: ParsedStructure): Boolean {
        // 1. 检测是否有 NEW_PASSWORD 字段
        val hasNewPasswordField = parsedStructure.items.any { 
            it.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD 
        }
        
        if (hasNewPasswordField) {
            AutofillLogger.i("SUGGESTION", "✓ NEW_PASSWORD field detected - suggesting password")
            return true
        }
        
        // 2. 检测是否有用户名和密码字段
        val hasUsernameOrEmail = parsedStructure.items.any { 
            it.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME || 
            it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS 
        }
        
        val hasPasswordField = parsedStructure.items.any { 
            it.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD 
        }
        
        if (hasUsernameOrEmail && hasPasswordField) {
            AutofillLogger.i("SUGGESTION", "✓ Username + Password fields detected - suggesting password")
            return true
        }
        
        AutofillLogger.d("SUGGESTION", "✗ Conditions not met for password suggestion")
        return false
    }
    
    /**
     * 🔐 构建密码建议响应
     * 
     * 创建一个包含"生成强密码"选项的 FillResponse
     * 
     * @param parsedStructure 解析的表单结构
     * @param packageName 应用包名
     * @param inlineRequest 内联建议请求 (Android 11+)
     * @return FillResponse 包含密码建议的响应
     */
    private suspend fun buildPasswordSuggestionResponse(
        parsedStructure: ParsedStructure,
        packageName: String,
        inlineRequest: InlineSuggestionsRequest? = null
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        try {
            // 1. 生成强密码
            val generatedPassword = generateStrongPassword(parsedStructure)
            AutofillLogger.i("SUGGESTION", "Generated strong password: ${generatedPassword.length} chars")
            
            // 2. 提取用户名 (如果有)
            val usernameItems = parsedStructure.items.filter { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS
            }
            val usernameValue = usernameItems.firstOrNull()?.value ?: ""
            
            // 3. 获取密码字段 AutofillId
            val passwordItems = parsedStructure.items.filter { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD ||
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD
            }
            
            if (passwordItems.isEmpty()) {
                AutofillLogger.w("SUGGESTION", "No password field found - cannot suggest password")
                return responseBuilder.build()
            }
            
            val passwordAutofillIds = passwordItems.map { it.id }
            
            // 4. 创建启动 PasswordSuggestionActivity 的 Intent
            val suggestionIntent = android.content.Intent(applicationContext, PasswordSuggestionActivity::class.java).apply {
                putExtra(PasswordSuggestionActivity.EXTRA_USERNAME, usernameValue)
                putExtra(PasswordSuggestionActivity.EXTRA_GENERATED_PASSWORD, generatedPassword)
                putExtra(PasswordSuggestionActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(PasswordSuggestionActivity.EXTRA_WEB_DOMAIN, parsedStructure.webDomain ?: "")
                putParcelableArrayListExtra(
                    PasswordSuggestionActivity.EXTRA_PASSWORD_FIELD_IDS,
                    ArrayList(passwordAutofillIds)
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 5. 创建 PendingIntent
            val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext,
                requestCode,
                suggestionIntent,
                flags
            )
            
            // 6. 创建密码建议 Dataset
            val datasetBuilder = Dataset.Builder()
            var hasFilledField = false
            
            // 创建 RemoteViews 显示
            val presentation = createPasswordSuggestionView(packageName)
            
            // 为所有密码字段设置认证 Intent (空值,仅用于触发认证)
            for (autofillId in passwordAutofillIds) {
                datasetBuilder.setValue(autofillId, null as AutofillValue?, presentation)
                hasFilledField = true
            }
            
            // 设置认证 Intent
            datasetBuilder.setAuthentication(pendingIntent.intentSender)
            
            // 只有在至少填充了一个字段时才构建dataset
            if (hasFilledField) {
                try {
                    responseBuilder.addDataset(datasetBuilder.build())
                } catch (e: IllegalStateException) {
                    android.util.Log.w("MonicaAutofill", "⚠️ Skipping password suggestion dataset - no fields filled")
                }
            }
            
            // 7. 添加 SaveInfo (确保用户使用建议密码后能自动保存)
            val saveInfo = takagi.ru.monica.autofill.core.SaveInfoBuilder.build(parsedStructure)
            if (saveInfo != null) {
                responseBuilder.setSaveInfo(saveInfo)
                AutofillLogger.i("SUGGESTION", "✓ SaveInfo configured for password suggestion")
            }
            
            AutofillLogger.i("SUGGESTION", "✓ Password suggestion response created successfully")
            
        } catch (e: Exception) {
            AutofillLogger.e("SUGGESTION", "Error building password suggestion response", e)
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 生成强密码
     * 根据表单要求智能生成符合条件的强密码
     * 
     * @param parsedStructure 解析的表单结构
     * @return 生成的强密码
     */
    private fun generateStrongPassword(parsedStructure: ParsedStructure): String {
        // 默认参数: 16位,包含大小写字母、数字和符号
        val options = takagi.ru.monica.utils.PasswordGenerator.PasswordOptions(
            length = 16,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
            excludeSimilar = true
        )
        
        // TODO: 未来可以分析 parsedStructure 中的密码字段约束
        // 例如: maxLength, inputType, hint 等来调整生成参数
        
        val generator = takagi.ru.monica.utils.PasswordGenerator()
        return generator.generatePassword(options)
    }
    
    /**
     * 创建密码建议的 RemoteViews
     * 显示 "生成强密码" 提示
     */
    private fun createPasswordSuggestionView(packageName: String): RemoteViews {
        val presentation = RemoteViews(this.packageName, R.layout.autofill_suggestion_item)
        
        // 设置图标
        presentation.setImageViewResource(R.id.icon, R.drawable.ic_key_24dp)
        
        // 设置标题
        presentation.setTextViewText(R.id.title, "生成强密码")
        
        // 设置副标题
        presentation.setTextViewText(R.id.subtitle, "Monica 将为您创建一个安全的强密码")
        
        return presentation
    }
    
    /**
     * 🚀 构建填充响应(增强版)
     * 使用 EnhancedAutofillStructureParserV2 的解析结果
     * 
     * @param passwords 匹配的密码列表
     * @param parsedStructure 增强解析器 V2 的解析结果
     * @param fieldCollection 传统字段集合(后备)
     * @param enhancedCollection 增强字段集合(后备)
     * @param packageName 应用包名
     * @param inlineRequest 内联建议请求(Android 11+)
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
        // 🎯 新用户体验: 直接显示所有匹配的密码 + "手动选择"选项
        AutofillLogger.i("RESPONSE", "Creating direct list response with ${passwords.size} passwords")
        android.util.Log.d("MonicaAutofill", "🎨 Using new direct list UI for ${passwords.size} passwords")
        
        return try {
            val domain = parsedStructure.webDomain
            
            // 🔧 修复: 获取所有密码的 ID,以便"手动选择"时可以显示所有密码
            val allPasswords = passwordRepository.getAllPasswordEntries().first()
            val allPasswordIds = allPasswords.map { it.id }
            android.util.Log.d("MonicaAutofill", "📋 Total passwords available for manual selection: ${allPasswordIds.size}")
            
            val directListResponse = AutofillPickerLauncher.createDirectListResponse(
                context = applicationContext,
                matchedPasswords = passwords,
                allPasswordIds = allPasswordIds, // 传递所有密码ID而不是空列表
                packageName = packageName,
                domain = domain,
                parsedStructure = parsedStructure
            )
            
            android.util.Log.d("MonicaAutofill", "✓ Direct list response created successfully")
            
            // 🔧 添加设备适配的 SaveInfo 配置
            val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
            if (requestSaveData) {
                val saveInfo = takagi.ru.monica.autofill.core.SaveInfoBuilder.build(parsedStructure)
                if (saveInfo != null) {
                    android.util.Log.i("MonicaAutofill", "📌 SaveInfo configured using SaveInfoBuilder")
                } else {
                    android.util.Log.w("MonicaAutofill", "⚠️ SaveInfo not configured - no saveable fields found")
                }
            }
            
            directListResponse
        } catch (e: Exception) {
            AutofillLogger.e("RESPONSE", "Failed to create direct list response, falling back to standard", e)
            android.util.Log.e("MonicaAutofill", "✗ Direct list failed, using standard UI", e)
            // 如果失败,继续使用标准方式
            buildStandardResponse(passwords, parsedStructure, fieldCollection, enhancedCollection, packageName, inlineRequest)
        }
    }
    
    /**
     * 构建标准的填充响应(原有逻辑)
     */
    private suspend fun buildStandardResponse(
        passwords: List<PasswordEntry>,
        parsedStructure: ParsedStructure,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection,
        packageName: String,
        inlineRequest: InlineSuggestionsRequest? = null
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 🔍 跟踪响应构建统计
        var datasetsCreated = 0
        var datasetsFailed = 0
        val buildErrors = mutableListOf<String>()
        
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
        
        // ✨ 计算内联建议的可用数量
        // 参考 Keyguard: 固定保留最后 1 个位置给"打开 Monica"兜底入口
        val totalInlineSlots = maxInlineSuggestions
        val reservedForManualSelection = if (totalInlineSlots > 1) 1 else 0
        val passwordInlineSlots = totalInlineSlots - reservedForManualSelection
        
        android.util.Log.d("MonicaAutofill", "Inline slots: total=$totalInlineSlots, passwords=$passwordInlineSlots, manual=$reservedForManualSelection")
        
        // 为每个匹配的密码创建数据集 - 最多显示3个
        // 单独的密码建议已被禁用，强制使用"Monica 自动填充"统一入口
        // passwords.take(maxDirectShow).forEachIndexed { ... } removed
        
        // ✨ 添加"打开 Monica"手动选择入口（始终作为最后一个选项）
        // 参考 Keyguard: 固定保留兜底入口确保用户始终有选择
        try {
            val manualSelectionPresentation = RemoteViews(this.packageName, R.layout.autofill_manual_card).apply {
                setTextViewText(R.id.text_title, "Monica 自动填充")
                setTextViewText(R.id.text_username, "点击进入选择界面")
                setImageViewResource(R.id.icon_app, R.mipmap.ic_launcher)
            }
            
            // 创建跳转到选择器的 Dataset
            val args = AutofillPickerActivityV2.Args(
                applicationId = packageName,
                webDomain = parsedStructure.webDomain,
                autofillIds = ArrayList(parsedStructure.items.map { it.id }),
                isSaveMode = false
            )
            val pickerIntent = AutofillPickerActivityV2.getIntent(this, args)
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val manualPendingIntent = PendingIntent.getActivity(
                this, 
                System.currentTimeMillis().toInt() and 0x7FFFFFFF,
                pickerIntent, 
                flags
            )
            
            val manualDatasetBuilder = Dataset.Builder(manualSelectionPresentation)
            
            // 为所有字段设置空值以触发 Authentication
            parsedStructure.items.forEach { item ->
                manualDatasetBuilder.setValue(item.id, null, manualSelectionPresentation)
            }
            manualDatasetBuilder.setAuthentication(manualPendingIntent.intentSender)
            
            // 添加内联建议的手动选择入口（如果有剩余槽位）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R 
                && inlineSpecs != null 
                && inlineSpecs.isNotEmpty()
                && reservedForManualSelection > 0) {
                val manualInlineSpec = inlineSpecs.lastOrNull() ?: inlineSpecs.first()
                val manualInline = createManualSelectionInlinePresentation(
                    manualInlineSpec, 
                    packageName, 
                    parsedStructure.webDomain,
                    parsedStructure
                )
                if (manualInline != null) {
                    // Android 11+ 需要使用 setInlinePresentation
                    // 但由于我们已经设置了 Authentication，需要重新构建
                    android.util.Log.d("MonicaAutofill", "✅ Manual selection inline added")
                }
            }
            
            responseBuilder.addDataset(manualDatasetBuilder.build())
            datasetsCreated++
            android.util.Log.d("MonicaAutofill", "✅ Manual selection dataset added")
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "❌ Failed to add manual selection dataset", e)
        }
        
        // 添加保存信息（如果启用）
        val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
        if (requestSaveData) {
            // 使用 SaveInfoBuilder 构建设备适配的 SaveInfo
            val saveInfo = takagi.ru.monica.autofill.core.SaveInfoBuilder.build(parsedStructure)
            
            if (saveInfo != null) {
                responseBuilder.setSaveInfo(saveInfo)
                android.util.Log.d("MonicaAutofill", "💾 SaveInfo configured using SaveInfoBuilder with device-specific flags")
            } else {
                android.util.Log.w("MonicaAutofill", "⚠️ SaveInfo not configured - no saveable fields found")
            }
        }
        
        // 🔍 记录响应构建结果到诊断系统
        diagnostics.logResponseBuilding(
            datasetsCreated = datasetsCreated,
            datasetsFailed = datasetsFailed,
            hasInlinePresentation = inlineSpecs != null,
            errors = buildErrors
        )
        
        android.util.Log.d("MonicaAutofill", "========================================")
        android.util.Log.d("MonicaAutofill", "✅ FillResponse built successfully (created=$datasetsCreated, failed=$datasetsFailed)")
        android.util.Log.d("MonicaAutofill", "========================================")
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
        val presentation = RemoteViews(this.packageName, R.layout.autofill_dataset_card)
        
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
     * 
     * 参考 Keyguard 的 tryCreateInlinePresentation 实现：
     * - 支持规格回退（fallback to spec[0]）
     * - 完整的无障碍支持
     * - 应用图标显示
     * 
     * @param password 密码条目
     * @param callingPackage 调用方包名
     * @param inlineSpec 内联展示规格
     * @param index 当前索引（用于规格回退）
     * @param allSpecs 所有可用规格（用于规格回退）
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createInlinePresentation(
        password: PasswordEntry,
        callingPackage: String,
        inlineSpec: InlinePresentationSpec,
        index: Int = 0,
        allSpecs: List<InlinePresentationSpec>? = null
    ): InlinePresentation? {
        try {
            // 规格回退逻辑：如果当前规格不支持，尝试使用第一个规格
            val effectiveSpec = if (UiVersions.getVersions(inlineSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                inlineSpec
            } else {
                // 尝试回退到第一个规格
                val fallbackSpec = allSpecs?.firstOrNull { spec ->
                    UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)
                }
                
                if (fallbackSpec != null) {
                    android.util.Log.d("MonicaAutofill", "Inline spec fallback: using spec[0] instead of spec[$index]")
                    fallbackSpec
                } else {
                    android.util.Log.w("MonicaAutofill", "No compatible inline spec found")
                    return null
                }
            }
            
            // 创建应用图标 - 参考 Keyguard 的 createAppIcon
            val appIcon = createAppIcon(password.appPackageName.ifBlank { callingPackage })
            
            // 构建显示文本
            val displayTitle = password.title.ifBlank { password.username.ifBlank { "密码" } }
            val displayUsername = password.username.ifBlank { "（无用户名）" }
            
            // 创建唯一的 PendingIntent（使用密码ID作为requestCode）
            val requestCode = password.id.toInt()
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                Intent().apply {
                    // 设置为Monica的自动填充回调Action
                    action = "takagi.ru.monica.AUTOFILL_INLINE_CLICK"
                    putExtra("password_id", password.id)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // 使用 InlineSuggestionUi 构建内联UI - 参考 Keyguard 的完整设置
            val inlineUi = InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                setTitle(displayTitle)
                setSubtitle(displayUsername)
                setStartIcon(appIcon)
                // 无障碍支持 - 参考 Keyguard
                setContentDescription("自动填充 $displayTitle，用户名: $displayUsername")
            }.build()
            
            return InlinePresentation(inlineUi.slice, effectiveSpec, false)
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error creating inline presentation", e)
            return null
        }
    }
    
    /**
     * 创建应用图标 - 参考 Keyguard 的 createAppIcon
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createAppIcon(packageNameOrDefault: String): Icon {
        return try {
            if (packageNameOrDefault.isNotBlank()) {
                val drawable = packageManager.getApplicationIcon(packageNameOrDefault)
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    Icon.createWithBitmap(drawable.bitmap).apply {
                        // 保持原始颜色 - 参考 Keyguard 的 setTintBlendMode(BlendMode.DST)
                        setTintBlendMode(BlendMode.DST)
                    }
                } else {
                    Icon.createWithResource(this, R.mipmap.ic_launcher).apply {
                        setTintBlendMode(BlendMode.DST)
                    }
                }
            } else {
                Icon.createWithResource(this, R.mipmap.ic_launcher).apply {
                    setTintBlendMode(BlendMode.DST)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MonicaAutofill", "Failed to create app icon for $packageNameOrDefault", e)
            Icon.createWithResource(this, R.mipmap.ic_launcher).apply {
                setTintBlendMode(BlendMode.DST)
            }
        }
    }
    
    /**
     * 创建手动选择入口的内联建议
     * 用于显示"打开 Monica"按钮作为兜底选项
     * 
     * 参考 Keyguard 的 tryBuildManualSelectionInlinePresentation
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createManualSelectionInlinePresentation(
        inlineSpec: InlinePresentationSpec,
        packageName: String,
        domain: String?,
        parsedStructure: ParsedStructure
    ): InlinePresentation? {
        try {
            if (!UiVersions.getVersions(inlineSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                return null
            }
            
            // 创建跳转到选择器的 Intent
            val args = AutofillPickerActivityV2.Args(
                applicationId = packageName,
                webDomain = domain,
                autofillIds = ArrayList(parsedStructure.items.map { it.id }),
                isSaveMode = false
            )
            val pickerIntent = AutofillPickerActivityV2.getIntent(this, args)
            
            val requestCode = System.currentTimeMillis().toInt() and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                pickerIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // 创建 Monica 图标
            val monicaIcon = Icon.createWithResource(this, R.mipmap.ic_launcher).apply {
                setTintBlendMode(BlendMode.DST)
            }
            
            // 构建内联UI
            val inlineUi = InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                setTitle("Monica 自动填充")
                setSubtitle("点击进入选择界面")
                setStartIcon(monicaIcon)
                setContentDescription("Monica 自动填充")
            }.build()
            
            return InlinePresentation(inlineUi.slice, inlineSpec, false)
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error creating manual selection inline", e)
            return null
        }
    }
    
    /**
     * 处理保存请求
     * 当用户提交表单时调用,可以保存新的密码或更新现有密码
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // 🚨 重要: 添加醒目的日志来确认此方法被调用
        AutofillLogger.i("REQUEST", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        AutofillLogger.i("REQUEST", "💾💾💾 onSaveRequest TRIGGERED! 💾💾💾")
        AutofillLogger.i("REQUEST", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.w("MonicaAutofill", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.w("MonicaAutofill", "💾💾💾 onSaveRequest TRIGGERED! 💾💾💾")
        android.util.Log.w("MonicaAutofill", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MonicaAutofill", "SaveRequest contexts: ${request.fillContexts.size}")
        
        serviceScope.launch {
            try {
                val intent = processSaveRequest(request)
                
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                    val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    } else {
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    
                    try {
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            applicationContext,
                            requestCode,
                            intent,
                            flags
                        )
                        
                        // 🎯 醒目的日志标记 - 用于确认 IntentSender 创建成功
                        AutofillLogger.i("REQUEST", "╔═══════════════════════════════════════════╗")
                        AutofillLogger.i("REQUEST", "║  ✅ PendingIntent 已创建!               ║")
                        AutofillLogger.i("REQUEST", "║  📤 将通过 IntentSender 交由系统启动   ║")
                        AutofillLogger.i("REQUEST", "╚═══════════════════════════════════════════╝")
                        android.util.Log.w("MonicaAutofill", "╔═══════════════════════════════════════════╗")
                        android.util.Log.w("MonicaAutofill", "║  ✅ PendingIntent 已创建!               ║")
                        android.util.Log.w("MonicaAutofill", "║  📤 即将调用 callback.onSuccess()       ║")
                        android.util.Log.w("MonicaAutofill", "╚═══════════════════════════════════════════╝")
                        
                        callback.onSuccess(pendingIntent.intentSender)
                        
                        // 🎯 确认回调已执行
                        AutofillLogger.i("REQUEST", "✅✅✅ callback.onSuccess(intentSender) 已调用!")
                        android.util.Log.w("MonicaAutofill", "✅✅✅ callback.onSuccess(intentSender) 已执行!")
                    } catch (intentSenderError: Exception) {
                        AutofillLogger.e("REQUEST", "IntentSender 启动失败,尝试直接 startActivity", intentSenderError)
                        android.util.Log.e("MonicaAutofill", "IntentSender 启动失败,回退到 startActivity", intentSenderError)
                        try {
                            startActivity(intent)
                            callback.onSuccess()
                        } catch (fallbackError: Exception) {
                            AutofillLogger.e("REQUEST", "回退 startActivity 仍然失败", fallbackError)
                            android.util.Log.e("MonicaAutofill", "回退 startActivity 仍然失败", fallbackError)
                            callback.onFailure(fallbackError.message ?: "启动失败")
                        }
                    }
                } else {
                    AutofillLogger.w("REQUEST", "无法创建 Intent")
                    android.util.Log.w("MonicaAutofill", "无法创建 Intent")
                    callback.onSuccess() // 即使失败也返回成功，避免系统重试
                }
                
            } catch (e: Exception) {
                AutofillLogger.e("REQUEST", "Error in onSaveRequest: ${e.message}", e)
                android.util.Log.e("MonicaAutofill", "Error in onSaveRequest", e)
                callback.onFailure(e.message ?: "保存失败")
            }
        }
    }
    
    /**
     * 处理保存请求的核心逻辑
     * @return Intent 用于启动自定义保存 UI,如果无法处理则返回 null
     */
    private suspend fun processSaveRequest(request: SaveRequest): android.content.Intent? {
        val startTime = System.currentTimeMillis()
        AutofillLogger.i("SAVE", "开始处理密码保存请求")
        
        try {
            // 1. 获取填充上下文
            val context = request.fillContexts.lastOrNull()
            if (context == null) {
                AutofillLogger.e("SAVE", "无法获取填充上下文")
                return null
            }
            
            val structure = context.structure
            
            // 2. 使用增强解析器提取字段
            val parsedStructure = enhancedParserV2.parse(structure)
            AutofillLogger.i("SAVE", "解析到 ${parsedStructure.items.size} 个字段")
            
            // 3. 提取用户名和密码字段的值
            var username = ""
            var password = ""
            var newPassword: String? = null
            var confirmPassword: String? = null
            var isNewPasswordScenario = false
            
            // 创建一个 map 来存储 AutofillId 到 ViewNode 的映射
            val idToNodeMap = mutableMapOf<android.view.autofill.AutofillId, AssistStructure.ViewNode>()
            
            // 递归收集所有 ViewNode
            val allNodes = mutableListOf<AssistStructure.ViewNode>()
            fun collectNodes(node: AssistStructure.ViewNode) {
                allNodes.add(node)
                node.autofillId?.let { id ->
                    idToNodeMap[id] = node
                }
                for (i in 0 until node.childCount) {
                    collectNodes(node.getChildAt(i))
                }
            }
            
            // 收集所有节点
            for (i in 0 until structure.windowNodeCount) {
                val windowNode = structure.getWindowNodeAt(i)
                collectNodes(windowNode.rootViewNode)
            }
            
            // 遍历解析的字段并从对应的 ViewNode 提取值
            // 记录密码字段的ID，用于位置推断
            var passwordFieldId: android.view.autofill.AutofillId? = null
            
            parsedStructure.items.forEach { item ->
                val node = idToNodeMap[item.id]
                var value = (node?.autofillValue)
                    .safeTextOrNull(tag = "SAVE", fieldDescription = item.hint.name)
                    ?: ""
                
                // ⚠️ 关键修复：如果 autofillValue 为空，尝试直接使用 text 属性
                if (value.isBlank() && node?.text != null) {
                    val textValue = node.text.toString()
                    if (textValue.isNotBlank()) {
                        value = textValue
                        AutofillLogger.d("SAVE", "⚠️ 使用 text 属性作为后备值: ${item.hint.name} = ${value.take(3)}***")
                    }
                }
                
                when (item.hint) {
                    EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                    EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                        if (username.isBlank()) {
                            username = value
                            AutofillLogger.d("SAVE", "提取用户名字段: ${value.take(3)}***")
                        }
                    }
                    EnhancedAutofillStructureParserV2.FieldHint.PASSWORD -> {
                        if (password.isBlank()) {
                            password = value
                            passwordFieldId = item.id
                            AutofillLogger.d("SAVE", "提取密码字段: ${value.length}个字符")
                        }
                    }
                    EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                        isNewPasswordScenario = true
                        if (newPassword == null) {
                            newPassword = value
                            passwordFieldId = item.id // 新密码也视为密码字段
                            AutofillLogger.d("SAVE", "提取新密码字段: ${value.length}个字符")
                        } else if (confirmPassword == null) {
                            confirmPassword = value
                            AutofillLogger.d("SAVE", "提取确认密码字段: ${value.length}个字符")
                        }
                    }
                    else -> {}
                }
            }
            
            // 🧠 智能回退机制：如果解析器未找到用户名，尝试使用启发式算法
            if (username.isBlank()) {
                AutofillLogger.i("SAVE", "⚠️ 标准解析未找到用户名，启动启发式搜索...")
                
                // 策略 1: Email 探测 (搜索包含 @ 的文本字段)
                val emailNode = allNodes.find { node ->
                    val text = node.text?.toString() ?: ""
                    val isPassword = node.autofillId == passwordFieldId || 
                                    (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0)
                    
                    !isPassword && 
                    text.contains("@") && 
                    text.length > 3 &&
                    node.visibility == android.view.View.VISIBLE
                }
                
                if (emailNode != null) {
                    username = emailNode.text.toString()
                    AutofillLogger.i("SAVE", "🧠 [Email探测] 找到潜在用户名: ${username.take(3)}***")
                }
                
                // 策略 2: 位置推断 (寻找密码框前一个文本输入框)
                if (username.isBlank() && passwordFieldId != null) {
                    val passwordNodeIndex = allNodes.indexOfFirst { it.autofillId == passwordFieldId }
                    if (passwordNodeIndex > 0) {
                        // 向前搜索最近的可见输入框
                        for (i in passwordNodeIndex - 1 downTo 0) {
                            val node = allNodes[i]
                            val isInput = node.className?.contains("EditText") == true || 
                                          node.className?.contains("TextInput") == true
                            val isVisible = node.visibility == android.view.View.VISIBLE
                            val hasText = !node.text.isNullOrEmpty()
                            
                            // 排除标签（通常不可编辑或点击）
                            // 简单判断: 如果有文字且是EditText类
                            if (isInput && isVisible && hasText) {
                                username = node.text.toString()
                                AutofillLogger.i("SAVE", "🧠 [位置推断] 找到密码框前方的输入框: ${username.take(3)}***")
                                break
                            }
                        }
                    }
                }
            }
            
            // 4. 提取包名和域名
            val packageName = structure.activityComponent.packageName
            val webDomain = PasswordSaveHelper.extractWebDomain(structure)
            
            AutofillLogger.i("SAVE", "来源信息: packageName=$packageName, domain=$webDomain")
            
            // 5. 构建SaveData并验证
            val saveData = PasswordSaveHelper.SaveData(
                username = username,
                password = password,
                newPassword = newPassword,
                confirmPassword = confirmPassword,
                packageName = packageName,
                webDomain = webDomain,
                isNewPasswordScenario = isNewPasswordScenario
            )
            
            when (val validation = saveData.validate()) {
                is PasswordSaveHelper.ValidationResult.Success -> {
                    AutofillLogger.i("SAVE", "数据验证通过")
                }
                is PasswordSaveHelper.ValidationResult.Warning -> {
                    AutofillLogger.w("SAVE", "数据验证警告: ${validation.message}")
                }
                is PasswordSaveHelper.ValidationResult.Error -> {
                    AutofillLogger.e("SAVE", "数据验证失败: ${validation.message}")
                    return null
                }
            }
            
            // 6. 检查是否启用保存功能
            val saveEnabled = autofillPreferences.isRequestSaveDataEnabled.first()
            if (!saveEnabled) {
                AutofillLogger.i("SAVE", "密码保存功能已禁用")
                return null
            }
            
            // 7. 检查重复密码
            val allPasswords = passwordRepository.getAllPasswordEntries().first()
            val duplicateCheck = PasswordSaveHelper.checkDuplicate(saveData, allPasswords)
            
            when (duplicateCheck) {
                is PasswordSaveHelper.DuplicateCheckResult.ExactDuplicate -> {
                    AutofillLogger.i("SAVE", "发现完全相同的密码,跳过保存")
                    return null // 完全重复不需要显示 UI
                }
                else -> {
                    // 其他情况继续保存流程
                    AutofillLogger.i("SAVE", "重复检查结果: ${duplicateCheck::class.simpleName}")
                }
            }
            
            // 8. 🎯 创建 Intent 用于启动自定义 Material 3 Bottom Sheet
            // Keyguard 风格: 返回 Intent,让系统在用户点击后启动
            // 🔧 关键优化: 完全不设置 flags!
            // 让 PendingIntent 自动处理,系统会在原应用上下文中启动
            val finalPassword = saveData.getFinalPassword()
            val saveIntent = android.content.Intent(applicationContext, AutofillSaveTransparentActivity::class.java).apply {
                putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, username)
                putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, finalPassword)
                putExtra(AutofillSaveTransparentActivity.EXTRA_WEBSITE, webDomain ?: "")
                putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra("EXTRA_IS_UPDATE", duplicateCheck is PasswordSaveHelper.DuplicateCheckResult.SameUsernameDifferentPassword)
                // ⚠️ 不设置任何 flags - 让系统自动处理!
            }
            
            val duration = System.currentTimeMillis() - startTime
            AutofillLogger.i("SAVE", "Intent 已创建,将由系统在用户点击后启动,耗时: ${duration}ms")
            return saveIntent
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            AutofillLogger.e("SAVE", "处理保存请求失败,耗时: ${duration}ms", e)
            return null
        }
    }
    
    override fun onConnected() {
        super.onConnected()
        AutofillLogger.i("SERVICE", "Autofill service connected to system")
        android.util.Log.d("MonicaAutofill", "Service connected")
    }
    
    override fun onDisconnected() {
        super.onDisconnected()
        AutofillLogger.i("SERVICE", "Autofill service disconnected from system")
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
    private val tag = "AutofillFieldParser"
    
    fun parse(): AutofillFieldCollection {
        val collection = AutofillFieldCollection()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, collection)
        }
        
        // 如果没有找到字段，不再尝试更宽松的匹配，以避免误触发（如聊天框）
        // if (!collection.hasCredentialFields()) {
        //     parseWithFallback(collection)
        // }
        
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
                        collection.usernameValue = (node.autofillValue)
                            .safeTextOrNull(tag, "username hint field")
                    }
                }
                android.view.View.AUTOFILL_HINT_PASSWORD -> {
                    if (collection.passwordField == null) {
                        collection.passwordField = node.autofillId
                        collection.passwordValue = (node.autofillValue)
                            .safeTextOrNull(tag, "password hint field")
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
                            collection.usernameValue = (node.autofillValue)
                                .safeTextOrNull(tag, "username heuristic field")
                        }
                    }
                    // 密码字段识别
                    isPasswordField(idEntry, hint, text, node) -> {
                        if (collection.passwordField == null) {
                            collection.passwordField = node.autofillId
                            collection.passwordValue = (node.autofillValue)
                                .safeTextOrNull(tag, "password heuristic field")
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
        // 排除非凭据字段
        val combined = "$idEntry $hint $text".lowercase()
        if (EXCLUSION_KEYWORDS.any { combined.contains(it) }) {
            return false
        }

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
        // 排除非凭据字段
        val combined = "$idEntry $hint $text".lowercase()
        if (EXCLUSION_KEYWORDS.any { combined.contains(it) }) {
            return false
        }

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

    private val EXCLUSION_KEYWORDS = listOf(
        "search", "query", "find", "filter", "搜索", "查找", "筛选", "搜一搜",
        "chat", "message", "msg", "messenger", "聊天", "消息", "私信", "发送", 
        "訊息", "私訊", "聊天框", "写消息", "发消息", "说些什么", "输入消息", 
        "打字机", "键盘输入", "说点什么", "写点什么", "说一个", "来说点什么吧",
        "comment", "reply", "评论", "回复", "留言", "评价", "吐槽", "弹幕",
        "note", "memo", "备注", "说明", "简介", "是个签名", "签到",
        "title", "subject", "content", "body", "标题", "主题", "内容", "正文"
    )
    
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
                    collection.passwordValue = (node.autofillValue)
                        .safeTextOrNull(tag, "password fallback field")
                }
                !isPasswordInput && collection.usernameField == null -> {
                    collection.usernameField = node.autofillId
                    collection.usernameValue = (node.autofillValue)
                        .safeTextOrNull(tag, "username fallback field")
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
        
        // 🔧 检查节点的文本内容，可能包含URL
        node.text?.toString()?.let { text ->
            if (text.contains("://") || text.contains(".com") || text.contains(".org")) {
                val domain = extractDomainFromUrl(text)
                if (domain != null) return domain
            }
        }
        
        // 🔧 检查contentDescription
        node.contentDescription?.toString()?.let { desc ->
            if (desc.contains("://") || desc.contains(".com")) {
                val domain = extractDomainFromUrl(desc)
                if (domain != null) return domain
            }
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val domain = extractWebDomainFromNode(node.getChildAt(i))
            if (domain != null) {
                return domain
            }
        }
        
        return null
    }
    
    private fun extractDomainFromUrl(url: String): String? {
        return try {
            val urlPattern = Regex("https?://([^/\\s]+)")
            val match = urlPattern.find(url)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
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
