package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.ui.cardwallet.CardFaceEditSection
import takagi.ru.monica.ui.cardwallet.billingAddressCardFacePreviewData
import takagi.ru.monica.ui.cardwallet.rememberCardFaceEditorState
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.isEmpty
import takagi.ru.monica.data.model.normalizedStorageTargets
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.ui.components.CustomFieldEditorSection
import takagi.ru.monica.ui.components.MultiStorageTargetPickerBottomSheet
import takagi.ru.monica.ui.components.MultiStorageTargetSelectorCard
import takagi.ru.monica.ui.components.*
import takagi.ru.monica.ui.components.SuggestedOutlinedTextField
import takagi.ru.monica.data.CommonSuggestionField
import takagi.ru.monica.ui.components.OutlinedTextField
import takagi.ru.monica.viewmodel.BillingAddressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillingAddressScreen(
    viewModel: BillingAddressViewModel,
    addressId: Long? = null,
    onNavigateBack: () -> Unit,
    initialCategoryId: Long? = null,
    initialMdbxDatabaseId: Long? = null,
    initialMdbxFolderId: String? = null,
    showTopBar: Boolean = true,
    showFab: Boolean = true,
    onFavoriteStateChanged: ((Boolean) -> Unit)? = null,
    onCanSaveChanged: ((Boolean) -> Unit)? = null,
    onSaveActionChanged: (((() -> Unit)) -> Unit)? = null,
    onToggleFavoriteActionChanged: (((() -> Unit)) -> Unit)? = null,
    modifier: Modifier = Modifier,
    embeddedDraft: takagi.ru.monica.data.model.EmbeddedWalletContent.Snapshot? = null,
    embeddedBitmap: android.graphics.Bitmap? = null,
    embeddedAttachmentsContent: (@Composable () -> Unit)? = null,
    onEmbeddedSave: (suspend (takagi.ru.monica.attachments.EmbeddedWalletEditorResult) -> Unit)? = null,
) {
    val context = LocalContext.current
    takagi.ru.monica.lansync.DeviceSyncEditorLease()
    val scope = rememberCoroutineScope()
    val database = remember { PasswordDatabase.getDatabase(context) }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val mdbxDatabases by database.localMdbxDatabaseDao().getAvailableDatabases().collectAsState(initial = emptyList())

    var title by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var streetAddress by rememberSaveable { mutableStateOf("") }
    var apartment by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateProvince by rememberSaveable { mutableStateOf("") }
    var postalCode by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val editorSections = rememberItemEditorSections()
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var workingAddressId by remember(addressId) { mutableStateOf(addressId) }
    var isDefault by rememberSaveable { mutableStateOf(false) }
    val cardFaceEditor = rememberCardFaceEditorState(addressId)
    var hasAppliedInitialStorage by rememberSaveable { mutableStateOf(false) }
    var hasLoadedExistingFields by rememberSaveable(addressId) { mutableStateOf(false) }
    var currentReplicaGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(initialCategoryId) }
    var mdbxDatabaseId by rememberSaveable { mutableStateOf(initialMdbxDatabaseId) }
    var mdbxFolderId by rememberSaveable { mutableStateOf(initialMdbxFolderId) }
    var showStorageTargetSheet by remember { mutableStateOf(false) }
    var customFields by rememberSaveable(stateSaver = takagi.ru.monica.ui.components.EntryFieldDraftSaver) { mutableStateOf<List<CustomFieldDraft>>(emptyList()) }
    val selectedStorageTargets = remember { mutableStateListOf<StorageTarget>() }

    fun syncStorageState(targets: List<StorageTarget>) {
        when (val target = targets.firstOrNull()) {
            is StorageTarget.MonicaLocal -> {
                selectedCategoryId = target.categoryId
                mdbxDatabaseId = null
                mdbxFolderId = null
            }
            is StorageTarget.Mdbx -> {
                selectedCategoryId = null
                mdbxDatabaseId = target.databaseId
                mdbxFolderId = target.folderId
            }
            else -> {
                selectedCategoryId = null
                mdbxDatabaseId = null
                mdbxFolderId = null
            }
        }
    }

    fun setSelectedStorageTargets(targets: List<StorageTarget>) {
        val supportedTargets = targets
            .filter { it is StorageTarget.MonicaLocal || it is StorageTarget.Mdbx }
            .normalizedStorageTargets()
        selectedStorageTargets.clear()
        selectedStorageTargets.addAll(supportedTargets)
        syncStorageState(supportedTargets)
    }

    LaunchedEffect(addressId, hasAppliedInitialStorage, initialCategoryId, initialMdbxDatabaseId, initialMdbxFolderId) {
        if (addressId != null || hasAppliedInitialStorage) return@LaunchedEffect
        val target = if (initialMdbxDatabaseId != null) {
            StorageTarget.Mdbx(initialMdbxDatabaseId, initialMdbxFolderId?.takeIf { it.isNotBlank() })
        } else {
            StorageTarget.MonicaLocal(initialCategoryId)
        }
        setSelectedStorageTargets(listOf(target))
        hasAppliedInitialStorage = true
    }

    LaunchedEffect(addressId, embeddedDraft?.id) {
        if (addressId != null || embeddedDraft != null) {
            if (hasLoadedExistingFields) return@LaunchedEffect
            (embeddedDraft?.displayItem() ?: withContext(Dispatchers.IO) { viewModel.getAddressById(requireNotNull(addressId)) })?.let { item ->
                val parsedData = withContext(Dispatchers.Default) {
                    viewModel.parseAddressData(item.itemData)
                } ?: BillingAddressData()
                cardFaceEditor.load(item, parsedData.cardFace)
                isDefault = parsedData.isDefault
                title = item.title
                notes = item.notes
                isFavorite = item.isFavorite
                currentReplicaGroupId = item.replicaGroupId
                selectedCategoryId = item.categoryId
                mdbxDatabaseId = item.mdbxDatabaseId
                mdbxFolderId = item.mdbxFolderId
                fullName = parsedData.fullName
                company = parsedData.company
                streetAddress = parsedData.streetAddress
                apartment = parsedData.apartment
                city = parsedData.city
                stateProvince = parsedData.stateProvince
                postalCode = parsedData.postalCode
                country = parsedData.country
                phone = parsedData.phone
                email = parsedData.email
                customFields = CardWalletDataCodec.customFieldsToDrafts(parsedData.customFields)
                val existingTarget = item.toStorageTarget()
                setSelectedStorageTargets(
                    listOf(
                        if (existingTarget is StorageTarget.Mdbx || existingTarget is StorageTarget.MonicaLocal) {
                            existingTarget
                        } else {
                            StorageTarget.MonicaLocal(item.categoryId)
                        }
                    )
                )
                hasLoadedExistingFields = true
            }
        } else {
            hasLoadedExistingFields = false
            currentReplicaGroupId = null
        }
    }

    val addressData = BillingAddressData(
        fullName = fullName.trim(),
        company = company.trim(),
        streetAddress = streetAddress.trim(),
        apartment = apartment.trim(),
        city = city.trim(),
        stateProvince = stateProvince.trim(),
        postalCode = postalCode.trim(),
        country = country.trim(),
        phone = phone.trim(),
        email = email.trim(),
        customFields = CardWalletDataCodec.draftsToCustomFields(customFields),
        isDefault = isDefault,
        cardFace = cardFaceEditor.config
    )
    val effectiveTitle = title.trim().ifBlank {
        fullName.trim().ifBlank {
            streetAddress.trim().ifBlank {
                context.getString(R.string.billing_address)
            }
        }
    }
    val canSave = !addressData.isEmpty()

    val save: () -> Unit = {
        if (!canSave || isSaving) {
            if (!canSave) {
                Toast.makeText(context, R.string.billing_address_empty, Toast.LENGTH_SHORT).show()
            }
        } else {
            isSaving = true
            val target = selectedStorageTargets.firstOrNull() ?: StorageTarget.MonicaLocal(null)
            scope.launch {
                try {
                    if (embeddedDraft != null && onEmbeddedSave != null) {
                        val data = kotlinx.serialization.json.Json.parseToJsonElement(CardWalletDataCodec.encodeBillingAddressData(addressData)) as kotlinx.serialization.json.JsonObject
                        onEmbeddedSave(takagi.ru.monica.attachments.EmbeddedWalletEditorResult(
                            embeddedDraft.edited(effectiveTitle, notes, data).withFavorite(isFavorite),
                            emptyList(), emptyList(), cardFaceEditor.imageBytes))
                        cardFaceEditor.clearPendingBytes()
                        return@launch
                    }
                    val localCategory = (target as? StorageTarget.MonicaLocal)?.categoryId
                    val mdbxTarget = target as? StorageTarget.Mdbx
                    val currentId = workingAddressId
                    if (currentId == null) {
                        viewModel.addAddress(
                            title = effectiveTitle, addressData = addressData,
                            notes = notes, isFavorite = isFavorite,
                            imagePaths = cardFaceEditor.item?.imagePaths.orEmpty(),
                            categoryId = localCategory, mdbxDatabaseId = mdbxTarget?.databaseId,
                            mdbxFolderId = mdbxTarget?.folderId, replicaGroupId = currentReplicaGroupId,
                            cardFaceImageBytes = cardFaceEditor.imageBytes,
                            onCreated = { workingAddressId = it }
                        ).await()
                    } else {
                        viewModel.updateAddress(
                            id = currentId, title = effectiveTitle, addressData = addressData,
                            notes = notes, isFavorite = isFavorite,
                            imagePaths = cardFaceEditor.item?.imagePaths.orEmpty(),
                            categoryId = localCategory, mdbxDatabaseId = mdbxTarget?.databaseId,
                            mdbxFolderId = mdbxTarget?.folderId, replicaGroupId = currentReplicaGroupId,
                            cardFaceImageBytes = cardFaceEditor.imageBytes
                        ).await()
                    }
                    cardFaceEditor.clearPendingBytes()
                    onNavigateBack()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.card_face_save_failed, Toast.LENGTH_LONG).show()
                } finally {
                    isSaving = false
                }
            }
        }
    }
    val toggleFavorite: () -> Unit = {
        isFavorite = !isFavorite
        onFavoriteStateChanged?.invoke(isFavorite)
    }

    LaunchedEffect(isFavorite, canSave, save, toggleFavorite) {
        onFavoriteStateChanged?.invoke(isFavorite)
        onCanSaveChanged?.invoke(canSave)
        onSaveActionChanged?.invoke(save)
        onToggleFavoriteActionChanged?.invoke(toggleFavorite)
    }

    val screenContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize().testTag("billing_item_editor").imePadding()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (embeddedDraft == null) MultiStorageTargetSelectorCard(
                selectedTargets = selectedStorageTargets.toList(),
                existingTargetKeys = emptySet(),
                categories = categories,
                keepassDatabases = emptyList(),
                mdbxDatabases = mdbxDatabases,
                bitwardenVaults = emptyList(),
                bitwardenFolderDao = database.bitwardenFolderDao(),
                isEditing = addressId != null,
                onAddTargetClick = { showStorageTargetSheet = true },
                onRemoveTarget = {}
            )
            ItemEditorIdentity(title, { title = it }, stringResource(R.string.title), Icons.Default.Home)

            CardFaceEditSection(
                    compact = true,
                state = cardFaceEditor,
                previewData = billingAddressCardFacePreviewData(effectiveTitle, addressData),
                enabled = !isSaving,
                overrideBitmap = embeddedBitmap
            )

            InfoCard(title = stringResource(R.string.billing_address)) {

                SuggestedOutlinedTextField(
                    suggestionField = CommonSuggestionField.FULL_NAME,
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(stringResource(R.string.full_name)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text(stringResource(R.string.document_company_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            InfoCard(title = stringResource(R.string.street_address)) {
                if (takagi.ru.monica.ui.components.rememberEntryContentStyle()) {
                    takagi.ru.monica.ui.components.EntryAddressFields(
                        street = streetAddress, city = city, region = stateProvince,
                        postalCode = postalCode, country = country,
                        onStreet = { streetAddress = it }, onCity = { city = it },
                        onRegion = { stateProvince = it }, onPostalCode = { postalCode = it },
                        onCountry = { country = it },
                        additionalStreet = {
                            SuggestedOutlinedTextField(suggestionField = CommonSuggestionField.APARTMENT, value = apartment, onValueChange = { apartment = it },
                                label = { Text(stringResource(R.string.apartment)) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(12.dp))
                        },
                    )
                } else {

                SuggestedOutlinedTextField(
                    suggestionField = CommonSuggestionField.STREET,
                    value = streetAddress,
                    onValueChange = { streetAddress = it },
                    label = { Text(stringResource(R.string.street_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                SuggestedOutlinedTextField(
                    suggestionField = CommonSuggestionField.APARTMENT,
                    value = apartment,
                    onValueChange = { apartment = it },
                    label = { Text(stringResource(R.string.apartment)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestedOutlinedTextField(
                        suggestionField = CommonSuggestionField.CITY,
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(stringResource(R.string.city)) },
                        containerModifier = Modifier.weight(1f), modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    SuggestedOutlinedTextField(
                        suggestionField = CommonSuggestionField.REGION,
                        value = stateProvince,
                        onValueChange = { stateProvince = it },
                        label = { Text(stringResource(R.string.state_province)) },
                        containerModifier = Modifier.weight(1f), modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestedOutlinedTextField(
                        suggestionField = CommonSuggestionField.POSTAL_CODE,
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text(stringResource(R.string.postal_code)) },
                        containerModifier = Modifier.weight(1f), modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    SuggestedOutlinedTextField(
                        suggestionField = CommonSuggestionField.COUNTRY,
                        value = country,
                        onValueChange = { country = it },
                        label = { Text(stringResource(R.string.country)) },
                        containerModifier = Modifier.weight(1f), modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                }
}

            ItemEditorOptionalSection(editorSections, "contact", email.isNotBlank() || phone.isNotBlank()) {
InfoCard(title = stringResource(R.string.billing_address_contact_title)) {
                if (takagi.ru.monica.ui.components.rememberEntryContentStyle()) {
                    takagi.ru.monica.ui.components.EntryContactFields(listOf(email), listOf(phone),
                        onEmails = { email = it.firstOrNull().orEmpty() },
                        onPhones = { phone = it.firstOrNull().orEmpty() }, allowMultiple = false)
                } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )
                    }

            }
            }

            ItemEditorOptionalSection(editorSections, "custom", customFields.isNotEmpty()) {
                CustomFieldEditorSection(fields = customFields, onFieldsChange = { customFields = it },
                    modifier = Modifier.fillMaxWidth(), contentStyle = true, showAddButton = false)
            }

            ItemEditorOptionalSection(editorSections, "notes", notes.isNotBlank()) {
InfoCard(title = stringResource(R.string.notes)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
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
            embeddedAttachmentsContent?.invoke()

            ItemEditorAddContent(editorSections, options = listOf(
                    ItemEditorContentOption("contact", R.string.billing_address_contact_title, Icons.Default.ContactMail, editorSections.visible("contact", email.isNotBlank() || phone.isNotBlank())),
                    ItemEditorContentOption("notes", R.string.notes, Icons.Default.Notes, editorSections.visible("notes", notes.isNotBlank()))
                ), fields = customFields, onFieldsChange = { customFields = it },
                enabled = !isSaving)
            Spacer(Modifier.height(96.dp))

        }
    }

    if (showTopBar || showFab) {
        Scaffold(
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.billing_address)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            IconButton(onClick = toggleFavorite) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
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
                                modifier = Modifier.height(22.dp).width(22.dp),
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

    MultiStorageTargetPickerBottomSheet(
        visible = showStorageTargetSheet,
        selectedTargets = selectedStorageTargets.toList(),
        lockedTargetKeys = emptySet(),
        categories = categories,
        keepassDatabases = emptyList(),
        mdbxDatabases = mdbxDatabases,
        bitwardenVaults = emptyList(),
        getBitwardenFolders = { flowOf(emptyList()) },
        getKeePassGroups = { flowOf(emptyList()) },
        onDismiss = { showStorageTargetSheet = false },
        onSelectedTargetsChange = ::setSelectedStorageTargets,
        showSelectionModeToggle = false
    )
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalEntryContentStyle provides true) {
        TemplateFormSection(title) { content() }
    }
}
