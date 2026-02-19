package takagi.ru.monica.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry

/**
 * AutofillPicker启动器
 * 
 * 负责创建启动AutofillPickerActivity的PendingIntent和FillResponse
 */
object AutofillPickerLauncher {
    
    /**
     * 创建直接列表响应 (单一入口模式)
     * 
     * 始终只显示一个"解锁/搜索"入口，点击后跳转到全屏选择器
     * 满足用户"始终是点进去进入一个页面然后填充"的需求
     */
    fun createDirectListResponse(
        context: Context,
        matchedPasswords: List<PasswordEntry>,
        allPasswordIds: List<Long>,
        packageName: String?,
        domain: String?,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure,
        credentialTargetsOverride: List<EnhancedAutofillStructureParserV2.ParsedItem>? = null,
        fieldSignatureKey: String? = null
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        val rawFillTargets = credentialTargetsOverride
            ?.filter { item ->
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                    item.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS ||
                    item.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD ||
                    item.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD
            }
            ?.ifEmpty { null }
            ?: selectCredentialFillTargets(parsedStructure)
        val fillTargets = normalizeCredentialTargetsById(rawFillTargets)
        val authTargets = selectAuthenticationTargets(fillTargets)

        if (fillTargets.isEmpty() || authTargets.isEmpty()) {
            android.util.Log.w("AutofillPicker", "No credential targets available, skip building response")
            return responseBuilder.build()
        }

        android.util.Log.d("AutofillPicker", "Creating single entry point response (Unlock/Search style)")
        
        // 1. 构建跳转 Intent - 始终跳转到全屏选择器
        val args = AutofillPickerActivityV2.Args(
            applicationId = packageName,
            webDomain = domain,
            autofillIds = ArrayList(fillTargets.map { it.id }),
            autofillHints = ArrayList(fillTargets.map { it.hint.name }),
            suggestedPasswordIds = matchedPasswords.map { it.id }.toLongArray(),
            isSaveMode = false,
            // 如果只有用户名/密码字段，传过去以便预填
            capturedUsername = parsedStructure.items.find { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME || 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS 
            }?.value,
            capturedPassword = parsedStructure.items.find { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD 
            }?.value,
            fieldSignatureKey = fieldSignatureKey ?: buildFieldSignatureKey(packageName, domain, fillTargets)
        )
        
        val pickerIntent = AutofillPickerActivityV2.getIntent(context, args)
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getActivity(context, requestCode, pickerIntent, flags)
        
        // 2. 创建手动触发入口 Presentation（始终保留）
        // v2 手动入口：使用本地化文案，避免中文系统显示英文硬编码
        val presentation = RemoteViews(context.packageName, R.layout.autofill_manual_card_v2).apply {
            setTextViewText(R.id.text_title, context.getString(R.string.tile_autofill_label))
            setTextViewText(R.id.text_username, context.getString(R.string.autofill_open_picker))
            setImageViewResource(R.id.icon_app, R.drawable.ic_key) 
        }
        
        // 3. 字段级入口：对每个认证目标字段挂一个触发项，避免单入口在部分 OEM 上二次不弹
        authTargets.forEach { item ->
            val datasetBuilder = Dataset.Builder(presentation)
            datasetBuilder.setValue(item.id, null, presentation)
            datasetBuilder.setAuthentication(pendingIntent.intentSender)
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        // 4. Keyguard-aligned behavior:
        // do not attach SaveInfo on the authentication-entry response path.
        // Keeping this response "selection-only" avoids sticky sessions
        // that can block second-pass autofill requests on some OEM builds.

        return responseBuilder.build()
    }
    
    /**
     * 添加最小化的 SaveInfo
     * 
     * 配置最简洁的 SaveInfo:
     * - 无 description(移除提示文字)
     * - 使用设备特定的 flags
     * - 目标:让系统对话框尽快消失
     */
    private fun addMinimalSaveInfo(
        responseBuilder: FillResponse.Builder,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ) {
        // 使用 SaveInfoBuilder 构建设备适配的 SaveInfo
        val saveInfo = takagi.ru.monica.autofill.core.SaveInfoBuilder.build(parsedStructure)
        
        if (saveInfo != null) {
            responseBuilder.setSaveInfo(saveInfo)
            android.util.Log.d("AutofillPicker", "✅ SaveInfo configured using SaveInfoBuilder with device-specific flags")
        } else {
            android.util.Log.w("AutofillPicker", "⚠️ SaveInfo not configured - no saveable fields found")
        }
    }
    
    /**
     * 配置SaveInfo
     * 
     * 根据字段类型智能配置SaveInfo:
     * - 区分普通登录和注册/修改密码场景
     * - 设置必需字段和可选字段
     * - 配置合适的flags确保提示显示
     */
    private fun addSaveInfo(
        responseBuilder: FillResponse.Builder,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure,
        context: Context
    ) {
        android.util.Log.w("AutofillPicker", "╔════════════════════════════════════════╗")
        android.util.Log.w("AutofillPicker", "║   addSaveInfo() CALLED                ║")
        android.util.Log.w("AutofillPicker", "╚════════════════════════════════════════╝")
        android.util.Log.d("AutofillPicker", "Parsed structure items: ${parsedStructure.items.size}")
        
        // 分类字段
        val usernameFields = mutableListOf<android.view.autofill.AutofillId>()
        val passwordFields = mutableListOf<android.view.autofill.AutofillId>()
        val newPasswordFields = mutableListOf<android.view.autofill.AutofillId>()
        
        parsedStructure.items.forEach { item ->
            android.util.Log.d("AutofillPicker", "  Field hint: ${item.hint}, id: ${item.id}")
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                    usernameFields.add(item.id)
                }
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD -> {
                    passwordFields.add(item.id)
                }
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    newPasswordFields.add(item.id)
                }
                else -> {}
            }
        }
        
        android.util.Log.d("AutofillPicker", "Field classification complete:")
        android.util.Log.d("AutofillPicker", "  Username fields: ${usernameFields.size}")
        android.util.Log.d("AutofillPicker", "  Password fields: ${passwordFields.size}")
        android.util.Log.d("AutofillPicker", "  New password fields: ${newPasswordFields.size}")
        
        // 判断场景类型
        val isNewPasswordScenario = newPasswordFields.isNotEmpty()
        
        android.util.Log.d("AutofillPicker", "Scenario determination:")
        android.util.Log.d("AutofillPicker", "  Is new password scenario: $isNewPasswordScenario")
        android.util.Log.d("AutofillPicker", "  Will configure SaveInfo: ${isNewPasswordScenario || passwordFields.isNotEmpty()}")
        
        if (isNewPasswordScenario) {
            android.util.Log.d("AutofillPicker", "→ Configuring NEW_PASSWORD SaveInfo")
            // 注册/修改密码场景
            configureSaveInfoForNewPassword(
                responseBuilder,
                usernameFields,
                newPasswordFields
            )
        } else if (passwordFields.isNotEmpty()) {
            android.util.Log.d("AutofillPicker", "→ Configuring LOGIN SaveInfo")
            // 普通登录场景
            configureSaveInfoForLogin(
                responseBuilder,
                usernameFields,
                passwordFields
            )
        } else {
            android.util.Log.w("AutofillPicker", "⚠️ No password fields found - SaveInfo NOT configured!")
        }
        
        android.util.Log.d(
            "AutofillPicker",
            "💾 SaveInfo configured: scenario=${if (isNewPasswordScenario) "NEW_PASSWORD" else "LOGIN"}, " +
            "username=${usernameFields.size}, password=${passwordFields.size}, newPassword=${newPasswordFields.size}"
        )
        android.util.Log.w("AutofillPicker", "╚════════════════════════════════════════╝")
    }
    
    /**
     * 配置普通登录场景的SaveInfo
     * 
     * ⚠️ 关键策略变更:
     * 既然移除 description 无法阻止系统对话框,我们就**利用系统对话框**!
     * - 保留系统对话框作为"触发器"
     * - 用户点击"Save"时,触发 onSaveRequest
     * - onSaveRequest 启动自定义 Bottom Sheet
     * 
     * 这样做的好处:
     * 1. 系统对话框快速消失(只是触发器)
     * 2. 立即显示我们的 Material 3 Bottom Sheet
     * 3. 用户看到的主要是我们的自定义UI
     */
    private fun configureSaveInfoForLogin(
        responseBuilder: FillResponse.Builder,
        usernameFields: List<android.view.autofill.AutofillId>,
        passwordFields: List<android.view.autofill.AutofillId>
    ) {
        if (passwordFields.isEmpty()) return
        
        val saveInfoBuilder = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            passwordFields.toTypedArray() // 密码字段是必需的
        )
        
        // 用户名字段是可选的(有些登录只需要密码)
        if (usernameFields.isNotEmpty()) {
            saveInfoBuilder.setOptionalIds(usernameFields.toTypedArray())
        }
        
        // 🔧 关键修复: 不设置 description!
        // 如果设置了 description,系统会显示自己的保存对话框
        // 用户点击后系统认为已完成,不会调用 onSaveRequest
        // 不设置 description → 系统直接调用 onSaveRequest → 显示我们的 BottomSheet
        // saveInfoBuilder.setDescription("保存到 Monica 密码管理器") // ❌ 移除
        
        // 使用标准 flags
        saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        
        responseBuilder.setSaveInfo(saveInfoBuilder.build())
        
        android.util.Log.d(
            "AutofillPicker",
            "💾 Login SaveInfo added (HYBRID MODE - system dialog + custom bottom sheet): " +
            "requiredFields=${passwordFields.size}, optionalFields=${usernameFields.size}"
        )
    }
    
    /**
     * 配置注册/修改密码场景的SaveInfo
     * 
     * ✨ 使用自定义UI替代系统默认保存提示:
     * - SaveInfo 触发 onSaveRequest 回调
     * - 移除 description 阻止系统默认UI
     * - 在 onSaveRequest 中启动自定义 Bottom Sheet
     */
    private fun configureSaveInfoForNewPassword(
        responseBuilder: FillResponse.Builder,
        usernameFields: List<android.view.autofill.AutofillId>,
        newPasswordFields: List<android.view.autofill.AutofillId>
    ) {
        if (newPasswordFields.isEmpty()) return
        
        // 对于新密码场景,使用不同的保存类型
        val saveInfoBuilder = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            newPasswordFields.take(1).toTypedArray() // 第一个新密码字段是必需的
        )
        
        // 如果有确认密码字段,添加为可选(用于验证)
        val optionalFields = mutableListOf<android.view.autofill.AutofillId>()
        if (newPasswordFields.size > 1) {
            optionalFields.addAll(newPasswordFields.drop(1))
        }
        // 用户名字段也是可选的
        optionalFields.addAll(usernameFields)
        
        if (optionalFields.isNotEmpty()) {
            saveInfoBuilder.setOptionalIds(optionalFields.toTypedArray())
        }
        
        // ⚠️ 关键: 不设置 description!
        // 移除 description 阻止系统显示默认保存对话框
        // saveInfoBuilder.setDescription("保存新密码到 Monica") // ← 故意注释掉
        
        // ✨ 只使用 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
        // 新密码场景也使用自定义 Bottom Sheet
        saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        
        responseBuilder.setSaveInfo(saveInfoBuilder.build())
        
        android.util.Log.d(
            "AutofillPicker",
            "💾 NewPassword SaveInfo added (CUSTOM UI MODE - no system dialog): " +
            "requiredFields=${newPasswordFields.take(1).size}, " +
            "optionalFields=${newPasswordFields.size - 1 + usernameFields.size}"
        )
    }
    
    /**
     * 🎯 配置完全自定义的 SaveInfo
     * 
     * 使用 NegativeAction 拦截系统对话框,直接启动自定义 Bottom Sheet
     */
    private fun addCustomSaveInfo(
        responseBuilder: FillResponse.Builder,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure,
        context: Context,
        packageName: String?,
        domain: String?
    ) {
        android.util.Log.w("AutofillPicker", "╔════════════════════════════════════════╗")
        android.util.Log.w("AutofillPicker", "║   addCustomSaveInfo() CALLED          ║")
        android.util.Log.w("AutofillPicker", "╚════════════════════════════════════════╝")
        
        // 分类字段
        val usernameFields = mutableListOf<android.view.autofill.AutofillId>()
        val passwordFields = mutableListOf<android.view.autofill.AutofillId>()
        val newPasswordFields = mutableListOf<android.view.autofill.AutofillId>()
        
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                    usernameFields.add(item.id)
                }
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD -> {
                    passwordFields.add(item.id)
                }
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    newPasswordFields.add(item.id)
                }
                else -> {}
            }
        }
        
        val isNewPasswordScenario = newPasswordFields.isNotEmpty()
        
        if (passwordFields.isEmpty() && newPasswordFields.isEmpty()) {
            android.util.Log.w("AutofillPicker", "⚠️ No password fields - SaveInfo NOT configured")
            return
        }
        
        // 构建 SaveInfo - 但使用自定义的 PendingIntent
        val requiredFields = if (isNewPasswordScenario) {
            newPasswordFields.take(1).toTypedArray()
        } else {
            passwordFields.toTypedArray()
        }
        
        val saveInfoBuilder = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            requiredFields
        )
        
        // 添加可选字段
        val optionalFields = mutableListOf<android.view.autofill.AutofillId>()
        if (isNewPasswordScenario && newPasswordFields.size > 1) {
            optionalFields.addAll(newPasswordFields.drop(1))
        }
        optionalFields.addAll(usernameFields)
        
        if (optionalFields.isNotEmpty()) {
            saveInfoBuilder.setOptionalIds(optionalFields.toTypedArray())
        }
        
        // ⚠️ 不设置 description - 这会阻止大部分系统UI显示
        // saveInfoBuilder.setDescription("...")
        
        saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        
        responseBuilder.setSaveInfo(saveInfoBuilder.build())
        
        android.util.Log.d("AutofillPicker", "✅ Custom SaveInfo configured (no description = minimal system UI)")
    }
    
    /**
     * 旧的SaveInfo配置(已废弃,保留用于参考)
     */
    @Deprecated("使用新的 addSaveInfo 方法")
    private fun addSaveInfoLegacy(
        responseBuilder: FillResponse.Builder,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ) {
        val saveFieldIds = mutableListOf<android.view.autofill.AutofillId>()
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS,
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    saveFieldIds.add(item.id)
                }
                else -> {}
            }
        }
        
        if (saveFieldIds.isNotEmpty()) {
            val saveInfoBuilder = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                saveFieldIds.toTypedArray()
            )
            saveInfoBuilder.setDescription("保存到 Monica 密码管理器")
            // 添加标志以确保在所有情况下都提示保存
            saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            responseBuilder.setSaveInfo(saveInfoBuilder.build())
            android.util.Log.d("AutofillPicker", "💾 SaveInfo configured for ${saveFieldIds.size} fields with FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE")
        }
    }
    
    /**
     * 创建带有AutofillPickerActivity的FillResponse
     * 
     * @param context Context
     * @param passwords 密码列表
     * @param packageName 应用包名
     * @param domain 网站域名
     * @param parsedStructure 解析的结构
     * @return FillResponse
     */
    fun createPickerResponse(
        context: Context,
        passwords: List<PasswordEntry>,
        packageName: String?,
        domain: String?,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        val fillTargets = selectCredentialFillTargets(parsedStructure)
        val authTargets = selectAuthenticationTargets(fillTargets)

        if (fillTargets.isEmpty() || authTargets.isEmpty()) {
            android.util.Log.w("AutofillPicker", "No credential targets for picker response")
            return responseBuilder.build()
        }
        
        // 创建启动AutofillPickerActivity的Intent
        val pickerIntent = Intent(context, AutofillPickerActivity::class.java).apply {
            // 只传递密码ID列表,避免跨进程序列化问题
            putExtra(
                AutofillPickerActivity.EXTRA_PASSWORD_IDS,
                passwords.map { it.id }.toLongArray()
            )
            putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AutofillPickerActivity.EXTRA_DOMAIN, domain)
            
            // 传递字段ID列表,用于构建FillResponse
            val autofillIds = ArrayList(fillTargets.map { it.id })
            putParcelableArrayListExtra("autofill_ids", autofillIds)
            putStringArrayListExtra(
                AutofillPickerActivity.EXTRA_AUTOFILL_HINTS,
                ArrayList(fillTargets.map { it.hint.name })
            )
            
            // 根据字段类型判断
            val fieldType = if (isPaymentForm(parsedStructure)) {
                "payment"
            } else {
                "password"
            }
            putExtra(AutofillPickerActivity.EXTRA_FIELD_TYPE, fieldType)
        }
        
        // 创建PendingIntent
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt() and 0x7FFFFFFF,
            pickerIntent,
            flags
        )
        
        // 创建一个占位Dataset,用于触发Activity
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, "选择密码 (${passwords.size})")
            setTextViewText(R.id.text_username, "点击查看所有密码")
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        authTargets.forEach { item ->
            val datasetBuilder = Dataset.Builder(presentation)
            datasetBuilder.setValue(item.id, null, presentation)
            datasetBuilder.setAuthentication(pendingIntent.intentSender)
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        // 添加 SaveInfo
        addSaveInfo(responseBuilder, parsedStructure, context)
        
        return responseBuilder.build()
    }
    
    /**
     * 检测是否为支付表单
     */
    private fun isPaymentForm(parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure): Boolean {
        return parsedStructure.items.any { item ->
            item.hint in listOf(
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE
            )
        }
    }

    private fun selectCredentialFillTargets(
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): List<EnhancedAutofillStructureParserV2.ParsedItem> {
        return parsedStructure.items.filter { item ->
            item.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS ||
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD ||
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD
        }
    }

    private fun normalizeCredentialTargetsById(
        targets: List<EnhancedAutofillStructureParserV2.ParsedItem>
    ): List<EnhancedAutofillStructureParserV2.ParsedItem> {
        if (targets.isEmpty()) return emptyList()
        val prioritized = targets.sortedWith(
            compareByDescending<EnhancedAutofillStructureParserV2.ParsedItem> { it.isFocused }
                .thenByDescending { credentialHintPriority(it.hint) }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        )
        val deduped = LinkedHashMap<AutofillId, EnhancedAutofillStructureParserV2.ParsedItem>()
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

    private fun credentialHintPriority(hint: EnhancedAutofillStructureParserV2.FieldHint): Int {
        return when (hint) {
            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
            EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> 3
            EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
            EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> 2
            else -> 1
        }
    }

    private fun selectAuthenticationTargets(
        fillTargets: List<EnhancedAutofillStructureParserV2.ParsedItem>
    ): List<EnhancedAutofillStructureParserV2.ParsedItem> {
        if (fillTargets.isEmpty()) return emptyList()
        val priority = compareByDescending<EnhancedAutofillStructureParserV2.ParsedItem> { it.isFocused }
            .thenByDescending {
                when (it.hint) {
                    EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                    EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> 3
                    EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                    EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> 2
                    else -> 1
                }
            }
            .thenBy { it.traversalIndex }

        // Keyguard 风格：对所有凭据字段挂认证入口，而不是只取账号+密码二元组合。
        return fillTargets
            .sortedWith(priority)
            .distinctBy { it.id }
    }

    private fun buildFieldSignatureKey(
        packageName: String?,
        domain: String?,
        fillTargets: List<EnhancedAutofillStructureParserV2.ParsedItem>
    ): String {
        val normalizedPackage = packageName?.trim()?.lowercase().orEmpty()
        val normalizedDomain = domain?.trim()?.lowercase().orEmpty()
        val fingerprint = fillTargets
            .sortedWith(
                compareBy<EnhancedAutofillStructureParserV2.ParsedItem>(
                    { it.traversalIndex },
                    { it.hint.name }
                )
            )
            .joinToString(separator = "|") { item ->
                "${item.hint.name}:${item.traversalIndex}"
            }
        return "pkg=$normalizedPackage;dom=$normalizedDomain;sig=$fingerprint"
    }
    
    /**
     * 创建简化的FillResponse(用于快速填充)
     * 
     * 当只有一个密码匹配时,可以直接填充而不显示选择界面
     */
    fun createDirectFillResponse(
        context: Context,
        password: PasswordEntry,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 初始化 SecurityManager 用于解密密码
        val securityManager = takagi.ru.monica.security.SecurityManager(context)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(password, securityManager)
        val fillEmailWithAccount = AccountFillPolicy.shouldFillEmailWithAccount(context)
        
        // 创建RemoteViews
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, password.title.ifEmpty { accountValue })
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        // 创建Dataset
        val datasetBuilder = Dataset.Builder(presentation)
        
        // 填充字段
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME -> {
                    datasetBuilder.setValue(
                        item.id,
                        android.view.autofill.AutofillValue.forText(accountValue)
                    )
                }
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                    if (fillEmailWithAccount || accountValue.contains("@")) {
                        datasetBuilder.setValue(
                            item.id,
                            android.view.autofill.AutofillValue.forText(accountValue)
                        )
                    }
                }
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    // 解密密码
                    val decryptedPassword = securityManager.decryptData(password.password)
                    datasetBuilder.setValue(
                        item.id,
                        android.view.autofill.AutofillValue.forText(decryptedPassword)
                    )
                }
                else -> {
                    // 其他字段类型暂不处理
                }
            }
        }
        
        responseBuilder.addDataset(datasetBuilder.build())
        
        // 添加 SaveInfo
        addSaveInfo(responseBuilder, parsedStructure, context)
        
        return responseBuilder.build()
    }
    
    /**
     * 生成强密码
     * 默认生成16位包含大小写字母、数字和符号的强密码
     * 
     * @return 生成的强密码
     */
    private fun generateStrongPassword(): String {
        val options = takagi.ru.monica.utils.PasswordGenerator.PasswordOptions(
            length = 16,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
            excludeSimilar = true
        )
        
        val generator = takagi.ru.monica.utils.PasswordGenerator()
        return generator.generatePassword(options)
    }
}

