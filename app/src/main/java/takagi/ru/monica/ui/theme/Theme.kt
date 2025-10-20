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

// ============================================
// 📱 默认配色方案 (Material Design 3 默认紫色)
// ============================================
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// ============================================
// 🎨 海洋蓝 (Ocean Blue) - Material Design 3
// ============================================
private val OceanBlueLightColorScheme = lightColorScheme(
    primary = OceanBluePrimary,
    onPrimary = OceanBlueOnPrimary,
    primaryContainer = OceanBluePrimaryContainer,
    onPrimaryContainer = OceanBlueOnPrimaryContainer,
    
    secondary = OceanBlueSecondary,
    onSecondary = OceanBlueOnSecondary,
    secondaryContainer = OceanBlueSecondaryContainer,
    onSecondaryContainer = OceanBlueOnSecondaryContainer,
    
    tertiary = OceanBlueTertiary,
    onTertiary = OceanBlueOnTertiary,
    tertiaryContainer = OceanBlueTertiaryContainer,
    onTertiaryContainer = OceanBlueOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = OceanBlueSurface,
    onSurface = OceanBlueOnSurface,
    surfaceVariant = OceanBlueSurfaceVariant,
    onSurfaceVariant = OceanBlueOnSurfaceVariant,
    
    background = OceanBlueBackground,
    onBackground = OceanBlueOnBackground,
    
    outline = OceanBlueOutline,
    outlineVariant = OceanBlueOutlineVariant
)

private val OceanBlueDarkColorScheme = darkColorScheme(
    primary = OceanBluePrimaryDark,
    onPrimary = OceanBlueOnPrimaryDark,
    primaryContainer = OceanBluePrimaryContainerDark,
    onPrimaryContainer = OceanBlueOnPrimaryContainerDark,
    
    secondary = OceanBlueSecondaryDark,
    onSecondary = OceanBlueOnSecondaryDark,
    secondaryContainer = OceanBlueSecondaryContainerDark,
    onSecondaryContainer = OceanBlueOnSecondaryContainerDark,
    
    tertiary = OceanBlueTertiaryDark,
    onTertiary = OceanBlueOnTertiaryDark,
    tertiaryContainer = OceanBlueTertiaryContainerDark,
    onTertiaryContainer = OceanBlueOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = OceanBlueSurfaceDark,
    onSurface = OceanBlueOnSurfaceDark,
    surfaceVariant = OceanBlueSurfaceVariantDark,
    onSurfaceVariant = OceanBlueOnSurfaceVariantDark,
    
    background = OceanBlueBackgroundDark,
    onBackground = OceanBlueOnBackgroundDark,
    
    outline = OceanBlueOutlineDark,
    outlineVariant = OceanBlueOutlineVariantDark
)

// ============================================
// 🌅 日落橙 (Sunset Orange) - Material Design 3
// ============================================
private val SunsetOrangeLightColorScheme = lightColorScheme(
    primary = SunsetOrangePrimary,
    onPrimary = SunsetOrangeOnPrimary,
    primaryContainer = SunsetOrangePrimaryContainer,
    onPrimaryContainer = SunsetOrangeOnPrimaryContainer,
    
    secondary = SunsetOrangeSecondary,
    onSecondary = SunsetOrangeOnSecondary,
    secondaryContainer = SunsetOrangeSecondaryContainer,
    onSecondaryContainer = SunsetOrangeOnSecondaryContainer,
    
    tertiary = SunsetOrangeTertiary,
    onTertiary = SunsetOrangeOnTertiary,
    tertiaryContainer = SunsetOrangeTertiaryContainer,
    onTertiaryContainer = SunsetOrangeOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = SunsetOrangeSurface,
    onSurface = SunsetOrangeOnSurface,
    surfaceVariant = SunsetOrangeSurfaceVariant,
    onSurfaceVariant = SunsetOrangeOnSurfaceVariant,
    
    background = SunsetOrangeBackground,
    onBackground = SunsetOrangeOnBackground,
    
    outline = SunsetOrangeOutline,
    outlineVariant = SunsetOrangeOutlineVariant
)

