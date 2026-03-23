package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddress
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.formatForDisplay
import takagi.ru.monica.data.model.isEmpty
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.ui.components.CustomFieldEditorSection
import takagi.ru.monica.ui.components.DualPhotoPicker
import takagi.ru.monica.ui.components.StorageTargetSelectorCard
import takagi.ru.monica.utils.RememberedStorageTarget
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.BankCardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBankCardScreen(
    viewModel: BankCardViewModel,
    cardId: Long? = null,
    onNavigateBack: () -> Unit,
    showTypeSwitcher: Boolean = false,
    onSwitchToDocument: (() -> Unit)? = null,
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
    val bitwardenSyncViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val settingsManager = remember { SettingsManager(context) }
    
    var title by rememberSaveable { mutableStateOf("") }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var cardholderName by rememberSaveable { mutableStateOf("") }
    var expiryMonth by rememberSaveable { mutableStateOf("") }
    var expiryYear by rememberSaveable { mutableStateOf("") }
    var cvv by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var cardType by rememberSaveable { mutableStateOf(CardType.DEBIT) }
    var brand by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var validFromMonth by rememberSaveable { mutableStateOf("") }
    var validFromYear by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var iban by rememberSaveable { mutableStateOf("") }
    var swiftBic by rememberSaveable { mutableStateOf("") }
    var routingNumber by rememberSaveable { mutableStateOf("") }
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var branchCode by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("") }
    var customerServicePhone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var showCardTypeMenu by remember { mutableStateOf(false) }
    var showCardNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    var hasBillingAddress by remember { mutableStateOf(false) }
    var billingAddress by remember { mutableStateOf(BillingAddress()) }
    var showBillingAddressDialog by remember { mutableStateOf(false) }
    var customFields by remember { mutableStateOf<List<CustomFieldDraft>>(emptyList()) }
    
    // 防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }
    
    // 图片路径管理
    var frontImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var backImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var keepassDatabaseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var keepassGroupPath by rememberSaveable { mutableStateOf<String?>(null) }
    var bitwardenVaultId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bitwardenFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAppliedInitialStorage by rememberSaveable { mutableStateOf(false) }
    val database = remember { PasswordDatabase.getDatabase(context) }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenVaults by database.bitwardenVaultDao().getAllVaultsFlow().collectAsState(initial = emptyList())
    val rememberedStorageTarget by settingsManager
        .rememberedStorageTargetFlow(SettingsManager.StorageTargetScope.BANK_CARD)
        .collectAsState(initial = null as RememberedStorageTarget?)
    val cardWalletCategoryFilterState by settingsManager
        .categoryFilterStateFlow(SettingsManager.CategoryFilterScope.CARD_WALLET)
        .collectAsState(initial = null)

    LaunchedEffect(cardId, hasAppliedInitialStorage, rememberedStorageTarget, cardWalletCategoryFilterState) {
        if (cardId != null || hasAppliedInitialStorage) return@LaunchedEffect
        val remembered = rememberedStorageTarget
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
        if (remembered == null && filterKeepassDatabaseId == null && filterKeepassGroupPath == null) return@LaunchedEffect
        selectedCategoryId = remembered?.categoryId
        keepassDatabaseId = filterKeepassDatabaseId ?: remembered?.keepassDatabaseId
        keepassGroupPath = filterKeepassGroupPath ?: remembered?.keepassGroupPath
        bitwardenVaultId = remembered?.bitwardenVaultId
        bitwardenFolderId = remembered?.bitwardenFolderId
        hasAppliedInitialStorage = true
    }
    
    // 如果是编辑模式，加载现有数据
    // 如果是添加模式，重置表单字段（防止保留上次添加的数据）
    LaunchedEffect(cardId) {
        if (cardId != null) {
            viewModel.getCardById(cardId)?.let { item ->
                title = item.title
                notes = item.notes
                isFavorite = item.isFavorite
                selectedCategoryId = item.categoryId
                keepassDatabaseId = item.keepassDatabaseId
                keepassGroupPath = item.keepassGroupPath
                bitwardenVaultId = item.bitwardenVaultId
                bitwardenFolderId = item.bitwardenFolderId
                
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
                
                viewModel.parseCardData(item.itemData)?.let { data ->
                    cardNumber = data.cardNumber
                    cardholderName = data.cardholderName
                    expiryMonth = data.expiryMonth
                    expiryYear = data.expiryYear
                    cvv = data.cvv
                    bankName = data.bankName
                    cardType = data.cardType
                    brand = data.brand
                    nickname = data.nickname
                    validFromMonth = data.validFromMonth
                    validFromYear = data.validFromYear
                    pin = data.pin
                    iban = data.iban
                    swiftBic = data.swiftBic
                    routingNumber = data.routingNumber
                    accountNumber = data.accountNumber
                    branchCode = data.branchCode
                    currency = data.currency
                    customerServicePhone = data.customerServicePhone
                    customFields = CardWalletDataCodec.customFieldsToDrafts(data.customFields)
                    if (data.billingAddress.isNotBlank()) {
                        billingAddress = CardWalletDataCodec.parseBillingAddress(data.billingAddress)
                        hasBillingAddress = !billingAddress.isEmpty()
                    } else {
                        billingAddress = BillingAddress()
                        hasBillingAddress = false
                    }
                }
            }
        } else {
            // 添加模式：重置表单字段
            title = ""
            cardNumber = ""
            cardholderName = ""
            expiryMonth = ""
            expiryYear = ""
            cvv = ""
            bankName = ""
            cardType = CardType.DEBIT
            brand = ""
            nickname = ""
            validFromMonth = ""
            validFromYear = ""
            pin = ""
            iban = ""
            swiftBic = ""
            routingNumber = ""
            accountNumber = ""
            branchCode = ""
            currency = ""
            customerServicePhone = ""
            notes = ""
            isFavorite = false
            hasBillingAddress = false
            billingAddress = BillingAddress()
            customFields = emptyList()
            frontImageFileName = null
            backImageFileName = null
        }
    }

    val canSave = cardNumber.isNotBlank() && !isSaving
    val save: () -> Unit = saveAction@{
        if (isSaving || cardNumber.isBlank()) return@saveAction
        isSaving = true // 防止重复点击
        val syncVaultId = bitwardenVaultId

        val billingAddressJson = if (hasBillingAddress) CardWalletDataCodec.encodeBillingAddress(billingAddress) else ""
        val cardData = BankCardData(
            cardNumber = cardNumber,
            cardholderName = cardholderName,
            expiryMonth = expiryMonth,
            expiryYear = expiryYear,
            cvv = cvv,
            bankName = bankName,
            cardType = cardType,
            billingAddress = billingAddressJson,
            brand = brand,
            nickname = nickname,
            validFromMonth = validFromMonth,
            validFromYear = validFromYear,
            pin = pin,
            iban = iban,
            swiftBic = swiftBic,
            routingNumber = routingNumber,
            accountNumber = accountNumber,
            branchCode = branchCode,
            currency = currency,
            customerServicePhone = customerServicePhone,
            customFields = CardWalletDataCodec.draftsToCustomFields(customFields)
        )

        val imagePathsList = listOf(
            frontImageFileName ?: "",
            backImageFileName ?: ""
        )
        val imagePathsJson = Json.encodeToString(imagePathsList)

        if (cardId == null) {
            viewModel.addCard(
                title = title.ifBlank { context.getString(R.string.bank_card_default_title) },
                cardData = cardData,
                notes = notes,
                isFavorite = isFavorite,
                imagePaths = imagePathsJson,
                categoryId = selectedCategoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            )
        } else {
            viewModel.updateCard(
                id = cardId,
                title = title.ifBlank { context.getString(R.string.bank_card_default_title) },
                cardData = cardData,
                notes = notes,
                isFavorite = isFavorite,
                imagePaths = imagePathsJson,
                categoryId = selectedCategoryId,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            )
        }
        coroutineScope.launch {
            settingsManager.updateRememberedStorageTarget(
                scope = SettingsManager.StorageTargetScope.BANK_CARD,
                target = RememberedStorageTarget(
                    categoryId = selectedCategoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    keepassGroupPath = keepassGroupPath,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId
                )
            )
        }
        syncVaultId?.let(bitwardenSyncViewModel::requestLocalMutationSync)
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
            StorageTargetSelectorCard(
                keepassDatabases = keepassDatabases,
                selectedKeePassDatabaseId = keepassDatabaseId,
                onKeePassDatabaseSelected = {
                    val previousKeepassDatabaseId = keepassDatabaseId
                    keepassDatabaseId = it
                    if (it != null) {
                        if (it != previousKeepassDatabaseId) keepassGroupPath = null
                        bitwardenVaultId = null
                        bitwardenFolderId = null
                    } else {
                        keepassGroupPath = null
                    }
                },
                bitwardenVaults = bitwardenVaults,
                selectedBitwardenVaultId = bitwardenVaultId,
                onBitwardenVaultSelected = {
                    bitwardenVaultId = it
                    if (it != null) {
                        keepassDatabaseId = null
                        keepassGroupPath = null
                    }
                },
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { selectedCategoryId = it },
                selectedBitwardenFolderId = bitwardenFolderId,
                onBitwardenFolderSelected = { folderId ->
                    bitwardenFolderId = folderId
                    if (bitwardenVaultId != null) {
                        keepassDatabaseId = null
                        keepassGroupPath = null
                    }
                }
            )

            // Basic Information
            InfoCard(title = stringResource(R.string.section_basic_info)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Card Name
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.card_name)) },
                        placeholder = { Text(stringResource(R.string.card_name_example)) },
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Bank Name
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text(stringResource(R.string.bank_name)) },
                        placeholder = { Text(stringResource(R.string.bank_name_example)) },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Card Type
                    ExposedDropdownMenuBox(
                        expanded = showCardTypeMenu,
                        onExpandedChange = { showCardTypeMenu = it }
                    ) {
                        OutlinedTextField(
                            value = when (cardType) {
                                CardType.CREDIT -> stringResource(R.string.credit_card)
                                CardType.DEBIT -> stringResource(R.string.debit_card)
                                CardType.PREPAID -> stringResource(R.string.prepaid_card)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.card_type)) },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCardTypeMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = showCardTypeMenu,
                            onDismissRequest = { showCardTypeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.debit_card)) },
                                onClick = {
                                    cardType = CardType.DEBIT
                                    showCardTypeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.credit_card)) },
                                onClick = {
                                    cardType = CardType.CREDIT
                                    showCardTypeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.prepaid_card)) },
                                onClick = {
                                    cardType = CardType.PREPAID
                                    showCardTypeMenu = false
                                }
                            )
                        }
                    }
                    
                    // Card Number
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { 
                            // Only allow digits and spaces
                            cardNumber = it.filter { char -> char.isDigit() || char == ' ' }
                        },
                        label = { Text(stringResource(R.string.card_number_required)) },
                        placeholder = { Text("1234 5678 9012 3456") },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showCardNumber = !showCardNumber }) {
                                Icon(
                                    if (showCardNumber) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(if (showCardNumber) R.string.hide_password else R.string.show_password)
                                )
                            }
                        },
                        visualTransformation = if (showCardNumber) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Cardholder Name
                    OutlinedTextField(
                        value = cardholderName,
                        onValueChange = { cardholderName = it },
                        label = { Text(stringResource(R.string.cardholder_name)) },
                        placeholder = { Text("ZHANG SAN") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Expiry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = expiryMonth,
                            onValueChange = { 
                                if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                    expiryMonth = it
                                }
                            },
                            label = { Text(stringResource(R.string.month)) },
                            placeholder = { Text("12") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        OutlinedTextField(
                            value = expiryYear,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    expiryYear = it
                                }
                            },
                            label = { Text(stringResource(R.string.year)) },
                            placeholder = { Text("2025") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    // CVV
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                cvv = it
                            }
                        },
                        label = { Text(stringResource(R.string.cvv)) },
                        placeholder = { Text("123") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showCvv = !showCvv }) {
                                Icon(
                                    if (showCvv) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(if (showCvv) R.string.hide_password else R.string.show_password)
                                )
                            }
                        },
                        visualTransformation = if (showCvv) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Billing Address Card
            InfoCard(title = stringResource(R.string.billing_address)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasBillingAddress && !billingAddress.isEmpty()) {
                        Text(
                            text = billingAddress.formatForDisplay(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showBillingAddressDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.edit_billing_address))
                            }

                            TextButton(
                                onClick = {
                                    billingAddress = BillingAddress()
                                    hasBillingAddress = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.billing_address_removed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.remove_billing_address))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.billing_address_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { showBillingAddressDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_billing_address)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_billing_address))
                        }
                    }
                }
            }

            InfoCard(title = "扩展字段") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("卡组织 / Brand") },
                        leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("卡片昵称") },
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = validFromMonth,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) validFromMonth = it },
                            label = { Text("起始月") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = validFromYear,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) validFromYear = it },
                            label = { Text("起始年") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = iban,
                        onValueChange = { iban = it },
                        label = { Text("IBAN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = swiftBic,
                        onValueChange = { swiftBic = it },
                        label = { Text("SWIFT / BIC") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it },
                            label = { Text("账户号") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = routingNumber,
                            onValueChange = { routingNumber = it },
                            label = { Text("Routing") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = branchCode,
                            onValueChange = { branchCode = it },
                            label = { Text("分行代码") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("币种") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = customerServicePhone,
                        onValueChange = { customerServicePhone = it },
                        label = { Text("客服电话") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            InfoCard(title = "自定义字段") {
                CustomFieldEditorSection(
                    fields = customFields,
                    onFieldsChange = { customFields = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Photos Card
            InfoCard(title = stringResource(R.string.section_photos)) {
                DualPhotoPicker(
                    frontImageFileName = frontImageFileName,
                    backImageFileName = backImageFileName,
                    onFrontImageSelected = { fileName -> frontImageFileName = fileName },
                    onFrontImageRemoved = { frontImageFileName = null },
                    onBackImageSelected = { fileName -> backImageFileName = fileName },
                    onBackImageRemoved = { backImageFileName = null },
                    frontLabel = stringResource(R.string.bank_card_photo_front_label),
                    backLabel = stringResource(R.string.bank_card_photo_back_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Notes Card
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
                            title = { Text(stringResource(if (cardId == null) R.string.add_bank_card_title else R.string.edit_bank_card_title)) },
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
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (showTypeSwitcher && cardId == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(stringResource(R.string.quick_action_add_card)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CreditCard,
                                            contentDescription = null
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = false,
                                    enabled = onSwitchToDocument != null,
                                    onClick = { onSwitchToDocument?.invoke() },
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

    if (showBillingAddressDialog) {
        var streetAddress by remember { mutableStateOf(billingAddress.streetAddress) }
        var apartment by remember { mutableStateOf(billingAddress.apartment) }
        var city by remember { mutableStateOf(billingAddress.city) }
        var stateProvince by remember { mutableStateOf(billingAddress.stateProvince) }
        var postalCode by remember { mutableStateOf(billingAddress.postalCode) }
        var country by remember { mutableStateOf(billingAddress.country) }

        AlertDialog(
            onDismissRequest = { showBillingAddressDialog = false },
            title = { Text(stringResource(R.string.billing_address)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        label = { Text(stringResource(R.string.street_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = apartment,
                        onValueChange = { apartment = it },
                        label = { Text(stringResource(R.string.apartment)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(stringResource(R.string.city)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = stateProvince,
                        onValueChange = { stateProvince = it },
                        label = { Text(stringResource(R.string.state_province)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text(stringResource(R.string.postal_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text(stringResource(R.string.country)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedAddress = BillingAddress(
                            streetAddress = streetAddress.trim(),
                            apartment = apartment.trim(),
                            city = city.trim(),
                            stateProvince = stateProvince.trim(),
                            postalCode = postalCode.trim(),
                            country = country.trim()
                        )
                        val hasAddress = !updatedAddress.isEmpty()
                        billingAddress = updatedAddress
                        hasBillingAddress = hasAddress
                        showBillingAddressDialog = false
                        val message = if (hasAddress) {
                            R.string.billing_address_saved
                        } else {
                            R.string.billing_address_removed
                        }
                        Toast.makeText(
                            context,
                            context.getString(message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBillingAddressDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
