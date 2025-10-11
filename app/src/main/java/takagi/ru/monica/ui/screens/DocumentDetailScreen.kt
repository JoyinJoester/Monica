package takagi.ru.monica.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.ui.components.ImageDialog
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.viewmodel.DocumentViewModel
import kotlinx.serialization.json.Json
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

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
    val scope = rememberCoroutineScope()
    val imageManager = remember { ImageManager(context) }
    
    var documentItem by remember { mutableStateOf<SecureItem?>(null) }
    var documentData by remember { mutableStateOf<DocumentData?>(null) }
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFrontImageDialog by remember { mutableStateOf(false) }
    var showBackImageDialog by remember { mutableStateOf(false) }
    var imagePathsList by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 加载证件详情
    LaunchedEffect(documentId) {
        viewModel.getDocumentById(documentId)?.let { item ->
            documentItem = item
            
            // 解析证件数据
            try {
                documentData = Json.decodeFromString<DocumentData>(item.itemData)
            } catch (e: Exception) {
                Toast.makeText(context, "解析证件数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            
            // 解析图片路径
            try {
                if (item.imagePaths.isNotBlank()) {
                    imagePathsList = Json.decodeFromString<List<String>>(item.imagePaths)
                    if (imagePathsList.isNotEmpty() && imagePathsList[0].isNotBlank()) {
                        // 加载正面图片
                        frontBitmap = imageManager.loadImage(imagePathsList[0])
                    }
                    if (imagePathsList.size > 1 && imagePathsList[1].isNotBlank()) {
                        // 加载背面图片
                        backBitmap = imageManager.loadImage(imagePathsList[1])
                    }
                }
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentItem?.title ?: "证件详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // 编辑按钮
                    IconButton(onClick = { onEditDocument(documentId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    
                    // 更多选项菜单
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 收藏/取消收藏
                        DropdownMenuItem(
                            text = { Text(if (documentItem?.isFavorite == true) "取消收藏" else "收藏") },
                            onClick = {
                                documentItem?.let { item ->
                                    viewModel.toggleFavorite(item.id)
                                    showMenu = false
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    if (documentItem?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null
                                )
                            }
                        )
                        
                        // 删除
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                documentItem?.let { item ->
                                    viewModel.deleteDocument(item.id)
                                    showMenu = false
                                    onNavigateBack()
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
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
                // 证件类型和基本信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (data.documentType) {
                            DocumentType.ID_CARD -> MaterialTheme.colorScheme.primaryContainer
                            DocumentType.PASSPORT -> MaterialTheme.colorScheme.secondaryContainer
                            DocumentType.DRIVER_LICENSE -> MaterialTheme.colorScheme.tertiaryContainer
                            DocumentType.SOCIAL_SECURITY -> MaterialTheme.colorScheme.surfaceVariant
                            DocumentType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 证件类型
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "证件类型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    when (data.documentType) {
                                        DocumentType.ID_CARD -> Icons.Default.Badge
                                        DocumentType.PASSPORT -> Icons.Default.FlightTakeoff
                                        DocumentType.DRIVER_LICENSE -> Icons.Default.DirectionsCar
                                        DocumentType.SOCIAL_SECURITY -> Icons.Default.HealthAndSafety
                                        DocumentType.OTHER -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = getDocumentTypeName(data.documentType),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        // 证件号码
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "证件号码",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = data.documentNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        
                        // 持有人
                        if (data.fullName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "持有人",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data.fullName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // 日期信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 签发日期
                        if (data.issuedDate.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "签发日期",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data.issuedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        // 有效期至
                        if (data.expiryDate.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "有效期至",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data.expiryDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        // 签发机关
                        if (data.issuedBy.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "签发机关",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data.issuedBy,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        
                        // 国籍（仅护照显示）
                        if (data.documentType == DocumentType.PASSPORT && data.nationality.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "国籍",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data.nationality,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // 照片部分
                if (frontBitmap != null || backBitmap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "证件照片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            
                            // 正面照片
                            if (frontBitmap != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "正面照片",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        bitmap = frontBitmap!!.asImageBitmap(),
                                        contentDescription = "正面照片",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showFrontImageDialog = true },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            
                            // 背面照片
                            if (backBitmap != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "背面照片",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        bitmap = backBitmap!!.asImageBitmap(),
                                        contentDescription = "背面照片",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showBackImageDialog = true },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 备注
                if (!documentItem?.notes.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "备注",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = documentItem?.notes ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 全屏图片查看对话框
    if (showFrontImageDialog && frontBitmap != null) {
        ImageDialog(
            bitmap = frontBitmap!!,
            onDismiss = { showFrontImageDialog = false }
        )
    }
    
    if (showBackImageDialog && backBitmap != null) {
        ImageDialog(
            bitmap = backBitmap!!,
            onDismiss = { showBackImageDialog = false }
        )
    }
}

/**
 * 获取证件类型名称
 */
private fun getDocumentTypeName(type: DocumentType): String {
    return when (type) {
        DocumentType.ID_CARD -> "身份证"
        DocumentType.PASSPORT -> "护照"
        DocumentType.DRIVER_LICENSE -> "驾驶证"
        DocumentType.SOCIAL_SECURITY -> "社保卡"
        DocumentType.OTHER -> "其他证件"
    }
}