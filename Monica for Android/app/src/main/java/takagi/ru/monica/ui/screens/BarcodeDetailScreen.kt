package takagi.ru.monica.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.viewmodel.PasswordViewModel

private enum class BarcodeRenderMode { QR_CODE, CODE_128 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeDetailScreen(
    viewModel: PasswordViewModel,
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<PasswordEntry?>(null) }
    var mode by remember { mutableStateOf(BarcodeRenderMode.QR_CODE) }

    LaunchedEffect(passwordId) {
        entry = viewModel.getPasswordEntryById(passwordId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.barcode_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            entry?.let { current ->
                ExtendedFloatingActionButton(
                    onClick = { onEdit(current.id) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(stringResource(R.string.edit)) }
                )
            }
        }
    ) { innerPadding ->
        val current = entry
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading))
            }
            return@Scaffold
        }

        val payload = current.password
        val generatedBitmap = remember(payload, mode) {
            generateBarcodeBitmap(payload, mode).getOrNull()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.barcode_detail_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == BarcodeRenderMode.QR_CODE,
                    onClick = { mode = BarcodeRenderMode.QR_CODE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.barcode_render_qr))
                }
                SegmentedButton(
                    selected = mode == BarcodeRenderMode.CODE_128,
                    onClick = { mode = BarcodeRenderMode.CODE_128 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.barcode_render_barcode))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (generatedBitmap != null) {
                        val imageModifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (mode == BarcodeRenderMode.QR_CODE) {
                                    Modifier.aspectRatio(1f)
                                } else {
                                    Modifier.height(160.dp)
                                }
                            )
                            .background(Color.White)
                            .padding(12.dp)
                        Image(
                            bitmap = generatedBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.barcode_detail_title),
                            modifier = imageModifier,
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.barcode_render_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.barcode_payload_label),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                ClipboardUtils.copyToClipboard(
                                    context = context,
                                    text = payload,
                                    label = context.getString(R.string.barcode_payload_label),
                                    sensitive = true
                                )
                                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                    }
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun generateBarcodeBitmap(payload: String, mode: BarcodeRenderMode): Result<Bitmap> =
    runCatching {
        val encoder = BarcodeEncoder()
        when (mode) {
            BarcodeRenderMode.QR_CODE -> {
                val hints = mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
                    EncodeHintType.MARGIN to 2
                )
                encoder.encodeBitmap(payload, BarcodeFormat.QR_CODE, 900, 900, hints)
            }
            BarcodeRenderMode.CODE_128 ->
                encoder.encodeBitmap(payload, BarcodeFormat.CODE_128, 1100, 360)
        }
    }
