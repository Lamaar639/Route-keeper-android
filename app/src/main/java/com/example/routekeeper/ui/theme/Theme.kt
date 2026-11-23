package com.example.routekeeper.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.routekeeper.data.model.AppThemeId

@Immutable
data class RouteThemePalette(
    val id: AppThemeId,
    val isDark: Boolean,
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val brandContainer: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val canvasBg: Color,
    val cardSurface: Color,
    val cardSurfaceVariant: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val heroCardBg: Color,
    val heroCardContainer: Color,
    val onHeroPrimary: Color,
    val onHeroMuted: Color,
    val deliveredAccent: Color,
    val onDelivered: Color,
    val deliveredBg: Color,
    val navAccent: Color,
    val warningAccent: Color,
    val warningBg: Color,
    val warningText: Color,
    val dangerAccent: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val slotCardBg: Color,
    val slotCardBorder: Color,
    val slotCardAccent: Color,
    val slotCardText: Color,
    val etaPillBg: Color,
    val etaPillText: Color
)

data class AppThemeSpec(
    val id: AppThemeId,
    val colorScheme: ColorScheme,
    val palette: RouteThemePalette
)

private val ForestPinePalette = RouteThemePalette(
    id = AppThemeId.FOREST_PINE,
    isDark = false,
    brandPrimary = PinePrimary,
    onBrandPrimary = CleanSurface,
    brandContainer = PinePrimaryContainer,
    accent = MintAccent,
    onAccent = PinePrimary,
    accentContainer = MintAccentContainer,
    onAccentContainer = PinePrimary,
    canvasBg = WarmSandBg,
    cardSurface = CleanSurface,
    cardSurfaceVariant = CleanSurfaceVariant,
    textPrimary = TextPrimary,
    textMuted = TextMuted,
    borderSubtle = BorderClean,
    heroCardBg = PinePrimary,
    heroCardContainer = PinePrimaryContainer,
    onHeroPrimary = CleanSurface,
    onHeroMuted = Color(0xFFE2ECE7),
    deliveredAccent = Color(0xFF047857),
    onDelivered = CleanSurface,
    deliveredBg = Color(0xFFECFDF5),
    navAccent = BlueNav,
    warningAccent = AmberAccess,
    warningBg = Color(0xFFFFFBEB),
    warningText = Color(0xFF92400E),
    dangerAccent = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEF2F2),
    dangerBorder = Color(0xFFFCA5A5),
    dangerText = Color(0xFF991B1B),
    slotCardBg = Color(0xFFF5F7FF),
    slotCardBorder = Color(0xFF4F46E5),
    slotCardAccent = Color(0xFF4338CA),
    slotCardText = Color(0xFF1E1B4B),
    etaPillBg = PinePrimary,
    etaPillText = CleanSurface
)

