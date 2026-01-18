package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddress
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.formatForDisplay
import takagi.ru.monica.data.model.isEmpty
import takagi.ru.monica.ui.components.DualPhotoPicker
import takagi.ru.monica.viewmodel.BankCardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBankCardScreen(
    viewModel: BankCardViewModel,
    cardId: Long? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var title by rememberSaveable { mutableStateOf("") }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var cardholderName by rememberSaveable { mutableStateOf("") }
    var expiryMonth by rememberSaveable { mutableStateOf("") }
    var expiryYear by rememberSaveable { mutableStateOf("") }
    var cvv by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var cardType by rememberSaveable { mutableStateOf(CardType.DEBIT) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var showCardTypeMenu by remember { mutableStateOf(false) }
    var showCardNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    var hasBillingAddress by remember { mutableStateOf(false) }
    var billingAddress by remember { mutableStateOf(BillingAddress()) }
    var showBillingAddressDialog by remember { mutableStateOf(false) }
    
    // 防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }
    
    // 图片路径管理
    var frontImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var backImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 如果是编辑模式，加载现有数据
    LaunchedEffect(cardId) {
        cardId?.let {
            viewModel.getCardById(it)?.let { item ->
                title = item.title
                notes = item.notes
                isFavorite = item.isFavorite
                
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
                    if (data.billingAddress.isNotBlank()) {
                        billingAddress = try {
                            Json.decodeFromString<BillingAddress>(data.billingAddress)
                        } catch (e: Exception) {
                            BillingAddress()
                        }
                        hasBillingAddress = !billingAddress.isEmpty()
                    } else {
                        billingAddress = BillingAddress()
                        hasBillingAddress = false
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (cardId == null) R.string.add_bank_card_title else R.string.edit_bank_card_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // 收藏按钮
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    
                    // 保存按钮
                    IconButton(
                        onClick = {
                            if (isSaving) return@IconButton
                            isSaving = true // 防止重复点击
                            
                            val billingAddressJson = if (hasBillingAddress && !billingAddress.isEmpty()) {
                                Json.encodeToString(billingAddress)
                            } else {
                                ""
                            }
                            val cardData = BankCardData(
                                cardNumber = cardNumber,
                                cardholderName = cardholderName,
                                expiryMonth = expiryMonth,
                                expiryYear = expiryYear,
                                cvv = cvv,
                                bankName = bankName,
                                cardType = cardType,
                                billingAddress = billingAddressJson
                            )
                            
                            val imagePathsList = listOf(
                                frontImageFileName ?: "",
                                backImageFileName ?: ""
                            )
                            val imagePathsJson = Json.encodeToString(imagePathsList)
                            
                            if (cardId == null) {
                                viewModel.addCard(
                                    title = title.ifBlank { "银行卡" },
                                    cardData = cardData,
                                    notes = notes,
                                    isFavorite = isFavorite,
                                    imagePaths = imagePathsJson
                                )
                            } else {
                                viewModel.updateCard(
                                    id = cardId,
                                    title = title.ifBlank { "银行卡" },
                                    cardData = cardData,
                                    notes = notes,
                                    isFavorite = isFavorite,
                                    imagePaths = imagePathsJson
                                )
                            }
                            onNavigateBack()
                        },
                        enabled = cardNumber.isNotBlank() && !isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.card_name)) },
                placeholder = { Text(stringResource(R.string.card_name_example)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 银行名称
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text(stringResource(R.string.bank_name)) },
                placeholder = { Text(stringResource(R.string.bank_name_example)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 卡类型选择
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCardTypeMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
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
            
            // 卡号
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { 
                    // 只允许数字和空格
                    cardNumber = it.filter { char -> char.isDigit() || char == ' ' }
                },
                label = { Text(stringResource(R.string.card_number_required)) },
                placeholder = { Text("1234 5678 9012 3456") },
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
                }
            )
            
            // 持卡人
            OutlinedTextField(
                value = cardholderName,
                onValueChange = { cardholderName = it },
                label = { Text(stringResource(R.string.cardholder_name)) },
                placeholder = { Text("ZHANG SAN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 有效期
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
                    singleLine = true
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
                    singleLine = true
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
                }
            )
            
            // 备注
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 5
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.billing_address),
                        style = MaterialTheme.typography.titleMedium
                    )

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
                                modifier = Modifier.weight(1f)
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
                            modifier = Modifier.align(Alignment.End)
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
            
            // 双面照片选择器
            DualPhotoPicker(
                frontImageFileName = frontImageFileName,
                backImageFileName = backImageFileName,
                onFrontImageSelected = { fileName -> frontImageFileName = fileName },
                onFrontImageRemoved = { frontImageFileName = null },
                onBackImageSelected = { fileName -> backImageFileName = fileName },
                onBackImageRemoved = { backImageFileName = null },
                frontLabel = "银行卡照片（正面）",
                backLabel = "银行卡照片（背面）",
                modifier = Modifier.fillMaxWidth()
            )
        }
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apartment,
                        onValueChange = { apartment = it },
                        label = { Text(stringResource(R.string.apartment)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(stringResource(R.string.city)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stateProvince,
                        onValueChange = { stateProvince = it },
                        label = { Text(stringResource(R.string.state_province)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text(stringResource(R.string.postal_code)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text(stringResource(R.string.country)) },
                        modifier = Modifier.fillMaxWidth()
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