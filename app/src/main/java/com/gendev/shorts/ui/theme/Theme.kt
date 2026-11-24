package com.gendev.shorts.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AmoledColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = PureWhite,
    primaryContainer = ElectricVioletDeep,
    onPrimaryContainer = PureWhite,
    secondary = ElectricVioletBright,
    onSecondary = AmoledBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = PureWhite,
    tertiary = EmeraldHealthy,
    onTertiary = AmoledBlack,
    background = AmoledBlack,
    onBackground = PureWhite,
    surface = AmoledBlack,
    onSurface = PureWhite,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianElevated,
    outline = GlassBorder,
    error = CoralError,
    onError = AmoledBlack,
)

private val GendevShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GendevShortsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        typography = GendevTypography,
        shapes = GendevShapes,
        content = content,
    )
}