private val ForestPineScheme = lightColorScheme(
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

private val MidnightDarkPalette = RouteThemePalette(
    id = AppThemeId.MIDNIGHT_DARK,
    isDark = true,
    brandPrimary = Color(0xFF38BDF8),
    onBrandPrimary = Color(0xFF062030),
    brandContainer = Color(0xFF15384B),
    accent = Color(0xFF5EEAD4),
    onAccent = Color(0xFF062825),
    accentContainer = Color(0xFF133A3E),
    onAccentContainer = Color(0xFFA7F3D0),
    canvasBg = Color(0xFF0B1217),
    cardSurface = Color(0xFF141E26),
    cardSurfaceVariant = Color(0xFF1E2B36),
    textPrimary = Color(0xFFF1F5F9),
    textMuted = Color(0xFFA3B4C2),
    borderSubtle = Color(0xFF2B3B48),
    heroCardBg = Color(0xFF102A3A),
    heroCardContainer = Color(0xFF1B4359),
    onHeroPrimary = Color(0xFFF8FAFC),
    onHeroMuted = Color(0xFFC7DFEC),
    deliveredAccent = Color(0xFF10B981),
    onDelivered = Color(0xFF022C22),
    deliveredBg = Color(0xFF0C2A22),
    navAccent = Color(0xFF38BDF8),
    warningAccent = Color(0xFFFBBF24),
    warningBg = Color(0xFF2B210B),
    warningText = Color(0xFFFDE68A),
    dangerAccent = Color(0xFFF87171),
    dangerBg = Color(0xFF2E1418),
    dangerBorder = Color(0xFF991B1B),
    dangerText = Color(0xFFFECACA),
    slotCardBg = Color(0xFF182038),
    slotCardBorder = Color(0xFF818CF8),
    slotCardAccent = Color(0xFFA5B4FC),
    slotCardText = Color(0xFFE0E7FF),
    etaPillBg = Color(0xFF38BDF8),
    etaPillText = Color(0xFF062030)
)

private val MidnightDarkScheme = darkColorScheme(
    primary = MidnightDarkPalette.brandPrimary,
    onPrimary = MidnightDarkPalette.onBrandPrimary,
    primaryContainer = MidnightDarkPalette.brandContainer,
    onPrimaryContainer = MidnightDarkPalette.accent,
    secondary = MidnightDarkPalette.accent,
    onSecondary = MidnightDarkPalette.onAccent,
    secondaryContainer = MidnightDarkPalette.accentContainer,
    onSecondaryContainer = MidnightDarkPalette.onAccentContainer,
    background = MidnightDarkPalette.canvasBg,
    onBackground = MidnightDarkPalette.textPrimary,
    surface = MidnightDarkPalette.cardSurface,
    onSurface = MidnightDarkPalette.textPrimary,
    surfaceVariant = MidnightDarkPalette.cardSurfaceVariant,
    onSurfaceVariant = MidnightDarkPalette.textMuted,
    outline = MidnightDarkPalette.borderSubtle
)

private val OceanCobaltPalette = RouteThemePalette(
    id = AppThemeId.OCEAN_COBALT,
    isDark = false,
    brandPrimary = Color(0xFF1D4ED8),
    onBrandPrimary = Color(0xFFFFFFFF),
    brandContainer = Color(0xFF1E3A8A),
    accent = Color(0xFF93C5FD),
    onAccent = Color(0xFF0F172A),
    accentContainer = Color(0xFFDBEAFE),
    onAccentContainer = Color(0xFF1E3A8A),
    canvasBg = Color(0xFFF0F4FA),
    cardSurface = Color(0xFFFFFFFF),
    cardSurfaceVariant = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF475569),
    borderSubtle = Color(0xFFCBD5E1),
    heroCardBg = Color(0xFF1E3A8A),
    heroCardContainer = Color(0xFF2548A8),
    onHeroPrimary = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFDCE7FF),
    deliveredAccent = Color(0xFF059669),
    onDelivered = Color(0xFFFFFFFF),
    deliveredBg = Color(0xFFECFDF5),
    navAccent = Color(0xFF0284C7),
    warningAccent = Color(0xFFD97706),
    warningBg = Color(0xFFFFFBEB),
    warningText = Color(0xFF92400E),
    dangerAccent = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEF2F2),
    dangerBorder = Color(0xFFFCA5A5),
    dangerText = Color(0xFF991B1B),
    slotCardBg = Color(0xFFEFF6FF),
    slotCardBorder = Color(0xFF2563EB),
    slotCardAccent = Color(0xFF1D4ED8),
    slotCardText = Color(0xFF1E3A8A),
    etaPillBg = Color(0xFF1D4ED8),
    etaPillText = Color(0xFFFFFFFF)
)

