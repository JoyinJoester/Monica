package takagi.ru.monica.ui.password

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.vector.ImageVector
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.FieldValidation

data class AdditionalInfoItem(
    val icon: ImageVector,
    val text: String
)

fun buildAdditionalInfoPreview(entry: PasswordEntry): List<AdditionalInfoItem> {
    val items = mutableListOf<AdditionalInfoItem>()

    if (entry.appName.isNotBlank()) {
        items.add(
            AdditionalInfoItem(
                icon = Icons.Default.Apps,
                text = entry.appName
            )
        )
    }

    if (entry.email.isNotBlank() && items.size < 2) {
        items.add(
            AdditionalInfoItem(
                icon = Icons.Default.Email,
                text = entry.email
            )
        )
    }

    if (entry.phone.isNotBlank() && items.size < 2) {
        items.add(
            AdditionalInfoItem(
                icon = Icons.Default.Phone,
                text = FieldValidation.formatPhone(entry.phone)
            )
        )
    }

    if (entry.creditCardNumber.isNotBlank() && items.size < 2) {
        items.add(
            AdditionalInfoItem(
                icon = Icons.Default.CreditCard,
                text = FieldValidation.maskCreditCard(entry.creditCardNumber)
            )
        )
    }

    if (entry.city.isNotBlank() && items.size < 2) {
        items.add(
            AdditionalInfoItem(
                icon = Icons.Default.LocationOn,
                text = entry.city
            )
        )
    }

    return items
}
