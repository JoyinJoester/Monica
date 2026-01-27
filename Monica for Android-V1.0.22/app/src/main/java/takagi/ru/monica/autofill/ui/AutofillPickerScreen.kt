package takagi.ru.monica.autofill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import takagi.ru.monica.autofill.data.AutofillItem
import takagi.ru.monica.autofill.data.PaymentInfo
import takagi.ru.monica.data.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutofillPickerScreen(
    passwords: List<PasswordEntry>,
    paymentInfo: List<PaymentInfo>,
    packageName: String?,
    domain: String?,
    fieldType: String?,
    onItemSelected: (AutofillItem) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val debouncedQuery by rememberDebouncedValue(searchQuery)
    val filteredPasswords by rememberFilteredPasswords(passwords, debouncedQuery)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AutofillSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth()
            )
            PasswordList(
                passwords = if (debouncedQuery.isBlank()) passwords else filteredPasswords,
                onItemSelected = onItemSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            )
        }
    }
}