private val OceanCobaltScheme = lightColorScheme(
    primary = OceanCobaltPalette.brandPrimary,
    onPrimary = OceanCobaltPalette.onBrandPrimary,
    primaryContainer = OceanCobaltPalette.brandContainer,
    onPrimaryContainer = OceanCobaltPalette.accent,
    secondary = OceanCobaltPalette.accent,
    onSecondary = OceanCobaltPalette.onAccent,
    secondaryContainer = OceanCobaltPalette.accentContainer,
    onSecondaryContainer = OceanCobaltPalette.onAccentContainer,
    background = OceanCobaltPalette.canvasBg,
    onBackground = OceanCobaltPalette.textPrimary,
    surface = OceanCobaltPalette.cardSurface,
    onSurface = OceanCobaltPalette.textPrimary,
    surfaceVariant = OceanCobaltPalette.cardSurfaceVariant,
    onSurfaceVariant = OceanCobaltPalette.textMuted,
    outline = OceanCobaltPalette.borderSubtle
)

private val SunsetTerracottaPalette = RouteThemePalette(
    id = AppThemeId.SUNSET_TERRACOTTA,
    isDark = false,
    brandPrimary = Color(0xFF9A3412),
    onBrandPrimary = Color(0xFFFFFFFF),
    brandContainer = Color(0xFF7C2D12),
    accent = Color(0xFFFCD34D),
    onAccent = Color(0xFF451A03),
    accentContainer = Color(0xFFFEF3C7),
    onAccentContainer = Color(0xFF78350F),
    canvasBg = Color(0xFFFAF4EE),
    cardSurface = Color(0xFFFFFCF9),
    cardSurfaceVariant = Color(0xFFF3E8DC),
    textPrimary = Color(0xFF291711),
    textMuted = Color(0xFF6B4F43),
    borderSubtle = Color(0xFFE6D6C7),
    heroCardBg = Color(0xFF7C2D12),
    heroCardContainer = Color(0xFF9A3412),
    onHeroPrimary = Color(0xFFFFFBF5),
    onHeroMuted = Color(0xFFFED7AA),
    deliveredAccent = Color(0xFF047857),
    onDelivered = Color(0xFFFFFFFF),
    deliveredBg = Color(0xFFECFDF5),
    navAccent = Color(0xFF0369A1),
    warningAccent = Color(0xFFB45309),
    warningBg = Color(0xFFFFFBEB),
    warningText = Color(0xFF78350F),
    dangerAccent = Color(0xFFB91C1C),
    dangerBg = Color(0xFFFEF2F2),
    dangerBorder = Color(0xFFFCA5A5),
    dangerText = Color(0xFF7F1D1D),
    slotCardBg = Color(0xFFFFF7ED),
    slotCardBorder = Color(0xFFEA580C),
    slotCardAccent = Color(0xFFC2410C),
    slotCardText = Color(0xFF431407),
    etaPillBg = Color(0xFF9A3412),
    etaPillText = Color(0xFFFFFFFF)
)

private val SunsetTerracottaScheme = lightColorScheme(
    primary = SunsetTerracottaPalette.brandPrimary,
    onPrimary = SunsetTerracottaPalette.onBrandPrimary,
    primaryContainer = SunsetTerracottaPalette.brandContainer,
    onPrimaryContainer = SunsetTerracottaPalette.accent,
    secondary = SunsetTerracottaPalette.accent,
    onSecondary = SunsetTerracottaPalette.onAccent,
    secondaryContainer = SunsetTerracottaPalette.accentContainer,
    onSecondaryContainer = SunsetTerracottaPalette.onAccentContainer,
    background = SunsetTerracottaPalette.canvasBg,
    onBackground = SunsetTerracottaPalette.textPrimary,
    surface = SunsetTerracottaPalette.cardSurface,
    onSurface = SunsetTerracottaPalette.textPrimary,
    surfaceVariant = SunsetTerracottaPalette.cardSurfaceVariant,
    onSurfaceVariant = SunsetTerracottaPalette.textMuted,
    outline = SunsetTerracottaPalette.borderSubtle
)

