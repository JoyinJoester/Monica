package takagi.ru.monica.autofill_ng

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

internal data class AutofillSaveCandidate(val id: Long, val title: String, val account: String, val source: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutofillSaveReview(candidates: List<AutofillSaveCandidate>, target: String,
                               onSelect: (Long?) -> Unit, onCancel: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.autofill_save_review_title)) },
        navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) } }) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(24.dp)) {
                Button(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.autofill_save_as_new))
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text(target, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.autofill_save_review_description), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(candidates, key = { _, candidate -> candidate.id }) { index, candidate ->
                Surface(onClick = { onSelect(candidate.id) }, shape = RoundedCornerShape(
                    topStart = if (index == 0) 24.dp else 6.dp, topEnd = if (index == 0) 24.dp else 6.dp,
                    bottomStart = if (index == candidates.lastIndex) 24.dp else 6.dp,
                    bottomEnd = if (index == candidates.lastIndex) 24.dp else 6.dp)) {
                    ListItem(headlineContent = { Text(candidate.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${candidate.account} · ${candidate.source}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Outlined.Key, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                }
            }
        }
    }
}
