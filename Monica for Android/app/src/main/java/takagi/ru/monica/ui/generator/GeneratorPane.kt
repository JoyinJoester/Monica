package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.GeneratorScreen
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel

@Composable
internal fun GeneratorPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    generatorViewModel: GeneratorViewModel,
    passwordViewModel: PasswordViewModel,
    externalRefreshRequestKey: Int,
    onRefreshRequestConsumed: () -> Unit,
    selectedGenerator: GeneratorType,
    generatedValue: String
) {
    if (isCompactWidth) {
        GeneratorScreen(
            onNavigateBack = {},
            viewModel = generatorViewModel,
            passwordViewModel = passwordViewModel,
            externalRefreshRequestKey = externalRefreshRequestKey,
            onRefreshRequestConsumed = onRefreshRequestConsumed,
            useExternalRefreshFab = true
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth)
            ) {
                GeneratorScreen(
                    onNavigateBack = {},
                    viewModel = generatorViewModel,
                    passwordViewModel = passwordViewModel,
                    externalRefreshRequestKey = externalRefreshRequestKey,
                    onRefreshRequestConsumed = onRefreshRequestConsumed,
                    useExternalRefreshFab = true
                )
            }
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                GeneratorDetailPane(
                    selectedGenerator = selectedGenerator,
                    generatedValue = generatedValue,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
