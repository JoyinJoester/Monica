package takagi.ru.monica.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.components.AppSelectorDialog
import takagi.ru.monica.ui.components.CustomIconActionDialog
import takagi.ru.monica.ui.components.CustomFieldEditorSection
import takagi.ru.monica.ui.components.CustomFieldEditCard
import takagi.ru.monica.ui.components.CustomFieldSectionHeader
import takagi.ru.monica.ui.components.InlineTotpPreviewCard
import takagi.ru.monica.ui.components.MultiStorageTargetPickerBottomSheet
import takagi.ru.monica.ui.components.MultiStorageTargetSelectorCard
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.components.NotePickerBottomSheet
import takagi.ru.monica.ui.components.PasswordEntryPickerBottomSheet
import takagi.ru.monica.ui.components.PasswordStrengthIndicator
import takagi.ru.monica.ui.components.buildMultiStorageTarget
import takagi.ru.monica.ui.components.SimpleIconPickerBottomSheet
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED
import takagi.ru.monica.ui.icons.PasswordCustomIconStore
import takagi.ru.monica.ui.icons.SimpleIconCatalog
import takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon
import takagi.ru.monica.ui.icons.rememberSimpleIconBitmap
import takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon
import takagi.ru.monica.ui.password.UsernameSuggestionPanel
import takagi.ru.monica.ui.password.UsernameSuggestionState
import takagi.ru.monica.ui.password.buildUsernameSuggestionState
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.utils.PasswordStrengthAnalyzer
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.autofill_ng.ui.rememberFavicon
import takagi.ru.monica.domain.provider.PasswordSource
import takagi.ru.monica.ui.model.SecretValueState
import takagi.ru.monica.ui.model.plainValueOrEmpty
import java.io.File
import java.util.Locale
import takagi.ru.monica.ui.components.OutlinedTextField

private const val MONICA_USERNAME_ALIAS_FIELD_TITLE = "__monica_username_alias"
private const val MONICA_USERNAME_ALIAS_META_FIELD_TITLE = "__monica_username_alias_meta"
private const val MONICA_USERNAME_ALIAS_META_VALUE = "migrated_v1"
private const val ICON_PICKER_PAGE_SIZE = 120

private data class CommonAccountFillOption(
    val id: String,
    val type: String,
    val content: String
)

private enum class PasswordFillMode {
    GENERATOR,
    COMMON_ACCOUNT
}

