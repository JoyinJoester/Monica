package takagi.ru.monica.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
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
     * 创建直接列表响应
     * 
     * 显示所有匹配的密码作为独立的Dataset,并添加"手动选择"选项
     * 
     * @param context Context
     * @param matchedPasswords 匹配的密码列表
     * @param allPasswordIds 所有密码ID(用于手动选择)
     * @param packageName 应用包名
     * @param domain 网站域名
     * @param parsedStructure 解析的结构
     * @return FillResponse
     */
    fun createDirectListResponse(
        context: Context,
        matchedPasswords: List<PasswordEntry>,
        allPasswordIds: List<Long>,
        packageName: String?,
        domain: String?,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 检查是否启用了填充前验证
        val autofillPreferences = AutofillPreferences(context)
        var biometricQuickFillEnabled = false
        
        // 同步读取 preference 值
        kotlinx.coroutines.runBlocking {
            biometricQuickFillEnabled = autofillPreferences.isBiometricQuickFillEnabled.first()
        }
        
        android.util.Log.d("AutofillPicker", "Biometric quick fill enabled: $biometricQuickFillEnabled")
        
        // 1. 为每个匹配的密码创建独立的Dataset - 只显示前3个最匹配的
        val maxDirectShow = 3 // 最多直接显示3个密码
        android.util.Log.d("AutofillPicker", "Creating direct list: showing ${minOf(matchedPasswords.size, maxDirectShow)} of ${matchedPasswords.size} passwords")
        android.util.Log.d("AutofillPicker", "Parsed structure has ${parsedStructure.items.size} fields")
        
        // 初始化 SecurityManager 用于解密密码
        val securityManager = takagi.ru.monica.security.SecurityManager(context)
        
        matchedPasswords.take(maxDirectShow).forEachIndexed { index, password -> // 限制显示前3个
            android.util.Log.d("AutofillPicker", "Creating dataset for: ${password.title}")
            
            // 智能显示标题和用户名
            val displayTitle = when {
                password.title.isNotEmpty() -> password.title
                password.username.isNotEmpty() -> password.username
                else -> "密码 ${index + 1}"
            }
            
            val displaySubtitle = when {
                password.title.isNotEmpty() && password.username.isNotEmpty() -> password.username
                password.website.isNotEmpty() -> password.website
                password.appName.isNotEmpty() -> password.appName
                else -> "点击填充"
            }
            
            // 创建卡片样式的 presentation
            val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
                setTextViewText(R.id.text_title, displayTitle)
                setTextViewText(R.id.text_username, displaySubtitle)
                setImageViewResource(R.id.icon_app, R.drawable.ic_key)
            }
            
            // 创建 Dataset.Builder
            val datasetBuilder = Dataset.Builder(presentation)
            var fieldCount = 0
            
            // 如果启用了身份验证,则为 dataset 添加验证
            if (biometricQuickFillEnabled) {
                // 创建验证 Intent
                val authIntent = Intent(context, AutofillAuthenticationActivity::class.java).apply {
                    putExtra(AutofillAuthenticationActivity.EXTRA_PASSWORD_ENTRY_ID, password.id)
                    putExtra(AutofillAuthenticationActivity.EXTRA_USERNAME_VALUE, 
                        if (password.username.contains("==") && password.username.length > 20) {
                            securityManager.decryptData(password.username)
                        } else {
                            password.username
                        })
                    putExtra(AutofillAuthenticationActivity.EXTRA_PASSWORD_VALUE, 
                        securityManager.decryptData(password.password))
                    
                    // 传递字段ID和类型
                    val autofillIds = ArrayList<android.view.autofill.AutofillId>()
                    val fieldTypes = ArrayList<String>()
                    
                    parsedStructure.items.forEach { item ->
                        when (item.hint) {
                            EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                            EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                                autofillIds.add(item.id)
                                fieldTypes.add("username")
                            }
                            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                            EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                                autofillIds.add(item.id)
                                fieldTypes.add("password")
                            }
                            else -> {}
                        }
                    }
                    
                    putParcelableArrayListExtra(AutofillAuthenticationActivity.EXTRA_AUTOFILL_IDS, autofillIds)
                    putStringArrayListExtra(AutofillAuthenticationActivity.EXTRA_FIELD_TYPES, fieldTypes)
                }
                
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val authPendingIntent = PendingIntent.getActivity(
                    context,
                    password.id.toInt(),
                    authIntent,
                    flags
                )
                
                // 设置验证
                datasetBuilder.setAuthentication(authPendingIntent.intentSender)
                
                android.util.Log.d("AutofillPicker", "  Dataset authentication configured for: ${password.title}")
            }
            
            // 填充字段 - 如果有内联建议，需要传入到 setValue
            parsedStructure.items.forEach { item ->
                when (item.hint) {
                    EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                    EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                        if (!biometricQuickFillEnabled) {
                            // 不需要验证,直接填充
                            // 用户名可能也需要解密(如果加密的话)
                            val decryptedUsername = if (password.username.contains("==") && password.username.length > 20) {
                                // 看起来像是Base64加密的,尝试解密
                                securityManager.decryptData(password.username)
                            } else {
                                password.username
                            }
                            android.util.Log.d("AutofillPicker", "  Setting username field: ${item.hint}")
                            android.util.Log.d("AutofillPicker", "  Username value: '${decryptedUsername}' (length: ${decryptedUsername.length})")
                            
                            datasetBuilder.setValue(
                                item.id,
                                android.view.autofill.AutofillValue.forText(decryptedUsername)
                            )
                            fieldCount++
                        } else {
                            // 需要验证,设置占位符
                            datasetBuilder.setValue(item.id, null, presentation)
                        }
                    }
                    EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                    EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                        if (!biometricQuickFillEnabled) {
                            // 不需要验证,直接填充
                            // 解密密码
                            val decryptedPassword = securityManager.decryptData(password.password)
                            android.util.Log.d("AutofillPicker", "  Setting password field: ${item.hint}")
                            android.util.Log.d("AutofillPicker", "  Encrypted password: '${password.password}' (length: ${password.password.length})")
                            android.util.Log.d("AutofillPicker", "  Decrypted password: '${decryptedPassword}' (length: ${decryptedPassword.length})")
                            android.util.Log.d("AutofillPicker", "  Password title: '${password.title}')")
                            
                            datasetBuilder.setValue(
                                item.id,
                                android.view.autofill.AutofillValue.forText(decryptedPassword)
                            )
                            fieldCount++
                        } else {
                            // 需要验证,设置占位符
                            datasetBuilder.setValue(item.id, null, presentation)
                        }
                    }
                    else -> {
                        android.util.Log.d("AutofillPicker", "  Skipping field: ${item.hint}")
                    }
                }
            }
            
            android.util.Log.d("AutofillPicker", "  Dataset has $fieldCount fields set")
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        // 2. 添加"手动选择"选项 - 使用Authentication打开Bottom Sheet
        val pickerIntent = Intent(context, AutofillPickerActivity::class.java).apply {
            // 🔧 修复: 传递所有密码ID而不仅仅是匹配的密码,这样用户可以从所有密码中选择
            putExtra(
                AutofillPickerActivity.EXTRA_PASSWORD_IDS,
                allPasswordIds.toLongArray() // 使用 allPasswordIds 而不是 matchedPasswords
            )
            putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AutofillPickerActivity.EXTRA_DOMAIN, domain)
            
            // 传递字段ID列表
            val autofillIds = ArrayList(parsedStructure.items.map { it.id })
            putParcelableArrayListExtra("autofill_ids", autofillIds)
            
            putExtra(AutofillPickerActivity.EXTRA_FIELD_TYPE, "password")
        }
        
        android.util.Log.d("AutofillPicker", "📋 Manual selection will show ${allPasswordIds.size} passwords (${matchedPasswords.size} matched + ${allPasswordIds.size - matchedPasswords.size} others)")
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(context, 0, pickerIntent, flags)
        
        // 创建"手动选择"Dataset - 使用专门的卡片布局
        val manualSelectPresentation = RemoteViews(context.packageName, R.layout.autofill_manual_card)
        
        val manualSelectDataset = Dataset.Builder(manualSelectPresentation)
        parsedStructure.items.forEach { item ->
            manualSelectDataset.setValue(item.id, null, manualSelectPresentation)
        }
        manualSelectDataset.setAuthentication(pendingIntent.intentSender)
        
        responseBuilder.addDataset(manualSelectDataset.build())
        
        // 3. 🔐 添加"生成强密码"Dataset
        val passwordSuggestionIntent = Intent(context, PasswordSuggestionActivity::class.java).apply {
            // 生成强密码
            val generatedPassword = generateStrongPassword()
            
            // 提取用户名 (如果有)
            val usernameValue = parsedStructure.items
                .firstOrNull { 
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS
                }?.value ?: ""
            
            // 获取密码字段 AutofillId
            val passwordAutofillIds = parsedStructure.items
                .filter { 
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD ||
                    it.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD
                }
                .map { it.id }
            
            putExtra(PasswordSuggestionActivity.EXTRA_USERNAME, usernameValue)
            putExtra(PasswordSuggestionActivity.EXTRA_GENERATED_PASSWORD, generatedPassword)
            putExtra(PasswordSuggestionActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(PasswordSuggestionActivity.EXTRA_WEB_DOMAIN, domain ?: "")
            putParcelableArrayListExtra(
                PasswordSuggestionActivity.EXTRA_PASSWORD_FIELD_IDS,
                ArrayList(passwordAutofillIds)
            )
            
            android.util.Log.d("AutofillPicker", "🔐 Password suggestion intent created:")
            android.util.Log.d("AutofillPicker", "  - Username: $usernameValue")
            android.util.Log.d("AutofillPicker", "  - Password fields count: ${passwordAutofillIds.size}")
            passwordAutofillIds.forEachIndexed { index, id ->
                android.util.Log.d("AutofillPicker", "  - Field $index: $id")
            }
        }
        
        val passwordSuggestionPendingIntent = PendingIntent.getActivity(
            context,
            1001, // 使用独特的 requestCode
            passwordSuggestionIntent,
            flags
        )
        
        // 创建密码建议卡片
        val passwordSuggestionPresentation = RemoteViews(context.packageName, R.layout.autofill_password_suggestion_card)
        
        val passwordSuggestionDataset = Dataset.Builder(passwordSuggestionPresentation)
        // 只为密码字段设置值，不为所有字段设置
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    passwordSuggestionDataset.setValue(item.id, null, passwordSuggestionPresentation)
                    android.util.Log.d("AutofillPicker", "🔐 Added password field to suggestion dataset: ${item.id}")
                }
                else -> {
                    // 不为非密码字段设置值
                }
            }
        }
        passwordSuggestionDataset.setAuthentication(passwordSuggestionPendingIntent.intentSender)
        
        responseBuilder.addDataset(passwordSuggestionDataset.build())
        
        android.util.Log.d("AutofillPicker", "🔐 Password suggestion card added")
        
        // 4. 🎯 添加最小化的 SaveInfo
        // Android 框架限制:无法完全移除系统对话框
        // 策略:让系统对话框尽可能简洁,然后立即显示自定义 Bottom Sheet
        // 用户体验:闪现系统对话框(< 0.5秒) → 立即切换到自定义 Bottom Sheet
        addMinimalSaveInfo(responseBuilder, parsedStructure)
        
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
            val autofillIds = ArrayList(parsedStructure.items.map { it.id })
            putParcelableArrayListExtra("autofill_ids", autofillIds)
            
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
            0,
            pickerIntent,
            flags
        )
        
        // 创建一个占位Dataset,用于触发Activity
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, "选择密码 (${passwords.size})")
            setTextViewText(R.id.text_username, "点击查看所有密码")
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        // 必须为至少一个字段设置值,否则Dataset不会显示
        val datasetBuilder = Dataset.Builder(presentation)
        
        // 为所有字段设置Authentication
        parsedStructure.items.forEach { item ->
            datasetBuilder.setValue(item.id, null, presentation)
        }
        
        // 设置Authentication - 点击后启动Activity
        datasetBuilder.setAuthentication(pendingIntent.intentSender)
        
        responseBuilder.addDataset(datasetBuilder.build())
        
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
        
        // 创建RemoteViews
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, password.title.ifEmpty { password.username })
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        // 创建Dataset
        val datasetBuilder = Dataset.Builder(presentation)
        
        // 填充字段
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                    // 用户名可能也需要解密
                    val decryptedUsername = if (password.username.contains("==") && password.username.length > 20) {
                        securityManager.decryptData(password.username)
                    } else {
                        password.username
                    }
                    datasetBuilder.setValue(
                        item.id,
                        android.view.autofill.AutofillValue.forText(decryptedUsername)
                    )
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