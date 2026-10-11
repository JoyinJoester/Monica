package takagi.ru.monica.lansync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun DeviceSyncEditorLease() {
    DisposableEffect(Unit) {
        val lease = DeviceSyncEditGate.hold()
        onDispose { lease.close() }
    }
}
