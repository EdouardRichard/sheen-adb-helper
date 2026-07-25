package com.sheen.adbhelper

import java.nio.file.Files
import java.nio.file.Path
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.annotations.Test

class VersionContractTest {
    @Test
    fun `v0_1 build identity is exposed without design placeholder versions`() {
        assertEquals(BuildConfig.VERSION_NAME, "0.1.0")
        assertEquals(BuildConfig.VERSION_CODE, 3)

        val appSource = String(
            Files.readAllBytes(
                Path.of("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt"),
            ),
        )
        assertFalse(appSource.contains("v2.4.1"))
        assertFalse(appSource.contains("测试版"))
    }
}
