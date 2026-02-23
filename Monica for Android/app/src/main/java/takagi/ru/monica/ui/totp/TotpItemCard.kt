package takagi.ru.monica.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.components.TotpCodeCard

@Composable
internal fun TotpItemCard(
    item: SecureItem,
    boundPasswordSummary: String? = null,
    onEdit: () -> Unit,
    onToggleSelect: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onGenerateNext: ((Long) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShowQrCode: ((SecureItem) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sharedTickSeconds: Long? = null,
    appSettings: AppSettings? = null
) {
    val context = LocalContext.current

    TotpCodeCard(
        item = item,
        boundPasswordSummary = boundPasswordSummary,
        onCopyCode = { code ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.verification_code_copied), Toast.LENGTH_SHORT).show()
        },
        onToggleSelect = onToggleSelect,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onGenerateNext = onGenerateNext,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onShowQrCode = onShowQrCode,
        onEdit = onEdit,
        onLongClick = onLongClick,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        allowVibration = true,
        sharedTickSeconds = sharedTickSeconds,
        appSettings = appSettings
    )
}
