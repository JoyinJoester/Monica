package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.utils.PasswordStrengthAnalyzer
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.ui.components.AppSelectorField
import takagi.ru.monica.ui.components.PasswordStrengthIndicator
import takagi.ru.monica.ui.icons.MonicaIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(
    viewModel: PasswordViewModel,
    passwordId: Long?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val passwordGenerator = remember { PasswordGenerator() }
    
    var title by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var appPackageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    
    // Phase 8: 计算密码强度
    val passwordStrength = remember(password) {
        if (password.isNotEmpty()) {
            PasswordStrengthAnalyzer.calculateStrength(password)
        } else {
            null
        }
    }
    
    // Phase 7: 新增字段状态
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var creditCardNumber by remember { mutableStateOf("") }
    var creditCardHolder by remember { mutableStateOf("") }
    var creditCardExpiry by remember { mutableStateOf("") }
    var creditCardCVV by remember { mutableStateOf("") }
    
    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(false) }
    var addressInfoExpanded by remember { mutableStateOf(false) }
    var paymentInfoExpanded by remember { mutableStateOf(false) }
    
    val isEditing = passwordId != null && passwordId > 0
    val isCopyMode = passwordId != null && passwordId < 0
    
    // Load existing password data if editing or copying
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
                    // Phase 7: 加载新字段
                    email = entry.email
                    phone = entry.phone
                    addressLine = entry.addressLine
                    city = entry.city
                    state = entry.state
                    zipCode = entry.zipCode
                    country = entry.country
                    creditCardNumber = entry.creditCardNumber
                    creditCardHolder = entry.creditCardHolder
                    creditCardExpiry = entry.creditCardExpiry
                    creditCardCVV = entry.creditCardCVV
                    
                    // 如果是编辑模式，加载密码和收藏状态
                    if (isEditing) {
                        password = entry.password
                        isFavorite = entry.isFavorite
                    }
                    // 如果是复制模式，密码留空，不继承收藏状态
                    // password 和 isFavorite 保持初始值（空字符串和false）
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(stringResource(
                        if (isEditing) R.string.edit_password_title 
                        else R.string.add_password_title
                    )) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = stringResource(R.string.back))
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
                    
                    TextButton(
                        onClick = {
                            if (title.isNotEmpty() && password.isNotEmpty()) {
                                val entry = PasswordEntry(
                                    id = if (isEditing) passwordId!! else 0,  // 复制模式下使用0创建新条目
                                    title = title,
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes,
                                    isFavorite = isFavorite,
                                    appPackageName = appPackageName,
                                    appName = appName,
                                    // Phase 7: 保存新字段
                                    email = email,
                                    phone = phone,
                                    addressLine = addressLine,
                                    city = city,
                                    state = state,
                                    zipCode = zipCode,
                                    country = country,
                                    creditCardNumber = creditCardNumber,
                                    creditCardHolder = creditCardHolder,
                                    creditCardExpiry = creditCardExpiry,
                                    creditCardCVV = creditCardCVV
                                )
                                
                                if (isEditing) {
                                    viewModel.updatePasswordEntry(entry)
                                } else {
                                    // 复制模式和新建模式都使用 addPasswordEntry
                                    viewModel.addPasswordEntry(entry)
                                }
                                onNavigateBack()
                            }
                        },
                        enabled = title.isNotEmpty() && password.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_required)) },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            
            // Website Field
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text(stringResource(R.string.website_url)) },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            
            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username_email)) },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            
            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_required)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                visualTransformation = if (passwordVisible) ColoredPasswordVisualTransformation() else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showPasswordGenerator = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate_password))
                        }
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            
            // Phase 8: 密码强度分析指示器
            if (passwordStrength != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthIndicator(
                    strength = passwordStrength,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Notes Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                maxLines = 5
            )
            
            // 应用选择器 (用于自动填充匹配)
            AppSelectorField(
                selectedPackageName = appPackageName,
                selectedAppName = appName,
                onAppSelected = { packageName, name ->
                    appPackageName = packageName
                    appName = name
                }
            )
            
            // Phase 7: 个人信息折叠面板
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { personalInfoExpanded = !personalInfoExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.personal_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Icon(
                            if (personalInfoExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    
                    // 展开内容
                    if (personalInfoExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 邮箱
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("邮箱") },
                                placeholder = { Text("user@example.com") },
                                leadingIcon = {
                                    Icon(MonicaIcons.General.email, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                isError = email.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidEmail(email),
                                supportingText = {
                                    if (email.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidEmail(email)) {
                                        Text(
                                            takagi.ru.monica.utils.FieldValidation.getEmailError(email) ?: "",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                            
                            // 手机号
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { 
                                    // 只允许输入数字
                                    if (it.all { char -> char.isDigit() } && it.length <= 11) {
                                        phone = it
                                    }
                                },
                                label = { Text("手机号") },
                                placeholder = { Text("13800000000") },
                                leadingIcon = {
                                    Icon(MonicaIcons.General.phone, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                isError = phone.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidPhone(phone),
                                supportingText = {
                                    if (phone.isNotEmpty()) {
                                        if (!takagi.ru.monica.utils.FieldValidation.isValidPhone(phone)) {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.getPhoneError(phone) ?: "",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.formatPhone(phone),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Phase 7: 地址信息折叠面板
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addressInfoExpanded = !addressInfoExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.address_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Icon(
                            if (addressInfoExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    
                    // 展开内容
                    if (addressInfoExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 详细地址
                            OutlinedTextField(
                                value = addressLine,
                                onValueChange = { addressLine = it },
                                label = { Text("详细地址") },
                                placeholder = { Text("街道、门牌号等") },
                                leadingIcon = {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                singleLine = true
                            )
                            
                            // 城市和省份（两列布局）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = { Text("城市") },
                                    placeholder = { Text("北京") },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationCity, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    singleLine = true
                                )
                                
                                OutlinedTextField(
                                    value = state,
                                    onValueChange = { state = it },
                                    label = { Text("省份") },
                                    placeholder = { Text("北京市") },
                                    leadingIcon = {
                                        Icon(MonicaIcons.General.location, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    singleLine = true
                                )
                            }
                            
                            // 邮编和国家（两列布局）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = zipCode,
                                    onValueChange = { 
                                        // 只允许输入数字
                                        if (it.all { char -> char.isDigit() } && it.length <= 6) {
                                            zipCode = it
                                        }
                                    },
                                    label = { Text("邮编") },
                                    placeholder = { Text("100000") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Markunread, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    isError = zipCode.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidZipCode(zipCode),
                                    supportingText = {
                                        if (zipCode.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidZipCode(zipCode)) {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.getZipCodeError(zipCode) ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                )
                                
                                OutlinedTextField(
                                    value = country,
                                    onValueChange = { country = it },
                                    label = { Text("国家") },
                                    placeholder = { Text("中国") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Public, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
            
            // Phase 7: 支付信息折叠面板
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paymentInfoExpanded = !paymentInfoExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.payment_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Icon(
                            if (paymentInfoExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    
                    // 展开内容
                    if (paymentInfoExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 信用卡号
                            OutlinedTextField(
                                value = creditCardNumber,
                                onValueChange = { 
                                    // 只允许输入数字
                                    if (it.all { char -> char.isDigit() } && it.length <= 19) {
                                        creditCardNumber = it
                                    }
                                },
                                label = { Text("信用卡号") },
                                placeholder = { Text("1234 5678 9012 3456") },
                                leadingIcon = {
                                    Icon(MonicaIcons.Data.creditCard, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                visualTransformation = if (creditCardNumber.isNotEmpty()) {
                                    VisualTransformation { text ->
                                        TransformedText(
                                            AnnotatedString(takagi.ru.monica.utils.FieldValidation.formatCreditCard(text.text)),
                                            OffsetMapping.Identity
                                        )
                                    }
                                } else {
                                    VisualTransformation.None
                                },
                                isError = creditCardNumber.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidCreditCard(creditCardNumber),
                                supportingText = {
                                    if (creditCardNumber.isNotEmpty()) {
                                        if (!takagi.ru.monica.utils.FieldValidation.isValidCreditCard(creditCardNumber)) {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.getCreditCardError(creditCardNumber) ?: "",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                "✓ 卡号有效",
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            )
                            
                            // 持卡人姓名
                            OutlinedTextField(
                                value = creditCardHolder,
                                onValueChange = { creditCardHolder = it },
                                label = { Text("持卡人姓名") },
                                placeholder = { Text("ZHANG SAN") },
                                leadingIcon = {
                                    Icon(MonicaIcons.General.person, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                singleLine = true
                            )
                            
                            // 有效期和CVV（两列布局）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = creditCardExpiry,
                                    onValueChange = { 
                                        // 格式化为 MM/YY
                                        val digits = it.filter { char -> char.isDigit() }
                                        creditCardExpiry = when {
                                            digits.length <= 2 -> digits
                                            digits.length <= 4 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                                            else -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}"
                                        }
                                    },
                                    label = { Text("有效期") },
                                    placeholder = { Text("MM/YY") },
                                    leadingIcon = {
                                        Icon(MonicaIcons.General.calendar, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    isError = creditCardExpiry.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidExpiry(creditCardExpiry),
                                    supportingText = {
                                        if (creditCardExpiry.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidExpiry(creditCardExpiry)) {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.getExpiryError(creditCardExpiry) ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                )
                                
                                OutlinedTextField(
                                    value = creditCardCVV,
                                    onValueChange = { 
                                        // 只允许输入数字，3-4位
                                        if (it.all { char -> char.isDigit() } && it.length <= 4) {
                                            creditCardCVV = it
                                        }
                                    },
                                    label = { Text("CVV") },
                                    placeholder = { Text("123") },
                                    leadingIcon = {
                                        Icon(MonicaIcons.Security.lock, contentDescription = null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    isError = creditCardCVV.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidCVV(creditCardCVV),
                                    supportingText = {
                                        if (creditCardCVV.isNotEmpty() && !takagi.ru.monica.utils.FieldValidation.isValidCVV(creditCardCVV)) {
                                            Text(
                                                takagi.ru.monica.utils.FieldValidation.getCVVError(creditCardCVV) ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                )
                            }
                            
                            // 安全提示
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Text(
                                    "信用卡信息将加密存储",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 复制按钮区域（仅编辑模式显示）
            if (isEditing && (username.isNotEmpty() || password.isNotEmpty())) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 复制账号按钮
                    if (username.isNotEmpty()) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("username", username)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.username_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_username))
                        }
                    }
                    
                    // 复制密码按钮
                    if (password.isNotEmpty()) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("password", password)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.password_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_password))
                        }
                    }
                }
            }
        }
    }
    
    // Password Generator Dialog
    if (showPasswordGenerator) {
        PasswordGeneratorDialog(
            onDismiss = { showPasswordGenerator = false },
            onPasswordGenerated = { generatedPassword ->
                password = generatedPassword
                showPasswordGenerator = false
            }
        )
    }
}

/**
 * 彩色密码显示转换器
 * 白色=字母, 蓝色=数字, 红色=符号
 */
class ColoredPasswordVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val coloredText = buildAnnotatedString {
            text.forEach { char ->
                when {
                    char.isLetter() -> {
                        // 字母 - 白色/浅色
                        withStyle(style = SpanStyle(color = Color(0xFFE0E0E0))) {
                            append(char)
                        }
                    }
                    char.isDigit() -> {
                        // 数字 - 蓝色
                        withStyle(style = SpanStyle(color = Color(0xFF64B5F6))) {
                            append(char)
                        }
                    }
                    else -> {
                        // 符号 - 红色
                        withStyle(style = SpanStyle(color = Color(0xFFEF5350))) {
                            append(char)
                        }
                    }
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
    
    // Generate initial password
    LaunchedEffect(Unit) {
        try {
            generatedPassword = passwordGenerator.generatePassword(
                PasswordGenerator.PasswordOptions(
                    length = length,
                    includeUppercase = includeUppercase,
                    includeLowercase = includeLowercase,
                    includeNumbers = includeNumbers,
                    includeSymbols = includeSymbols,
                    excludeSimilar = excludeSimilar
                )
            )
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_generator)) },
        text = {
            Column {
                // Generated Password Display
                OutlinedTextField(
                    value = generatedPassword,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                try {
                                    generatedPassword = passwordGenerator.generatePassword(
                                        PasswordGenerator.PasswordOptions(
                                            length = length,
                                            includeUppercase = includeUppercase,
                                            includeLowercase = includeLowercase,
                                            includeNumbers = includeNumbers,
                                            includeSymbols = includeSymbols,
                                            excludeSimilar = excludeSimilar
                                        )
                                    )
                                } catch (e: Exception) {
                                    // Handle error
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate_password))
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Length Slider
                Text(stringResource(R.string.length_value, length))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { 
                        length = it.toInt()
                        try {
                            generatedPassword = passwordGenerator.generatePassword(
                                PasswordGenerator.PasswordOptions(
                                    length = length,
                                    includeUppercase = includeUppercase,
                                    includeLowercase = includeLowercase,
                                    includeNumbers = includeNumbers,
                                    includeSymbols = includeSymbols,
                                    excludeSimilar = excludeSimilar
                                )
                            )
                        } catch (e: Exception) {
                            // Handle error
                        }
                    },
                    valueRange = 8f..32f,
                    steps = 24
                )
                
                // Options Checkboxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.uppercase_az))
                    Checkbox(
                        checked = includeUppercase,
                        onCheckedChange = { 
                            includeUppercase = it
                            try {
                                generatedPassword = passwordGenerator.generatePassword(
                                    PasswordGenerator.PasswordOptions(
                                        length = length,
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols,
                                        excludeSimilar = excludeSimilar
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.lowercase_az))
                    Checkbox(
                        checked = includeLowercase,
                        onCheckedChange = { 
                            includeLowercase = it
                            try {
                                generatedPassword = passwordGenerator.generatePassword(
                                    PasswordGenerator.PasswordOptions(
                                        length = length,
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols,
                                        excludeSimilar = excludeSimilar
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.numbers_09))
                    Checkbox(
                        checked = includeNumbers,
                        onCheckedChange = { 
                            includeNumbers = it
                            try {
                                generatedPassword = passwordGenerator.generatePassword(
                                    PasswordGenerator.PasswordOptions(
                                        length = length,
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols,
                                        excludeSimilar = excludeSimilar
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.symbols))
                    Checkbox(
                        checked = includeSymbols,
                        onCheckedChange = { 
                            includeSymbols = it
                            try {
                                generatedPassword = passwordGenerator.generatePassword(
                                    PasswordGenerator.PasswordOptions(
                                        length = length,
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols,
                                        excludeSimilar = excludeSimilar
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.exclude_similar))
                    Checkbox(
                        checked = excludeSimilar,
                        onCheckedChange = { 
                            excludeSimilar = it
                            try {
                                generatedPassword = passwordGenerator.generatePassword(
                                    PasswordGenerator.PasswordOptions(
                                        length = length,
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols,
                                        excludeSimilar = excludeSimilar
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPasswordGenerated(generatedPassword) }
            ) {
                Text(stringResource(R.string.use_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}