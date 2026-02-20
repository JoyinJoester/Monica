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
// 🎨 莫奈印象派系列 (Monet Impressionist Collection)
// ============================================

// 1. 《睡莲·绿色和谐》（Water Lilies）
private val WaterLiliesLightColorScheme = lightColorScheme(
    primary = WaterLiliesPrimary,
    onPrimary = WaterLiliesOnPrimary,
    primaryContainer = WaterLiliesPrimaryContainer,
    onPrimaryContainer = WaterLiliesOnPrimaryContainer,
    secondary = WaterLiliesSecondary,
    onSecondary = WaterLiliesOnSecondary,
    secondaryContainer = WaterLiliesSecondaryContainer,
    onSecondaryContainer = WaterLiliesOnSecondaryContainer,
    tertiary = WaterLiliesTertiary,
    onTertiary = WaterLiliesOnTertiary,
    tertiaryContainer = WaterLiliesTertiaryContainer,
    onTertiaryContainer = WaterLiliesOnTertiaryContainer,
    background = WaterLiliesBackground,
    onBackground = WaterLiliesOnBackground,
    surface = WaterLiliesSurface,
    onSurface = WaterLiliesOnSurface,
)

private val WaterLiliesDarkColorScheme = darkColorScheme(
    primary = WaterLiliesPrimaryDark,
    onPrimary = WaterLiliesOnPrimaryDark,
    primaryContainer = WaterLiliesPrimaryContainerDark,
    onPrimaryContainer = WaterLiliesOnPrimaryContainerDark,
    secondary = WaterLiliesSecondaryDark,
    onSecondary = WaterLiliesOnSecondaryDark,
    secondaryContainer = WaterLiliesSecondaryContainerDark,
    onSecondaryContainer = WaterLiliesOnSecondaryContainerDark,
    tertiary = WaterLiliesTertiaryDark,
    onTertiary = WaterLiliesOnTertiaryDark,
    tertiaryContainer = WaterLiliesTertiaryContainerDark,
    onTertiaryContainer = WaterLiliesOnTertiaryContainerDark,
    background = WaterLiliesBackgroundDark,
    onBackground = WaterLiliesOnBackgroundDark,
    surface = WaterLiliesSurfaceDark,
    onSurface = WaterLiliesOnSurfaceDark,
)

// 2. 《印象·日出》（Impression, Sunrise）
private val ImpressionSunriseLightColorScheme = lightColorScheme(
    primary = ImpressionSunrisePrimary,
    onPrimary = ImpressionSunriseOnPrimary,
    primaryContainer = ImpressionSunrisePrimaryContainer,
    onPrimaryContainer = ImpressionSunriseOnPrimaryContainer,
    secondary = ImpressionSunriseSecondary,
    onSecondary = ImpressionSunriseOnSecondary,
    secondaryContainer = ImpressionSunriseSecondaryContainer,
    onSecondaryContainer = ImpressionSunriseOnSecondaryContainer,
    tertiary = ImpressionSunriseTertiary,
    onTertiary = ImpressionSunriseOnTertiary,
    tertiaryContainer = ImpressionSunriseTertiaryContainer,
    onTertiaryContainer = ImpressionSunriseOnTertiaryContainer,
    background = ImpressionSunriseBackground,
    onBackground = ImpressionSunriseOnBackground,
    surface = ImpressionSunriseSurface,
    onSurface = ImpressionSunriseOnSurface,
)

private val ImpressionSunriseDarkColorScheme = darkColorScheme(
    primary = ImpressionSunrisePrimaryDark,
    onPrimary = ImpressionSunriseOnPrimaryDark,
    primaryContainer = ImpressionSunrisePrimaryContainerDark,
    onPrimaryContainer = ImpressionSunriseOnPrimaryContainerDark,
    secondary = ImpressionSunriseSecondaryDark,
    onSecondary = ImpressionSunriseOnSecondaryDark,
    secondaryContainer = ImpressionSunriseSecondaryContainerDark,
    onSecondaryContainer = ImpressionSunriseOnSecondaryContainerDark,
    tertiary = ImpressionSunriseTertiaryDark,
    onTertiary = ImpressionSunriseOnTertiaryDark,
    tertiaryContainer = ImpressionSunriseTertiaryContainerDark,
    onTertiaryContainer = ImpressionSunriseOnTertiaryContainerDark,
    background = ImpressionSunriseBackgroundDark,
    onBackground = ImpressionSunriseOnBackgroundDark,
    surface = ImpressionSunriseSurfaceDark,
    onSurface = ImpressionSunriseOnSurfaceDark,
)

