package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import takagi.ru.monica.data.*
import takagi.ru.monica.repository.CommonFieldSuggestionRepository
import takagi.ru.monica.repository.CommonFieldSuggestionSource
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.rememberUiSecurityManager

internal val LocalCommonFieldSuggestionSource = staticCompositionLocalOf<CommonFieldSuggestionSource?> { null }

/** One source for inline completion and the explicit common-name picker. */
@Composable
internal fun rememberCommonFieldSuggestionSource(database: PasswordDatabase? = null): CommonFieldSuggestionSource {
    val context = LocalContext.current.applicationContext
    val security = rememberUiSecurityManager()
    val override = LocalCommonFieldSuggestionSource.current
    val nameType = androidx.compose.ui.res.stringResource(takagi.ru.monica.R.string.common_account_type_name)
    return remember(context, security, override, database, nameType) { override ?: CommonFieldSuggestionRepository(
        database ?: PasswordDatabase.getDatabase(context), security::decryptDataIfMonicaCiphertext,
        nameTemplates = CommonAccountPreferences(context, security).templatesFlow.map { templates ->
            templates.filter { it.type.trim().equals("name", true) || it.type.trim() == "姓名" || it.type.trim() == nameType }
                .map { it.content }
        }, billingTemplate = CommonAccountPreferences(context, security).billingAddress) }
}

/** The normal form control plus non-modal suggestions; focus and IME stay with the input. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestionField: CommonSuggestionField?,
    modifier: Modifier = Modifier,
    containerModifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    saveTextState: Boolean = true,
    enabled: Boolean = true,
    entryContentStyle: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var accepted by remember { mutableStateOf<String?>(null) }
    val textState = if (saveTextState) rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    } else remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var fieldValue by textState
    LaunchedEffect(value) {
        if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(
            fieldValue.selection.start.coerceIn(0, value.length), fieldValue.selection.end.coerceIn(0, value.length)))
    }
    Column(containerModifier.fillMaxWidth()) {
        OutlinedTextField(fieldValue, { next ->
            val changed = next.text != fieldValue.text
            fieldValue = next
            if (changed) { accepted = null; onValueChange(next.text) }
        }, modifier = modifier,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            supportingText = supportingText,
            singleLine = singleLine, shape = shape, keyboardOptions = keyboardOptions,
            enabled = enabled, interactionSource = interaction,
            visualTransformation = visualTransformation, entryContentStyle = entryContentStyle)
        if (suggestionField != null && enabled && focused && value.isNotBlank() && accepted != value) {
            val unlocked by SessionManager.isUnlocked.collectAsStateWithLifecycle()
            if (unlocked) {
                val source = rememberCommonFieldSuggestionSource()
                val flow = remember(source, suggestionField) { source.observe(suggestionField) }
                val index by flow.collectAsStateWithLifecycle(initialValue = CommonFieldSuggestionIndex(emptyList(), suggestionField))
                var matches by remember(index, value) { mutableStateOf(emptyList<CommonFieldCandidate>()) }
                LaunchedEffect(index, value) {
                    matches = withContext(Dispatchers.Default) { index.match(value) }
                }
                val bringIntoView = remember { BringIntoViewRequester() }
                var panelSize by remember { mutableStateOf(IntSize.Zero) }
                val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                val bottomClearance = with(LocalDensity.current) { 88.dp.toPx() }
                // The IME can finish opening after the first match appears. Reposition for its final viewport.
                LaunchedEffect(matches.isNotEmpty(), imeBottom, panelSize) {
                    if (matches.isNotEmpty() && panelSize.height > 0) {
                        delay(180)
                        // Reserve the editor's floating Save button area without adding gaps between fields.
                        bringIntoView.bringIntoView(Rect(0f, 0f, panelSize.width.toFloat(), panelSize.height + bottomClearance))
                    }
                }
                AnimatedVisibility(matches.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp).bringIntoViewRequester(bringIntoView)
                        .onSizeChanged { panelSize = it }
                        .testTag("common_suggestions_${suggestionField.key}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matches.forEachIndexed { i, match -> key(match.value) {
                            Surface(onClick = {
                                if (SessionManager.isUnlocked.value) {
                                    accepted = match.value
                                    fieldValue = TextFieldValue(match.value, TextRange(match.value.length))
                                    onValueChange(match.value)
                                }
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("common_suggestion_${suggestionField.key}_$i"),
                                shape = RoundedCornerShape(topStart = if (i == 0) 20.dp else 4.dp, topEnd = if (i == 0) 20.dp else 4.dp,
                                    bottomStart = if (i == matches.lastIndex) 20.dp else 4.dp, bottomEnd = if (i == matches.lastIndex) 20.dp else 4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                                    Text(match.value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } }
                    }
                }
            }
        }
    }
}
