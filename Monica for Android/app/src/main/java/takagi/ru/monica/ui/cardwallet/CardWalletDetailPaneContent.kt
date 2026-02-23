package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

@Composable
internal fun CardWalletDetailPaneContent(
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    isAddingBankCardInline: Boolean,
    inlineBankCardEditorId: Long?,
    onInlineBankCardEditorBack: () -> Unit,
    isAddingDocumentInline: Boolean,
    inlineDocumentEditorId: Long?,
    onInlineDocumentEditorBack: () -> Unit,
    selectedBankCardId: Long?,
    onClearSelectedBankCard: () -> Unit,
    onEditBankCard: (Long) -> Unit,
    selectedDocumentId: Long?,
    onClearSelectedDocument: () -> Unit,
    onEditDocument: (Long) -> Unit
) {
    when {
        isAddingBankCardInline || inlineBankCardEditorId != null -> {
            AddEditBankCardScreen(
                viewModel = bankCardViewModel,
                cardId = inlineBankCardEditorId,
                onNavigateBack = onInlineBankCardEditorBack,
                modifier = Modifier.fillMaxSize()
            )
        }
        isAddingDocumentInline || inlineDocumentEditorId != null -> {
            AddEditDocumentScreen(
                viewModel = documentViewModel,
                documentId = inlineDocumentEditorId,
                onNavigateBack = onInlineDocumentEditorBack,
                modifier = Modifier.fillMaxSize()
            )
        }
        selectedBankCardId != null -> {
            BankCardDetailScreen(
                viewModel = bankCardViewModel,
                cardId = selectedBankCardId,
                onNavigateBack = onClearSelectedBankCard,
                onEditCard = onEditBankCard,
                modifier = Modifier.fillMaxSize()
            )
        }
        selectedDocumentId != null -> {
            DocumentDetailScreen(
                viewModel = documentViewModel,
                documentId = selectedDocumentId,
                onNavigateBack = onClearSelectedDocument,
                onEditDocument = onEditDocument,
                modifier = Modifier.fillMaxSize()
            )
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select an item to view details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
