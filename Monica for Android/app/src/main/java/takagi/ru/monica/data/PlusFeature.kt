package takagi.ru.monica.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import takagi.ru.monica.R

data class PlusFeature(
    val id: String,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val isAvailable: Boolean = true
)

object PlusFeatures {
    fun getPlaceholderFeatures(): List<PlusFeature> = listOf(
        PlusFeature(
            id = "premium_themes",
            icon = Icons.Default.Palette,
            titleRes = R.string.plus_feature_premium_themes_title,
            descriptionRes = R.string.plus_feature_premium_themes_desc,
            isAvailable = true
        ),
        PlusFeature(
            id = "notification_validator",
            icon = Icons.Default.Notifications,
            titleRes = R.string.plus_feature_notification_validator_title,
            descriptionRes = R.string.plus_feature_notification_validator_desc,
            isAvailable = true
        ),
        PlusFeature(
            id = "validator_vibration",
            icon = Icons.Default.Vibration,
            titleRes = R.string.plus_feature_validator_vibration_title,
            descriptionRes = R.string.plus_feature_validator_vibration_desc,
            isAvailable = true
        ),
        PlusFeature(
            id = "copy_next_code",
            icon = Icons.Default.Update,
            titleRes = R.string.plus_feature_copy_next_code_title,
            descriptionRes = R.string.plus_feature_copy_next_code_desc,
            isAvailable = true
        ),
        PlusFeature(
            id = "bitwarden_sync",
            icon = Icons.Default.CloudSync,
            titleRes = R.string.plus_feature_bitwarden_sync_title,
            descriptionRes = R.string.plus_feature_bitwarden_sync_desc,
            isAvailable = true
        )
    )
}
