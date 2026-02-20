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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import takagi.ru.monica.R
import takagi.ru.monica.data.model.CardScanResult
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CardScannerScreen(
    onCardScanned: (CardScanResult) -> Unit,
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
                title = { Text(stringResource(R.string.scan_bank_card_title)) },
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
                    CardScannerContent(
                        onCardScanned = onCardScanned,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    CardCameraPermissionRequest(
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
private fun CardCameraPermissionRequest(
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
            text = stringResource(R.string.card_scan_camera_permission_title),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.card_scan_camera_permission_body),
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
private fun CardScannerContent(
    onCardScanned: (CardScanResult) -> Unit,
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
        processCardImage(
            context = context,
            textRecognizer = textRecognizer,
            uri = uri
        ) { scanResult ->
            if (!hasScanned) {
                if (scanResult != null) {
                    hasScanned = true
                    onCardScanned(scanResult)
                } else {
                    Toast.makeText(context, context.getString(R.string.card_scan_no_card_info), Toast.LENGTH_SHORT).show()
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

                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            textRecognizer.process(image)
                                .addOnSuccessListener { result ->
                                    if (hasScanned) return@addOnSuccessListener
                                    parseCardFromText(result.text)?.let { scanResult ->
                                        hasScanned = true
                                        onCardScanned(scanResult)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
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
            factory = { ctx ->
                PreviewView(ctx).also { previewView = it }
            },
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
                    text = stringResource(R.string.card_scan_hint),
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
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = stringResource(R.string.qr_pick_from_gallery)
            )
        }
    }
}

private fun processCardImage(
    context: Context,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    uri: Uri,
    onResult: (CardScanResult?) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        textRecognizer.process(image)
            .addOnSuccessListener { text ->
                onResult(parseCardFromText(text.text))
            }
            .addOnFailureListener {
                onResult(null)
            }
    } catch (e: IOException) {
        onResult(null)
    }
}

private fun parseCardFromText(text: String): CardScanResult? {
    if (text.isBlank()) return null

    val numberCandidates = Regex("""(?:\d[ -]?){13,19}""")
        .findAll(text)
        .map { it.value.filter { ch -> ch.isDigit() } }
        .filter { it.length in 13..19 }
        .toList()
    if (numberCandidates.isEmpty()) return null

    val cardNumber = numberCandidates
        .firstOrNull { isLuhnValid(it) }
        ?: numberCandidates.first()

    val expiryMatch = Regex("""(?<!\d)(0[1-9]|1[0-2])\s*/\s*(\d{2}|\d{4})(?!\d)""")
        .find(text)

    val expiryMonth = expiryMatch?.groupValues?.getOrNull(1).orEmpty()
    val expiryYearRaw = expiryMatch?.groupValues?.getOrNull(2).orEmpty()
    val expiryYear = when (expiryYearRaw.length) {
        2 -> "20$expiryYearRaw"
        4 -> expiryYearRaw
        else -> ""
    }

    val nameLine = text.lines()
        .map { it.trim() }
        .firstOrNull { isLikelyCardholderLine(it) }
        .orEmpty()

    return CardScanResult(
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        cardholderName = nameLine,
        rawText = text.take(2000)
    )
}

private fun isLikelyCardholderLine(line: String): Boolean {
    if (line.isBlank() || line.length !in 4..32) return false
    if (line.any { it.isDigit() }) return false
    if (!line.contains(' ')) return false
    if (!line.matches(Regex("""[A-Z .'\-]+"""))) return false

    val upper = line.uppercase(Locale.US)
    val blacklist = listOf(
        "VALID",
        "THRU",
        "GOOD",
        "MONTH",
        "YEAR",
        "BANK",
        "CARD",
        "VISA",
        "MASTERCARD",
        "UNIONPAY",
        "DEBIT",
        "CREDIT"
    )
    return blacklist.none { upper.contains(it) }
}

private fun isLuhnValid(number: String): Boolean {
    if (number.isBlank() || number.length !in 13..19) return false
    var sum = 0
    var shouldDouble = false
    for (i in number.length - 1 downTo 0) {
        var digit = number[i] - '0'
        if (shouldDouble) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        shouldDouble = !shouldDouble
    }
    return sum % 10 == 0
}
