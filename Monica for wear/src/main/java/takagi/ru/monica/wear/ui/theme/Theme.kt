package takagi.ru.monica.wear.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme as WearColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import takagi.ru.monica.wear.viewmodel.ColorScheme

/**
 * Wear OS 主题
 * 使用标准 Compose Material 3 颜色方案
 */
@Composable
fun MonicaWearTheme(
    colorScheme: ColorScheme,
    useOledBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val backgroundColor = getBackgroundColor(useOledBlack)
    
    val colors = when (colorScheme) {
        ColorScheme.OCEAN_BLUE -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = OceanBlueSurfaceDark,
            onSurface = OceanBlueOnSurfaceDark,
            surfaceVariant = OceanBlueSurfaceVariantDark,
            onSurfaceVariant = OceanBlueOnSurfaceVariantDark,
            outline = OceanBlueOutlineDark,
            outlineVariant = OceanBlueOutlineVariantDark
        )
        
        ColorScheme.SUNSET_ORANGE -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = SunsetOrangeSurfaceDark,
            onSurface = SunsetOrangeOnSurfaceDark,
            surfaceVariant = SunsetOrangeSurfaceVariantDark,
            onSurfaceVariant = SunsetOrangeOnSurfaceVariantDark,
            outline = SunsetOrangeOutlineDark,
            outlineVariant = SunsetOrangeOutlineVariantDark
        )
        
        ColorScheme.FOREST_GREEN -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = ForestGreenSurfaceDark,
            onSurface = ForestGreenOnSurfaceDark,
            surfaceVariant = ForestGreenSurfaceVariantDark,
            onSurfaceVariant = ForestGreenOnSurfaceVariantDark,
            outline = ForestGreenOutlineDark,
            outlineVariant = ForestGreenOutlineVariantDark
        )
        
        ColorScheme.TECH_PURPLE -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = TechPurpleSurfaceDark,
            onSurface = TechPurpleOnSurfaceDark,
            surfaceVariant = TechPurpleSurfaceVariantDark,
            onSurfaceVariant = TechPurpleOnSurfaceVariantDark,
            outline = TechPurpleOutlineDark,
            outlineVariant = TechPurpleOutlineVariantDark
        )
        
        ColorScheme.BLACK_MAMBA -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = BlackMambaSurfaceDark,
            onSurface = BlackMambaOnSurfaceDark,
            surfaceVariant = BlackMambaSurfaceVariantDark,
            onSurfaceVariant = BlackMambaOnSurfaceVariantDark,
            outline = BlackMambaOutlineDark,
            outlineVariant = BlackMambaOutlineVariantDark
        )
        
        ColorScheme.GREY_STYLE -> monicaWearColorScheme(
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
            background = backgroundColor,
            surface = GreyStyleSurfaceDark,
            onSurface = GreyStyleOnSurfaceDark,
            surfaceVariant = GreyStyleSurfaceVariantDark,
            onSurfaceVariant = GreyStyleOnSurfaceVariantDark,
            outline = GreyStyleOutlineDark,
            outlineVariant = GreyStyleOutlineVariantDark
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(),
        motionScheme = MotionScheme.standard()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            content()
        }
    }
}

private fun monicaWearColorScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    background: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
    outlineVariant: Color,
): WearColorScheme {
    return WearColorScheme(
        primary = primary,
        primaryDim = primaryContainer,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryDim = secondaryContainer,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        tertiaryDim = tertiaryContainer,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = surfaceVariant,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        background = background,
        onBackground = onSurface,
        error = error,
        errorDim = errorContainer,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
}

