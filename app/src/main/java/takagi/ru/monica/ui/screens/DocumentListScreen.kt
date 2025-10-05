package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.DocumentCard
import takagi.ru.monica.ui.components.EmptyState
import takagi.ru.monica.ui.components.LoadingIndicator
import takagi.ru.monica.viewmodel.DocumentViewModel

@Composable
fun DocumentListScreen(
    viewModel: DocumentViewModel,
    onDocumentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                LoadingIndicator()
            }
            documents.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.no_documents_title),
                    description = stringResource(R.string.no_documents_description)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = documents,
                        key = { it.id }
                    ) { document ->
                        DocumentCard(
                            item = document,
                            onClick = { onDocumentClick(document.id) }
                        )
                    }
                }
            }
        }
    }
}
