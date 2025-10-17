package takagi.ru.monica.utils

import android.app.assist.AssistStructure
import android.util.Log

/**
 * 中国主流浏览器检测器
 * 识别小米浏览器、OPPO浏览器、vivo浏览器等国产浏览器
 * 以及微信、支付宝等内置浏览器
 */
object ChineseBrowserDetector {
    
    private const val TAG = "ChineseBrowserDetector"
    
    /**
     * 浏览器类型枚举
     */
    enum class BrowserType {
        // 国产手机自带浏览器
        XIAOMI_BROWSER,          // 小米浏览器
        OPPO_BROWSER,            // OPPO浏览器
        VIVO_BROWSER,            // vivo浏览器
        HUAWEI_BROWSER,          // 华为浏览器
        HONOR_BROWSER,           // 荣耀浏览器
        
        // 第三方国产浏览器
        UC_BROWSER,              // UC浏览器
        QQ_BROWSER,              // QQ浏览器
        BAIDU_BROWSER,           // 百度浏览器
        SOGOU_BROWSER,           // 搜狗浏览器
        BROWSER_360,             // 360浏览器
        LIEBAO_BROWSER,          // 猎豹浏览器
        
        // 内置浏览器
        WECHAT_WEBVIEW,          // 微信内置浏览器
        ALIPAY_WEBVIEW,          // 支付宝内置浏览器
        QQ_WEBVIEW,              // QQ内置浏览器
        TAOBAO_WEBVIEW,          // 淘宝内置浏览器
        DOUYIN_WEBVIEW,          // 抖音内置浏览器
        WEIBO_WEBVIEW,           // 微博内置浏览器
        
        // 国际浏览器
        CHROME,                  // Chrome浏览器
        FIREFOX,                 // Firefox浏览器
        EDGE,                    // Edge浏览器
        SAMSUNG_BROWSER,         // 三星浏览器
        
        // 其他
        SYSTEM_WEBVIEW,          // 系统WebView
        UNKNOWN                  // 未知
    }
    
    /**
     * 浏览器信息
     */
    data class BrowserInfo(
        val type: BrowserType,
        val packageName: String,
        val version: String? = null,
        val isWebView: Boolean = false,
        val needsSpecialHandling: Boolean = false,
        val description: String = ""
    )
    
    /**
     * 已知浏览器包名映射
     */
    private val browserPackageMap = mapOf(
        // 小米浏览器
        "com.android.browser" to BrowserType.XIAOMI_BROWSER,
        "com.mi.globalbrowser" to BrowserType.XIAOMI_BROWSER,
        
        // OPPO浏览器
        "com.coloros.browser" to BrowserType.OPPO_BROWSER,
        "com.oppo.browser" to BrowserType.OPPO_BROWSER,
        
        // vivo浏览器
        "com.vivo.browser" to BrowserType.VIVO_BROWSER,
        "com.iqoo.browser" to BrowserType.VIVO_BROWSER,
        
        // 华为浏览器
        "com.huawei.browser" to BrowserType.HUAWEI_BROWSER,
        
        // 荣耀浏览器
        "com.hihonor.browser" to BrowserType.HONOR_BROWSER,
        
        // UC浏览器
        "com.UCMobile" to BrowserType.UC_BROWSER,
        "com.uc.browser.en" to BrowserType.UC_BROWSER,
        "com.uc.browser.hd" to BrowserType.UC_BROWSER,
        
        // QQ浏览器
        "com.tencent.mtt" to BrowserType.QQ_BROWSER,
        
        // 百度浏览器
        "com.baidu.browser.apps" to BrowserType.BAIDU_BROWSER,
        
        // 搜狗浏览器
        "sogou.mobile.explorer" to BrowserType.SOGOU_BROWSER,
        
        // 360浏览器
        "com.qihoo.browser" to BrowserType.BROWSER_360,
        "com.qihoo.contents" to BrowserType.BROWSER_360,
        
        // 猎豹浏览器
        "com.ijinshan.browser_fast" to BrowserType.LIEBAO_BROWSER,
        
        // 微信
        "com.tencent.mm" to BrowserType.WECHAT_WEBVIEW,
        
        // 支付宝
        "com.eg.android.AlipayGphone" to BrowserType.ALIPAY_WEBVIEW,
        
        // QQ
        "com.tencent.mobileqq" to BrowserType.QQ_WEBVIEW,
        
        // 淘宝
        "com.taobao.taobao" to BrowserType.TAOBAO_WEBVIEW,
        
        // 抖音
        "com.ss.android.ugc.aweme" to BrowserType.DOUYIN_WEBVIEW,
        
        // 微博
        "com.sina.weibo" to BrowserType.WEIBO_WEBVIEW,
        
        // Chrome
        "com.android.chrome" to BrowserType.CHROME,
        "com.chrome.beta" to BrowserType.CHROME,
        "com.chrome.dev" to BrowserType.CHROME,
        "com.chrome.canary" to BrowserType.CHROME,
        
        // Firefox
        "org.mozilla.firefox" to BrowserType.FIREFOX,
        "org.mozilla.firefox_beta" to BrowserType.FIREFOX,
        
        // Edge
        "com.microsoft.emmx" to BrowserType.EDGE,
        
        // 三星浏览器
        "com.sec.android.app.sbrowser" to BrowserType.SAMSUNG_BROWSER
    )
    
