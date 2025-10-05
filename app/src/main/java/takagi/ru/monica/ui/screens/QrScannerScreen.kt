package takagi.ru.monica.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import takagi.ru.monica.R
import java.util.concurrent.Executors


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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qr_code_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                cameraPermissionState.status.isGranted -> {
                    // 相机权限已授予，显示扫描界面
                    QrCodeScanner(
                        onQrCodeScanned = onQrCodeScanned,
                        onNavigateBack = onNavigateBack
                    )
                }
                else -> {
                    // 请求相机权限
                    CameraPermissionRequest(
                        permissionState = cameraPermissionState,
                        onNavigateBack = onNavigateBack
                    )
                }
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
            text = "需要相机权限",
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
 * QR码扫描器组件
 */
@Composable
private fun QrCodeScanner(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasScanned by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // 预览
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    // 图像分析
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImageProxy(imageProxy, hasScanned) { qrCode ->
                                    if (!hasScanned) {
                                        hasScanned = true
                                        onQrCodeScanned(qrCode)
                                    }
                                }
                            }
                        }
                    
                    // 选择后置摄像头
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Camera binding failed", e)
                    }
                }, executor)
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 扫描提示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = "将二维码对准扫描框",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // 扫描框
        ScannerOverlay(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 扫描框覆盖层
 */
@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(250.dp)
    ) {
        // 四个角的装饰
        val cornerSize = 40.dp
        val cornerWidth = 4.dp
        
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerSize, cornerWidth),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerWidth, cornerSize),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerSize, cornerWidth),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerWidth, cornerSize),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerSize, cornerWidth),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerWidth, cornerSize),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerSize, cornerWidth),
            color = MaterialTheme.colorScheme.primary
        ) {}
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerWidth, cornerSize),
            color = MaterialTheme.colorScheme.primary
        ) {}
    }
}

/**
 * 处理图像并检测QR码
 */
@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    imageProxy: ImageProxy,
    hasScanned: Boolean,
    onQrCodeDetected: (String) -> Unit
) {
    if (hasScanned) {
        imageProxy.close()
        return
    }
    
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_URL) {
                        barcode.rawValue?.let { value ->
                            onQrCodeDetected(value)
                        }
                    }
                }
            }
            .addOnFailureListener {
                Log.e("QrScanner", "Barcode scanning failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
