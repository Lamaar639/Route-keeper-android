package com.example.routekeeper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PinePrimary,
    onPrimary = CleanSurface,
    primaryContainer = PinePrimaryContainer,
    onPrimaryContainer = MintAccent,
    secondary = MintAccent,
    onSecondary = PinePrimary,
    secondaryContainer = MintAccentContainer,
    onSecondaryContainer = PinePrimary,
    background = WarmSandBg,
    onBackground = TextPrimary,
    surface = CleanSurface,
    onSurface = TextPrimary,
    surfaceVariant = CleanSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = BorderClean
)

@Composable
fun RouteKeeperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
