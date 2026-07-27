package com.sheen.adbhelper

import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class V1ArchitectureBoundaryTest {
    private val repositoryRoot = File("..").canonicalFile

    @Test
    fun `feature and presentation sources own no raw adb transport or command assembly`() {
        val presentationRoots = listOf(
            "app/src/main",
            "core/ui/src/main",
            "feature",
        ).map(repositoryRoot::resolve)
        val transportTokens = listOf(
            "import com.flyfish233",
            "import java.net.Socket",
            "import javax.net.ssl",
            "import android.net.nsd",
            "KadbConnection",
        )
        val rawCommandPatterns = listOf(
            Regex("\"pm\\s"),
            Regex("\"am\\s"),
            Regex("\"kill\\s"),
            Regex("\"logcat(?:\\s|\")"),
            Regex("\"dumpsys\\s"),
            Regex("ProcessBuilder\\("),
            Regex("Runtime\\.getRuntime\\(\\)\\.exec"),
        )

        val violations = kotlinSources(presentationRoots).flatMap { file ->
            val text = file.readText()
            buildList {
                if (transportTokens.any(text::contains)) add("${relative(file)} [transport-owner]")
                if (rawCommandPatterns.any { it.containsMatchIn(text) }) {
                    add("${relative(file)} [raw-command-owner]")
                }
            }
        }

        assertEquals(violations, emptyList<String>(), violations.joinToString("\n"))
    }

    @Test
    fun `feature modules do not depend on one another`() {
        val violations = repositoryRoot.resolve("feature")
            .walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { it.readText().contains("project(\":feature:") }
            .map { "${relative(it)} [horizontal-feature-dependency]" }
            .toList()

        assertEquals(violations, emptyList<String>(), violations.joinToString("\n"))
    }

    @Test
    fun `core ui and app retain only their approved v1 ownership`() {
        val coreUiFiles = kotlinSources(listOf(repositoryRoot.resolve("core/ui/src/main")))
        val coreUiViolations = coreUiFiles.flatMap { file ->
            val source = file.readText()
            buildList {
                if (
                    listOf(
                        "import com.sheen.adb.core",
                        "import com.sheen.adb.data",
                        "import com.sheen.adb.feature",
                    ).any(source::contains)
                ) {
                    add("${relative(file)} [core-ui-business-dependency]")
                }
            }
        }
        val targetFeatureScreens = listOf(
            "feature/files/src/main",
            "feature/apps/src/main",
            "feature/processes/src/main",
            "feature/shell/src/main",
            "feature/logcat/src/main",
            "feature/devices/src/main",
            "feature/overview/src/main",
        ).map(repositoryRoot::resolve)
        val directErrorProse = kotlinSources(targetFeatureScreens)
            .filter { file ->
                val source = file.readText()
                source.contains(".userMessage") || source.contains(".nextStep")
            }
            .map { "${relative(it)} [direct-core-error-prose]" }
            .toList()

        assertEquals(coreUiViolations, emptyList<String>(), coreUiViolations.joinToString("\n"))
        assertTrue(
            directErrorProse.isEmpty(),
            directErrorProse.joinToString("\n"),
        )
    }

    private fun kotlinSources(roots: List<File>): List<File> =
        roots.filter(File::exists).flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && "/test/" !in it.invariantSeparatorsPath }
                .toList()
        }

    private fun relative(file: File): String =
        file.canonicalFile.relativeTo(repositoryRoot).invariantSeparatorsPath
}
