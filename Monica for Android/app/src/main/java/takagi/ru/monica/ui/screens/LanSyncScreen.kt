package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.lansync.DeviceSyncCoordinator
import takagi.ru.monica.lansync.DeviceSyncDiagLogger
import takagi.ru.monica.lansync.LanPairingForm
import takagi.ru.monica.lansync.LanPairingInvitation
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.DateFormat
import java.util.Date

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LanSyncScreen(vaults: MdbxViewModel, onNavigateBack: () -> Unit, onManageVaults: () -> Unit) {
    val context = LocalContext.current
    val qrPixels = with(LocalDensity.current) { 280.dp.roundToPx() }
    val coordinator = remember { DeviceSyncCoordinator.get(context) }
    val state by coordinator.state.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(LanPairingForm()) }
    var showLink by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("10") }
    var qrLink by remember { mutableStateOf("") }
    var forgetting by remember { mutableStateOf<String?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    LaunchedEffect(state.invitations) { qrLink = state.invitations.firstOrNull().orEmpty() }
    LaunchedEffect(state.phase) { if (state.phase == "completed" || state.phase == "locked") pairing = LanPairingForm() }
    DisposableEffect(coordinator) { onDispose { coordinator.cancelPairing() } }

    if (scanning) {
        QrScannerScreen(
            onQrCodeScanned = {
                pairing = pairing.withInvitation(it, emptyList())
                scanning = false
                coordinator.pair(pairing.connectionLink)
            },
            onNavigateBack = { scanning = false },
            title = stringResource(R.string.lan_scan),
            allowedFormats = listOf(BarcodeFormat.QR_CODE),
            resultValidator = { LanPairingInvitation.parse(it)?.version == 2 },
            invalidResultMessage = stringResource(R.string.device_sync_qr_invalid),
            diagnosticLabel = "lan-pairing",
            onDiagnostic = DeviceSyncDiagLogger::scanner
        )
        return
    }
    if (custom) {
        val value = minutes.toIntOrNull()
        AlertDialog(onDismissRequest = { custom = false },
            title = { Text(stringResource(R.string.device_sync_custom)) },
            text = {
                OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.device_sync_minutes_hint)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = value == null || value !in 1..1440)
            },
            confirmButton = { TextButton(onClick = { coordinator.setInterval(requireNotNull(value) * 60); custom = false },
                enabled = value != null && value in 1..1440) { Text(stringResource(R.string.device_sync_save)) } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text(stringResource(R.string.cancel)) } })
    }
    forgetting?.let { id ->
        AlertDialog(onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.device_sync_forget)) },
            text = { Text(stringResource(R.string.device_sync_forget_hint)) },
            confirmButton = { TextButton(onClick = { coordinator.forget(id); forgetting = null }) { Text(stringResource(R.string.device_sync_forget)) } },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.cancel)) } })
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.lan_title)) }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.device_sync_description))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.device_sync_automatic), modifier = Modifier.weight(1f).padding(top = 12.dp))
                        Switch(checked = state.enabled, onCheckedChange = coordinator::setEnabled)
                    }
                    Text(stringResource(R.string.device_sync_frequency), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 60, 300, 900, 1800).forEach { seconds ->
                            FilterChip(selected = state.intervalSeconds == seconds, onClick = { coordinator.setInterval(seconds) },
                                label = { Text(if (seconds == 0) stringResource(R.string.device_sync_realtime)
                                    else stringResource(R.string.device_sync_every_minutes, seconds / 60)) })
                        }
                        FilterChip(selected = state.intervalSeconds !in listOf(0, 60, 300, 900, 1800),
                            onClick = { minutes = (state.intervalSeconds / 60).coerceAtLeast(1).toString(); custom = true },
                            label = { Text(if (state.intervalSeconds !in listOf(0, 60, 300, 900, 1800))
                                stringResource(R.string.device_sync_every_minutes, state.intervalSeconds / 60) else stringResource(R.string.device_sync_custom)) })
                    }
                    Text(stringResource(R.string.device_sync_frequency_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = coordinator::syncNow, enabled = state.enabled && state.peers.isNotEmpty() && !state.pairing) {
                        Text(stringResource(R.string.device_sync_now))
                    }
                }
            }
            Text(stringResource(R.string.device_sync_devices), style = MaterialTheme.typography.titleMedium)
            if (state.peers.isEmpty()) Text(stringResource(R.string.device_sync_no_devices), style = MaterialTheme.typography.bodyMedium)
            state.peers.forEach { peer ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(peer.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(when (peer.status) {
                            "syncing" -> R.string.device_sync_syncing
                            "online" -> R.string.device_sync_online
                            "scheduled" -> R.string.device_sync_scheduled
                            "paused" -> R.string.device_sync_paused
                            "locked" -> R.string.device_sync_locked
                            "waiting" -> R.string.device_sync_waiting
                            else -> R.string.device_sync_offline
                        }))
                        if (peer.lastSyncedAt > 0) Text(stringResource(R.string.device_sync_last,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(peer.lastSyncedAt))),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { forgetting = peer.deviceId }) { Text(stringResource(R.string.device_sync_forget)) }
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.device_sync_pair_hint), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { coordinator.pair() }, enabled = !state.pairing) { Text(stringResource(R.string.device_sync_show_qr)) }
                OutlinedButton(onClick = { scanning = true }, enabled = !state.pairing) { Text(stringResource(R.string.lan_scan)) }
            }
            if (qrLink.isNotEmpty()) {
                val bitmap by produceState<Bitmap?>(null, qrLink, qrPixels) {
                    value = withContext(Dispatchers.Default) { BarcodeEncoder().encodeBitmap(qrLink, BarcodeFormat.QR_CODE, qrPixels, qrPixels) }
                }
                bitmap?.let {
                    Box(Modifier.background(Color.White).padding(12.dp)) {
                        Image(it.asImageBitmap(), stringResource(R.string.lan_scan), Modifier.size(280.dp), filterQuality = FilterQuality.None)
                    }
                }
                if (state.invitations.size > 1) FlowRow {
                    state.invitations.forEach { link -> TextButton(onClick = { qrLink = link }) { Text(LanPairingInvitation.parse(link)?.address.orEmpty()) } }
                }
                TextButton(onClick = {
                    val clip = ClipData.newPlainText("Monica", qrLink).apply {
                        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                }) { Text(stringResource(R.string.lan_copy_link)) }
            }
            TextButton(onClick = { showLink = !showLink }, enabled = !state.pairing) { Text(stringResource(R.string.device_sync_use_link)) }
            if (showLink) {
                OutlinedTextField(value = pairing.invitation, onValueChange = { pairing = pairing.withInvitation(it.take(512), emptyList()) },
                    label = { Text(stringResource(R.string.lan_paste_link)) }, singleLine = true, enabled = !state.pairing, modifier = Modifier.fillMaxWidth())
                if (pairing.autoFilled) Text(stringResource(R.string.device_sync_autofilled), color = MaterialTheme.colorScheme.primary)
                Button(onClick = { coordinator.pair(pairing.connectionLink) }, enabled = !state.pairing && LanPairingInvitation.parse(pairing.connectionLink)?.version == 2) {
                    Text(stringResource(R.string.device_sync_connect))
                }
            }
            if (state.pairing || state.phase in listOf("preparing", "sending", "receiving", "transferring", "applying", "connecting", "authenticating", "checking", "confirming")) {
                Text(stringResource(when(state.phase) { "waiting" -> R.string.device_sync_scan_other; "preparing" -> R.string.device_sync_preparing;
                    "applying" -> R.string.device_sync_applying; "sending" -> R.string.device_sync_sending; "receiving" -> R.string.device_sync_receiving; "transferring" -> R.string.device_sync_transferring; else -> R.string.device_sync_syncing }))
                val progress = state.progress
                val total = if(progress?.totalBytes != null && progress.totalBytes > 0) progress.totalBytes else progress?.total ?: 0
                val completed = if(progress?.totalBytes != null && progress.totalBytes > 0) progress.bytes else progress?.completed ?: 0
                if(total > 0) {
                    LinearProgressIndicator(progress = { (completed.toFloat() / total).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if(progress?.totalBytes != null && progress.totalBytes > 0) String.format(java.util.Locale.getDefault(), "%.1f / %.1f MB", completed/1048576.0, total/1048576.0) else "$completed / $total")
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.pairing) TextButton(onClick = coordinator::cancelPairing) { Text(stringResource(R.string.cancel)) }
            } else if (state.phase == "completed") {
                Text(stringResource(R.string.device_sync_paired), color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    "connection_failed", "listen_failed", "discovery_unavailable" -> R.string.device_sync_connection_failed
                    "pairing_rejected", "authentication_failed", "different_device" -> R.string.device_sync_pairing_failed
                    "peer_merge_failed" -> R.string.lan_peer_failed
                    "unsupported_protocol", "invalid_invitation" -> R.string.device_sync_qr_invalid
                    "pairing_expired" -> R.string.lan_expired
                    "same_device" -> R.string.device_sync_same_device
                    "too_many_devices" -> R.string.device_sync_limit
                    "native_unavailable", "device_sync_unavailable" -> R.string.lan_unavailable
                    "invalid_password_payload", "invalid_record" -> R.string.device_sync_invalid_data
                    else -> when {
                        error.startsWith("storage_") -> R.string.device_sync_storage_failed
                        state.failureStage == "preparing" -> R.string.device_sync_prepare_failed
                        state.failureStage in setOf("applying", "received", "remembering") -> R.string.device_sync_apply_failed
                        else -> R.string.device_sync_failed
                    }
                }), color = MaterialTheme.colorScheme.error)
                val diagnostics = stringResource(R.string.device_sync_diagnostic_format, state.failureStage, error,
                    state.failureKind, state.connectionAttempts, state.failureOperation.ifEmpty { "-" })
                TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(stringResource(R.string.device_sync_diagnostics)) }
                if (showDiagnostics) {
                    Text(diagnostics, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Monica diagnostics", diagnostics))
                    }) { Text(stringResource(R.string.device_sync_copy_diagnostics)) }
                }
            }
            Text(stringResource(R.string.device_sync_scope), style = MaterialTheme.typography.bodySmall)
        }
    }
}
