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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.ui.components.DualPhotoPicker
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDocumentScreen(
    viewModel: DocumentViewModel,
    documentId: Long? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var title by rememberSaveable { mutableStateOf("") }
    var documentNumber by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var issuedDate by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    var issuedBy by rememberSaveable { mutableStateOf("") }
    var nationality by rememberSaveable { mutableStateOf("") } // 添加国籍字段
    var documentType by rememberSaveable { mutableStateOf(DocumentType.ID_CARD) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var showDocumentTypeMenu by remember { mutableStateOf(false) }
    
    // 防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }
    var showDocumentNumber by remember { mutableStateOf(false) }
    
    // 图片路径管理
    var frontImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var backImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 如果是编辑模式，加载现有数据
    LaunchedEffect(documentId) {
        documentId?.let {
            viewModel.getDocumentById(it)?.let { item ->
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
                
                viewModel.parseDocumentData(item.itemData)?.let { data -> 
                    documentNumber = data.documentNumber
                    fullName = data.fullName
                    issuedDate = data.issuedDate
                    expiryDate = data.expiryDate
                    issuedBy = data.issuedBy
                    nationality = data.nationality // 加载国籍信息
                    documentType = data.documentType
                }
            }
        }
    }
    
    val canSave = documentNumber.isNotBlank() && !isSaving
    val save: () -> Unit = saveAction@{
        if (isSaving || documentNumber.isBlank()) return@saveAction
        isSaving = true // 防止重复点击

        val documentData = DocumentData(
            documentNumber = documentNumber,
            fullName = fullName,
            issuedDate = issuedDate,
            expiryDate = expiryDate,
            issuedBy = issuedBy,
            nationality = nationality, // 保存国籍信息
            documentType = documentType
        )

        val imagePathsList = listOf(
            frontImageFileName ?: "",
            backImageFileName ?: ""
        )
        val imagePathsJson = Json.encodeToString(imagePathsList)

        if (documentId == null) {
            viewModel.addDocument(
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
                imagePaths = imagePathsJson
            )
        } else {
            viewModel.updateDocument(
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
                imagePaths = imagePathsJson
            )
        }
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (documentId == null) R.string.add_document_title else R.string.edit_document_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
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
                    
                    // Holder Name
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(stringResource(R.string.holder_name)) },
                        placeholder = { Text(stringResource(R.string.holder_name_example)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
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
