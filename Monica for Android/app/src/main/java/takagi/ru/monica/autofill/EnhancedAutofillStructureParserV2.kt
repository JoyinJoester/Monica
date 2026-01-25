package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.autofill.HintConstants
import takagi.ru.monica.autofill.core.safeTextOrNull

/**
 * 增强的自动填充结构解析器
 * 
 * 基于 Keyguard 的实现，提供更准确的字段识别：
 * - 多语言标签支持（中文、英文、俄语等）
 * - 准确度评分系统
 * - WebView 检测
 * - HTML 属性解析
 * - 更多字段类型支持
 * 
 * @author Monica Development Team
 * @version 2.0
 */
class EnhancedAutofillStructureParserV2 {
    
    /**
     * 解析结果数据类
     */
    data class ParsedStructure(
        val applicationId: String? = null,
        val webScheme: String? = null,
        val webDomain: String? = null,
        val webView: Boolean = false,
        val items: List<ParsedItem>
    )
    
    /**
     * 解析的字段项
     */
    data class ParsedItem(
        val id: AutofillId,
        val hint: FieldHint,
        val accuracy: Accuracy,
        val value: String? = null,
        val isFocused: Boolean = false,
        val isVisible: Boolean = true,
        val parentWebViewNodeId: Int? = null,
        val traversalIndex: Int = 0 // 新增：遍历顺序索引，用于根据垂直位置纠错
    )
    
    /**
     * 字段类型提示
     */
    enum class FieldHint {
        USERNAME,
        PASSWORD,
        NEW_PASSWORD,
        EMAIL_ADDRESS,
        PHONE_NUMBER,
        SEARCH_FIELD,  // 搜索字段 - 不应触发自动填充
        CREDIT_CARD_NUMBER,
        CREDIT_CARD_EXPIRATION_DATE,
        CREDIT_CARD_SECURITY_CODE,
        CREDIT_CARD_HOLDER_NAME,  // 新增: 持卡人姓名
        POSTAL_ADDRESS,
        POSTAL_CODE,
        PERSON_NAME,
        OTP_CODE,
        UNKNOWN
    }
    
    /**
     * 准确度评分
     */
    enum class Accuracy(val score: Float) {
        LOWEST(0.3f),
        LOW(0.7f),
        MEDIUM(1.5f),
        HIGH(4f),
        HIGHEST(10f)
    }
    
    /**
     * 标签匹配器
     */
    private data class LabelMatcher(
        val hint: FieldHint,
        val pattern: String,
        val accuracy: Accuracy = Accuracy.HIGH,
        val partialMatch: Boolean = false
    ) {
        fun matches(text: String): Boolean {
            return if (partialMatch) {
                text.contains(pattern, ignoreCase = true)
            } else {
                text.equals(pattern, ignoreCase = true)
            }
        }
    }
    
    // ==================== 多语言标签定义 ====================
    
    /**
     * 密码字段的多语言翻译
     */
    private val passwordTranslations = listOf(
        "password",     // 英语
        "密码",         // 中文简体
        "密碼",         // 中文繁体
        "パスワード",   // 日语
        "비밀번호",     // 韩语
        "passwort",     // 德语
        "mot de passe", // 法语
        "contraseña",   // 西班牙语
        "senha",        // 葡萄牙语
        "пароль",       // 俄语
        "wachtwoord",   // 荷兰语
        "hasło",        // 波兰语
        "şifre",        // 土耳其语
        "รหัสผ่าน",    // 泰语
        "mật khẩu",     // 越南语
    )
    
    /**
     * 用户名字段的多语言翻译
     */
    private val usernameTranslations = listOf(
        "username",     // 英语
        "login",
        "account",
        "用户名",       // 中文简体
        "用戶名",       // 中文繁体
        "账号",
        "帐号",
        "ユーザー名",   // 日语
        "사용자명",     // 韩语
        "benutzername", // 德语
        "utilisateur",  // 法语
        "usuario",      // 西班牙语
        "usuário",      // 葡萄牙语
        "пользователь", // 俄语
        "gebruiker",    // 荷兰语
        "użytkownik",   // 波兰语
        "kullanıcı",    // 土耳其语
        "ชื่อผู้ใช้",  // 泰语
        "tên người dùng", // 越南语
        "nickname",
        "customer",
    )
    
