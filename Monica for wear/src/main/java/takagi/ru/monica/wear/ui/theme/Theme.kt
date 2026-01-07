package takagi.ru.monica.wear.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
        ColorScheme.OCEAN_BLUE -> darkColorScheme(
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
            onBackground = OceanBlueOnSurfaceDark,
            surface = OceanBlueSurfaceDark,
            onSurface = OceanBlueOnSurfaceDark,
            surfaceVariant = OceanBlueSurfaceVariantDark,
            onSurfaceVariant = OceanBlueOnSurfaceVariantDark,
            outline = OceanBlueOutlineDark,
            outlineVariant = OceanBlueOutlineVariantDark
        )
        
        ColorScheme.SUNSET_ORANGE -> darkColorScheme(
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
            onBackground = SunsetOrangeOnSurfaceDark,
            surface = SunsetOrangeSurfaceDark,
            onSurface = SunsetOrangeOnSurfaceDark,
            surfaceVariant = SunsetOrangeSurfaceVariantDark,
            onSurfaceVariant = SunsetOrangeOnSurfaceVariantDark,
            outline = SunsetOrangeOutlineDark,
            outlineVariant = SunsetOrangeOutlineVariantDark
        )
        
        ColorScheme.FOREST_GREEN -> darkColorScheme(
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
            onBackground = ForestGreenOnSurfaceDark,
            surface = ForestGreenSurfaceDark,
            onSurface = ForestGreenOnSurfaceDark,
            surfaceVariant = ForestGreenSurfaceVariantDark,
            onSurfaceVariant = ForestGreenOnSurfaceVariantDark,
            outline = ForestGreenOutlineDark,
            outlineVariant = ForestGreenOutlineVariantDark
        )
        
        ColorScheme.TECH_PURPLE -> darkColorScheme(
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
            onBackground = TechPurpleOnSurfaceDark,
            surface = TechPurpleSurfaceDark,
            onSurface = TechPurpleOnSurfaceDark,
            surfaceVariant = TechPurpleSurfaceVariantDark,
            onSurfaceVariant = TechPurpleOnSurfaceVariantDark,
            outline = TechPurpleOutlineDark,
            outlineVariant = TechPurpleOutlineVariantDark
        )
        
        ColorScheme.BLACK_MAMBA -> darkColorScheme(
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
            onBackground = BlackMambaOnSurfaceDark,
            surface = BlackMambaSurfaceDark,
            onSurface = BlackMambaOnSurfaceDark,
            surfaceVariant = BlackMambaSurfaceVariantDark,
            onSurfaceVariant = BlackMambaOnSurfaceVariantDark,
            outline = BlackMambaOutlineDark,
            outlineVariant = BlackMambaOutlineVariantDark
        )
        
        ColorScheme.GREY_STYLE -> darkColorScheme(
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
            onBackground = GreyStyleOnSurfaceDark,
            surface = GreyStyleSurfaceDark,
            onSurface = GreyStyleOnSurfaceDark,
            surfaceVariant = GreyStyleSurfaceVariantDark,
            onSurfaceVariant = GreyStyleOnSurfaceVariantDark,
            outline = GreyStyleOutlineDark,
            outlineVariant = GreyStyleOutlineVariantDark
        )
    }

    // 使用 Wear Material 主题
    androidx.wear.compose.material.MaterialTheme(
        colors = androidx.wear.compose.material.Colors(
            primary = colors.primary,
            primaryVariant = colors.primaryContainer,
            secondary = colors.secondary,
            secondaryVariant = colors.secondaryContainer,
            error = colors.error,
            onPrimary = colors.onPrimary,
            onSecondary = colors.onSecondary,
            onError = colors.onError,
            surface = colors.surface,
            onSurface = colors.onSurface,
            background = colors.background,
            onBackground = colors.onBackground
        )
    ) {
        // 提供 M3 颜色给需要它的组件
        MaterialTheme(
            colorScheme = colors
        ) {
            // 重要：为 Wear OS 添加显式背景包装器
            // Wear OS 不会自动应用背景色，需要手动包装
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                content()
            }
        }
    }
}

