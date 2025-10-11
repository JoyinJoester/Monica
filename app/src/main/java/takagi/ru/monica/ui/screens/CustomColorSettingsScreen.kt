package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R
import takagi.ru.monica.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState()
    
    var primaryColor by remember(settings.customPrimaryColor) { mutableStateOf(Color(settings.customPrimaryColor)) }
    var secondaryColor by remember(settings.customSecondaryColor) { mutableStateOf(Color(settings.customSecondaryColor)) }
    var tertiaryColor by remember(settings.customTertiaryColor) { mutableStateOf(Color(settings.customTertiaryColor)) }
    
    // 颜色选择器状态
    var showPrimaryColorPicker by remember { mutableStateOf(false) }
    var showSecondaryColorPicker by remember { mutableStateOf(false) }
    var showTertiaryColorPicker by remember { mutableStateOf(false) }
    
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 说明文本
            Text(
                text = stringResource(R.string.custom_color_scheme_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 当前颜色预览
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.current_colors),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // 颜色预览圆点
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(primaryColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(secondaryColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(tertiaryColor)
                        )
                    }
                    
                    // 颜色标签
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.primary_color),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.secondary_color),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.tertiary_color),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 颜色选择器
            ColorOption(
                label = stringResource(R.string.primary_color),
                color = primaryColor,
                onClick = { showPrimaryColorPicker = true }
            )
            
            ColorOption(
                label = stringResource(R.string.secondary_color),
                color = secondaryColor,
                onClick = { showSecondaryColorPicker = true }
            )
            
            ColorOption(
                label = stringResource(R.string.tertiary_color),
                color = tertiaryColor,
                onClick = { showTertiaryColorPicker = true }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 应用按钮
            Button(
                onClick = {
                    // 保存自定义颜色并应用
                    settingsViewModel.updateCustomColors(
                        primaryColor.toLong(),
                        secondaryColor.toLong(),
                        tertiaryColor.toLong()
                    )
                    // 设置颜色方案为自定义
                    settingsViewModel.updateColorScheme(takagi.ru.monica.data.ColorScheme.CUSTOM)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(stringResource(R.string.apply_custom_colors))
            }
        }
    }
    
    // 颜色选择器对话框
    if (showPrimaryColorPicker) {
        ColorPickerDialog(
            initialColor = primaryColor,
            onColorSelected = { color ->
                primaryColor = color
                showPrimaryColorPicker = false
            },
            onDismiss = { showPrimaryColorPicker = false }
        )
    }
    
    if (showSecondaryColorPicker) {
        ColorPickerDialog(
            initialColor = secondaryColor,
            onColorSelected = { color ->
                secondaryColor = color
                showSecondaryColorPicker = false
            },
            onDismiss = { showSecondaryColorPicker = false }
        )
    }
    
    if (showTertiaryColorPicker) {
        ColorPickerDialog(
            initialColor = tertiaryColor,
            onColorSelected = { color ->
                tertiaryColor = color
                showTertiaryColorPicker = false
            },
            onDismiss = { showTertiaryColorPicker = false }
        )
    }
}

@Composable
fun ColorOption(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 颜色预览圆点
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 颜色标签
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            // 选择指示器
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_color)) },
        text = {
            Column {
                // 这里可以实现一个简单的颜色选择器
                // 为了简化，我们使用预定义的颜色选项
                Text(
                    text = stringResource(R.string.select_from_preset_colors),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 预定义颜色选项
                val presetColors = listOf(
                    Color(0xFF6650a4), // 默认紫色
                    Color(0xFF4286ff), // 蓝色
                    Color(0xFF286181), // 深蓝色
                    Color(0xFF61c1bd), // 青色
                    Color(0xFF529bba), // 浅蓝色
                    Color(0xFFca3032), // 红色
                    Color(0xFFe53939), // 深红色
                    Color(0xFFff5757), // 浅红色
                    Color(0xFF63b8a7), // 绿色
                    Color(0xFFaf9dc0), // 紫色
                    Color(0xFFc26dbc), // 粉色
                    Color(0xFF5f7a8c)  // 灰蓝色
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(presetColors) { color ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// 扩展函数将Color转换为Long
fun Color.toLong(): Long {
    return (alpha.times(255).toInt().toLong() shl 24) or 
           (red.times(255).toInt().toLong() shl 16) or 
           (green.times(255).toInt().toLong() shl 8) or 
           blue.times(255).toInt().toLong()
}

// 扩展函数将Long转换为Color
fun Long.toColor(): Color {
    return Color(
        alpha = ((this shr 24) and 0xFF).toInt().div(255f),
        red = ((this shr 16) and 0xFF).toInt().div(255f),
        green = ((this shr 8) and 0xFF).toInt().div(255f),
        blue = (this and 0xFF).toInt().div(255f)
    )
}