    /**
     * 邮箱字段的多语言翻译
     */
    private val emailTranslations = listOf(
        "email",        // 英语
        "e-mail",
        "mail",
        "电子邮件",     // 中文简体
        "电子邮箱",
        "邮箱",
        "電子郵件",     // 中文繁体
        "電子郵箱",
        "郵箱",
        "メール",       // 日语
        "이메일",       // 韩语
        "correo",       // 西班牙语
        "почта",        // 俄语
        "posta",        // 土耳其语
        "อีเมล",       // 泰语
    )
    
    /**
     * 电话字段的多语言翻译
     */
    private val phoneTranslations = listOf(
        "phone",        // 英语
        "telephone",
        "tel",
        "mobile",
        "电话",         // 中文简体
        "手机",
        "電話",         // 中文繁体
        "手機",
        "電話番号",     // 日语
        "전화",         // 韩语
        "telefon",      // 德语/土耳其语
        "téléphone",    // 法语
        "teléfono",     // 西班牙语
        "telefone",     // 葡萄牙语
        "телефон",      // 俄语
        "โทรศัพท์",   // 泰语
    )
    
    /**
     * OTP/验证码字段的翻译
     */
    private val otpTranslations = listOf(
        "otp",
        "2fa",
        "mfa",
        "code",
        "verification",
        "verify",
        "验证码",       // 中文简体
        "驗證碼",       // 中文繁体
        "認證コード",   // 日语
        "인증코드",     // 韩语
        "код",          // 俄语
        "doğrulama",    // 土耳其语
        "xác minh",     // 越南语
    )
    
    /**
     * 信用卡号的翻译
     */
    private val creditCardNumberTranslations = listOf(
        "card number",
        "card",
        "credit",
        "debit",
        "卡号",         // 中文简体
        "銀行卡",
        "信用卡",
        "クレジットカード", // 日语
        "카드번호",     // 韩语
        "tarjeta",      // 西班牙语
        "cartão",       // 葡萄牙语
        "карта",        // 俄语
        "kart",         // 土耳其语
    )
    
    /**
     * 信用卡有效期的翻译
     */
    private val creditCardExpiryTranslations = listOf(
        "expiry",
        "expiration",
        "exp date",
        "valid",
        "有效期",       // 中文简体
        "到期日",
        "有效日期",
        "有効期限",     // 日语
        "만료일",       // 韩语
        "vencimiento",  // 西班牙语
        "validade",     // 葡萄牙语
        "срок",         // 俄语
        "son kullanma", // 土耳其语
    )
    
    /**
     * CVV/安全码的翻译
     */
    private val creditCardCvvTranslations = listOf(
        "cvv",
        "cvc",
        "cid",
        "security code",
        "verification",
        "安全码",       // 中文简体
        "验证码",
        "セキュリティコード", // 日语
        "보안코드",     // 韩语
        "código",       // 西班牙语/葡萄牙语
        "код",          // 俄语
        "güvenlik",     // 土耳其语
    )
    
    /**
     * 持卡人姓名的翻译
     */
    private val cardHolderNameTranslations = listOf(
        "cardholder",
        "card holder",
        "name on card",
        "持卡人",       // 中文简体
        "姓名",
        "カード名義",   // 日语
        "카드소유자",   // 韩语
        "titular",      // 西班牙语/葡萄牙语
        "владелец",     // 俄语
        "kart sahibi",  // 土耳其语
    )
    
