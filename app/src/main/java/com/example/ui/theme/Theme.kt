package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PennyLensLightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralSoftBg,
    onPrimaryContainer = CoralPrimary,
    secondary = BlueIncome,
    onSecondary = Color.White,
    secondaryContainer = BlueSoftBg,
    onSecondaryContainer = BlueIncome,
    tertiary = MidnightSlate,
    onTertiary = Color.White,
    background = SurfaceGroupedBg,
    onBackground = MidnightInk,
    surface = SurfaceCard,
    onSurface = MidnightInk,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = SlateSecondary,
    outline = DividerHairline,
    outlineVariant = DividerHairline,
    error = CoralPrimary,
    onError = Color.White
)

val PennyLensShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PennyLensTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PennyLensLightColorScheme,
        typography = PennyLensTypography,
        shapes = PennyLensShapes,
        content = content
    )
}
