package takagi.ru.monica.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.ui.components.ActionStrip
import takagi.ru.monica.ui.components.ActionStripItem
import takagi.ru.monica.ui.components.BankCardCard
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.ui.components.InfoFieldWithCopy
import takagi.ru.monica.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardDetailScreen(
    viewModel: BankCardViewModel,
    cardId: Long,
    onNavigateBack: () -> Unit,
    onEditCard: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var cardItem by remember { mutableStateOf<SecureItem?>(null) }
    var cardData by remember { mutableStateOf<BankCardData?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var cvvVisible by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) } // Assuming PIN might be stored in notes or generic field if not in model, but model has no PIN. Checking model...
    // Model has `cvv`. 
    
    // Load card details
    LaunchedEffect(cardId) {
        viewModel.getCardById(cardId)?.let { item ->
            cardItem = item
            cardData = viewModel.parseCardData(item.itemData)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cardItem?.title ?: stringResource(R.string.bank_card_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ActionStrip(
                actions = listOf(
                    ActionStripItem(
                        icon = if (cardItem?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.favorite),
                        onClick = {
                            cardItem?.let { item ->
                                viewModel.toggleFavorite(item.id)
                                cardItem = item.copy(isFavorite = !item.isFavorite)
                            }
                        },
                        tint = if (cardItem?.isFavorite == true) MaterialTheme.colorScheme.primary else null
                    ),
                    ActionStripItem(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = { onEditCard(cardId) }
                    ),
                    ActionStripItem(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = { showDeleteDialog = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                ),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    ) { paddingValues ->
        cardItem?.let { item ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Visual Card Representation
                BankCardCard(
                    item = item,
                    onClick = { /* No-op in detail view */ },
                    onDelete = { showDeleteDialog = true }
                )
                
                cardData?.let { data ->
                    // Card Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.card_details),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Card Number
                            InfoFieldWithCopy(
                                label = stringResource(R.string.card_number),
                                value = data.cardNumber.chunked(4).joinToString(" "),
                                copyValue = data.cardNumber,
                                context = context
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Expiry
                                Box(modifier = Modifier.weight(1f)) {
                                    InfoFieldWithCopy(
                                        label = stringResource(R.string.expiry_date),
                                        value = "${data.expiryMonth}/${data.expiryYear}",
                                        context = context
                                    )
                                }
                                
                                // CVV
                                Box(modifier = Modifier.weight(1f)) {
                                    PasswordField(
                                        label = stringResource(R.string.cvv),
                                        value = data.cvv,
                                        visible = cvvVisible,
                                        onToggleVisibility = { cvvVisible = !cvvVisible },
                                        context = context
                                    )
                                }
                            }
                            
                            // Cardholder
                            InfoFieldWithCopy(
                                label = stringResource(R.string.card_holder),
                                value = data.cardholderName,
                                context = context
                            )
                            
                            // Bank Name
                            if (data.bankName.isNotEmpty()) {
                                InfoFieldWithCopy(
                                    label = stringResource(R.string.bank_name),
                                    value = data.bankName,
                                    context = context
                                )
                            }
                        }
                    }
                    
                    // Billing Address
                    if (data.billingAddress.isNotEmpty()) {
                         Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                         ) {
                             Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 Text(
                                    text = stringResource(R.string.billing_address),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = data.billingAddress, // Assuming it's a formatted string for now based on model or we might need JSON parse if it is complex object. Model says String.
                                    style = MaterialTheme.typography.bodyMedium
                                )
                             }
                         }
                    }
                }
                
                // Notes
                if (item.notes.isNotEmpty()) {
                     Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                             Text(
                                text = stringResource(R.string.notes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                 Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_card_title)) },
            text = { Text(stringResource(R.string.delete_card_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cardItem?.let { viewModel.deleteCard(it.id) }
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
