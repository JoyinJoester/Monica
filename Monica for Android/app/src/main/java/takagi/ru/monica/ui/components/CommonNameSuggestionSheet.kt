package takagi.ru.monica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.CommonAccountPreferences
import takagi.ru.monica.data.CommonAccountTemplate
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.displayFullName
import java.util.Locale

enum class CommonNameSuggestionSource {
    TEMPLATE,
    ANALYZED
}

data class CommonNameSuggestion(
    val id: String,
    val name: String,
    val supportingText: String?,
    val source: CommonNameSuggestionSource
)

data class CommonNameSuggestionState(
    val templateSuggestions: List<CommonNameSuggestion>,
    val analyzedSuggestions: List<CommonNameSuggestion>
) {
    val hasAny: Boolean
        get() = templateSuggestions.isNotEmpty() || analyzedSuggestions.isNotEmpty()

    fun hasTemplateFor(name: String): Boolean {
        val key = normalizeCommonNameKey(name)
        return templateSuggestions.any { normalizeCommonNameKey(it.name) == key }
    }
}

private data class CommonNameAggregate(
    val name: String,
    var count: Int = 0,
    val sources: LinkedHashSet<String> = linkedSetOf()
)

@Composable
fun rememberCommonNameSuggestionState(
    database: PasswordDatabase
): CommonNameSuggestionState {
    val context = LocalContext.current
    val preferences = remember(context) { CommonAccountPreferences(context) }
    val templates by preferences.templatesFlow.collectAsState(initial = emptyList())
    val secureItems by database.secureItemDao().getAllItems().collectAsState(initial = emptyList())
    val localizedNameType = stringResource(R.string.common_account_type_name)
    val analyzedLabel = stringResource(R.string.common_name_fill_from_analysis)

    return remember(templates, secureItems, localizedNameType, analyzedLabel) {
        buildCommonNameSuggestionState(
            templates = templates,
            secureItems = secureItems,
            localizedNameType = localizedNameType,
            analyzedLabel = analyzedLabel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommonNameSuggestionSheet(
    suggestionState: CommonNameSuggestionState,
    currentName: String,
    onDismiss: () -> Unit,
    onSelectName: (String) -> Unit,
    onSaveCurrentName: (String) -> Unit
) {
    val canSaveCurrentName = currentName.trim().isNotBlank() &&
        !suggestionState.hasTemplateFor(currentName)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_name_fill_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.common_name_fill_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${suggestionState.templateSuggestions.size}",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "${suggestionState.analyzedSuggestions.size}",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (!suggestionState.hasAny) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_name_fill_empty),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.common_name_fill_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                suggestionState.templateSuggestions.forEach { suggestion ->
                    CommonNameSuggestionCard(
                        suggestion = suggestion,
                        icon = Icons.Default.Person,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        badgeText = null,
                        onClick = { onSelectName(suggestion.name) }
                    )
                }

                if (suggestionState.analyzedSuggestions.isNotEmpty()) {
                    suggestionState.analyzedSuggestions.forEach { suggestion ->
                        CommonNameSuggestionCard(
                            suggestion = suggestion,
                            icon = Icons.Default.AutoAwesome,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            badgeText = stringResource(R.string.common_name_fill_from_analysis),
                            onClick = { onSelectName(suggestion.name) }
                        )
                    }
                }
            }

            if (canSaveCurrentName) {
                Button(
                    onClick = { onSaveCurrentName(currentName.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.common_name_fill_save_template))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CommonNameSuggestionCard(
    suggestion: CommonNameSuggestion,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    badgeText: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                badgeText?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = suggestion.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                suggestion.supportingText?.takeIf { it.isNotBlank() }?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun buildCommonNameSuggestionState(
    templates: List<CommonAccountTemplate>,
    secureItems: List<SecureItem>,
    localizedNameType: String,
    analyzedLabel: String
): CommonNameSuggestionState {
    val templateSuggestions = templates
        .asSequence()
        .filter { isCommonNameTemplateType(it.type, localizedNameType) }
        .mapNotNull { template ->
            val normalizedName = normalizeCommonNameValue(template.content)
            normalizedName.takeIf { it.isNotBlank() }?.let {
                CommonNameSuggestion(
                    id = template.id,
                    name = it,
                    supportingText = null,
                    source = CommonNameSuggestionSource.TEMPLATE
                )
            }
        }
        .distinctBy { normalizeCommonNameKey(it.name) }
        .toList()

    val templateKeys = templateSuggestions
        .map { normalizeCommonNameKey(it.name) }
        .toSet()

    val analyzedAggregates = linkedMapOf<String, CommonNameAggregate>()
    secureItems.asSequence()
        .filterNot { it.isDeleted }
        .forEach { item ->
            val extractedName = extractCommonName(item)
            if (extractedName.isBlank()) return@forEach

            val normalizedName = normalizeCommonNameValue(extractedName)
            val key = normalizeCommonNameKey(normalizedName)
            if (key.isEmpty()) return@forEach

            val aggregate = analyzedAggregates.getOrPut(key) {
                CommonNameAggregate(name = normalizedName)
            }
            aggregate.count += 1
            item.title.trim().takeIf { it.isNotBlank() }?.let(aggregate.sources::add)
        }

    val analyzedSuggestions = analyzedAggregates.values
        .asSequence()
        .filterNot { templateKeys.contains(normalizeCommonNameKey(it.name)) }
        .sortedWith(
            compareByDescending<CommonNameAggregate> { it.count }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
        .map { aggregate ->
            CommonNameSuggestion(
                id = "analyzed_${normalizeCommonNameKey(aggregate.name)}",
                name = aggregate.name,
                supportingText = aggregate.sources
                    .take(2)
                    .joinToString(" · ")
                    .ifBlank { analyzedLabel },
                source = CommonNameSuggestionSource.ANALYZED
            )
        }
        .toList()

    return CommonNameSuggestionState(
        templateSuggestions = templateSuggestions,
        analyzedSuggestions = analyzedSuggestions
    )
}

private fun isCommonNameTemplateType(rawType: String, localizedNameType: String): Boolean {
    val normalized = rawType.trim().lowercase(Locale.ROOT)
    return normalized == localizedNameType.lowercase(Locale.ROOT) ||
        normalized == "name" ||
        normalized == "姓名"
}

private fun extractCommonName(item: SecureItem): String {
    return when (item.itemType) {
        ItemType.BANK_CARD -> runCatching {
            Json.decodeFromString<BankCardData>(item.itemData).cardholderName
        }.getOrDefault("")
        ItemType.DOCUMENT -> runCatching {
            Json.decodeFromString<DocumentData>(item.itemData).displayFullName()
        }.getOrDefault("")
        else -> ""
    }
}

private fun normalizeCommonNameValue(rawName: String): String {
    return rawName
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun normalizeCommonNameKey(rawName: String): String {
    return normalizeCommonNameValue(rawName)
        .lowercase(Locale.ROOT)
}
