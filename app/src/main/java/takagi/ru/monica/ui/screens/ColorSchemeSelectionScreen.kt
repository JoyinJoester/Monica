package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
                colorScheme = ColorScheme.DEEP_BLUE_TEAL,
                name = stringResource(R.string.deep_blue_teal_scheme),
                primaryColor = Color(0xFF143268),
                secondaryColor = Color(0xFF61c1bd),
                tertiaryColor = Color(0xFF98c9d9),
                isSelected = previewColorScheme == ColorScheme.DEEP_BLUE_TEAL,
                onClick = { 
                    previewColorScheme = ColorScheme.DEEP_BLUE_TEAL
                    settingsViewModel.updateColorScheme(ColorScheme.DEEP_BLUE_TEAL)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.DEEP_BLUE_RED,
                name = stringResource(R.string.deep_blue_red_scheme),
                primaryColor = Color(0xFF32586d),
                secondaryColor = Color(0xFFca3032),
                tertiaryColor = Color(0xFFb2d1d6),
                isSelected = previewColorScheme == ColorScheme.DEEP_BLUE_RED,
                onClick = { 
                    previewColorScheme = ColorScheme.DEEP_BLUE_RED
                    settingsViewModel.updateColorScheme(ColorScheme.DEEP_BLUE_RED)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.PINK_PURPLE_BLUE,
                name = stringResource(R.string.pink_purple_blue_scheme),
                primaryColor = Color(0xFFe6c5cf),
                secondaryColor = Color(0xFFbdd8dd),
                tertiaryColor = Color(0xFFaf9dc0),
                isSelected = previewColorScheme == ColorScheme.PINK_PURPLE_BLUE,
                onClick = { 
                    previewColorScheme = ColorScheme.PINK_PURPLE_BLUE
                    settingsViewModel.updateColorScheme(ColorScheme.PINK_PURPLE_BLUE)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.DARK_THEME,
                name = stringResource(R.string.dark_theme_scheme),
                primaryColor = Color(0xFF263238),
                secondaryColor = Color(0xFF529bba),
                tertiaryColor = Color(0xFF63b8a7),
                isSelected = previewColorScheme == ColorScheme.DARK_THEME,
                onClick = { 
                    previewColorScheme = ColorScheme.DARK_THEME
                    settingsViewModel.updateColorScheme(ColorScheme.DARK_THEME)
                }
            )
            
            ColorSchemeOption(
                colorScheme = ColorScheme.GRAY_BLUE_RED,
                name = stringResource(R.string.gray_blue_red_scheme),
                primaryColor = Color(0xFFd9d9d9),
                secondaryColor = Color(0xFF286181),
                tertiaryColor = Color(0xFFff5757),
                isSelected = previewColorScheme == ColorScheme.GRAY_BLUE_RED,
                onClick = { 
                    previewColorScheme = ColorScheme.GRAY_BLUE_RED
                    settingsViewModel.updateColorScheme(ColorScheme.GRAY_BLUE_RED)
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