    /**
     * 邮政编码的翻译
     */
    private val postalCodeTranslations = listOf(
        "zip",
        "zip code",
        "postal",
        "postcode",
        "邮编",         // 中文简体
        "邮政编码",
        "郵便番号",     // 日语
        "우편번호",     // 韩语
        "código postal", // 西班牙语
        "código postal", // 葡萄牙语
        "почтовый индекс", // 俄语
        "posta kodu",   // 土耳其语
    )
    
    /**
     * 地址的翻译
     */
    private val addressTranslations = listOf(
        "address",
        "street",
        "billing",
        "地址",         // 中文简体
        "住所",         // 日语
        "주소",         // 韩语
        "dirección",    // 西班牙语
        "endereço",     // 葡萄牙语
        "адрес",        // 俄语
        "adres",        // 土耳其语
    )
    
    /**
     * 非凭据字段的翻译（用于过滤，避免在非登录输入框弹出自动填充）
     * 包括：搜索框、评论框、聊天框、发帖框、备注框等
     * 
     * ⚠️ 重要：这些关键词会被用于排除非凭据输入框，防止在评论/聊天/搜索等场景误触发自动填充
     */
    private val searchTranslations = listOf(
        // ========== 搜索相关 ==========
        "search", "query", "find", "lookup", "explore", "filter", "q",
        "searchbox", "searchfield", "searchinput", "searchbar",
        "搜索", "查找", "检索", "探索", "筛选", "搜一搜",
        "搜尋", "查詢", "檢索",
        "検索", "探す",
        "검색", "찾기",
        "поиск", "искать",
        "buscar", "búsqueda",
        "pesquisar", "busca",
        
        // ========== 评论相关 (增强) ==========
        "comment", "comments", "commenting", "reply", "replies", "replying",
        "review", "reviews", "feedback", "feedbacks",
        "评论", "留言", "回复", "回覆", "评价", "吐槽", "弹幕", "发言",
        "发表评论", "写评论", "添加评论", "我要评论", "说点什么", "来说点什么吧",
        "说一个", "输入评论", "评论框", "留言板",
        "コメント", "返信", "댓글", "답글", "отзыв", "комментарий",
        
        // ========== 聊天/消息相关 (增强) ==========
        "chat", "chatting", "message", "messages", "messaging", "msg",
        "messenger", "send", "sending", "input_message", "messagebox",
        "im_input", "chat_input", "chatinput", "inputbox", "inputtext",
        "聊天", "消息", "私信", "发送", "訊息", "私訊", "信息", "短信",
        "发消息", "说些什么", "输入消息", "写消息", "发送消息", "聊天框",
        "输入点什么", "打字机", "键盘输入", "写点什么",
        "チャット", "メッセージ", "送信", "채팅", "메시지", "чат", "сообщение",
        
        // ========== 发帖/发推/社交相关 (增强) ==========
        "post", "posting", "tweet", "tweeting", "toot", "status", "statuses",
        "compose", "composing", "write", "writing", "publish", "publishing",
        "share", "sharing", "newpost", "new_post", "createpost", "create_post",
        "发帖", "发推", "发文", "发布", "分享", "动态", "發文", "说说",
        "朋友圈", "微博", "想法", "问答", "提问", "回答", "举报", "发布动态",
        "投稿", "ツイート", "게시", "글쓰기",
        
        // ========== 备注/说明相关 ==========
        "note", "notes", "noting", "memo", "memos", "remark", "remarks",
        "description", "descriptions", "bio", "biography", "about", "aboutme",
        "备注", "说明", "简介", "描述", "自我介绍", "个人简介", "个性签名", "签到",
        "メモ", "備考", "メモ帳", "メモを入力", "메모", "заметка",
        
        // ========== 标题/内容/编辑相关 (增强) ==========
        "title", "titles", "content", "contents", "body", "bodies",
        "text", "texts", "article", "articles", "editor", "editing",

        "textarea", "textfield", "textbox", "edittext", "inputfield",
        "input_bar", "bottom_bar", "input_panel", "smile_panel",
        "标题", "内容", "正文", "文章", "编辑", "输入框", "文本内容",
        "主题", "摘要", "引言",
        
        // ========== 其他非凭据字段 ==========
        "caption", "captions", "tag", "tags", "hashtag", "hashtags",
        "label", "labels", "location", "locations", "place", "places",
        "address_search", "poi", "keyword", "keywords",
        "标签", "位置", "地点", "关键词", "关键字", "分类",
        
        // ========== 游戏/应用特定 ==========
        "nickname", "nick", "gameid", "playerid", "ingame",
        "昵称", "游戏名", "角色名", "玩家名", "绰号", "别名",
        
        // ========== 表单非凭据字段 ==========
        "subject", "subjects", "reason", "reasons", "purpose", "purposes",
        "suggestion", "suggestions", "opinion", "opinions", "idea", "ideas",
        "主题", "原因", "目的", "建议", "意见", "想法", "问题", "回答内容"
    )
    
