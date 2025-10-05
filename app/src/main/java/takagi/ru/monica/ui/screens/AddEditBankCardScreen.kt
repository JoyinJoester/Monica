package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.ui.components.ImagePicker
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBankCardScreen(
    viewModel: BankCardViewModel,
    cardId: Long? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardholderName by remember { mutableStateOf("") }
    var expiryMonth by remember { mutableStateOf("") }
    var expiryYear by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var cardType by remember { mutableStateOf(CardType.DEBIT) }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var showCardTypeMenu by remember { mutableStateOf(false) }
    var showCardNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    
    // 图片路径列表
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 如果是编辑模式，加载现有数据
    LaunchedEffect(cardId) {
        cardId?.let {
            viewModel.getCardById(it)?.let { item ->
                title = item.title
                notes = item.notes
                isFavorite = item.isFavorite
                
                // 解析图片路径
                imagePaths = try {
                    if (item.imagePaths.isNotBlank()) {
                        Json.decodeFromString<List<String>>(item.imagePaths)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                
                viewModel.parseCardData(item.itemData)?.let { data ->
                    cardNumber = data.cardNumber
                    cardholderName = data.cardholderName
                    expiryMonth = data.expiryMonth
                    expiryYear = data.expiryYear
                    cvv = data.cvv
                    bankName = data.bankName
                    cardType = data.cardType
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
                            val cardData = BankCardData(
                                cardNumber = cardNumber,
                                cardholderName = cardholderName,
                                expiryMonth = expiryMonth,
                                expiryYear = expiryYear,
                                cvv = cvv,
                                bankName = bankName,
                                cardType = cardType
                            )
                            
                            val imagePathsJson = Json.encodeToString(imagePaths)
                            
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
                        enabled = cardNumber.isNotBlank()
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
            
            // 卡片照片（正面）
            ImagePicker(
                imageFileName = imagePaths.getOrNull(0),
                onImageSelected = { fileName ->
                    imagePaths = if (imagePaths.isEmpty()) {
                        listOf(fileName)
                    } else {
                        listOf(fileName) + imagePaths.drop(1)
                    }
                },
                onImageRemoved = {
                    imagePaths = if (imagePaths.size > 1) {
                        imagePaths.drop(1)
                    } else {
                        emptyList()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "银行卡照片（正面）"
            )
            
            // 卡片照片（反面）
            ImagePicker(
                imageFileName = imagePaths.getOrNull(1),
                onImageSelected = { fileName ->
                    imagePaths = when (imagePaths.size) {
                        0 -> listOf("", fileName)
                        1 -> imagePaths + fileName
                        else -> imagePaths.take(1) + fileName
                    }
                },
                onImageRemoved = {
                    imagePaths = if (imagePaths.size > 1) {
                        imagePaths.take(1)
                    } else {
                        imagePaths
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "银行卡照片（反面，可选）"
            )
            
            // 提示信息
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "所有敏感信息将加密存储在本地设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
