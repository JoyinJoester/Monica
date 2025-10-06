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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.PasswordGenerator
import takagi.ru.monica.viewmodel.PasswordViewModel

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
    
    val isEditing = passwordId != null
    
    // Load existing password data if editing
    LaunchedEffect(passwordId) {
        if (passwordId != null) {
            coroutineScope.launch {
                viewModel.getPasswordEntryById(passwordId)?.let { entry ->
                    title = entry.title
                    website = entry.website
                    username = entry.username
                    password = entry.password
                    notes = entry.notes
                    isFavorite = entry.isFavorite
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.edit_password_title else R.string.add_password_title)) },
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
                    
                    TextButton(
                        onClick = {
                            if (title.isNotEmpty() && password.isNotEmpty()) {
                                val entry = PasswordEntry(
                                    id = passwordId ?: 0,
                                    title = title,
                                    website = website,
                                    username = username,
                                    password = password,
                                    notes = notes,
                                    isFavorite = isFavorite
                                )
                                
                                if (isEditing) {
                                    viewModel.updatePasswordEntry(entry)
                                } else {
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
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
            
            // Password Strength Indicator
            if (password.isNotEmpty()) {
                val strength = passwordGenerator.calculatePasswordStrength(password)
                val strengthColor = when {
                    strength >= 80 -> MaterialTheme.colorScheme.primary
                    strength >= 60 -> MaterialTheme.colorScheme.secondary
                    strength >= 40 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = strength / 100f,
                        modifier = Modifier.weight(1f),
                        color = strengthColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = passwordGenerator.getPasswordStrengthDescription(strength),
                        style = MaterialTheme.typography.bodySmall,
                        color = strengthColor
                    )
                }
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