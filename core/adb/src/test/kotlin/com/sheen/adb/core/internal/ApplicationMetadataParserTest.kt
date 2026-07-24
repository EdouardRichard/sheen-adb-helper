package com.sheen.adb.core.internal

import com.sheen.adb.core.internal.applications.ApplicationMetadataParseFailure
import com.sheen.adb.core.internal.applications.ApplicationMetadataParseResult
import com.sheen.adb.core.internal.applications.ApplicationMetadataParser
import com.sheen.adb.core.internal.applications.DecodedApkMetadata
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ApplicationMetadataParserTest {
    @Test
    fun `selects preferred locale label without reading icons`() {
        val apk = syntheticApk()
        val parser = ApplicationMetadataParser { _, locale ->
            DecodedApkMetadata(
                packageName = "com.example.fixture",
                splitName = null,
                label = if (locale.language == "zh") "示例应用" else "Example",
            )
        }

        val result = parser.parse(apk, preferredLocaleTags = listOf("zh-CN", "en-US"))

        assertTrue(result is ApplicationMetadataParseResult.Success)
        val metadata = (result as ApplicationMetadataParseResult.Success).metadata
        assertEquals(metadata.displayName, "示例应用")
    }

    @Test
    fun `label parser ignores icon resources`() {
        val parser = ApplicationMetadataParser { _, _ ->
            DecodedApkMetadata(
                packageName = "com.example.fixture",
                splitName = null,
                label = "Example",
            )
        }

        val result = parser.parse(syntheticApk(), listOf("en-US")) as ApplicationMetadataParseResult.Success

    }

    @Test
    fun `missing label and icon resources remain a successful nullable degradation`() {
        val parser = ApplicationMetadataParser { _, _ ->
            DecodedApkMetadata(
                packageName = "com.example.fixture",
                splitName = null,
                label = null,
            )
        }

        val result = parser.parse(syntheticApk(), listOf("en-US")) as ApplicationMetadataParseResult.Success

        assertNull(result.metadata.displayName)
    }

    @Test
    fun `rejects corrupt zip before invoking third party decoder`() {
        val invoked = AtomicBoolean(false)
        val parser = ApplicationMetadataParser { _, _ ->
            invoked.set(true)
            error("must not decode")
        }

        val result = parser.parse("not-an-apk".toByteArray(), listOf("en-US"))

        assertFailure(result, ApplicationMetadataParseFailure.MALFORMED_ARCHIVE)
        assertFalse(invoked.get())
    }

    @Test
    fun `rejects zip traversal before invoking third party decoder`() {
        val invoked = AtomicBoolean(false)
        val parser = ApplicationMetadataParser { _, _ ->
            invoked.set(true)
            error("must not decode")
        }

        val result = parser.parse(zipOf("../escaped" to byteArrayOf(1)), listOf("en-US"))

        assertFailure(result, ApplicationMetadataParseFailure.UNSAFE_ARCHIVE)
        assertFalse(invoked.get())
    }

    @Test
    fun `rejects suspicious compression ratio before decoding`() {
        val invoked = AtomicBoolean(false)
        val parser = ApplicationMetadataParser { _, _ ->
            invoked.set(true)
            error("must not decode")
        }
        val compressedBomb = zipOf("res/raw/payload.bin" to ByteArray(2 * 1024 * 1024))

        val result = parser.parse(compressedBomb, listOf("en-US"))

        assertFailure(result, ApplicationMetadataParseFailure.UNSAFE_ARCHIVE)
        assertFalse(invoked.get())
    }

    @Test
    fun `rejects split apk instead of treating it as a base apk`() {
        val parser = ApplicationMetadataParser { _, _ ->
            DecodedApkMetadata(
                packageName = "com.example.fixture",
                splitName = "config.en",
                label = "Example",
            )
        }

        val result = parser.parse(syntheticApk(), listOf("en-US"))

        assertFailure(result, ApplicationMetadataParseFailure.SPLIT_APK_UNSUPPORTED)
    }

    @Test
    fun `rejects apk bytes above the 32 MiB input limit`() {
        val invoked = AtomicBoolean(false)
        val parser = ApplicationMetadataParser { _, _ ->
            invoked.set(true)
            error("must not decode")
        }

        val result = parser.parse(ByteArray(32 * 1024 * 1024 + 1), listOf("en-US"))

        assertFailure(result, ApplicationMetadataParseFailure.APK_TOO_LARGE)
        assertFalse(invoked.get())
    }

    private fun assertFailure(result: ApplicationMetadataParseResult, expected: ApplicationMetadataParseFailure) {
        assertTrue(result is ApplicationMetadataParseResult.Failure)
        assertEquals((result as ApplicationMetadataParseResult.Failure).reason, expected)
    }

    private fun syntheticApk(): ByteArray = zipOf(
        "AndroidManifest.xml" to byteArrayOf(3, 0, 8, 0),
        "resources.arsc" to byteArrayOf(2, 0, 12, 0),
        "res/mipmap/icon.png" to PNG_1X1,
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private companion object {
        val PNG_1X1: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}
