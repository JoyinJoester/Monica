package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.PasswordUrlRuleMatcher
import takagi.ru.monica.data.model.PasswordUrlRules.Mode

@Composable
internal fun urlMatchModeLabel(mode: Mode): String = stringResource(when (mode) {
    Mode.DEFAULT -> R.string.url_match_default
    Mode.BASE_DOMAIN -> R.string.autofill_domain_strategy_base_domain
    Mode.HOST -> R.string.url_match_host
    Mode.STARTS_WITH -> R.string.autofill_domain_strategy_starts_with
    Mode.EXACT -> R.string.autofill_domain_strategy_exact_match
    Mode.REGEX -> R.string.autofill_domain_strategy_regex
    Mode.NEVER -> R.string.autofill_domain_strategy_never
})

@Composable
internal fun UrlMatchModeDialog(url: String, initial: Mode, onConfirm: (Mode) -> Unit, onDismiss: () -> Unit) {
    var selectedName by rememberSaveable { mutableStateOf(initial.name) }
    val selected = Mode.valueOf(selectedName)
    val valid = selected != Mode.REGEX || PasswordUrlRuleMatcher.validRegex(url)
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Rule, null) },
        title = { Text(stringResource(R.string.url_match_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(url, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.url_match_explanation))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Mode.entries.forEachIndexed { index, mode ->
                        Surface(shape = entryGroupShape(index, Mode.entries.size),
                            color = if (selected == mode) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().selectable(selected == mode, role = Role.RadioButton,
                                onClick = { selectedName = mode.name }).testTag("url_match_${mode.name}")
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected == mode, onClick = null)
                                Text(urlMatchModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.url_match_limited_context), style = MaterialTheme.typography.bodySmall)
                if (!valid) Text(stringResource(R.string.url_match_regex_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = valid,
            modifier = Modifier.testTag("url_match_confirm")) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