// 3. 《日本桥》（The Bridge at Argenteuil）
private val JapaneseBridgeLightColorScheme = lightColorScheme(
    primary = JapaneseBridgePrimary,
    onPrimary = JapaneseBridgeOnPrimary,
    primaryContainer = JapaneseBridgePrimaryContainer,
    onPrimaryContainer = JapaneseBridgeOnPrimaryContainer,
    secondary = JapaneseBridgeSecondary,
    onSecondary = JapaneseBridgeOnSecondary,
    secondaryContainer = JapaneseBridgeSecondaryContainer,
    onSecondaryContainer = JapaneseBridgeOnSecondaryContainer,
    tertiary = JapaneseBridgeTertiary,
    onTertiary = JapaneseBridgeOnTertiary,
    tertiaryContainer = JapaneseBridgeTertiaryContainer,
    onTertiaryContainer = JapaneseBridgeOnTertiaryContainer,
    background = JapaneseBridgeBackground,
    onBackground = JapaneseBridgeOnBackground,
    surface = JapaneseBridgeSurface,
    onSurface = JapaneseBridgeOnSurface,
)

private val JapaneseBridgeDarkColorScheme = darkColorScheme(
    primary = JapaneseBridgePrimaryDark,
    onPrimary = JapaneseBridgeOnPrimaryDark,
    primaryContainer = JapaneseBridgePrimaryContainerDark,
    onPrimaryContainer = JapaneseBridgeOnPrimaryContainerDark,
    secondary = JapaneseBridgeSecondaryDark,
    onSecondary = JapaneseBridgeOnSecondaryDark,
    secondaryContainer = JapaneseBridgeSecondaryContainerDark,
    onSecondaryContainer = JapaneseBridgeOnSecondaryContainerDark,
    tertiary = JapaneseBridgeTertiaryDark,
    onTertiary = JapaneseBridgeOnTertiaryDark,
    tertiaryContainer = JapaneseBridgeTertiaryContainerDark,
    onTertiaryContainer = JapaneseBridgeOnTertiaryContainerDark,
    background = JapaneseBridgeBackgroundDark,
    onBackground = JapaneseBridgeOnBackgroundDark,
    surface = JapaneseBridgeSurfaceDark,
    onSurface = JapaneseBridgeOnSurfaceDark,
)

// 4. 《干草堆·落日》（Haystacks, Sunset）
private val HaystacksLightColorScheme = lightColorScheme(
    primary = HaystacksPrimary,
    onPrimary = HaystacksOnPrimary,
    primaryContainer = HaystacksPrimaryContainer,
    onPrimaryContainer = HaystacksOnPrimaryContainer,
    secondary = HaystacksSecondary,
    onSecondary = HaystacksOnSecondary,
    secondaryContainer = HaystacksSecondaryContainer,
    onSecondaryContainer = HaystacksOnSecondaryContainer,
    tertiary = HaystacksTertiary,
    onTertiary = HaystacksOnTertiary,
    tertiaryContainer = HaystacksTertiaryContainer,
    onTertiaryContainer = HaystacksOnTertiaryContainer,
    background = HaystacksBackground,
    onBackground = HaystacksOnBackground,
    surface = HaystacksSurface,
    onSurface = HaystacksOnSurface,
)

private val HaystacksDarkColorScheme = darkColorScheme(
    primary = HaystacksPrimaryDark,
    onPrimary = HaystacksOnPrimaryDark,
    primaryContainer = HaystacksPrimaryContainerDark,
    onPrimaryContainer = HaystacksOnPrimaryContainerDark,
    secondary = HaystacksSecondaryDark,
    onSecondary = HaystacksOnSecondaryDark,
    secondaryContainer = HaystacksSecondaryContainerDark,
    onSecondaryContainer = HaystacksOnSecondaryContainerDark,
    tertiary = HaystacksTertiaryDark,
    onTertiary = HaystacksOnTertiaryDark,
    tertiaryContainer = HaystacksTertiaryContainerDark,
    onTertiaryContainer = HaystacksOnTertiaryContainerDark,
    background = HaystacksBackgroundDark,
    onBackground = HaystacksOnBackgroundDark,
    surface = HaystacksSurfaceDark,
    onSurface = HaystacksOnSurfaceDark,
)