    /**
     * 检测浏览器类型
     */
    fun detectBrowser(packageName: String, structure: AssistStructure? = null): BrowserInfo {
        Log.d(TAG, "Detecting browser for package: $packageName")
        
        // 1. 首先通过包名匹配
        val browserType = browserPackageMap[packageName] ?: BrowserType.UNKNOWN
        
        // 2. 检查是否是WebView环境
        val isWebView = isWebViewContext(packageName, structure)
        
        // 3. 判断是否需要特殊处理
        val needsSpecialHandling = needsSpecialHandling(browserType)
        
        // 4. 生成描述
        val description = getBrowserDescription(browserType, isWebView)
        
        val browserInfo = BrowserInfo(
            type = browserType,
            packageName = packageName,
            isWebView = isWebView,
            needsSpecialHandling = needsSpecialHandling,
            description = description
        )
        
        Log.d(TAG, "Browser detected: ${browserInfo.description}")
        
        return browserInfo
    }
    
    /**
     * 判断是否是WebView环境
     */
    private fun isWebViewContext(packageName: String, structure: AssistStructure?): Boolean {
        // 已知的App内置浏览器
        val webViewApps = setOf(
            "com.tencent.mm",           // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.tencent.mobileqq",     // QQ
            "com.taobao.taobao",        // 淘宝
            "com.ss.android.ugc.aweme", // 抖音
            "com.sina.weibo"            // 微博
        )
        
        if (packageName in webViewApps) {
            return true
        }
        
        // 检查AssistStructure中是否包含WebView特征
        if (structure != null) {
            for (i in 0 until structure.windowNodeCount) {
                val windowNode = structure.getWindowNodeAt(i)
                if (containsWebView(windowNode.rootViewNode)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 递归检查ViewNode是否包含WebView
     */
    private fun containsWebView(node: AssistStructure.ViewNode): Boolean {
        val className = node.className ?: return false
        
        // 检查是否是WebView相关的类
        if (className.contains("WebView", ignoreCase = true) ||
            className.contains("X5WebView", ignoreCase = true) || // 腾讯X5内核
            className.contains("UCWebView", ignoreCase = true)) {
            return true
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            if (containsWebView(node.getChildAt(i))) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 判断是否需要特殊处理
     */
    private fun needsSpecialHandling(browserType: BrowserType): Boolean {
        return when (browserType) {
            // 国产手机浏览器通常需要特殊处理
            BrowserType.XIAOMI_BROWSER,
            BrowserType.OPPO_BROWSER,
            BrowserType.VIVO_BROWSER,
            BrowserType.HUAWEI_BROWSER,
            BrowserType.HONOR_BROWSER -> true
            
            // 内置浏览器需要特殊处理
            BrowserType.WECHAT_WEBVIEW,
            BrowserType.ALIPAY_WEBVIEW,
            BrowserType.QQ_WEBVIEW,
            BrowserType.TAOBAO_WEBVIEW,
            BrowserType.DOUYIN_WEBVIEW,
            BrowserType.WEIBO_WEBVIEW -> true
            
            // 使用X5内核的浏览器
            BrowserType.UC_BROWSER,
            BrowserType.QQ_BROWSER -> true
            
            else -> false
        }
    }
    
    /**
     * 获取浏览器描述
     */
    private fun getBrowserDescription(browserType: BrowserType, isWebView: Boolean): String {
        val base = when (browserType) {
            BrowserType.XIAOMI_BROWSER -> "小米浏览器"
            BrowserType.OPPO_BROWSER -> "OPPO浏览器"
            BrowserType.VIVO_BROWSER -> "vivo浏览器"
            BrowserType.HUAWEI_BROWSER -> "华为浏览器"
            BrowserType.HONOR_BROWSER -> "荣耀浏览器"
            BrowserType.UC_BROWSER -> "UC浏览器"
            BrowserType.QQ_BROWSER -> "QQ浏览器"
            BrowserType.BAIDU_BROWSER -> "百度浏览器"
            BrowserType.SOGOU_BROWSER -> "搜狗浏览器"
            BrowserType.BROWSER_360 -> "360浏览器"
            BrowserType.LIEBAO_BROWSER -> "猎豹浏览器"
            BrowserType.WECHAT_WEBVIEW -> "微信内置浏览器"
            BrowserType.ALIPAY_WEBVIEW -> "支付宝内置浏览器"
            BrowserType.QQ_WEBVIEW -> "QQ内置浏览器"
            BrowserType.TAOBAO_WEBVIEW -> "淘宝内置浏览器"
            BrowserType.DOUYIN_WEBVIEW -> "抖音内置浏览器"
            BrowserType.WEIBO_WEBVIEW -> "微博内置浏览器"
            BrowserType.CHROME -> "Chrome浏览器"
            BrowserType.FIREFOX -> "Firefox浏览器"
            BrowserType.EDGE -> "Edge浏览器"
            BrowserType.SAMSUNG_BROWSER -> "三星浏览器"
            BrowserType.SYSTEM_WEBVIEW -> "系统WebView"
            BrowserType.UNKNOWN -> "未知浏览器"
        }
        
        return if (isWebView && !base.contains("内置")) {
            "$base (WebView)"
        } else {
            base
        }
    }
    
    /**
     * 获取针对特定浏览器的优化建议
     */
    fun getOptimizationTips(browserInfo: BrowserInfo): List<String> {
        return when (browserInfo.type) {
            BrowserType.XIAOMI_BROWSER -> listOf(
                "小米浏览器使用自定义WebView内核",
                "建议增加字段识别的容错率",
                "可能需要延长响应超时时间"
            )
            
            BrowserType.OPPO_BROWSER, BrowserType.VIVO_BROWSER -> listOf(
                "ColorOS/OriginOS浏览器对自动填充有定制优化",
                "响应速度较快，可适当缩短超时时间",
                "支持完整的内联建议功能"
            )
            
            BrowserType.HUAWEI_BROWSER, BrowserType.HONOR_BROWSER -> listOf(
                "HarmonyOS/MagicOS浏览器需要更长的响应时间",
                "不支持内联建议，仅使用下拉列表",
                "WebView解析可能需要额外处理"
            )
            
            BrowserType.WECHAT_WEBVIEW -> listOf(
                "微信使用腾讯X5 WebView内核",
                "DOM结构可能与标准WebView不同",
                "建议使用更宽松的字段匹配规则"
            )
            
            BrowserType.ALIPAY_WEBVIEW -> listOf(
                "支付宝WebView有严格的安全限制",
                "可能无法访问某些DOM属性",
                "建议使用多种字段识别策略"
            )
            
            BrowserType.UC_BROWSER, BrowserType.QQ_BROWSER -> listOf(
                "使用腾讯X5内核",
                "与微信WebView类似的特性",
                "支持较完整的自动填充API"
            )
            
            else -> listOf(
                "使用标准Android自动填充API",
                "无需特殊处理"
            )
        }
    }
    
    /**
     * 获取推荐的超时时间（毫秒）
     */
    fun getRecommendedTimeout(browserInfo: BrowserInfo): Long {
        return when (browserInfo.type) {
            // 国产快速浏览器
            BrowserType.OPPO_BROWSER,
            BrowserType.VIVO_BROWSER -> 2500L
            
            // 小米浏览器
            BrowserType.XIAOMI_BROWSER -> 3000L
            
            // 华为/荣耀浏览器
            BrowserType.HUAWEI_BROWSER,
            BrowserType.HONOR_BROWSER -> 4000L
            
            // 内置浏览器（可能较慢）
            BrowserType.WECHAT_WEBVIEW,
            BrowserType.ALIPAY_WEBVIEW,
            BrowserType.QQ_WEBVIEW,
            BrowserType.TAOBAO_WEBVIEW,
            BrowserType.DOUYIN_WEBVIEW,
            BrowserType.WEIBO_WEBVIEW -> 4500L
            
            // 使用X5内核的浏览器
            BrowserType.UC_BROWSER,
            BrowserType.QQ_BROWSER -> 3500L
            
            // 国际浏览器（较标准）
            BrowserType.CHROME,
            BrowserType.FIREFOX,
            BrowserType.EDGE -> 3000L
            
            // 默认
            else -> 5000L
        }
    }
    
    /**
     * 是否支持内联建议
     */
    fun supportsInlineSuggestions(browserInfo: BrowserInfo): Boolean {
        return when (browserInfo.type) {
            // 华为/荣耀浏览器不支持
            BrowserType.HUAWEI_BROWSER,
            BrowserType.HONOR_BROWSER -> false
            
            // 部分内置浏览器不稳定
            BrowserType.WECHAT_WEBVIEW,
            BrowserType.ALIPAY_WEBVIEW -> false
            
            // 其他浏览器支持
            else -> true
        }
    }
    
    /**
     * 获取浏览器的完整分析报告
     */
    fun getAnalysisReport(browserInfo: BrowserInfo): String {
        return buildString {
            appendLine("=== 浏览器分析报告 ===")
            appendLine("浏览器: ${browserInfo.description}")
            appendLine("包名: ${browserInfo.packageName}")
            appendLine("WebView环境: ${if (browserInfo.isWebView) "是" else "否"}")
            appendLine("需要特殊处理: ${if (browserInfo.needsSpecialHandling) "是" else "否"}")
            appendLine("推荐超时: ${getRecommendedTimeout(browserInfo)}ms")
            appendLine("支持内联建议: ${if (supportsInlineSuggestions(browserInfo)) "是" else "否"}")
            appendLine()
            appendLine("优化建议:")
            getOptimizationTips(browserInfo).forEachIndexed { index, tip ->
                appendLine("  ${index + 1}. $tip")
            }
            appendLine("========================")
        }
    }
}
