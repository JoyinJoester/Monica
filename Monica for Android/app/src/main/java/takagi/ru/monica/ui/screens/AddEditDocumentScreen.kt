package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.CommonAccountPreferences
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.displayFullName
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.ui.components.CommonNameSuggestionSheet
import takagi.ru.monica.ui.components.CustomFieldEditorSection
import takagi.ru.monica.ui.components.DualPhotoPicker
import takagi.ru.monica.ui.components.MultiStorageTargetPickerBottomSheet
import takagi.ru.monica.ui.components.MultiStorageTargetSelectorCard
import takagi.ru.monica.ui.components.buildMultiStorageTarget
import takagi.ru.monica.ui.components.rememberCommonNameSuggestionState
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.RememberedStorageTarget
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.ui.components.OutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDocumentScreen(
    viewModel: DocumentViewModel,
    documentId: Long? = null,
    onNavigateBack: () -> Unit,
    initialCategoryId: Long? = null,
    initialKeePassDatabaseId: Long? = null,
    initialKeePassGroupPath: String? = null,
    initialBitwardenVaultId: Long? = null,
    initialBitwardenFolderId: String? = null,
    showTypeSwitcher: Boolean = false,
    onSwitchToBankCard: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    showFab: Boolean = true,
    onFavoriteStateChanged: ((Boolean) -> Unit)? = null,
    onCanSaveChanged: ((Boolean) -> Unit)? = null,
    onSaveActionChanged: (((() -> Unit)) -> Unit)? = null,
    onToggleFavoriteActionChanged: (((() -> Unit)) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { PasswordDatabase.getDatabase(context) }
    val securityManager = remember { SecurityManager(context) }
    val bitwardenSyncViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val localKeePassViewModel: LocalKeePassViewModel = viewModel {
        LocalKeePassViewModel(
            context.applicationContext as android.app.Application,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val settingsManager = remember { SettingsManager(context) }
    val commonAccountPreferences = remember { CommonAccountPreferences(context) }
    
    var title by rememberSaveable { mutableStateOf("") }
    var documentNumber by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var issuedDate by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    var issuedBy by rememberSaveable { mutableStateOf("") }
    var nationality by rememberSaveable { mutableStateOf("") } // 添加国籍字段
    var titlePrefix by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var middleName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var address1 by rememberSaveable { mutableStateOf("") }
    var address2 by rememberSaveable { mutableStateOf("") }
    var address3 by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateProvince by rememberSaveable { mutableStateOf("") }
    var postalCode by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var ssn by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var passportNumber by rememberSaveable { mutableStateOf("") }
    var licenseNumber by rememberSaveable { mutableStateOf("") }
    var additionalInfo by rememberSaveable { mutableStateOf("") }
    var documentType by rememberSaveable { mutableStateOf(DocumentType.ID_CARD) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var showDocumentTypeMenu by remember { mutableStateOf(false) }
    var customFields by remember { mutableStateOf<List<CustomFieldDraft>>(emptyList()) }
    var identityDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var addressDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var showCommonNamePicker by rememberSaveable { mutableStateOf(false) }
    
    // 防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }
    var showDocumentNumber by remember { mutableStateOf(false) }
    
    // 图片路径管理
    var frontImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var backImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var keepassDatabaseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var keepassGroupPath by rememberSaveable { mutableStateOf<String?>(null) }
    var bitwardenVaultId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bitwardenFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAppliedInitialStorage by rememberSaveable { mutableStateOf(false) }
    val selectedStorageTargets = remember { mutableStateListOf<StorageTarget>() }
    var existingReplicaTargetKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentReplicaGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showStorageTargetSheet by remember { mutableStateOf(false) }
    var hasLoadedExistingDocumentFields by rememberSaveable(documentId) { mutableStateOf(false) }
    val commonNameSuggestions = rememberCommonNameSuggestionState(database)
    val commonNameType = stringResource(R.string.common_account_type_name)
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenVaults by database.bitwardenVaultDao().getAllVaultsFlow().collectAsState(initial = emptyList())
    val allDocuments by viewModel.allDocuments.collectAsState(initial = emptyList())
    val rememberedStorageTarget by settingsManager
        .rememberedStorageTargetFlow(SettingsManager.StorageTargetScope.DOCUMENT)
        .collectAsState(initial = null as RememberedStorageTarget?)
    val cardWalletCategoryFilterState by settingsManager
        .categoryFilterStateFlow(SettingsManager.CategoryFilterScope.CARD_WALLET)
        .collectAsState(initial = null)

    fun syncLegacyStorageState(targets: List<StorageTarget>) {
        when (val primaryTarget = targets.firstOrNull()) {
            is StorageTarget.MonicaLocal -> {
                selectedCategoryId = primaryTarget.categoryId
                keepassDatabaseId = null
                keepassGroupPath = null
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is StorageTarget.KeePass -> {
                selectedCategoryId = null
                keepassDatabaseId = primaryTarget.databaseId
                keepassGroupPath = primaryTarget.groupPath
                bitwardenVaultId = null
                bitwardenFolderId = null
            }
            is StorageTarget.Bitwarden -> {
                selectedCategoryId = null
                keepassDatabaseId = null
                keepassGroupPath = null
                bitwardenVaultId = primaryTarget.vaultId
                bitwardenFolderId = primaryTarget.folderId
            }
            null -> {
                selectedCategoryId = null
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

    LaunchedEffect(
        documentId,
        hasAppliedInitialStorage,
        initialCategoryId,
        initialKeePassDatabaseId,
        initialKeePassGroupPath,
        initialBitwardenVaultId,
        initialBitwardenFolderId,
        rememberedStorageTarget,
        cardWalletCategoryFilterState
    ) {
        if (documentId != null || hasAppliedInitialStorage) return@LaunchedEffect
        val remembered = rememberedStorageTarget
        val explicitGroupPath = initialKeePassGroupPath?.takeIf { it.isNotBlank() }
        val explicitFolderId = initialBitwardenFolderId?.takeIf { it.isNotBlank() }
        val hasExplicitInitialStorage = initialCategoryId != null ||
            initialKeePassDatabaseId != null ||
            explicitGroupPath != null ||
            initialBitwardenVaultId != null ||
            explicitFolderId != null
        val filterKeepassDatabaseId = when (cardWalletCategoryFilterState?.type) {
            "keepass_database", "keepass_group", "keepass_database_starred", "keepass_database_uncategorized" ->
                cardWalletCategoryFilterState?.primaryId
            else -> null
        }
        val filterKeepassGroupPath = if (cardWalletCategoryFilterState?.type == "keepass_group") {
            cardWalletCategoryFilterState?.text
        } else {
            null
        }
        if (!hasExplicitInitialStorage && remembered == null && filterKeepassDatabaseId == null && filterKeepassGroupPath == null) {
            setSelectedStorageTargets(listOf(StorageTarget.MonicaLocal(null)))
            return@LaunchedEffect
        }
        selectedCategoryId = if (hasExplicitInitialStorage) {
            initialCategoryId
        } else {
            remembered?.categoryId
        }
        keepassDatabaseId = if (hasExplicitInitialStorage) {
            initialKeePassDatabaseId
        } else {
            filterKeepassDatabaseId ?: remembered?.keepassDatabaseId
        }
        keepassGroupPath = if (hasExplicitInitialStorage) {
            explicitGroupPath
        } else {
            filterKeepassGroupPath ?: remembered?.keepassGroupPath
        }
        bitwardenVaultId = if (hasExplicitInitialStorage) {
            initialBitwardenVaultId
        } else {
            remembered?.bitwardenVaultId
        }
        bitwardenFolderId = if (hasExplicitInitialStorage) {
            explicitFolderId
        } else {
            remembered?.bitwardenFolderId
        }
        setSelectedStorageTargets(
            listOf(
                buildMultiStorageTarget(
                    categoryId = selectedCategoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    keepassGroupPath = keepassGroupPath,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId
                )
            )
        )
        hasAppliedInitialStorage = true
    }
    
    // 如果是编辑模式，加载现有数据
    // 如果是添加模式，重置表单字段（防止保留上次添加的数据）
    LaunchedEffect(documentId) {
        if (documentId != null) {
            if (hasLoadedExistingDocumentFields) return@LaunchedEffect
            viewModel.getDocumentById(documentId)?.let { item ->
                title = item.title
                notes = item.notes
                isFavorite = item.isFavorite
                selectedCategoryId = item.categoryId
                keepassDatabaseId = item.keepassDatabaseId
                keepassGroupPath = item.keepassGroupPath
                bitwardenVaultId = item.bitwardenVaultId
                bitwardenFolderId = item.bitwardenFolderId
                currentReplicaGroupId = item.replicaGroupId
                
                // 解析图片路径
                try {
                    if (item.imagePaths.isNotBlank()) {
                        val pathsList = Json.decodeFromString<List<String>>(item.imagePaths)
                        if (pathsList.isNotEmpty() && pathsList[0].isNotBlank()) {
                            frontImageFileName = pathsList[0]
                        }
                        if (pathsList.size > 1 && pathsList[1].isNotBlank()) {
                            backImageFileName = pathsList[1]
                        }
                    }
                } catch (e: Exception) {
                    // 忽略解析错误
                }
                
                viewModel.parseDocumentData(item.itemData)?.let { data -> 
                    documentNumber = data.documentNumber
                    fullName = data.displayFullName()
                    issuedDate = data.issuedDate
                    expiryDate = data.expiryDate
                    issuedBy = data.issuedBy
                    nationality = data.nationality
                    titlePrefix = data.title
                    firstName = data.firstName
                    middleName = data.middleName
                    lastName = data.lastName
                    address1 = data.address1
                    address2 = data.address2
                    address3 = data.address3
                    city = data.city
                    stateProvince = data.stateProvince
                    postalCode = data.postalCode
                    country = data.country
                    company = data.company
                    email = data.email
                    phone = data.phone
                    ssn = data.ssn
                    username = data.username
                    passportNumber = data.passportNumber
                    licenseNumber = data.licenseNumber
                    additionalInfo = data.additionalInfo
                    customFields = CardWalletDataCodec.customFieldsToDrafts(data.customFields)
                    documentType = data.documentType
                }

                setSelectedStorageTargets(listOf(item.toStorageTarget()))
                hasLoadedExistingDocumentFields = true
            }
        } else {
            hasLoadedExistingDocumentFields = false
            currentReplicaGroupId = null
            existingReplicaTargetKeys = emptySet()
            // 添加模式：重置表单字段
            title = ""
            documentNumber = ""
            fullName = ""
            issuedDate = ""
            expiryDate = ""
            issuedBy = ""
            nationality = ""
            titlePrefix = ""
            firstName = ""
            middleName = ""
            lastName = ""
            address1 = ""
            address2 = ""
            address3 = ""
            city = ""
            stateProvince = ""
            postalCode = ""
            country = ""
            company = ""
            email = ""
            phone = ""
            ssn = ""
            username = ""
            passportNumber = ""
            licenseNumber = ""
            additionalInfo = ""
            documentType = DocumentType.ID_CARD
            notes = ""
            isFavorite = false
            customFields = emptyList()
            frontImageFileName = null
            backImageFileName = null
        }
    }

    LaunchedEffect(documentId, allDocuments, currentReplicaGroupId, hasLoadedExistingDocumentFields) {
        if (documentId == null || !hasLoadedExistingDocumentFields) return@LaunchedEffect
        val currentItem = viewModel.getDocumentById(documentId) ?: return@LaunchedEffect
        val selectedTargets = if (!currentReplicaGroupId.isNullOrBlank()) {
            allDocuments
                .filter { replica ->
                    replica.replicaGroupId == currentReplicaGroupId && !replica.isDeleted
                }
                .map { it.toStorageTarget() }
                .distinctBy(StorageTarget::stableKey)
                .ifEmpty { listOf(currentItem.toStorageTarget()) }
        } else {
            listOf(currentItem.toStorageTarget())
        }
        setSelectedStorageTargets(selectedTargets)
        existingReplicaTargetKeys = selectedTargets.map(StorageTarget::stableKey).toSet()
    }

    val canSave = documentNumber.isNotBlank() && !isSaving
    val save: () -> Unit = saveAction@{
        if (isSaving || documentNumber.isBlank()) return@saveAction
        isSaving = true // 防止重复点击
        val effectiveTargets = selectedStorageTargets.toList().ifEmpty {
            listOf(
                buildMultiStorageTarget(
                    categoryId = selectedCategoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    keepassGroupPath = keepassGroupPath,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId
                )
            )
        }
        val primaryTarget = effectiveTargets.first()
        val syncVaultIds = effectiveTargets
            .filterIsInstance<StorageTarget.Bitwarden>()
            .map { it.vaultId }
            .distinct()

        val resolvedFullName = listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { fullName }
        val documentData = DocumentData(
            documentNumber = documentNumber,
            fullName = resolvedFullName,
            issuedDate = issuedDate,
            expiryDate = expiryDate,
            issuedBy = issuedBy,
            nationality = nationality, // 保存国籍信息
            documentType = documentType,
            additionalInfo = additionalInfo,
            title = titlePrefix,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            address1 = address1,
            address2 = address2,
            address3 = address3,
            city = city,
            stateProvince = stateProvince,
            postalCode = postalCode,
            country = country,
            company = company,
            email = email,
            phone = phone,
            ssn = ssn,
            username = username,
            passportNumber = passportNumber,
            licenseNumber = licenseNumber,
            customFields = CardWalletDataCodec.draftsToCustomFields(customFields)
        )

        val imagePathsList = listOf(
            frontImageFileName ?: "",
            backImageFileName ?: ""
        )
        val imagePathsJson = Json.encodeToString(imagePathsList)

        viewModel.saveDocumentAcrossTargets(
            id = documentId,
            title = title.ifBlank {
                when (documentType) {
                    DocumentType.ID_CARD -> context.getString(R.string.id_card)
                    DocumentType.PASSPORT -> context.getString(R.string.passport)
                    DocumentType.DRIVER_LICENSE -> context.getString(R.string.drivers_license)
                    DocumentType.SOCIAL_SECURITY -> context.getString(R.string.social_security_card)
                    DocumentType.OTHER -> context.getString(R.string.other_document)
                }
            },
            documentData = documentData,
            notes = notes,
            isFavorite = isFavorite,
            imagePaths = imagePathsJson,
            targets = effectiveTargets
        )
        coroutineScope.launch {
            settingsManager.updateRememberedStorageTarget(
                scope = SettingsManager.StorageTargetScope.DOCUMENT,
                target = RememberedStorageTarget(
                    categoryId = (primaryTarget as? StorageTarget.MonicaLocal)?.categoryId,
                    keepassDatabaseId = (primaryTarget as? StorageTarget.KeePass)?.databaseId,
                    keepassGroupPath = (primaryTarget as? StorageTarget.KeePass)?.groupPath,
                    bitwardenVaultId = (primaryTarget as? StorageTarget.Bitwarden)?.vaultId,
                    bitwardenFolderId = (primaryTarget as? StorageTarget.Bitwarden)?.folderId
                )
            )
        }
        syncVaultIds.forEach(bitwardenSyncViewModel::requestLocalMutationSync)
        onNavigateBack()
    }
    val toggleFavoriteAction: () -> Unit = {
        val updated = !isFavorite
        isFavorite = updated
        onFavoriteStateChanged?.invoke(updated)
    }

    SideEffect {
        onFavoriteStateChanged?.invoke(isFavorite)
        onCanSaveChanged?.invoke(canSave)
        onSaveActionChanged?.invoke(save)
        onToggleFavoriteActionChanged?.invoke(toggleFavoriteAction)
    }
    val screenContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MultiStorageTargetSelectorCard(
                selectedTargets = selectedStorageTargets,
                existingTargetKeys = existingReplicaTargetKeys,
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                bitwardenFolderDao = database.bitwardenFolderDao(),
                isEditing = documentId != null,
                onAddTargetClick = { showStorageTargetSheet = true },
                onRemoveTarget = ::removeSelectedStorageTarget
            )

            // Basic Info
            InfoCard(title = stringResource(R.string.section_basic_info)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.document_name)) },
                        placeholder = { Text(stringResource(R.string.document_name_example)) },
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Document Type
                    ExposedDropdownMenuBox(
                        expanded = showDocumentTypeMenu,
                        onExpandedChange = { showDocumentTypeMenu = it }
                    ) {
                        OutlinedTextField(
                            value = when (documentType) {
                                DocumentType.ID_CARD -> stringResource(R.string.id_card)
                                DocumentType.PASSPORT -> stringResource(R.string.passport)
                                DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                                DocumentType.SOCIAL_SECURITY -> stringResource(R.string.social_security_card)
                                DocumentType.OTHER -> stringResource(R.string.other_document)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.document_type)) },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDocumentTypeMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = showDocumentTypeMenu,
                            onDismissRequest = { showDocumentTypeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.id_card)) },
                                onClick = {
                                    documentType = DocumentType.ID_CARD
                                    showDocumentTypeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.passport)) },
                                onClick = {
                                    documentType = DocumentType.PASSPORT
                                    showDocumentTypeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.drivers_license)) },
                                onClick = {
                                    documentType = DocumentType.DRIVER_LICENSE
                                    showDocumentTypeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.social_security_card)) },
                                onClick = {
                                    documentType = DocumentType.SOCIAL_SECURITY
                                    showDocumentTypeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.other_document)) },
                                onClick = {
                                    documentType = DocumentType.OTHER
                                    showDocumentTypeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                            )
                        }
                    }
                    
                    // Document Number
                    OutlinedTextField(
                        value = documentNumber,
                        onValueChange = { documentNumber = it },
                        label = { Text(stringResource(R.string.document_number_required)) },
                        placeholder = { Text(when (documentType) {
                            DocumentType.ID_CARD -> "110101199001011234"
                            DocumentType.PASSPORT -> "E12345678"
                            DocumentType.DRIVER_LICENSE -> "123456789012"
                            DocumentType.SOCIAL_SECURITY -> "1234567890"
                            DocumentType.OTHER -> stringResource(R.string.document_number_required)
                        }) },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDocumentNumber = !showDocumentNumber }) {
                                Icon(
                                    if (showDocumentNumber) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showDocumentNumber) stringResource(R.string.hide) else stringResource(R.string.show)
                                )
                            }
                        },
                        visualTransformation = if (showDocumentNumber) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(stringResource(R.string.full_name)) },
                        placeholder = { Text(stringResource(R.string.holder_name_example)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = {
                            if (commonNameSuggestions.hasAny || fullName.isNotBlank()) {
                                IconButton(onClick = { showCommonNamePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = stringResource(R.string.common_name_fill_title),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = issuedDate,
                            onValueChange = { issuedDate = it },
                            label = { Text(stringResource(R.string.issue_date)) },
                            placeholder = { Text("2020/01/01") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { expiryDate = it },
                            label = { Text(stringResource(R.string.expiry_date_label)) },
                            placeholder = { Text("2030/01/01") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    // Issuing Authority
                    OutlinedTextField(
                        value = issuedBy,
                        onValueChange = { issuedBy = it },
                        label = { Text(stringResource(R.string.issuing_authority)) },
                        placeholder = { Text(stringResource(R.string.issuing_authority_example)) },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Nationality (Passport only)
                    if (documentType == DocumentType.PASSPORT) {
                        OutlinedTextField(
                            value = nationality,
                            onValueChange = { nationality = it },
                            label = { Text(stringResource(R.string.nationality)) },
                            placeholder = { Text(stringResource(R.string.nationality_example)) },
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            ExpandableSectionCard(
                title = stringResource(R.string.document_identity_extended_title),
                icon = Icons.Default.Badge,
                expanded = identityDetailsExpanded,
                onExpandedChange = { identityDetailsExpanded = it }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = titlePrefix, onValueChange = { titlePrefix = it }, label = { Text(stringResource(R.string.document_title_prefix_label)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text(stringResource(R.string.document_first_name_label)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = middleName, onValueChange = { middleName = it }, label = { Text(stringResource(R.string.document_middle_name_label)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text(stringResource(R.string.document_last_name_label)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text(stringResource(R.string.document_company_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.username)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.document_phone_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(12.dp))
            }

            ExpandableSectionCard(
                title = stringResource(R.string.document_address_extra_title),
                icon = Icons.Default.Home,
                expanded = addressDetailsExpanded,
                onExpandedChange = { addressDetailsExpanded = it }
            ) {
                OutlinedTextField(value = address1, onValueChange = { address1 = it }, label = { Text(stringResource(R.string.document_address_line_1)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = address2, onValueChange = { address2 = it }, label = { Text(stringResource(R.string.document_address_line_2)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = address3, onValueChange = { address3 = it }, label = { Text(stringResource(R.string.document_address_line_3)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.city)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = stateProvince, onValueChange = { stateProvince = it }, label = { Text(stringResource(R.string.state)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = postalCode, onValueChange = { postalCode = it }, label = { Text(stringResource(R.string.postal_code)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text(stringResource(R.string.country)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                OutlinedTextField(value = passportNumber, onValueChange = { passportNumber = it }, label = { Text(stringResource(R.string.document_passport_number_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = { Text(stringResource(R.string.document_license_number_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = ssn, onValueChange = { ssn = it }, label = { Text(stringResource(R.string.document_ssn_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = additionalInfo, onValueChange = { additionalInfo = it }, label = { Text(stringResource(R.string.document_additional_info_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp))
            }

            InfoCard(title = stringResource(R.string.custom_field_title)) {
                CustomFieldEditorSection(
                    fields = customFields,
                    onFieldsChange = { customFields = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Photos InfoCard
            InfoCard(title = stringResource(R.string.section_photos)) {
                DualPhotoPicker(
                    frontImageFileName = frontImageFileName,
                    backImageFileName = backImageFileName,
                    onFrontImageSelected = { fileName -> frontImageFileName = fileName },
                    onFrontImageRemoved = { frontImageFileName = null },
                    onBackImageSelected = { fileName -> backImageFileName = fileName },
                    onBackImageRemoved = { backImageFileName = null },
                    frontLabel = stringResource(R.string.document_photo_front, when (documentType) {
                        DocumentType.ID_CARD -> stringResource(R.string.id_card)
                        DocumentType.PASSPORT -> stringResource(R.string.passport)
                        DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                        DocumentType.SOCIAL_SECURITY -> stringResource(R.string.social_security_card)
                        DocumentType.OTHER -> stringResource(R.string.other_document)
                    }),
                    backLabel = stringResource(R.string.document_photo_back, when (documentType) {
                        DocumentType.ID_CARD -> stringResource(R.string.id_card)
                        DocumentType.PASSPORT -> stringResource(R.string.passport)
                        DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                        DocumentType.SOCIAL_SECURITY -> stringResource(R.string.social_security_card)
                        DocumentType.OTHER -> stringResource(R.string.other_document)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Notes InfoCard
            InfoCard(title = stringResource(R.string.section_notes)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    if (showTopBar || showFab) {
        Scaffold(
            topBar = {
                if (showTopBar) {
                    Column {
                        TopAppBar(
                            title = { Text(stringResource(if (documentId == null) R.string.add_document_title else R.string.edit_document_title)) },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            actions = {
                                IconButton(onClick = toggleFavoriteAction) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.favorite),
                                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (showTypeSwitcher && documentId == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    enabled = onSwitchToBankCard != null,
                                    onClick = { onSwitchToBankCard?.invoke() },
                                    label = { Text(stringResource(R.string.quick_action_add_card)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CreditCard,
                                            contentDescription = null
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(stringResource(R.string.quick_action_add_document)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = save,
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
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            }
        ) { paddingValues ->
            screenContent(paddingValues)
        }
    } else {
        screenContent(PaddingValues(0.dp))
    }

    if (showCommonNamePicker) {
        CommonNameSuggestionSheet(
            suggestionState = commonNameSuggestions,
            currentName = fullName,
            onDismiss = { showCommonNamePicker = false },
            onSelectName = { selectedName ->
                fullName = selectedName
                val splitName = splitDocumentSuggestedName(selectedName)
                firstName = splitName.firstName
                middleName = splitName.middleName
                lastName = splitName.lastName
                showCommonNamePicker = false
            },
            onSaveCurrentName = { currentName ->
                coroutineScope.launch {
                    commonAccountPreferences.addTemplate(
                        type = commonNameType,
                        content = currentName
                    )
                }
            }
        )
    }

    MultiStorageTargetPickerBottomSheet(
        visible = showStorageTargetSheet,
        selectedTargets = selectedStorageTargets.toList(),
        lockedTargetKeys = existingReplicaTargetKeys,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = localKeePassViewModel::getGroups,
        onDismiss = { showStorageTargetSheet = false },
        onSelectedTargetsChange = ::setSelectedStorageTargets
    )
}

private data class SuggestedDocumentNameParts(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = ""
)

private fun splitDocumentSuggestedName(fullName: String): SuggestedDocumentNameParts {
    val normalizedName = fullName.trim()
    if (normalizedName.isBlank()) return SuggestedDocumentNameParts()

    val parts = normalizedName
        .split(Regex("[\\s·•・]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return when {
        parts.size >= 3 -> SuggestedDocumentNameParts(
            firstName = parts.first(),
            middleName = parts.subList(1, parts.lastIndex).joinToString(" "),
            lastName = parts.last()
        )
        parts.size == 2 -> SuggestedDocumentNameParts(
            firstName = parts.first(),
            lastName = parts.last()
        )
        else -> SuggestedDocumentNameParts()
    }
}

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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun ExpandableSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (expanded) 2.dp else 0.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.clickable { onExpandedChange(!expanded) }
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                content()
            }
        }
    }
}
