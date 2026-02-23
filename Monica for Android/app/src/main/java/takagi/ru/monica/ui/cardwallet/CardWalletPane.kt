package takagi.ru.monica.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

@Composable
internal fun CardWalletPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    saveableStateHolder: SaveableStateHolder,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    contentState: CardWalletContentState,
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
    val listPaneContent: @Composable ColumnScope.() -> Unit = {
        CardWalletContent(
            saveableStateHolder = saveableStateHolder,
            bankCardViewModel = bankCardViewModel,
            documentViewModel = documentViewModel,
            state = contentState
        )
    }

    if (isCompactWidth) {
        ListPane(
            modifier = Modifier.fillMaxSize(),
            content = listPaneContent
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth),
                content = listPaneContent
            )
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CardWalletDetailPaneContent(
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    isAddingBankCardInline = isAddingBankCardInline,
                    inlineBankCardEditorId = inlineBankCardEditorId,
                    onInlineBankCardEditorBack = onInlineBankCardEditorBack,
                    isAddingDocumentInline = isAddingDocumentInline,
                    inlineDocumentEditorId = inlineDocumentEditorId,
                    onInlineDocumentEditorBack = onInlineDocumentEditorBack,
                    selectedBankCardId = selectedBankCardId,
                    onClearSelectedBankCard = onClearSelectedBankCard,
                    onEditBankCard = onEditBankCard,
                    selectedDocumentId = selectedDocumentId,
                    onClearSelectedDocument = onClearSelectedDocument,
                    onEditDocument = onEditDocument
                )
            }
        }
    }
}
