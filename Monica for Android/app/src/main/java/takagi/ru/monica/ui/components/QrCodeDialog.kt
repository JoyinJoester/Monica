package takagi.ru.monica.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import java.io.OutputStream
import java.net.URLEncoder

@Composable
fun QrCodeDialog(
    item: SecureItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Generate QR Code
    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            try {
                val totpData = Json.decodeFromString<TotpData>(item.itemData)
                val uri = generateOtpUri(totpData, item.title)
                val hints = mapOf(
                    com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                    com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q,
                    com.google.zxing.EncodeHintType.MARGIN to 2
                )
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(uri, BarcodeFormat.QR_CODE, 800, 800, hints)
                qrBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.close))
                    }
                    
                    Button(
                        onClick = {
                            qrBitmap?.let { bitmap ->
                                scope.launch {
                                    saveBitmapToGallery(context, bitmap, "Monica_QR_${item.title}")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

private fun generateOtpUri(data: TotpData, title: String): String {
    val type = if (data.otpType == OtpType.HOTP) "hotp" else "totp"
    val account = if (data.accountName.isNotBlank()) data.accountName else title
    
    val label = if (data.issuer.isNotBlank()) {
        "${data.issuer}:$account"
    } else {
        account
    }
    
    val encodedLabel = URLEncoder.encode(label, "UTF-8")
        .replace("+", "%20")
        .replace("%3A", ":") // Google Authenticator expects colon to be unencoded or specific format
    
    val sb = StringBuilder("otpauth://$type/$encodedLabel")
    sb.append("?secret=${data.secret}")
    
    if (data.issuer.isNotBlank()) {
        sb.append("&issuer=${URLEncoder.encode(data.issuer, "UTF-8")}")
    }
    
    if (data.algorithm != "SHA1") {
        sb.append("&algorithm=${data.algorithm}")
    }
    
    if (data.digits != 6) {
        sb.append("&digits=${data.digits}")
    }
    
    if (data.period != 30) {
        sb.append("&period=${data.period}")
    }
    
    if (data.otpType == OtpType.HOTP) {
        sb.append("&counter=${data.counter}")
    }
    
    return sb.toString()
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "${title}_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monica")
                }
                
                val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                // For older versions, we might need WRITE_EXTERNAL_STORAGE permission
                // But since minSdk is 29 (Android 10), we are safe with MediaStore API above
                // If minSdk was lower, we would need legacy file handling
            }
            
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "QR码已保存到相册", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
