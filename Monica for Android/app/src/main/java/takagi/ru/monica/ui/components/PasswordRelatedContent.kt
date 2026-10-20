package takagi.ru.monica.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardFaceDisplayMode
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.cardwallet.CardFaceArtwork
import takagi.ru.monica.ui.cardwallet.CardFaceImageProcessor
import takagi.ru.monica.ui.cardwallet.bankCardFacePreviewData
import takagi.ru.monica.utils.ClipboardUtils

/** A view of the existing payment fields; no second wallet item is created. */
@Composable
fun PasswordPaymentCard(
    entry: PasswordEntry,
    onCreateSend: ((String, String) -> Unit)? = null,
    previewOnly: Boolean = false,
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val (month, year) = EntryPaymentFormat.splitExpiry(entry.creditCardExpiry)
    val data = BankCardData(cardNumber = entry.creditCardNumber, cardholderName = entry.creditCardHolder,
        expiryMonth = month, expiryYear = year, cvv = entry.creditCardCVV)
    val preview = bankCardFacePreviewData(entry.title, data)
    val facePreview = if (LocalDensity.current.fontScale > 1.25f && data.cardNumber.isNotBlank()) {
        val compactNumber = data.cardNumber.filterNot(Char::isWhitespace)
        preview.copy(identifier = if (compactNumber.length >= 4) "•••• ${compactNumber.takeLast(4)}" else "••••",
            maskIdentifier = false)
    } else preview
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardFaceArtwork(
            previewData = facePreview, bitmap = null,
            displayMode = CardFaceDisplayMode.ALL,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardFaceImageProcessor.CARD_ASPECT_RATIO)
                .testTag("password_payment_face"),
        )
        if (!previewOnly) DetailCardSurface {
            Row(
                Modifier.fillMaxWidth().testTag("password_payment_expand")
                    .clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.payment_info), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MonicaExpansionChevron(expanded = expanded,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand))
            }
            MonicaExpandableContent(expanded = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    EntryPaymentDetails(entry.creditCardNumber, entry.creditCardHolder,
                        entry.creditCardExpiry, entry.creditCardCVV, onCreateSend)
                }
            }
        }
    }
}

/** Uses the authenticator page's code formatting, timer, masking and copy behavior. */
@Composable
fun PasswordAuthenticatorCard(
    entry: PasswordEntry,
    data: TotpData,
    settings: AppSettings,
    onEdit: () -> Unit,
    connected: Boolean = false,
    hasFollowingContent: Boolean = false,
) {
    val context = LocalContext.current
    // This item only supplies display identity to the existing renderer. It is never persisted.
    val displayItem = remember(entry.id, entry.title, data.issuer) {
        SecureItem(id = entry.id, itemType = ItemType.TOTP,
            title = data.issuer.ifBlank { entry.title }, itemData = "")
    }
    var menuOpen by remember(entry.id, data) { mutableStateOf(false) }
    var qrContent by remember(entry.id, data) { mutableStateOf<Pair<String, String>?>(null) }
    fun copyCode(next: Boolean) {
        val code = passwordAuthenticatorActionCode(data, settings.totpTimeOffset, next)
        ClipboardUtils.copyToClipboard(context, code, context.getString(R.string.verification_code), sensitive = true)
        Toast.makeText(context, context.getString(R.string.copied,
            context.getString(R.string.verification_code)), Toast.LENGTH_SHORT).show()
        menuOpen = false
    }
    Box {
        TotpCodeCard(
            item = displayItem, parsedTotpData = data,
            appSettings = settings.copy(validatorUnifiedProgressBar = takagi.ru.monica.data.UnifiedProgressBarMode.DISABLED,
                iconCardsEnabled = settings.iconCardsEnabled && !connected),
            modifier = Modifier.fillMaxWidth().testTag("password_authenticator_card"),
            cardVerticalPadding = 16.dp, showContentDetails = true, showIdentity = !connected, onEdit = onEdit,
            cardShape = entryGroupShape(if (connected) 1 else 0,
                (if (connected) 2 else 1) + if (hasFollowingContent) 1 else 0),
            onCardClick = { menuOpen = true }, onLongClick = { menuOpen = true },
            onActionMenu = { menuOpen = true }, onCopyCode = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.auth_detail_copy_current)) },
                modifier = Modifier.testTag("auth_copy_current"), onClick = { copyCode(false) })
            if (data.otpType != takagi.ru.monica.data.model.OtpType.HOTP) {
                DropdownMenuItem(text = { Text(stringResource(R.string.auth_detail_copy_next)) },
                    modifier = Modifier.testTag("auth_copy_next"), onClick = { copyCode(true) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.auth_detail_qr_migrate)) },
                modifier = Modifier.testTag("auth_qr_migrate"), onClick = {
                    menuOpen = false
                    qrContent = context.getString(R.string.auth_detail_qr_migrate) to
                        takagi.ru.monica.util.TotpUriParser.generateUri(
                            listOf(data.issuer.ifBlank { entry.title }, data.accountName)
                                .filter { it.isNotBlank() }.joinToString(":"), data, includePin = true)
                })
            DropdownMenuItem(text = { Text(stringResource(R.string.auth_detail_qr_current)) },
                modifier = Modifier.testTag("auth_qr_current"), onClick = {
                    menuOpen = false
                    qrContent = context.getString(R.string.auth_detail_qr_current) to
                        passwordAuthenticatorActionCode(data, settings.totpTimeOffset, false)
                })
            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                menuOpen = false
                onEdit()
            })
        }
    }
    qrContent?.let { (title, content) ->
        TextQrCodeDialog(title = title, content = content, onDismiss = { qrContent = null })
    }
}

/** Generate at action time, even when a menu has remained open across a period boundary. */
internal fun passwordAuthenticatorActionCode(
    data: TotpData,
    offset: Int,
    next: Boolean,
    seconds: Long = System.currentTimeMillis() / 1000L,
): String = takagi.ru.monica.util.TotpGenerator.generateOtp(
    totpData = data, timeOffset = offset,
    currentSeconds = seconds + if (next && data.otpType != takagi.ru.monica.data.model.OtpType.HOTP) data.period else 0,
)