private val SunsetOrangeDarkColorScheme = darkColorScheme(
    primary = SunsetOrangePrimaryDark,
    onPrimary = SunsetOrangeOnPrimaryDark,
    primaryContainer = SunsetOrangePrimaryContainerDark,
    onPrimaryContainer = SunsetOrangeOnPrimaryContainerDark,
    
    secondary = SunsetOrangeSecondaryDark,
    onSecondary = SunsetOrangeOnSecondaryDark,
    secondaryContainer = SunsetOrangeSecondaryContainerDark,
    onSecondaryContainer = SunsetOrangeOnSecondaryContainerDark,
    
    tertiary = SunsetOrangeTertiaryDark,
    onTertiary = SunsetOrangeOnTertiaryDark,
    tertiaryContainer = SunsetOrangeTertiaryContainerDark,
    onTertiaryContainer = SunsetOrangeOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = SunsetOrangeSurfaceDark,
    onSurface = SunsetOrangeOnSurfaceDark,
    surfaceVariant = SunsetOrangeSurfaceVariantDark,
    onSurfaceVariant = SunsetOrangeOnSurfaceVariantDark,
    
    background = SunsetOrangeBackgroundDark,
    onBackground = SunsetOrangeOnBackgroundDark,
    
    outline = SunsetOrangeOutlineDark,
    outlineVariant = SunsetOrangeOutlineVariantDark
)

// ============================================
// 🌲 森林绿 (Forest Green) - Material Design 3
// ============================================
private val ForestGreenLightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = ForestGreenOnPrimary,
    primaryContainer = ForestGreenPrimaryContainer,
    onPrimaryContainer = ForestGreenOnPrimaryContainer,
    
    secondary = ForestGreenSecondary,
    onSecondary = ForestGreenOnSecondary,
    secondaryContainer = ForestGreenSecondaryContainer,
    onSecondaryContainer = ForestGreenOnSecondaryContainer,
    
    tertiary = ForestGreenTertiary,
    onTertiary = ForestGreenOnTertiary,
    tertiaryContainer = ForestGreenTertiaryContainer,
    onTertiaryContainer = ForestGreenOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = ForestGreenSurface,
    onSurface = ForestGreenOnSurface,
    surfaceVariant = ForestGreenSurfaceVariant,
    onSurfaceVariant = ForestGreenOnSurfaceVariant,
    
    background = ForestGreenBackground,
    onBackground = ForestGreenOnBackground,
    
    outline = ForestGreenOutline,
    outlineVariant = ForestGreenOutlineVariant
)

private val ForestGreenDarkColorScheme = darkColorScheme(
    primary = ForestGreenPrimaryDark,
    onPrimary = ForestGreenOnPrimaryDark,
    primaryContainer = ForestGreenPrimaryContainerDark,
    onPrimaryContainer = ForestGreenOnPrimaryContainerDark,
    
    secondary = ForestGreenSecondaryDark,
    onSecondary = ForestGreenOnSecondaryDark,
    secondaryContainer = ForestGreenSecondaryContainerDark,
    onSecondaryContainer = ForestGreenOnSecondaryContainerDark,
    
    tertiary = ForestGreenTertiaryDark,
    onTertiary = ForestGreenOnTertiaryDark,
    tertiaryContainer = ForestGreenTertiaryContainerDark,
    onTertiaryContainer = ForestGreenOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = ForestGreenSurfaceDark,
    onSurface = ForestGreenOnSurfaceDark,
    surfaceVariant = ForestGreenSurfaceVariantDark,
    onSurfaceVariant = ForestGreenOnSurfaceVariantDark,
    
    background = ForestGreenBackgroundDark,
    onBackground = ForestGreenOnBackgroundDark,
    
    outline = ForestGreenOutlineDark,
    outlineVariant = ForestGreenOutlineVariantDark
)

// ============================================
// 💜 科技紫 (Tech Purple) - Material Design 3
// ============================================
private val TechPurpleLightColorScheme = lightColorScheme(
    primary = TechPurplePrimary,
    onPrimary = TechPurpleOnPrimary,
    primaryContainer = TechPurplePrimaryContainer,
    onPrimaryContainer = TechPurpleOnPrimaryContainer,
    
    secondary = TechPurpleSecondary,
    onSecondary = TechPurpleOnSecondary,
    secondaryContainer = TechPurpleSecondaryContainer,
    onSecondaryContainer = TechPurpleOnSecondaryContainer,
    
    tertiary = TechPurpleTertiary,
    onTertiary = TechPurpleOnTertiary,
    tertiaryContainer = TechPurpleTertiaryContainer,
    onTertiaryContainer = TechPurpleOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = TechPurpleSurface,
    onSurface = TechPurpleOnSurface,
    surfaceVariant = TechPurpleSurfaceVariant,
    onSurfaceVariant = TechPurpleOnSurfaceVariant,
    
    background = TechPurpleBackground,
    onBackground = TechPurpleOnBackground,
    
    outline = TechPurpleOutline,
    outlineVariant = TechPurpleOutlineVariant
)

