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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.components.AppSelectorField
import takagi.ru.monica.ui.components.PasswordStrengthIndicator
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.utils.PasswordStrengthAnalyzer
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(
    viewModel: PasswordViewModel,
    totpViewModel: TotpViewModel? = null,
    bankCardViewModel: BankCardViewModel? = null,
    passwordId: Long?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val passwordGenerator = remember { PasswordGenerator() }

    // 获取设置以读取进度条样式
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val settings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())

    var title by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    // CHANGE: Support multiple passwords
    val passwords = remember { mutableStateListOf("") }
    var originalIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    
    var authenticatorKey by remember { mutableStateOf("") }
    var existingTotpId by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    // Control which password field is showing generator/visibility (simplified: global visibility)
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var currentPasswordIndexForGenerator by remember { mutableStateOf(-1) }

    var appPackageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }

    // 绑定选项状态
    var bindTitle by remember { mutableStateOf(false) }
    var bindWebsite by remember { mutableStateOf(false) }

    // 新增字段状态 - 支持多个邮箱和电话
    val emails = remember { mutableStateListOf("") }
    val phones = remember { mutableStateListOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var creditCardNumber by remember { mutableStateOf("") }
    var creditCardHolder by remember { mutableStateOf("") }
    var creditCardExpiry by remember { mutableStateOf("") }
    var creditCardCVV by remember { mutableStateOf("") }

    var categoryId by remember { mutableStateOf<Long?>(null) }
    val categories by viewModel.categories.collectAsState()

    // 折叠面板状态
    var personalInfoExpanded by remember { mutableStateOf(false) }
    var addressInfoExpanded by remember { mutableStateOf(false) }
    var paymentInfoExpanded by remember { mutableStateOf(false) }

    val isEditing = passwordId != null && passwordId > 0

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
                        
                        // Load linked TOTP if exists
                        viewModel.getLinkedTotpFlow(actualId).first()?.let { totpData ->
                            authenticatorKey = totpData.secret
                            // Find the TOTP item ID for update
                            // We need to query the SecureItem by boundPasswordId
                            // This is a simplification - ideally we'd get the ID directly
                        }
                    } else {
                        passwords.add("")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isEditing) R.string.edit_password_title else R.string.add_password_title),
                        style = MaterialTheme.typography.titleLarge
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
                    TextButton(
                        onClick = {
                            if (title.isNotEmpty()) {
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
                                    categoryId = categoryId
                                )
                                
                                viewModel.saveGroupedPasswords(
                                    originalIds = originalIds,
                                    commonEntry = commonEntry,
                                    passwords = passwords.toList(), // Snapshot
                                    onComplete = { firstPasswordId ->
                                        // Save TOTP if authenticatorKey is provided
                                        if (currentAuthKey.isNotEmpty() && firstPasswordId != null && totpViewModel != null) {
                                            val totpData = TotpData(
                                                secret = currentAuthKey,
                                                issuer = currentTitle,
                                                accountName = currentUsername,
                                                boundPasswordId = firstPasswordId
                                            )
                                            totpViewModel.saveTotpItem(
                                                id = null, // Always create new
                                                title = currentTitle,
                                                notes = "",
                                                totpData = totpData,
                                                isFavorite = false
                                            )
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
                        },
                        enabled = title.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            )
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
            // Header Section - Title Input
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_required)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Credentials Card
            item {
                InfoCard(title = "凭据") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.username_email)) },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            trailingIcon = if (username.isNotEmpty()) {
                                {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("username", username)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.username_copied), Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Passwords
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
                                        style = settings.validatorProgressBarStyle,
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
            
            // Security Card (TOTP)
            item {
                InfoCard(title = "安全验证") {
                    OutlinedTextField(
                        value = authenticatorKey,
                        onValueChange = { authenticatorKey = it },
                        label = { Text("验证码密钥 (可选)") },
                        placeholder = { Text("输入密钥以自动创建验证器") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Organization Card
            item {
                InfoCard(title = "分类与备注") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Category Selector
                        var categoryExpanded by remember { mutableStateOf(false) }
                        var showAddCategoryDialog by remember { mutableStateOf(false) }
                        var newCategoryName by remember { mutableStateOf("") }
                        
                        Box {
                            OutlinedTextField(
                                value = categories.find { it.id == categoryId }?.name ?: "无分类",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("分类") },
                                leadingIcon = { Icon(Icons.Default.Category, null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier.fillMaxWidth().clickable { categoryExpanded = true },
                                enabled = false, // Disable text input, handle click on Box or Overlay
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            // Invisible overlay to capture clicks since TextField is disabled for readOnly look
                            Box(modifier = Modifier.matchParentSize().clickable { categoryExpanded = true })
                        }

                        // Dropdown Logic (kept simple but functional)
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(text = { Text("无分类") }, onClick = { categoryId = null; categoryExpanded = false })
                            categories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; categoryExpanded = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("新建分类") } },
                                onClick = { categoryExpanded = false; showAddCategoryDialog = true }
                            )
                        }
                        
                        // Add Category Dialog
                        if (showAddCategoryDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddCategoryDialog = false },
                                title = { Text("新建分类") },
                                text = { OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("分类名称") }, singleLine = true) },
                                confirmButton = {
                                    TextButton(onClick = { if (newCategoryName.isNotBlank()) { viewModel.addCategory(newCategoryName) { id -> if (id > 0) categoryId = id }; newCategoryName = ""; showAddCategoryDialog = false } }) { Text("确定") }
                                },
                                dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("取消") } }
                            )
                        }

                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.notes)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            
            // App Binding Card
            item {
                InfoCard(title = "应用关联") {
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
                                        Text("绑定网址", style = MaterialTheme.typography.bodyMedium)
                                        Text("将该应用关联到所有相同网址的密码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { bindTitle = !bindTitle }
                                ) {
                                    Checkbox(checked = bindTitle, onCheckedChange = { bindTitle = it })
                                    Column {
                                        Text("绑定标题", style = MaterialTheme.typography.bodyMedium)
                                        Text("将该应用关联到所有相同标题的密码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible: Personal Info
            item {
                CollapsibleCard(
                    title = stringResource(R.string.personal_info),
                    icon = Icons.Default.Person,
                    expanded = personalInfoExpanded,
                    onExpandChange = { personalInfoExpanded = it }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Multiple Email Fields
                        Text(
                            text = stringResource(R.string.field_email),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
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
                                            contentDescription = "删除",
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
                            Text("添加邮箱")
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Multiple Phone Fields
                        Text(
                            text = stringResource(R.string.field_phone),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
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
                                            contentDescription = "删除",
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
                            Text("添加电话")
                        }
                    }
                }
            }

            // Collapsible: Address Info
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

            // Collapsible: Payment Info
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
                                Text("从银行卡导入")
                            }
                            
                            // Bank Card Selection Logic (retained)
                            if (showBankCardDialog) {
                                val bankCards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
                                AlertDialog(
                                    onDismissRequest = { showBankCardDialog = false },
                                    title = { Text("选择银行卡") },
                                    text = {
                                        if (bankCards.isEmpty()) Text("暂无银行卡数据")
                                        else LazyColumn {
                                            items(bankCards) { item ->
                                                val cardData = try { Json.decodeFromString<BankCardData>(item.itemData) } catch (e: Exception) { null }
                                                ListItem(
                                                    headlineContent = { Text(item.title) },
                                                    supportingContent = { Text(if (cardData != null) "尾号 ${cardData.cardNumber.takeLast(4)}" else "解析失败") },
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
                                                            Toast.makeText(context, "已导入", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showBankCardDialog = false }) { Text("取消") } }
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