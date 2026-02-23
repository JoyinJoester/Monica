package takagi.ru.monica.ui.main.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab

private const val SETTINGS_TAB_KEY = "SETTINGS"

sealed class BottomNavItem(
    val contentTab: BottomNavContentTab?,
    val icon: ImageVector
) {
    val key: String = contentTab?.name ?: SETTINGS_TAB_KEY

    object Passwords : BottomNavItem(BottomNavContentTab.PASSWORDS, Icons.Default.Lock)
    object Authenticator : BottomNavItem(BottomNavContentTab.AUTHENTICATOR, Icons.Default.Security)
    object CardWallet : BottomNavItem(BottomNavContentTab.CARD_WALLET, Icons.Default.Wallet)
    object Generator : BottomNavItem(BottomNavContentTab.GENERATOR, Icons.Default.AutoAwesome)
    object Notes : BottomNavItem(BottomNavContentTab.NOTES, Icons.Default.Note)
    object Send : BottomNavItem(BottomNavContentTab.SEND, Icons.Default.Send)
    object Timeline : BottomNavItem(BottomNavContentTab.TIMELINE, Icons.Default.AccountTree)
    object Passkey : BottomNavItem(BottomNavContentTab.PASSKEY, Icons.Default.Key)
    object Settings : BottomNavItem(null, Icons.Default.Settings)
}

fun BottomNavContentTab.toBottomNavItem(): BottomNavItem = when (this) {
    BottomNavContentTab.PASSWORDS -> BottomNavItem.Passwords
    BottomNavContentTab.AUTHENTICATOR -> BottomNavItem.Authenticator
    BottomNavContentTab.CARD_WALLET -> BottomNavItem.CardWallet
    BottomNavContentTab.GENERATOR -> BottomNavItem.Generator
    BottomNavContentTab.NOTES -> BottomNavItem.Notes
    BottomNavContentTab.SEND -> BottomNavItem.Send
    BottomNavContentTab.TIMELINE -> BottomNavItem.Timeline
    BottomNavContentTab.PASSKEY -> BottomNavItem.Passkey
}

fun BottomNavItem.fullLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords
    BottomNavItem.Authenticator -> R.string.nav_authenticator
    BottomNavItem.CardWallet -> R.string.nav_card_wallet
    BottomNavItem.Generator -> R.string.nav_generator
    BottomNavItem.Notes -> R.string.nav_notes
    BottomNavItem.Send -> R.string.nav_v2_send
    BottomNavItem.Timeline -> R.string.nav_timeline
    BottomNavItem.Passkey -> R.string.nav_passkey
    BottomNavItem.Settings -> R.string.nav_settings
}

fun BottomNavItem.shortLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords_short
    BottomNavItem.Authenticator -> R.string.nav_authenticator_short
    BottomNavItem.CardWallet -> R.string.nav_card_wallet_short
    BottomNavItem.Generator -> R.string.nav_generator_short
    BottomNavItem.Notes -> R.string.nav_notes_short
    BottomNavItem.Send -> R.string.nav_v2_send_short
    BottomNavItem.Timeline -> R.string.nav_timeline_short
    BottomNavItem.Passkey -> R.string.nav_passkey_short
    BottomNavItem.Settings -> R.string.nav_settings_short
}

fun indexToDefaultTabKey(index: Int): String = when (index) {
    0 -> BottomNavContentTab.PASSWORDS.name
    1 -> BottomNavContentTab.AUTHENTICATOR.name
    2 -> BottomNavContentTab.CARD_WALLET.name
    3 -> BottomNavContentTab.GENERATOR.name
    4 -> BottomNavContentTab.NOTES.name
    5 -> SETTINGS_TAB_KEY
    else -> BottomNavContentTab.PASSWORDS.name
}