private val TechPurpleDarkColorScheme = darkColorScheme(
    primary = TechPurplePrimaryDark,
    onPrimary = TechPurpleOnPrimaryDark,
    primaryContainer = TechPurplePrimaryContainerDark,
    onPrimaryContainer = TechPurpleOnPrimaryContainerDark,
    
    secondary = TechPurpleSecondaryDark,
    onSecondary = TechPurpleOnSecondaryDark,
    secondaryContainer = TechPurpleSecondaryContainerDark,
    onSecondaryContainer = TechPurpleOnSecondaryContainerDark,
    
    tertiary = TechPurpleTertiaryDark,
    onTertiary = TechPurpleOnTertiaryDark,
    tertiaryContainer = TechPurpleTertiaryContainerDark,
    onTertiaryContainer = TechPurpleOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = TechPurpleSurfaceDark,
    onSurface = TechPurpleOnSurfaceDark,
    surfaceVariant = TechPurpleSurfaceVariantDark,
    onSurfaceVariant = TechPurpleOnSurfaceVariantDark,
    
    background = TechPurpleBackgroundDark,
    onBackground = TechPurpleOnBackgroundDark,
    
    outline = TechPurpleOutlineDark,
    outlineVariant = TechPurpleOutlineVariantDark
)

// ============================================
// 🐍 黑曼巴 (Black Mamba - Kobe Lakers) - Material Design 3
// ============================================
private val BlackMambaLightColorScheme = lightColorScheme(
    primary = BlackMambaPrimary,
    onPrimary = BlackMambaOnPrimary,
    primaryContainer = BlackMambaPrimaryContainer,
    onPrimaryContainer = BlackMambaOnPrimaryContainer,
    
    secondary = BlackMambaSecondary,
    onSecondary = BlackMambaOnSecondary,
    secondaryContainer = BlackMambaSecondaryContainer,
    onSecondaryContainer = BlackMambaOnSecondaryContainer,
    
    tertiary = BlackMambaTertiary,
    onTertiary = BlackMambaOnTertiary,
    tertiaryContainer = BlackMambaTertiaryContainer,
    onTertiaryContainer = BlackMambaOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = BlackMambaSurface,
    onSurface = BlackMambaOnSurface,
    surfaceVariant = BlackMambaSurfaceVariant,
    onSurfaceVariant = BlackMambaOnSurfaceVariant,
    
    background = BlackMambaBackground,
    onBackground = BlackMambaOnBackground,
    
    outline = BlackMambaOutline,
    outlineVariant = BlackMambaOutlineVariant
)

private val BlackMambaDarkColorScheme = darkColorScheme(
    primary = BlackMambaPrimaryDark,
    onPrimary = BlackMambaOnPrimaryDark,
    primaryContainer = BlackMambaPrimaryContainerDark,
    onPrimaryContainer = BlackMambaOnPrimaryContainerDark,
    
    secondary = BlackMambaSecondaryDark,
    onSecondary = BlackMambaOnSecondaryDark,
    secondaryContainer = BlackMambaSecondaryContainerDark,
    onSecondaryContainer = BlackMambaOnSecondaryContainerDark,
    
    tertiary = BlackMambaTertiaryDark,
    onTertiary = BlackMambaOnTertiaryDark,
    tertiaryContainer = BlackMambaTertiaryContainerDark,
    onTertiaryContainer = BlackMambaOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = BlackMambaSurfaceDark,
    onSurface = BlackMambaOnSurfaceDark,
    surfaceVariant = BlackMambaSurfaceVariantDark,
    onSurfaceVariant = BlackMambaOnSurfaceVariantDark,
    
    background = BlackMambaBackgroundDark,
    onBackground = BlackMambaOnBackgroundDark,
    
    outline = BlackMambaOutlineDark,
    outlineVariant = BlackMambaOutlineVariantDark
)

