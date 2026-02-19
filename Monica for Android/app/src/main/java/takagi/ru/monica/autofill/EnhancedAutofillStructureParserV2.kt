package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.autofill.HintConstants
import takagi.ru.monica.autofill.core.safeTextOrNull
import java.util.Locale

class EnhancedAutofillStructureParserV2 {
    data class ParsedStructure(
        val applicationId: String? = null,
        val webScheme: String? = null,
        val webDomain: String? = null,
        val webView: Boolean = false,
        val items: List<ParsedItem>,
    )

    data class ParsedItem(
        val id: AutofillId,
        val hint: FieldHint,
        val accuracy: Accuracy,
        val value: String? = null,
        val isFocused: Boolean = false,
        val isVisible: Boolean = true,
        val parentWebViewNodeId: Int? = null,
        val traversalIndex: Int = 0,
    )

    enum class FieldHint {
        USERNAME,
        PASSWORD,
        NEW_PASSWORD,
        EMAIL_ADDRESS,
        PHONE_NUMBER,
        SEARCH_FIELD,
        CREDIT_CARD_NUMBER,
        CREDIT_CARD_EXPIRATION_DATE,
        CREDIT_CARD_SECURITY_CODE,
        CREDIT_CARD_HOLDER_NAME,
        POSTAL_ADDRESS,
        POSTAL_CODE,
        PERSON_NAME,
        OTP_CODE,
        UNKNOWN,
    }

    enum class Accuracy(val score: Float) {
        LOWEST(0.3f),
        LOW(0.7f),
        MEDIUM(1.5f),
        HIGH(4f),
        HIGHEST(10f),
    }

    private enum class InternalHint {
        USERNAME,
        PASSWORD,
        NEW_PASSWORD,
        EMAIL_ADDRESS,
        PHONE_NUMBER,
        CREDIT_CARD_NUMBER,
        CREDIT_CARD_EXPIRATION_DATE,
        CREDIT_CARD_EXPIRATION_MONTH,
        CREDIT_CARD_EXPIRATION_YEAR,
        CREDIT_CARD_EXPIRATION_DAY,
        CREDIT_CARD_SECURITY_CODE,
        CREDIT_CARD_HOLDER_NAME,
        POSTAL_ADDRESS,
        POSTAL_CODE,
        PERSON_NAME,
        OTP_CODE,
        OFF,
        UNKNOWN,
    }

    private data class RawParsedStructure(
        val webScheme: String? = null,
        val webDomain: String? = null,
        val webView: Boolean = false,
        val items: List<RawParsedItem>,
    )

    private data class RawParsedItem(
        val id: AutofillId,
        val accuracy: Accuracy,
        val hint: InternalHint,
        val value: String? = null,
        val reason: String? = null,
        val parentWebViewNodeId: Int? = null,
        val isFocused: Boolean = false,
        val isVisible: Boolean = true,
        val traversalIndex: Int = 0,
    )

    private data class ParsedItemBuilder(
        val accuracy: Accuracy,
        val hint: InternalHint,
        val value: String? = null,
        val reason: String? = null,
    )

    private data class HintScore(
        val score: Float,
        val hint: InternalHint,
        val value: String?,
        val accuracy: Accuracy,
        val isFocused: Boolean,
        val isVisible: Boolean,
        val parentWebViewNodeId: Int?,
        val traversalIndex: Int,
    )

    private data class ParseContext(
        var traversalIndex: Int = 0,
    )

    private class AutofillHintMatcher(
        val hint: InternalHint,
        val target: String,
        val partly: Boolean = false,
    ) {
        val accuracy = if (partly) Accuracy.MEDIUM else Accuracy.HIGH

        fun matches(value: String): Boolean = if (partly) {
            value.contains(target, ignoreCase = true)
        } else {
            value.equals(target, ignoreCase = true)
        }
    }

    private val autofillLabelPasswordTranslations = listOf(
        "password",
        "парол",
        "parol",
        "passwort",
        "passe",
        "密码",
        "密碼",
    )

    private val autofillLabel2faTranslations = listOf(
        "totp",
        "otp",
        "2fa",
    )

    private val autofillLabelEmailTranslations = listOf(
        "email",
        "e-mail",
        "почта",
        "пошта",
        "мейл",
        "мэйл",
        "майл",
        "电子邮箱",
        "電子郵箱",
    )

