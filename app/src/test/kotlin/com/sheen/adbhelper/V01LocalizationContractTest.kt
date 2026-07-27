package com.sheen.adbhelper

import com.sheen.adb.ui.UiLanguage
import com.sheen.adb.ui.V01StringKey
import com.sheen.adb.ui.V01Strings
import java.io.File
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class V01LocalizationContractTest {
    @Test
    fun `shell menu about navigation and status semantics are bilingual`() {
        val keys = listOf(
            V01StringKey.MENU,
            V01StringKey.HISTORY_DEVICES,
            V01StringKey.SETTINGS,
            V01StringKey.ABOUT,
            V01StringKey.ABOUT_SUPPORT,
            V01StringKey.NAV_CONNECT,
            V01StringKey.NAV_FILES,
            V01StringKey.NAV_APPS,
            V01StringKey.NAV_PROCESSES,
            V01StringKey.NAV_TERMINAL,
            V01StringKey.NAV_LOGS,
            V01StringKey.CONNECTION_CONNECTING,
            V01StringKey.CONNECTION_CONNECTED,
            V01StringKey.CONNECTION_DISCONNECTED,
            V01StringKey.CONNECTION_ENDPOINT_CONTENT_DESCRIPTION,
        )
        keys.forEach { key ->
            assertFalse(
                V01Strings.text(UiLanguage.ZH_CN, key) ==
                    V01Strings.text(UiLanguage.EN_US, key),
                "Missing bilingual value for ${key.semanticKey}",
            )
        }
    }

    @Test
    fun `app and connection page read the shared language catalog`() {
        val app = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()
        val devices = File(
            "../feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt",
        ).readText()
        assertTrue(app.contains("V01Strings.text(language"))
        assertTrue(app.contains("V01StringKey.CONNECTION_ENDPOINT_HINT"))
        assertTrue(devices.contains("V01Strings.text(language"))
    }
}
