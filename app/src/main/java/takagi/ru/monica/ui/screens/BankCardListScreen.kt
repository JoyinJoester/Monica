package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.BankCardCard
import takagi.ru.monica.ui.components.EmptyState
import takagi.ru.monica.ui.components.LoadingIndicator
import takagi.ru.monica.viewmodel.BankCardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardListScreen(
    viewModel: BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                LoadingIndicator()
            }
            cards.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.CreditCard,
                    title = stringResource(R.string.no_bank_cards_title),
                    description = stringResource(R.string.no_bank_cards_description)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = cards,
                        key = { it.id }
                    ) { card ->
                        BankCardCard(
                            item = card,
                            onClick = { onCardClick(card.id) },
                            onDelete = {
                                itemToDelete = card
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_bank_card_title)) },
            text = { Text(stringResource(R.string.delete_bank_card_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCard(item.id)
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
