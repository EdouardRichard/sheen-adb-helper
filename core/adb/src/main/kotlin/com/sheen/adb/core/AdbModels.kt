package com.sheen.adb.core

import kotlin.time.Duration

enum class AdbOperationStage {
    ADDRESS,
    CONNECT,
    AUTHENTICATE,
    PAIR,
    SHELL,
    OVERVIEW,
    PROCESSES,
    LOGCAT,
    APPLICATIONS_LIST,
    APPLICATION_FORCE_STOP,
    APPLICATION_SET_ENABLED,
    APPLICATION_VERIFY,
    DISCONNECT,
}

enum class AdbDiagnosticOutcome {
    STARTED,
    SUCCEEDED,
    CANCELLED,
    FAILED,
    RESOURCE_CLOSED,
}

data class AdbDiagnosticEvent(
    val sequence: Long,
    val stage: AdbOperationStage,
    val outcome: AdbDiagnosticOutcome,
    val technicalCode: String,
    val redactedTarget: String,
    val causeType: String? = null,
)

sealed interface AdbError {
    val stage: AdbOperationStage
    val userMessage: String
    val nextStep: String
    val technicalCode: String
    val allowsPairingFallback: Boolean get() = false

    data class InvalidAddress(override val userMessage: String) : AdbError {
        override val stage = AdbOperationStage.ADDRESS
        override val nextStep = "检查地址格式；IPv6 请使用 [地址]:端口。"
        override val technicalCode = "ADB_ADDRESS_INVALID"
    }

    data class NetworkUnreachable(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "目标网络或端口不可达。"
        override val nextStep =
            "确认两台设备在同一局域网、无线调试仍开启，并使用无线调试主页面显示的调试端口；Android 11+ 请勿默认填写 5555。"
        override val technicalCode = "ADB_NETWORK_UNREACHABLE"
    }

    data class Timeout(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "操作已超时。"
        override val nextStep = "检查无线调试页面上的端口是否已变化，然后重试。"
        override val technicalCode = "ADB_TIMEOUT"
    }

    data class AuthenticationFailed(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "ADB 身份未获信任或 TLS 认证失败。"
        override val nextStep = "首次连接可使用系统无线调试页面显示的配对码；已配对设备请移除旧记录后重试。"
        override val technicalCode = "ADB_AUTH_FAILED"
        override val allowsPairingFallback = true
    }

    data class DeviceRejected(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "设备拒绝了本次 ADB 操作。"
        override val nextStep = "在被控设备上确认授权提示，或重新打开无线调试后重试。"
        override val technicalCode = "ADB_DEVICE_REJECTED"
    }

    data class ProtocolIncompatible(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "目标端口不是兼容的 ADB 服务。"
        override val nextStep = "确认使用的是调试端口而非配对端口；旧式设备需先由用户启用 ADB TCP/IP。"
        override val technicalCode = "ADB_PROTOCOL_INCOMPATIBLE"
    }

    data class RemoteClosed(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "ADB 会话已被远端关闭。"
        override val nextStep = "返回系统无线调试页面确认端口，然后重新连接。"
        override val technicalCode = "ADB_REMOTE_CLOSED"
    }

    data class CommandStreamClosed(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "本次 ADB 命令流在完成前已关闭。"
        override val nextStep = "活动连接仍会保留；请直接重试命令，若持续出现再重新连接。"
        override val technicalCode = "ADB_COMMAND_STREAM_CLOSED"
    }

    data class IoFailure(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "本次 ADB 子流发生输入输出错误。"
        override val nextStep = "活动连接仍会保留；可直接重试，若设备已离线再重新连接。"
        override val technicalCode = "ADB_IO_FAILURE"
    }

    data object ApplicationCurrentUserUnavailable : AdbError {
        override val stage = AdbOperationStage.APPLICATIONS_LIST
        override val userMessage = "无法确认被控端当前 Android 用户。"
        override val nextStep = "检查 ROM 是否支持当前用户查询；不会回退到其他用户。"
        override val technicalCode = "ADB_APP_CURRENT_USER_UNAVAILABLE"
    }

