package takagi.ru.monica.ui.cardwallet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import takagi.ru.monica.data.model.DocumentType

fun DocumentType.walletIcon(): ImageVector =
    when (this) {
        DocumentType.ID_CARD -> Icons.Default.Badge
        DocumentType.PASSPORT -> Icons.Default.FlightTakeoff
        DocumentType.DRIVER_LICENSE -> Icons.Default.DirectionsCar
        DocumentType.SOCIAL_SECURITY -> Icons.Default.HealthAndSafety
        DocumentType.OTHER -> Icons.Default.Description
    }

val billingAddressWalletIcon: ImageVector
    get() = Icons.Default.Home

@Composable
fun DocumentTypeIcon(
    documentType: DocumentType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = documentType.walletIcon(),
        contentDescription = documentType.name,
        modifier = modifier,
        tint = tint
    )
}
