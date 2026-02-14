package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PresetCustomField
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.components.AppSelectorField
import takagi.ru.monica.ui.components.CustomFieldEditorSection
import takagi.ru.monica.ui.components.CustomFieldEditCard
import takagi.ru.monica.ui.components.CustomFieldSectionHeader
import takagi.ru.monica.ui.components.PasswordStrengthIndicator
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.utils.PasswordStrengthAnalyzer
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.bitwarden.repository.BitwardenRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(
    viewModel: PasswordViewModel,
    totpViewModel: TotpViewModel? = null,
    bankCardViewModel: BankCardViewModel? = null,
    localKeePassViewModel: LocalKeePassViewModel? = null,
    passwordId: Long?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val passwordGenerator = remember { PasswordGenerator() }

    // 获取设置以读取进度条样式
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val settings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())
    
    // 获取预设自定义字段列表
    val presetCustomFields by settingsManager.presetCustomFieldsFlow.collectAsState(initial = emptyList())
    
    // 常用账号信息
    val commonAccountPreferences = remember { takagi.ru.monica.data.CommonAccountPreferences(context) }
    val commonAccountInfo by commonAccountPreferences.commonAccountInfo.collectAsState(
        initial = takagi.ru.monica.data.CommonAccountInfo()
    )
    
    // 是否显示常用账号选择器
    var showCommonAccountSelector by remember { mutableStateOf(false) }
    var commonAccountSelectorField by remember { mutableStateOf("") } // "email", "phone", "username"

    var title by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // CHANGE: Support multiple passwords
    val passwords = rememberSaveable(saver = takagi.ru.monica.utils.StringListSaver) { mutableStateListOf("") }
    var originalIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    
    var authenticatorKey by rememberSaveable { mutableStateOf("") }
    var passkeyBindings by rememberSaveable { mutableStateOf("") }
    var originalAuthenticatorKey by rememberSaveable { mutableStateOf("") }
    var existingTotpId by remember { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    // Control which password field is showing generator/visibility (simplified: global visibility)
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var currentPasswordIndexForGenerator by remember { mutableStateOf(-1) }
    
    // 防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }

    var appPackageName by rememberSaveable { mutableStateOf("") }
    var appName by rememberSaveable { mutableStateOf("") }

    // 绑定选项状态
    var bindTitle by rememberSaveable { mutableStateOf(false) }
    var bindWebsite by rememberSaveable { mutableStateOf(false) }

    // 新增字段状态 - 支持多个邮箱和电话
    val emails = rememberSaveable(saver = takagi.ru.monica.utils.StringListSaver) { mutableStateListOf("") }
    val phones = rememberSaveable(saver = takagi.ru.monica.utils.StringListSaver) { mutableStateListOf("") }
    var addressLine by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var zipCode by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var creditCardNumber by rememberSaveable { mutableStateOf("") }
    var creditCardHolder by rememberSaveable { mutableStateOf("") }
    var creditCardExpiry by rememberSaveable { mutableStateOf("") }
    var creditCardCVV by rememberSaveable { mutableStateOf("") }

    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    
    // KeePass 数据库选择
    var keepassDatabaseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val keepassDatabases by (localKeePassViewModel?.allDatabases ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    
    // Bitwarden Vault 选择
    var bitwardenVaultId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bitwardenFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
    
    // 加载 Bitwarden vaults
    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }
    
    // SSO 登录方式字段
    var loginType by rememberSaveable { mutableStateOf("PASSWORD") }
    var ssoProvider by rememberSaveable { mutableStateOf("") }
    var ssoRefEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    
    // 获取所有密码条目用于SSO关联选择
    val allPasswordsForRef by viewModel.allPasswords.collectAsState(initial = emptyList())
    
    // 自定义字段状态
    val customFields = remember { mutableStateListOf<CustomFieldDraft>() }
    var customFieldsExpanded by remember { mutableStateOf(false) }

    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(false) }
    var addressInfoExpanded by remember { mutableStateOf(false) }
    var paymentInfoExpanded by remember { mutableStateOf(false) }

    val isEditing = passwordId != null && passwordId > 0
    var hasAppliedFilterStorageDefaults by rememberSaveable(passwordId) { mutableStateOf(false) }
    
    // 字段可见性设置
    val fieldVisibility = settings.passwordFieldVisibility
    
    // 判断字段是否应该显示：设置开启 或 条目已有该字段数据
    fun shouldShowSecurityVerification() = fieldVisibility.securityVerification || authenticatorKey.isNotEmpty()
    fun shouldShowCategoryAndNotes() = fieldVisibility.categoryAndNotes || notes.isNotEmpty()
    fun shouldShowAppBinding() = fieldVisibility.appBinding || appPackageName.isNotEmpty()
    fun shouldShowPersonalInfo() = fieldVisibility.personalInfo || 
        emails.any { it.isNotEmpty() } || phones.any { it.isNotEmpty() }
    fun shouldShowAddressInfo() = fieldVisibility.addressInfo || 
        addressLine.isNotEmpty() || city.isNotEmpty() || state.isNotEmpty() || 
        zipCode.isNotEmpty() || country.isNotEmpty()
    fun shouldShowPaymentInfo() = fieldVisibility.paymentInfo || 
        creditCardNumber.isNotEmpty() || creditCardHolder.isNotEmpty() || 
        creditCardExpiry.isNotEmpty() || creditCardCVV.isNotEmpty()
    
    // 新建条目时的自动填充标记（只执行一次）
    var hasAutoFilled by rememberSaveable { mutableStateOf(false) }
    
    // 新建条目时自动填充常用账号信息
    LaunchedEffect(commonAccountInfo, isEditing, hasAutoFilled) {
        if (!isEditing && !hasAutoFilled && commonAccountInfo.autoFillEnabled && commonAccountInfo.hasAnyInfo()) {
            hasAutoFilled = true
            if (username.isEmpty() && commonAccountInfo.username.isNotEmpty()) {
                username = commonAccountInfo.username
            }
            if (emails.size == 1 && emails[0].isEmpty() && commonAccountInfo.email.isNotEmpty()) {
                emails[0] = commonAccountInfo.email
            }
            if (phones.size == 1 && phones[0].isEmpty() && commonAccountInfo.phone.isNotEmpty()) {
                phones[0] = commonAccountInfo.phone
            }
        }
    }
    
    // 新建条目时初始化预设自定义字段（只执行一次）
    var hasLoadedPresets by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(presetCustomFields, isEditing, hasLoadedPresets) {
        if (!isEditing && !hasLoadedPresets && presetCustomFields.isNotEmpty()) {
            hasLoadedPresets = true
            // 将预设字段添加到自定义字段列表（按order排序）
            val presetDrafts = presetCustomFields
                .sortedBy { it.order }
                .map { preset -> CustomFieldDraft.fromPreset(preset) }
            customFields.addAll(presetDrafts)
            // 如果有预设字段，默认展开自定义字段区域
            if (presetDrafts.isNotEmpty()) {
                customFieldsExpanded = true
            }
        }
    }

    // Load existing password data (including siblings)
    LaunchedEffect(passwordId) {
        if (passwordId != null) {
            coroutineScope.launch {
                val actualId = if (passwordId < 0) -passwordId else passwordId
                viewModel.getPasswordEntryById(actualId)?.let { entry ->
                    title = entry.title
                    website = entry.website
                    username = entry.username
                    notes = entry.notes
                    appPackageName = entry.appPackageName
                    appName = entry.appName
                    
                    // Load emails (stored as pipe-separated)
                    emails.clear()
                    if (entry.email.isNotEmpty()) {
                        emails.addAll(entry.email.split("|").filter { it.isNotBlank() })
                    }
                    if (emails.isEmpty()) emails.add("")
                    
                    // Load phones (stored as pipe-separated)
                    phones.clear()
                    if (entry.phone.isNotEmpty()) {
                        phones.addAll(entry.phone.split("|").filter { it.isNotBlank() })
                    }
                    if (phones.isEmpty()) phones.add("")
                    addressLine = entry.addressLine
                    city = entry.city
                    state = entry.state
                    zipCode = entry.zipCode
                    country = entry.country
                    creditCardNumber = entry.creditCardNumber
                    creditCardHolder = entry.creditCardHolder
                    creditCardExpiry = entry.creditCardExpiry
                    creditCardCVV = entry.creditCardCVV
                    categoryId = entry.categoryId
                    keepassDatabaseId = entry.keepassDatabaseId
                    bitwardenVaultId = entry.bitwardenVaultId
                    bitwardenFolderId = entry.bitwardenFolderId
                    authenticatorKey = entry.authenticatorKey  // ✅ 从密码条目中读取验证器密钥
                    originalAuthenticatorKey = entry.authenticatorKey
                    passkeyBindings = entry.passkeyBindings
                    
                    // 加载SSO登录方式字段
                    loginType = entry.loginType
                    ssoProvider = entry.ssoProvider
                    ssoRefEntryId = entry.ssoRefEntryId

                    if (isEditing) {
                        isFavorite = entry.isFavorite
                        
                        // Fetch all passwords in the group
                        val allEntries = viewModel.allPasswords.first()
                        val key = "${entry.title}|${entry.website}|${entry.username}|${entry.notes}|${entry.appPackageName}|${entry.appName}"
                        val siblings = allEntries.filter { item: PasswordEntry -> 
                            val itKey = "${item.title}|${item.website}|${item.username}|${item.notes}|${item.appPackageName}|${item.appName}"
                            itKey == key
                        }
                        
                        passwords.clear()
                        if (siblings.isNotEmpty()) {
                            passwords.addAll(siblings.map { s: PasswordEntry -> s.password })
                            originalIds = siblings.map { s: PasswordEntry -> s.id }
                        } else {
                            passwords.add(entry.password)
                            originalIds = listOf(entry.id)
                        }
                        
                        // 加载自定义字段
                        val existingFields = viewModel.getCustomFieldsByEntryIdSync(actualId)
                        customFields.clear()
                        
                        // 将现有字段转换为Draft
                        val existingDrafts = existingFields.map { field ->
                            CustomFieldDraft.fromCustomField(field)
                        }.toMutableList()
                        
                        // 获取预设字段并标记
                        // 检查现有字段是否匹配预设（按标题匹配）
                        val currentPresets = presetCustomFields.sortedBy { it.order }
                        val existingTitles = existingDrafts.map { it.title.lowercase() }.toSet()
                        
                        // 为匹配预设的现有字段添加预设标记
                        existingDrafts.replaceAll { draft ->
                            val matchingPreset = currentPresets.find { 
                                it.fieldName.lowercase() == draft.title.lowercase() 
                            }
                            if (matchingPreset != null) {
                                draft.copy(
                                    isPreset = true,
                                    isRequired = matchingPreset.isRequired,
                                    presetId = matchingPreset.id,
                                    placeholder = matchingPreset.placeholder
                                )
                            } else {
                                draft
                            }
                        }
                        
                        // 添加未在现有字段中出现的预设字段
                        currentPresets.forEach { preset ->
                            if (preset.fieldName.lowercase() !in existingTitles) {
                                existingDrafts.add(CustomFieldDraft.fromPreset(preset))
                            }
                        }
                        
                        customFields.addAll(existingDrafts)
                        if (existingDrafts.isNotEmpty()) {
                            customFieldsExpanded = true
                        }
                        Unit
                    } else {
                        passwords.add("")
                        Unit
                    }
                } ?: run {
                     // Fallback if entry not found or new
                     if (passwords.isEmpty()) passwords.add("")
                }
            }
        } else {
             if (passwords.isEmpty()) passwords.add("")
        }
    }

    val canSave = title.isNotEmpty() && !isSaving
    val handleSave: () -> Unit = {
        if (title.isNotEmpty() && !isSaving) {
            isSaving = true // 防止重复点击
            // Capture values before async call
            val currentAuthKey = authenticatorKey
            val currentTitle = title
            val currentUsername = username
            val currentAppPackageName = appPackageName
            val currentAppName = appName
            val currentWebsite = website
            val currentBindWebsite = bindWebsite
            val currentBindTitle = bindTitle

            // Create common entry without password
            val commonEntry = PasswordEntry(
                id = 0, // Will be ignored by saveGroupedPasswords logic for new items
                title = title,
                website = website,
                username = username,
                password = "", // Placeholder
                notes = notes,
                isFavorite = isFavorite,
                appPackageName = appPackageName,
                appName = appName,
                email = emails.filter { it.isNotBlank() }.joinToString("|"),
                phone = phones.filter { it.isNotBlank() }.joinToString("|"),
                addressLine = addressLine,
                city = city,
                state = state,
                zipCode = zipCode,
                country = country,
                creditCardNumber = creditCardNumber,
                creditCardHolder = creditCardHolder,
                creditCardExpiry = creditCardExpiry,
                creditCardCVV = creditCardCVV,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,  // ✅ 保存到 Bitwarden Vault
                bitwardenFolderId = bitwardenFolderId,
                authenticatorKey = currentAuthKey,  // ✅ 保存验证器密钥
                passkeyBindings = passkeyBindings,
                loginType = loginType,
                ssoProvider = ssoProvider,
                ssoRefEntryId = ssoRefEntryId
            )

            // 快照自定义字段
            val currentCustomFields = customFields.toList()

            viewModel.saveGroupedPasswords(
                originalIds = originalIds,
                commonEntry = commonEntry,
                passwords = passwords.toList(), // Snapshot
                customFields = currentCustomFields, // 保存自定义字段
                onComplete = { firstPasswordId ->
                    // Save TOTP if authenticatorKey is provided
                    if (currentAuthKey.isNotEmpty() && firstPasswordId != null && totpViewModel != null) {
                        // 检查是否已有相同密钥的验证器
                        val existingTotp = totpViewModel.findTotpBySecret(currentAuthKey)
                        val totpIdToSave = existingTotp?.id ?: existingTotpId // 优先选择相同密钥的，其次是原本绑定的

                        val totpData = TotpData(
                            secret = currentAuthKey,
                            issuer = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).issuer } ?: currentTitle,
                            accountName = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).accountName } ?: currentUsername,
                            boundPasswordId = firstPasswordId,
                            otpType = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).otpType } ?: takagi.ru.monica.data.model.OtpType.TOTP,
                            digits = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).digits } ?: 6,
                            period = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).period } ?: 30,
                            algorithm = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).algorithm } ?: "SHA1",
                            counter = existingTotp?.let { Json.decodeFromString<TotpData>(it.itemData).counter } ?: 0
                        )

                        totpViewModel.saveTotpItem(
                            id = totpIdToSave,
                            title = existingTotp?.title ?: currentTitle,
                            notes = existingTotp?.notes ?: "",
                            totpData = totpData,
                            isFavorite = existingTotp?.isFavorite ?: false
                        )
                    } else if (currentAuthKey.isEmpty() && originalAuthenticatorKey.isNotEmpty() && firstPasswordId != null && totpViewModel != null) {
                        // 密码页清空密钥：只取消验证器绑定，不删除验证器
                        totpViewModel.unbindTotpFromPassword(firstPasswordId, originalAuthenticatorKey)
                    }

                    if (currentAppPackageName.isNotEmpty()) {
                        if (currentBindWebsite && currentWebsite.isNotEmpty()) {
                            viewModel.updateAppAssociationByWebsite(currentWebsite, currentAppPackageName, currentAppName)
                        }
                        if (currentBindTitle && currentTitle.isNotEmpty()) {
                            viewModel.updateAppAssociationByTitle(currentTitle, currentAppPackageName, currentAppName)
                        }
                    }
                    onNavigateBack()
                }
            )
        }
    }

    LaunchedEffect(isEditing, currentFilter, hasAppliedFilterStorageDefaults) {
        if (isEditing || hasAppliedFilterStorageDefaults) return@LaunchedEffect
        when (val filter = currentFilter) {
            is CategoryFilter.Custom -> {
                categoryId = filter.categoryId
                keepassDatabaseId = null
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is CategoryFilter.KeePassDatabase -> {
                categoryId = null
                keepassDatabaseId = filter.databaseId
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is CategoryFilter.KeePassGroupFilter -> {
                categoryId = null
                keepassDatabaseId = filter.databaseId
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is CategoryFilter.KeePassDatabaseStarred -> {
                categoryId = null
                keepassDatabaseId = filter.databaseId
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is CategoryFilter.KeePassDatabaseUncategorized -> {
                categoryId = null
                keepassDatabaseId = filter.databaseId
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is CategoryFilter.BitwardenVault -> {
                categoryId = null
                keepassDatabaseId = null
                bitwardenVaultId = filter.vaultId
                bitwardenFolderId = null
            }
            is CategoryFilter.BitwardenFolderFilter -> {
                categoryId = null
                keepassDatabaseId = null
                bitwardenVaultId = filter.vaultId
                bitwardenFolderId = filter.folderId
            }
            is CategoryFilter.BitwardenVaultStarred -> {
                categoryId = null
                keepassDatabaseId = null
                bitwardenVaultId = filter.vaultId
                bitwardenFolderId = null
            }
            is CategoryFilter.BitwardenVaultUncategorized -> {
                categoryId = null
                keepassDatabaseId = null
                bitwardenVaultId = filter.vaultId
                bitwardenFolderId = null
            }
            else -> {
                categoryId = null
                keepassDatabaseId = null
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
        }
        hasAppliedFilterStorageDefaults = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isEditing) R.string.edit_password_title else R.string.add_password_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = handleSave,
                containerColor = if (canSave) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSave) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.save)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Vault/Storage Selector - 保管库选择器（类似Bitwarden）
            item {
                VaultSelector(
                    keepassDatabases = keepassDatabases,
                    selectedKeePassDatabaseId = keepassDatabaseId,
                    onKeePassDatabaseSelected = { 
                        keepassDatabaseId = it
                        // 选择 KeePass 时清除 Bitwarden 选择
                        if (it != null) {
                            bitwardenVaultId = null
                            bitwardenFolderId = null
                            categoryId = null
                        }
                    },
                    bitwardenVaults = bitwardenVaults,
                    selectedBitwardenVaultId = bitwardenVaultId,
                    onBitwardenVaultSelected = {
                        bitwardenVaultId = it
                        // 选择 Bitwarden 时清除 KeePass 选择
                        if (it != null) {
                            keepassDatabaseId = null
                            categoryId = null
                        }
                    },
                    selectedBitwardenFolderId = bitwardenFolderId,
                    onBitwardenFolderSelected = { folderId ->
                        bitwardenFolderId = folderId
                        if (bitwardenVaultId != null) {
                            keepassDatabaseId = null
                            categoryId = null
                        }
                    },
                    categories = categories,
                    selectedCategoryId = categoryId,
                    onCategorySelected = {
                        categoryId = it
                        keepassDatabaseId = null
                        bitwardenVaultId = null
                        bitwardenFolderId = null
                    }
                )
            }
            
            // Credentials Card
            item {
                InfoCard(title = stringResource(R.string.section_credentials)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.title_required)) },
                            leadingIcon = { Icon(Icons.Default.Label, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Website
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text(stringResource(R.string.website_url)) },
                            leadingIcon = { Icon(Icons.Default.Language, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Username - 支持常用账号填充
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.username_email)) },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                trailingIcon = {
                                    Row {
                                        // 常用账号填充按钮（仅在新建且有配置常用信息时显示）
                                        if (!isEditing && commonAccountInfo.hasAnyInfo() && !commonAccountInfo.autoFillEnabled && commonAccountInfo.username.isNotEmpty()) {
                                            IconButton(
                                                onClick = { username = commonAccountInfo.username }
                                            ) {
                                                Icon(
                                                    Icons.Default.PersonAdd,
                                                    contentDescription = stringResource(R.string.fill_common_account),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        // 复制按钮
                                        if (username.isNotEmpty()) {
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("username", username)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, context.getString(R.string.username_copied), Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        
                        // 登录方式选择
                        LoginTypeSelector(
                            loginType = loginType,
                            ssoProvider = ssoProvider,
                            ssoRefEntryId = ssoRefEntryId,
                            allPasswords = allPasswordsForRef,
                            onLoginTypeChange = { loginType = it },
                            onSsoProviderChange = { ssoProvider = it },
                            onSsoRefEntryIdChange = { ssoRefEntryId = it }
                        )

                        // Passwords (仅在账号密码模式下显示)
                        AnimatedVisibility(visible = loginType == "PASSWORD") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                passwords.forEachIndexed { index, pwd ->
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = pwd,
                                                onValueChange = { passwords[index] = it },
                                                label = { Text(if (passwords.size > 1) stringResource(R.string.password) + " ${index + 1}" else stringResource(R.string.password_required)) },
                                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    Row {
                                                        IconButton(onClick = { 
                                                            showPasswordGenerator = true 
                                                            currentPasswordIndexForGenerator = index
                                                        }) {
                                                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate_password))
                                                        }
                                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                            Icon(
                                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                                contentDescription = null
                                                            )
                                                        }
                                                        // Allow removing only if more than 1
                                                        if (passwords.size > 1) {
                                                            IconButton(onClick = { passwords.removeAt(index) }) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                        
                                        // Strength Indicator for EACH password or just hide it to avoid clutter?
                                        // User didn't specify. But showing it is good.
                                        if (pwd.isNotEmpty()) {
                                            val strength = PasswordStrengthAnalyzer.calculateStrength(pwd)
                                            PasswordStrengthIndicator(
                                                strength = strength,
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                                            )
                                        }
                                    }
                                }

                                // Add Password Button
                                OutlinedButton(
                                    onClick = { passwords.add("") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_password))
                                }
                            }
                        }
                    }
                }
            }
            
            // Security Card (TOTP) - 根据设置和数据决定是否显示
            if (shouldShowSecurityVerification()) {
                item {
                    InfoCard(title = stringResource(R.string.section_security_verification)) {
                        OutlinedTextField(
                            value = authenticatorKey,
                            onValueChange = { authenticatorKey = it },
                            label = { Text(stringResource(R.string.authenticator_key_optional)) },
                            placeholder = { Text(stringResource(R.string.authenticator_key_hint)) },
                            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Organization Card - 根据设置和数据决定是否显示
            if (shouldShowCategoryAndNotes()) {
                item {
                    InfoCard(title = stringResource(R.string.notes)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.notes)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            }  // 分类与备注 if 结束
            
            // 自定义字段区域标题 (带添加按钮)
            item {
                CustomFieldSectionHeader(
                    onAddClick = {
                        customFields.add(CustomFieldDraft(
                            id = CustomFieldDraft.nextTempId(),
                            title = "",
                            value = "",
                            isProtected = false
                        ))
                    }
                )
            }
            
            // 自定义字段编辑卡片 (独立卡片样式)
            items(customFields.size) { index ->
                val field = customFields[index]
                CustomFieldEditCard(
                    index = index,
                    field = field,
                    onFieldChange = { updated ->
                        customFields[index] = updated
                    },
                    onDelete = {
                        customFields.removeAt(index)
                    }
                )
            }
            
            // App Binding Card - 根据设置和数据决定是否显示
            if (shouldShowAppBinding()) {
                item {
                    InfoCard(title = stringResource(R.string.section_app_association)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppSelectorField(
                                selectedPackageName = appPackageName,
                                selectedAppName = appName,
                                onAppSelected = { packageName, name ->
                                    appPackageName = packageName
                                    appName = name
                                }
                            )
                            
                            AnimatedVisibility(visible = appPackageName.isNotEmpty()) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { bindWebsite = !bindWebsite }
                                    ) {
                                        Checkbox(checked = bindWebsite, onCheckedChange = { bindWebsite = it })
                                        Column {
                                            Text(stringResource(R.string.bind_website), style = MaterialTheme.typography.bodyMedium)
                                            Text(stringResource(R.string.bind_website_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { bindTitle = !bindTitle }
                                    ) {
                                        Checkbox(checked = bindTitle, onCheckedChange = { bindTitle = it })
                                        Column {
                                            Text(stringResource(R.string.bind_title), style = MaterialTheme.typography.bodyMedium)
                                            Text(stringResource(R.string.bind_title_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible: Personal Info - 根据设置和数据决定是否显示
            if (shouldShowPersonalInfo()) {
                item {
                    CollapsibleCard(
                        title = stringResource(R.string.personal_info),
                        icon = Icons.Default.Person,
                        expanded = personalInfoExpanded,
                        onExpandChange = { personalInfoExpanded = it }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Multiple Email Fields
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.field_email),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                // 常用邮箱填充按钮
                                if (!isEditing && commonAccountInfo.hasAnyInfo() && !commonAccountInfo.autoFillEnabled && commonAccountInfo.email.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            if (emails.size == 1 && emails[0].isEmpty()) {
                                                emails[0] = commonAccountInfo.email
                                            } else {
                                                emails.add(commonAccountInfo.email)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.fill_common_account))
                                    }
                                }
                        }
                        emails.forEachIndexed { index, emailValue ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = emailValue,
                                    onValueChange = { emails[index] = it },
                                    label = { Text("${stringResource(R.string.field_email)} ${index + 1}") },
                                    leadingIcon = { Icon(MonicaIcons.General.email, null) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    isError = emailValue.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidEmail(emailValue)
                                )
                                if (emails.size > 1) {
                                    IconButton(
                                        onClick = { emails.removeAt(index) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { emails.add("") },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_email))
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Multiple Phone Fields
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.field_phone),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            // 常用电话填充按钮
                            if (!isEditing && commonAccountInfo.hasAnyInfo() && !commonAccountInfo.autoFillEnabled && commonAccountInfo.phone.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        if (phones.size == 1 && phones[0].isEmpty()) {
                                            phones[0] = commonAccountInfo.phone
                                        } else {
                                            phones.add(commonAccountInfo.phone)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.fill_common_account))
                                }
                            }
                        }
                        phones.forEachIndexed { index, phoneValue ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = phoneValue,
                                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 15) phones[index] = it },
                                    label = { Text("${stringResource(R.string.field_phone)} ${index + 1}") },
                                    leadingIcon = { Icon(MonicaIcons.General.phone, null) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                if (phones.size > 1) {
                                    IconButton(
                                        onClick = { phones.removeAt(index) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { phones.add("") },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_phone))
                        }
                    }
                }
            }
            }  // Personal Info if 结束

            // Collapsible: Address Info - 根据设置和数据决定是否显示
            if (shouldShowAddressInfo()) {
                item {
                    CollapsibleCard(
                        title = stringResource(R.string.address_info),
                        icon = Icons.Default.Home,
                        expanded = addressInfoExpanded,
                        onExpandChange = { addressInfoExpanded = it }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = addressLine,
                                onValueChange = { addressLine = it },
                                label = { Text(stringResource(R.string.field_address)) },
                                leadingIcon = { Icon(Icons.Default.Home, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text(stringResource(R.string.field_city)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = state,
                                onValueChange = { state = it },
                                label = { Text(stringResource(R.string.field_state)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = zipCode,
                                onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) zipCode = it },
                                label = { Text(stringResource(R.string.field_postal_code)) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it },
                                label = { Text(stringResource(R.string.field_country)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
            }  // Address Info if 结束

            // Collapsible: Payment Info
            if (shouldShowPaymentInfo()) {
            item {
                CollapsibleCard(
                    title = stringResource(R.string.payment_info),
                    icon = Icons.Default.CreditCard,
                    expanded = paymentInfoExpanded,
                    onExpandChange = { paymentInfoExpanded = it }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Import Button
                        if (bankCardViewModel != null) {
                            var showBankCardDialog by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showBankCardDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CreditCard, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.import_from_bank_card))
                            }
                            
                            // Bank Card Selection Logic (retained)
                            if (showBankCardDialog) {
                                val bankCards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
                                AlertDialog(
                                    onDismissRequest = { showBankCardDialog = false },
                                    title = { Text(stringResource(R.string.select_bank_card)) },
                                    text = {
                                        if (bankCards.isEmpty()) Text(stringResource(R.string.no_bank_card_data))
                                        else LazyColumn {
                                            items(bankCards) { item ->
                                                val cardData = try { Json.decodeFromString<BankCardData>(item.itemData) } catch (e: Exception) { null }
                                                ListItem(
                                                    headlineContent = { Text(item.title) },
                                                    supportingContent = { Text(if (cardData != null) stringResource(R.string.tail_number_last4, cardData.cardNumber.takeLast(4)) else stringResource(R.string.parse_failed)) },
                                                    leadingContent = { Icon(Icons.Default.CreditCard, null) },
                                                    modifier = Modifier.clickable {
                                                        if (cardData != null) {
                                                            creditCardNumber = cardData.cardNumber
                                                            creditCardHolder = cardData.cardholderName
                                                            creditCardCVV = cardData.cvv
                                                            val month = cardData.expiryMonth.padStart(2, '0')
                                                            val year = if (cardData.expiryYear.length == 4) cardData.expiryYear.takeLast(2) else cardData.expiryYear
                                                            creditCardExpiry = "$month/$year"
                                                            showBankCardDialog = false
                                                            Toast.makeText(context, context.getString(R.string.imported), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showBankCardDialog = false }) { Text(stringResource(R.string.cancel)) } }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = creditCardNumber,
                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 19) creditCardNumber = it },
                            label = { Text(stringResource(R.string.field_card_number)) },
                            leadingIcon = { Icon(MonicaIcons.Data.creditCard, null) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (creditCardNumber.isNotEmpty()) {
                                VisualTransformation { text ->
                                    val offsetMapping = object : OffsetMapping {
                                        override fun originalToTransformed(offset: Int) = if (offset <= 0) 0 else offset + (offset - 1) / 4
                                        override fun transformedToOriginal(offset: Int) = if (offset <= 0) 0 else offset - offset / 5
                                    }
                                    TransformedText(AnnotatedString(takagi.ru.monica.utils.FieldValidation.formatCreditCard(text.text)), offsetMapping)
                                }
                            } else VisualTransformation.None
                        )

                        OutlinedTextField(
                            value = creditCardHolder,
                            onValueChange = { creditCardHolder = it },
                            label = { Text(stringResource(R.string.field_cardholder)) },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = creditCardExpiry,
                                onValueChange = {
                                    val digits = it.filter { char -> char.isDigit() }
                                    creditCardExpiry = when {
                                        digits.length <= 2 -> digits
                                        digits.length <= 4 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                                        else -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}"
                                    }
                                },
                                label = { Text(stringResource(R.string.field_expiry)) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = creditCardCVV,
                                onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 4) creditCardCVV = it },
                                label = { Text(stringResource(R.string.field_cvv)) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    }
                }
            }
            }  // Payment Info if 结束
        }
    }

    if (showPasswordGenerator) {
        PasswordGeneratorDialog(
            onDismiss = { showPasswordGenerator = false },
            onPasswordGenerated = { generatedPassword ->
                if (currentPasswordIndexForGenerator >= 0 && currentPasswordIndexForGenerator < passwords.size) {
                    passwords[currentPasswordIndexForGenerator] = generatedPassword
                }
                showPasswordGenerator = false
            }
        )
    }
}

/**
 * Common Card Container for grouping fields
 */
@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

/**
 * Collapsible Card for optional sections
 */
@Composable
private fun CollapsibleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 彩色密码显示转换器
 */
class ColoredPasswordVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val coloredText = buildAnnotatedString {
            text.forEach { char ->
                when {
                    char.isLetter() -> withStyle(style = SpanStyle(color = Color(0xFFE0E0E0))) { append(char) }
                    char.isDigit() -> withStyle(style = SpanStyle(color = Color(0xFF64B5F6))) { append(char) }
                    else -> withStyle(style = SpanStyle(color = Color(0xFFEF5350))) { append(char) }
                }
            }
        }
        return TransformedText(coloredText, OffsetMapping.Identity)
    }
}

@Composable
fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onPasswordGenerated: (String) -> Unit
) {
    val passwordGenerator = remember { PasswordGenerator() }
    var length by remember { mutableStateOf(16) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var excludeSimilar by remember { mutableStateOf(true) }
    var generatedPassword by remember { mutableStateOf("") }
    
    // Helper to generate
    fun generate() {
        try {
            generatedPassword = passwordGenerator.generatePassword(
                PasswordGenerator.PasswordOptions(length, includeUppercase, includeLowercase, includeNumbers, includeSymbols, excludeSimilar)
            )
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) { generate() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_generator)) },
        text = {
            Column {
                OutlinedTextField(
                    value = generatedPassword,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { generate() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate_password))
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.length_value, length))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt(); generate() },
                    valueRange = 8f..32f,
                    steps = 24
                )
                Column {
                    Row(Modifier.fillMaxWidth().clickable { includeUppercase = !includeUppercase; generate() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.uppercase_az)); Checkbox(checked = includeUppercase, onCheckedChange = { includeUppercase = it; generate() }) }
                    Row(Modifier.fillMaxWidth().clickable { includeLowercase = !includeLowercase; generate() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lowercase_az)); Checkbox(checked = includeLowercase, onCheckedChange = { includeLowercase = it; generate() }) }
                    Row(Modifier.fillMaxWidth().clickable { includeNumbers = !includeNumbers; generate() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.numbers_09)); Checkbox(checked = includeNumbers, onCheckedChange = { includeNumbers = it; generate() }) }
                    Row(Modifier.fillMaxWidth().clickable { includeSymbols = !includeSymbols; generate() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.symbols)); Checkbox(checked = includeSymbols, onCheckedChange = { includeSymbols = it; generate() }) }
                    Row(Modifier.fillMaxWidth().clickable { excludeSimilar = !excludeSimilar; generate() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.exclude_similar)); Checkbox(checked = excludeSimilar, onCheckedChange = { excludeSimilar = it; generate() }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPasswordGenerated(generatedPassword) }) { Text(stringResource(R.string.use_password)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * 登录方式选择器组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginTypeSelector(
    loginType: String,
    ssoProvider: String,
    ssoRefEntryId: Long?,
    allPasswords: List<PasswordEntry>,
    onLoginTypeChange: (String) -> Unit,
    onSsoProviderChange: (String) -> Unit,
    onSsoRefEntryIdChange: (Long?) -> Unit
) {
    val context = LocalContext.current
    var showProviderMenu by remember { mutableStateOf(false) }
    var showRefEntryPicker by remember { mutableStateOf(false) }
    
    // 获取引用的条目信息
    val refEntry = remember(ssoRefEntryId, allPasswords) {
        allPasswords.find { it.id == ssoRefEntryId }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 登录方式标签
        Text(
            text = context.getString(R.string.login_type_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 登录方式切换
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = loginType == "PASSWORD",
                onClick = { onLoginTypeChange("PASSWORD") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { 
                    if (loginType == "PASSWORD") {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    }
                }
            ) {
                Text(context.getString(R.string.login_type_password))
            }
            SegmentedButton(
                selected = loginType == "SSO",
                onClick = { onLoginTypeChange("SSO") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { 
                    if (loginType == "SSO") {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    }
                }
            ) {
                Text(context.getString(R.string.login_type_sso))
            }
        }
        
        // SSO 详细设置
        AnimatedVisibility(visible = loginType == "SSO") {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 提供商选择
                ExposedDropdownMenuBox(
                    expanded = showProviderMenu,
                    onExpandedChange = { showProviderMenu = it }
                ) {
                    val providerDisplayName = if (ssoProvider.isNotEmpty()) {
                        takagi.ru.monica.data.SsoProvider.fromName(ssoProvider).displayName
                    } else {
                        context.getString(R.string.sso_provider_select)
                    }
                    
                    OutlinedTextField(
                        value = providerDisplayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.sso_provider_label)) },
                        leadingIcon = { 
                            Icon(
                                imageVector = getSsoProviderIcon(ssoProvider),
                                contentDescription = null
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviderMenu) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        takagi.ru.monica.data.SsoProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                leadingIcon = { Icon(imageVector = getSsoProviderIcon(provider.name), contentDescription = null) },
                                trailingIcon = if (ssoProvider == provider.name) {
                                    { Icon(Icons.Default.Check, null) }
                                } else null,
                                onClick = {
                                    onSsoProviderChange(provider.name)
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }
                
                // 关联账号选择
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRefEntryPicker = true }
                ) {
                    OutlinedTextField(
                        value = refEntry?.let { "${it.title} (${it.username})" } 
                            ?: context.getString(R.string.sso_ref_entry_none),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(context.getString(R.string.sso_ref_entry_label)) },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        trailingIcon = {
                            Row {
                                if (ssoRefEntryId != null) {
                                    IconButton(onClick = { onSsoRefEntryIdChange(null) }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                    }
                                }
                                Icon(
                                    Icons.Default.Search, 
                                    contentDescription = stringResource(R.string.select),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                // 提示文字
                val displayProvider = if (ssoProvider.isNotEmpty()) {
                    takagi.ru.monica.data.SsoProvider.fromName(ssoProvider).displayName
                } else {
                    context.getString(R.string.sso_provider_select)
                }

                Text(
                    text = context.getString(R.string.sso_description, displayProvider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // 关联账号选择对话框
    if (showRefEntryPicker) {
        SsoRefEntryPickerDialog(
            allPasswords = allPasswords.filter { it.loginType == "PASSWORD" && it.id != ssoRefEntryId },
            currentRefEntryId = ssoRefEntryId,
            onSelect = { entry ->
                onSsoRefEntryIdChange(entry.id)
                showRefEntryPicker = false
            },
            onDismiss = { showRefEntryPicker = false }
        )
    }
}

/**
 * 获取SSO提供商图标
 */
@Composable
private fun getSsoProviderIcon(providerName: String): ImageVector {
    return when (providerName) {
        "GOOGLE" -> Icons.Default.Public
        "APPLE" -> Icons.Default.PhoneIphone
        "FACEBOOK" -> Icons.Default.Facebook
        "MICROSOFT" -> Icons.Default.Computer
        "GITHUB" -> Icons.Default.Code
        "TWITTER" -> Icons.Default.Public
        "WECHAT" -> Icons.Default.Chat
        "QQ" -> Icons.Default.Chat
        "WEIBO" -> Icons.Default.Public
        else -> Icons.Default.Login
    }
}

/**
 * SSO关联账号选择对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SsoRefEntryPickerDialog(
    allPasswords: List<PasswordEntry>,
    currentRefEntryId: Long?,
    onSelect: (PasswordEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredPasswords = remember(searchQuery, allPasswords) {
        if (searchQuery.isBlank()) {
            allPasswords
        } else {
            allPasswords.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) ||
                it.website.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.sso_ref_entry_picker_title)) },
        text = {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(context.getString(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 密码列表
                if (filteredPasswords.isEmpty()) {
                    Text(
                        text = context.getString(R.string.no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredPasswords) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(entry) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (entry.id == currentRefEntryId)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = entry.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (entry.website.isNotEmpty()) {
                                            Text(
                                                text = entry.website,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    if (entry.id == currentRefEntryId) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}

/**
 * 保管库选择器 - M3E 风格设计
 * 选择存储位置：仅 Monica 本地、KeePass 数据库 或 Bitwarden Vault
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSelector(
    keepassDatabases: List<LocalKeePassDatabase>,
    selectedKeePassDatabaseId: Long?,
    onKeePassDatabaseSelected: (Long?) -> Unit,
    bitwardenVaults: List<BitwardenVault>,
    selectedBitwardenVaultId: Long?,
    onBitwardenVaultSelected: (Long?) -> Unit,
    selectedBitwardenFolderId: String?,
    onBitwardenFolderSelected: (String?) -> Unit,
    categories: List<takagi.ru.monica.data.Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    // 如果没有任何外部数据库，不显示选择器
    if (keepassDatabases.isEmpty() && bitwardenVaults.isEmpty()) return
    
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val database = remember { PasswordDatabase.getDatabase(context) }
    
    val selectedKeePassDatabase = keepassDatabases.find { it.id == selectedKeePassDatabaseId }
    val selectedBitwardenVault = bitwardenVaults.find { it.id == selectedBitwardenVaultId }
    
    // 判断当前选择的类型
    val isKeePass = selectedKeePassDatabase != null
    val isBitwarden = selectedBitwardenVault != null
    val isLocal = !isKeePass && !isBitwarden
    
    val displayName = when {
        isKeePass -> selectedKeePassDatabase!!.name
        isBitwarden -> "Bitwarden (${selectedBitwardenVault!!.email})"
        else -> stringResource(R.string.vault_monica_only)
    }

    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name
        ?: stringResource(R.string.category_none)
    val displaySubtitle = when {
        isKeePass -> stringResource(R.string.vault_sync_hint)
        isBitwarden -> stringResource(R.string.sync_save_to_bitwarden)
        else -> stringResource(R.string.vault_monica_only_desc)
    } + " · ${stringResource(R.string.category)}: $selectedCategoryName"
    
    val containerColor = when {
        isBitwarden -> MaterialTheme.colorScheme.tertiaryContainer
        isKeePass -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    val contentColor = when {
        isBitwarden -> MaterialTheme.colorScheme.onTertiaryContainer
        isKeePass -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    val iconColor = when {
        isBitwarden -> MaterialTheme.colorScheme.tertiary
        isKeePass -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    
    val icon = when {
        isBitwarden -> Icons.Default.Cloud
        isKeePass -> Icons.Default.Key
        else -> Icons.Default.Shield
    }
    
    // M3E 风格的卡片选择器
    Surface(
        onClick = { showBottomSheet = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // M3E 风格图标容器
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            isBitwarden -> MaterialTheme.colorScheme.onTertiary
                            isKeePass -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSecondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 文字区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            
            // 展开图标
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
    
    // M3E 风格的 BottomSheet 选择器
    if (showBottomSheet) {
        var localExpanded by remember { mutableStateOf(false) }
        var expandedBitwardenVaultId by remember { mutableStateOf<Long?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.vault_select_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                VaultSectionHeaderItem(
                    title = stringResource(R.string.vault_monica_only),
                    subtitle = stringResource(R.string.vault_monica_only_desc),
                    icon = Icons.Default.Shield,
                    isSelected = isLocal,
                    expanded = localExpanded,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        if (!localExpanded) {
                            localExpanded = true
                            expandedBitwardenVaultId = null
                        } else {
                            onKeePassDatabaseSelected(null)
                            onBitwardenVaultSelected(null)
                            onBitwardenFolderSelected(null)
                        }
                    }
                )

                AnimatedVisibility(visible = localExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VaultOptionItem(
                            title = stringResource(R.string.category_none),
                            subtitle = null,
                            icon = Icons.Default.FolderOff,
                            isSelected = selectedCategoryId == null,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.outline,
                            onClick = { onCategorySelected(null) }
                        )

                        categories.forEach { category ->
                            VaultOptionItem(
                                title = category.name,
                                subtitle = null,
                                icon = Icons.Default.Folder,
                                isSelected = selectedCategoryId == category.id,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.primary,
                                onClick = { onCategorySelected(category.id) }
                            )
                        }
                    }
                }

                bitwardenVaults.forEach { vault ->
                    val vaultExpanded = expandedBitwardenVaultId == vault.id
                    val folders by (
                        if (vaultExpanded) {
                            database.bitwardenFolderDao().getFoldersByVaultFlow(vault.id)
                        } else {
                            flowOf(emptyList<BitwardenFolder>())
                        }
                    ).collectAsState(initial = emptyList())
                    val vaultSelectedAsRoot = selectedBitwardenVaultId == vault.id && selectedBitwardenFolderId == null

                    VaultSectionHeaderItem(
                        title = vault.displayName ?: vault.email,
                        subtitle = stringResource(R.string.sync_save_to_bitwarden),
                        icon = Icons.Default.Cloud,
                        isSelected = vaultSelectedAsRoot,
                        expanded = vaultExpanded,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            if (!vaultExpanded) {
                                expandedBitwardenVaultId = vault.id
                                localExpanded = false
                            } else {
                                onBitwardenVaultSelected(vault.id)
                                onBitwardenFolderSelected(null)
                            }
                        }
                    )

                    AnimatedVisibility(visible = vaultExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            folders.forEach { folder ->
                                VaultOptionItem(
                                    title = folder.name,
                                    subtitle = null,
                                    icon = Icons.Default.Folder,
                                    isSelected = selectedBitwardenVaultId == vault.id &&
                                        selectedBitwardenFolderId == folder.bitwardenFolderId,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    iconColor = MaterialTheme.colorScheme.tertiary,
                                    onClick = {
                                        onBitwardenVaultSelected(vault.id)
                                        onBitwardenFolderSelected(folder.bitwardenFolderId)
                                    }
                                )
                            }
                        }
                    }
                }

                keepassDatabases.forEach { database ->
                    val storageText = if (database.storageLocation == takagi.ru.monica.data.KeePassStorageLocation.EXTERNAL)
                        stringResource(R.string.external_storage)
                    else
                        stringResource(R.string.internal_storage)

                    VaultOptionItem(
                        title = database.name,
                        subtitle = storageText,
                        icon = Icons.Default.Key,
                        isSelected = selectedKeePassDatabaseId == database.id,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onKeePassDatabaseSelected(database.id)
                            onBitwardenFolderSelected(null)
                            expandedBitwardenVaultId = null
                            localExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultSectionHeaderItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isSelected: Boolean,
    expanded: Boolean,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) iconColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) contentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 保管库选项卡片 - M3E 风格
 */
@Composable
private fun VaultOptionItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isSelected: Boolean,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) iconColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) 
                            MaterialTheme.colorScheme.surface 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected)
                            contentColor.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 选中指示
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = iconColor,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