    data object ApplicationListUnsupported : AdbError {
        override val stage = AdbOperationStage.APPLICATIONS_LIST
        override val userMessage = "ROM 未提供可安全解析的第三方应用列表。"
        override val nextStep = "其他调试功能仍可使用；可重连后重试应用列表。"
        override val technicalCode = "ADB_APP_LIST_UNSUPPORTED"
    }

    data object ApplicationListCapacityExceeded : AdbError {
        override val stage = AdbOperationStage.APPLICATIONS_LIST
        override val userMessage = "应用列表超过 20,000 项安全上限。"
        override val nextStep = "不会加载部分列表；请检查被控端包管理服务后重试。"
        override val technicalCode = "ADB_APP_LIST_CAPACITY_EXCEEDED"
    }

    data class ApplicationPackageNotFound(
        override val stage: AdbOperationStage,
    ) : AdbError {
        override val userMessage = "目标应用已不存在或不再属于当前列表。"
        override val nextStep = "刷新应用列表后，以设备当前状态为准。"
        override val technicalCode = "ADB_APP_PACKAGE_NOT_FOUND"
    }

    data class ApplicationTargetNotAllowed(
        override val stage: AdbOperationStage,
    ) : AdbError {
        override val userMessage = "该应用不在允许操作的当前用户第三方应用范围内。"
        override val nextStep = "系统应用、本机 Sheen 自身、未知状态或范围外目标不可绕过限制。"
        override val technicalCode = "ADB_APP_TARGET_NOT_ALLOWED"
    }

    data class ApplicationPolicyRejected(
        override val stage: AdbOperationStage,
    ) : AdbError {
        override val userMessage = "ROM 或设备管理策略拒绝了应用操作。"
        override val nextStep = "在被控端检查企业管理或系统策略，然后刷新确认实际状态。"
        override val technicalCode = "ADB_APP_POLICY_REJECTED"
    }

    data object ApplicationStateVerifyFailed : AdbError {
        override val stage = AdbOperationStage.APPLICATION_VERIFY
        override val userMessage = "操作后读取到的应用状态与目标状态不一致。"
        override val nextStep = "刷新列表并以设备实际状态为准。"
        override val technicalCode = "ADB_APP_STATE_VERIFY_FAILED"
    }

    data class ApplicationOutcomeUnknown(
        override val stage: AdbOperationStage,
    ) : AdbError {
        override val userMessage = "连接中断、超时或取消，无法确认设备是否已执行操作。"
        override val nextStep = "重新连接并刷新列表，以设备实际状态为准。"
        override val technicalCode = "ADB_APP_OUTCOME_UNKNOWN"
    }

    data class ApplicationSessionInvalid(
        override val stage: AdbOperationStage,
    ) : AdbError {
        override val userMessage = "发起操作的 ADB 会话已失效或已切换。"
        override val nextStep = "返回当前设备的应用列表并重新发起操作。"
        override val technicalCode = "ADB_APP_SESSION_INVALID"
    }

    data class Unknown(override val stage: AdbOperationStage) : AdbError {
        override val userMessage = "发生未识别的 ADB 错误。"
        override val nextStep = "断开后重试；若持续出现，请复制脱敏技术详情用于排查。"
        override val technicalCode = "ADB_UNKNOWN"
    }
}

sealed interface AdbConnectionState {
    data class Disconnected(
        val reason: DisconnectionReason = DisconnectionReason.NONE,
    ) : AdbConnectionState
    data class Connecting(val endpoint: AdbEndpoint) : AdbConnectionState
    data class AwaitingAuthorization(val endpoint: AdbEndpoint) : AdbConnectionState
    data class Connected(val endpoint: AdbEndpoint, val sessionId: String) : AdbConnectionState
    data class Pairing(val endpoint: AdbEndpoint) : AdbConnectionState
    data object Disconnecting : AdbConnectionState
    data class Error(val error: AdbError, val technicalDetails: String) : AdbConnectionState
}

