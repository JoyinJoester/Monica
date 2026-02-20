package takagi.ru.monica.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.TonalPalette
import com.google.android.material.color.utilities.Variant

private fun Long.toArgbInt(): Int = toInt()

private fun Int.toComposeColor(): Color = Color(toLong() and 0xFFFFFFFF)

private fun buildCompositeDynamicScheme(
    darkTheme: Boolean,
    primarySeed: Long,
    secondarySeed: Long,
    tertiarySeed: Long
): DynamicScheme {
    val primaryHct = Hct.fromInt(primarySeed.toArgbInt())
    val secondaryHct = Hct.fromInt(secondarySeed.toArgbInt())
    val tertiaryHct = Hct.fromInt(tertiarySeed.toArgbInt())

    return DynamicScheme(
        primaryHct,
        Variant.TONAL_SPOT,
        darkTheme,
        0.0,
        TonalPalette.fromHct(primaryHct),
        TonalPalette.fromHct(secondaryHct),
        TonalPalette.fromHct(tertiaryHct),
        TonalPalette.fromHueAndChroma(primaryHct.hue, 8.0),
        TonalPalette.fromHueAndChroma(secondaryHct.hue, 12.0)
    )
}

private fun DynamicColor.resolveToColor(scheme: DynamicScheme): Color =
    getArgb(scheme).toComposeColor()

fun generateCustomMaterialColorScheme(
    darkTheme: Boolean,
    primarySeed: Long,
    secondarySeed: Long,
    tertiarySeed: Long
): ColorScheme {
    val scheme = buildCompositeDynamicScheme(
        darkTheme = darkTheme,
        primarySeed = primarySeed,
        secondarySeed = secondarySeed,
        tertiarySeed = tertiarySeed
    )
    val roles = MaterialDynamicColors()

    return if (darkTheme) {
        darkColorScheme(
            primary = roles.primary().resolveToColor(scheme),
            onPrimary = roles.onPrimary().resolveToColor(scheme),
            primaryContainer = roles.primaryContainer().resolveToColor(scheme),
            onPrimaryContainer = roles.onPrimaryContainer().resolveToColor(scheme),
            inversePrimary = roles.inversePrimary().resolveToColor(scheme),
            secondary = roles.secondary().resolveToColor(scheme),
            onSecondary = roles.onSecondary().resolveToColor(scheme),
            secondaryContainer = roles.secondaryContainer().resolveToColor(scheme),
            onSecondaryContainer = roles.onSecondaryContainer().resolveToColor(scheme),
            tertiary = roles.tertiary().resolveToColor(scheme),
            onTertiary = roles.onTertiary().resolveToColor(scheme),
            tertiaryContainer = roles.tertiaryContainer().resolveToColor(scheme),
            onTertiaryContainer = roles.onTertiaryContainer().resolveToColor(scheme),
            background = roles.background().resolveToColor(scheme),
            onBackground = roles.onBackground().resolveToColor(scheme),
            surface = roles.surface().resolveToColor(scheme),
            onSurface = roles.onSurface().resolveToColor(scheme),
            surfaceVariant = roles.surfaceVariant().resolveToColor(scheme),
            onSurfaceVariant = roles.onSurfaceVariant().resolveToColor(scheme),
            surfaceTint = roles.surfaceTint().resolveToColor(scheme),
            inverseSurface = roles.inverseSurface().resolveToColor(scheme),
            inverseOnSurface = roles.inverseOnSurface().resolveToColor(scheme),
            error = roles.error().resolveToColor(scheme),
            onError = roles.onError().resolveToColor(scheme),
            errorContainer = roles.errorContainer().resolveToColor(scheme),
            onErrorContainer = roles.onErrorContainer().resolveToColor(scheme),
            outline = roles.outline().resolveToColor(scheme),
            outlineVariant = roles.outlineVariant().resolveToColor(scheme),
            scrim = roles.scrim().resolveToColor(scheme),
            surfaceBright = roles.surfaceBright().resolveToColor(scheme),
            surfaceContainer = roles.surfaceContainer().resolveToColor(scheme),
            surfaceContainerHigh = roles.surfaceContainerHigh().resolveToColor(scheme),
            surfaceContainerHighest = roles.surfaceContainerHighest().resolveToColor(scheme),
            surfaceContainerLow = roles.surfaceContainerLow().resolveToColor(scheme),
            surfaceContainerLowest = roles.surfaceContainerLowest().resolveToColor(scheme),
            surfaceDim = roles.surfaceDim().resolveToColor(scheme)
        )
    } else {
        lightColorScheme(
            primary = roles.primary().resolveToColor(scheme),
            onPrimary = roles.onPrimary().resolveToColor(scheme),
            primaryContainer = roles.primaryContainer().resolveToColor(scheme),
            onPrimaryContainer = roles.onPrimaryContainer().resolveToColor(scheme),
            inversePrimary = roles.inversePrimary().resolveToColor(scheme),
            secondary = roles.secondary().resolveToColor(scheme),
            onSecondary = roles.onSecondary().resolveToColor(scheme),
            secondaryContainer = roles.secondaryContainer().resolveToColor(scheme),
            onSecondaryContainer = roles.onSecondaryContainer().resolveToColor(scheme),
            tertiary = roles.tertiary().resolveToColor(scheme),
            onTertiary = roles.onTertiary().resolveToColor(scheme),
            tertiaryContainer = roles.tertiaryContainer().resolveToColor(scheme),
            onTertiaryContainer = roles.onTertiaryContainer().resolveToColor(scheme),
            background = roles.background().resolveToColor(scheme),
            onBackground = roles.onBackground().resolveToColor(scheme),
            surface = roles.surface().resolveToColor(scheme),
            onSurface = roles.onSurface().resolveToColor(scheme),
            surfaceVariant = roles.surfaceVariant().resolveToColor(scheme),
            onSurfaceVariant = roles.onSurfaceVariant().resolveToColor(scheme),
            surfaceTint = roles.surfaceTint().resolveToColor(scheme),
            inverseSurface = roles.inverseSurface().resolveToColor(scheme),
            inverseOnSurface = roles.inverseOnSurface().resolveToColor(scheme),
            error = roles.error().resolveToColor(scheme),
            onError = roles.onError().resolveToColor(scheme),
            errorContainer = roles.errorContainer().resolveToColor(scheme),
            onErrorContainer = roles.onErrorContainer().resolveToColor(scheme),
            outline = roles.outline().resolveToColor(scheme),
            outlineVariant = roles.outlineVariant().resolveToColor(scheme),
            scrim = roles.scrim().resolveToColor(scheme),
            surfaceBright = roles.surfaceBright().resolveToColor(scheme),
            surfaceContainer = roles.surfaceContainer().resolveToColor(scheme),
            surfaceContainerHigh = roles.surfaceContainerHigh().resolveToColor(scheme),
            surfaceContainerHighest = roles.surfaceContainerHighest().resolveToColor(scheme),
            surfaceContainerLow = roles.surfaceContainerLow().resolveToColor(scheme),
            surfaceContainerLowest = roles.surfaceContainerLowest().resolveToColor(scheme),
            surfaceDim = roles.surfaceDim().resolveToColor(scheme)
        )
    }
}
