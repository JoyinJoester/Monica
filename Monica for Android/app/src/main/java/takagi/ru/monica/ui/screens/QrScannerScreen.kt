package takagi.ru.monica.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.ResultPoint
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import takagi.ru.monica.R
import java.util.concurrent.atomic.AtomicBoolean


/**
 * QR码扫描屏幕
 * 用于扫描TOTP密钥的QR码
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // 添加权限状态监听，在页面显示时自动检查权限
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            cameraPermissionState.status.isGranted -> {
                QrCodeScanner(
                    onQrCodeScanned = onQrCodeScanned,
                    onNavigateBack = onNavigateBack
                )
            }
            else -> {
                CameraPermissionRequest(
                    permissionState = cameraPermissionState,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

/**
 * 请求相机权限界面
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPermissionRequest(
    permissionState: PermissionState,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.qr_camera_permission_title),
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { permissionState.launchPermissionRequest() }
        ) {
            Text(stringResource(R.string.grant_permission))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.go_back))
        }
    }
}

/**
 * QR码扫描器组件 - 使用 ZXing
 */
@Composable
private fun QrCodeScanner(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    val scanConsumed = remember { AtomicBoolean(false) }
    var showOverlay by remember { mutableStateOf(false) }
    
    // 图片选择器 - 使用 GetContent 以兼容所有设备
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val result = processImage(context, uri)
            if (result != null && scanConsumed.compareAndSet(false, true)) {
                onQrCodeScanned(result)
            } else if (result == null) {
                Toast.makeText(context, context.getString(R.string.qr_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(barcodeView) {
        runCatching { barcodeView?.resume() }
        onDispose {
            runCatching { barcodeView?.pause() }
        }
    }

    LaunchedEffect(Unit) {
        showOverlay = true
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                DecoratedBarcodeView(ctx).also { scannerView ->
                    // Hide ZXing default finder overlay (mask + laser) and keep only the custom M3 frame.
                    runCatching { scannerView.viewFinder.visibility = View.GONE }
                    val viewFinderId = listOf(
                        ctx.resources.getIdentifier("zxing_viewfinder_view", "id", ctx.packageName),
                        ctx.resources.getIdentifier("zxing_viewfinder_view", "id", "com.journeyapps.barcodescanner")
                    ).firstOrNull { it != 0 }
                    if (viewFinderId != null) {
                        scannerView.findViewById<View>(viewFinderId)?.visibility = View.GONE
                    }
                    val statusViewId = listOf(
                        ctx.resources.getIdentifier("zxing_status_view", "id", ctx.packageName),
                        ctx.resources.getIdentifier("zxing_status_view", "id", "com.journeyapps.barcodescanner")
                    ).firstOrNull { it != 0 }
                    if (statusViewId != null) {
                        scannerView.findViewById<View>(statusViewId)?.visibility = View.GONE
                    }

                    val formats = listOf(BarcodeFormat.QR_CODE)
                    val decodeHints = mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )

                    // Use mixed scan type (normal + inverted) to improve low-contrast/complex QR detection.
                    scannerView.barcodeView.decoderFactory = DefaultDecoderFactory(
                        formats,
                        decodeHints,
                        "UTF-8",
                        2
                    )

                    val cameraSettings = CameraSettings().apply {
                        isAutoFocusEnabled = true
                        isContinuousFocusEnabled = true
                        isMeteringEnabled = true
                        isBarcodeSceneModeEnabled = true
                    }
                    scannerView.cameraSettings = cameraSettings

                    scannerView.decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            val value = result?.text?.trim()
                            if (!value.isNullOrBlank() && scanConsumed.compareAndSet(false, true)) {
                                onQrCodeScanned(value)
                            }
                        }

                        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    })
                    barcodeView = scannerView
                }
            },
            update = { view ->
                if (view !== barcodeView) {
                    barcodeView = view
                }
            },
            onRelease = { view ->
                runCatching { view.pause() }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.46f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.66f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                animationSpec = tween(280),
                initialOffsetY = { -it / 8 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_qr_code_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.qr_align_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        ScannerFrame(
            modifier = Modifier.align(Alignment.Center)
        )

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(320)) + slideInVertically(
                animationSpec = tween(320),
                initialOffsetY = { it / 6 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qr_align_hint),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FilledTonalButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(R.string.qr_pick_from_gallery)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.qr_pick_from_gallery))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerFrame(
    modifier: Modifier = Modifier
) {
    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .size(268.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.08f))
        )

        ScannerCorner(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.TopStart
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.TopEnd
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.BottomStart
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.BottomEnd
        )
    }
}

