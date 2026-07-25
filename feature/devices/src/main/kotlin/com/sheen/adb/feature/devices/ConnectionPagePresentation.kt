package com.sheen.adb.feature.devices

import com.sheen.adb.core.AdbConnectionState
import com.sheen.adb.core.AdbEndpoint
import com.sheen.adb.core.AdbEndpointParser
import com.sheen.adb.core.AdbError
import com.sheen.adb.core.EndpointParseResult

enum class ConnectionEndpointInputError {
    EMPTY,
    MALFORMED,
    PORT_OUT_OF_RANGE,
}

sealed interface ConnectionEndpointValidation {
    data class Valid(val endpoint: AdbEndpoint) : ConnectionEndpointValidation
    data class Invalid(val error: ConnectionEndpointInputError) : ConnectionEndpointValidation
}

enum class ConnectionPagePhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED,
}

data class ConnectionPageState(
    val phase: ConnectionPagePhase,
    val endpoint: AdbEndpoint? = null,
    val sessionId: String? = null,
    val error: AdbError? = null,
)

object ConnectionPagePresentation {
    fun validateEndpoint(raw: String): ConnectionEndpointValidation {
        if (raw.isBlank()) {
            return ConnectionEndpointValidation.Invalid(ConnectionEndpointInputError.EMPTY)
        }
        val port = raw.trim().portTextOrNull()?.toIntOrNull()
        if (port != null && port !in 1..65535) {
            return ConnectionEndpointValidation.Invalid(ConnectionEndpointInputError.PORT_OUT_OF_RANGE)
        }
        return when (val parsed = AdbEndpointParser.parse(raw)) {
            is EndpointParseResult.Valid -> ConnectionEndpointValidation.Valid(parsed.endpoint)
            is EndpointParseResult.Invalid -> ConnectionEndpointValidation.Invalid(ConnectionEndpointInputError.MALFORMED)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun from(
        state: AdbConnectionState,
        previous: ConnectionPageState? = null,
    ): ConnectionPageState = when (state) {
        is AdbConnectionState.Disconnected -> ConnectionPageState(ConnectionPagePhase.DISCONNECTED)
        is AdbConnectionState.Connecting -> ConnectionPageState(
            phase = ConnectionPagePhase.CONNECTING,
            endpoint = state.endpoint,
        )
        is AdbConnectionState.AwaitingAuthorization -> ConnectionPageState(
            phase = ConnectionPagePhase.CONNECTING,
            endpoint = state.endpoint,
        )
        is AdbConnectionState.Pairing -> ConnectionPageState(
            phase = ConnectionPagePhase.CONNECTING,
            endpoint = state.endpoint,
        )
        is AdbConnectionState.Connected -> ConnectionPageState(
            phase = ConnectionPagePhase.CONNECTED,
            endpoint = state.endpoint,
            sessionId = state.sessionId,
        )
        AdbConnectionState.Disconnecting -> ConnectionPageState(ConnectionPagePhase.DISCONNECTING)
        is AdbConnectionState.Error -> ConnectionPageState(
            phase = ConnectionPagePhase.FAILED,
            error = state.error,
        )
    }

    private fun String.portTextOrNull(): String? {
        val portText = when {
            startsWith('[') -> substringAfter("]:", missingDelimiterValue = "")
            count { it == ':' } == 1 -> substringAfter(':')
            else -> return null
        }
        return portText.ifBlank { null }
    }
}
