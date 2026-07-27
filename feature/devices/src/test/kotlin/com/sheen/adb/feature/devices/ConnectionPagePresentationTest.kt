package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import java.io.File
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ConnectionPagePresentationTest {
    private val screen =
        File("src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt")

    @Test
    fun `empty and malformed endpoints are rejected before connection`() {
        listOf(
            "",
            "   ",
            "192.0.2.10",
            "192.0.2.10:",
            "192.0.2.10:not-a-port",
            "2001:db8::10:5555",
        ).forEach { input ->
            assertTrue(
                ConnectionPagePresentation.validateEndpoint(input) is ConnectionEndpointValidation.Invalid,
                "Expected invalid endpoint: $input",
            )
        }
    }

    @Test
    fun `IPv4 IPv6 and inclusive port boundaries are accepted`() {
        assertEquals(
            ConnectionPagePresentation.validateEndpoint("192.0.2.10:1"),
            ConnectionEndpointValidation.Valid(AdbEndpoint("192.0.2.10", 1)),
        )
        assertEquals(
            ConnectionPagePresentation.validateEndpoint("192.0.2.10:65535"),
            ConnectionEndpointValidation.Valid(AdbEndpoint("192.0.2.10", 65535)),
        )
        assertEquals(
            ConnectionPagePresentation.validateEndpoint("[2001:db8::10]:5555"),
            ConnectionEndpointValidation.Valid(AdbEndpoint("2001:db8::10", 5555)),
        )
    }

    @Test
    fun `ports outside the valid range are rejected`() {
        listOf(
            "192.0.2.10:0",
            "192.0.2.10:65536",
            "[2001:db8::10]:0",
            "[2001:db8::10]:65536",
        ).forEach { input ->
            assertEquals(
                ConnectionPagePresentation.validateEndpoint(input),
                ConnectionEndpointValidation.Invalid(ConnectionEndpointInputError.PORT_OUT_OF_RANGE),
            )
        }
    }

    @Test
    fun `connection lifecycle maps to explicit page phases`() {
        val endpoint = AdbEndpoint("synthetic.invalid", 4711)
        val failure = AdbError.Timeout(AdbOperationStage.CONNECT)

        val connecting = ConnectionPagePresentation.from(AdbConnectionState.Connecting(endpoint))
        assertEquals(connecting.phase, ConnectionPagePhase.CONNECTING)
        assertEquals(connecting.endpoint, endpoint)
        assertNull(connecting.sessionId)

        val failed = ConnectionPagePresentation.from(
            AdbConnectionState.Error(failure, "sanitized"),
        )
        assertEquals(failed.phase, ConnectionPagePhase.FAILED)
        assertSame(failed.error, failure)
        assertNull(failed.sessionId)

        val connected = ConnectionPagePresentation.from(
            AdbConnectionState.Connected(endpoint, "session-new"),
        )
        assertEquals(connected.phase, ConnectionPagePhase.CONNECTED)
        assertEquals(connected.endpoint, endpoint)
        assertEquals(connected.sessionId, "session-new")

        val disconnecting = ConnectionPagePresentation.from(AdbConnectionState.Disconnecting)
        assertEquals(disconnecting.phase, ConnectionPagePhase.DISCONNECTING)
        assertNull(disconnecting.sessionId)
    }

    @Test
    fun `disconnected state clears endpoint error and stale session identity`() {
        val endpoint = AdbEndpoint("synthetic.invalid", 4711)
        val oldPresentation = ConnectionPagePresentation.from(
            AdbConnectionState.Connected(endpoint, "session-old"),
        )

        val disconnected = ConnectionPagePresentation.from(
            state = AdbConnectionState.Disconnected(),
            previous = oldPresentation,
        )

        assertEquals(disconnected.phase, ConnectionPagePhase.DISCONNECTED)
        assertNull(disconnected.endpoint)
        assertNull(disconnected.sessionId)
        assertNull(disconnected.error)
    }

    @Test
    fun `connection error exposes a close action owned by presentation only`() {
        val source = screen.readText()
        val errorCard = source.substringAfter("private fun CompactConnectionError")
            .substringBefore("\nprivate fun ", missingDelimiterValue = source)

        assertTrue(errorCard.contains("IconButton"), "error card must expose a dedicated icon action")
        assertTrue(errorCard.contains("SheenIcons.Close"), "error card close affordance must use the close icon")
        assertTrue(
            errorCard.contains("onDismiss"),
            "error dismissal must be a presentation callback instead of mutating connection input, discovery, or Session",
        )
        assertFalse(
            errorCard.contains("cancelCurrentOperation") ||
                errorCard.contains("disconnect(") ||
                errorCard.contains("updateEndpoint(") ||
                errorCard.contains("refreshDiscovery("),
            "closing the error must not alter input, discovery, or Session ownership",
        )
    }

    @Test
    fun `connection error details open a visible redacted dialog before explicit copy`() {
        val source = screen.readText()
        val disconnected = source.substringAfter("private fun DisconnectedContent")
            .substringBefore("\n@Composable\nprivate fun DismissibleInputError", missingDelimiterValue = source)
        val errorCard = source.substringAfter("private fun CompactConnectionError")
            .substringBefore("\n@Composable\nprivate fun PairingActionButtons", missingDelimiterValue = source)

        assertTrue(disconnected.contains("showConnectionErrorDetails"))
        assertTrue(disconnected.contains("AlertDialog"))
        assertTrue(disconnected.contains(".technicalDetails"))
        assertTrue(errorCard.contains("onShowDetails"))
        assertFalse(
            errorCard.contains("copy(context"),
            "Details must display an in-app dialog instead of silently copying to the clipboard",
        )
    }

    @Test
    fun `connection failure does not render a duplicate input error card`() {
        val connectionError = AdbConnectionState.Error(
            error = AdbError.DeviceRejected(AdbOperationStage.CONNECT),
            technicalDetails = "redacted details",
        )

        assertNull(
            ConnectionPagePresentation.visibleInputError(
                inputError = connectionError.error.technicalCode,
                connection = connectionError,
            ),
        )
        assertEquals(
            ConnectionPagePresentation.visibleInputError(
                inputError = "配对码必须是 6 位数字",
                connection = connectionError,
            ),
            "配对码必须是 6 位数字",
        )
    }

    @Test
    fun `input and discovery errors also expose a close action`() {
        val source = screen.readText()

        assertTrue(source.contains("dismissedInputError"))
        assertTrue(source.contains("DismissibleInputError"))
        val inputError = source.substringAfter("private fun DismissibleInputError")
            .substringBefore("\nprivate fun ", missingDelimiterValue = source)
        assertTrue(inputError.contains("IconButton"))
        assertTrue(inputError.contains("SheenIcons.Close"))
        assertTrue(inputError.contains("onDismiss"))
    }

    @Test
    fun `disconnected page represents every required semantic state without overview ownership`() {
        val source = screen.readText()

        listOf(
            "Loading",
            "Content",
            "Empty",
            "Error",
            "Cancelled",
            "Disconnected",
            "Unsupported",
        ).forEach { token ->
            assertTrue(source.contains(token), "missing disconnected connection-page state $token")
        }
        assertFalse(source.contains("Screenshot"), "Devices must not own screenshot presentation")
        assertFalse(source.contains("ScreenRecord"), "Devices must not own screen-recording presentation")
    }

    @Test
    fun `hostile device label and technical code are bounded isolated safe verbatim displays`() {
        val rawDeviceLabel = "device\u0000\u202E\n" + "名称".repeat(100)
        val rawTechnicalCode = "ADB\u0085_FAILURE\u202D_" + "X".repeat(200)
        val deviceIdentity = rawDeviceLabel.toCharArray().copyOf()
        val technicalIdentity = rawTechnicalCode.toCharArray().copyOf()

        val deviceDisplay = SafeVerbatimText.render(
            rawDeviceLabel,
            SafeVerbatimPolicy.SingleLine(maxCodePoints = 96),
        )
        val technicalDisplay = SafeVerbatimText.render(
            rawTechnicalCode,
            SafeVerbatimPolicy.SingleLine(maxCodePoints = 64),
        )

        assertEquals(rawDeviceLabel.toCharArray().toList(), deviceIdentity.toList())
        assertEquals(rawTechnicalCode.toCharArray().toList(), technicalIdentity.toList())
        assertTrue(deviceDisplay.truncated)
        assertTrue(technicalDisplay.truncated)
        assertTrue(deviceDisplay.replacementCount >= 2)
        assertTrue(technicalDisplay.replacementCount >= 2)
        listOf(deviceDisplay.display, technicalDisplay.display).forEach { display ->
            assertTrue(display.startsWith("\u2066") && display.endsWith("\u2069"))
            assertFalse(display.contains('\n'))
            assertFalse(display.contains('\u202E'))
            assertFalse(display.contains('\u202D'))
        }

        val source = screen.readText()
        assertTrue(source.contains("SafeVerbatimText"))
        assertTrue(source.contains("SafeVerbatimPolicy.SingleLine"))
    }
}
