package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.viewmodel.GeneratorType

@Composable
internal fun GeneratorDetailPane(
    selectedGenerator: GeneratorType,
    generatedValue: String,
    modifier: Modifier = Modifier
) {
    val generatorLabel = when (selectedGenerator) {
        GeneratorType.SYMBOL -> stringResource(R.string.generator_symbol)
        GeneratorType.PASSWORD -> stringResource(R.string.generator_word)
        GeneratorType.PASSPHRASE -> stringResource(R.string.generator_passphrase)
        GeneratorType.PIN -> stringResource(R.string.generator_pin)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.generator_result),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = generatorLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = generatedValue.ifBlank { stringResource(R.string.loading_default) },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
