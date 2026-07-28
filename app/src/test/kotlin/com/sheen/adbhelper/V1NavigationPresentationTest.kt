package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class V1NavigationPresentationTest {
    @Test
    fun `page host uses one current destination for content selection and navigation selection`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(source.contains("PageHostState"))
        assertTrue(source.contains("pageHostState.current"))
        assertTrue(source.contains("selected = pageHostState.current"))
        assertTrue(source.contains("destination = pageHostState.current"))
        assertFalse(source.contains("var destination by rememberSaveable"))
    }

    @Test
    fun `navigation uses fade only and compact expanded chrome are mutually exclusive`() {
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()
        val theme = File("../core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt").readText()

        assertTrue(theme.contains("topBarVisualHeight = 44.dp"))
        assertTrue(app.contains("fadeOut("))
        assertTrue(app.contains("fadeIn("))
        assertTrue(app.contains("maxWidth >= 700.dp"))
        assertTrue(app.contains("PermanentNavigationDrawer"))
        assertTrue(app.contains("SheenBottomNavigation"))
        assertFalse(app.contains("HorizontalPager"))
        assertFalse(app.contains("slideIn"))
        assertFalse(app.contains("slideOut"))
    }

    @Test
    fun `page loading navigation wait is a frameless centered overlay`() {
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertTrue(app.contains("PageLoadNavigationOverlay("))
        val overlay = app.substringAfter("private fun PageLoadNavigationOverlay(")
            .substringBefore("\n@Composable", missingDelimiterValue = "")
        assertTrue(overlay.contains("contentAlignment = Alignment.Center"))
        assertTrue(overlay.contains("SheenTonalLayers.framelessOverlayDimAlpha"))
        assertTrue(overlay.contains("PAGE_LOADING_WAIT"))
        assertTrue(overlay.contains("PAGE_LOADING_CANCEL"))
        assertFalse(overlay.contains("Alignment.TopCenter"))
        assertFalse(overlay.contains("surfaceContainerHighest"))
        assertFalse(overlay.contains(".border("))
    }
}