    private val autofillLabelUsernameTranslations = listOf(
        "nickname",
        "username",
        "utilisateur",
        "login",
        "логин",
        "логін",
        "користувач",
        "пользовател",
        "用户名",
        "用戶名",
        "id",
        "customer",
    )

    private val autofillLabelCreditCardNumberTranslations = listOf(
        ".*(credit|debit|card)+.*number.*".toRegex(),
    )

    private val autofillHintMatchers = listOf(
        AutofillHintMatcher(
            hint = InternalHint.EMAIL_ADDRESS,
            target = HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS,
        ),
        AutofillHintMatcher(
            hint = InternalHint.EMAIL_ADDRESS,
            target = "email",
            partly = true,
        ),
        AutofillHintMatcher(
            hint = InternalHint.USERNAME,
            target = HintConstants.AUTOFILL_HINT_USERNAME,
        ),
        AutofillHintMatcher(
            hint = InternalHint.USERNAME,
            target = "nickname",
        ),
        AutofillHintMatcher(
            hint = InternalHint.PASSWORD,
            target = HintConstants.AUTOFILL_HINT_PASSWORD,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PASSWORD,
            target = "password",
            partly = true,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PHONE_NUMBER,
            target = HintConstants.AUTOFILL_HINT_PHONE,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PHONE_NUMBER,
            target = HintConstants.AUTOFILL_HINT_PHONE_NUMBER,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PHONE_NUMBER,
            target = "phone",
        ),
        AutofillHintMatcher(
            hint = InternalHint.NEW_PASSWORD,
            target = "new-password",
        ),
        AutofillHintMatcher(
            hint = InternalHint.USERNAME,
            target = "new-username",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_NUMBER,
            target = HintConstants.AUTOFILL_HINT_CREDIT_CARD_NUMBER,
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_NUMBER,
            target = "cc-number",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_NUMBER,
            target = "credit_card_number",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_SECURITY_CODE,
            target = HintConstants.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE,
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_SECURITY_CODE,
            target = "cc-csc",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_SECURITY_CODE,
            target = "credit_card_csv",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_EXPIRATION_DATE,
            target = HintConstants.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE,
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_EXPIRATION_DATE,
            target = "cc-exp",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_EXPIRATION_MONTH,
            target = "cc-exp-month",
        ),
        AutofillHintMatcher(
            hint = InternalHint.CREDIT_CARD_EXPIRATION_YEAR,
            target = "cc-exp-year",
        ),
        AutofillHintMatcher(
            hint = InternalHint.POSTAL_ADDRESS,
            target = HintConstants.AUTOFILL_HINT_POSTAL_ADDRESS,
        ),
        AutofillHintMatcher(
            hint = InternalHint.POSTAL_CODE,
            target = HintConstants.AUTOFILL_HINT_POSTAL_CODE,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PERSON_NAME,
            target = HintConstants.AUTOFILL_HINT_PERSON_NAME,
        ),
        AutofillHintMatcher(
            hint = InternalHint.PERSON_NAME,
            target = HintConstants.AUTOFILL_HINT_NAME,
        ),
        AutofillHintMatcher(
            hint = InternalHint.OTP_CODE,
            target = "one-time-code",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OTP_CODE,
            target = "sms-otp",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OTP_CODE,
            target = "email-otp",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OTP_CODE,
            target = "totp",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OTP_CODE,
            target = "2fa",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OFF,
            target = "chrome-off",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OFF,
            target = "off",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OFF,
            target = "no",
        ),
        AutofillHintMatcher(
            hint = InternalHint.OFF,
            target = "nope",
        ),
    )

    fun parse(structure: AssistStructure, respectAutofillOff: Boolean = true): ParsedStructure {
        var applicationId: String? = structure.activityComponent?.packageName
        var rawStructure: RawParsedStructure? = null
        val parseContext = ParseContext()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val appIdCandidate = windowNode.title?.toString()?.split("/")?.firstOrNull()
            if (!appIdCandidate.isNullOrBlank()) {
                if (appIdCandidate.contains(":")) {
                    continue
                }
                applicationId = appIdCandidate
            }

            val nodeStructure = parseViewNode(
                node = windowNode.rootViewNode,
                context = parseContext,
            )
            if (rawStructure == null) {
                rawStructure = nodeStructure
            }
            val hasItems = nodeStructure.items.any { it.hint != InternalHint.OFF }
            if (hasItems) {
                rawStructure = nodeStructure
                break
            }
        }

