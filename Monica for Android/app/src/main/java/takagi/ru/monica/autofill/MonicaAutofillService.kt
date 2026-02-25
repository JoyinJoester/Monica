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
    private val manualRequestFlagMask = 0x1
    private val compatibilityRequestFlagMask = 0x2
    private val preferredTriggerPackages = setOf(
        "com.tencent.mobileqq",
        "com.tencent.tim"
    )
    private enum class TriggerMode {
        AUTO,
        COMPATIBILITY,
        MANUAL
    }

    private enum class FallbackSource {
        COMPATIBILITY,
        HISTORICAL,
        SESSION
    }

    private data class WebCredentialFallbackIds(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?
    )

    private data class WebCandidateMetadata(
        val isCredentialCandidate: Boolean,
        val isPassword: Boolean,
        val isUsernameLike: Boolean,
        val isExcludedNonCredential: Boolean
    )

    private data class TriggerResolution(
        val parsedStructure: ParsedStructure,
        val parserCredentialTargets: List<ParsedItem>,
        val targets: List<ParsedItem>,
        val isWebContext: Boolean,
        val repeatRequest: Boolean,
        val isPreferredTriggerPackage: Boolean,
        val hasPasswordSignalNow: Boolean,
        val triggerMode: TriggerMode,
        val compatibilityAdded: Int,
        val historicalAdded: Int,
        val sessionRecoveryAdded: Int,
        val canRecoverFromSessionNow: Boolean
    )

    private data class InteractionSessionState(
        val usernameIdKey: String? = null,
        val passwordIdKey: String? = null,
        val usernameTraversalIndex: Int? = null,
        val passwordTraversalIndex: Int? = null,
        val hasPasswordSignal: Boolean = false,
        val updatedAt: Long = 0L
    )

    private data class DirectEntryModeDecision(
        val mode: AutofillPickerLauncher.DirectEntryMode,
        val stage: Int,
        val reason: String
    )

    private val interactionSessionStates = mutableMapOf<String, InteractionSessionState>()
    private val interactionSessionTtlMs = 3 * 60 * 1000L
    private val interactionSessionMaxSize = 64
    private val lastFilledSuggestionWindowMs = 3 * 60 * 1000L
    private val directEntryCycleTtlMs = 10 * 60 * 1000L
    private val directEntryCycleMaxSize = 128
    private val directEntryModeResolver by lazy {
        DirectEntryModeResolver(
            cycleTtlMs = directEntryCycleTtlMs,
            maxSize = directEntryCycleMaxSize
        )
    }
    private var serviceCreatedAtMs: Long = 0L
    private var fillContextRequestIdMethod: java.lang.reflect.Method? = null
    private val nonCredentialFocusKeywords = listOf(
        "chat", "message", "msg", "messenger", "comment", "reply", "search", "query",
        "send", "input_message", "chat_input", "messagebox",
        "聊天", "消息", "评论", "回复", "发送", "搜索", "查找", "输入消息"
    )
    
    override fun onCreate() {
        super.onCreate()
        serviceCreatedAtMs = System.currentTimeMillis()
        
        try {
            AutofillLogger.initialize(applicationContext)
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
        interactionSessionStates.clear()
        directEntryModeResolver.clear()
        
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
        
        val fillJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                // Keyguard 风格：单次稳定处理，不做短超时+二次重试，避免首击丢响应。
                val processingTimeout = maxOf(DeviceUtils.getRecommendedAutofillTimeout() * 2, 9000L)
                AutofillLogger.i(
                    "REQUEST",
                    "Using stable processing timeout: ${processingTimeout}ms (Brand: ${DeviceUtils.getManufacturer()})"
                )

                val result = withTimeoutOrNull(processingTimeout) {
                    processFillRequest(request, cancellationSignal)
                }

                val processingTime = System.currentTimeMillis() - startTime
                diagnostics.logRequestTime(processingTime)

                if (result != null) {
                    AutofillLogger.i("REQUEST", "Fill request completed successfully in ${processingTime}ms")
                    callback.onSuccess(result)
                } else {
                    AutofillLogger.w("REQUEST", "Fill request returned null/timed out after ${processingTime}ms")
                    android.util.Log.w("MonicaAutofill", "Fill request returned null/timed out")
                    callback.onSuccess(null)
                }
                
            } catch (e: Exception) {
                AutofillLogger.e("REQUEST", "Error in onFillRequest: ${e.message}", e)
                android.util.Log.e("MonicaAutofill", "Error in onFillRequest", e)
                diagnostics.logError("REQUEST", "Fill request failed: ${e.message}", e)
                callback.onFailure(e.message ?: "Unknown error")
            }
        }
        cancellationSignal.setOnCancelListener { fillJob.cancel() }
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
        val parsedStructure = enhancedParserV2.parse(structure, respectAutofillOff)
        
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
            it.hint == FieldHint.USERNAME ||
                it.hint == FieldHint.EMAIL_ADDRESS ||
                it.hint == FieldHint.PHONE_NUMBER
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
        
        // 获取标识符 - 修复Chrome浏览器域名提取
        val packageName = parsedStructure.applicationId ?: structure.activityComponent.packageName
        
        // 🔧 Chrome特殊处理：从节点中提取域名
        var webDomain = parsedStructure.webDomain ?: parser.extractWebDomain()
        if (webDomain == null && (packageName == "com.android.chrome" || packageName.contains("browser"))) {
            // 尝试从结构中所有节点提取域名
            webDomain = extractDomainFromStructure(structure)
        }
        
        val isWebContextForSuppression =
            parsedStructure.webView ||
                !webDomain.isNullOrBlank() ||
                isLikelyBrowserPackage(packageName)
        val interactionIdentifiers = buildInteractionIdentifiers(
            packageName = packageName,
            domain = webDomain,
            isWebContext = isWebContextForSuppression
        )
        val identifier = interactionIdentifiers.first()
        
        AutofillLogger.d("MATCHING", "Package: $packageName, WebDomain: $webDomain, Identifier: $identifier")
        android.util.Log.d("MonicaAutofill", "Identifier: $identifier (package: $packageName, web: $webDomain)")

        val effectiveParsedStructure = parsedStructure
        val parserCredentialTargets = selectCredentialFillTargets(effectiveParsedStructure)
        val parserOnlyCredentialTargets = normalizeCredentialTargetsById(parserCredentialTargets)
        val triggerMode = resolveTriggerMode(request.flags)
        val lowConfidenceContext = isLowConfidenceCredentialContext(parserCredentialTargets)
        val shouldSuppressNonCredentialFocus = shouldSuppressFocusedNonCredential(
            structure = structure,
            triggerMode = triggerMode,
            isWebContext = isWebContextForSuppression
        )

        if (shouldSuppressNonCredentialFocus && triggerMode == TriggerMode.AUTO) {
            AutofillLogger.i(
                "PARSING",
                "Suppressing auto request because focused field looks like non-credential input"
            )
            android.util.Log.i(
                "MonicaAutofill",
                "Suppressing auto request: focused field looks non-credential"
            )
            return null
        }

        val shouldUseCompatibilityRecovery = shouldUseCompatibilityTargetRecovery(triggerMode)
        val shouldForcePairRecovery = shouldRecoverIncompleteCredentialPair(
            targets = parserOnlyCredentialTargets,
            parsedStructure = parsedStructure,
            fieldCollection = fieldCollection,
            enhancedCollection = enhancedCollection
        )
        val shouldAttemptRecovery = shouldUseCompatibilityRecovery || shouldForcePairRecovery
        var effectiveCredentialTargets = parserOnlyCredentialTargets
        var targetStrategy = "keyguard_parser_only"
        var compatibilityRecoveryApplied = false

        if (shouldAttemptRecovery &&
            (effectiveCredentialTargets.isEmpty() || lowConfidenceContext || shouldForcePairRecovery)
        ) {
            val triggerResolution = resolveCredentialTargetsForRequest(
                requestFlags = request.flags,
                fillContexts = request.fillContexts,
                respectAutofillOff = respectAutofillOff,
                parsedStructure = parsedStructure,
                fieldCollection = fieldCollection,
                enhancedCollection = enhancedCollection,
                structure = structure,
                packageName = packageName,
                webDomain = webDomain
            )
            if (triggerResolution.targets.isNotEmpty()) {
                effectiveCredentialTargets = triggerResolution.targets
                compatibilityRecoveryApplied =
                    triggerResolution.compatibilityAdded > 0 ||
                        triggerResolution.historicalAdded > 0 ||
                        triggerResolution.sessionRecoveryAdded > 0 ||
                        triggerResolution.targets.size != parserOnlyCredentialTargets.size
                if (compatibilityRecoveryApplied) {
                    targetStrategy = "compatibility_recovery"
                }
                AutofillLogger.i(
                    "PARSING",
                    "Compatibility recovery: parser=${parserOnlyCredentialTargets.size}, " +
                        "resolved=${triggerResolution.targets.size}, compatAdded=${triggerResolution.compatibilityAdded}, " +
                        "historyAdded=${triggerResolution.historicalAdded}, sessionAdded=${triggerResolution.sessionRecoveryAdded}, " +
                        "mode=$triggerMode, forcePairRecovery=$shouldForcePairRecovery"
                )
                android.util.Log.i(
                    "MonicaAutofill",
                    "Compatibility recovery resolved targets=${triggerResolution.targets.size} " +
                        "(compat=${triggerResolution.compatibilityAdded}, history=${triggerResolution.historicalAdded}, " +
                        "session=${triggerResolution.sessionRecoveryAdded}, forcePair=$shouldForcePairRecovery)"
                )
            }
        }

        if (effectiveCredentialTargets.isEmpty() && shouldAttemptRecovery) {
            val focusedFallback = buildFocusedTextFallbackTarget(structure)
            if (focusedFallback != null) {
                effectiveCredentialTargets = listOf(focusedFallback)
                compatibilityRecoveryApplied = true
                targetStrategy = "focused_text_fallback"
                AutofillLogger.i("PARSING", "Applied focused text fallback target for trigger continuity")
                android.util.Log.i("MonicaAutofill", "Applied focused text fallback target")
            }
        }

        if (effectiveCredentialTargets.isEmpty()) {
            AutofillLogger.w("PARSING", "No credential targets resolved for this request")
            android.util.Log.i("MonicaAutofill", "No credential targets resolved for this request")
            return null
        }

        if (lowConfidenceContext && triggerMode == TriggerMode.AUTO && !compatibilityRecoveryApplied) {
            AutofillLogger.i(
                "PARSING",
                "Suppressing low-confidence parser context"
            )
            android.util.Log.i(
                "MonicaAutofill",
                "Suppressing low-confidence parser context"
            )
            return null
        }
        if (lowConfidenceContext && compatibilityRecoveryApplied) {
            AutofillLogger.i(
                "PARSING",
                "Low-confidence parser context accepted due to compatibility recovery"
            )
        }

        val fieldSignatureKey = buildFieldSignatureKey(packageName, webDomain, effectiveCredentialTargets)
        val requestOrdinal = resolveFillRequestOrdinal(request)
        val requestContextCount = request.fillContexts.size
        AutofillLogger.d(
            "PARSING",
            "Credential targets: parser=${parserCredentialTargets.size}, parserOnly=${parserOnlyCredentialTargets.size}, " +
                "effective=${effectiveCredentialTargets.size}, mode=$triggerMode, lowConfidence=$lowConfidenceContext, " +
                "compatibilityRecovery=$compatibilityRecoveryApplied, strategy=$targetStrategy"
        )
        val targetDetails = effectiveCredentialTargets.joinToString(separator = ";") { item ->
            val idKey = autofillIdKey(item.id) ?: item.id.toString()
            "${item.hint}@${item.traversalIndex},focused=${item.isFocused},id=$idKey"
        }
        AutofillLogger.d("PARSING", "Credential target details: $targetDetails")
        android.util.Log.i(
            "MonicaAutofill",
            "Resolved targets parser=${parserCredentialTargets.size}, effective=${effectiveCredentialTargets.size}, " +
                "mode=$triggerMode, lowConfidence=$lowConfidenceContext, strategy=$targetStrategy"
        )
        
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
        
        var diagnosticsTotalPasswords = -1
        val matchedPasswords = if (useNewEngine) {
            AutofillLogger.i("MATCHING", "Using new autofill engine for matching")
            try {
                // 构建 AutofillContext
                val autofillContext = AutofillContext(
                    packageName = packageName,
                    domain = webDomain,
                    webUrl = effectiveParsedStructure.webDomain,
                    isWebView = effectiveParsedStructure.webView,
                    detectedFields = effectiveParsedStructure.items.map { it.hint.name }
                )
                
                // 调用新引擎
                val result = autofillEngine.processRequest(autofillContext)
                
                if (result.isSuccess) {
                    diagnosticsTotalPasswords = result.candidateCount
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
        val allPasswordsCount = if (diagnosticsTotalPasswords >= 0) {
            diagnosticsTotalPasswords
        } else {
            passwordRepository.getAllPasswordEntries().first().size
        }
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
        autofillPreferences.touchAutofillInteraction(identifier)
        
        return buildFillResponseEnhanced(
            passwords = matchedPasswords, 
            parsedStructure = effectiveParsedStructure,
            fieldCollection = fieldCollection,
            enhancedCollection = enhancedCollection,
            packageName = packageName, 
            inlineRequest = inlineRequest,
            credentialTargets = effectiveCredentialTargets,
            fieldSignatureKey = fieldSignatureKey,
            interactionIdentifiers = interactionIdentifiers,
            requestOrdinal = requestOrdinal,
            requestContextCount = requestContextCount
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
        val copyPendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val copyPendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, copyIntent, copyPendingIntentFlags
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
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS ||
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER
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
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS ||
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER
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
        inlineRequest: InlineSuggestionsRequest? = null,
        credentialTargets: List<ParsedItem>,
        fieldSignatureKey: String,
        interactionIdentifiers: List<String>,
        requestOrdinal: Int,
        requestContextCount: Int
    ): FillResponse {
        // 🎯 新用户体验: 直接显示所有匹配的密码 + "手动选择"选项
        AutofillLogger.i("RESPONSE", "Creating direct list response with ${passwords.size} passwords")
        android.util.Log.d("MonicaAutofill", "🎨 Using new direct list UI for ${passwords.size} passwords")
        
        return try {
            val domain = parsedStructure.webDomain
            val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
            val shouldAttachSaveInfo = requestSaveData && shouldAttachSaveInfoForDirectResponse(parsedStructure)
            val baseIdentifiers = interactionIdentifiers
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
            val primaryIdentifier = baseIdentifiers.firstOrNull()
                ?: buildInteractionIdentifiers(
                    packageName = packageName,
                    domain = domain,
                    isWebContext = parsedStructure.webView || !domain.isNullOrBlank() || isLikelyBrowserPackage(packageName)
                ).first()
            val lookupIdentifiers = (baseIdentifiers + primaryIdentifier).distinct()
            val sessionMarker = extractAutofillSessionMarker(credentialTargets)
            val sessionScopedIdentifiers = buildSuggestionStageIdentifiers(lookupIdentifiers, sessionMarker)
            val useSessionScopedState = sessionMarker != null && sessionScopedIdentifiers.isNotEmpty()
            val stateIdentifiers = if (useSessionScopedState) {
                sessionScopedIdentifiers
            } else {
                lookupIdentifiers
            }
            val stageIdentifier = primaryIdentifier
            val lastFilledLookupIdentifiers = (stateIdentifiers + lookupIdentifiers).distinct()
            val now = System.currentTimeMillis()
            val recentLastFilledCredential = lastFilledLookupIdentifiers
                .mapNotNull { identifier ->
                    autofillPreferences.getLastFilledCredential(identifier)?.let { identifier to it }
                }
                .filter { (_, credential) ->
                    now - credential.timestamp <= lastFilledSuggestionWindowMs
                }
                .maxByOrNull { (_, credential) -> credential.timestamp }
            val lastFilledIdentifier = recentLastFilledCredential?.first
            val lastFilledCredential = recentLastFilledCredential?.second
            val lastFilledPassword = lastFilledCredential?.let { credential ->
                passwords.firstOrNull { it.id == credential.passwordId }
                    ?: passwordRepository.getPasswordEntryById(credential.passwordId)
            }
            val pickerIdentifierAliases = lastFilledLookupIdentifiers
                .filterNot { it == stageIdentifier }
            val directCycleKey = buildDirectEntryCycleKey(
                primaryIdentifier = primaryIdentifier,
                fieldSignatureKey = fieldSignatureKey
            )
            val directEntryProgressToken = buildDirectEntryProgressToken(
                requestOrdinal = requestOrdinal,
                contextCount = requestContextCount,
                credentialTargets = credentialTargets
            )
            val modeDecision = resolveDirectEntryMode(
                cycleKey = directCycleKey,
                hasLastFilled = lastFilledPassword != null,
                requestOrdinal = requestOrdinal,
                contextCount = requestContextCount,
                progressToken = directEntryProgressToken,
                sessionMarker = sessionMarker,
                fieldSignatureKey = fieldSignatureKey
            )
            val entryMode = modeDecision.mode
            AutofillLogger.i(
                "RESPONSE",
                "Direct entry mode=$entryMode, hasLastFilled=${lastFilledPassword != null}, " +
                    "baseId=$primaryIdentifier, sessionMarker=${sessionMarker ?: "none"}, " +
                    "scope=${if (useSessionScopedState) "session" else "base"}, " +
                    "lastFilledId=${lastFilledIdentifier ?: "none"}, stage=${modeDecision.stage}, " +
                    "reason=${modeDecision.reason}, stageKey=$stageIdentifier, " +
                    "lookupAliases=${lastFilledLookupIdentifiers.size}, reqOrdinal=$requestOrdinal, reqContexts=$requestContextCount, " +
                    "serviceCreatedAt=$serviceCreatedAtMs"
            )
            android.util.Log.d(
                "MonicaAutofill",
                "Direct response entryMode=$entryMode, hasLastFilled=${lastFilledPassword != null}, " +
                    "id=$primaryIdentifier, aliases=${lookupIdentifiers.size}, " +
                    "stateAliases=${lastFilledLookupIdentifiers.size}, " +
                    "sessionMarker=${sessionMarker ?: "none"}, stage=${modeDecision.stage}, " +
                    "reason=${modeDecision.reason}, reqOrdinal=$requestOrdinal, reqContexts=$requestContextCount, " +
                    "serviceCreatedAt=$serviceCreatedAtMs"
            )
            
            val directListResponse = AutofillPickerLauncher.createDirectListResponse(
                context = applicationContext,
                matchedPasswords = passwords,
                packageName = packageName,
                domain = domain,
                parsedStructure = parsedStructure,
                credentialTargetsOverride = credentialTargets,
                fieldSignatureKey = fieldSignatureKey,
                attachSaveInfo = shouldAttachSaveInfo,
                entryMode = entryMode,
                lastFilledPassword = lastFilledPassword,
                interactionIdentifier = stageIdentifier,
                interactionIdentifierAliases = pickerIdentifierAliases,
            )
            
            android.util.Log.d("MonicaAutofill", "✓ Direct list response created successfully")

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
        val fillTargets = selectCredentialFillTargets(parsedStructure)
        val authTargets = selectAuthenticationTargets(fillTargets)
        
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
            val manualSelectionPresentation = RemoteViews(this.packageName, R.layout.autofill_manual_card_v2).apply {
                setTextViewText(R.id.text_title, getString(R.string.autofill_manual_entry_title))
                setViewVisibility(R.id.text_username, android.view.View.GONE)
                setImageViewResource(R.id.icon_app, R.drawable.ic_list)
            }
            
            // 创建跳转到选择器的 Dataset
            val args = AutofillPickerActivityV2.Args(
                applicationId = packageName,
                webDomain = parsedStructure.webDomain,
                autofillIds = ArrayList(fillTargets.map { it.id }),
                autofillHints = ArrayList(fillTargets.map { it.hint.name }),
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

            authTargets.forEach { item ->
                val manualDatasetBuilder = Dataset.Builder(manualSelectionPresentation)
                manualDatasetBuilder.setValue(item.id, null, manualSelectionPresentation)
                manualDatasetBuilder.setAuthentication(manualPendingIntent.intentSender)
                responseBuilder.addDataset(manualDatasetBuilder.build())
                datasetsCreated++
            }
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
        
        // 数据来源前缀
        val sourcePrefix = when {
            password.isBitwardenEntry() -> "☁️ "  // Bitwarden 云同步
            password.isKeePassEntry() -> "🔐 "     // KeePass 本地
            else -> ""                              // Monica 本地
        }
        
        // 设置标题（带来源标识）
        val displayTitle = sourcePrefix + if (password.title.isNotBlank()) {
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
            
            // 数据来源前缀
            val sourcePrefix = when {
                password.isBitwardenEntry() -> "☁️ "  // Bitwarden 云同步
                password.isKeePassEntry() -> "🔐 "     // KeePass 本地
                else -> ""                              // Monica 本地
            }
            
            // 构建显示文本（带来源标识）
            val displayTitle = sourcePrefix + password.title.ifBlank { password.username.ifBlank { "密码" } }
            val displayUsername = password.username.ifBlank { "（无用户名）" }
            
            // 创建唯一的 PendingIntent（使用密码ID作为requestCode）
            val requestCode = password.id.toInt()
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                Intent().apply {
                    // 设置为Monica的自动填充回调Action
                    action = "takagi.ru.monica.AUTOFILL_INLINE_CLICK"
                    putExtra("password_id", password.id)
                },
                pendingIntentFlags
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
            val fillTargets = selectCredentialFillTargets(parsedStructure)
            if (fillTargets.isEmpty()) {
                return null
            }
            val args = AutofillPickerActivityV2.Args(
                applicationId = packageName,
                webDomain = domain,
                autofillIds = ArrayList(fillTargets.map { it.id }),
                autofillHints = ArrayList(fillTargets.map { it.hint.name }),
                isSaveMode = false,
                fieldSignatureKey = buildFieldSignatureKey(packageName, domain, fillTargets)
            )
            val pickerIntent = AutofillPickerActivityV2.getIntent(this, args)
            
            val requestCode = System.currentTimeMillis().toInt() and 0x7FFFFFFF
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                pickerIntent,
                pendingIntentFlags
            )
            
            // 创建 Monica 图标
            val monicaIcon = Icon.createWithResource(this, R.mipmap.ic_launcher).apply {
                setTintBlendMode(BlendMode.DST)
            }
            
            // 构建内联UI
            val inlineUi = InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                setTitle(getString(R.string.tile_autofill_label))
                setSubtitle(getString(R.string.autofill_open_picker))
                setStartIcon(monicaIcon)
                setContentDescription(getString(R.string.tile_autofill_label))
            }.build()
            
            return InlinePresentation(inlineUi.slice, inlineSpec, false)
            
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error creating manual selection inline", e)
            return null
        }
    }

    private fun resolveCredentialTargetsForRequest(
        requestFlags: Int,
        fillContexts: List<FillContext>,
        respectAutofillOff: Boolean,
        parsedStructure: ParsedStructure,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection,
        structure: AssistStructure,
        packageName: String,
        webDomain: String?
    ): TriggerResolution {
        val triggerMode = resolveTriggerMode(requestFlags)
        // 浏览器中二次请求时 webDomain 可能抖动为空，导致用户名侧被误判为非 web。
        // 这里把“浏览器包名”也纳入 web 上下文判定，稳定二次触发路径。
        val isWebContext =
            parsedStructure.webView ||
                !webDomain.isNullOrBlank() ||
                isLikelyBrowserPackage(packageName)
        val currentAutofillIdMap = collectCurrentAutofillIdMap(structure)
        val currentAutofillIds = currentAutofillIdMap.values.toSet()
        val focusedAutofillId = findFocusedAutofillId(structure)
        val sessionKeys = buildInteractionSessionKeys(
            packageName = packageName,
            domain = webDomain,
            isWebView = isWebContext,
            activityClassName = structure.activityComponent.className
        )
        cleanupInteractionSessions()
        val cachedSession = sessionKeys
            .mapNotNull { key -> interactionSessionStates[key] }
            .maxByOrNull { it.updatedAt }

        fun remapToCurrentAutofillId(id: AutofillId?): AutofillId? {
            if (id == null) return null
            return currentAutofillIdMap[autofillIdKey(id)]
        }

        val parserCredentialTargets = selectCredentialFillTargets(parsedStructure)
            .mapNotNull { candidate ->
                val remappedId = remapToCurrentAutofillId(candidate.id) ?: return@mapNotNull null
                candidate.copy(id = remappedId)
            }
        val isPreferredTriggerPackage = preferredTriggerPackages.any { packageName.startsWith(it) }
        val hasPasswordSignalFromCurrent = parsedStructure.items.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        } || enhancedCollection.passwordField != null || fieldCollection.passwordField != null
        val focusedWebUsernameFallbackId = if (isWebContext || isPreferredTriggerPackage) {
            findFocusedUsernameLikeAutofillId(structure)
        } else {
            null
        }
        val webCredentialFallbackIds = if (isWebContext || isPreferredTriggerPackage) {
            findWebCredentialFallbackIds(structure)
        } else {
            WebCredentialFallbackIds(usernameId = null, passwordId = null)
        }
        fun resolveCachedUsernameIdForCurrent(): AutofillId? {
            val idFromKey = currentAutofillIdMap[cachedSession?.usernameIdKey]
            if (idFromKey != null) return idFromKey
            val indexFromSession = cachedSession?.usernameTraversalIndex
            if (indexFromSession != null) {
                val idFromTraversal = parsedStructure.items.firstOrNull { item ->
                    item.traversalIndex == indexFromSession &&
                        (
                            item.hint == FieldHint.USERNAME ||
                                item.hint == FieldHint.EMAIL_ADDRESS ||
                                item.hint == FieldHint.PHONE_NUMBER
                            )
                }?.id
                if (idFromTraversal != null && currentAutofillIds.contains(idFromTraversal)) {
                    return idFromTraversal
                }
            }
            val webFallback = webCredentialFallbackIds.usernameId
            if (webFallback != null && currentAutofillIds.contains(webFallback)) return webFallback
            return null
        }
        fun resolveCachedPasswordIdForCurrent(): AutofillId? {
            val idFromKey = currentAutofillIdMap[cachedSession?.passwordIdKey]
            if (idFromKey != null) return idFromKey
            val indexFromSession = cachedSession?.passwordTraversalIndex
            if (indexFromSession != null) {
                val idFromTraversal = parsedStructure.items.firstOrNull { item ->
                    item.traversalIndex == indexFromSession &&
                        (item.hint == FieldHint.PASSWORD || item.hint == FieldHint.NEW_PASSWORD)
                }?.id
                if (idFromTraversal != null && currentAutofillIds.contains(idFromTraversal)) {
                    return idFromTraversal
                }
            }
            val webFallback = webCredentialFallbackIds.passwordId
            if (webFallback != null && currentAutofillIds.contains(webFallback)) return webFallback
            return null
        }
        val compatibilityCredentialTargets = buildCompatibilityCredentialTargets(
            parsedStructure = parsedStructure,
            fieldCollection = fieldCollection,
            enhancedCollection = enhancedCollection,
            packageName = packageName,
            isWebContext = isWebContext,
            focusedWebUsernameFallbackId = focusedWebUsernameFallbackId,
            webHeuristicUsernameFallbackId = webCredentialFallbackIds.usernameId,
            webHeuristicPasswordFallbackId = webCredentialFallbackIds.passwordId
        ).mapNotNull { candidate ->
            val remappedId = remapToCurrentAutofillId(candidate.id) ?: return@mapNotNull null
            candidate.copy(id = remappedId)
        }
        val historicalCredentialTargets = collectHistoricalCredentialTargets(
            fillContexts = fillContexts.dropLast(1),
            respectAutofillOff = respectAutofillOff,
            currentPackageName = packageName,
            currentWebDomain = webDomain,
            currentActivityClassName = structure.activityComponent.className
        ).mapNotNull { candidate ->
            val remappedId = remapToCurrentAutofillId(candidate.id) ?: return@mapNotNull null
            candidate.copy(id = remappedId)
        }
        val hasPasswordSignalFromHistory = historicalCredentialTargets.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        var hasPasswordSignalNow =
            hasPasswordSignalFromCurrent ||
                hasPasswordSignalFromHistory ||
                (cachedSession?.hasPasswordSignal == true)

        val resolvedTargets = parserCredentialTargets.toMutableList()
        var compatibilityAddedCount = 0
        var historicalAddedCount = 0
        var sessionRecoveryAddedCount = 0

        fun hasUsernameTarget(items: List<ParsedItem>): Boolean {
            return items.any {
                it.hint == FieldHint.USERNAME ||
                    it.hint == FieldHint.EMAIL_ADDRESS ||
                    it.hint == FieldHint.PHONE_NUMBER
            }
        }

        fun hasPasswordTarget(items: List<ParsedItem>): Boolean {
            return items.any { it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD }
        }

        fun addFallbackTarget(
            id: AutofillId?,
            hint: FieldHint,
            source: FallbackSource = FallbackSource.COMPATIBILITY
        ) {
            if (id == null) return
            if (!currentAutofillIds.contains(id)) return
            val duplicated = resolvedTargets.any { target ->
                target.id == id && isSameCredentialFamily(target.hint, hint)
            }
            if (duplicated) return
            val conflictingCredentialType = resolvedTargets.any { target ->
                target.id == id && !isSameCredentialFamily(target.hint, hint)
            }
            if (conflictingCredentialType) return
            resolvedTargets.add(
                ParsedItem(
                    id = id,
                    hint = hint,
                    accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                    value = null,
                    isFocused = false,
                    isVisible = true,
                    traversalIndex = Int.MAX_VALUE - resolvedTargets.size
                )
            )
            when (source) {
                FallbackSource.COMPATIBILITY -> compatibilityAddedCount++
                FallbackSource.HISTORICAL -> historicalAddedCount++
                FallbackSource.SESSION -> sessionRecoveryAddedCount++
            }
        }
        val resolvedCachedUsernameId = if (cachedSession != null) {
            resolveCachedUsernameIdForCurrent()
        } else {
            null
        }
        val resolvedCachedPasswordId = if (cachedSession != null) {
            resolveCachedPasswordIdForCurrent()
        } else {
            null
        }

        compatibilityCredentialTargets.forEach { compatibilityTarget ->
            addFallbackTarget(
                id = compatibilityTarget.id,
                hint = compatibilityTarget.hint,
                source = FallbackSource.COMPATIBILITY
            )
        }

        historicalCredentialTargets.forEach { historicalTarget ->
            addFallbackTarget(
                id = historicalTarget.id,
                hint = historicalTarget.hint,
                source = FallbackSource.HISTORICAL
            )
        }

        if (isWebContext || isPreferredTriggerPackage) {
            if (!hasUsernameTarget(resolvedTargets)) {
                addFallbackTarget(
                    id = webCredentialFallbackIds.usernameId,
                    hint = FieldHint.USERNAME,
                    source = FallbackSource.COMPATIBILITY
                )
            }
            if (!hasPasswordTarget(resolvedTargets)) {
                addFallbackTarget(
                    id = webCredentialFallbackIds.passwordId,
                    hint = FieldHint.PASSWORD,
                    source = FallbackSource.COMPATIBILITY
                )
            }
        }

        if (!hasUsernameTarget(resolvedTargets)) {
            addFallbackTarget(
                id = resolvedCachedUsernameId,
                hint = FieldHint.USERNAME,
                source = FallbackSource.SESSION
            )
        }
        if (!hasPasswordTarget(resolvedTargets)) {
            addFallbackTarget(
                id = resolvedCachedPasswordId,
                hint = FieldHint.PASSWORD,
                source = FallbackSource.SESSION
            )
        }
        if (focusedAutofillId != null) {
            val focusedIdKey = autofillIdKey(focusedAutofillId)
            if (focusedIdKey != null) {
                if (!hasUsernameTarget(resolvedTargets) &&
                    (focusedIdKey == cachedSession?.usernameIdKey || focusedAutofillId == resolvedCachedUsernameId)
                ) {
                    addFallbackTarget(
                        id = focusedAutofillId,
                        hint = FieldHint.USERNAME,
                        source = FallbackSource.SESSION
                    )
                }
                if (!hasPasswordTarget(resolvedTargets) &&
                    (focusedIdKey == cachedSession?.passwordIdKey || focusedAutofillId == resolvedCachedPasswordId)
                ) {
                    addFallbackTarget(
                        id = focusedAutofillId,
                        hint = FieldHint.PASSWORD,
                        source = FallbackSource.SESSION
                    )
                }
            }
        }

        val normalizedTargets = normalizeCredentialTargetsById(
            resolvedTargets.filter { currentAutofillIds.contains(it.id) }
        )
        var effectiveTargets = if (normalizedTargets.isEmpty() && isWebContext) {
            val webFallbackTargets = buildWebFallbackCredentialTargets(structure)
                .filter { currentAutofillIds.contains(it.id) }
            if (webFallbackTargets.isNotEmpty()) {
                compatibilityAddedCount += webFallbackTargets.size
                webFallbackTargets
            } else {
                normalizedTargets
            }
        } else {
            normalizedTargets
        }
        if (cachedSession != null && (resolvedCachedUsernameId != null || resolvedCachedPasswordId != null)) {
            val repeatedTargets = effectiveTargets.toMutableList()
            if (!hasUsernameTarget(repeatedTargets) && resolvedCachedUsernameId != null) {
                repeatedTargets.add(
                    ParsedItem(
                        id = resolvedCachedUsernameId,
                        hint = FieldHint.USERNAME,
                        accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                        value = null,
                        isFocused = resolvedCachedUsernameId == focusedAutofillId,
                        isVisible = true,
                        traversalIndex = Int.MAX_VALUE - repeatedTargets.size
                    )
                )
                sessionRecoveryAddedCount++
            }
            if (!hasPasswordTarget(repeatedTargets) && resolvedCachedPasswordId != null) {
                repeatedTargets.add(
                    ParsedItem(
                        id = resolvedCachedPasswordId,
                        hint = FieldHint.PASSWORD,
                        accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                        value = null,
                        isFocused = resolvedCachedPasswordId == focusedAutofillId,
                        isVisible = true,
                        traversalIndex = Int.MAX_VALUE - repeatedTargets.size
                    )
                )
                sessionRecoveryAddedCount++
            }
            effectiveTargets = normalizeCredentialTargetsById(repeatedTargets)
        }
        if (cachedSession != null && effectiveTargets.isEmpty()) {
            val focusedNode = findFocusedViewNode(structure)
            val forcedId = when {
                focusedAutofillId != null && currentAutofillIds.contains(focusedAutofillId) -> focusedAutofillId
                resolvedCachedPasswordId != null -> resolvedCachedPasswordId
                else -> resolvedCachedUsernameId
            }
            if (forcedId != null) {
                val forcedHint = when {
                    forcedId == resolvedCachedPasswordId -> FieldHint.PASSWORD
                    forcedId == resolvedCachedUsernameId -> FieldHint.USERNAME
                    focusedNode != null && isPasswordInputType(focusedNode.inputType) -> FieldHint.PASSWORD
                    else -> FieldHint.USERNAME
                }
                effectiveTargets = listOf(
                    ParsedItem(
                        id = forcedId,
                        hint = forcedHint,
                        accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                        value = null,
                        isFocused = forcedId == focusedAutofillId,
                        isVisible = true,
                        traversalIndex = Int.MAX_VALUE - 1
                    )
                )
                sessionRecoveryAddedCount++
            }
        }

        if (effectiveTargets.any { it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD }) {
            hasPasswordSignalNow = true
        }
        sessionKeys.forEach { sessionKey ->
            updateInteractionSessionState(
                sessionKey = sessionKey,
                cached = interactionSessionStates[sessionKey] ?: cachedSession,
                resolvedTargets = effectiveTargets,
                hasPasswordSignalNow = hasPasswordSignalNow
            )
        }

        return TriggerResolution(
            parsedStructure = parsedStructure,
            parserCredentialTargets = parserCredentialTargets,
            targets = effectiveTargets,
            isWebContext = isWebContext,
            repeatRequest = cachedSession != null,
            isPreferredTriggerPackage = isPreferredTriggerPackage,
            hasPasswordSignalNow = hasPasswordSignalNow,
            triggerMode = triggerMode,
            compatibilityAdded = compatibilityAddedCount,
            historicalAdded = historicalAddedCount,
            sessionRecoveryAdded = sessionRecoveryAddedCount,
            canRecoverFromSessionNow = sessionRecoveryAddedCount > 0
        )
    }

    private fun selectCredentialFillTargets(parsedStructure: ParsedStructure): List<ParsedItem> {
        return parsedStructure.items.filter { item ->
            item.hint == FieldHint.USERNAME ||
                item.hint == FieldHint.EMAIL_ADDRESS ||
                item.hint == FieldHint.PHONE_NUMBER ||
                item.hint == FieldHint.PASSWORD ||
                item.hint == FieldHint.NEW_PASSWORD
        }
    }

    /**
     * Keyguard-style safeguard:
     * if all parser-derived credential signals are weak and we cannot form a
     * username+password pair, treat this context as likely noise (chat/comment/search).
     */
    private fun isLowConfidenceCredentialContext(parserCredentialTargets: List<ParsedItem>): Boolean {
        if (parserCredentialTargets.isEmpty()) return true

        val lowAccuracyThreshold = EnhancedAutofillStructureParserV2.Accuracy.LOW.score
        val onlyLowAccuracy = parserCredentialTargets.all { item ->
            item.accuracy.score <= lowAccuracyThreshold
        }
        if (!onlyLowAccuracy) return false

        val hasUsernameLike = parserCredentialTargets.any { item ->
            (
                item.hint == FieldHint.USERNAME ||
                    item.hint == FieldHint.EMAIL_ADDRESS ||
                    item.hint == FieldHint.PHONE_NUMBER
                ) &&
                item.accuracy.score > EnhancedAutofillStructureParserV2.Accuracy.LOWEST.score
        }
        val hasPasswordLike = parserCredentialTargets.any { item ->
            (item.hint == FieldHint.PASSWORD || item.hint == FieldHint.NEW_PASSWORD) &&
                item.accuracy.score > EnhancedAutofillStructureParserV2.Accuracy.LOWEST.score
        }
        return !(hasUsernameLike && hasPasswordLike)
    }

    private fun collectCurrentAutofillIdMap(structure: AssistStructure): Map<String, AutofillId> {
        val ids = linkedMapOf<String, AutofillId>()
        fun walk(node: AssistStructure.ViewNode) {
            node.autofillId?.let { id ->
                autofillIdKey(id)?.let { key ->
                    ids[key] = id
                }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode)
        }
        return ids
    }

    private fun normalizeCredentialTargetsById(targets: List<ParsedItem>): List<ParsedItem> {
        if (targets.isEmpty()) return emptyList()
        val prioritized = targets.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { credentialHintPriority(it.hint) }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        )
        val deduped = LinkedHashMap<AutofillId, ParsedItem>()
        prioritized.forEach { candidate ->
            val existing = deduped[candidate.id]
            if (existing == null) {
                deduped[candidate.id] = candidate
                return@forEach
            }
            if (credentialHintPriority(candidate.hint) > credentialHintPriority(existing.hint)) {
                deduped[candidate.id] = candidate
            }
        }
        return deduped.values.sortedBy { it.traversalIndex }
    }

    private fun credentialHintPriority(hint: FieldHint): Int {
        return when (hint) {
            FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> 3
            FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER -> 2
            else -> 1
        }
    }

    private fun isSameCredentialFamily(lhs: FieldHint, rhs: FieldHint): Boolean {
        val lhsUsernameLike =
            lhs == FieldHint.USERNAME || lhs == FieldHint.EMAIL_ADDRESS || lhs == FieldHint.PHONE_NUMBER
        val rhsUsernameLike =
            rhs == FieldHint.USERNAME || rhs == FieldHint.EMAIL_ADDRESS || rhs == FieldHint.PHONE_NUMBER
        if (lhsUsernameLike && rhsUsernameLike) return true

        val lhsPasswordLike = lhs == FieldHint.PASSWORD || lhs == FieldHint.NEW_PASSWORD
        val rhsPasswordLike = rhs == FieldHint.PASSWORD || rhs == FieldHint.NEW_PASSWORD
        if (lhsPasswordLike && rhsPasswordLike) return true

        return false
    }

    private fun buildCompatibilityCredentialTargets(
        parsedStructure: ParsedStructure,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection,
        packageName: String,
        isWebContext: Boolean,
        focusedWebUsernameFallbackId: AutofillId?,
        webHeuristicUsernameFallbackId: AutofillId?,
        webHeuristicPasswordFallbackId: AutofillId?
    ): List<ParsedItem> {
        val compatibilityTargets = mutableListOf<ParsedItem>()
        val isPreferredTriggerPackage = preferredTriggerPackages.any { packageName.startsWith(it) }
        val hasPasswordSignal = parsedStructure.items.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        } || enhancedCollection.passwordField != null || fieldCollection.passwordField != null
        val hasPreferredHeuristicPassword =
            isPreferredTriggerPackage && webHeuristicPasswordFallbackId != null

        fun addTarget(id: AutofillId?, hint: FieldHint) {
            if (id == null) return
            if (compatibilityTargets.any { it.id == id && it.hint == hint }) return
            compatibilityTargets.add(
                ParsedItem(
                    id = id,
                    hint = hint,
                    accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                    value = null,
                    isFocused = false,
                    isVisible = true,
                    traversalIndex = Int.MAX_VALUE - compatibilityTargets.size
                )
            )
        }

        val usernameFallbackId = enhancedCollection.usernameField
            ?: fieldCollection.usernameField
            ?: if (hasPasswordSignal) enhancedCollection.phoneField else null
        addTarget(usernameFallbackId, FieldHint.USERNAME)
        if (usernameFallbackId == null && (isWebContext || hasPreferredHeuristicPassword)) {
            addTarget(focusedWebUsernameFallbackId, FieldHint.USERNAME)
            addTarget(webHeuristicUsernameFallbackId, FieldHint.USERNAME)
        }
        if ((hasPasswordSignal || hasPreferredHeuristicPassword) && usernameFallbackId == null) {
            val focusedNumericCandidate = parsedStructure.items.firstOrNull { item ->
                item.isFocused && (item.hint == FieldHint.PHONE_NUMBER || item.hint == FieldHint.OTP_CODE)
            }?.id
            addTarget(focusedNumericCandidate, FieldHint.USERNAME)

            val firstPhoneCandidate = parsedStructure.items.firstOrNull { item ->
                item.hint == FieldHint.PHONE_NUMBER
            }?.id
            addTarget(firstPhoneCandidate, FieldHint.USERNAME)
        }
        if (enhancedCollection.emailField != null && enhancedCollection.emailField != enhancedCollection.usernameField) {
            addTarget(enhancedCollection.emailField, FieldHint.EMAIL_ADDRESS)
        }

        val focusedItem = parsedStructure.items.firstOrNull { it.isFocused }
        if (focusedItem != null) {
            val focusedHint = when (focusedItem.hint) {
                FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> FieldHint.PASSWORD
                FieldHint.USERNAME,
                FieldHint.EMAIL_ADDRESS,
                FieldHint.PHONE_NUMBER,
                FieldHint.OTP_CODE -> FieldHint.USERNAME
                else -> if (hasPasswordSignal || hasPreferredHeuristicPassword) {
                    FieldHint.USERNAME
                } else {
                    null
                }
            }
            if (focusedHint != null) {
                addTarget(focusedItem.id, focusedHint)
            }
        }

        val usernameLikeIds = mutableSetOf<AutofillId>().apply {
            addAll(
                parsedStructure.items.filter {
                    it.hint == FieldHint.USERNAME ||
                        it.hint == FieldHint.EMAIL_ADDRESS ||
                        it.hint == FieldHint.PHONE_NUMBER
                }.map { it.id }
            )
            addAll(
                compatibilityTargets.filter {
                    it.hint == FieldHint.USERNAME ||
                        it.hint == FieldHint.EMAIL_ADDRESS ||
                        it.hint == FieldHint.PHONE_NUMBER
                }.map { it.id }
            )
        }
        val passwordFallbackIdRaw = enhancedCollection.passwordField
            ?: fieldCollection.passwordField
            ?: if (isWebContext || hasPreferredHeuristicPassword) webHeuristicPasswordFallbackId else null
        val passwordFallbackId = passwordFallbackIdRaw?.takeUnless { usernameLikeIds.contains(it) }
            ?: if (isWebContext || hasPreferredHeuristicPassword) {
                webHeuristicPasswordFallbackId?.takeUnless { usernameLikeIds.contains(it) }
            } else {
                null
            }
        addTarget(passwordFallbackId, FieldHint.PASSWORD)

        return compatibilityTargets
    }

    private fun mergeCredentialItems(
        existingItems: List<ParsedItem>,
        compatibilityTargets: List<ParsedItem>
    ): List<ParsedItem> {
        if (compatibilityTargets.isEmpty()) return existingItems
        val merged = existingItems.toMutableList()
        compatibilityTargets.forEach { candidate ->
            val duplicated = merged.any { it.id == candidate.id && it.hint == candidate.hint }
            if (!duplicated) {
                merged.add(candidate)
            }
        }
        return merged
    }

    private fun findFocusedAutofillId(structure: AssistStructure): AutofillId? {
        fun walk(node: AssistStructure.ViewNode): AutofillId? {
            val id = node.autofillId
            if (id != null && node.isFocused && node.visibility == android.view.View.VISIBLE) {
                return id
            }
            for (i in 0 until node.childCount) {
                val child = walk(node.getChildAt(i))
                if (child != null) return child
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val found = walk(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun collectHistoricalCredentialTargets(
        fillContexts: List<FillContext>,
        respectAutofillOff: Boolean,
        currentPackageName: String,
        currentWebDomain: String?,
        currentActivityClassName: String?
    ): List<ParsedItem> {
        if (fillContexts.isEmpty()) return emptyList()

        val collected = mutableListOf<ParsedItem>()
        val seen = mutableSetOf<Pair<AutofillId, FieldHint>>()
        val recentContexts = fillContexts.asReversed().take(3)

        for (fillContext in recentContexts) {
            try {
                val parsed = enhancedParserV2.parse(fillContext.structure, respectAutofillOff)
                val parsedPackage = parsed.applicationId ?: fillContext.structure.activityComponent.packageName
                if (parsedPackage != currentPackageName) continue
                val parsedActivityClassName = fillContext.structure.activityComponent.className
                if (!isCompatibleHistoricalContext(
                        currentWebDomain = currentWebDomain,
                        currentActivityClassName = currentActivityClassName,
                        historicalWebDomain = parsed.webDomain,
                        historicalActivityClassName = parsedActivityClassName
                    )
                ) continue

                for (item in parsed.items) {
                    if (!isCredentialHint(item.hint)) continue
                    val key = item.id to item.hint
                    if (!seen.add(key)) continue

                    collected.add(
                        ParsedItem(
                            id = item.id,
                            hint = item.hint,
                            accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
                            value = null,
                            isFocused = false,
                            isVisible = true,
                            traversalIndex = Int.MAX_VALUE - collected.size
                        )
                    )
                }
            } catch (e: Exception) {
                AutofillLogger.w("PARSING", "Skip historical context parse failure: ${e.message}")
            }
        }

        return collected
    }

    private fun isCredentialHint(hint: FieldHint): Boolean {
        return hint == FieldHint.USERNAME ||
            hint == FieldHint.EMAIL_ADDRESS ||
            hint == FieldHint.PHONE_NUMBER ||
            hint == FieldHint.PASSWORD ||
            hint == FieldHint.NEW_PASSWORD
    }

    private fun isCompatibleHistoricalContext(
        currentWebDomain: String?,
        currentActivityClassName: String?,
        historicalWebDomain: String?,
        historicalActivityClassName: String?
    ): Boolean {
        val currentDomain = normalizeWebDomain(currentWebDomain)
        val historicalDomain = normalizeWebDomain(historicalWebDomain)
        if (currentDomain != null || historicalDomain != null) {
            if (currentDomain == null || historicalDomain == null) return false
            return currentDomain == historicalDomain ||
                currentDomain.endsWith(".$historicalDomain") ||
                historicalDomain.endsWith(".$currentDomain")
        }

        val currentActivity = currentActivityClassName?.trim()?.lowercase().orEmpty()
        val historicalActivity = historicalActivityClassName?.trim()?.lowercase().orEmpty()
        if (currentActivity.isBlank() || historicalActivity.isBlank()) return false
        return currentActivity == historicalActivity
    }

    private fun normalizeWebDomain(rawDomain: String?): String? {
        val normalized = rawDomain?.trim()?.lowercase()?.ifBlank { null } ?: return null
        val withoutScheme = normalized.substringAfter("://", normalized)
        val hostAndMaybePath = withoutScheme.substringBefore('/')
        val host = hostAndMaybePath.substringBefore(':').removePrefix("www.")
        return host.ifBlank { null }
    }

    private fun autofillIdKey(id: AutofillId?): String? {
        return id?.toString()?.ifBlank { null }
    }

    private fun buildInteractionSessionKey(
        packageName: String,
        domain: String?,
        isWebView: Boolean,
        activityClassName: String?
    ): String {
        val normalizedPackage = packageName.trim().lowercase()
        val normalizedDomain = normalizeWebDomain(domain).orEmpty()
        val normalizedActivity = activityClassName?.trim()?.lowercase().orEmpty()
        return if (isWebView && normalizedDomain.isNotBlank()) {
            "web:$normalizedDomain"
        } else {
            if (normalizedActivity.isNotBlank()) {
                "app:$normalizedPackage:$normalizedActivity"
            } else {
                "app:$normalizedPackage"
            }
        }
    }

    private fun buildInteractionSessionKeys(
        packageName: String,
        domain: String?,
        isWebView: Boolean,
        activityClassName: String?
    ): List<String> {
        val keys = linkedSetOf<String>()
        keys += buildInteractionSessionKey(
            packageName = packageName,
            domain = domain,
            isWebView = isWebView,
            activityClassName = activityClassName
        )
        // Always keep an app/activity scoped alias so web-domain extraction jitter
        // does not break session recovery between consecutive requests.
        keys += buildInteractionSessionKey(
            packageName = packageName,
            domain = null,
            isWebView = false,
            activityClassName = activityClassName
        )
        return keys.toList()
    }

    private fun shouldSuppressFocusedNonCredential(
        structure: AssistStructure,
        triggerMode: TriggerMode,
        isWebContext: Boolean
    ): Boolean {
        if (triggerMode != TriggerMode.AUTO) return false

        val focusedNode = findFocusedViewNode(structure) ?: return false
        if (isPasswordInputType(focusedNode.inputType)) return false

        val htmlTag = focusedNode.htmlInfo?.tag?.lowercase().orEmpty()
        val htmlAttributesText = focusedNode.htmlInfo?.attributes
            ?.joinToString(" ") { "${it.first.lowercase()}=${it.second.lowercase()}" }
            .orEmpty()
        val combined = listOfNotNull(
            focusedNode.idEntry,
            focusedNode.hint,
            focusedNode.contentDescription?.toString(),
            focusedNode.text?.toString(),
            focusedNode.className,
            htmlTag,
            htmlAttributesText
        ).joinToString(" ").lowercase()
        if (combined.isBlank()) return false

        // 只要有明确凭证信号，永不抑制，优先保证登录页可用性。
        val credentialSignals = listOf(
            "user", "username", "email", "mail", "account", "login", "password", "pass", "pwd",
            "账号", "账户", "用户名", "邮箱", "登录", "密码"
        )
        val hasCredentialHint = focusedNode.autofillHints
            ?.map { it.lowercase() }
            ?.any { hint ->
                hint.contains("username") ||
                    hint.contains("email") ||
                    hint.contains("account") ||
                    hint.contains("login") ||
                    hint.contains("password")
            } == true
        if (hasCredentialHint || credentialSignals.any { token -> combined.contains(token) }) {
            return false
        }

        val htmlType = focusedNode.htmlInfo?.attributes
            ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
            ?.second
            ?.lowercase()
            .orEmpty()
        if (htmlType == "search" || htmlType == "url") return true

        val variation = focusedNode.inputType and android.text.InputType.TYPE_MASK_VARIATION
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_URI ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_FILTER
        ) {
            return true
        }

        val keywordHit = nonCredentialFocusKeywords.any { keyword -> combined.contains(keyword) }
        if (keywordHit) return true

        // Web 场景仅在明确的搜索/地址栏关键词时抑制，避免误拦登录框。
        if (isWebContext) {
            val webNonCredentialHints = listOf("search", "query", "keyword", "url", "address", "omnibox", "搜索", "查找", "网址", "地址栏")
            return webNonCredentialHints.any { token -> combined.contains(token) }
        }
        return false
    }

    private fun hasUsernameLikeTarget(targets: List<ParsedItem>): Boolean {
        return targets.any {
            it.hint == FieldHint.USERNAME ||
                it.hint == FieldHint.EMAIL_ADDRESS ||
                it.hint == FieldHint.PHONE_NUMBER
        }
    }

    private fun hasPasswordLikeTarget(targets: List<ParsedItem>): Boolean {
        return targets.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
    }

    private fun shouldRecoverIncompleteCredentialPair(
        targets: List<ParsedItem>,
        parsedStructure: ParsedStructure,
        fieldCollection: AutofillFieldCollection,
        enhancedCollection: EnhancedAutofillFieldCollection
    ): Boolean {
        if (targets.isEmpty()) return false
        val hasUsernameTarget = hasUsernameLikeTarget(targets)
        val hasPasswordTarget = hasPasswordLikeTarget(targets)
        if (hasUsernameTarget && hasPasswordTarget) return false
        if (hasUsernameTarget != hasPasswordTarget) {
            return true
        }

        val parsedHasUsernameSignal = parsedStructure.items.any {
            it.hint == FieldHint.USERNAME ||
                it.hint == FieldHint.EMAIL_ADDRESS ||
                it.hint == FieldHint.PHONE_NUMBER
        }
        val parsedHasPasswordSignal = parsedStructure.items.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        val enhancedHasUsernameSignal =
            enhancedCollection.usernameField != null ||
                enhancedCollection.emailField != null ||
                enhancedCollection.phoneField != null ||
                fieldCollection.usernameField != null
        val enhancedHasPasswordSignal =
            enhancedCollection.passwordField != null ||
                fieldCollection.passwordField != null

        val hasUsernameSignal = parsedHasUsernameSignal || enhancedHasUsernameSignal
        val hasPasswordSignal = parsedHasPasswordSignal || enhancedHasPasswordSignal
        return hasUsernameSignal && hasPasswordSignal
    }

    private fun findFocusedViewNode(structure: AssistStructure): AssistStructure.ViewNode? {
        fun walk(node: AssistStructure.ViewNode): AssistStructure.ViewNode? {
            if (node.isFocused && node.visibility == android.view.View.VISIBLE) return node
            for (i in 0 until node.childCount) {
                val found = walk(node.getChildAt(i))
                if (found != null) return found
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val found = walk(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun updateInteractionSessionState(
        sessionKey: String,
        cached: InteractionSessionState?,
        resolvedTargets: List<ParsedItem>,
        hasPasswordSignalNow: Boolean
    ) {
        val bestUsernameTarget = resolvedTargets
            .filter {
                it.hint == FieldHint.USERNAME ||
                    it.hint == FieldHint.EMAIL_ADDRESS ||
                    it.hint == FieldHint.PHONE_NUMBER
            }
            .sortedWith(compareByDescending<ParsedItem> { it.isFocused }.thenBy { it.traversalIndex })
            .firstOrNull()
        val bestPasswordTarget = resolvedTargets
            .filter { it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD }
            .sortedWith(compareByDescending<ParsedItem> { it.isFocused }.thenBy { it.traversalIndex })
            .firstOrNull()
        val bestUsernameKey = autofillIdKey(bestUsernameTarget?.id)
        val bestPasswordKey = autofillIdKey(bestPasswordTarget?.id)
        val bestUsernameTraversalIndex = bestUsernameTarget?.traversalIndex
        val bestPasswordTraversalIndex = bestPasswordTarget?.traversalIndex

        // Some web pages collapse second-pass signals into a single field id.
        // If both "best" candidates point to the same id, prefer the cached side-specific
        // ids to avoid degrading a previously healthy username/password pair.
        val collapsedBestKeys = bestUsernameKey != null &&
            bestPasswordKey != null &&
            bestUsernameKey == bestPasswordKey
        val candidateUsernameKey = if (collapsedBestKeys && cached?.usernameIdKey != null) {
            null
        } else {
            bestUsernameKey
        }
        val candidatePasswordKey = if (collapsedBestKeys && cached?.passwordIdKey != null) {
            null
        } else {
            bestPasswordKey
        }
        val candidateUsernameTraversalIndex = if (collapsedBestKeys && cached?.usernameTraversalIndex != null) {
            null
        } else {
            bestUsernameTraversalIndex
        }
        val candidatePasswordTraversalIndex = if (collapsedBestKeys && cached?.passwordTraversalIndex != null) {
            null
        } else {
            bestPasswordTraversalIndex
        }

        val resolvedUsernameKey = candidateUsernameKey ?: cached?.usernameIdKey
        val resolvedPasswordKey = candidatePasswordKey ?: cached?.passwordIdKey
        val resolvedUsernameTraversalIndex = candidateUsernameTraversalIndex ?: cached?.usernameTraversalIndex
        val resolvedPasswordTraversalIndex = candidatePasswordTraversalIndex ?: cached?.passwordTraversalIndex
        val normalizedPasswordKey = if (resolvedPasswordKey == resolvedUsernameKey) {
            null
        } else {
            resolvedPasswordKey
        }
        val normalizedPasswordTraversalIndex = if (normalizedPasswordKey == null) {
            cached?.passwordTraversalIndex
        } else {
            resolvedPasswordTraversalIndex
        }
        if (resolvedUsernameKey == null && normalizedPasswordKey == null && !hasPasswordSignalNow) {
            return
        }

        interactionSessionStates[sessionKey] = InteractionSessionState(
            usernameIdKey = resolvedUsernameKey,
            passwordIdKey = normalizedPasswordKey,
            usernameTraversalIndex = resolvedUsernameTraversalIndex,
            passwordTraversalIndex = normalizedPasswordTraversalIndex,
            hasPasswordSignal = hasPasswordSignalNow || (cached?.hasPasswordSignal == true),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun cleanupInteractionSessions() {
        val now = System.currentTimeMillis()
        interactionSessionStates.entries.removeAll { (_, value) ->
            now - value.updatedAt > interactionSessionTtlMs
        }
        if (interactionSessionStates.size <= interactionSessionMaxSize) return

        val oldestKeys = interactionSessionStates.entries
            .sortedBy { it.value.updatedAt }
            .take(interactionSessionStates.size - interactionSessionMaxSize)
            .map { it.key }
        oldestKeys.forEach { interactionSessionStates.remove(it) }
    }

    private fun buildFieldSignatureKey(
        packageName: String,
        domain: String?,
        credentialTargets: List<ParsedItem>
    ): String {
        val normalizedPackage = packageName.trim().lowercase()
        val normalizedDomain = domain?.trim()?.lowercase().orEmpty()
        val fingerprint = credentialTargets
            .sortedWith(compareBy<ParsedItem>({ it.traversalIndex }, { it.hint.name }))
            .joinToString(separator = "|") { item ->
                "${item.hint.name}:${item.traversalIndex}"
            }
        return "pkg=$normalizedPackage;dom=$normalizedDomain;sig=$fingerprint"
    }

    private fun isManualFillRequest(requestFlags: Int): Boolean {
        // FLAG_MANUAL_REQUEST is not exposed on some SDK stubs; use the platform bitmask directly.
        return (requestFlags and manualRequestFlagMask) != 0
    }

    private fun isCompatibilityFillRequest(requestFlags: Int): Boolean {
        // FLAG_COMPATIBILITY_MODE_REQUEST is not exposed on some SDK stubs; use the platform bitmask directly.
        return (requestFlags and compatibilityRequestFlagMask) != 0
    }

    private fun resolveTriggerMode(requestFlags: Int): TriggerMode {
        return when {
            isManualFillRequest(requestFlags) -> TriggerMode.MANUAL
            isCompatibilityFillRequest(requestFlags) -> TriggerMode.COMPATIBILITY
            else -> TriggerMode.AUTO
        }
    }

    private fun shouldUseCompatibilityTargetRecovery(triggerMode: TriggerMode): Boolean {
        if (triggerMode != TriggerMode.AUTO) return true
        val isAndroid12Family =
            Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2
        if (!isAndroid12Family) return false
        if (!DeviceUtils.isChineseROM()) return false
        return when (DeviceUtils.getROMType()) {
            DeviceUtils.ROMType.MIUI,
            DeviceUtils.ROMType.COLOR_OS,
            DeviceUtils.ROMType.REALME_UI,
            DeviceUtils.ROMType.ORIGIN_OS,
            DeviceUtils.ROMType.FUNTOUCH_OS,
            DeviceUtils.ROMType.EMUI,
            DeviceUtils.ROMType.HARMONY_OS,
            DeviceUtils.ROMType.MAGIC_OS,
            DeviceUtils.ROMType.OXYGEN_OS -> true
            else -> false
        }
    }

    private fun buildInteractionIdentifiers(
        packageName: String,
        domain: String?,
        isWebContext: Boolean
    ): List<String> {
        val normalizedPackage = packageName.trim().lowercase()
        val normalizedDomain = normalizeWebDomain(domain).orEmpty()
        val identifiers = linkedSetOf<String>()
        if (isWebContext && normalizedDomain.isNotBlank()) {
            identifiers += "web:$normalizedDomain"
        }
        identifiers += "app:$normalizedPackage"
        return identifiers.toList()
    }

    private fun buildSuggestionStageIdentifiers(
        baseIdentifiers: List<String>,
        sessionMarker: String?
    ): List<String> {
        val normalizedBase = baseIdentifiers
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedBase.isEmpty()) return emptyList()
        val marker = sessionMarker?.trim()?.takeIf { it.isNotBlank() } ?: return normalizedBase
        return normalizedBase.map { identifier -> "$identifier#session:$marker" }
    }

    private fun buildDirectEntryCycleKey(
        primaryIdentifier: String,
        fieldSignatureKey: String
    ): String {
        return "${primaryIdentifier.trim().lowercase()}|${fieldSignatureKey.trim().lowercase()}"
    }

    private fun resolveFillRequestOrdinal(request: FillRequest): Int {
        val fallback = request.fillContexts.size
        val latestContext = request.fillContexts.lastOrNull() ?: return fallback
        val requestId = runCatching {
            val method = fillContextRequestIdMethod ?: latestContext.javaClass.methods
                .firstOrNull { it.name == "getRequestId" && it.parameterCount == 0 }
                ?.also { fillContextRequestIdMethod = it }
            (method?.invoke(latestContext) as? Number)?.toInt()
        }.getOrNull()
        return requestId ?: fallback
    }

    private fun resolveDirectEntryMode(
        cycleKey: String,
        hasLastFilled: Boolean,
        requestOrdinal: Int,
        contextCount: Int,
        progressToken: String,
        sessionMarker: String?,
        fieldSignatureKey: String
    ): DirectEntryModeDecision {
        val resolverDecision = directEntryModeResolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = hasLastFilled,
            requestOrdinal = requestOrdinal,
            contextCount = contextCount,
            progressToken = progressToken,
            sessionMarker = sessionMarker,
            fieldSignatureKey = fieldSignatureKey
        )
        val nextMode = when (resolverDecision.mode) {
            DirectEntryModeResolver.Mode.TRIGGER_ONLY ->
                AutofillPickerLauncher.DirectEntryMode.TRIGGER_ONLY
            DirectEntryModeResolver.Mode.TRIGGER_AND_LAST_FILLED ->
                AutofillPickerLauncher.DirectEntryMode.TRIGGER_AND_LAST_FILLED
            DirectEntryModeResolver.Mode.LAST_FILLED_ONLY ->
                AutofillPickerLauncher.DirectEntryMode.LAST_FILLED_ONLY
        }
        return DirectEntryModeDecision(
            mode = nextMode,
            stage = resolverDecision.stage,
            reason = resolverDecision.reason
        )
    }

    private fun buildDirectEntryProgressToken(
        requestOrdinal: Int,
        contextCount: Int,
        credentialTargets: List<ParsedItem>
    ): String {
        val orderedTargets = credentialTargets.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenBy { it.traversalIndex }
                .thenBy { it.hint.name }
        )
        val anchorTarget = orderedTargets.firstOrNull()
        val anchorKey = if (anchorTarget == null) {
            "none"
        } else {
            val idKey = autofillIdKey(anchorTarget.id) ?: anchorTarget.id.toString()
            "${anchorTarget.hint.name}:$idKey:${anchorTarget.traversalIndex}:${anchorTarget.isFocused}"
        }
        return "$requestOrdinal|$contextCount|$anchorKey"
    }

    private fun extractAutofillSessionMarker(
        credentialTargets: List<ParsedItem>
    ): String? {
        if (credentialTargets.isEmpty()) return null
        val markerFromToken = credentialTargets
            .asSequence()
            .mapNotNull { item ->
                val raw = item.id.toString()
                extractAutofillIdSessionToken(raw)
            }
            .firstOrNull()
        if (!markerFromToken.isNullOrBlank()) return markerFromToken

        // Fallback: use full id fingerprint. This should still differ when page-level
        // AutofillId objects are recreated after a refresh/navigation.
        val fingerprint = credentialTargets
            .map { it.id.toString().trim() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(separator = "|")
        return if (fingerprint.isBlank()) null else fingerprint
    }

    private fun extractAutofillIdSessionToken(rawId: String): String? {
        val normalized = rawId.trim()
        if (normalized.isBlank()) return null
        val atIndex = normalized.lastIndexOf('@')
        if (atIndex in 1 until normalized.lastIndex) {
            val token = normalized.substring(atIndex + 1).trim()
            if (token.isNotBlank()) return token
        }
        return null
    }

    private fun shouldAttachSaveInfoForDirectResponse(parsedStructure: ParsedStructure): Boolean {
        // 对 direct-list 登录入口保持最保守策略：只在“新密码”场景附加 SaveInfo。
        // 这能显著降低部分 OEM 上因 SaveInfo 导致的会话粘住问题（清空后不回调第二次 onFillRequest）。
        return parsedStructure.items.any { it.hint == FieldHint.NEW_PASSWORD }
    }

    private fun buildInteractionIdentifier(packageName: String, domain: String?, isWebView: Boolean): String {
        return buildInteractionIdentifiers(
            packageName = packageName,
            domain = domain,
            isWebContext = isWebView
        ).first()
    }

    private fun isLikelyBrowserPackage(packageName: String): Boolean {
        val normalized = packageName.trim().lowercase()
        val browserPrefixes = listOf(
            "com.android.chrome",
            "com.chrome.",
            "org.chromium.",
            "com.microsoft.emmx",
            "org.mozilla.",
            "com.opera.",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.UCMobile".lowercase()
        )
        return browserPrefixes.any { normalized.startsWith(it) } ||
            normalized.contains("browser")
    }

    private fun buildFocusedTextFallbackTarget(structure: AssistStructure): ParsedItem? {
        val node = findFocusedViewNode(structure) ?: return null
        if (!isCredentialLikeNode(node)) return null

        val focusedId = node.autofillId ?: return null
        val hint = if (isPasswordInputType(node.inputType)) {
            FieldHint.PASSWORD
        } else {
            FieldHint.USERNAME
        }
        return ParsedItem(
            id = focusedId,
            hint = hint,
            accuracy = EnhancedAutofillStructureParserV2.Accuracy.MEDIUM,
            value = null,
            isFocused = node.isFocused,
            isVisible = node.visibility == android.view.View.VISIBLE,
            traversalIndex = Int.MAX_VALUE - 1
        )
    }

    private fun findFirstEditableTextNode(structure: AssistStructure): AssistStructure.ViewNode? {
        fun isEditableTextLike(node: AssistStructure.ViewNode): Boolean {
            if (node.visibility != android.view.View.VISIBLE) return false
            if (node.autofillId == null) return false
            if (node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT) return true
            if (node.inputType != 0) return true

            val className = node.className?.lowercase().orEmpty()
            if (className.contains("edittext") || className.contains("textinput") || className.contains("textfield")) {
                return true
            }

            val htmlType = node.htmlInfo?.attributes
                ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
                ?.second
                ?.lowercase()
            return htmlType == "text" ||
                htmlType == "password" ||
                htmlType == "email" ||
                htmlType == "tel" ||
                htmlType == "search"
        }

        fun walk(node: AssistStructure.ViewNode): AssistStructure.ViewNode? {
            if (isEditableTextLike(node)) return node
            for (i in 0 until node.childCount) {
                val found = walk(node.getChildAt(i))
                if (found != null) return found
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val found = walk(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedUsernameLikeAutofillId(structure: AssistStructure): AutofillId? {
        fun walk(node: AssistStructure.ViewNode): AutofillId? {
            val nodeAutofillId = node.autofillId
            if (nodeAutofillId != null && node.isFocused && node.visibility == android.view.View.VISIBLE) {
                val isTextField = node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT
                val isPasswordInput =
                    (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                        (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                        (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0
                if (isTextField && !isPasswordInput) {
                    return nodeAutofillId
                }
            }
            for (i in 0 until node.childCount) {
                val child = walk(node.getChildAt(i))
                if (child != null) return child
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val found = walk(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun findWebCredentialFallbackIds(structure: AssistStructure): WebCredentialFallbackIds {
        data class Candidate(
            val id: AutofillId,
            val isPassword: Boolean,
            val isUsernameLike: Boolean,
            val isFocused: Boolean,
            val order: Int
        )

        val candidates = mutableListOf<Candidate>()
        var traversalOrder = 0

        fun analyzeWebCandidate(node: AssistStructure.ViewNode): WebCandidateMetadata {
            val className = (node.className ?: "").lowercase()
            val idEntry = (node.idEntry ?: "").lowercase()
            val hintText = (node.hint ?: "").lowercase()
            val textValue = node.text?.toString()?.lowercase().orEmpty()
            val contentDesc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val joined = listOf(idEntry, hintText, textValue, contentDesc).joinToString(" ")
            val htmlTag = node.htmlInfo?.tag?.lowercase().orEmpty()
            val hasHtmlTextTag = htmlTag == "input" || htmlTag == "textarea"
            val htmlAttributes = node.htmlInfo?.attributes.orEmpty()
            val htmlAttributeJoined = htmlAttributes.joinToString(" ") { attribute ->
                "${attribute.first.lowercase()}=${attribute.second.lowercase()}"
            }

            val hintTokens = node.autofillHints
                ?.map { it.lowercase() }
                .orEmpty()
            val hasTextInputType = node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT
            val hasInputTypeSignal = node.inputType != 0
            val hasHintSignal = hintTokens.isNotEmpty()
            val hasFieldKeyword = listOf("user", "email", "account", "login", "pass", "pwd", "password")
                .any { joined.contains(it) }
            val hasHtmlAttributeSignal = listOf(
                "autocomplete",
                "username",
                "email",
                "account",
                "login",
                "password",
                "current-password",
                "new-password",
                "name=",
                "id=",
                "placeholder="
            ).any { htmlAttributeJoined.contains(it) }
            val hasEditClassSignal =
                className.contains("edittext") || className.contains("textfield") || className.contains("autocompletetextview")

            val isCredentialCandidate =
                hasTextInputType ||
                    hasInputTypeSignal ||
                    hasHintSignal ||
                    hasEditClassSignal ||
                    hasFieldKeyword ||
                    hasHtmlTextTag ||
                    hasHtmlAttributeSignal

            val isPasswordFromInputType =
                (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                    (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                    (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0
            val isPasswordFromHint = hintTokens.any { hint ->
                hint.contains("password") || hint.contains("current-password") || hint.contains("new-password")
            }
            val isPasswordFromText =
                joined.contains("password") || joined.contains("pass") || joined.contains("pwd") ||
                    joined.contains("密码") || joined.contains("密碼")
            val isPassword = isPasswordFromInputType || isPasswordFromHint || isPasswordFromText
            val isUsernameFromHint = hintTokens.any { hint ->
                hint.contains("username") ||
                    hint.contains("email") ||
                    hint.contains("account") ||
                    hint.contains("login")
            }
            val isUsernameFromText = listOf(
                "username",
                "user",
                "email",
                "account",
                "login",
                "手机号",
                "邮箱",
                "账户",
                "账号"
            ).any { token -> joined.contains(token) || htmlAttributeJoined.contains(token) }
            val isUsernameLike = !isPassword && (isUsernameFromHint || isUsernameFromText)

            val isExcludedNonCredential = listOf(
                "search", "query", "find", "chat", "message", "comment", "reply",
                "搜索", "查找", "聊天", "消息", "评论", "回复"
            ).any { joined.contains(it) }

            return WebCandidateMetadata(
                isCredentialCandidate = isCredentialCandidate,
                isPassword = isPassword,
                isUsernameLike = isUsernameLike,
                isExcludedNonCredential = isExcludedNonCredential
            )
        }

        fun addCandidate(
            node: AssistStructure.ViewNode,
            id: AutofillId,
            metadata: WebCandidateMetadata,
            allowRelaxed: Boolean = false
        ) {
            if (metadata.isExcludedNonCredential) return
            if (!metadata.isCredentialCandidate && !allowRelaxed) return
            candidates.add(
                Candidate(
                    id = id,
                    isPassword = metadata.isPassword,
                    isUsernameLike = metadata.isUsernameLike,
                    isFocused = node.isFocused,
                    order = traversalOrder++
                )
            )
        }

        fun walk(node: AssistStructure.ViewNode) {
            val nodeAutofillId = node.autofillId
            if (nodeAutofillId != null &&
                node.visibility == android.view.View.VISIBLE
            ) {
                val metadata = analyzeWebCandidate(node)
                addCandidate(node, nodeAutofillId, metadata)
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode)
        }

        // Some WebView/OEM trees only expose minimal signals on second requests.
        // Run a relaxed pass before giving up, while still honoring non-credential exclusions.
        if (candidates.isEmpty()) {
            fun walkRelaxed(node: AssistStructure.ViewNode) {
                val nodeAutofillId = node.autofillId
                if (nodeAutofillId != null && node.visibility == android.view.View.VISIBLE) {
                    val className = (node.className ?: "").lowercase()
                    val htmlTag = node.htmlInfo?.tag?.lowercase().orEmpty()
                    val relaxedSignal =
                        node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT ||
                            node.inputType != 0 ||
                            className.contains("edittext") ||
                            className.contains("textfield") ||
                            className.contains("textinput") ||
                            htmlTag == "input" ||
                            htmlTag == "textarea"
                    if (relaxedSignal) {
                        val metadata = analyzeWebCandidate(node)
                        addCandidate(node, nodeAutofillId, metadata, allowRelaxed = true)
                    }
                }
                for (i in 0 until node.childCount) {
                    walkRelaxed(node.getChildAt(i))
                }
            }
            for (i in 0 until structure.windowNodeCount) {
                walkRelaxed(structure.getWindowNodeAt(i).rootViewNode)
            }
        }

        if (candidates.isEmpty()) {
            return WebCredentialFallbackIds(usernameId = null, passwordId = null)
        }

        val ranked = candidates
            .sortedWith(compareByDescending<Candidate> { it.isFocused }.thenBy { it.order })
            .distinctBy { it.id }

        var passwordId = ranked.firstOrNull { it.isPassword }?.id
        var usernameId = ranked.firstOrNull { it.isUsernameLike && it.id != passwordId }?.id

        // 如果网页没有明确的密码/用户名信号，退化为选择两个不同文本框，
        // 保证账号和密码框都能挂上认证入口，避免二次清空后丢触发。
        if (usernameId == null) {
            usernameId = ranked.firstOrNull { !it.isPassword && it.id != passwordId }?.id
                ?: ranked.firstOrNull { it.id != passwordId }?.id
                ?: ranked.firstOrNull()?.id
        }
        if (passwordId == null) {
            passwordId = ranked.firstOrNull { it.isPassword && it.id != usernameId }?.id
                ?: ranked.firstOrNull { it.id != usernameId }?.id
                ?: ranked.firstOrNull()?.id
        }
        if (usernameId == passwordId) {
            val alternative = ranked.firstOrNull { it.id != usernameId }?.id
            if (alternative != null) {
                if (ranked.firstOrNull { it.id == usernameId }?.isPassword == true) {
                    usernameId = alternative
                } else {
                    passwordId = alternative
                }
            }
        }

        return WebCredentialFallbackIds(
            usernameId = usernameId,
            passwordId = passwordId
        )
    }

    private fun buildWebFallbackCredentialTargets(structure: AssistStructure): List<ParsedItem> {
        val fallbackIds = findWebCredentialFallbackIds(structure)
        val fallbackTargets = mutableListOf<ParsedItem>()
        var index = 0

        fun addTarget(id: AutofillId?, hint: FieldHint, focused: Boolean = false) {
            if (id == null) return
            if (fallbackTargets.any { it.id == id && it.hint == hint }) return
            fallbackTargets.add(
                ParsedItem(
                    id = id,
                    hint = hint,
                    accuracy = if (focused) {
                        EnhancedAutofillStructureParserV2.Accuracy.MEDIUM
                    } else {
                        EnhancedAutofillStructureParserV2.Accuracy.LOWEST
                    },
                    value = null,
                    isFocused = focused,
                    isVisible = true,
                    traversalIndex = Int.MAX_VALUE - index++
                )
            )
        }

        addTarget(fallbackIds.usernameId, FieldHint.USERNAME)
        addTarget(fallbackIds.passwordId, FieldHint.PASSWORD)

        val focusedNode = findFocusedViewNode(structure)
        if (focusedNode != null && isCredentialLikeNode(focusedNode)) {
            val focusedHint = if (isPasswordInputType(focusedNode.inputType)) {
                FieldHint.PASSWORD
            } else {
                FieldHint.USERNAME
            }
            addTarget(focusedNode.autofillId, focusedHint, focused = true)
        }

        return normalizeCredentialTargetsById(fallbackTargets)
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private fun isCredentialLikeNode(node: AssistStructure.ViewNode): Boolean {
        if (node.visibility != android.view.View.VISIBLE) return false
        if (node.autofillId == null) return false
        if (isPasswordInputType(node.inputType)) return true

        val combined = listOfNotNull(
            node.idEntry,
            node.hint,
            node.contentDescription?.toString(),
            node.text?.toString(),
            node.className
        ).joinToString(" ").lowercase()

        val hasCredentialHint = node.autofillHints
            ?.map { it.lowercase() }
            ?.any { hint ->
                hint.contains("username") ||
                    hint.contains("email") ||
                    hint.contains("account") ||
                    hint.contains("login") ||
                    hint.contains("password")
            } == true

        val htmlType = node.htmlInfo?.attributes
            ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
            ?.second
            ?.lowercase()
            .orEmpty()
        val hasCredentialHtmlType =
            htmlType == "password" ||
                htmlType == "email" ||
                htmlType == "tel"

        val hasCredentialToken = listOf(
            "user", "username", "email", "mail", "account", "login", "password", "pass", "pwd",
            "账号", "账户", "用户名", "邮箱", "登录", "密码"
        ).any { token -> combined.contains(token) }

        if (htmlType == "search" || htmlType == "url") return false
        if (nonCredentialFocusKeywords.any { keyword -> combined.contains(keyword) }) {
            if (!hasCredentialHint && !hasCredentialHtmlType && !hasCredentialToken) {
                return false
            }
        }

        return hasCredentialHint || hasCredentialHtmlType || hasCredentialToken
    }

    private fun selectAuthenticationTargets(fillTargets: List<ParsedItem>): List<ParsedItem> {
        if (fillTargets.isEmpty()) return emptyList()
        val priority = compareByDescending<ParsedItem> { it.isFocused }
            .thenByDescending {
                when (it.hint) {
                    FieldHint.PASSWORD,
                    FieldHint.NEW_PASSWORD -> 3
                    FieldHint.USERNAME,
                    FieldHint.EMAIL_ADDRESS,
                    FieldHint.PHONE_NUMBER -> 2
                    else -> 1
                }
            }
            .thenBy { it.traversalIndex }

        return fillTargets
            .sortedWith(priority)
            .distinctBy { it.id }
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
                    EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS,
                    EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER -> {
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
            val securityManager = takagi.ru.monica.security.SecurityManager(applicationContext)
            val duplicateCheck = PasswordSaveHelper.checkDuplicate(
                saveData = saveData,
                existingPasswords = allPasswords,
                resolvePassword = { entry ->
                    runCatching { securityManager.decryptData(entry.password) }
                        .getOrElse { entry.password }
                }
            )
            
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