// 5. 《鲁昂大教堂·正午》（Rouen Cathedral）
private val RouenCathedralLightColorScheme = lightColorScheme(
    primary = RouenCathedralPrimary,
    onPrimary = RouenCathedralOnPrimary,
    primaryContainer = RouenCathedralPrimaryContainer,
    onPrimaryContainer = RouenCathedralOnPrimaryContainer,
    secondary = RouenCathedralSecondary,
    onSecondary = RouenCathedralOnSecondary,
    secondaryContainer = RouenCathedralSecondaryContainer,
    onSecondaryContainer = RouenCathedralOnSecondaryContainer,
    tertiary = RouenCathedralTertiary,
    onTertiary = RouenCathedralOnTertiary,
    tertiaryContainer = RouenCathedralTertiaryContainer,
    onTertiaryContainer = RouenCathedralOnTertiaryContainer,
    background = RouenCathedralBackground,
    onBackground = RouenCathedralOnBackground,
    surface = RouenCathedralSurface,
    onSurface = RouenCathedralOnSurface,
)

private val RouenCathedralDarkColorScheme = darkColorScheme(
    primary = RouenCathedralPrimaryDark,
    onPrimary = RouenCathedralOnPrimaryDark,
    primaryContainer = RouenCathedralPrimaryContainerDark,
    onPrimaryContainer = RouenCathedralOnPrimaryContainerDark,
    secondary = RouenCathedralSecondaryDark,
    onSecondary = RouenCathedralOnSecondaryDark,
    secondaryContainer = RouenCathedralSecondaryContainerDark,
    onSecondaryContainer = RouenCathedralOnSecondaryContainerDark,
    tertiary = RouenCathedralTertiaryDark,
    onTertiary = RouenCathedralOnTertiaryDark,
    tertiaryContainer = RouenCathedralTertiaryContainerDark,
    onTertiaryContainer = RouenCathedralOnTertiaryContainerDark,
    background = RouenCathedralBackgroundDark,
    onBackground = RouenCathedralOnBackgroundDark,
    surface = RouenCathedralSurfaceDark,
    onSurface = RouenCathedralOnSurfaceDark,
)

// 6. 《国会大厦·雾霾》（Houses of Parliament）
private val ParliamentFogLightColorScheme = lightColorScheme(
    primary = ParliamentFogPrimary,
    onPrimary = ParliamentFogOnPrimary,
    primaryContainer = ParliamentFogPrimaryContainer,
    onPrimaryContainer = ParliamentFogOnPrimaryContainer,
    secondary = ParliamentFogSecondary,
    onSecondary = ParliamentFogOnSecondary,
    secondaryContainer = ParliamentFogSecondaryContainer,
    onSecondaryContainer = ParliamentFogOnSecondaryContainer,
    tertiary = ParliamentFogTertiary,
    onTertiary = ParliamentFogOnTertiary,
    tertiaryContainer = ParliamentFogTertiaryContainer,
    onTertiaryContainer = ParliamentFogOnTertiaryContainer,
    background = ParliamentFogBackground,
    onBackground = ParliamentFogOnBackground,
    surface = ParliamentFogSurface,
    onSurface = ParliamentFogOnSurface,
)

private val ParliamentFogDarkColorScheme = darkColorScheme(
    primary = ParliamentFogPrimaryDark,
    onPrimary = ParliamentFogOnPrimaryDark,
    primaryContainer = ParliamentFogPrimaryContainerDark,
    onPrimaryContainer = ParliamentFogOnPrimaryContainerDark,
    secondary = ParliamentFogSecondaryDark,
    onSecondary = ParliamentFogOnSecondaryDark,
    secondaryContainer = ParliamentFogSecondaryContainerDark,
    onSecondaryContainer = ParliamentFogOnSecondaryContainerDark,
    tertiary = ParliamentFogTertiaryDark,
    onTertiary = ParliamentFogOnTertiaryDark,
    tertiaryContainer = ParliamentFogTertiaryContainerDark,
    onTertiaryContainer = ParliamentFogOnTertiaryContainerDark,
    background = ParliamentFogBackgroundDark,
    onBackground = ParliamentFogOnBackgroundDark,
    surface = ParliamentFogSurfaceDark,
    onSurface = ParliamentFogOnSurfaceDark,
)