data class AddEditPasswordInitialDraft(
    val title: String = "",
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val appPackageName: String = "",
    val appName: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(
    viewModel: PasswordViewModel,
    totpViewModel: TotpViewModel? = null,
    bankCardViewModel: BankCardViewModel? = null,
    noteViewModel: NoteViewModel? = null,
    localKeePassViewModel: LocalKeePassViewModel? = null,
    passwordId: Long?,
    initialDraft: AddEditPasswordInitialDraft? = null,
    forceShowAppBinding: Boolean = false,
    initialCategoryId: Long? = null,
    initialKeePassDatabaseId: Long? = null,
    initialKeePassGroupPath: String? = null,
    initialBitwardenVaultId: Long? = null,
    initialBitwardenFolderId: String? = null,
    pendingQrResult: String? = null,
    onConsumePendingQrResult: () -> Unit = {},
    onScanAuthenticatorQrCode: (() -> Unit)? = null,
    onSaveCompleted: ((Long?) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { PasswordDatabase.getDatabase(context) }

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
    val commonAccountTemplates by commonAccountPreferences.templatesFlow.collectAsState(initial = emptyList())
    
    // 是否显示常用账号选择器
    var showCommonAccountSelector by remember { mutableStateOf(false) }
    var commonAccountSelectorField by remember { mutableStateOf("") } // "email", "phone", "username", "password"
    var commonAccountSelectorTargetIndex by remember { mutableStateOf(-1) }
    var isUsernameFieldFocused by remember { mutableStateOf(false) }
    var isSeparatedUsernameFieldFocused by remember { mutableStateOf(false) }
    val usernameSuggestionBringIntoViewRequester = remember { BringIntoViewRequester() }
    val separatedUsernameSuggestionBringIntoViewRequester = remember { BringIntoViewRequester() }
    val passwordSuggestionBringIntoViewRequester = remember { BringIntoViewRequester() }
    var focusedPasswordFieldIndex by remember { mutableStateOf<Int?>(null) }

    var title by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // CHANGE: Support multiple passwords
    val passwords = rememberSaveable(saver = takagi.ru.monica.utils.StringListSaver) { mutableStateListOf("") }
    var originalIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var unreadablePasswordIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hasOwnershipConflict by remember { mutableStateOf(false) }
    
    var authenticatorSecret by rememberSaveable { mutableStateOf("") }
    var selectedAuthenticatorOtpTypeName by rememberSaveable { mutableStateOf(OtpType.TOTP.name) }
    var passkeyBindings by rememberSaveable { mutableStateOf("") }
    var originalAuthenticatorKey by rememberSaveable { mutableStateOf("") }
    var existingSshKeyData by rememberSaveable { mutableStateOf("") }
    var existingTotpId by remember { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var boundNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    // 每个密码条目维护独立可见状态，避免一个小眼睛影响全部条目
    val passwordVisibilityStates = remember { mutableStateMapOf<Int, Boolean>() }
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
    var keepassGroupPath by rememberSaveable { mutableStateOf<String?>(null) }
    val keepassDatabases by (localKeePassViewModel?.allDatabases ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    
    // Bitwarden Vault 选择
    var bitwardenVaultId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bitwardenFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    val bitwardenVaults by bitwardenRepository.getAllVaultsFlow().collectAsState(initial = emptyList())
    val hasExplicitInitialStorage = initialCategoryId != null ||
        initialKeePassDatabaseId != null ||
        initialKeePassGroupPath != null ||
        initialBitwardenVaultId != null ||
        initialBitwardenFolderId != null
    val selectedStorageTargets = remember { mutableStateListOf<StorageTarget>() }
    var existingReplicaTargetKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentReplicaGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showStorageTargetSheet by remember { mutableStateOf(false) }
    
    // SSO 登录方式字段
    var loginType by rememberSaveable { mutableStateOf("PASSWORD") }
    var ssoProvider by rememberSaveable { mutableStateOf("") }
    var ssoRefEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    
    // 获取所有密码条目用于SSO关联选择
    val allPasswordsForRef by viewModel.allPasswords.collectAsState(initial = emptyList())
    val allNotes by (noteViewModel?.allNotes ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val selectableNotes = remember(allNotes) { allNotes.filter { !it.isDeleted } }
    val selectedBoundNote = remember(boundNoteId, selectableNotes) {
        boundNoteId?.let { noteId -> selectableNotes.firstOrNull { it.id == noteId } }
    }
    var showBoundNotePicker by remember { mutableStateOf(false) }
    
    // 自定义字段状态
    val customFields = remember { mutableStateListOf<CustomFieldDraft>() }
    var customFieldsExpanded by remember { mutableStateOf(false) }
    var separatedUsername by rememberSaveable { mutableStateOf("") }
    val inlineGeneratedPasswords = remember { mutableStateMapOf<Int, String>() }
    val inlinePasswordGenerator = remember { PasswordGenerator() }
    val selectedAuthenticatorOtpType = remember(selectedAuthenticatorOtpTypeName) {
        runCatching { OtpType.valueOf(selectedAuthenticatorOtpTypeName) }.getOrDefault(OtpType.TOTP)
    }
    val authenticatorKey = remember(authenticatorSecret, selectedAuthenticatorOtpType, title, username) {
        buildPasswordScreenAuthenticatorPayload(
            secret = authenticatorSecret,
            otpType = selectedAuthenticatorOtpType,
            issuer = title,
            accountName = username
        )
    }
    val authenticatorPreviewTotpData = remember(authenticatorKey, title, username) {
        buildPasswordScreenInlinePreviewTotpData(
            rawKey = authenticatorKey,
            issuer = title,
            accountName = username
        )
    }
    val authenticatorPreviewVisible = authenticatorPreviewTotpData != null
    val authenticatorPreviewCurrentSeconds by produceState(
        initialValue = System.currentTimeMillis() / 1000,
        key1 = authenticatorPreviewTotpData?.otpType
    ) {
        value = System.currentTimeMillis() / 1000
        while (true) {
            value = System.currentTimeMillis() / 1000
            kotlinx.coroutines.delay(1000)
        }
    }
    val authenticatorPreviewProgressTimeMillis by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = authenticatorPreviewTotpData?.otpType,
        key2 = settings.validatorSmoothProgress
    ) {
        value = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            val waitMillis = if (settings.validatorSmoothProgress) {
                50L
            } else {
                (1000L - (now % 1000L)).coerceAtLeast(16L)
            }
            kotlinx.coroutines.delay(waitMillis)
        }
    }

    // 自定义图标状态
    var customIconType by rememberSaveable { mutableStateOf(PASSWORD_ICON_TYPE_NONE) }
    var customIconValue by rememberSaveable { mutableStateOf<String?>(null) }
    var customIconUpdatedAt by rememberSaveable { mutableStateOf(0L) }
    var originalCustomIconType by remember { mutableStateOf(PASSWORD_ICON_TYPE_NONE) }
    var originalCustomIconValue by remember { mutableStateOf<String?>(null) }
    var hasSavedSuccessfully by remember { mutableStateOf(false) }

    var showCustomIconDialog by remember { mutableStateOf(false) }
    var showSimpleIconPicker by remember { mutableStateOf(false) }
    var customIconSearchQuery by rememberSaveable { mutableStateOf("") }

    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(false) }
    var addressInfoExpanded by remember { mutableStateOf(false) }
    var paymentInfoExpanded by remember { mutableStateOf(false) }

    val isEditing = passwordId != null && passwordId > 0
    val usernameLabel = stringResource(R.string.autofill_username)
    val selectedSimpleIconBitmap = rememberSimpleIconBitmap(
        slug = if (customIconType == PASSWORD_ICON_TYPE_SIMPLE) customIconValue else null,
        tintColor = MaterialTheme.colorScheme.primary,
        enabled = settings.iconCardsEnabled
    )
    val selectedUploadedIconBitmap = rememberUploadedPasswordIcon(
        value = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) customIconValue else null
    )
    val autoMatchedSimpleIcon = rememberAutoMatchedSimpleIcon(
        website = website,
        title = title,
        appPackageName = appPackageName,
        tintColor = MaterialTheme.colorScheme.primary,
        enabled = settings.iconCardsEnabled && customIconType == PASSWORD_ICON_TYPE_NONE
    )
    val fallbackWebsiteFavicon = rememberFavicon(
        url = website,
        enabled = settings.iconCardsEnabled &&
            customIconType == PASSWORD_ICON_TYPE_NONE &&
            autoMatchedSimpleIcon.resolved &&
            autoMatchedSimpleIcon.slug == null
    )
    
    // 字段可见性设置
    val fieldVisibility = settings.passwordFieldVisibility
    val commonAccountTypeEmail = stringResource(R.string.common_account_type_email)
    val commonAccountTypeAccount = stringResource(R.string.common_account_type_account)
    val commonAccountTypePhone = stringResource(R.string.common_account_type_phone)
    val commonAccountTypePassword = stringResource(R.string.common_account_type_password)
    val commonAccountTypeName = stringResource(R.string.common_account_type_name)
    val fieldAccountLabel = stringResource(R.string.field_account)
    val fieldEmailLabel = stringResource(R.string.field_email)
    val fieldPhoneLabel = stringResource(R.string.field_phone)
    var authenticatorTypeExpanded by remember { mutableStateOf(false) }

    fun applyAuthenticatorInput(rawValue: String) {
        val trimmed = rawValue.trim()
        val parsed = if (trimmed.contains("://")) {
            TotpDataResolver.fromAuthenticatorKey(
                rawKey = trimmed,
                fallbackIssuer = title,
                fallbackAccountName = username
            )
        } else {
            null
        }
        if (parsed != null) {
            authenticatorSecret = parsed.secret
            selectedAuthenticatorOtpTypeName = parsed.otpType.toPasswordScreenOtpType().name
        } else {
            authenticatorSecret = rawValue
        }
    }

    fun applyScannedAuthenticator(rawValue: String) {
        when (val scanResult = takagi.ru.monica.util.TotpUriParser.parseScannedContent(rawValue)) {
            is takagi.ru.monica.util.TotpScanParseResult.Single -> {
                val imported = scanResult.item.totpData
                authenticatorSecret = imported.secret
                selectedAuthenticatorOtpTypeName = imported.otpType.toPasswordScreenOtpType().name
                if (title.isBlank()) {
                    title = scanResult.item.label
                        .substringBefore(":")
                        .ifBlank { imported.issuer }
                        .ifBlank { title }
                }
                if (username.isBlank()) {
                    username = imported.accountName
                }
            }
            is takagi.ru.monica.util.TotpScanParseResult.Multiple -> {
                scanResult.items.firstOrNull()?.let { first ->
                    val imported = first.totpData
                    authenticatorSecret = imported.secret
                    selectedAuthenticatorOtpTypeName = imported.otpType.toPasswordScreenOtpType().name
                    if (title.isBlank()) {
                        title = first.label
                            .substringBefore(":")
                            .ifBlank { imported.issuer }
                            .ifBlank { title }
                    }
                    if (username.isBlank()) {
                        username = imported.accountName
                    }
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.qr_migration_multiple_fill_first, scanResult.items.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
            takagi.ru.monica.util.TotpScanParseResult.UnsupportedPhoneFactor -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.qr_phonefactor_not_supported),
                    Toast.LENGTH_SHORT
                ).show()
            }
            takagi.ru.monica.util.TotpScanParseResult.InvalidFormat -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.qr_invalid_authenticator),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(pendingQrResult) {
        pendingQrResult?.let { qrValue ->
            applyScannedAuthenticator(qrValue)
            onConsumePendingQrResult()
        }
    }

    fun syncLegacyStorageState(targets: List<StorageTarget>) {
        when (val primaryTarget = targets.firstOrNull()) {
            is StorageTarget.MonicaLocal -> {
                categoryId = primaryTarget.categoryId
                keepassDatabaseId = null
                keepassGroupPath = null
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is StorageTarget.KeePass -> {
                categoryId = null
                keepassDatabaseId = primaryTarget.databaseId
                keepassGroupPath = primaryTarget.groupPath
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is StorageTarget.Bitwarden -> {
                categoryId = null
                keepassDatabaseId = null
                keepassGroupPath = null
                bitwardenVaultId = primaryTarget.vaultId
                bitwardenFolderId = primaryTarget.folderId
            }
            null -> {
                categoryId = null
                keepassDatabaseId = null
                keepassGroupPath = null
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
        }
    }

    fun setSelectedStorageTargets(targets: List<StorageTarget>) {
        val normalizedTargets = targets.distinctBy(StorageTarget::stableKey)
        selectedStorageTargets.clear()
        selectedStorageTargets.addAll(normalizedTargets)
        syncLegacyStorageState(normalizedTargets)
    }

    fun addSelectedStorageTarget(target: StorageTarget) {
        if (selectedStorageTargets.any { it.stableKey == target.stableKey }) return
        selectedStorageTargets.add(target)
        syncLegacyStorageState(selectedStorageTargets)
    }

    fun removeSelectedStorageTarget(target: StorageTarget) {
        if (selectedStorageTargets.size <= 1) return
        selectedStorageTargets.removeAll { it.stableKey == target.stableKey }
        syncLegacyStorageState(selectedStorageTargets)
    }

    fun normalizeCommonTemplateType(raw: String): String {
        val value = raw.trim()
        val normalized = value.lowercase(Locale.ROOT)
        return when {
            normalized == commonAccountTypeEmail.lowercase(Locale.ROOT) ||
                normalized == "email" || normalized == "邮箱" -> commonAccountTypeEmail
            normalized == commonAccountTypeAccount.lowercase(Locale.ROOT) ||
                normalized == "account" || normalized == "账号" -> commonAccountTypeAccount
            normalized == commonAccountTypePhone.lowercase(Locale.ROOT) ||
                normalized == "phone" || normalized == "手机号" || normalized == "电话" -> commonAccountTypePhone
            normalized == commonAccountTypePassword.lowercase(Locale.ROOT) ||
                normalized == "password" || normalized == "密码" -> commonAccountTypePassword
            normalized == commonAccountTypeName.lowercase(Locale.ROOT) ||
                normalized == "name" || normalized == "姓名" -> commonAccountTypeName
            else -> commonAccountTypeAccount
        }
    }

    val accountTemplates = remember(commonAccountTemplates, commonAccountTypeAccount, commonAccountTypeEmail, commonAccountTypePassword) {
        commonAccountTemplates.filter {
            normalizeCommonTemplateType(it.type) == commonAccountTypeAccount && it.content.isNotBlank()
        }
    }
    val emailTemplates = remember(commonAccountTemplates, commonAccountTypeAccount, commonAccountTypeEmail, commonAccountTypePassword) {
        commonAccountTemplates.filter {
            normalizeCommonTemplateType(it.type) == commonAccountTypeEmail && it.content.isNotBlank()
        }
    }
    val phoneTypeTemplates = remember(commonAccountTemplates, commonAccountTypePhone) {
        commonAccountTemplates.filter {
            normalizeCommonTemplateType(it.type) == commonAccountTypePhone && it.content.isNotBlank()
        }
    }
    val passwordTemplates = remember(commonAccountTemplates, commonAccountTypeAccount, commonAccountTypeEmail, commonAccountTypePhone, commonAccountTypePassword) {
        commonAccountTemplates.filter {
            normalizeCommonTemplateType(it.type) == commonAccountTypePassword && it.content.isNotBlank()
        }
    }
    val phoneTemplates = remember(phoneTypeTemplates, accountTemplates, fieldPhoneLabel) {
        val normalizedPhoneLabel = fieldPhoneLabel.trim().lowercase(Locale.ROOT)
        val legacyPhoneLikeFromAccount = accountTemplates.filter { template ->
            val normalizedTitle = template.title.trim().lowercase(Locale.ROOT)
            val digits = template.content.filter { it.isDigit() }
            val trimmedContent = template.content.trim()
            val looksLikePhoneByTitle =
                normalizedTitle.contains(normalizedPhoneLabel) ||
                    normalizedTitle.contains("phone") ||
                    normalizedTitle.contains("手机号") ||
                    normalizedTitle.contains("电话")
            val looksLikePhoneByPattern =
                trimmedContent.matches(Regex("^[+()\\-\\s\\d]{7,}$")) && digits.length >= 7
            looksLikePhoneByTitle || looksLikePhoneByPattern
        }
        (phoneTypeTemplates + legacyPhoneLikeFromAccount)
            .distinctBy { it.content.trim().lowercase(Locale.ROOT) }
    }

    val canSelectUsernameTemplate = !isEditing && !commonAccountInfo.autoFillEnabled &&
        (accountTemplates.isNotEmpty() || emailTemplates.isNotEmpty() || phoneTemplates.isNotEmpty())
    val canSelectEmailTemplate = !isEditing && !commonAccountInfo.autoFillEnabled &&
        emailTemplates.isNotEmpty()
    val canSelectPhoneTemplate = !isEditing && !commonAccountInfo.autoFillEnabled &&
        phoneTemplates.isNotEmpty()
    fun buildCommonAccountOptions(field: String): List<CommonAccountFillOption> {
        val options = buildList {
            when (field) {
                "username" -> {
                    accountTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = template.id,
                                type = commonAccountTypeAccount,
                                content = template.content
                            )
                        )
                    }
                    emailTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = "email_as_account_${template.id}",
                                type = commonAccountTypeEmail,
                                content = template.content
                            )
                        )
                    }
                    phoneTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = "phone_as_account_${template.id}",
                                type = commonAccountTypePhone,
                                content = template.content
                            )
                        )
                    }
                }
                "email" -> {
                    emailTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = template.id,
                                type = commonAccountTypeEmail,
                                content = template.content
                            )
                        )
                    }
                }
                "phone" -> {
                    phoneTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = template.id,
                                type = commonAccountTypePhone,
                                content = template.content
                            )
                        )
                    }
                }
                "password" -> {
                    passwordTemplates.forEach { template ->
                        add(
                            CommonAccountFillOption(
                                id = template.id,
                                type = commonAccountTypePassword,
                                content = template.content
                            )
                        )
                    }
                }
            }
        }

        return options
            .filter { it.content.isNotBlank() }
            .distinctBy { "${it.type.trim().lowercase(Locale.ROOT)}|${it.content.trim().lowercase(Locale.ROOT)}" }
    }

    val currentEntryIdForUsernameSuggestion = remember(passwordId) {
        passwordId?.let { if (it < 0) -it else it }
    }
    val usernameSuggestionState = remember(
        username,
        isUsernameFieldFocused,
        currentEntryIdForUsernameSuggestion,
        allPasswordsForRef
    ) {
        if (!isUsernameFieldFocused) {
            UsernameSuggestionState.Hidden
        } else {
            buildUsernameSuggestionState(
                query = username,
                currentEntryId = currentEntryIdForUsernameSuggestion,
                passwordEntries = allPasswordsForRef
            )
        }
    }
    val separatedUsernameSuggestionState = remember(
        separatedUsername,
        settings.separateUsernameAccountEnabled,
        isSeparatedUsernameFieldFocused,
        currentEntryIdForUsernameSuggestion,
        allPasswordsForRef
    ) {
        if (!settings.separateUsernameAccountEnabled || !isSeparatedUsernameFieldFocused) {
            UsernameSuggestionState.Hidden
        } else {
            buildUsernameSuggestionState(
                query = separatedUsername,
                currentEntryId = currentEntryIdForUsernameSuggestion,
                passwordEntries = allPasswordsForRef
            )
        }
    }

    val usernameSuggestionVisible = usernameSuggestionState !is UsernameSuggestionState.Hidden
    val separatedUsernameSuggestionVisible =
        separatedUsernameSuggestionState !is UsernameSuggestionState.Hidden
    val focusedPasswordSuggestionVisible = focusedPasswordFieldIndex?.let { index ->
        index in passwords.indices &&
            passwords[index].isBlank() &&
            !inlineGeneratedPasswords[index].isNullOrBlank()
    } ?: false

    LaunchedEffect(usernameSuggestionVisible) {
        if (usernameSuggestionVisible) {
            kotlinx.coroutines.delay(80)
            usernameSuggestionBringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(separatedUsernameSuggestionVisible) {
        if (separatedUsernameSuggestionVisible) {
            kotlinx.coroutines.delay(80)
            separatedUsernameSuggestionBringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(focusedPasswordSuggestionVisible) {
        if (focusedPasswordSuggestionVisible) {
            kotlinx.coroutines.delay(80)
            passwordSuggestionBringIntoViewRequester.bringIntoView()
        }
    }

    fun generateInlinePasswordSuggestion(): String {
        return inlinePasswordGenerator.generatePassword()
    }

    fun ensureInlinePasswordSuggestion(index: Int) {
        if (inlineGeneratedPasswords[index].isNullOrBlank()) {
            inlineGeneratedPasswords[index] = generateInlinePasswordSuggestion()
        }
    }

    fun <T> shiftIndexedStateMapAfterRemoval(stateMap: MutableMap<Int, T>, removedIndex: Int) {
        if (stateMap.isEmpty()) return
        val shiftedEntries = stateMap.entries
            .asSequence()
            .filter { it.key != removedIndex }
            .map { entry ->
                val shiftedIndex = if (entry.key > removedIndex) entry.key - 1 else entry.key
                shiftedIndex to entry.value
            }
            .toList()
        stateMap.clear()
        shiftedEntries.forEach { (shiftedIndex, value) ->
            stateMap[shiftedIndex] = value
        }
    }

    fun resetPasswordFieldTransientState() {
        inlineGeneratedPasswords.clear()
        passwordVisibilityStates.clear()
        focusedPasswordFieldIndex = null
        showPasswordGenerator = false
        currentPasswordIndexForGenerator = -1
    }

    fun removePasswordFieldAt(index: Int) {
        if (index !in passwords.indices) return
        passwords.removeAt(index)
        shiftIndexedStateMapAfterRemoval(inlineGeneratedPasswords, index)
        shiftIndexedStateMapAfterRemoval(passwordVisibilityStates, index)

        val focusedIndex = focusedPasswordFieldIndex
        focusedPasswordFieldIndex = when {
            focusedIndex == null -> null
            focusedIndex == index -> null
            focusedIndex > index -> focusedIndex - 1
            else -> focusedIndex
        }

        currentPasswordIndexForGenerator = when {
            currentPasswordIndexForGenerator == index -> -1
            currentPasswordIndexForGenerator > index -> currentPasswordIndexForGenerator - 1
            else -> currentPasswordIndexForGenerator
        }
    }

    fun isPasswordFieldVisible(index: Int): Boolean = passwordVisibilityStates[index] == true

    fun togglePasswordFieldVisibility(index: Int) {
        passwordVisibilityStates[index] = !isPasswordFieldVisible(index)
    }

    fun applyCommonAccountSelection(field: String, content: String) {
        val value = content.trim()
        if (value.isEmpty()) return
        when (field) {
            "username" -> username = value
            "email" -> {
                val targetIndex = commonAccountSelectorTargetIndex.takeIf { it in emails.indices }
                if (targetIndex != null) {
                    emails[targetIndex] = value
                } else if (emails.size == 1 && emails[0].isEmpty()) {
                    emails[0] = value
                } else {
                    emails.add(value)
                }
            }
            "phone" -> {
                val targetIndex = commonAccountSelectorTargetIndex.takeIf { it in phones.indices }
                if (targetIndex != null) {
                    phones[targetIndex] = value
                } else if (phones.size == 1 && phones[0].isEmpty()) {
                    phones[0] = value
                } else {
                    phones.add(value)
                }
            }
            "password" -> {
                val targetIndex = commonAccountSelectorTargetIndex.takeIf { it in passwords.indices }
                if (targetIndex != null) {
                    passwords[targetIndex] = value
                } else if (passwords.size == 1 && passwords[0].isEmpty()) {
                    passwords[0] = value
                } else {
                    passwords.add(value)
                }
            }
        }
    }
    
    fun normalizedIconFileName(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let { File(it).name }
    fun isOriginalUploadedIconFile(value: String?): Boolean {
        val current = normalizedIconFileName(value)
        val original = normalizedIconFileName(originalCustomIconValue)
        return originalCustomIconType == PASSWORD_ICON_TYPE_UPLOADED &&
            !original.isNullOrBlank() &&
            current == original
    }
    
    // 判断字段是否应该显示：设置开启 或 条目已有该字段数据
    fun shouldShowSecurityVerification() = fieldVisibility.securityVerification || authenticatorSecret.isNotEmpty()
    fun shouldShowCategoryAndNotes() =
        fieldVisibility.categoryAndNotes ||
            notes.isNotEmpty() ||
            boundNoteId != null ||
            noteViewModel != null
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
    var initialDraftApplied by rememberSaveable(passwordId) { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val imported = PasswordCustomIconStore.importAndCompress(context, uri)
            imported.onSuccess { fileName ->
                if (customIconType == PASSWORD_ICON_TYPE_UPLOADED && customIconValue != fileName) {
                    val previous = normalizedIconFileName(customIconValue)
                    if (!previous.isNullOrBlank() && !isOriginalUploadedIconFile(previous)) {
                        PasswordCustomIconStore.deleteIconFile(context, previous)
                    }
                }
                customIconType = PASSWORD_ICON_TYPE_UPLOADED
                customIconValue = fileName
                customIconUpdatedAt = System.currentTimeMillis()
                Toast.makeText(context, context.getString(R.string.custom_icon_upload_success), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.custom_icon_upload_failed, error.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (!hasSavedSuccessfully) {
                val currentUploaded = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                    normalizedIconFileName(customIconValue)
                } else {
                    null
                }
                if (!currentUploaded.isNullOrBlank() && !isOriginalUploadedIconFile(currentUploaded)) {
                    PasswordCustomIconStore.deleteIconFile(context, currentUploaded)
                }
            }
        }
    }

    // 旧版默认账号信息迁移到模板，避免新页面出现“默认XXX”遗留项
    LaunchedEffect(commonAccountTypeAccount, commonAccountTypeEmail, commonAccountTypePhone, fieldAccountLabel, fieldEmailLabel, fieldPhoneLabel) {
        commonAccountPreferences.migrateLegacyDefaultsToTemplatesIfNeeded(
            accountType = commonAccountTypeAccount,
            emailType = commonAccountTypeEmail,
            phoneType = commonAccountTypePhone,
            accountTitle = fieldAccountLabel,
            emailTitle = fieldEmailLabel,
            phoneTitle = fieldPhoneLabel
        )
    }
    
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
        resetPasswordFieldTransientState()
        if (passwordId != null) {
            coroutineScope.launch {
                val actualId = if (passwordId < 0) -passwordId else passwordId
                viewModel.getRawPasswordEntryById(actualId)?.let { rawEntry ->
                    hasOwnershipConflict = viewModel.hasOwnershipConflict(rawEntry)
                    val secretState = viewModel.inspectSecretState(rawEntry)
                    val entry = rawEntry.copy(
                        password = secretState.plainValueOrEmpty()
                    )
                    title = entry.title
                    website = entry.website
                    username = entry.username
                    notes = entry.notes
                    boundNoteId = entry.boundNoteId
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
                    keepassGroupPath = entry.keepassGroupPath
                    bitwardenVaultId = entry.bitwardenVaultId
                    bitwardenFolderId = entry.bitwardenFolderId
                    currentReplicaGroupId = entry.replicaGroupId
                    val authenticatorDraft = resolvePasswordScreenAuthenticatorDraft(
                        rawKey = entry.authenticatorKey,
                        fallbackIssuer = entry.title,
                        fallbackAccountName = entry.username
                    )
                    authenticatorSecret = authenticatorDraft.secret
                    selectedAuthenticatorOtpTypeName = authenticatorDraft.otpType.toPasswordScreenOtpType().name
                    originalAuthenticatorKey = entry.authenticatorKey
                    passkeyBindings = entry.passkeyBindings
                    existingSshKeyData = entry.sshKeyData
                    
                    // 加载SSO登录方式字段
                    loginType = if (entry.loginType.equals("SSO", ignoreCase = true)) {
                        "SSO"
                    } else {
                        "PASSWORD"
                    }
                    ssoProvider = entry.ssoProvider
                    ssoRefEntryId = entry.ssoRefEntryId
                    customIconType = entry.customIconType
                    customIconValue = normalizedIconFileName(entry.customIconValue)
                    customIconUpdatedAt = entry.customIconUpdatedAt
                    originalCustomIconType = entry.customIconType
                    originalCustomIconValue = normalizedIconFileName(entry.customIconValue)

                    if (isEditing) {
                        isFavorite = entry.isFavorite
                        
                        // Fetch all passwords in the group
                        val allEntries = viewModel.getRawActivePasswordEntries()
                        val key = buildPasswordSiblingGroupKey(entry)
                        val siblings = allEntries.filter { item: PasswordEntry -> 
                            val itKey = buildPasswordSiblingGroupKey(item)
                            itKey == key
                        }.sortedBy { it.id }
                        
                        passwords.clear()
                        if (siblings.isNotEmpty()) {
                            val unreadableIds = mutableSetOf<Long>()
                            passwords.addAll(
                                siblings.map { sibling ->
                                    val secretState = viewModel.inspectSecretState(sibling)
                                    if (
                                        secretState is SecretValueState.Unreadable &&
                                        secretState.source is PasswordSource.Bitwarden
                                    ) {
                                        unreadableIds += sibling.id
                                    }
                                    secretState.plainValueOrEmpty()
                                }
                            )
                            originalIds = siblings.map { s: PasswordEntry -> s.id }
                            unreadablePasswordIds = unreadableIds
                        } else {
                            passwords.add(entry.password)
                            originalIds = listOf(entry.id)
                            unreadablePasswordIds = if (
                                secretState is SecretValueState.Unreadable &&
                                secretState.source is PasswordSource.Bitwarden
                            ) {
                                setOf(entry.id)
                            } else {
                                emptySet()
                            }
                        }

                        val currentTarget = rawEntry.toStorageTarget()
                        val replicaTargets = if (!entry.replicaGroupId.isNullOrBlank()) {
                            allEntries
                                .filter {
                                    it.replicaGroupId == entry.replicaGroupId &&
                                        !it.isDeleted &&
                                        !it.isArchived
                                }
                                .map(PasswordEntry::toStorageTarget)
                                .distinctBy(StorageTarget::stableKey)
                        } else {
                            listOf(currentTarget)
                        }
                        val selectedTargets = buildList {
                            add(currentTarget)
                            addAll(
                                replicaTargets.filter {
                                    it.stableKey != currentTarget.stableKey
                                }.sortedBy(StorageTarget::stableKey)
                            )
                        }
                        setSelectedStorageTargets(selectedTargets)
                        existingReplicaTargetKeys = selectedTargets
                            .map(StorageTarget::stableKey)
                            .toSet()
                        
                        // 加载自定义字段
                        val existingFields = viewModel.getCustomFieldsByEntryIdSync(actualId)
                        customFields.clear()
                        
                        // 将现有字段转换为Draft
                        val existingDrafts = existingFields.map { field ->
                            CustomFieldDraft.fromCustomField(field)
                        }.toMutableList()

                        val hasAliasMeta = existingDrafts.any {
                            it.title == MONICA_USERNAME_ALIAS_META_FIELD_TITLE &&
                                it.value == MONICA_USERNAME_ALIAS_META_VALUE
                        }
                        val aliasDraft = existingDrafts.firstOrNull {
                            it.title == MONICA_USERNAME_ALIAS_FIELD_TITLE ||
                                (hasAliasMeta && it.title == usernameLabel)
                        }
                        if (aliasDraft != null) {
                            separatedUsername = aliasDraft.value
                        }

                        // 内部转换字段始终不在普通自定义字段列表中显示
                        existingDrafts.removeAll {
                            it.title == MONICA_USERNAME_ALIAS_FIELD_TITLE ||
                                it.title == MONICA_USERNAME_ALIAS_META_FIELD_TITLE
                        }
                        
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
                     hasOwnershipConflict = false
                     unreadablePasswordIds = emptySet()
                     if (passwords.isEmpty()) passwords.add("")
                }
            }
        } else {
             hasOwnershipConflict = false
             unreadablePasswordIds = emptySet()
             existingSshKeyData = ""
             if (passwords.isEmpty()) passwords.add("")
             if (!initialDraftApplied && initialDraft != null) {
                 if (title.isBlank()) title = initialDraft.title
                 if (website.isBlank()) website = initialDraft.website
                 if (username.isBlank()) username = initialDraft.username
                 if (appPackageName.isBlank()) appPackageName = initialDraft.appPackageName
                 if (appName.isBlank()) appName = initialDraft.appName
                 if (initialDraft.password.isNotBlank()) {
                     passwords.clear()
                     passwords.add(initialDraft.password)
                 }
                 initialDraftApplied = true
             }
        }
    }

    val canSave = title.isNotEmpty() && !isSaving && !hasOwnershipConflict
    val handleSave: () -> Unit = handleSave@{
        if (title.isNotEmpty() && !isSaving) {
            if (hasOwnershipConflict) {
                Toast.makeText(
                    context,
                    context.getString(R.string.password_owner_conflict_display),
                    Toast.LENGTH_SHORT
                ).show()
                return@handleSave
            }
            isSaving = true // 防止重复点击
            val normalizedPasswords = passwords.map { it.trim() }
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
                boundNoteId = boundNoteId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,  // ✅ 保存到 Bitwarden Vault
                bitwardenFolderId = bitwardenFolderId,
                authenticatorKey = currentAuthKey,  // ✅ 保存验证器密钥
                passkeyBindings = passkeyBindings,
                sshKeyData = existingSshKeyData,
                loginType = loginType,
                ssoProvider = ssoProvider,
                ssoRefEntryId = ssoRefEntryId,
                customIconType = customIconType,
                customIconValue = normalizedIconFileName(customIconValue),
                customIconUpdatedAt = customIconUpdatedAt
            )

            // 快照自定义字段，并追加“用户名分离”内部转换字段（带标记）
            val currentCustomFields = customFields.toMutableList().apply {
                removeAll {
                    it.title == MONICA_USERNAME_ALIAS_FIELD_TITLE ||
                        it.title == MONICA_USERNAME_ALIAS_META_FIELD_TITLE
                }
                val normalizedSeparatedUsername = separatedUsername.trim()
                if (normalizedSeparatedUsername.isNotEmpty()) {
                    add(
                        CustomFieldDraft(
                            id = CustomFieldDraft.nextTempId(),
                            title = MONICA_USERNAME_ALIAS_FIELD_TITLE,
                            value = normalizedSeparatedUsername
                        )
                    )
                    add(
                        CustomFieldDraft(
                            id = CustomFieldDraft.nextTempId(),
                            title = MONICA_USERNAME_ALIAS_META_FIELD_TITLE,
                            value = MONICA_USERNAME_ALIAS_META_VALUE
                        )
                    )
                }
            }

            viewModel.savePasswordsAcrossTargets(
                originalIds = originalIds,
                commonEntry = commonEntry.copy(replicaGroupId = currentReplicaGroupId),
                passwords = normalizedPasswords, // Snapshot (trimmed)
                targets = selectedStorageTargets.toList(),
                customFields = currentCustomFields, // 保存自定义字段
                onComplete = onComplete@{ firstPasswordId ->
                    if (firstPasswordId == null) {
                        isSaving = false
                        Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                        return@onComplete
                    }
                    // Save TOTP if authenticatorKey is provided
                    if (currentAuthKey.isNotEmpty() && totpViewModel != null) {
                        val resolvedAuthTotp = TotpDataResolver.fromAuthenticatorKey(
                            rawKey = currentAuthKey,
                            fallbackIssuer = currentTitle,
                            fallbackAccountName = currentUsername
                        ) ?: TotpData(
                            secret = currentAuthKey,
                            issuer = currentTitle,
                            accountName = currentUsername
                        )

                        // 检查是否已有相同密钥的验证器
                        val existingTotp = totpViewModel.findTotpBySecret(resolvedAuthTotp.secret)
                        val existingTotpData = existingTotp?.let {
                            runCatching { Json.decodeFromString<TotpData>(it.itemData) }.getOrNull()
                        }
                        val totpIdToSave = existingTotp?.id ?: existingTotpId // 优先选择相同密钥的，其次是原本绑定的
                        val baseTotpData = existingTotpData ?: resolvedAuthTotp
                        val totpData = baseTotpData.copy(
                            secret = resolvedAuthTotp.secret,
                            issuer = baseTotpData.issuer.ifBlank {
                                resolvedAuthTotp.issuer.ifBlank { currentTitle }
                            },
                            accountName = baseTotpData.accountName.ifBlank {
                                resolvedAuthTotp.accountName.ifBlank { currentUsername }
                            },
                            otpType = resolvedAuthTotp.otpType,
                            digits = resolvedAuthTotp.digits,
                            period = resolvedAuthTotp.period,
                            algorithm = resolvedAuthTotp.algorithm,
                            counter = resolvedAuthTotp.counter,
                            pin = if (baseTotpData.pin.isNotBlank()) baseTotpData.pin else resolvedAuthTotp.pin,
                            link = if (baseTotpData.link.isNotBlank()) baseTotpData.link else resolvedAuthTotp.link,
                            associatedApp = if (baseTotpData.associatedApp.isNotBlank()) {
                                baseTotpData.associatedApp
                            } else {
                                resolvedAuthTotp.associatedApp
                            },
                            boundPasswordId = firstPasswordId,
                            customIconType = baseTotpData.customIconType,
                            customIconValue = baseTotpData.customIconValue,
                            customIconUpdatedAt = baseTotpData.customIconUpdatedAt
                        )

                        totpViewModel.saveTotpItem(
                            id = totpIdToSave,
                            title = existingTotp?.title ?: currentTitle,
                            notes = existingTotp?.notes ?: "",
                            totpData = totpData,
                            isFavorite = existingTotp?.isFavorite ?: false,
                            keepassDatabaseId = keepassDatabaseId
                        )
                    } else if (currentAuthKey.isEmpty() && originalAuthenticatorKey.isNotEmpty() && totpViewModel != null) {
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
                    val originalUploaded = if (originalCustomIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                        normalizedIconFileName(originalCustomIconValue)
                    } else {
                        null
                    }
                    val currentUploaded = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                        normalizedIconFileName(customIconValue)
                    } else {
                        null
                    }
                    if (!originalUploaded.isNullOrBlank() && originalUploaded != currentUploaded) {
                        PasswordCustomIconStore.deleteIconFile(context, originalUploaded)
                    }
                    hasSavedSuccessfully = true
                    onSaveCompleted?.invoke(firstPasswordId)
                    onNavigateBack()
                }
            )
        }
    }

    LaunchedEffect(
        isEditing,
        currentFilter,
        hasExplicitInitialStorage,
        initialCategoryId,
        initialKeePassDatabaseId,
        initialKeePassGroupPath,
        initialBitwardenVaultId,
        initialBitwardenFolderId
    ) {
        if (isEditing) return@LaunchedEffect
        existingReplicaTargetKeys = emptySet()
        currentReplicaGroupId = null
        if (hasExplicitInitialStorage) {
            setSelectedStorageTargets(
                listOf(
                    buildMultiStorageTarget(
                        categoryId = initialCategoryId,
                        keepassDatabaseId = initialKeePassDatabaseId,
                        keepassGroupPath = initialKeePassGroupPath,
                        bitwardenVaultId = initialBitwardenVaultId,
                        bitwardenFolderId = initialBitwardenFolderId
                    )
                )
            )
            return@LaunchedEffect
        }
        val defaultTarget = when (val filter = currentFilter) {
            is CategoryFilter.Custom -> StorageTarget.MonicaLocal(filter.categoryId)
            is CategoryFilter.KeePassDatabase -> StorageTarget.KeePass(filter.databaseId, null)
            is CategoryFilter.KeePassGroupFilter -> StorageTarget.KeePass(filter.databaseId, filter.groupPath)
            is CategoryFilter.KeePassDatabaseStarred -> StorageTarget.KeePass(filter.databaseId, null)
            is CategoryFilter.KeePassDatabaseUncategorized -> StorageTarget.KeePass(filter.databaseId, null)
            is CategoryFilter.BitwardenVault -> StorageTarget.Bitwarden(filter.vaultId, null)
            is CategoryFilter.BitwardenFolderFilter -> StorageTarget.Bitwarden(filter.vaultId, filter.folderId)
            is CategoryFilter.BitwardenVaultStarred -> StorageTarget.Bitwarden(filter.vaultId, null)
            is CategoryFilter.BitwardenVaultUncategorized -> StorageTarget.Bitwarden(filter.vaultId, null)
            else -> StorageTarget.MonicaLocal(null)
        }
        setSelectedStorageTargets(listOf(defaultTarget))
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
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Vault/Storage Selector - 保管库选择器（类似Bitwarden）
            item {
                MultiStorageTargetSelectorCard(
                    selectedTargets = selectedStorageTargets,
                    existingTargetKeys = existingReplicaTargetKeys,
                    categories = categories,
                    keepassDatabases = keepassDatabases,
                    bitwardenVaults = bitwardenVaults,
                    bitwardenFolderDao = database.bitwardenFolderDao(),
                    isEditing = isEditing,
                    onAddTargetClick = { showStorageTargetSheet = true },
                    onRemoveTarget = ::removeSelectedStorageTarget
                )
            }

            if (hasOwnershipConflict) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.password_owner_conflict_display),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // Credentials Card
            item {
                InfoCard(title = stringResource(R.string.section_credentials)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Title
                        if (settings.iconCardsEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showCustomIconDialog = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    when {
                                        selectedSimpleIconBitmap != null -> {
                                            Image(
                                                bitmap = selectedSimpleIconBitmap,
                                                contentDescription = stringResource(R.string.custom_icon_button),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        selectedUploadedIconBitmap != null -> {
                                            Image(
                                                bitmap = selectedUploadedIconBitmap,
                                                contentDescription = stringResource(R.string.custom_icon_button),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        autoMatchedSimpleIcon.bitmap != null -> {
                                            Image(
                                                bitmap = autoMatchedSimpleIcon.bitmap,
                                                contentDescription = stringResource(R.string.custom_icon_button),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        fallbackWebsiteFavicon != null -> {
                                            Image(
                                                bitmap = fallbackWebsiteFavicon,
                                                contentDescription = stringResource(R.string.custom_icon_button),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = stringResource(R.string.custom_icon_button)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text(stringResource(R.string.title_required)) },
                                    leadingIcon = { Icon(Icons.Default.Label, null) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        } else {
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
                        }

                        // Website + App Binding (inline)
                        var showAppSelectorFromWebsite by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text(stringResource(R.string.website_url)) },
                            leadingIcon = { Icon(Icons.Default.Language, null) },
                            trailingIcon = {
                                IconButton(onClick = { showAppSelectorFromWebsite = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = stringResource(R.string.linked_app),
                                        tint = if (appPackageName.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            },
                            supportingText = if (appPackageName.isNotEmpty()) { {
                                InputChip(
                                    selected = true,
                                    onClick = { showAppSelectorFromWebsite = true },
                                    label = { Text(appName) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_app_selection),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    appPackageName = ""
                                                    appName = ""
                                                }
                                        )
                                    }
                                )
                            } } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (showAppSelectorFromWebsite) {
                            AppSelectorDialog(
                                onDismiss = { showAppSelectorFromWebsite = false },
                                onAppSelected = { packageName, name ->
                                    appPackageName = packageName
                                    appName = name
                                    showAppSelectorFromWebsite = false
                                }
                            )
                        }

                        // Username - 支持常用账号填充
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.field_account)) },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                trailingIcon = {
                                    Row {
                                        // 常用账号填充按钮（新建时可从模板/默认值中选择）
                                        if (canSelectUsernameTemplate) {
                                            IconButton(
                                                onClick = {
                                                    commonAccountSelectorField = "username"
                                                    commonAccountSelectorTargetIndex = -1
                                                    showCommonAccountSelector = true
                                                }
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
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        isUsernameFieldFocused = focusState.isFocused
                                    },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        val usernameSuggestionAnimationSpec = remember {
                            spring<IntSize>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        }
                        AnimatedVisibility(
                            visible = usernameSuggestionVisible,
                            enter = slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 240,
                                    easing = FastOutSlowInEasing
                                ),
                                initialOffsetY = { -it / 2 }
                            ) +
                                fadeIn(animationSpec = tween(180)) +
                                expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = usernameSuggestionAnimationSpec
                                ),
                            exit = slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = 160,
                                    easing = FastOutSlowInEasing
                                ),
                                targetOffsetY = { -it / 4 }
                            ) +
                                fadeOut(animationSpec = tween(120)) +
                                shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                )
                        ) {
                            UsernameSuggestionPanel(
                                state = usernameSuggestionState,
                                onApplySuggestion = { applyCommonAccountSelection("username", it) },
                                modifier = Modifier.bringIntoViewRequester(usernameSuggestionBringIntoViewRequester)
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.separateUsernameAccountEnabled,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                OutlinedTextField(
                                    value = separatedUsername,
                                    onValueChange = { separatedUsername = it },
                                    label = { Text(stringResource(R.string.autofill_username)) },
                                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isSeparatedUsernameFieldFocused = focusState.isFocused
                                        },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                AnimatedVisibility(
                                    visible = separatedUsernameSuggestionVisible,
                                    enter = slideInVertically(
                                        animationSpec = tween(
                                            durationMillis = 240,
                                            easing = FastOutSlowInEasing
                                        ),
                                        initialOffsetY = { -it / 2 }
                                    ) +
                                        fadeIn(animationSpec = tween(180)) +
                                        expandVertically(
                                            expandFrom = Alignment.Top,
                                            animationSpec = usernameSuggestionAnimationSpec
                                        ),
                                    exit = slideOutVertically(
                                        animationSpec = tween(
                                            durationMillis = 160,
                                            easing = FastOutSlowInEasing
                                        ),
                                        targetOffsetY = { -it / 4 }
                                    ) +
                                        fadeOut(animationSpec = tween(120)) +
                                        shrinkVertically(
                                            shrinkTowards = Alignment.Top,
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        )
                                ) {
                                    UsernameSuggestionPanel(
                                        state = separatedUsernameSuggestionState,
                                        onApplySuggestion = { separatedUsername = it },
                                        modifier = Modifier.bringIntoViewRequester(
                                            separatedUsernameSuggestionBringIntoViewRequester
                                        )
                                    )
                                }
                            }
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
                        AnimatedVisibility(
                            visible = loginType.equals("PASSWORD", ignoreCase = true),
                            enter = EnterTransition.None,
                            exit = ExitTransition.None
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                passwords.forEachIndexed { index, pwd ->
                                    val isPasswordVisible = isPasswordFieldVisible(index)
                                    val isUnreadablePassword =
                                        isEditing && originalIds.getOrNull(index) in unreadablePasswordIds
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = pwd,
                                                onValueChange = { passwords[index] = it },
                                                label = { Text(if (passwords.size > 1) stringResource(R.string.password) + " ${index + 1}" else stringResource(R.string.password)) },
                                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    Row {
                                                        IconButton(onClick = { 
                                                            showPasswordGenerator = true 
                                                            currentPasswordIndexForGenerator = index
                                                        }) {
                                                            Icon(
                                                                Icons.Default.Key,
                                                                contentDescription = stringResource(R.string.password_fill_title)
                                                            )
                                                        }
                                                        IconButton(onClick = { togglePasswordFieldVisibility(index) }) {
                                                            Icon(
                                                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                                contentDescription = null
                                                            )
                                                        }
                                                        // Allow removing only if more than 1
                                                        if (passwords.size > 1) {
                                                            IconButton(onClick = { removePasswordFieldAt(index) }) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            focusedPasswordFieldIndex = index
                                                            if (passwords[index].isBlank()) {
                                                                ensureInlinePasswordSuggestion(index)
                                                            }
                                                        } else if (focusedPasswordFieldIndex == index) {
                                                            focusedPasswordFieldIndex = null
                                                        }
                                                    },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                                                shape = RoundedCornerShape(12.dp),
                                                supportingText = if (isUnreadablePassword) {
                                                    {
                                                        Text(stringResource(R.string.bitwarden_password_unreadable_inline))
                                                    }
                                                } else if (hasOwnershipConflict) {
                                                    {
                                                        Text(stringResource(R.string.password_owner_conflict_inline))
                                                    }
                                                } else {
                                                    null
                                                }
                                            )
                                        }

                                        val inlinePasswordSuggestion = inlineGeneratedPasswords[index]
                                        val passwordSuggestionVisible = focusedPasswordFieldIndex == index &&
                                            pwd.isBlank() &&
                                            !inlinePasswordSuggestion.isNullOrBlank()
                                        AnimatedVisibility(
                                            visible = passwordSuggestionVisible,
                                            enter = slideInVertically(
                                                animationSpec = tween(
                                                    durationMillis = 240,
                                                    easing = FastOutSlowInEasing
                                                ),
                                                initialOffsetY = { -it / 2 }
                                            ) +
                                                fadeIn(animationSpec = tween(180)) +
                                                expandVertically(
                                                    expandFrom = Alignment.Top,
                                                    animationSpec = usernameSuggestionAnimationSpec
                                                ),
                                            exit = slideOutVertically(
                                                animationSpec = tween(
                                                    durationMillis = 160,
                                                    easing = FastOutSlowInEasing
                                                ),
                                                targetOffsetY = { -it / 4 }
                                            ) +
                                                fadeOut(animationSpec = tween(120)) +
                                                shrinkVertically(
                                                    shrinkTowards = Alignment.Top,
                                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                                )
                                        ) {
                                            inlinePasswordSuggestion?.let { suggestion ->
                                                InlineGeneratedPasswordSuggestionCard(
                                                    password = suggestion,
                                                    onApply = {
                                                        passwords[index] = suggestion
                                                        inlineGeneratedPasswords.remove(index)
                                                    },
                                                    modifier = Modifier.bringIntoViewRequester(
                                                        passwordSuggestionBringIntoViewRequester
                                                    )
                                                )
                                            }
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
                        Column {
                            OutlinedTextField(
                                value = authenticatorSecret,
                                onValueChange = ::applyAuthenticatorInput,
                                label = { Text(stringResource(R.string.authenticator_key_optional)) },
                                placeholder = { Text(stringResource(R.string.authenticator_key_hint)) },
                                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                supportingText = {
                                    if (selectedAuthenticatorOtpType == OtpType.STEAM) {
                                        Text(stringResource(R.string.steam_uses_5_chars))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = authenticatorTypeExpanded,
                                onExpandedChange = { authenticatorTypeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = when (selectedAuthenticatorOtpType) {
                                        OtpType.STEAM -> stringResource(R.string.otp_type_steam)
                                        else -> stringResource(R.string.otp_type_totp)
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.otp_type)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (selectedAuthenticatorOtpType == OtpType.STEAM) {
                                                Icons.Default.Games
                                            } else {
                                                Icons.Default.Shield
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = authenticatorTypeExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = authenticatorTypeExpanded,
                                    onDismissRequest = { authenticatorTypeExpanded = false }
                                ) {
                                    listOf(
                                        OtpType.TOTP to R.string.otp_type_totp,
                                        OtpType.STEAM to R.string.otp_type_steam
                                    ).forEach { (type, labelRes) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(labelRes)) },
                                            onClick = {
                                                selectedAuthenticatorOtpTypeName = type.name
                                                authenticatorTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (onScanAuthenticatorQrCode != null) {
                                FilledTonalButton(
                                    onClick = onScanAuthenticatorQrCode,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_qr_code))
                                }
                            }

                            AnimatedVisibility(
                                visible = authenticatorPreviewVisible,
                                enter = slideInVertically(
                                    initialOffsetY = { -it / 3 },
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                ) + expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                ),
                                exit = slideOutVertically(
                                    targetOffsetY = { -it / 4 },
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                ) + shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 140)
                                )
                            ) {
                                authenticatorPreviewTotpData?.let { previewData ->
                                    InlineTotpPreviewCard(
                                        totpData = previewData,
                                        currentSeconds = authenticatorPreviewCurrentSeconds,
                                        progressTimeMillis = authenticatorPreviewProgressTimeMillis,
                                        timeOffset = settings.totpTimeOffset,
                                        smoothProgress = settings.validatorSmoothProgress,
                                        modifier = Modifier.padding(top = 10.dp),
                                        showHeader = false,
                                        showProgress = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Organization Card - 根据设置和数据决定是否显示
            if (shouldShowCategoryAndNotes()) {
                item {
                    InfoCard(title = stringResource(R.string.notes)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val selectedNotePreview = remember(selectedBoundNote) {
                                selectedBoundNote?.let { note ->
                                    takagi.ru.monica.notes.domain.NoteContentCodec
                                        .decodeFromItem(note)
                                        .content
                                        .replace("\n", " ")
                                        .trim()
                                }.orEmpty()
                            }

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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = stringResource(R.string.password_bound_note_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedBoundNote != null) {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = selectedBoundNote.title.ifBlank {
                                                        stringResource(R.string.untitled)
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                if (selectedNotePreview.isNotBlank()) {
                                                    Text(
                                                        text = selectedNotePreview,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(onClick = { showBoundNotePicker = true }) {
                                                Text(stringResource(R.string.change_bound_note))
                                            }
                                            TextButton(onClick = { boundNoteId = null }) {
                                                Text(stringResource(R.string.unbind_note))
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showBoundNotePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.bind_note))
                                }
                            }
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
                                    isError = emailValue.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidEmail(emailValue),
                                    trailingIcon = if (canSelectEmailTemplate) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    commonAccountSelectorField = "email"
                                                    commonAccountSelectorTargetIndex = index
                                                    showCommonAccountSelector = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.PersonAdd,
                                                    contentDescription = stringResource(R.string.fill_common_account),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else null
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
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = if (canSelectPhoneTemplate) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    commonAccountSelectorField = "phone"
                                                    commonAccountSelectorTargetIndex = index
                                                    showCommonAccountSelector = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.PersonAdd,
                                                    contentDescription = stringResource(R.string.fill_common_account),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else null
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
            commonPasswordOptions = buildCommonAccountOptions("password"),
            onDismiss = { showPasswordGenerator = false },
            onPasswordGenerated = { generatedPassword ->
                if (currentPasswordIndexForGenerator >= 0 && currentPasswordIndexForGenerator < passwords.size) {
                    passwords[currentPasswordIndexForGenerator] = generatedPassword
                }
                showPasswordGenerator = false
            }
        )
    }

    if (showCustomIconDialog) {
        CustomIconActionDialog(
            showClearAction = customIconType != PASSWORD_ICON_TYPE_NONE,
            onPickFromLibrary = {
                customIconSearchQuery = ""
                showCustomIconDialog = false
                showSimpleIconPicker = true
            },
            onUploadImage = {
                showCustomIconDialog = false
                imagePickerLauncher.launch("image/*")
            },
            onClearIcon = {
                val currentUploaded = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                    normalizedIconFileName(customIconValue)
                } else {
                    null
                }
                if (!currentUploaded.isNullOrBlank() && !isOriginalUploadedIconFile(currentUploaded)) {
                    PasswordCustomIconStore.deleteIconFile(context, currentUploaded)
                }
                customIconType = PASSWORD_ICON_TYPE_NONE
                customIconValue = null
                customIconUpdatedAt = System.currentTimeMillis()
                showCustomIconDialog = false
            },
            onDismissRequest = { showCustomIconDialog = false }
        )
    }

    if (showSimpleIconPicker) {
        var iconVisibleCount by rememberSaveable { mutableStateOf(ICON_PICKER_PAGE_SIZE) }
        val iconOptions = remember(context, customIconSearchQuery) {
            SimpleIconCatalog.search(context, customIconSearchQuery)
        }
        LaunchedEffect(customIconSearchQuery, showSimpleIconPicker) {
            if (showSimpleIconPicker) {
                iconVisibleCount = ICON_PICKER_PAGE_SIZE
            }
        }
        val visibleOptions = remember(iconOptions, iconVisibleCount) {
            iconOptions.take(iconVisibleCount.coerceAtMost(iconOptions.size))
        }
        SimpleIconPickerBottomSheet(
            searchQuery = customIconSearchQuery,
            onSearchQueryChange = {
                customIconSearchQuery = it
                iconVisibleCount = ICON_PICKER_PAGE_SIZE
            },
            iconOptions = iconOptions,
            visibleOptions = visibleOptions,
            hasMore = visibleOptions.size < iconOptions.size,
            remainingCount = iconOptions.size - visibleOptions.size,
            iconCardsEnabled = settings.iconCardsEnabled,
            selectedSlug = if (customIconType == PASSWORD_ICON_TYPE_SIMPLE) customIconValue else null,
            onSelectOption = { option ->
                val currentUploaded = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                    normalizedIconFileName(customIconValue)
                } else {
                    null
                }
                if (!currentUploaded.isNullOrBlank() && !isOriginalUploadedIconFile(currentUploaded)) {
                    PasswordCustomIconStore.deleteIconFile(context, currentUploaded)
                }
                customIconType = PASSWORD_ICON_TYPE_SIMPLE
                customIconValue = option.slug
                customIconUpdatedAt = System.currentTimeMillis()
                showSimpleIconPicker = false
            },
            onLoadMore = {
                iconVisibleCount = (iconVisibleCount + ICON_PICKER_PAGE_SIZE)
                    .coerceAtMost(iconOptions.size)
            },
            onDismissRequest = { showSimpleIconPicker = false }
        )
    }

    if (showCommonAccountSelector) {
        val selectorOptions = buildCommonAccountOptions(commonAccountSelectorField)
        val allFilterLabel = stringResource(R.string.filter_all)
        val selectorFieldLabel = when (commonAccountSelectorField) {
            "username" -> stringResource(R.string.field_account)
            "email" -> stringResource(R.string.field_email)
            "phone" -> stringResource(R.string.field_phone)
            "password" -> stringResource(R.string.password)
            else -> ""
        }
        val availableTypeFilters = remember(selectorOptions, allFilterLabel) {
            buildList {
                add(allFilterLabel)
                addAll(selectorOptions.map { it.type }.distinct())
            }
        }
        var selectedTypeFilter by remember(commonAccountSelectorField) {
            mutableStateOf(allFilterLabel)
        }
        val filteredSelectorOptions = remember(selectorOptions, selectedTypeFilter, allFilterLabel) {
            if (selectedTypeFilter == allFilterLabel) {
                selectorOptions
            } else {
                selectorOptions.filter { it.type == selectedTypeFilter }
            }
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun dismissCommonAccountSelector(afterDismiss: (() -> Unit)? = null) {
            coroutineScope.launch {
                if (sheetState.isVisible) {
                    sheetState.hide()
                }
                showCommonAccountSelector = false
                commonAccountSelectorTargetIndex = -1
                afterDismiss?.invoke()
            }
        }

        ModalBottomSheet(
            onDismissRequest = { dismissCommonAccountSelector() },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.fill_common_account),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectorFieldLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { dismissCommonAccountSelector() }
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }

                if (selectorOptions.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = stringResource(R.string.no_results),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTypeFilters.forEach { typeFilter ->
                            FilterChip(
                                selected = selectedTypeFilter == typeFilter,
                                onClick = { selectedTypeFilter = typeFilter },
                                label = { Text(typeFilter) }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredSelectorOptions, key = { it.id }) { option ->
                            val typeIcon = when (option.type) {
                                commonAccountTypeEmail -> MonicaIcons.General.email
                                commonAccountTypePassword -> Icons.Default.Lock
                                commonAccountTypePhone -> MonicaIcons.General.phone
                                else -> Icons.Default.Person
                            }

                            Surface(
                                onClick = {
                                    dismissCommonAccountSelector {
                                        applyCommonAccountSelection(commonAccountSelectorField, option.content)
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Icon(
                                                    imageVector = typeIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(6.dp).size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Text(
                                                text = option.type,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        SuggestionChip(
                                            onClick = { },
                                            enabled = false,
                                            label = { Text(option.type) }
                                        )
                                    }
                                    Text(
                                        text = option.content,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    MultiStorageTargetPickerBottomSheet(
        visible = showStorageTargetSheet,
        selectedTargets = selectedStorageTargets.toList(),
        lockedTargetKeys = existingReplicaTargetKeys,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = localKeePassViewModel?.let { keepassVm -> keepassVm::getGroups }
            ?: { flowOf(emptyList<takagi.ru.monica.utils.KeePassGroupInfo>()) },
        onDismiss = { showStorageTargetSheet = false },
        onSelectedTargetsChange = ::setSelectedStorageTargets
    )

    NotePickerBottomSheet(
        visible = showBoundNotePicker,
        title = stringResource(R.string.note_picker_title),
        notes = selectableNotes,
        selectedNoteId = boundNoteId,
        onSelect = { note ->
            boundNoteId = note.id
            showBoundNotePicker = false
        },
        onDismiss = { showBoundNotePicker = false }
    )

}
@Composable
private fun InlineGeneratedPasswordSuggestionCard(
    password: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    InlinePrimarySuggestionCard(
        label = password,
        leadingIcon = Icons.Default.Key,
        onClick = onApply,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun InlinePrimarySuggestionCard(
    label: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildPasswordScreenInlinePreviewTotpData(
    rawKey: String,
    issuer: String,
    accountName: String
): TotpData? {
    return TotpDataResolver.fromAuthenticatorKey(
        rawKey = rawKey,
        fallbackIssuer = issuer,
        fallbackAccountName = accountName
    )
}

private data class PasswordScreenAuthenticatorDraft(
    val secret: String,
    val otpType: OtpType
)

private fun resolvePasswordScreenAuthenticatorDraft(
    rawKey: String,
    fallbackIssuer: String = "",
    fallbackAccountName: String = ""
): PasswordScreenAuthenticatorDraft {
    val resolved = TotpDataResolver.fromAuthenticatorKey(
        rawKey = rawKey,
        fallbackIssuer = fallbackIssuer,
        fallbackAccountName = fallbackAccountName
    )
    return if (resolved != null) {
        PasswordScreenAuthenticatorDraft(
            secret = resolved.secret,
            otpType = resolved.otpType
        )
    } else {
        PasswordScreenAuthenticatorDraft(
            secret = rawKey.trim(),
            otpType = OtpType.TOTP
        )
    }
}

private fun buildPasswordScreenAuthenticatorPayload(
    secret: String,
    otpType: OtpType,
    issuer: String,
    accountName: String
): String {
    val normalizedSecret = TotpDataResolver.normalizeBase32Secret(secret)
    if (normalizedSecret.isBlank()) return ""
    if (otpType == OtpType.TOTP) return normalizedSecret

    return TotpDataResolver.toBitwardenPayload(
        title = issuer,
        data = TotpData(
            secret = normalizedSecret,
            issuer = issuer.trim(),
            accountName = accountName.trim(),
            otpType = otpType,
            digits = if (otpType == OtpType.STEAM) 5 else 6,
            period = 30
        )
    )
}

private fun OtpType.toPasswordScreenOtpType(): OtpType {
    return if (this == OtpType.STEAM) OtpType.STEAM else OtpType.TOTP
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
                enter = EnterTransition.None,
                exit = ExitTransition.None
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

@Composable
private fun PasswordGeneratorDialog(
    commonPasswordOptions: List<CommonAccountFillOption>,
    onDismiss: () -> Unit,
    onPasswordGenerated: (String) -> Unit
) {
    val passwordGenerator = remember { PasswordGenerator() }
    val configuration = LocalConfiguration.current
    val contentViewportHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp * 0.46f).coerceIn(280.dp, 420.dp)
    }
    val generatorScrollState = rememberScrollState()
    var length by remember { mutableStateOf(16) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var excludeSimilar by remember { mutableStateOf(true) }
    var generatedPassword by remember { mutableStateOf("") }
    var fillMode by remember { mutableStateOf(PasswordFillMode.GENERATOR) }
    
    // Helper to generate
    fun generate() {
        try {
            generatedPassword = passwordGenerator.generatePassword(
                PasswordGenerator.PasswordOptions(length, includeUppercase, includeLowercase, includeNumbers, includeSymbols, excludeSimilar)
            )
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) { generate() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp).size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.password_fill_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.password_fill_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = fillMode == PasswordFillMode.GENERATOR,
                        onClick = { fillMode = PasswordFillMode.GENERATOR },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.password_fill_mode_generator))
                    }
                    SegmentedButton(
                        selected = fillMode == PasswordFillMode.COMMON_ACCOUNT,
                        onClick = { fillMode = PasswordFillMode.COMMON_ACCOUNT },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.password_fill_mode_common))
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentViewportHeight),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent
                ) {
                    when (fillMode) {
                        PasswordFillMode.GENERATOR -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(generatorScrollState),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = generatedPassword,
                                    onValueChange = { },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 0.2.sp
                                    ),
                                    trailingIcon = {
                                        FilledTonalIconButton(onClick = { generate() }) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.generate_password)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.length_value, length),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Slider(
                                            value = length.toFloat(),
                                            onValueChange = { length = it.toInt(); generate() },
                                            valueRange = 8f..32f,
                                            steps = 24
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Column {
                                        PasswordFillOptionRow(
                                            title = stringResource(R.string.uppercase_az),
                                            checked = includeUppercase,
                                            onChange = { includeUppercase = it; generate() },
                                            showDivider = true
                                        )
                                        PasswordFillOptionRow(
                                            title = stringResource(R.string.lowercase_az),
                                            checked = includeLowercase,
                                            onChange = { includeLowercase = it; generate() },
                                            showDivider = true
                                        )
                                        PasswordFillOptionRow(
                                            title = stringResource(R.string.numbers_09),
                                            checked = includeNumbers,
                                            onChange = { includeNumbers = it; generate() },
                                            showDivider = true
                                        )
                                        PasswordFillOptionRow(
                                            title = stringResource(R.string.symbols),
                                            checked = includeSymbols,
                                            onChange = { includeSymbols = it; generate() },
                                            showDivider = true
                                        )
                                        PasswordFillOptionRow(
                                            title = stringResource(R.string.exclude_similar),
                                            checked = excludeSimilar,
                                            onChange = { excludeSimilar = it; generate() },
                                            showDivider = false
                                        )
                                    }
                                }
                            }
                        }

                        PasswordFillMode.COMMON_ACCOUNT -> {
                            if (commonPasswordOptions.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.password_fill_no_templates),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.password_fill_no_templates_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(commonPasswordOptions, key = { it.id }) { option ->
                                        OutlinedCard(
                                            onClick = { onPasswordGenerated(option.content) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        modifier = Modifier.padding(6.dp).size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = option.type,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = maskSensitiveContent(option.content),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(R.string.use_password),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    if (fillMode == PasswordFillMode.GENERATOR) {
                        FilledTonalButton(onClick = { onPasswordGenerated(generatedPassword) }) {
                            Text(stringResource(R.string.use_password))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordFillOptionRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Checkbox(
                checked = checked,
                onCheckedChange = onChange
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}

private fun maskSensitiveContent(content: String): String {
    val value = content.trim()
    if (value.isEmpty()) return ""
    if (value.length <= 2) return "•".repeat(value.length)
    return value.first() + "•".repeat((value.length - 2).coerceAtLeast(0)) + value.last()
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
                selected = loginType.equals("PASSWORD", ignoreCase = true),
                onClick = { onLoginTypeChange("PASSWORD") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { 
                    if (loginType.equals("PASSWORD", ignoreCase = true)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    }
                }
            ) {
                Text(context.getString(R.string.login_type_password))
            }
            SegmentedButton(
                selected = loginType.equals("SSO", ignoreCase = true),
                onClick = { onLoginTypeChange("SSO") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { 
                    if (loginType.equals("SSO", ignoreCase = true)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    }
                }
            ) {
                Text(context.getString(R.string.login_type_sso))
            }
        }
        
        // SSO 详细设置
        AnimatedVisibility(
            visible = loginType.equals("SSO", ignoreCase = true),
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
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
    PasswordEntryPickerBottomSheet(
        visible = showRefEntryPicker,
        title = stringResource(R.string.sso_ref_entry_picker_title),
        passwords = allPasswords.filter {
            it.loginType.equals("PASSWORD", ignoreCase = true) &&
                it.id != ssoRefEntryId &&
                !it.isDeleted &&
                !it.isArchived
        },
        selectedEntryId = ssoRefEntryId,
        onSelect = { entry ->
            onSsoRefEntryIdChange(entry.id)
            showRefEntryPicker = false
        },
        onDismiss = { showRefEntryPicker = false }
    )
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

private fun buildPasswordSiblingGroupKey(entry: PasswordEntry): String {
    val sourceKey = when {
        !entry.bitwardenCipherId.isNullOrBlank() ->
            "bw:${entry.bitwardenVaultId}:${entry.bitwardenCipherId}"
        entry.bitwardenVaultId != null ->
            "bw-local:${entry.bitwardenVaultId}:${entry.bitwardenFolderId.orEmpty()}"
        entry.keepassDatabaseId != null ->
            "kp:${entry.keepassDatabaseId}:${entry.keepassGroupPath.orEmpty()}"
        else -> "local"
    }

    val title = entry.title.trim().lowercase(Locale.ROOT)
    val username = entry.username.trim().lowercase(Locale.ROOT)
    val website = normalizeWebsiteForSiblingGroupKey(entry.website)

    return "$sourceKey|$title|$website|$username"
}

private fun normalizeWebsiteForSiblingGroupKey(value: String): String {
    val raw = value.trim()
    if (raw.isEmpty()) return ""
    return raw
        .lowercase(Locale.ROOT)
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .trimEnd('/')
}

