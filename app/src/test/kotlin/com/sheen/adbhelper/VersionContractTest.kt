package com.sheen.adbhelper

import java.nio.file.Files
import java.nio.file.Path
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.annotations.Test

class VersionContractTest {
    @Test
    fun `v1 build identity is exposed without design placeholder versions`() {
        assertEquals(BuildConfig.VERSION_NAME, "1.0")
        assertEquals(BuildConfig.VERSION_CODE, 4)

        val appSource = String(
            Files.readAllBytes(
                Path.of("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt"),
            ),
        )
        val buildSource = String(
            Files.readAllBytes(
                Path.of("build.gradle.kts"),
            ),
        )
        assertFalse(buildSource.contains("versionName = \"v1.0\""))
        assertEquals(
            appSource.windowed("\"v\${BuildConfig.VERSION_NAME}\"".length)
                .count { it == "\"v\${BuildConfig.VERSION_NAME}\"" },
            1,
        )
        assertFalse(appSource.contains("v0."))
        assertFalse(appSource.contains("0.1.0"))
        assertFalse(appSource.contains("v2.4.1"))
        assertFalse(appSource.contains("测试版"))
        assertFalse(appSource.contains("\"vv1.0\""))
    }
}