private enum class ScannerCornerPosition {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd
}

@Composable
private fun ScannerCorner(
    modifier: Modifier = Modifier,
    color: Color,
    position: ScannerCornerPosition
) {
    val horizontalAlignment = when (position) {
        ScannerCornerPosition.TopStart, ScannerCornerPosition.BottomStart -> Alignment.CenterStart
        ScannerCornerPosition.TopEnd, ScannerCornerPosition.BottomEnd -> Alignment.CenterEnd
    }
    val verticalAlignment = when (position) {
        ScannerCornerPosition.TopStart, ScannerCornerPosition.TopEnd -> Alignment.TopCenter
        ScannerCornerPosition.BottomStart, ScannerCornerPosition.BottomEnd -> Alignment.BottomCenter
    }
    val cornerAlignment = when (position) {
        ScannerCornerPosition.TopStart -> Alignment.TopStart
        ScannerCornerPosition.TopEnd -> Alignment.TopEnd
        ScannerCornerPosition.BottomStart -> Alignment.BottomStart
        ScannerCornerPosition.BottomEnd -> Alignment.BottomEnd
    }

    Box(modifier = modifier.size(30.dp)) {
        Box(
            modifier = Modifier
                .align(verticalAlignment)
                .height(5.dp)
                .width(30.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(horizontalAlignment)
                .width(5.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(cornerAlignment)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun processImage(
    context: Context,
    uri: Uri
): String? {
    val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (imageBytes.isEmpty()) return null

    val sampleSizes = computeSampleSizes(imageBytes, maxEdge = 3072)

    for (sampleSize in sampleSizes) {
        val baseBitmap = decodeBitmapFromBytes(imageBytes, sampleSize) ?: continue
        try {
            val decoded = decodeQrFromBitmap(baseBitmap)
                ?: decodeWithRotation(baseBitmap, 90f)
                ?: decodeWithRotation(baseBitmap, 180f)
                ?: decodeWithRotation(baseBitmap, 270f)

            if (decoded != null) {
                return decoded
            }
        } finally {
            if (!baseBitmap.isRecycled) baseBitmap.recycle()
        }
    }

    return null
}

private fun computeSampleSizes(imageBytes: ByteArray, maxEdge: Int): List<Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return listOf(1)

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxEdge || bounds.outHeight / sampleSize > maxEdge) {
        sampleSize *= 2
    }

    val sizes = linkedSetOf(sampleSize)
    while (sampleSize > 1) {
        sampleSize /= 2
        sizes += sampleSize
    }
    sizes += 1
    return sizes.toList()
}

private fun decodeBitmapFromBytes(imageBytes: ByteArray, sampleSize: Int): Bitmap? {
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
}

private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(angle) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun decodeWithRotation(baseBitmap: Bitmap, angle: Float): String? {
    val rotated = runCatching { rotateBitmap(baseBitmap, angle) }.getOrNull() ?: return null
    return try {
        decodeQrFromBitmap(rotated)
    } finally {
        if (!rotated.isRecycled) rotated.recycle()
    }
}

private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    val baseSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    val luminanceSources = listOf(
        baseSource,
        InvertedLuminanceSource(baseSource)
    )

    luminanceSources.forEach { source ->
        val bitmaps = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source))
        )

        bitmaps.forEach { binaryBitmap ->
            val decoded = runCatching {
                MultiFormatReader()
                    .decode(binaryBitmap, hints)
                    .text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
            if (decoded != null) return decoded
        }
    }

    return null
}
