package takagi.ru.monica.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import takagi.ru.monica.wear.viewmodel.ColorScheme

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

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
