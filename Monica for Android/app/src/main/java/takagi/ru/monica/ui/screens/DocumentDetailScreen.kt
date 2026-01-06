package takagi.ru.monica.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.ui.components.ActionStrip
import takagi.ru.monica.ui.components.ActionStripItem
import takagi.ru.monica.ui.components.ImageDialog
import takagi.ru.monica.ui.components.InfoFieldWithCopy
import takagi.ru.monica.ui.components.InfoField
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.viewmodel.DocumentViewModel
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    viewModel: DocumentViewModel,
    documentId: Long,
    onNavigateBack: () -> Unit,
    onEditDocument: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageManager = remember { ImageManager(context) }
    
    var documentItem by remember { mutableStateOf<SecureItem?>(null) }
    var documentData by remember { mutableStateOf<DocumentData?>(null) }
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFrontImageDialog by remember { mutableStateOf(false) }
    var showBackImageDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Load document details
    LaunchedEffect(documentId) {
        viewModel.getDocumentById(documentId)?.let { item ->
            documentItem = item
            
            try {
                documentData = Json.decodeFromString<DocumentData>(item.itemData)
            } catch (e: Exception) {
                // Handle parsing error
            }
            
            try {
                if (item.imagePaths.isNotBlank()) {
                    val paths = Json.decodeFromString<List<String>>(item.imagePaths)
                    if (paths.isNotEmpty() && paths[0].isNotBlank()) frontBitmap = imageManager.loadImage(paths[0])
                    if (paths.size > 1 && paths[1].isNotBlank()) backBitmap = imageManager.loadImage(paths[1])
                }
            } catch (e: Exception) {
                // Handle image loading error
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentItem?.title ?: stringResource(R.string.document_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ActionStrip(
                actions = listOf(
                    ActionStripItem(
                        icon = if (documentItem?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.favorite),
                        onClick = {
                            documentItem?.let { item ->
                                viewModel.toggleFavorite(item.id)
                                documentItem = item.copy(isFavorite = !item.isFavorite)
                            }
                        },
                        tint = if (documentItem?.isFavorite == true) MaterialTheme.colorScheme.primary else null
                    ),
                    ActionStripItem(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = { onEditDocument(documentId) }
                    ),
                    ActionStripItem(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = { showDeleteDialog = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                ),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    ) { paddingValues ->
        documentData?.let { data ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card using M3E color roles based on type
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (data.documentType) {
                            DocumentType.ID_CARD -> MaterialTheme.colorScheme.primaryContainer
                            DocumentType.PASSPORT -> MaterialTheme.colorScheme.secondaryContainer
                            DocumentType.DRIVER_LICENSE -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (data.documentType) {
                                    DocumentType.ID_CARD -> Icons.Default.Badge
                                    DocumentType.PASSPORT -> Icons.Default.FlightTakeoff
                                    DocumentType.DRIVER_LICENSE -> Icons.Default.DirectionsCar
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null
                            )
                            Text(
                                text = getDocumentTypeName(data.documentType),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Divider(color = LocalContentColor.current.copy(alpha = 0.2f))
                        
                        InfoFieldWithCopy(
                            label = stringResource(R.string.document_number),
                            value = data.documentNumber,
                            context = context
                        )
                        
                         if (data.fullName.isNotBlank()) {
                            InfoFieldWithCopy(
                                label = stringResource(R.string.full_name),
                                value = data.fullName,
                                context = context
                            )
                        }
                    }
                }
                
                // Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                         Text(
                            text = stringResource(R.string.details),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (data.issuedDate.isNotBlank() || data.expiryDate.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (data.issuedDate.isNotBlank()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoField(
                                            label = stringResource(R.string.issued_date),
                                            value = data.issuedDate
                                        )
                                    }
                                }
                                if (data.expiryDate.isNotBlank()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoField(
                                            label = stringResource(R.string.expiry_date),
                                            value = data.expiryDate
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (data.issuedBy.isNotBlank()) {
                             InfoField(
                                label = stringResource(R.string.issued_by),
                                value = data.issuedBy
                            )
                        }
                        
                         if (data.nationality.isNotBlank()) {
                             InfoField(
                                label = stringResource(R.string.nationality),
                                value = data.nationality
                            )
                        }
                    }
                }
                
                // Images
                if (frontBitmap != null || backBitmap != null) {
                     Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.document_images),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                             if (frontBitmap != null) {
                                Image(
                                    bitmap = frontBitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.front_image),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showFrontImageDialog = true },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (backBitmap != null) {
                                Image(
                                    bitmap = backBitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.back_image),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showBackImageDialog = true },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                
                // Notes
                 if (!documentItem?.notes.isNullOrBlank()) {
                     Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                             Text(
                                text = stringResource(R.string.notes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = documentItem?.notes ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // Dialogs
    if (showFrontImageDialog && frontBitmap != null) {
        ImageDialog(bitmap = frontBitmap!!, onDismiss = { showFrontImageDialog = false })
    }
    
    if (showBackImageDialog && backBitmap != null) {
        ImageDialog(bitmap = backBitmap!!, onDismiss = { showBackImageDialog = false })
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message)) },
            confirmButton = {
                TextButton(
                     onClick = {
                        documentItem?.let { viewModel.deleteDocument(it.id) }
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun getDocumentTypeName(type: DocumentType): String {
    return when (type) {
        DocumentType.ID_CARD -> "身份证"
        DocumentType.PASSPORT -> "护照"
        DocumentType.DRIVER_LICENSE -> "驾驶证"
        DocumentType.SOCIAL_SECURITY -> "社保卡"
        DocumentType.OTHER -> "其他证件"
    }
}
