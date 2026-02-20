package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.ui.theme.generateCustomMaterialColorScheme
import takagi.ru.monica.viewmodel.SettingsViewModel
import java.util.Locale

private const val DEFAULT_PRIMARY_SEED = 0xFF6650A4
private const val DEFAULT_SECONDARY_SEED = 0xFF625B71
private const val DEFAULT_TERTIARY_SEED = 0xFF7D5260

private enum class SeedTarget {
    PRIMARY, SECONDARY, TERTIARY
}

private fun Color.toStoreLong(): Long = toArgb().toLong() and 0xFFFFFFFF

private fun Color.toHexColor(): String {
    val rgb = toArgb() and 0xFFFFFF
    return String.format(Locale.US, "#%06X", rgb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState()

    var primarySeed by remember(settings.customPrimaryColor) {
        mutableStateOf(Color(settings.customPrimaryColor))
    }
    var secondarySeed by remember(settings.customSecondaryColor) {
        mutableStateOf(Color(settings.customSecondaryColor))
    }
    var tertiarySeed by remember(settings.customTertiaryColor) {
        mutableStateOf(Color(settings.customTertiaryColor))
    }

    var pickerTarget by remember { mutableStateOf<SeedTarget?>(null) }
    val isDarkTheme = isSystemInDarkTheme()
    val previewScheme = remember(primarySeed, secondarySeed, tertiarySeed, isDarkTheme) {
        generateCustomMaterialColorScheme(
            darkTheme = isDarkTheme,
            primarySeed = primarySeed.toStoreLong(),
            secondarySeed = secondarySeed.toStoreLong(),
            tertiarySeed = tertiarySeed.toStoreLong()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_color_scheme)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            primarySeed = Color(DEFAULT_PRIMARY_SEED)
                            secondarySeed = Color(DEFAULT_SECONDARY_SEED)
                            tertiarySeed = Color(DEFAULT_TERTIARY_SEED)
                        }
                    ) {
                        Text(stringResource(R.string.reset_custom_colors))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.custom_color_scheme_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.custom_color_scheme_generated_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.seed_colors),
                style = MaterialTheme.typography.titleMedium
            )

            SeedColorCard(
                label = stringResource(R.string.primary_color),
                color = primarySeed,
                onClick = { pickerTarget = SeedTarget.PRIMARY }
            )
            SeedColorCard(
                label = stringResource(R.string.secondary_color),
                color = secondarySeed,
                onClick = { pickerTarget = SeedTarget.SECONDARY }
            )
            SeedColorCard(
                label = stringResource(R.string.tertiary_color),
                color = tertiarySeed,
                onClick = { pickerTarget = SeedTarget.TERTIARY }
            )

            Text(
                text = stringResource(R.string.live_preview),
                style = MaterialTheme.typography.titleMedium
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                color = previewScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewDot(color = previewScheme.primary)
                        PreviewDot(color = previewScheme.secondary)
                        PreviewDot(color = previewScheme.tertiary)
                        PreviewDot(color = previewScheme.error)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = previewScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.custom_preview_primary_container),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = previewScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = previewScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.custom_preview_surface_outline),
                                color = previewScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(previewScheme.outlineVariant)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    settingsViewModel.updateCustomColors(
                        primary = primarySeed.toStoreLong(),
                        secondary = secondarySeed.toStoreLong(),
                        tertiary = tertiarySeed.toStoreLong()
                    )
                    settingsViewModel.updateColorScheme(ColorScheme.CUSTOM)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                Text(stringResource(R.string.apply_custom_colors))
            }
        }
    }

    val target = pickerTarget
    if (target != null) {
        val initialColor = when (target) {
            SeedTarget.PRIMARY -> primarySeed
            SeedTarget.SECONDARY -> secondarySeed
            SeedTarget.TERTIARY -> tertiarySeed
        }
        ColorPickerDialog(
            initialColor = initialColor,
            onColorSelected = { selected ->
                when (target) {
                    SeedTarget.PRIMARY -> primarySeed = selected
                    SeedTarget.SECONDARY -> secondarySeed = selected
                    SeedTarget.TERTIARY -> tertiarySeed = selected
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun SeedColorCard(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = color.toHexColor(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text(stringResource(R.string.select_color))
            }
        }
    }
}

@Composable
private fun PreviewDot(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(initialColor) { mutableStateOf(initialColor) }
    val presetColors = remember {
        listOf(
            Color(0xFF6650A4), Color(0xFF3366FF), Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF5E35B1),
            Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
            Color(0xFF7CB342), Color(0xFF43A047), Color(0xFF00897B), Color(0xFF546E7A), Color(0xFF6D4C41),
            Color(0xFF3949AB), Color(0xFFC2185B), Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF455A64)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.select_from_preset_colors),
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetColors) { color ->
                        val selectedColor = color.toArgb() == selected.toArgb()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selected = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selected) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