enum class DisconnectionReason {
    NONE,
    CONNECT_CANCELLED,
    PAIR_CANCELLED,
    SHELL_CANCELLED,
    DISCONNECT_CANCELLED,
}

sealed interface AdbOperationResult<out T> {
    data class Success<T>(val value: T) : AdbOperationResult<T>
    data class Failure(val error: AdbError) : AdbOperationResult<Nothing>
    data object Cancelled : AdbOperationResult<Nothing>
}

data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val elapsed: Duration,
    val outputMode: ShellOutputMode = ShellOutputMode.SEPARATED,
    val wasTruncated: Boolean = false,
)

enum class ShellOutputMode { SEPARATED, MERGED }

data class DeviceOverview(
    val brand: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val deviceCode: String? = null,
    val androidVersion: String? = null,
    val sdk: String? = null,
    val buildDisplay: String? = null,
    val buildFingerprint: String? = null,
    val securityPatch: String? = null,
    val cpuAbi: String? = null,
    val availableCores: Int? = null,
    val memoryTotalBytes: Long? = null,
    val memoryAvailableBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val storageAvailableBytes: Long? = null,
    val batteryPercent: Int? = null,
    val chargingState: String? = null,
    val temperatureCelsius: Double? = null,
    val uptimeSeconds: Long? = null,
    val networkAddresses: List<String> = emptyList(),
)

data class DynamicDeviceMetrics(
    val memoryTotalBytes: Long? = null,
    val memoryAvailableBytes: Long? = null,
    val batteryPercent: Int? = null,
    val chargingState: String? = null,
    val temperatureCelsius: Double? = null,
    val uptimeSeconds: Long? = null,
)

data class DeviceProcess(
    val name: String,
    val pid: Int,
    val uid: String? = null,
    val state: String? = null,
    val residentMemoryBytes: Long? = null,
)

data class ProcessSnapshot(
    val processes: List<DeviceProcess>,
    val degradedReason: String? = null,
)

enum class RemoteApplicationEnabledState {
    ENABLED,
    DISABLED,
    UNKNOWN,
}

enum class ApplicationField {
    VERSION_CODE,
    VERSION_NAME,
    INSTALLER_PACKAGE,
}

data class RemoteApplication(
    val packageName: String,
    val userId: Int,
    val enabledState: RemoteApplicationEnabledState,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val installerPackage: String? = null,
    val isSystem: Boolean,
)

data class ApplicationSnapshot(
    val sessionId: String,
    val userId: Int,
    val applications: List<RemoteApplication>,
    val unavailableFields: Set<ApplicationField>,
    val degradedReason: String? = null,
)

sealed interface ApplicationMutationResult {
    val sessionId: String

    data class Verified(
        override val sessionId: String,
        val application: RemoteApplication,
    ) : ApplicationMutationResult

    data class RequestAccepted(override val sessionId: String) : ApplicationMutationResult

    data class OutcomeUnknown(
        override val sessionId: String,
        val reason: AdbError,
    ) : ApplicationMutationResult
}

enum class LogcatLevel(val argument: String) {
    VERBOSE("V"), DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), FATAL("F")
}

enum class LogcatBuffer(val argument: String) {
    MAIN("main"), SYSTEM("system"), CRASH("crash"), RADIO("radio"), EVENTS("events")
}

data class LogcatConfig(
    val minimumLevel: LogcatLevel = LogcatLevel.INFO,
    val buffers: Set<LogcatBuffer> = setOf(LogcatBuffer.MAIN, LogcatBuffer.SYSTEM, LogcatBuffer.CRASH),
) {
    init { require(buffers.isNotEmpty()) }
}

data class LogcatLine(val text: String, val fromStandardError: Boolean = false)