// ============================================
// 🐱 Catppuccin (Plus)
// ============================================
private val CatLatteLightColorScheme = lightColorScheme(
    primary = CatLattePrimary,
    onPrimary = CatLatteOnPrimary,
    primaryContainer = CatLattePrimaryContainer,
    onPrimaryContainer = CatLatteOnPrimaryContainer,
    secondary = CatLatteSecondary,
    onSecondary = CatLatteOnSecondary,
    secondaryContainer = CatLatteSecondaryContainer,
    onSecondaryContainer = CatLatteOnSecondaryContainer,
    tertiary = CatLatteTertiary,
    onTertiary = CatLatteOnTertiary,
    tertiaryContainer = CatLatteTertiaryContainer,
    onTertiaryContainer = CatLatteOnTertiaryContainer,
    background = CatLatteBackground,
    onBackground = CatLatteOnBackground,
    surface = CatLatteSurface,
    onSurface = CatLatteOnSurface,
    surfaceVariant = CatLatteSurfaceVariant,
    onSurfaceVariant = CatLatteOnSurfaceVariant,
    outline = CatLatteOutline,
    outlineVariant = CatLatteOutlineVariant,
)

private val CatLatteDarkColorScheme = darkColorScheme(
    primary = CatLattePrimaryDark,
    onPrimary = CatLatteOnPrimaryDark,
    primaryContainer = CatLattePrimaryContainerDark,
    onPrimaryContainer = CatLatteOnPrimaryContainerDark,
    secondary = CatLatteSecondaryDark,
    onSecondary = CatLatteOnSecondaryDark,
    secondaryContainer = CatLatteSecondaryContainerDark,
    onSecondaryContainer = CatLatteOnSecondaryContainerDark,
    tertiary = CatLatteTertiaryDark,
    onTertiary = CatLatteOnTertiaryDark,
    tertiaryContainer = CatLatteTertiaryContainerDark,
    onTertiaryContainer = CatLatteOnTertiaryContainerDark,
    background = CatLatteBackgroundDark,
    onBackground = CatLatteOnBackgroundDark,
    surface = CatLatteSurfaceDark,
    onSurface = CatLatteOnSurfaceDark,
    surfaceVariant = CatLatteSurfaceVariantDark,
    onSurfaceVariant = CatLatteOnSurfaceVariantDark,
    outline = CatLatteOutlineDark,
    outlineVariant = CatLatteOutlineVariantDark,
)

private val CatFrappeLightColorScheme = lightColorScheme(
    primary = CatFrappePrimaryLight,
    onPrimary = CatFrappeOnPrimaryLight,
    primaryContainer = CatFrappePrimaryContainerLight,
    onPrimaryContainer = CatFrappeOnPrimaryContainerLight,
    secondary = CatFrappeSecondaryLight,
    onSecondary = CatFrappeOnSecondaryLight,
    secondaryContainer = CatFrappeSecondaryContainerLight,
    onSecondaryContainer = CatFrappeOnSecondaryContainerLight,
    tertiary = CatFrappeTertiaryLight,
    onTertiary = CatFrappeOnTertiaryLight,
    tertiaryContainer = CatFrappeTertiaryContainerLight,
    onTertiaryContainer = CatFrappeOnTertiaryContainerLight,
    background = CatFrappeBackgroundLight,
    onBackground = CatFrappeOnBackgroundLight,
    surface = CatFrappeSurfaceLight,
    onSurface = CatFrappeOnSurfaceLight,
    surfaceVariant = CatFrappeSurfaceVariantLight,
    onSurfaceVariant = CatFrappeOnSurfaceVariantLight,
    outline = CatFrappeOutlineLight,
    outlineVariant = CatFrappeOutlineVariantLight,
)

