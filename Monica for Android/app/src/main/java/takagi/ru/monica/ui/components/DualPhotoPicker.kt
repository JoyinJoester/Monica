package takagi.ru.monica.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.util.ImageManager

private enum class DualPhotoSlot {
    FRONT,
    BACK
}

private data class PendingDualPhotoImport(
    val slot: DualPhotoSlot,
    val bitmap: Bitmap,
    val originalSizeBytes: Long?
)

private const val DUAL_PHOTO_PICKER_TAG = "DualPhotoPicker"

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
    frontLabel: String? = null,
    backLabel: String? = null
) {
    val context = LocalContext.current
    val imageManager = remember { ImageManager(context) }
    val scope = rememberCoroutineScope()
    
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFrontImageDialog by remember { mutableStateOf(false) }
    var showBackImageDialog by remember { mutableStateOf(false) }
    var pendingSlot by remember { mutableStateOf<DualPhotoSlot?>(null) }
    var pendingCameraImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoImport by remember { mutableStateOf<PendingDualPhotoImport?>(null) }
    var isSavingPendingPhoto by remember { mutableStateOf(false) }
    val resolvedFrontLabel = frontLabel ?: stringResource(R.string.photo_front_label)
    val resolvedBackLabel = backLabel ?: stringResource(R.string.photo_back_label)

    fun clearPendingPhotoImport() {
        pendingPhotoImport?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        pendingPhotoImport = null
        isSavingPendingPhoto = false
    }

    suspend fun prepareSelectedPhoto(slot: DualPhotoSlot, uri: Uri) {
        try {
            Log.d(DUAL_PHOTO_PICKER_TAG, "Preparing photo import for slot=$slot uri=$uri")
            val preparedImport = imageManager.prepareImageImport(uri)
            if (preparedImport != null) {
                clearPendingPhotoImport()
                pendingPhotoImport = PendingDualPhotoImport(
                    slot = slot,
                    bitmap = preparedImport.bitmap,
                    originalSizeBytes = preparedImport.originalSizeBytes
                )
            } else {
                Log.w(DUAL_PHOTO_PICKER_TAG, "Photo prepare returned null for slot=$slot uri=$uri")
                Toast.makeText(context, context.getString(R.string.photo_save_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(DUAL_PHOTO_PICKER_TAG, "Photo prepare failed for slot=$slot uri=$uri", e)
            Toast.makeText(
                context,
                context.getString(R.string.photo_process_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            pendingSlot = null
        }
    }

    suspend fun confirmPendingPhotoImport(quality: Int) {
        val importRequest = pendingPhotoImport ?: return
        try {
            isSavingPendingPhoto = true
            Log.d(
                DUAL_PHOTO_PICKER_TAG,
                "Confirming photo import for slot=${importRequest.slot} quality=$quality"
            )
            val fileName = imageManager.saveImage(
                bitmap = importRequest.bitmap,
                compressionFormat = Bitmap.CompressFormat.JPEG,
                compressionQuality = quality
            )
            if (fileName != null) {
                when (importRequest.slot) {
                    DualPhotoSlot.FRONT -> onFrontImageSelected(fileName)
                    DualPhotoSlot.BACK -> onBackImageSelected(fileName)
                }
                clearPendingPhotoImport()
            } else {
                Log.w(
                    DUAL_PHOTO_PICKER_TAG,
                    "confirmPendingPhotoImport returned null for slot=${importRequest.slot}"
                )
                isSavingPendingPhoto = false
                Toast.makeText(context, context.getString(R.string.photo_save_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            isSavingPendingPhoto = false
            Log.e(
                DUAL_PHOTO_PICKER_TAG,
                "confirmPendingPhotoImport failed for slot=${importRequest.slot}",
                e
            )
            Toast.makeText(
                context,
                context.getString(R.string.photo_process_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val slot = pendingSlot
        Log.d(DUAL_PHOTO_PICKER_TAG, "Gallery result slot=$slot uri=$uri")
        if (uri == null || slot == null) {
            pendingSlot = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            prepareSelectedPhoto(slot, uri)
        }
    }

    val galleryFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val slot = pendingSlot
        Log.d(DUAL_PHOTO_PICKER_TAG, "Fallback gallery result slot=$slot uri=$uri")
        if (uri == null || slot == null) {
            pendingSlot = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            prepareSelectedPhoto(slot, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val slot = pendingSlot
        val tempPath = pendingCameraImagePath
        val tempUri = pendingCameraImageUri
        pendingCameraImagePath = null
        pendingCameraImageUri = null
        Log.d(DUAL_PHOTO_PICKER_TAG, "Camera result slot=$slot success=$success tempPath=$tempPath tempUri=$tempUri")
        if (!success || slot == null || tempPath.isNullOrBlank()) {
            tempPath?.let { path: String -> java.io.File(path).delete() }
            pendingSlot = null
            return@rememberLauncherForActivityResult
        }
        val resolvedTempPath = tempPath
        val resolvedTempUri = tempUri
        scope.launch {
            val tempFile = java.io.File(resolvedTempPath)
            try {
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    Toast.makeText(context, context.getString(R.string.photo_file_missing_or_empty), Toast.LENGTH_SHORT).show()
                    pendingSlot = null
                    return@launch
                }
                prepareSelectedPhoto(slot, resolvedTempUri?.let(Uri::parse) ?: Uri.fromFile(tempFile))
            } finally {
                tempFile.delete()
            }
        }
    }

    val launchCameraCapture: () -> Unit = {
        runCatching {
            val (tempFile, tempUri) = imageManager.createTempPhotoCaptureRequest()
            pendingCameraImagePath = tempFile.absolutePath
            pendingCameraImageUri = tempUri.toString()
            Log.d(DUAL_PHOTO_PICKER_TAG, "Launching camera for slot=$pendingSlot tempPath=${tempFile.absolutePath} uri=$tempUri")
            cameraLauncher.launch(tempUri)
        }.onFailure { error ->
            pendingCameraImagePath = null
            pendingCameraImageUri = null
            pendingSlot = null
            Log.e(DUAL_PHOTO_PICKER_TAG, "Camera launch failed", error)
            Toast.makeText(
                context,
                context.getString(R.string.photo_process_failed, error.message ?: error.javaClass.simpleName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(DUAL_PHOTO_PICKER_TAG, "Camera permission result granted=$granted pendingSlot=$pendingSlot")
        if (granted) {
            launchCameraCapture()
        } else {
            pendingSlot = null
            pendingCameraImagePath = null
            pendingCameraImageUri = null
            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGallery(slot: DualPhotoSlot) {
        pendingSlot = slot
        Log.d(DUAL_PHOTO_PICKER_TAG, "Launching gallery for slot=$slot")
        runCatching {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                Log.w(DUAL_PHOTO_PICKER_TAG, "PickVisualMedia unavailable, falling back to GetContent for slot=$slot", error)
                runCatching {
                    galleryFallbackLauncher.launch("image/*")
                }.onFailure { fallbackError ->
                    Log.e(DUAL_PHOTO_PICKER_TAG, "Fallback gallery launch failed for slot=$slot", fallbackError)
                    pendingSlot = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.photo_process_failed, fallbackError.message ?: fallbackError.javaClass.simpleName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.e(DUAL_PHOTO_PICKER_TAG, "Gallery launch failed for slot=$slot", error)
                pendingSlot = null
                Toast.makeText(
                    context,
                    context.getString(R.string.photo_process_failed, error.message ?: error.javaClass.simpleName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun launchCamera(slot: DualPhotoSlot) {
        pendingSlot = slot
        Log.d(
            DUAL_PHOTO_PICKER_TAG,
            "Launch camera requested for slot=$slot permission=${ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)}"
        )
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
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
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 正面照片
        PhotoCard(
            bitmap = frontBitmap,
            label = resolvedFrontLabel,
            onImageSelected = {
                launchGallery(DualPhotoSlot.FRONT)
            },
            onImageCaptured = {
                launchCamera(DualPhotoSlot.FRONT)
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
        
        // 背面照片（所有证件类型都显示背面照片选择器）
        PhotoCard(
            bitmap = backBitmap,
            label = resolvedBackLabel,
            onImageSelected = {
                launchGallery(DualPhotoSlot.BACK)
            },
            onImageCaptured = {
                launchCamera(DualPhotoSlot.BACK)
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

    pendingPhotoImport?.let { importRequest ->
        ImageImportConfirmDialog(
            imageManager = imageManager,
            bitmap = importRequest.bitmap,
            originalSizeBytes = importRequest.originalSizeBytes,
            isSaving = isSavingPendingPhoto,
            onDismiss = {
                clearPendingPhotoImport()
                pendingSlot = null
            },
            onConfirm = { quality ->
                scope.launch {
                    confirmPendingPhotoImport(quality)
                }
            }
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
                            contentDescription = stringResource(R.string.delete_image),
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
                        contentDescription = stringResource(R.string.no_image),
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
 * 全屏图片查看对话框（支持双指缩放）
 */
@Composable
fun ImageDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }
    
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
                .clickable { 
                    // 只有在未缩放时点击才关闭
                    if (scale <= 1.05f) {
                        onDismiss() 
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.view_image),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformableState),
                contentScale = ContentScale.Fit
            )
            
            // 关闭按钮
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
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // 下载按钮
            if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.save_to_gallery),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // 重置缩放按钮（当缩放时显示）
            if (scale > 1.05f) {
                FilledTonalButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text(stringResource(R.string.reset_zoom))
                }
            }
        }
    }
}