private val HighContrastCabPalette = RouteThemePalette(
    id = AppThemeId.HIGH_CONTRAST_CAB,
    isDark = true,
    brandPrimary = Color(0xFFFBBF24),
    onBrandPrimary = Color(0xFF181202),
    brandContainer = Color(0xFF27272A),
    accent = Color(0xFFFDE047),
    onAccent = Color(0xFF141100),
    accentContainer = Color(0xFF3B2F08),
    onAccentContainer = Color(0xFFFEF08A),
    canvasBg = Color(0xFF09090B),
    cardSurface = Color(0xFF141417),
    cardSurfaceVariant = Color(0xFF27272A),
    textPrimary = Color(0xFFFAFAFA),
    textMuted = Color(0xFFD4D4D8),
    borderSubtle = Color(0xFF3F3F46),
    heroCardBg = Color(0xFF18181B),
    heroCardContainer = Color(0xFF27272A),
    onHeroPrimary = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFE4E4E7),
    deliveredAccent = Color(0xFF22C55E),
    onDelivered = Color(0xFF03210D),
    deliveredBg = Color(0xFF0B2916),
    navAccent = Color(0xFF60A5FA),
    warningAccent = Color(0xFFFACC15),
    warningBg = Color(0xFF2A2106),
    warningText = Color(0xFFFEF08A),
    dangerAccent = Color(0xFFF87171),
    dangerBg = Color(0xFF2E1215),
    dangerBorder = Color(0xFFDC2626),
    dangerText = Color(0xFFFECACA),
    slotCardBg = Color(0xFF1E1B2E),
    slotCardBorder = Color(0xFFA78BFA),
    slotCardAccent = Color(0xFFC4B5FD),
    slotCardText = Color(0xFFF5F3FF),
    etaPillBg = Color(0xFFFBBF24),
    etaPillText = Color(0xFF141100)
)

private val HighContrastCabScheme = darkColorScheme(
    primary = HighContrastCabPalette.brandPrimary,
    onPrimary = HighContrastCabPalette.onBrandPrimary,
    primaryContainer = HighContrastCabPalette.brandContainer,
    onPrimaryContainer = HighContrastCabPalette.accent,
    secondary = HighContrastCabPalette.accent,
    onSecondary = HighContrastCabPalette.onAccent,
    secondaryContainer = HighContrastCabPalette.accentContainer,
    onSecondaryContainer = HighContrastCabPalette.onAccentContainer,
    background = HighContrastCabPalette.canvasBg,
    onBackground = HighContrastCabPalette.textPrimary,
    surface = HighContrastCabPalette.cardSurface,
    onSurface = HighContrastCabPalette.textPrimary,
    surfaceVariant = HighContrastCabPalette.cardSurfaceVariant,
    onSurfaceVariant = HighContrastCabPalette.textMuted,
    outline = HighContrastCabPalette.borderSubtle
)

object AppThemes {
    val all: List<AppThemeSpec> = listOf(
        AppThemeSpec(AppThemeId.FOREST_PINE, ForestPineScheme, ForestPinePalette),
        AppThemeSpec(AppThemeId.MIDNIGHT_DARK, MidnightDarkScheme, MidnightDarkPalette),
        AppThemeSpec(AppThemeId.OCEAN_COBALT, OceanCobaltScheme, OceanCobaltPalette),
        AppThemeSpec(AppThemeId.SUNSET_TERRACOTTA, SunsetTerracottaScheme, SunsetTerracottaPalette),
        AppThemeSpec(AppThemeId.HIGH_CONTRAST_CAB, HighContrastCabScheme, HighContrastCabPalette)
    )

    fun resolve(id: AppThemeId): AppThemeSpec =
        all.firstOrNull { it.id == id } ?: all.first()
}

val LocalRoutePalette = staticCompositionLocalOf { ForestPinePalette }

object RouteTheme {
    val colors: RouteThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalRoutePalette.current
}

@Composable
fun RouteKeeperTheme(
    themeId: AppThemeId = AppThemeId.FOREST_PINE,
    content: @Composable () -> Unit
) {
    val spec = AppThemes.resolve(themeId)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !spec.palette.isDark
                controller.isAppearanceLightNavigationBars = !spec.palette.isDark
            }
        }
    }

    CompositionLocalProvider(LocalRoutePalette provides spec.palette) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = Typography,
            content = content
        )
    }
}