private val CatFrappeDarkColorScheme = darkColorScheme(
    primary = CatFrappePrimary,
    onPrimary = CatFrappeOnPrimary,
    primaryContainer = CatFrappePrimaryContainer,
    onPrimaryContainer = CatFrappeOnPrimaryContainer,
    secondary = CatFrappeSecondary,
    onSecondary = CatFrappeOnSecondary,
    secondaryContainer = CatFrappeSecondaryContainer,
    onSecondaryContainer = CatFrappeOnSecondaryContainer,
    tertiary = CatFrappeTertiary,
    onTertiary = CatFrappeOnTertiary,
    tertiaryContainer = CatFrappeTertiaryContainer,
    onTertiaryContainer = CatFrappeOnTertiaryContainer,
    background = CatFrappeBackground,
    onBackground = CatFrappeOnBackground,
    surface = CatFrappeSurface,
    onSurface = CatFrappeOnSurface,
    surfaceVariant = CatFrappeSurfaceVariant,
    onSurfaceVariant = CatFrappeOnSurfaceVariant,
    outline = CatFrappeOutline,
    outlineVariant = CatFrappeOutlineVariant,
)

private val CatMacchiatoLightColorScheme = lightColorScheme(
    primary = CatMacchiatoPrimaryLight,
    onPrimary = CatMacchiatoOnPrimaryLight,
    primaryContainer = CatMacchiatoPrimaryContainerLight,
    onPrimaryContainer = CatMacchiatoOnPrimaryContainerLight,
    secondary = CatMacchiatoSecondaryLight,
    onSecondary = CatMacchiatoOnSecondaryLight,
    secondaryContainer = CatMacchiatoSecondaryContainerLight,
    onSecondaryContainer = CatMacchiatoOnSecondaryContainerLight,
    tertiary = CatMacchiatoTertiaryLight,
    onTertiary = CatMacchiatoOnTertiaryLight,
    tertiaryContainer = CatMacchiatoTertiaryContainerLight,
    onTertiaryContainer = CatMacchiatoOnTertiaryContainerLight,
    background = CatMacchiatoBackgroundLight,
    onBackground = CatMacchiatoOnBackgroundLight,
    surface = CatMacchiatoSurfaceLight,
    onSurface = CatMacchiatoOnSurfaceLight,
    surfaceVariant = CatMacchiatoSurfaceVariantLight,
    onSurfaceVariant = CatMacchiatoOnSurfaceVariantLight,
    outline = CatMacchiatoOutlineLight,
    outlineVariant = CatMacchiatoOutlineVariantLight,
)

private val CatMacchiatoDarkColorScheme = darkColorScheme(
    primary = CatMacchiatoPrimary,
    onPrimary = CatMacchiatoOnPrimary,
    primaryContainer = CatMacchiatoPrimaryContainer,
    onPrimaryContainer = CatMacchiatoOnPrimaryContainer,
    secondary = CatMacchiatoSecondary,
    onSecondary = CatMacchiatoOnSecondary,
    secondaryContainer = CatMacchiatoSecondaryContainer,
    onSecondaryContainer = CatMacchiatoOnSecondaryContainer,
    tertiary = CatMacchiatoTertiary,
    onTertiary = CatMacchiatoOnTertiary,
    tertiaryContainer = CatMacchiatoTertiaryContainer,
    onTertiaryContainer = CatMacchiatoOnTertiaryContainer,
    background = CatMacchiatoBackground,
    onBackground = CatMacchiatoOnBackground,
    surface = CatMacchiatoSurface,
    onSurface = CatMacchiatoOnSurface,
    surfaceVariant = CatMacchiatoSurfaceVariant,
    onSurfaceVariant = CatMacchiatoOnSurfaceVariant,
    outline = CatMacchiatoOutline,
    outlineVariant = CatMacchiatoOutlineVariant,
)

