package takagi.ru.monica.ui.password

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import java.util.Locale

private data class UsernameAggregate(
    val value: String,
    var usageCount: Int = 0
)

private data class RankedUsernameSuggestion(
    val suggestion: UsernameSuggestionCandidate,
    val score: Int
)

data class UsernameSuggestionCandidate(
    val value: String,
    val usageCount: Int
)

sealed class UsernameSuggestionState {
    object Hidden : UsernameSuggestionState()

    data class Multiple(
        val suggestions: List<UsernameSuggestionCandidate>,
        val totalMatches: Int
    ) : UsernameSuggestionState()

    data class Unique(
        val suggestion: UsernameSuggestionCandidate
    ) : UsernameSuggestionState()

    data class NoMatch(
        val query: String
    ) : UsernameSuggestionState()
}

fun buildUsernameSuggestionState(
    query: String,
    currentEntryId: Long?,
    passwordEntries: List<PasswordEntry>,
    maxSuggestions: Int = 3
): UsernameSuggestionState {
    val normalizedQuery = normalizeUsernameValue(query)
    if (normalizedQuery.isBlank()) {
        return UsernameSuggestionState.Hidden
    }

    val normalizedCurrentEntryId = currentEntryId?.let { if (it < 0) -it else it }
    val aggregates = linkedMapOf<String, UsernameAggregate>()

    passwordEntries
        .asSequence()
        .filter { entry -> normalizedCurrentEntryId == null || entry.id != normalizedCurrentEntryId }
        .forEach { entry ->
            val normalizedUsername = normalizeUsernameValue(entry.username)
            if (normalizedUsername.isBlank()) return@forEach

            val key = normalizedUsername.lowercase(Locale.ROOT)
            val aggregate = aggregates.getOrPut(key) {
                UsernameAggregate(value = normalizedUsername)
            }
            aggregate.usageCount += 1
        }

    val rankedSuggestions = aggregates.values
        .mapNotNull { aggregate ->
            val score = calculateUsernameMatchScore(
                query = normalizedQuery,
                candidate = aggregate.value,
                usageCount = aggregate.usageCount
            ) ?: return@mapNotNull null
            RankedUsernameSuggestion(
                suggestion = UsernameSuggestionCandidate(
                    value = aggregate.value,
                    usageCount = aggregate.usageCount
                ),
                score = score
            )
        }
        .sortedWith(
            compareByDescending<RankedUsernameSuggestion> { it.score }
                .thenByDescending { it.suggestion.usageCount }
                .thenBy { it.suggestion.value.length }
                .thenBy { it.suggestion.value.lowercase(Locale.ROOT) }
        )

    if (rankedSuggestions.isEmpty()) {
        return UsernameSuggestionState.NoMatch(query = normalizedQuery)
    }

    val suggestions = rankedSuggestions.map { it.suggestion }
    if (suggestions.size == 1) {
        return UsernameSuggestionState.Unique(suggestion = suggestions.first())
    }

    return UsernameSuggestionState.Multiple(
        suggestions = suggestions.take(maxSuggestions),
        totalMatches = suggestions.size
    )
}

@Composable
fun UsernameSuggestionPanel(
    state: UsernameSuggestionState,
    onApplySuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        UsernameSuggestionState.Hidden -> Unit
        is UsernameSuggestionState.NoMatch -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = stringResource(R.string.username_suggestions_no_match, state.query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        is UsernameSuggestionState.Unique -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.username_suggestions_unique_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                UsernameSuggestionChip(
                    suggestion = state.suggestion,
                    onClick = { onApplySuggestion(state.suggestion.value) }
                )
            }
        }

        is UsernameSuggestionState.Multiple -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.suggestions.forEach { suggestion ->
                    UsernameSuggestionChip(
                        suggestion = suggestion,
                        onClick = { onApplySuggestion(suggestion.value) }
                    )
                }

                val hiddenCount = state.totalMatches - state.suggestions.size
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.username_suggestions_more_count, hiddenCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UsernameSuggestionChip(
    suggestion: UsernameSuggestionCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = suggestion.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun normalizeUsernameValue(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ")
}

private fun calculateUsernameMatchScore(
    query: String,
    candidate: String,
    usageCount: Int
): Int? {
    val normalizedQuery = query.lowercase(Locale.ROOT)
    val normalizedCandidate = candidate.lowercase(Locale.ROOT)

    val lengthBonus = normalizedQuery.length * 45
    val usageBonus = usageCount * 8

    return when {
        normalizedCandidate == normalizedQuery -> 10_000 + lengthBonus + usageBonus
        normalizedCandidate.startsWith(normalizedQuery) -> {
            val tailLength = (normalizedCandidate.length - normalizedQuery.length).coerceAtLeast(0)
            8_000 + lengthBonus + usageBonus - tailLength
        }

        else -> {
            val index = normalizedCandidate.indexOf(normalizedQuery)
            if (index < 0) {
                null
            } else {
                val tailLength = (normalizedCandidate.length - normalizedQuery.length).coerceAtLeast(0)
                5_000 + lengthBonus + usageBonus - (index * 3) - tailLength
            }
        }
    }
}
