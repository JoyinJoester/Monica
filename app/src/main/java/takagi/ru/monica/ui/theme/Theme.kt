package takagi.ru.monica.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import takagi.ru.monica.data.ColorScheme

// 默认配色方案
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// 深蓝青绿方案
private val DeepBlueTealDarkColorScheme = darkColorScheme(
    primary = DeepBlueTealPrimary,
    secondary = DeepBlueTealSecondary,
    tertiary = DeepBlueTealTertiary
)

private val DeepBlueTealLightColorScheme = lightColorScheme(
    primary = DeepBlueTealPrimary,
    secondary = DeepBlueTealSecondary,
    tertiary = DeepBlueTealTertiary
)

// 深蓝红灰方案
private val DeepBlueRedGreyDarkColorScheme = darkColorScheme(
    primary = DeepBlueRedPrimary,
    secondary = DeepBlueRedSecondary,
    tertiary = DeepBlueRedTertiary
)

private val DeepBlueRedGreyLightColorScheme = lightColorScheme(
    primary = DeepBlueRedPrimary,
    secondary = DeepBlueRedSecondary,
    tertiary = DeepBlueRedTertiary
)

// 粉紫蓝方案
private val PinkPurpleBlueDarkColorScheme = darkColorScheme(
    primary = PinkPurpleBluePrimary,
    secondary = PinkPurpleBlueSecondary,
    tertiary = PinkPurpleBlueTertiary
)

private val PinkPurpleBlueLightColorScheme = lightColorScheme(
    primary = PinkPurpleBluePrimary,
    secondary = PinkPurpleBlueSecondary,
    tertiary = PinkPurpleBlueTertiary
)

// 深色系方案
private val DarkSchemeDarkColorScheme = darkColorScheme(
    primary = DarkThemePrimary,
    secondary = DarkThemeSecondary,
    tertiary = DarkThemeTertiary
)

private val DarkSchemeLightColorScheme = lightColorScheme(
    primary = DarkThemePrimary,
    secondary = DarkThemeSecondary,
    tertiary = DarkThemeTertiary
)

// 灰蓝红方案
private val GrayBlueRedDarkColorScheme = darkColorScheme(
    primary = GrayBlueRedPrimary,
    secondary = GrayBlueRedSecondary,
    tertiary = GrayBlueRedTertiary
)

private val GrayBlueRedLightColorScheme = lightColorScheme(
    primary = GrayBlueRedPrimary,
    secondary = GrayBlueRedSecondary,
    tertiary = GrayBlueRedTertiary
)

// 自定义方案
private fun customDarkColorScheme(primary: Long, secondary: Long, tertiary: Long) = darkColorScheme(
    primary = Color(primary),
    secondary = Color(secondary),
    tertiary = Color(tertiary)
)

private fun customLightColorScheme(primary: Long, secondary: Long, tertiary: Long) = lightColorScheme(
    primary = Color(primary),
    secondary = Color(secondary),
    tertiary = Color(tertiary)
)

@Composable
fun MonicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    colorScheme: ColorScheme = ColorScheme.DEFAULT,
    customPrimaryColor: Long = 0xFF6650a4,
    customSecondaryColor: Long = 0xFF625b71,
    customTertiaryColor: Long = 0xFF7D5260,
    content: @Composable () -> Unit
) {
    val finalColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        
        colorScheme == ColorScheme.DEEP_BLUE_TEAL -> {
            if (darkTheme) DeepBlueTealDarkColorScheme else DeepBlueTealLightColorScheme
        }
        
        colorScheme == ColorScheme.DEEP_BLUE_RED -> {
            if (darkTheme) DeepBlueRedGreyDarkColorScheme else DeepBlueRedGreyLightColorScheme
        }
        
        colorScheme == ColorScheme.PINK_PURPLE_BLUE -> {
            if (darkTheme) PinkPurpleBlueDarkColorScheme else PinkPurpleBlueLightColorScheme
        }
        
        colorScheme == ColorScheme.DARK_THEME -> {
            if (darkTheme) DarkSchemeDarkColorScheme else DarkSchemeLightColorScheme
        }
        
        colorScheme == ColorScheme.GRAY_BLUE_RED -> {
            if (darkTheme) GrayBlueRedDarkColorScheme else GrayBlueRedLightColorScheme
        }
        
        colorScheme == ColorScheme.CUSTOM -> {
            if (darkTheme) customDarkColorScheme(customPrimaryColor, customSecondaryColor, customTertiaryColor) else customLightColorScheme(customPrimaryColor, customSecondaryColor, customTertiaryColor)
        }
        
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}