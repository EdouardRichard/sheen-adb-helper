package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class LanguageStateIsolationTest {
    @Test
    fun `language is absent from business identity and effect keys`() {
        val roots = listOf(File("src/main"), File("../feature"))
        val files = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        val violations = files.flatMap { file ->
            val source = file.readText()
            buildList {
                if (Regex("(LaunchedEffect|DisposableEffect|key)\\([^)]*language").containsMatchIn(source)) {
                    add("${file.path}: language-effect-key")
                }
                if (
                    Regex(
                        "data class (NavigationLock|DeliveryIoState|.*Task|.*Session|.*Window|.*Attempt)[\\s\\S]{0,500}UiLanguage",
                    ).containsMatchIn(source)
                ) {
                    add("${file.path}: language-business-identity")
                }
            }
        }

        assertEquals(violations, emptyList<String>(), violations.joinToString("\n"))
    }

    @Test
    fun `one root language snapshot reaches every in scope route`() {
        val source = File("src/main/kotlin/com/sheen/adbhelper/SheenApp.kt").readText()

        assertEquals(
            Regex("languagePreference\\.collectAsStateWithLifecycle").findAll(source).count(),
            1,
        )
        listOf(
            "DevicesRoute",
            "OverviewRoute",
            "FilesRoute",
            "AppsRoute",
            "ProcessesRoute",
            "ShellRoute",
            "LogcatRoute",
            "SettingsRoute",
        ).forEach { route ->
            val invocation = source.substringAfter("$route(", missingDelimiterValue = "")
                .substringBefore(")")
            assertTrue(invocation.contains("language"), "$route is not using root language")
        }
        assertFalse(source.contains("Activity.recreate("))
    }
}
