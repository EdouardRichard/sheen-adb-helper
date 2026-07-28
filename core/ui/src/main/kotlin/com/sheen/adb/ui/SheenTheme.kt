package com.sheen.adb.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SheenDesignTokens {
    /**
     * SHA-256 of the reviewed DESIGN.md baseline. Runtime styling is fully
     * embedded; the app never reads the repository-external design file.
     */
    const val designSourceSha256 =
        "D8033B1F788BB576F724BE3E7E0923385C2D35F539725153429B7B8EF1CFC878"
}

object SheenDimensions {
    val rhythm = 4.dp
    val gutter = 12.dp
    val commonGutter = 12.dp
    val screenPadding = 16.dp
    val compactPageHorizontalMargin = 16.dp
    val expandedPageHorizontalMargin = 24.dp
    val itemSpacing = 12.dp
    val visualTouchTarget = 44.dp
    val minimumTouchTarget = 44.dp
    val topBarVisualHeight = 44.dp
    val bottomBarHeight = 60.dp
    val drawerWidth = 288.dp
    val expandedPaneWidth = drawerWidth
    val deviceIconContainer = 40.dp
    val metricCardMinHeight = 206.dp
}

object SheenColors {
    val terminalBackground = Color.Black
}

object SheenTonalLayers {
    const val quietOutlineAlpha = 0.28f
    const val subtleOutlineAlpha = 0.42f
    const val emphasisOutlineAlpha = 0.72f
    const val framelessOverlayDimAlpha = 0.26f
}

object SheenCornerRadii {
    val small = 4.dp
    val default = 4.dp
    val medium = 6.dp
    val large = 8.dp
    val extraLarge = 12.dp
}

object SheenShapes {
    val small = RoundedCornerShape(SheenCornerRadii.small)
    val default = RoundedCornerShape(SheenCornerRadii.default)
    val medium = RoundedCornerShape(SheenCornerRadii.medium)
    val large = RoundedCornerShape(SheenCornerRadii.large)
    val extraLarge = RoundedCornerShape(SheenCornerRadii.extraLarge)
    val full = RoundedCornerShape(9999.dp)
}

val SheenDarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFADC6FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF002E6A),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF4D8EFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00285D),
    secondary = androidx.compose.ui.graphics.Color(0xFF4EDEA3),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF003824),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF00A572),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00311F),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFB95F),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF472A00),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFCA8100),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF3E2400),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    background = androidx.compose.ui.graphics.Color(0xFF0B1326),
    onBackground = androidx.compose.ui.graphics.Color(0xFFDAE2FD),
    surface = androidx.compose.ui.graphics.Color(0xFF0B1326),
    onSurface = androidx.compose.ui.graphics.Color(0xFFDAE2FD),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2D3449),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC2C6D6),
    outline = androidx.compose.ui.graphics.Color(0xFF8C909F),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF424754),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFDAE2FD),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF283044),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFF005AC2),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFFADC6FF),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFF31394D),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFF0B1326),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF060E20),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF131B2E),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF171F33),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF222A3D),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2D3449),
)

object SheenTypography {
    val headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
    )
    val headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    )
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    )
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )
    val labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    )
    val bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    val labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
    )
}

private val SheenMaterialShapes = Shapes(
    extraSmall = SheenShapes.small,
    small = SheenShapes.default,
    medium = SheenShapes.medium,
    large = SheenShapes.large,
    extraLarge = SheenShapes.extraLarge,
)

private val SheenMaterialTypography = Typography(
    headlineLarge = SheenTypography.headlineLarge,
    headlineMedium = SheenTypography.headlineMedium,
    bodyLarge = SheenTypography.bodyLarge,
    bodyMedium = SheenTypography.bodyMedium,
    labelLarge = SheenTypography.labelLarge,
    bodySmall = SheenTypography.bodySmall,
    labelMedium = SheenTypography.labelMedium,
)

@Composable
fun SheenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SheenDarkColorScheme,
        typography = SheenMaterialTypography,
        shapes = SheenMaterialShapes,
        content = content,
    )
}
