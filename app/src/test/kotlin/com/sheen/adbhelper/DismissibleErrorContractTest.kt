package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DismissibleErrorContractTest {
    @Test
    fun `every feature error surface exposes a top trailing close action wired to state dismissal`() {
        val features = listOf(
            "apps" to "dismissError",
            "files" to "dismissError",
            "processes" to "dismissError",
            "shell" to "dismissError",
            "logcat" to "dismissError",
            "overview" to "dismissError",
        )
        features.forEach { (feature, dismissMethod) ->
            val root = File("../feature/$feature/src/main/kotlin")
            val source = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }
            assertTrue(source.contains("fun $dismissMethod"), "$feature must clear its visible error state")
            assertTrue(source.contains("onDismissError"), "$feature must wire the close action through its route")
            assertTrue(source.contains("SheenIcons.Close"), "$feature error surface must show an X icon")
        }
    }
}