private val CatMochaLightColorScheme = lightColorScheme(
    primary = CatMochaPrimaryLight,
    onPrimary = CatMochaOnPrimaryLight,
    primaryContainer = CatMochaPrimaryContainerLight,
    onPrimaryContainer = CatMochaOnPrimaryContainerLight,
    secondary = CatMochaSecondaryLight,
    onSecondary = CatMochaOnSecondaryLight,
    secondaryContainer = CatMochaSecondaryContainerLight,
    onSecondaryContainer = CatMochaOnSecondaryContainerLight,
    tertiary = CatMochaTertiaryLight,
    onTertiary = CatMochaOnTertiaryLight,
    tertiaryContainer = CatMochaTertiaryContainerLight,
    onTertiaryContainer = CatMochaOnTertiaryContainerLight,
    background = CatMochaBackgroundLight,
    onBackground = CatMochaOnBackgroundLight,
    surface = CatMochaSurfaceLight,
    onSurface = CatMochaOnSurfaceLight,
    surfaceVariant = CatMochaSurfaceVariantLight,
    onSurfaceVariant = CatMochaOnSurfaceVariantLight,
    outline = CatMochaOutlineLight,
    outlineVariant = CatMochaOutlineVariantLight,
)

private val CatMochaDarkColorScheme = darkColorScheme(
    primary = CatMochaPrimary,
    onPrimary = CatMochaOnPrimary,
    primaryContainer = CatMochaPrimaryContainer,
    onPrimaryContainer = CatMochaOnPrimaryContainer,
    secondary = CatMochaSecondary,
    onSecondary = CatMochaOnSecondary,
    secondaryContainer = CatMochaSecondaryContainer,
    onSecondaryContainer = CatMochaOnSecondaryContainer,
    tertiary = CatMochaTertiary,
    onTertiary = CatMochaOnTertiary,
    tertiaryContainer = CatMochaTertiaryContainer,
    onTertiaryContainer = CatMochaOnTertiaryContainer,
    background = CatMochaBackground,
    onBackground = CatMochaOnBackground,
    surface = CatMochaSurface,
    onSurface = CatMochaOnSurface,
    surfaceVariant = CatMochaSurfaceVariant,
    onSurfaceVariant = CatMochaOnSurfaceVariant,
    outline = CatMochaOutline,
    outlineVariant = CatMochaOutlineVariant,
)

// ============================================
// 🎨 自定义方案
// ============================================
private fun customDarkColorScheme(primary: Long, secondary: Long, tertiary: Long) =
    generateCustomMaterialColorScheme(
        darkTheme = true,
        primarySeed = primary,
        secondarySeed = secondary,
        tertiarySeed = tertiary
    )

private fun customLightColorScheme(primary: Long, secondary: Long, tertiary: Long) =
    generateCustomMaterialColorScheme(
        darkTheme = false,
        primarySeed = primary,
        secondarySeed = secondary,
        tertiarySeed = tertiary
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
        colorScheme == ColorScheme.DEFAULT && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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

        colorScheme == ColorScheme.WATER_LILIES -> {
            if (darkTheme) WaterLiliesDarkColorScheme else WaterLiliesLightColorScheme
        }

        colorScheme == ColorScheme.IMPRESSION_SUNRISE -> {
            if (darkTheme) ImpressionSunriseDarkColorScheme else ImpressionSunriseLightColorScheme
        }

        colorScheme == ColorScheme.JAPANESE_BRIDGE -> {
            if (darkTheme) JapaneseBridgeDarkColorScheme else JapaneseBridgeLightColorScheme
        }

        colorScheme == ColorScheme.HAYSTACKS -> {
            if (darkTheme) HaystacksDarkColorScheme else HaystacksLightColorScheme
        }

        colorScheme == ColorScheme.ROUEN_CATHEDRAL -> {
            if (darkTheme) RouenCathedralDarkColorScheme else RouenCathedralLightColorScheme
        }

        colorScheme == ColorScheme.PARLIAMENT_FOG -> {
            if (darkTheme) ParliamentFogDarkColorScheme else ParliamentFogLightColorScheme
        }

        colorScheme == ColorScheme.CATPPUCCIN_LATTE -> {
            if (darkTheme) CatLatteDarkColorScheme else CatLatteLightColorScheme
        }

        colorScheme == ColorScheme.CATPPUCCIN_FRAPPE -> {
            if (darkTheme) CatFrappeDarkColorScheme else CatFrappeLightColorScheme
        }

        colorScheme == ColorScheme.CATPPUCCIN_MACCHIATO -> {
            if (darkTheme) CatMacchiatoDarkColorScheme else CatMacchiatoLightColorScheme
        }

        colorScheme == ColorScheme.CATPPUCCIN_MOCHA -> {
            if (darkTheme) CatMochaDarkColorScheme else CatMochaLightColorScheme
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
