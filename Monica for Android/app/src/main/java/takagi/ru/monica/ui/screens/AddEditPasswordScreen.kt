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

    var title by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // CHANGE: Support multiple passwords
    val passwords = rememberSaveable(saver = takagi.ru.monica.utils.StringListSaver) { mutableStateListOf("") }
    var originalIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    
    var authenticatorKey by rememberSaveable { mutableStateOf("") }
    var existingTotpId by remember { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    // Control which password field is showing generator/visibility (simplified: global visibility)
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var currentPasswordIndexForGenerator by remember { mutableStateOf(-1) }

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
    
    // SSO 登录方式字段
    var loginType by rememberSaveable { mutableStateOf("PASSWORD") }
    var ssoProvider by rememberSaveable { mutableStateOf("") }
    var ssoRefEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    
    // 获取所有密码条目用于SSO关联选择
    val allPasswordsForRef by viewModel.allPasswords.collectAsState(initial = emptyList())

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
                    authenticatorKey = entry.authenticatorKey  // ✅ 从密码条目中读取验证器密钥
                    
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
                                    categoryId = categoryId,
                                    authenticatorKey = currentAuthKey,  // ✅ 保存验证器密钥
                                    loginType = loginType,
                                    ssoProvider = ssoProvider,
                                    ssoRefEntryId = ssoRefEntryId
                                )
                                
                                viewModel.saveGroupedPasswords(
                                    originalIds = originalIds,
                                    commonEntry = commonEntry,
                                    passwords = passwords.toList(), // Snapshot
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
            // Credentials Card
            item {
                InfoCard(title = "凭据") {
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
                        
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            val selectedCategoryName = categories.find { it.id == categoryId }?.name ?: "无分类"

                            OutlinedTextField(
                                value = selectedCategoryName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("分类") },
                                leadingIcon = { Icon(Icons.Default.Category, null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false },
                                modifier = Modifier
                                    .exposedDropdownSize()
                                    .clip(RoundedCornerShape(18.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("无分类") },
                                    leadingIcon = { Icon(Icons.Default.FolderOff, null) },
                                    trailingIcon = if (categoryId == null) { { Icon(Icons.Default.Check, null) } } else null,
                                    onClick = {
                                        categoryId = null
                                        categoryExpanded = false
                                    }
                                )

                                categories.forEach { category ->
                                    val selected = categoryId == category.id
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                                        trailingIcon = if (selected) { { Icon(Icons.Default.Check, null) } } else null,
                                        onClick = {
                                            categoryId = category.id
                                            categoryExpanded = false
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("新建分类")
                                        }
                                    },
                                    onClick = {
                                        categoryExpanded = false
                                        showAddCategoryDialog = true
                                    }
                                )
                            }
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
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                                Icon(
                                    Icons.Default.Search, 
                                    contentDescription = "选择",
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
                Text(
                    text = context.getString(R.string.sso_description),
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