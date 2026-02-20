package takagi.ru.monica.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import takagi.ru.monica.R
import takagi.ru.monica.data.model.DocumentScanResult
import java.io.IOException
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DocumentScannerScreen(
    onDocumentScanned: (DocumentScanResult) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_document_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
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
                    DocumentScannerContent(
                        onDocumentScanned = onDocumentScanned,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    DocumentCameraPermissionRequest(
                        permissionState = cameraPermissionState,
                        onNavigateBack = onNavigateBack
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun DocumentCameraPermissionRequest(
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
            text = stringResource(R.string.document_scan_camera_permission_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.document_scan_camera_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { permissionState.launchPermissionRequest() }) {
            Text(stringResource(R.string.grant_permission))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
private fun DocumentScannerContent(
    onDocumentScanned: (DocumentScanResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || hasScanned) return@rememberLauncherForActivityResult
        processDocumentImage(context, textRecognizer, uri) { result ->
            if (!hasScanned) {
                if (result != null) {
                    hasScanned = true
                    onDocumentScanned(result)
                } else {
                    Toast.makeText(context, context.getString(R.string.document_scan_no_info), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { textRecognizer.close() }
            runCatching { cameraExecutor.shutdown() }
        }
    }

    LaunchedEffect(previewView, hasScanned) {
        val targetView = previewView ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(targetView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            if (hasScanned) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            textRecognizer.process(image)
                                .addOnSuccessListener { result ->
                                    if (!hasScanned) {
                                        parseDocumentFromText(result.text)?.let { scanResult ->
                                            hasScanned = true
                                            onDocumentScanned(scanResult)
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { previewView = it } },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = stringResource(R.string.document_scan_hint),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        FloatingActionButton(
            onClick = { photoPickerLauncher.launch("image/*") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = stringResource(R.string.qr_pick_from_gallery))
        }
    }
}

private fun processDocumentImage(
    context: Context,
    textRecognizer: TextRecognizer,
    uri: Uri,
    onResult: (DocumentScanResult?) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        textRecognizer.process(image)
            .addOnSuccessListener { text -> onResult(parseDocumentFromText(text.text)) }
            .addOnFailureListener { onResult(null) }
    } catch (_: IOException) {
        onResult(null)
    }
}

private fun parseDocumentFromText(text: String): DocumentScanResult? {
    if (text.isBlank()) return null
    val normalizedText = text.replace('\n', ' ')

    val dateRegex = Regex("""(?<!\d)(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)""")
    val dates = dateRegex.findAll(text).map { match ->
        val year = match.groupValues[1]
        val month = match.groupValues[2].padStart(2, '0')
        val day = match.groupValues[3].padStart(2, '0')
        "$year/$month/$day"
    }.toList()

    val id18Regex = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")
    val id15Regex = Regex("""(?<!\d)\d{15}(?!\d)""")
    val passportRegex = Regex("""\b[A-Z]{1,2}\d{6,9}\b""")
    val genericDocRegex = Regex("""\b[A-Z0-9]{6,24}\b""")

    val id18 = id18Regex.find(normalizedText)?.value
    val id15 = id15Regex.find(normalizedText)?.value
    val passport = passportRegex.find(normalizedText)?.value
    val generic = genericDocRegex.findAll(normalizedText)
        .map { it.value }
        .firstOrNull { !it.matches(Regex("""\d{8,}""")) || it.length >= 15 }

    val documentNumber = id18 ?: passport ?: id15 ?: generic.orEmpty()

    val nameRegex = Regex("""\b[A-Z][A-Z '\-]{2,30}\b""")
    val englishName = nameRegex.findAll(text.uppercase())
        .map { it.value.trim() }
        .firstOrNull { !it.contains("PASSPORT") && !it.contains("NATIONALITY") && !it.contains("DOCUMENT") }
        .orEmpty()

    val chineseNameRegex = Regex("""[一-龥]{2,10}""")
    val chineseName = chineseNameRegex.findAll(text)
        .map { it.value.trim() }
        .firstOrNull()
        .orEmpty()

    val fullName = if (englishName.isNotBlank()) englishName else chineseName

    val nationalityRegex = Regex("""(?:NATIONALITY|国籍)\s*[:：]?\s*([A-Z]{2,3}|[一-龥A-Za-z]{2,20})""")
    val nationality = nationalityRegex.find(text)?.groupValues?.getOrNull(1).orEmpty()

    val issuedDate = dates.getOrNull(0).orEmpty()
    val expiryDate = dates.getOrNull(1).orEmpty()

    val hasAnyCore = documentNumber.isNotBlank() || fullName.isNotBlank() || issuedDate.isNotBlank() || expiryDate.isNotBlank()
    if (!hasAnyCore) return null

    return DocumentScanResult(
        documentNumber = documentNumber,
        fullName = fullName,
        issuedDate = issuedDate,
        expiryDate = expiryDate,
        nationality = nationality,
        rawText = text.take(3000)
    )
}
