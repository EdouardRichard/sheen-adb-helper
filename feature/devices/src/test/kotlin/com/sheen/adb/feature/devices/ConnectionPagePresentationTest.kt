package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.AdbOperationStage
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ConnectionPagePresentationTest {
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
}
