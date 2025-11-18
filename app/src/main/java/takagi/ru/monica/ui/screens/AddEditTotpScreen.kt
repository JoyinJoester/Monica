package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

/**
 * 添加/编辑TOTP验证器页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTotpScreen(
    totpId: Long?,
    initialData: TotpData?,
    initialTitle: String,
    initialNotes: String,
    onSave: (title: String, notes: String, totpData: TotpData) -> Unit,
    onNavigateBack: () -> Unit,
    onScanQrCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf(initialNotes) }
    var secret by remember { mutableStateOf(initialData?.secret ?: "") }
    var issuer by remember { mutableStateOf(initialData?.issuer ?: "") }
    var accountName by remember { mutableStateOf(initialData?.accountName ?: "") }
    var period by remember { mutableStateOf(initialData?.period?.toString() ?: "30") }
    var digits by remember { mutableStateOf(initialData?.digits?.toString() ?: "6") }
    var selectedOtpType by remember { mutableStateOf(initialData?.otpType ?: OtpType.TOTP) }
    var counter by remember { mutableStateOf(initialData?.counter?.toString() ?: "0") }
    var pin by remember { mutableStateOf(initialData?.pin ?: "") }
    var showAdvanced by remember { mutableStateOf(false) }
    var expandedOtpType by remember { mutableStateOf(false) }
    
    // 根据OTP类型自动调整digits
    LaunchedEffect(selectedOtpType) {
        when (selectedOtpType) {
            OtpType.STEAM -> digits = "5"
            OtpType.TOTP, OtpType.HOTP, OtpType.YANDEX, OtpType.MOTP -> {
                if (digits == "5") digits = "6"
            }
        }
    }
    
    val isEditing = totpId != null && totpId > 0
    val canSave = title.isNotBlank() && secret.isNotBlank()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.edit_totp_title else R.string.add_totp_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (canSave) {
                                val totpData = TotpData(
                                    secret = secret.trim(),
                                    issuer = issuer.trim(),
                                    accountName = accountName.trim(),
                                    period = period.toIntOrNull() ?: 30,
                                    digits = digits.toIntOrNull() ?: 6,
                                    algorithm = "SHA1",
                                    otpType = selectedOtpType,
                                    counter = counter.toLongOrNull() ?: 0L,
                                    pin = pin.trim()
                                )
                                onSave(title, notes, totpData)
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(stringResource(R.string.save))
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
                .padding(16.dp)
        ) {
            // 标题
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.totp_name_required)) },
                placeholder = { Text(stringResource(R.string.totp_name_example)) },
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = title.isBlank()
            )
            
            if (title.isBlank()) {
                Text(
                    text = stringResource(R.string.enter_name),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 密钥输入 + 扫描按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it.uppercase() },
                    label = { Text(stringResource(R.string.secret_key_required)) },
                    placeholder = { Text(stringResource(R.string.secret_key_example)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    isError = secret.isBlank(),
                    supportingText = {
                        Text(stringResource(R.string.secret_key_hint))
                    }
                )
                
                // 扫描二维码按钮
                if (!isEditing) {
                    IconButton(
                        onClick = onScanQrCode,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_qr_code),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            if (secret.isBlank()) {
                Text(
                    text = stringResource(R.string.enter_secret_key),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 发行者
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(stringResource(R.string.issuer)) },
                placeholder = { Text(stringResource(R.string.issuer_example)) },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 账户名
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text(stringResource(R.string.account_name)) },
                placeholder = { Text(stringResource(R.string.account_name_example)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 备注
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                placeholder = { Text(stringResource(R.string.notes_optional)) },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 高级选项
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAdvanced = !showAdvanced }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.advanced_options),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            
            if (showAdvanced) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // OTP类型选择器
                ExposedDropdownMenuBox(
                    expanded = expandedOtpType,
                    onExpandedChange = { expandedOtpType = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedOtpType) {
                            OtpType.TOTP -> stringResource(R.string.otp_type_totp)
                            OtpType.HOTP -> stringResource(R.string.otp_type_hotp)
                            OtpType.STEAM -> stringResource(R.string.otp_type_steam)
                            OtpType.YANDEX -> stringResource(R.string.otp_type_yandex)
                            OtpType.MOTP -> stringResource(R.string.otp_type_motp)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.otp_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOtpType) },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedOtpType,
                        onDismissRequest = { expandedOtpType = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.otp_type_totp))
                                    Text(
                                        stringResource(R.string.otp_type_description_totp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedOtpType = OtpType.TOTP
                                expandedOtpType = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.otp_type_hotp))
                                    Text(
                                        stringResource(R.string.otp_type_description_hotp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedOtpType = OtpType.HOTP
                                expandedOtpType = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.otp_type_steam))
                                    Text(
                                        stringResource(R.string.otp_type_description_steam),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedOtpType = OtpType.STEAM
                                expandedOtpType = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.otp_type_yandex))
                                    Text(
                                        stringResource(R.string.otp_type_description_yandex),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedOtpType = OtpType.YANDEX
                                expandedOtpType = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.otp_type_motp))
                                    Text(
                                        stringResource(R.string.otp_type_description_motp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedOtpType = OtpType.MOTP
                                expandedOtpType = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // HOTP特有: 初始计数器
                if (selectedOtpType == OtpType.HOTP) {
                    OutlinedTextField(
                        value = counter,
                        onValueChange = { counter = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.initial_counter)) },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(stringResource(R.string.hotp_counter_hint)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // mOTP特有: PIN码
                if (selectedOtpType == OtpType.MOTP) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
                        label = { Text(stringResource(R.string.pin_code)) },
                        placeholder = { Text(stringResource(R.string.pin_code_example)) },
                        leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        supportingText = { Text(stringResource(R.string.motp_pin_hint)) },
                        isError = pin.isEmpty()
                    )
                    if (pin.isEmpty()) {
                        Text(
                            text = stringResource(R.string.enter_pin_code),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 时间周期 (HOTP不需要)
                if (selectedOtpType != OtpType.HOTP) {
                    OutlinedTextField(
                        value = period,
                        onValueChange = { period = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.time_period_seconds)) },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(stringResource(R.string.usually_30_seconds)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 验证码位数
                OutlinedTextField(
                    value = digits,
                    onValueChange = { 
                        val newValue = it.filter { char -> char.isDigit() }
                        if (newValue.isEmpty() || newValue.toInt() in 5..8) {
                            digits = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.code_digits)) },
                    leadingIcon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = selectedOtpType != OtpType.STEAM,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { 
                        Text(
                            if (selectedOtpType == OtpType.STEAM) 
                                stringResource(R.string.steam_uses_5_chars)
                            else 
                                stringResource(R.string.usually_6_digits)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 提示卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.how_to_get_secret_key),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.secret_key_instructions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
