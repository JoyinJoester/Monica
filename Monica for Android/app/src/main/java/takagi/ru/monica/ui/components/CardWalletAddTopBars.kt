package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.screens.CardWalletTab

@Composable
fun CardWalletAddTypeChip(
    current: CardWalletTab,
    onSelect: (CardWalletTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    drawContainer: Boolean = true,
    contentColorOverride: Color? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val chipContentDescription = stringResource(R.string.nav_card_wallet)
    val interactionSource = remember { MutableInteractionSource() }
    val options = remember {
        listOf(
            CardWalletTab.BANK_CARDS,
            CardWalletTab.DOCUMENTS,
            CardWalletTab.BILLING_ADDRESSES,
            CardWalletTab.MEMBERSHIP_CARDS, CardWalletTab.REWARDS_CARDS, CardWalletTab.TRAVEL_PASSES
        )
    }

    val targetContent = contentColorOverride ?: if (drawContainer) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "CardWalletAddTypeChipArrow"
    )
    val containerColor = if (drawContainer) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Box {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = { expanded = true }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = chipContentDescription }
    ) {
        Icon(
            imageVector = cardWalletAddTypeIcon(current),
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = cardWalletAddTypeLabel(current),
            color = targetContent,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = targetContent,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation)
        )
    }

    EditorActionMenu(expanded, { expanded = false }, options.map { option ->
        EditorMenuAction(cardWalletAddTypeLabel(option),
            if (option == current) Icons.Default.Check else cardWalletAddTypeIcon(option),
            onClick = { if (option != current) onSelect(option) }, tag = "wallet_template_${option.name}")
    })
    }
}

@Composable
private fun cardWalletAddTypeLabel(type: CardWalletTab): String = stringResource(
    when (type) {
        CardWalletTab.MEMBERSHIP_CARDS -> R.string.wallet_membership_card
        CardWalletTab.REWARDS_CARDS -> R.string.wallet_rewards_card
        CardWalletTab.TRAVEL_PASSES -> R.string.wallet_travel_pass
        CardWalletTab.DOCUMENTS -> R.string.item_type_document
        CardWalletTab.BILLING_ADDRESSES -> R.string.billing_address
        else -> R.string.item_type_bank_card
    }
)

private fun cardWalletAddTypeIcon(type: CardWalletTab): ImageVector = when (type) {
    CardWalletTab.MEMBERSHIP_CARDS -> Icons.Default.Badge
    CardWalletTab.REWARDS_CARDS -> Icons.Default.Star
    CardWalletTab.TRAVEL_PASSES -> Icons.Default.FlightTakeoff
    CardWalletTab.DOCUMENTS -> Icons.Default.Badge
    CardWalletTab.BILLING_ADDRESSES -> Icons.Default.Home
    else -> Icons.Default.CreditCard
}