    // ==================== 标签匹配器列表 ====================
    
    private val labelMatchers = buildList {
        // 密码匹配
        passwordTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.PASSWORD, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 用户名匹配
        usernameTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.USERNAME, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 邮箱匹配
        emailTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.EMAIL_ADDRESS, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 电话匹配
        phoneTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.PHONE_NUMBER, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // OTP 匹配
        otpTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.OTP_CODE, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 信用卡号匹配
        creditCardNumberTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.CREDIT_CARD_NUMBER, translation, Accuracy.MEDIUM, partialMatch = true))
        }
        
        // 信用卡有效期匹配
        creditCardExpiryTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.CREDIT_CARD_EXPIRATION_DATE, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // CVV/安全码匹配
        creditCardCvvTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.CREDIT_CARD_SECURITY_CODE, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 持卡人姓名匹配
        cardHolderNameTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.CREDIT_CARD_HOLDER_NAME, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 邮政编码匹配
        postalCodeTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.POSTAL_CODE, translation, Accuracy.HIGH, partialMatch = true))
        }
        
        // 地址匹配
        addressTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.POSTAL_ADDRESS, translation, Accuracy.MEDIUM, partialMatch = true))
        }
        
        // 搜索字段匹配（用于过滤，设置为HIGHEST优先级确保优先检测）
        searchTranslations.forEach { translation ->
            add(LabelMatcher(FieldHint.SEARCH_FIELD, translation, Accuracy.HIGHEST, partialMatch = true))
        }
    }
    
    /**
     * 解析 AssistStructure
     */
    fun parse(structure: AssistStructure, respectAutofillOff: Boolean = true): ParsedStructure {
        val items = mutableListOf<ParsedItem>()
        var applicationId: String? = null
        var webScheme: String? = null
        var webDomain: String? = null
        var isWebView = false
        
        // 获取应用ID
        applicationId = structure.activityComponent.packageName
        
        // 全局遍历计数器
        var globalTraversalIndex = 0
        
        // 遍历所有窗口和节点
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode

            val info = WebViewInfo()
            
            // 使用可变计数器进行遍历
            globalTraversalIndex = traverseNode(
                node = rootNode,
                items = items,
                respectAutofillOff = respectAutofillOff,
                webViewInfo = info,
                startIndex = globalTraversalIndex
            )

            if (info.isWebView) {
                isWebView = true
            }

            if (info.webDomain != null) {
                webDomain = info.webDomain
            }

            if (info.webScheme != null) {
                webScheme = info.webScheme
            }
        }
        
        // 按准确度排序
        items.sortByDescending { it.accuracy.score }
        
        android.util.Log.d("EnhancedParser", "Parsed ${items.size} items, webView=$isWebView, domain=$webDomain")
        
        return ParsedStructure(
            applicationId = applicationId,
            webScheme = webScheme,
            webDomain = webDomain,
            webView = isWebView,
            items = items
        )
    }
    
    /**
     * WebView 信息
     */
    private data class WebViewInfo(
        var isWebView: Boolean = false,
        var webScheme: String? = null,
        var webDomain: String? = null
    )
    
    /**
     * 递归遍历节点
     */
    private fun traverseNode(
        node: AssistStructure.ViewNode,
        items: MutableList<ParsedItem>,
        respectAutofillOff: Boolean,
        parentWebViewNodeId: Int? = null,
        webViewInfo: WebViewInfo,
        startIndex: Int
    ): Int {
        var currentIndex = startIndex
        
        // 检查是否是 WebView
        val currentWebViewNodeId = if (node.className == "android.webkit.WebView") {
            webViewInfo.isWebView = true
            node.id
        } else {
            parentWebViewNodeId
        }

        if (currentWebViewNodeId != null) {
            webViewInfo.isWebView = true
        }

        // 尝试提取 WebView 或浏览器提供的域名信息
        node.webDomain?.let { domain ->
            if (webViewInfo.webDomain == null) {
                webViewInfo.webDomain = domain
            }
            val scheme = node.webScheme ?: "https"
            if (webViewInfo.webScheme == null) {
                webViewInfo.webScheme = scheme
            }
        }
        
        // 检查是否需要忽略（autofill=off）
        if (respectAutofillOff && node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO) {
            android.util.Log.d("EnhancedParser", "Skipping node with autofill=off: ${node.idEntry}")
            return currentIndex
        }
        
        // 增加遍历索引（仅对可见节点计数，或者对所有节点计数皆可，这里统计所有节点以保持相对顺序）
        currentIndex++
        
        // 尝试解析当前节点
        val autofillId = node.autofillId
        if (autofillId != null && node.autofillType != View.AUTOFILL_TYPE_NONE) {
            detectFieldType(node, currentWebViewNodeId)?.let { parsedItem ->
                // 过滤搜索字段，不将其添加到可填充字段列表中
                if (parsedItem.hint == FieldHint.SEARCH_FIELD) {
                    android.util.Log.d("EnhancedParser", "🔍 Skipping search field: ${node.idEntry ?: node.hint ?: "unknown"}")
                    return@let
                }
                
                // 添加带有遍历索引的项
                val itemWithIndex = parsedItem.copy(traversalIndex = currentIndex)
                items.add(itemWithIndex)
                
                android.util.Log.d("EnhancedParser", "Found ${parsedItem.hint} field with accuracy ${parsedItem.accuracy} at index $currentIndex")
            }
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            node.getChildAt(i)?.let { childNode ->
                currentIndex = traverseNode(childNode, items, respectAutofillOff, currentWebViewNodeId, webViewInfo, currentIndex)
            }
        }
        
        return currentIndex
    }
    
    /**
     * 检测字段类型
     * 
     * ⚠️ 优先级说明：
     * - 首先检查是否为非凭据字段（评论/聊天/搜索等），避免误触发
     * - 然后按 autofillHints > HTML属性 > inputType > 文本标签 的优先级检测
     */
    private fun detectFieldType(
        node: AssistStructure.ViewNode,
        parentWebViewNodeId: Int?
    ): ParsedItem? {
        val autofillId = node.autofillId ?: return null
        
        // ========== 首先：检查是否为非凭据字段（最高优先级排除）==========
        // 收集所有可用于匹配的文本
        val allTexts = listOfNotNull(
            node.idEntry?.lowercase(),
            node.hint?.lowercase(),
            node.text?.toString()?.lowercase(),
            node.contentDescription?.toString()?.lowercase()
        )
        
        // 检查是否匹配任何非凭据字段关键词
        for (text in allTexts) {
            for (searchKeyword in searchTranslations) {
                if (text.contains(searchKeyword, ignoreCase = true)) {
                    android.util.Log.d("EnhancedParser", "⛔ Non-credential field detected: '$text' contains '$searchKeyword'")
                    return ParsedItem(
                        id = autofillId,
                        hint = FieldHint.SEARCH_FIELD,
                        accuracy = Accuracy.HIGHEST,
                        value = null,
                        isFocused = node.isFocused,
                        isVisible = node.visibility == View.VISIBLE,
                        parentWebViewNodeId = parentWebViewNodeId,
                        traversalIndex = 0
                    )
                }
            }
        }
        
        var bestMatch: Pair<FieldHint, Accuracy>? = null
        
        // ========== 策略 1: 检查 autofillHints（最高优先级）==========
        node.autofillHints?.forEach { hint ->
            val detected = detectFromAutofillHint(hint)
            if (detected != null && (bestMatch == null || detected.second.score > bestMatch!!.second.score)) {
                bestMatch = detected
            }
        }
        
        // ========== 策略 2: 检查 HTML 属性 ==========
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val detected = detectFromHtmlAttributes(node)
                if (detected != null && (bestMatch == null || detected.second.score > bestMatch!!.second.score)) {
                    bestMatch = detected
                }
        }
        
        // ========== 策略 3: 检查 inputType ==========
        val detected = detectFromInputType(node.inputType)
        if (detected != null && (bestMatch == null || detected.second.score > bestMatch!!.second.score)) {
            bestMatch = detected
        }
        
        // ========== 策略 4: 检查文本标签（最低优先级）==========
        val labelTexts = listOfNotNull(
            node.idEntry,
            node.hint,
            node.text?.toString(),
            node.contentDescription?.toString()
        )
        
        labelTexts.forEach { labelText ->
            labelMatchers.forEach { matcher ->
                if (matcher.matches(labelText)) {
                    val detectedAccuracy = if (matcher.partialMatch) Accuracy.MEDIUM else matcher.accuracy
                    if (bestMatch == null || detectedAccuracy.score > bestMatch!!.second.score) {
                        bestMatch = matcher.hint to detectedAccuracy
                    }
                }
            }
        }
        
        // 如果找到匹配，返回结果
        return bestMatch?.let { (hint, accuracy) ->
            ParsedItem(
                id = autofillId,
                hint = hint,
                accuracy = accuracy,
                value = (node.autofillValue)
                    .safeTextOrNull(tag = "EnhancedParserV2", fieldDescription = hint.name),
                isFocused = node.isFocused,
                isVisible = node.visibility == View.VISIBLE,
                parentWebViewNodeId = parentWebViewNodeId,
                traversalIndex = 0 // 默认为0，将在 traverseNode 中重新赋值
            )
        }
    }
    
    /**
     * 从 autofillHints 检测字段类型
     */
    private fun detectFromAutofillHint(hint: String): Pair<FieldHint, Accuracy>? {
        return when (hint) {
            HintConstants.AUTOFILL_HINT_USERNAME,
            "username" -> FieldHint.USERNAME to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_PASSWORD,
            "password",
            "current-password" -> FieldHint.PASSWORD to Accuracy.HIGHEST
            
            "new-password" -> FieldHint.NEW_PASSWORD to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS,
            "email" -> FieldHint.EMAIL_ADDRESS to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_PHONE,
            HintConstants.AUTOFILL_HINT_PHONE_NUMBER,
            "tel" -> FieldHint.PHONE_NUMBER to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_CREDIT_CARD_NUMBER,
            "cc-number" -> FieldHint.CREDIT_CARD_NUMBER to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE,
            "cc-exp" -> FieldHint.CREDIT_CARD_EXPIRATION_DATE to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE,
            "cc-csc" -> FieldHint.CREDIT_CARD_SECURITY_CODE to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_POSTAL_ADDRESS,
            "address" -> FieldHint.POSTAL_ADDRESS to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_POSTAL_CODE,
            "postal-code" -> FieldHint.POSTAL_CODE to Accuracy.HIGHEST
            
            HintConstants.AUTOFILL_HINT_NAME,
            HintConstants.AUTOFILL_HINT_PERSON_NAME,
            "name" -> FieldHint.PERSON_NAME to Accuracy.HIGHEST
            
            "one-time-code",
            "sms-otp" -> FieldHint.OTP_CODE to Accuracy.HIGHEST
            
            else -> null
        }
    }
    
    /**
     * 从 HTML 属性检测字段类型
     */
    private fun detectFromHtmlAttributes(htmlInfo: android.app.assist.AssistStructure.ViewNode): Pair<FieldHint, Accuracy>? {
        // 检查 autocomplete 属性
        val autocomplete = htmlInfo.htmlInfo?.attributes
            ?.find { attr -> attr.first == "autocomplete" }
            ?.second?.lowercase()
        
        autocomplete?.let { value ->
            return when {
                value.contains("username") || value.contains("email") -> FieldHint.USERNAME to Accuracy.HIGH
                value.contains("current-password") -> FieldHint.PASSWORD to Accuracy.HIGH
                value.contains("new-password") -> FieldHint.NEW_PASSWORD to Accuracy.HIGH
                value.contains("tel") -> FieldHint.PHONE_NUMBER to Accuracy.HIGH
                value.contains("cc-number") -> FieldHint.CREDIT_CARD_NUMBER to Accuracy.HIGH
                value.contains("one-time-code") -> FieldHint.OTP_CODE to Accuracy.HIGH
                else -> null
            }
        }
        
        // 检查 type 属性
        val type = htmlInfo.htmlInfo?.attributes
            ?.find { attr -> attr.first == "type" }
            ?.second?.lowercase()
        
        type?.let { typeValue ->
            return when (typeValue) {
                "password" -> FieldHint.PASSWORD to Accuracy.HIGH
                "email" -> FieldHint.EMAIL_ADDRESS to Accuracy.HIGH
                "tel" -> FieldHint.PHONE_NUMBER to Accuracy.HIGH
                else -> null
            }
        }
        
        // 检查 name 属性
        val name = htmlInfo.htmlInfo?.attributes
            ?.find { attr -> attr.first == "name" }
            ?.second?.lowercase()
        
        name?.let { nameValue ->
            return when {
                nameValue.contains("password") -> FieldHint.PASSWORD to Accuracy.MEDIUM
                nameValue.contains("email") -> FieldHint.EMAIL_ADDRESS to Accuracy.MEDIUM
                nameValue.contains("username") || nameValue.contains("login") -> FieldHint.USERNAME to Accuracy.MEDIUM
                nameValue.contains("tel") || nameValue.contains("phone") -> FieldHint.PHONE_NUMBER to Accuracy.MEDIUM
                nameValue.contains("otp") || nameValue.contains("code") -> FieldHint.OTP_CODE to Accuracy.MEDIUM
                else -> null
            }
        }
        
        return null
    }
    
    /**
     * 从 inputType 检测字段类型
     */
    private fun detectFromInputType(inputType: Int): Pair<FieldHint, Accuracy>? {
        return when {
            // 密码类型
            (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 -> 
                FieldHint.PASSWORD to Accuracy.HIGH
            
            // 邮箱类型
            (inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) != 0 -> 
                FieldHint.EMAIL_ADDRESS to Accuracy.HIGH
            
            // 电话类型
            (inputType and InputType.TYPE_CLASS_PHONE) != 0 -> 
                FieldHint.PHONE_NUMBER to Accuracy.HIGH
            
            // 数字类型（可能是OTP）
            (inputType and InputType.TYPE_CLASS_NUMBER) != 0 -> 
                FieldHint.OTP_CODE to Accuracy.LOW
            
            // 人名类型
            (inputType and InputType.TYPE_TEXT_VARIATION_PERSON_NAME) != 0 -> 
                FieldHint.PERSON_NAME to Accuracy.HIGH
            
            // 地址类型
            (inputType and InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS) != 0 -> 
                FieldHint.POSTAL_ADDRESS to Accuracy.HIGH
            
            else -> null
        }
    }
}