// ============================================
// 🕴️ 小黑紫 (Grey Style - Cai Xukun) - Material Design 3
// ============================================
private val GreyStyleLightColorScheme = lightColorScheme(
    primary = GreyStylePrimary,
    onPrimary = GreyStyleOnPrimary,
    primaryContainer = GreyStylePrimaryContainer,
    onPrimaryContainer = GreyStyleOnPrimaryContainer,
    
    secondary = GreyStyleSecondary,
    onSecondary = GreyStyleOnSecondary,
    secondaryContainer = GreyStyleSecondaryContainer,
    onSecondaryContainer = GreyStyleOnSecondaryContainer,
    
    tertiary = GreyStyleTertiary,
    onTertiary = GreyStyleOnTertiary,
    tertiaryContainer = GreyStyleTertiaryContainer,
    onTertiaryContainer = GreyStyleOnTertiaryContainer,
    
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    
    surface = GreyStyleSurface,
    onSurface = GreyStyleOnSurface,
    surfaceVariant = GreyStyleSurfaceVariant,
    onSurfaceVariant = GreyStyleOnSurfaceVariant,
    
    background = GreyStyleBackground,
    onBackground = GreyStyleOnBackground,
    
    outline = GreyStyleOutline,
    outlineVariant = GreyStyleOutlineVariant
)

private val GreyStyleDarkColorScheme = darkColorScheme(
    primary = GreyStylePrimaryDark,
    onPrimary = GreyStyleOnPrimaryDark,
    primaryContainer = GreyStylePrimaryContainerDark,
    onPrimaryContainer = GreyStyleOnPrimaryContainerDark,
    
    secondary = GreyStyleSecondaryDark,
    onSecondary = GreyStyleOnSecondaryDark,
    secondaryContainer = GreyStyleSecondaryContainerDark,
    onSecondaryContainer = GreyStyleOnSecondaryContainerDark,
    
    tertiary = GreyStyleTertiaryDark,
    onTertiary = GreyStyleOnTertiaryDark,
    tertiaryContainer = GreyStyleTertiaryContainerDark,
    onTertiaryContainer = GreyStyleOnTertiaryContainerDark,
    
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    
    surface = GreyStyleSurfaceDark,
    onSurface = GreyStyleOnSurfaceDark,
    surfaceVariant = GreyStyleSurfaceVariantDark,
    onSurfaceVariant = GreyStyleOnSurfaceVariantDark,
    
    background = GreyStyleBackgroundDark,
    onBackground = GreyStyleOnBackgroundDark,
    
    outline = GreyStyleOutlineDark,
    outlineVariant = GreyStyleOutlineVariantDark
)

// ============================================
// 🎨 自定义方案
// ============================================
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
        
        colorScheme == ColorScheme.OCEAN_BLUE -> {
            if (darkTheme) OceanBlueDarkColorScheme else OceanBlueLightColorScheme
        }
        
        colorScheme == ColorScheme.SUNSET_ORANGE -> {
            if (darkTheme) SunsetOrangeDarkColorScheme else SunsetOrangeLightColorScheme
        }
        
        colorScheme == ColorScheme.FOREST_GREEN -> {
            if (darkTheme) ForestGreenDarkColorScheme else ForestGreenLightColorScheme
        }
        
        colorScheme == ColorScheme.TECH_PURPLE -> {
            if (darkTheme) TechPurpleDarkColorScheme else TechPurpleLightColorScheme
        }
        
        colorScheme == ColorScheme.BLACK_MAMBA -> {
            if (darkTheme) BlackMambaDarkColorScheme else BlackMambaLightColorScheme
        }
        
        colorScheme == ColorScheme.GREY_STYLE -> {
            if (darkTheme) GreyStyleDarkColorScheme else GreyStyleLightColorScheme
        }
        
        colorScheme == ColorScheme.CUSTOM -> {
            if (darkTheme) {
                customDarkColorScheme(customPrimaryColor, customSecondaryColor, customTertiaryColor)
            } else {
                customLightColorScheme(customPrimaryColor, customSecondaryColor, customTertiaryColor)
            }
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