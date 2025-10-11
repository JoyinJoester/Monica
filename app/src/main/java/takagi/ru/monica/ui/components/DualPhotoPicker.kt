package takagi.ru.monica.ui.components

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.util.PhotoPickerHelper

/**
 * 双面照片选择器组件
 * 专门用于处理正面和背面照片的显示和选择
 */
@Composable
fun DualPhotoPicker(
    frontImageFileName: String?,
    backImageFileName: String?,
    onFrontImageSelected: (String) -> Unit,
    onFrontImageRemoved: () -> Unit,
    onBackImageSelected: (String) -> Unit,
    onBackImageRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    frontLabel: String = "正面照片",
    backLabel: String = "背面照片"
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val imageManager = remember { ImageManager(context) }
    val scope = rememberCoroutineScope()
    
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFrontImageDialog by remember { mutableStateOf(false) }
    var showBackImageDialog by remember { mutableStateOf(false) }
    
    // 加载正面图片
    LaunchedEffect(frontImageFileName) {
        frontBitmap = if (!frontImageFileName.isNullOrEmpty()) {
            imageManager.loadImage(frontImageFileName)
        } else {
            null
        }
    }
    
    // 加载背面图片
    LaunchedEffect(backImageFileName) {
        backBitmap = if (!backImageFileName.isNullOrEmpty()) {
            imageManager.loadImage(backImageFileName)
        } else {
            null
        }
    }
    
    // 设置照片选择回调
    LaunchedEffect(Unit) {
        PhotoPickerHelper.setCallback(context, object : PhotoPickerHelper.PhotoPickerCallback {
            override fun onPhotoSelected(imagePath: String?) {
                imagePath?.let { path ->
                    scope.launch {
                        try {
                            // 检查文件是否存在
                            val file = java.io.File(path)
                            if (!file.exists() || file.length() == 0L) {
                                Toast.makeText(context, "文件不存在或为空", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            
                            // 保存图片
                            val uri = android.net.Uri.fromFile(file)
                            val fileName = imageManager.saveImageFromUri(uri)
                            if (fileName != null) {
                                // 根据标签判断是正面还是背面
                                if (PhotoPickerHelper.currentTag == "front") {
                                    onFrontImageSelected(fileName)
                                } else if (PhotoPickerHelper.currentTag == "back") {
                                    onBackImageSelected(fileName)
                                }
                                // 删除临时文件
                                file.delete()
                            } else {
                                Toast.makeText(context, "保存图片失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            override fun onError(error: String) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 正面照片
        PhotoCard(
            bitmap = frontBitmap,
            label = frontLabel,
            onImageSelected = {
                PhotoPickerHelper.currentTag = "front"
                activity?.let { act ->
                    PhotoPickerHelper.pickFromGallery(act)
                } ?: run {
                    Toast.makeText(context, "无法启动相册", Toast.LENGTH_SHORT).show()
                }
            },
            onImageCaptured = {
                PhotoPickerHelper.currentTag = "front"
                activity?.let { act ->
                    PhotoPickerHelper.takePhoto(act)
                } ?: run {
                    Toast.makeText(context, "无法启动相机，将使用图库选择", Toast.LENGTH_SHORT).show()
                    // 直接打开图库作为替代
                    PhotoPickerHelper.currentTag = "front"
                    activity?.let { act ->
                        PhotoPickerHelper.pickFromGallery(act)
                    } ?: run {
                        Toast.makeText(context, "无法启动图库", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImageRemoved = {
                scope.launch {
                    frontImageFileName?.let { 
                        imageManager.deleteImage(it)
                        onFrontImageRemoved()
                    }
                }
            },
            onImageClicked = { showFrontImageDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
        
        // 背面照片
        PhotoCard(
            bitmap = backBitmap,
            label = backLabel,
            onImageSelected = {
                PhotoPickerHelper.currentTag = "back"
                activity?.let { act ->
                    PhotoPickerHelper.pickFromGallery(act)
                } ?: run {
                    Toast.makeText(context, "无法启动相册", Toast.LENGTH_SHORT).show()
                }
            },
            onImageCaptured = {
                PhotoPickerHelper.currentTag = "back"
                activity?.let { act ->
                    PhotoPickerHelper.takePhoto(act)
                } ?: run {
                    Toast.makeText(context, "无法启动相机，将使用图库选择", Toast.LENGTH_SHORT).show()
                    // 直接打开图库作为替代
                    PhotoPickerHelper.currentTag = "back"
                    activity?.let { act ->
                        PhotoPickerHelper.pickFromGallery(act)
                    } ?: run {
                        Toast.makeText(context, "无法启动图库", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImageRemoved = {
                scope.launch {
                    backImageFileName?.let { 
                        imageManager.deleteImage(it)
                        onBackImageRemoved()
                    }
                }
            },
            onImageClicked = { showBackImageDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
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
 * 照片卡片组件
 */
@Composable
private fun PhotoCard(
    bitmap: Bitmap?,
    label: String,
    onImageSelected: () -> Unit,
    onImageCaptured: () -> Unit,
    onImageRemoved: () -> Unit,
    onImageClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (bitmap != null) {
                // 显示已选择的图片
                Box {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier
                            .size(200.dp, 150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClicked() },
                        contentScale = ContentScale.Crop
                    )
                    
                    // 删除按钮
                    IconButton(
                        onClick = onImageRemoved,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除图片",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // 未选择图片
                Box(
                    modifier = Modifier
                        .size(200.dp, 150.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "无图片",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 图库按钮
                OutlinedButton(
                    onClick = onImageSelected,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.gallery))
                }
                
                // 相机按钮
                OutlinedButton(
                    onClick = onImageCaptured,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.camera))
                }

            }
        }
    }
}

/**
 * 全屏图片查看对话框
 */
@Composable
fun ImageDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "查看图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}