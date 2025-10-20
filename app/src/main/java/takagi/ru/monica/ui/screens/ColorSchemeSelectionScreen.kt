package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSchemeSelectionScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCustomColors: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState()
    
    // 用于即时预览的颜色方案
    var previewColorScheme by remember { mutableStateOf(settings.colorScheme) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.color_scheme)) },
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
                .verticalScroll(rememberScrollState())
        ) {
            // 说明文本
            Text(
                text = stringResource(R.string.color_scheme_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 配色方案选项
            ColorSchemeOption(
                colorScheme = ColorScheme.DEFAULT,
                name = stringResource(R.string.default_color_scheme),
                primaryColor = Color(0xFF6650a4),
                secondaryColor = Color(0xFF625b71),
                tertiaryColor = Color(0xFF7D5260),
                isSelected = previewColorScheme == ColorScheme.DEFAULT,
                onClick = { 
                    previewColorScheme = ColorScheme.DEFAULT
                    settingsViewModel.updateColorScheme(ColorScheme.DEFAULT)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.OCEAN_BLUE,
                name = stringResource(R.string.ocean_blue_scheme),
                primaryColor = Color(0xFF1565C0),
                secondaryColor = Color(0xFF0277BD),
                tertiaryColor = Color(0xFF26C6DA),
                isSelected = previewColorScheme == ColorScheme.OCEAN_BLUE,
                onClick = { 
                    previewColorScheme = ColorScheme.OCEAN_BLUE
                    settingsViewModel.updateColorScheme(ColorScheme.OCEAN_BLUE)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.SUNSET_ORANGE,
                name = stringResource(R.string.sunset_orange_scheme),
                primaryColor = Color(0xFFE65100),
                secondaryColor = Color(0xFFF57C00),
                tertiaryColor = Color(0xFFFFA726),
                isSelected = previewColorScheme == ColorScheme.SUNSET_ORANGE,
                onClick = { 
                    previewColorScheme = ColorScheme.SUNSET_ORANGE
                    settingsViewModel.updateColorScheme(ColorScheme.SUNSET_ORANGE)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.FOREST_GREEN,
                name = stringResource(R.string.forest_green_scheme),
                primaryColor = Color(0xFF1B5E20),
                secondaryColor = Color(0xFF2E7D32),
                tertiaryColor = Color(0xFF388E3C),
                isSelected = previewColorScheme == ColorScheme.FOREST_GREEN,
                onClick = { 
                    previewColorScheme = ColorScheme.FOREST_GREEN
                    settingsViewModel.updateColorScheme(ColorScheme.FOREST_GREEN)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.TECH_PURPLE,
                name = stringResource(R.string.tech_purple_scheme),
                primaryColor = Color(0xFF4A148C),
                secondaryColor = Color(0xFF6A1B9A),
                tertiaryColor = Color(0xFF8E24AA),
                isSelected = previewColorScheme == ColorScheme.TECH_PURPLE,
                onClick = { 
                    previewColorScheme = ColorScheme.TECH_PURPLE
                    settingsViewModel.updateColorScheme(ColorScheme.TECH_PURPLE)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.BLACK_MAMBA,
                name = stringResource(R.string.black_mamba_scheme),
                primaryColor = Color(0xFF552583),  // 湖人紫
                secondaryColor = Color(0xFFFDB927), // 湖人金
                tertiaryColor = Color(0xFF2A2A2A),  // 黑曼巴灰
                isSelected = previewColorScheme == ColorScheme.BLACK_MAMBA,
                onClick = { 
                    previewColorScheme = ColorScheme.BLACK_MAMBA
                    settingsViewModel.updateColorScheme(ColorScheme.BLACK_MAMBA)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.GREY_STYLE,
                name = stringResource(R.string.grey_style_scheme),
                primaryColor = Color(0xFF616161),  // 高级灰
                secondaryColor = Color(0xFFE0E0E0), // 浅灰
                tertiaryColor = Color(0xFF37474F),  // 深蓝灰
                isSelected = previewColorScheme == ColorScheme.GREY_STYLE,
                onClick = { 
                    previewColorScheme = ColorScheme.GREY_STYLE
                    settingsViewModel.updateColorScheme(ColorScheme.GREY_STYLE)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.CUSTOM,
                name = stringResource(R.string.custom_color_scheme),
                primaryColor = Color(settings.customPrimaryColor),
                secondaryColor = Color(settings.customSecondaryColor),
                tertiaryColor = Color(settings.customTertiaryColor),
                isSelected = previewColorScheme == ColorScheme.CUSTOM,
                onClick = { 
                    // 导航到自定义颜色设置界面
                    onNavigateToCustomColors()
                }
            )
        }
    }
}

@Composable
fun ColorSchemeOption(
    colorScheme: ColorScheme,
    name: String,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    isSelected: Boolean,
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
            Row(
                modifier = Modifier
                    .size(48.dp)
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
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 方案名称
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            // 选中指示器
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}