        val allowOnlyWebViewItems = rawStructure?.webView == true
        var candidateItems = rawStructure?.items.orEmpty()
        if (allowOnlyWebViewItems) {
            candidateItems = candidateItems.filter { it.parentWebViewNodeId != null }
        }

        val confidenceFilteredItems = candidateItems.let { list ->
            val onlyLowAccuracy = list.all {
                it.accuracy.score <= Accuracy.LOW.score || it.hint == InternalHint.OFF
            }
            if (!onlyLowAccuracy) {
                return@let list
            }

            val hasUsernameAndPassword =
                list.any {
                    val usernameLike =
                        it.hint == InternalHint.USERNAME ||
                            it.hint == InternalHint.EMAIL_ADDRESS ||
                            it.hint == InternalHint.PHONE_NUMBER
                    usernameLike && it.accuracy.score > Accuracy.LOWEST.score
                } &&
                    list.any {
                        val passwordLike =
                            it.hint == InternalHint.PASSWORD ||
                                it.hint == InternalHint.NEW_PASSWORD
                        passwordLike && it.accuracy.score > Accuracy.LOWEST.score
                    }
            if (hasUsernameAndPassword) {
                list
            } else {
                emptyList()
            }
        }

        val items = mutableListOf<ParsedItem>()
        confidenceFilteredItems
            .groupBy { it.id }
            .forEach { groupedById ->
                val forceAutofillOff = groupedById.value.any {
                    it.hint == InternalHint.OFF && it.accuracy == Accuracy.HIGHEST
                }
                var structureItems = if (forceAutofillOff) {
                    return@forEach
                } else if (respectAutofillOff) {
                    if (groupedById.value.any { it.hint == InternalHint.OFF }) {
                        return@forEach
                    }
                    groupedById.value
                } else {
                    groupedById.value.filter { it.hint != InternalHint.OFF }
                }

                val derivesOfPassword = structureItems.any {
                    it.hint == InternalHint.CREDIT_CARD_SECURITY_CODE || it.hint == InternalHint.OTP_CODE
                }
                if (derivesOfPassword) {
                    structureItems = structureItems.filter { it.hint != InternalHint.PASSWORD }
                }

                val derivesOfUsername = structureItems.any {
                    it.hint == InternalHint.CREDIT_CARD_NUMBER ||
                        it.hint == InternalHint.CREDIT_CARD_EXPIRATION_DATE ||
                        it.hint == InternalHint.CREDIT_CARD_EXPIRATION_MONTH ||
                        it.hint == InternalHint.CREDIT_CARD_EXPIRATION_YEAR ||
                        it.hint == InternalHint.CREDIT_CARD_EXPIRATION_DAY
                }
                if (derivesOfUsername) {
                    structureItems = structureItems.filter { it.hint != InternalHint.USERNAME }
                }

                val selectedItem = structureItems
                    .groupBy { it.hint }
                    .mapNotNull { groupedByHint ->
                        val score = groupedByHint.value.fold(0f) { acc, item ->
                            acc + item.accuracy.score
                        }
                        val best = groupedByHint.value
                            .maxByOrNull { it.accuracy.score }
                            ?: return@mapNotNull null
                        val value = groupedByHint.value
                            .sortedByDescending { it.accuracy.score }
                            .asSequence()
                            .mapNotNull { it.value }
                            .firstOrNull()
                        HintScore(
                            score = score,
                            hint = groupedByHint.key,
                            value = value,
                            accuracy = best.accuracy,
                            isFocused = best.isFocused,
                            isVisible = best.isVisible,
                            parentWebViewNodeId = best.parentWebViewNodeId,
                            traversalIndex = best.traversalIndex,
                        )
                    }
                    .maxByOrNull { it.score }
                    ?: return@forEach

                if (selectedItem.score <= Accuracy.LOWEST.score + 0.1f) {
                    val shouldSkip = rawStructure?.items.orEmpty().any {
                        it.hint == selectedItem.hint &&
                            it.accuracy.score > Accuracy.LOWEST.score
                    }
                    if (shouldSkip) {
                        return@forEach
                    }
                }

                val mappedHint = mapHint(selectedItem.hint) ?: return@forEach
                items += ParsedItem(
                    id = groupedById.key,
                    hint = mappedHint,
                    value = selectedItem.value,
                    accuracy = selectedItem.accuracy,
                    isFocused = selectedItem.isFocused,
                    isVisible = selectedItem.isVisible,
                    parentWebViewNodeId = selectedItem.parentWebViewNodeId,
                    traversalIndex = selectedItem.traversalIndex,
                )
            }

