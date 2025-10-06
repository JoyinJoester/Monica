package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import takagi.ru.monica.R
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.ui.components.ImagePicker
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
    
    var title by remember { mutableStateOf("") }
    var documentNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var issuedDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var issuedBy by remember { mutableStateOf("") }
    var documentType by remember { mutableStateOf(DocumentType.ID_CARD) }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var showDocumentTypeMenu by remember { mutableStateOf(false) }
    var showDocumentNumber by remember { mutableStateOf(false) }
    
    // 图片路径列表
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 如果是编辑模式，加载现有数据
    LaunchedEffect(documentId) {
        documentId?.let {
            viewModel.getDocumentById(it)?.let { item ->
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
                
                viewModel.parseDocumentData(item.itemData)?.let { data ->
                    documentNumber = data.documentNumber
                    fullName = data.fullName
                    issuedDate = data.issuedDate
                    expiryDate = data.expiryDate
                    issuedBy = data.issuedBy
                    documentType = data.documentType
                }
            }
        }
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
                            val documentData = DocumentData(
                                documentNumber = documentNumber,
                                fullName = fullName,
                                issuedDate = issuedDate,
                                expiryDate = expiryDate,
                                issuedBy = issuedBy,
                                documentType = documentType
                            )
                            
                            val imagePathsJson = Json.encodeToString(imagePaths)
                            
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
                        },
                        enabled = documentNumber.isNotBlank()
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
                label = { Text(stringResource(R.string.document_name)) },
                placeholder = { Text(stringResource(R.string.document_name_example)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 证件类型选择
            ExposedDropdownMenuBox(
                expanded = showDocumentTypeMenu,
                onExpandedChange = { showDocumentTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = when (documentType) {
                        DocumentType.ID_CARD -> stringResource(R.string.id_card)
                        DocumentType.PASSPORT -> stringResource(R.string.passport)
                        DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                        DocumentType.SOCIAL_SECURITY -> "Social Security Card" // 需要添加这个字符串
                        DocumentType.OTHER -> stringResource(R.string.other_document)
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.document_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDocumentTypeMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
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
                        text = { Text(stringResource(R.string.other_document)) },
                        onClick = {
                            documentType = DocumentType.OTHER
                            showDocumentTypeMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                    )
                }
            }
            
            // 证件号码
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
                }
            )
            
            // 持有人
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text(stringResource(R.string.holder_name)) },
                placeholder = { Text(stringResource(R.string.holder_name_example)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 签发日期
            OutlinedTextField(
                value = issuedDate,
                onValueChange = { issuedDate = it },
                label = { Text(stringResource(R.string.issue_date)) },
                placeholder = { Text("2020-01-01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
            )
            
            // 有效期至
            OutlinedTextField(
                value = expiryDate,
                onValueChange = { expiryDate = it },
                label = { Text(stringResource(R.string.expiry_date_label)) },
                placeholder = { Text("2030-01-01 或 长期") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
            )
            
            // 签发机关
            OutlinedTextField(
                value = issuedBy,
                onValueChange = { issuedBy = it },
                label = { Text(stringResource(R.string.issuing_authority)) },
                placeholder = { Text(stringResource(R.string.issuing_authority_example)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 备注
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 4
            )
            
            // 证件照片（正面）
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
                onImageDownloaded = { success ->
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.download_success) 
                        else context.getString(R.string.download_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.document_photo_front, when (documentType) {
                    DocumentType.ID_CARD -> stringResource(R.string.id_card)
                    DocumentType.PASSPORT -> stringResource(R.string.passport)
                    DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                    DocumentType.SOCIAL_SECURITY -> stringResource(R.string.social_security_card)
                    DocumentType.OTHER -> stringResource(R.string.other_document)
                })
            )
            
            // 证件照片（反面）
            if (documentType != DocumentType.PASSPORT) { // 护照通常不需要反面
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
                    onImageDownloaded = { success ->
                        Toast.makeText(
                            context,
                            if (success) context.getString(R.string.download_success) 
                            else context.getString(R.string.download_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.document_photo_back, when (documentType) {
                        DocumentType.ID_CARD -> stringResource(R.string.id_card)
                        DocumentType.PASSPORT -> stringResource(R.string.passport)
                        DocumentType.DRIVER_LICENSE -> stringResource(R.string.drivers_license)
                        DocumentType.SOCIAL_SECURITY -> stringResource(R.string.social_security_card)
                        DocumentType.OTHER -> stringResource(R.string.other_document)
                    })
                )
            }
            
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
                        text = stringResource(R.string.document_encryption_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
