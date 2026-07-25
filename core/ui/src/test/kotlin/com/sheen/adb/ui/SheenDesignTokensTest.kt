package com.sheen.adb.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class SheenDesignTokensTest {
    @Test
    fun `design baseline is embedded and does not depend on external files`() {
        assertEquals(
            SheenDesignTokens.designSourceSha256,
            "D8033B1F788BB576F724BE3E7E0923385C2D35F539725153429B7B8EF1CFC878",
        )
    }

    @Test
    fun `dark first palette preserves design source colors`() {
        assertEquals(SheenDarkColorScheme.surface, Color(0xFF0B1326))
        assertEquals(SheenDarkColorScheme.surfaceContainerLow, Color(0xFF131B2E))
        assertEquals(SheenDarkColorScheme.surfaceContainer, Color(0xFF171F33))
        assertEquals(SheenDarkColorScheme.surfaceContainerHigh, Color(0xFF222A3D))
        assertEquals(SheenDarkColorScheme.onSurface, Color(0xFFDAE2FD))
        assertEquals(SheenDarkColorScheme.onSurfaceVariant, Color(0xFFC2C6D6))
        assertEquals(SheenDarkColorScheme.primary, Color(0xFFADC6FF))
        assertEquals(SheenDarkColorScheme.secondary, Color(0xFF4EDEA3))
        assertEquals(SheenDarkColorScheme.tertiary, Color(0xFFFFB95F))
        assertEquals(SheenDarkColorScheme.error, Color(0xFFFFB4AB))
    }

    @Test
    fun `spacing and shapes follow four dp rhythm`() {
        assertEquals(SheenDimensions.rhythm, 4.dp)
        assertEquals(SheenDimensions.gutter, 12.dp)
        assertEquals(SheenDimensions.screenPadding, 16.dp)
        assertEquals(SheenCornerRadii.small, 2.dp)
        assertEquals(SheenCornerRadii.default, 4.dp)
        assertEquals(SheenCornerRadii.medium, 6.dp)
        assertEquals(SheenCornerRadii.large, 8.dp)
        assertEquals(SheenCornerRadii.extraLarge, 12.dp)
    }

    @Test
    fun `typography separates ui and technical data roles`() {
        assertEquals(SheenTypography.headlineLarge.fontFamily, FontFamily.SansSerif)
        assertEquals(SheenTypography.headlineLarge.fontSize, 24.sp)
        assertEquals(SheenTypography.headlineLarge.fontWeight, FontWeight.Bold)
        assertEquals(SheenTypography.bodyLarge.fontFamily, FontFamily.SansSerif)
        assertEquals(SheenTypography.bodyLarge.fontSize, 16.sp)
        assertEquals(SheenTypography.bodyLarge.lineHeight, 24.sp)
        assertEquals(SheenTypography.labelMedium.fontFamily, FontFamily.Monospace)
        assertEquals(SheenTypography.labelMedium.fontSize, 10.sp)
        assertEquals(SheenTypography.labelMedium.fontWeight, FontWeight.Bold)
        assertEquals(SheenTypography.bodySmall.fontFamily, FontFamily.Monospace)
        assertEquals(SheenTypography.bodySmall.fontSize, 12.sp)
    }

    @Test
    fun `visual size and accessible hit target are distinct`() {
        assertEquals(SheenDimensions.visualTouchTarget, 44.dp)
        assertTrue(SheenDimensions.minimumTouchTarget >= 48.dp)
    }
}
