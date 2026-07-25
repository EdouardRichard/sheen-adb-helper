package com.sheen.adb.feature.overview

import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings
import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class OverviewLocalizationTest {
    @Test
    fun `overview degradation and every quick action terminal state are bilingual`() {
        val keys = listOf(
            V01StringKey.VALUE_UNAVAILABLE,
            V01StringKey.EMPTY_OVERVIEW,
            V01StringKey.QUICK_SCREENSHOT,
            V01StringKey.QUICK_RECORD,
            V01StringKey.QUICK_REBOOT,
            V01StringKey.QUICK_RECORDING,
            V01StringKey.QUICK_EXPORT,
            V01StringKey.QUICK_EXPORT_CANCELLED,
            V01StringKey.QUICK_UNSUPPORTED,
            V01StringKey.QUICK_RESULT_UNKNOWN,
        )
        keys.forEach { key ->
            assertFalse(
                V01Strings.text(UiLanguage.ZH_CN, key) ==
                    V01Strings.text(UiLanguage.EN_US, key),
            )
        }
    }

    @Test
    fun `overview screen localizes presentation without changing reducer state`() {
        val screen = File(
            "src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt",
        ).readText()
        val reducer = File(
            "src/main/kotlin/com/sheen/adb/feature/overview/QuickActionPresentation.kt",
        ).readText()
        assertTrue(screen.contains("V01Strings.text(language"))
        assertFalse(reducer.contains("UiLanguage"))
        assertFalse(reducer.contains("V01Strings"))
    }
}
