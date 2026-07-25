package com.sheen.adb.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNotNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QuickActionContractTest {
    @Test
    fun `all three requests bind the initiating Session and recording limits are bounded`() {
        val screenshot = ScreenshotCaptureRequest(
            expectedSessionId = "session-screenshot",
            timeout = 10.seconds,
        )
        val recording = ScreenRecordRequest(
            expectedSessionId = "session-record",
            timeout = 5.minutes,
            maxDuration = 5.minutes,
            maxBytes = 256L * 1024L * 1024L,
        )
        val reboot = RebootRequest(
            expectedSessionId = "session-reboot",
            timeout = 10.seconds,
        )

        assertEquals(screenshot.expectedSessionId, "session-screenshot")
        assertEquals(recording.expectedSessionId, "session-record")
        assertEquals(reboot.expectedSessionId, "session-reboot")
        assertEquals(recording.maxDuration, 5.minutes)
        assertEquals(recording.maxBytes, 256L * 1024L * 1024L)
    }

    @Test
    fun `capabilities are independent session-bound and structurally exhaustive`() {
        val capabilities = QuickActionCapabilities(
            expectedSessionId = "session-capabilities",
            screenshot = QuickActionCapability.Supported,
            screenRecord = QuickActionCapability.Unsupported("fixture-no-recorder"),
            reboot = QuickActionCapability.PolicyRejected("fixture-policy"),
        )

        assertEquals(capabilities.expectedSessionId, "session-capabilities")
        assertTrue(capabilities.screenshot is QuickActionCapability.Supported)
        assertTrue(capabilities.screenRecord is QuickActionCapability.Unsupported)
        assertTrue(capabilities.reboot is QuickActionCapability.PolicyRejected)
        assertNotNull(QuickActionCapability.ProbeFailed("fixture-probe"))
        assertNotNull(QuickActionCapability.Unknown)
    }

    @Test
    fun `capture metadata progress and terminal results use project owned values`() {
        val metadata = CaptureMetadata(
            expectedSessionId = "session-capture",
            kind = QuickActionKind.SCREENSHOT,
            bytesWritten = 128L,
            elapsed = 1.seconds,
            format = CaptureFormat.PNG,
        )
        val progress = QuickActionProgress(
            expectedSessionId = "session-capture",
            kind = QuickActionKind.SCREENSHOT,
            phase = QuickActionProgressPhase.CAPTURING,
            bytesWritten = 64L,
            elapsed = 1.seconds,
            maxBytes = null,
            maxDuration = 10.seconds,
        )
        val terminals: List<QuickActionResult<CaptureMetadata>> = listOf(
            QuickActionResult.Success(metadata),
            QuickActionResult.Failure(AdbError.PairingUnsupported),
            QuickActionResult.Cancelled,
            QuickActionResult.StaleSession("session-capture"),
            QuickActionResult.ResultUnknown("session-capture"),
        )

        assertEquals(metadata.expectedSessionId, progress.expectedSessionId)
        assertEquals(metadata.kind, progress.kind)
        assertEquals(terminals.size, 5)
    }

    @Test
    fun `manager exposes typed screenshot recording reboot and capability operations`() {
        val methods = AdbSessionManager::class.java.methods.associateBy { it.name.substringBefore('-') }

        assertTypedOperation(
            method = methods["quickActionCapabilities"],
            requiredParameter = String::class.java,
        )
        assertTypedOperation(
            method = methods["captureScreenshot"],
            requiredParameter = ScreenshotCaptureRequest::class.java,
            requiredSink = AdbCaptureSink::class.java,
        )
        assertTypedOperation(
            method = methods["recordScreen"],
            requiredParameter = ScreenRecordRequest::class.java,
            requiredSink = AdbCaptureSink::class.java,
        )
        assertTypedOperation(
            method = methods["stopScreenRecord"],
            requiredParameter = String::class.java,
        )
        assertTypedOperation(
            method = methods["reboot"],
            requiredParameter = RebootRequest::class.java,
        )
    }

    @Test
    fun `public quick action contract does not expose platform protocol or artifact types`() {
        val contractTypes = listOf(
            AdbCaptureSink::class.java,
            ScreenshotCaptureRequest::class.java,
            ScreenRecordRequest::class.java,
            RebootRequest::class.java,
            QuickActionCapabilities::class.java,
            QuickActionCapability::class.java,
            CaptureMetadata::class.java,
            QuickActionProgress::class.java,
            QuickActionResult::class.java,
        )
        val forbiddenTypeFragments = listOf(
            "com.sheen.adb.data",
            "java.io.File",
            "java.io.OutputStream",
            "java.net.URI",
            "java.net.Socket",
            "android.net.Uri",
            "android.content.ContentResolver",
            "com.flyfishxu",
            "kadb",
            "ShellResult",
        )
        val forbiddenMemberFragments = listOf(
            "remotePath",
            "remoteTemp",
            "shellCommand",
            "rawCommand",
            "artifactPath",
        )

        contractTypes.forEach { type ->
            val signatures = buildList {
                add(type.name)
                type.declaredFields.forEach { add(it.genericType.render()) }
                type.declaredMethods.forEach { method ->
                    add(method.genericReturnType.render())
                    method.genericParameterTypes.forEach { add(it.render()) }
                }
            }.joinToString(" ")
            forbiddenTypeFragments.forEach { forbidden ->
                assertFalse(
                    signatures.contains(forbidden, ignoreCase = true),
                    "${type.name} leaks forbidden type $forbidden",
                )
            }
            (type.declaredFields.map { it.name } + type.declaredMethods.map { it.name })
                .forEach { member ->
                    forbiddenMemberFragments.forEach { forbidden ->
                        assertFalse(
                            member.contains(forbidden, ignoreCase = true),
                            "${type.name} exposes forbidden member $member",
                        )
                    }
                }
        }
    }

    private fun assertTypedOperation(
        method: java.lang.reflect.Method?,
        requiredParameter: Class<*>,
        requiredSink: Class<*>? = null,
    ) {
        assertNotNull(method)
        val parameters = method!!.parameterTypes.toList()
        assertTrue(parameters.contains(requiredParameter), method.toGenericString())
        if (requiredSink != null) {
            assertTrue(parameters.contains(requiredSink), method.toGenericString())
        }
        val signature = method.toGenericString()
        assertTrue(signature.contains("QuickAction"), signature)
    }

    private fun Type.render(): String = when (this) {
        is ParameterizedType -> buildString {
            append(rawType.typeName)
            actualTypeArguments.joinTo(
                buffer = this,
                prefix = "<",
                postfix = ">",
                transform = { it.render() },
            )
        }
        else -> typeName
    }
}