        val isInSelfHostedServer = kotlin.run {
            val webDomain = rawStructure?.webDomain
            val webView = rawStructure?.webView == true
            webView && (webDomain == "127.0.0.1" || webDomain == "localhost")
        }

        return ParsedStructure(
            applicationId = applicationId,
            webDomain = rawStructure?.webDomain.takeUnless { isInSelfHostedServer },
            webScheme = rawStructure?.webScheme.takeUnless { isInSelfHostedServer },
            webView = if (isInSelfHostedServer) false else rawStructure?.webView == true,
            items = items.sortedBy { it.traversalIndex },
        )
    }

    private fun parseViewNode(
        node: AssistStructure.ViewNode,
        parentWebViewNodeId: Int? = null,
        context: ParseContext,
    ): RawParsedStructure {
        var webView = false
        var webDomain: String? = node.webDomain?.takeIf { it.isNotEmpty() }
        var webScheme: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.webScheme?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val out = mutableListOf<RawParsedItem>()
        if (node.visibility == View.VISIBLE) {
            webView = node.className == "android.webkit.WebView"
            val webViewNodeId = node.id.takeIf { webView } ?: parentWebViewNodeId

            if (node.autofillId != null) {
                val outBuilders = mutableListOf<ParsedItemBuilder>()
                val hints = node.autofillHints
                if (!hints.isNullOrEmpty()) {
                    outBuilders += parseNodeByAutofillHint(node)
                }

                outBuilders += parseNodeByHtmlAttributes(node)
                val inputOut = parseNodeByAndroidInput(node)
                val labelOut = parseNodeByLabel(node)
                outBuilders += inputOut + labelOut

                out += outBuilders.map { builder ->
                    context.traversalIndex += 1
                    RawParsedItem(
                        id = node.autofillId!!,
                        accuracy = builder.accuracy,
                        hint = builder.hint,
                        value = builder.value ?: node.autofillValue.safeTextOrNull(
                            tag = "EnhancedParserV2",
                            fieldDescription = builder.hint.name,
                        ),
                        reason = builder.reason,
                        parentWebViewNodeId = webViewNodeId,
                        isFocused = node.isFocused,
                        isVisible = node.visibility == View.VISIBLE,
                        traversalIndex = context.traversalIndex,
                    )
                }
            }

            for (i in 0 until node.childCount) {
                val childStructure = parseViewNode(
                    node = node.getChildAt(i),
                    parentWebViewNodeId = webViewNodeId,
                    context = context,
                )
                if (childStructure.webView) {
                    webView = true
                }
                webDomain = webDomain ?: childStructure.webDomain
                webScheme = webScheme ?: childStructure.webScheme
                out += childStructure.items
            }
        }

        return RawParsedStructure(
            webScheme = webScheme,
            webDomain = webDomain,
            webView = webView,
            items = out,
        )
    }

    private fun parseNodeByAutofillHint(
        node: AssistStructure.ViewNode,
    ): List<ParsedItemBuilder> = kotlin.run {
        val out = mutableListOf<ParsedItemBuilder>()
        node.autofillHints?.forEach { value ->
            val matchers = autofillHintMatchers.filter { matcher -> matcher.matches(value) }
            matchers.forEach { matcher ->
                out += ParsedItemBuilder(
                    accuracy = matcher.accuracy,
                    hint = matcher.hint,
                    reason = "autofill-hint",
                )
            }
        }
        out
    }

    private fun parseNodeByHtmlAttributes(
        node: AssistStructure.ViewNode,
    ): List<ParsedItemBuilder> = kotlin.run {
        val out = mutableListOf<ParsedItemBuilder>()
        val nodeHtml = node.htmlInfo
        when (nodeHtml?.tag?.lowercase(Locale.ENGLISH)) {
            "input" -> {
                val attributes = kotlin.run {
                    nodeHtml.attributes
                        ?.map { it.first to it.second }
                        ?.takeUnless { it.isEmpty() } ?: kotlin.runCatching {
                        val values = nodeHtml.javaClass.getDeclaredField("mValues")
                            .apply { isAccessible = true }
                            .get(nodeHtml)
                        val names = nodeHtml.javaClass.getDeclaredField("mNames")
                            .apply { isAccessible = true }
                            .get(nodeHtml)
                        if (values is Array<*> && names is Array<*>) {
                            values.mapIndexed { i, v -> v.toString() to names[i].toString() }
                        } else if (values is Collection<*> && names is Collection<*>) {
                            val namesList = names.toList()
                            values.mapIndexed { i, v -> v.toString() to namesList[i].toString() }
                        } else {
                            null
                        }
                    }.getOrNull()
                }

                attributes?.forEach { attribute ->
                    val key = attribute.first.lowercase(Locale.ENGLISH)
                    when (key) {
                        "autocomplete",
                        "ua-autofill-hints",
                        -> {
                            val value = attribute.second?.lowercase(Locale.ENGLISH).orEmpty()
                            val matchers = autofillHintMatchers.filter { matcher ->
                                matcher.matches(value)
                            }
                            matchers.forEach { matcher ->
                                out += ParsedItemBuilder(
                                    accuracy = matcher.accuracy,
                                    hint = matcher.hint,
                                    reason = key,
                                )
                            }
                        }

                        "type" -> {
                            val type = attribute.second.orEmpty()
                            extractOfType(type).let(out::addAll)
                        }

                        "name" -> {
                            val type = attribute.second.orEmpty()
                            extractOfId(type).let(out::addAll)
                        }

                        "id" -> {
                            val type = attribute.second.orEmpty()
                            extractOfId(type).let(out::addAll)
                        }

                        "label" -> {
                            val label = attribute.second.orEmpty()
                            extractOfLabel(label).let(out::addAll)
                        }
                    }
                }
            }
        }
        out
    }

    private fun parseNodeByLabel(
        node: AssistStructure.ViewNode,
    ): List<ParsedItemBuilder> {
        val hint = node.hint ?: return emptyList()
        return extractOfLabel(hint)
    }

    private fun extractOfType(
        value: String,
    ): List<ParsedItemBuilder> = when (value.lowercase(Locale.ENGLISH)) {
        "tel" -> ParsedItemBuilder(
            accuracy = Accuracy.MEDIUM,
            hint = InternalHint.PHONE_NUMBER,
            reason = "type",
        )

        "email" -> ParsedItemBuilder(
            accuracy = Accuracy.MEDIUM,
            hint = InternalHint.EMAIL_ADDRESS,
            reason = "type",
        )

        "username" -> ParsedItemBuilder(
            accuracy = Accuracy.MEDIUM,
            hint = InternalHint.USERNAME,
            reason = "type",
        )

        "text" -> ParsedItemBuilder(
            accuracy = Accuracy.LOWEST,
            hint = InternalHint.USERNAME,
            reason = "type",
        )

        "password" -> ParsedItemBuilder(
            accuracy = Accuracy.HIGH,
            hint = InternalHint.PASSWORD,
            reason = "type",
        )

        "totp",
        "twofa",
        "2fa",
        -> ParsedItemBuilder(
            accuracy = Accuracy.HIGH,
            hint = InternalHint.OTP_CODE,
            reason = "type",
        )

        "expdate" -> ParsedItemBuilder(
            accuracy = Accuracy.HIGH,
            hint = InternalHint.CREDIT_CARD_EXPIRATION_DATE,
            reason = "type",
        )

        else -> null
    }.let { listOfNotNull(it) }

    private fun extractOfId(
        value: String,
    ): List<ParsedItemBuilder> = kotlin.run {
        val id = value.lowercase(Locale.ENGLISH)
        when {
            "email" in id -> ParsedItemBuilder(
                accuracy = Accuracy.MEDIUM,
                hint = InternalHint.EMAIL_ADDRESS,
                reason = "id",
            )

            "username" in id -> ParsedItemBuilder(
                accuracy = Accuracy.MEDIUM,
                hint = InternalHint.USERNAME,
                reason = "id",
            )

            "password" in id -> ParsedItemBuilder(
                accuracy = Accuracy.HIGH,
                hint = InternalHint.PASSWORD,
                reason = "id",
            )

            "totp" in id || "twofa" in id || "2fa" in id -> ParsedItemBuilder(
                accuracy = Accuracy.HIGH,
                hint = InternalHint.OTP_CODE,
                reason = "id",
            )

            else -> null
        }.let { listOfNotNull(it) }
    }

    private fun extractOfLabel(
        value: String,
    ): List<ParsedItemBuilder> {
        val hint = value.lowercase(Locale.ENGLISH).trim()
        if (hint.isBlank()) {
            return emptyList()
        }

        val out = when {
            autofillLabelEmailTranslations.any { it in hint } ->
                ParsedItemBuilder(
                    accuracy = Accuracy.MEDIUM,
                    hint = InternalHint.EMAIL_ADDRESS,
                    reason = "label:$hint",
                )

            autofillLabelUsernameTranslations.any { it in hint } ->
                ParsedItemBuilder(
                    accuracy = Accuracy.MEDIUM,
                    hint = InternalHint.USERNAME,
                    reason = "label:$hint",
                )

            autofillLabelPasswordTranslations.any { it in hint } ->
                ParsedItemBuilder(
                    accuracy = Accuracy.MEDIUM,
                    hint = InternalHint.PASSWORD,
                    reason = "label:$hint",
                )

            autofillLabel2faTranslations.any { it in hint } ->
                ParsedItemBuilder(
                    accuracy = Accuracy.MEDIUM,
                    hint = InternalHint.OTP_CODE,
                    reason = "label:$hint",
                )

            autofillLabelCreditCardNumberTranslations.any { it.matches(hint) } ->
                ParsedItemBuilder(
                    accuracy = Accuracy.MEDIUM,
                    hint = InternalHint.CREDIT_CARD_NUMBER,
                    reason = "label:$hint",
                )

            else -> null
        }
        return listOfNotNull(out)
    }

    private fun parseNodeByAndroidInput(
        node: AssistStructure.ViewNode,
    ): List<ParsedItemBuilder> {
        val out = mutableListOf<ParsedItemBuilder>()

        if (node.idType.orEmpty().equals("id", ignoreCase = true)) {
            val idEntry = node.idEntry.orEmpty()
            if (
                idEntry.contains("url", ignoreCase = true) ||
                idEntry.contentEquals(other = "location_bar_edit_text", ignoreCase = true)
            ) {
                out += ParsedItemBuilder(
                    accuracy = Accuracy.HIGHEST,
                    hint = InternalHint.OFF,
                )
                return out
            }
        }

        val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.importantForAutofill
        } else {
            View.IMPORTANT_FOR_AUTOFILL_AUTO
        }
        if (importance == View.IMPORTANT_FOR_AUTOFILL_NO) {
            out += ParsedItemBuilder(
                accuracy = Accuracy.HIGHEST,
                hint = InternalHint.OFF,
            )
            return out
        }

        val inputType = node.inputType
        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> {
                when {
                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = Accuracy.HIGH,
                            hint = InternalHint.EMAIL_ADDRESS,
                        )
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = Accuracy.LOW,
                            hint = InternalHint.PERSON_NAME,
                        )
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_NORMAL,
                        InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                        InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = Accuracy.LOWEST,
                            hint = InternalHint.USERNAME,
                        )
                        extractOfType(node.idType.orEmpty()).let(out::addAll)
                        extractOfId(node.idEntry.orEmpty()).let(out::addAll)
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    ) -> {
                        val hasUsername = out.any {
                            it.hint == InternalHint.USERNAME ||
                                it.hint == InternalHint.EMAIL_ADDRESS ||
                                it.hint == InternalHint.PHONE_NUMBER
                        }
                        if (hasUsername) {
                            out += ParsedItemBuilder(
                                accuracy = Accuracy.LOWEST,
                                hint = InternalHint.PASSWORD,
                            )
                        } else {
                            out += ParsedItemBuilder(
                                accuracy = Accuracy.LOWEST,
                                hint = InternalHint.USERNAME,
                            )
                        }
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = Accuracy.HIGH,
                            hint = InternalHint.PASSWORD,
                        )
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
                        InputType.TYPE_TEXT_VARIATION_FILTER,
                        InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
                        InputType.TYPE_TEXT_VARIATION_PHONETIC,
                        InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
                        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                        InputType.TYPE_TEXT_VARIATION_URI,
                    ) -> {
                    }

                    else -> {
                    }
                }
            }

            InputType.TYPE_CLASS_NUMBER -> {
                when {
                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_NUMBER_VARIATION_NORMAL,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = if (importance == View.IMPORTANT_FOR_AUTOFILL_YES) {
                                Accuracy.MEDIUM
                            } else {
                                Accuracy.LOW
                            },
                            hint = InternalHint.USERNAME,
                        )
                    }

                    inputIsVariationType(
                        inputType,
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                    ) -> {
                        out += ParsedItemBuilder(
                            accuracy = Accuracy.LOW,
                            hint = InternalHint.PASSWORD,
                        )
                    }

                    else -> {
                    }
                }
            }
        }

        return out
    }

    private fun isEditableTextLikeNode(node: AssistStructure.ViewNode): Boolean {
        if (node.visibility != View.VISIBLE) return false
        if (node.autofillId == null) return false

        if (node.autofillType == View.AUTOFILL_TYPE_TEXT) return true
        if (node.inputType != 0) return true

        val className = node.className?.lowercase(Locale.ENGLISH).orEmpty()
        if (
            className.contains("edittext") ||
            className.contains("textinput") ||
            className.contains("textfield") ||
            className.contains("autocompletetextview")
        ) {
            return true
        }

        val htmlTag = node.htmlInfo?.tag?.lowercase(Locale.ENGLISH).orEmpty()
        if (htmlTag == "input" || htmlTag == "textarea") return true

        val htmlType = node.htmlInfo?.attributes
            ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
            ?.second
            ?.lowercase(Locale.ENGLISH)
            .orEmpty()
        return htmlType == "text" ||
            htmlType == "email" ||
            htmlType == "tel" ||
            htmlType == "password"
    }

    private fun mapHint(hint: InternalHint): FieldHint? = when (hint) {
        InternalHint.USERNAME -> FieldHint.USERNAME
        InternalHint.PASSWORD -> FieldHint.PASSWORD
        InternalHint.NEW_PASSWORD -> FieldHint.NEW_PASSWORD
        InternalHint.EMAIL_ADDRESS -> FieldHint.EMAIL_ADDRESS
        InternalHint.PHONE_NUMBER -> FieldHint.PHONE_NUMBER
        InternalHint.CREDIT_CARD_NUMBER -> FieldHint.CREDIT_CARD_NUMBER
        InternalHint.CREDIT_CARD_EXPIRATION_DATE,
        InternalHint.CREDIT_CARD_EXPIRATION_MONTH,
        InternalHint.CREDIT_CARD_EXPIRATION_YEAR,
        InternalHint.CREDIT_CARD_EXPIRATION_DAY,
        -> FieldHint.CREDIT_CARD_EXPIRATION_DATE
        InternalHint.CREDIT_CARD_SECURITY_CODE -> FieldHint.CREDIT_CARD_SECURITY_CODE
        InternalHint.CREDIT_CARD_HOLDER_NAME -> FieldHint.CREDIT_CARD_HOLDER_NAME
        InternalHint.POSTAL_ADDRESS -> FieldHint.POSTAL_ADDRESS
        InternalHint.POSTAL_CODE -> FieldHint.POSTAL_CODE
        InternalHint.PERSON_NAME -> FieldHint.PERSON_NAME
        InternalHint.OTP_CODE -> FieldHint.OTP_CODE
        InternalHint.OFF -> null
        InternalHint.UNKNOWN -> FieldHint.UNKNOWN
    }

    private fun inputIsVariationType(inputType: Int, vararg type: Int): Boolean {
        type.forEach {
            if (inputType and InputType.TYPE_MASK_VARIATION == it) {
                return true
            }
        }
        return false
    }
}
