package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.WalletPassTemplate

internal fun WalletPassTemplate.labelRes(): Int = when (this) {
    WalletPassTemplate.MEMBERSHIP -> R.string.wallet_membership_card
    WalletPassTemplate.REWARDS -> R.string.wallet_rewards_card
    WalletPassTemplate.TRAVEL -> R.string.wallet_travel_pass
}

internal fun WalletPassTemplate.icon(): ImageVector = when (this) {
    WalletPassTemplate.MEMBERSHIP -> Icons.Default.Badge
    WalletPassTemplate.REWARDS -> Icons.Default.Star
    WalletPassTemplate.TRAVEL -> Icons.Default.FlightTakeoff
}

internal fun WalletPassTemplate.fields(): List<Pair<String, Int>> = when (this) {
    WalletPassTemplate.MEMBERSHIP -> listOf("tier" to R.string.wallet_pass_tier,
        "website" to R.string.wallet_pass_website)
    WalletPassTemplate.REWARDS -> listOf("tier" to R.string.wallet_pass_tier,
        "points" to R.string.wallet_pass_points, "pointsExpiry" to R.string.wallet_pass_points_expiry,
        "website" to R.string.wallet_pass_website)
    WalletPassTemplate.TRAVEL -> listOf("serviceNumber" to R.string.wallet_pass_service,
        "origin" to R.string.wallet_pass_origin, "destination" to R.string.wallet_pass_destination,
        "departure" to R.string.wallet_pass_departure, "arrival" to R.string.wallet_pass_arrival,
        "terminal" to R.string.wallet_pass_terminal, "gate" to R.string.wallet_pass_gate,
        "seat" to R.string.wallet_pass_seat, "travelClass" to R.string.wallet_pass_class)
}

@Composable
internal fun WalletPassFields(template: WalletPassTemplate,
    number: String, onNumber: (String) -> Unit, holder: String, onHolder: (String) -> Unit,
    issuer: String, onIssuer: (String) -> Unit, expiry: String, onExpiry: (String) -> Unit,
    fields: Map<String, String>, onField: (String, String) -> Unit) {
    TemplateFormSection(stringResource(template.labelRes())) {
        Column(Modifier.fillMaxWidth().testTag("wallet_pass_fields"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WalletProfileField(issuer, onIssuer, R.string.wallet_pass_issuer)
            WalletProfileField(number, onNumber, if (template == WalletPassTemplate.TRAVEL) R.string.wallet_pass_booking else R.string.wallet_pass_number)
            WalletProfileField(holder, onHolder, if (template == WalletPassTemplate.TRAVEL) R.string.wallet_pass_passenger else R.string.wallet_pass_holder)
            template.fields().forEach { (key, label) ->
                Box(Modifier.testTag("wallet_pass_$key")) { WalletProfileField(fields[key].orEmpty(), { onField(key, it) }, label) }
            }
            WalletProfileField(expiry, onExpiry, R.string.expiry_date_label)
        }
    }
}

@Composable
internal fun WalletPassDetails(template: WalletPassTemplate, data: DocumentData) {
    WalletProfileDetails(template.labelRes(), buildList {
        add(R.string.wallet_pass_issuer to data.issuedBy)
        template.fields().forEach { (key, label) -> add(label to data.walletFields[key].orEmpty()) }
        add(R.string.expiry_date_label to data.expiryDate)
    })
}
