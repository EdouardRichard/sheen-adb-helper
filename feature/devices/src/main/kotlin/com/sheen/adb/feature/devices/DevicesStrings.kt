package com.sheen.adb.feature.devices

import com.sheen.adb.ui.LocalizedTextRef
import com.sheen.adb.ui.SafeVerbatimPolicy
import com.sheen.adb.ui.SafeVerbatimText
import com.sheen.adb.ui.TextArgumentType
import com.sheen.adb.ui.TypedTextArgument
import com.sheen.adb.ui.UiLanguage

enum class DevicesStringKey(
    val semanticKey: String,
    val placeholderSchema: Map<String, TextArgumentType> = emptyMap(),
) {
    TITLE("devices.title"),
    DISCOVERY_SCANNING("devices.discovery.scanning"),
    DISCOVERY_EMPTY("devices.discovery.empty"),
    DISCOVERY_ERROR("devices.discovery.error"),
    CONNECTION_ERROR("devices.connection.error"),
    DISMISS_ERROR("devices.connection.dismiss_error"),

    PAIRING_QR_TITLE("devices.pairing.qr.title"),
    PAIRING_QR_INSTRUCTION("devices.pairing.qr.instruction"),
    SWITCH_TO_PAIRING_CODE("devices.pairing.code.switch"),
    PAIRING_CODE_TITLE("devices.pairing.code.title"),
    LOCAL_PAIRING_TITLE("devices.pairing.local.title"),
    LOCAL_PAIRING_INSTRUCTION("devices.pairing.local.instruction"),
    PAIRING_PORT_SCANNING("devices.pairing.port.scanning"),
    PAIRING_PORT_FOUND("devices.pairing.port.found"),
    PAIRING_CODE_PLACEHOLDER("devices.pairing.code.placeholder"),
    PAIR("devices.pairing.submit"),
    PAIRING_SUBMITTING("devices.pairing.submitting"),
    PAIRING_SUCCEEDED("devices.pairing.succeeded"),
    PAIRING_SUCCEEDED_DETAIL("devices.pairing.succeeded_detail"),
    PAIRING_FAILED("devices.pairing.failed"),
    PAIRING_FAILED_RETRY("devices.pairing.failed_retry"),
    PAIRING_INVALID_CODE("devices.pairing.invalid_code"),
    PAIRING_UNSUPPORTED_QR("devices.pairing.unsupported_qr"),
    PAIRING_CANCELLED("devices.pairing.cancelled"),
    LOCAL_PAIRING_CANCELLED("devices.pairing.local.cancelled"),
    PAIRING_EXPIRED("devices.pairing.expired"),
    LOCAL_PAIRING_EXPIRED("devices.pairing.local.expired"),
    PAIRING_PORT_NOT_FOUND("devices.pairing.port.not_found"),
    RETRY_PAIRING_SCAN("devices.pairing.port.retry"),
    PAIRING_CHOOSE_METHOD("devices.pairing.choose_method"),
    PAIRING_CONFIRMATION("devices.pairing.confirmation"),
    SESSION_REPLACEMENT_CONFIRMATION("devices.pairing.session_replacement_confirmation"),
    CANCEL_PAIRING("devices.pairing.cancel"),
    CLOSE_PAIRING("devices.pairing.close"),

    DEVICE_ROW_CONTENT_DESCRIPTION("devices.accessibility.device_row"),
    DISMISS_ERROR_CONTENT_DESCRIPTION("devices.accessibility.dismiss_error"),
    PAIRING_OVERLAY_CONTENT_DESCRIPTION("devices.accessibility.pairing_overlay"),
    PAIR_CONTENT_DESCRIPTION("devices.accessibility.pair"),
    RETRY_CONTENT_DESCRIPTION("devices.accessibility.retry"),

    VERBATIM_ENDPOINT(
        "devices.verbatim.endpoint",
        mapOf("endpoint" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_PAIRING_CODE(
        "devices.verbatim.pairing_code",
        mapOf("pairingCode" to TextArgumentType.VERBATIM),
    ),
    VERBATIM_TECHNICAL_CODE(
        "devices.verbatim.technical_code",
        mapOf("technicalCode" to TextArgumentType.VERBATIM),
    ),
}

object DevicesStrings {
    const val OWNER = "devices"

    private val catalogs: Map<UiLanguage, Map<DevicesStringKey, String>> = mapOf(
        UiLanguage.ZH_CN to mapOf(
            DevicesStringKey.TITLE to "连接",
            DevicesStringKey.DISCOVERY_SCANNING to "正在扫描设备",
            DevicesStringKey.DISCOVERY_EMPTY to "未发现可连接设备",
            DevicesStringKey.DISCOVERY_ERROR to "设备扫描失败",
            DevicesStringKey.CONNECTION_ERROR to "连接失败",
            DevicesStringKey.DISMISS_ERROR to "关闭错误提示",
            DevicesStringKey.PAIRING_QR_TITLE to "二维码配对",
            DevicesStringKey.PAIRING_QR_INSTRUCTION to
                "在被控端打开系统“无线调试”，由被控端系统扫描这里显示的临时二维码。",
            DevicesStringKey.SWITCH_TO_PAIRING_CODE to "切换配对码配对",
            DevicesStringKey.PAIRING_CODE_TITLE to "配对码配对",
            DevicesStringKey.LOCAL_PAIRING_TITLE to "本机配对",
            DevicesStringKey.LOCAL_PAIRING_INSTRUCTION to
                "请开启无线调试。已检测到配对端口，请输入配对码：",
            DevicesStringKey.PAIRING_PORT_SCANNING to "正在扫描配对端口",
            DevicesStringKey.PAIRING_PORT_FOUND to "已发现配对端口",
            DevicesStringKey.PAIRING_CODE_PLACEHOLDER to "请输入六位配对码",
            DevicesStringKey.PAIR to "配对",
            DevicesStringKey.PAIRING_SUBMITTING to "正在配对",
            DevicesStringKey.PAIRING_SUCCEEDED to "配对成功",
            DevicesStringKey.PAIRING_SUCCEEDED_DETAIL to
                "配对成功，授权已建立；连接设备仍需用户确认",
            DevicesStringKey.PAIRING_FAILED to "配对失败",
            DevicesStringKey.PAIRING_FAILED_RETRY to "配对失败，请重试",
            DevicesStringKey.PAIRING_INVALID_CODE to "配对码必须是 6 位数字",
            DevicesStringKey.PAIRING_UNSUPPORTED_QR to "当前系统不支持二维码配对",
            DevicesStringKey.PAIRING_CANCELLED to "配对已取消",
            DevicesStringKey.LOCAL_PAIRING_CANCELLED to "本机配对已取消",
            DevicesStringKey.PAIRING_EXPIRED to "配对已过期，请重新开始",
            DevicesStringKey.LOCAL_PAIRING_EXPIRED to "本机配对已超时，请重新开始",
            DevicesStringKey.PAIRING_PORT_NOT_FOUND to "未发现配对端口",
            DevicesStringKey.RETRY_PAIRING_SCAN to "重新扫描",
            DevicesStringKey.PAIRING_CHOOSE_METHOD to "请选择配对方式",
            DevicesStringKey.PAIRING_CONFIRMATION to "确认与此设备配对？",
            DevicesStringKey.SESSION_REPLACEMENT_CONFIRMATION to
                "开始新配对前必须先断开当前 ADB Session。是否继续？",
            DevicesStringKey.CANCEL_PAIRING to "取消配对",
            DevicesStringKey.CLOSE_PAIRING to "关闭配对窗口",
            DevicesStringKey.DEVICE_ROW_CONTENT_DESCRIPTION to "选择发现的设备",
            DevicesStringKey.DISMISS_ERROR_CONTENT_DESCRIPTION to "关闭连接错误提示",
            DevicesStringKey.PAIRING_OVERLAY_CONTENT_DESCRIPTION to "设备配对窗口",
            DevicesStringKey.PAIR_CONTENT_DESCRIPTION to "提交六位配对码",
            DevicesStringKey.RETRY_CONTENT_DESCRIPTION to "重新扫描配对端口",
            DevicesStringKey.VERBATIM_ENDPOINT to "{endpoint}",
            DevicesStringKey.VERBATIM_PAIRING_CODE to "{pairingCode}",
            DevicesStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
        ),
        UiLanguage.EN_US to mapOf(
            DevicesStringKey.TITLE to "Connection",
            DevicesStringKey.DISCOVERY_SCANNING to "Scanning for devices",
            DevicesStringKey.DISCOVERY_EMPTY to "No connectable devices found",
            DevicesStringKey.DISCOVERY_ERROR to "Device discovery failed",
            DevicesStringKey.CONNECTION_ERROR to "Connection failed",
            DevicesStringKey.DISMISS_ERROR to "Dismiss error",
            DevicesStringKey.PAIRING_QR_TITLE to "Pair with QR code",
            DevicesStringKey.PAIRING_QR_INSTRUCTION to
                "Scan this QR code on the controlled device to complete pairing",
            DevicesStringKey.SWITCH_TO_PAIRING_CODE to "Switch to pairing code",
            DevicesStringKey.PAIRING_CODE_TITLE to "Pair with a code",
            DevicesStringKey.LOCAL_PAIRING_TITLE to "Pair this device",
            DevicesStringKey.LOCAL_PAIRING_INSTRUCTION to
                "Keep the system wireless debugging pairing screen open",
            DevicesStringKey.PAIRING_PORT_SCANNING to "Scanning for a pairing port",
            DevicesStringKey.PAIRING_PORT_FOUND to "Pairing port found",
            DevicesStringKey.PAIRING_CODE_PLACEHOLDER to "Enter the six-digit pairing code",
            DevicesStringKey.PAIR to "Pair",
            DevicesStringKey.PAIRING_SUBMITTING to "Pairing",
            DevicesStringKey.PAIRING_SUCCEEDED to "Pairing succeeded",
            DevicesStringKey.PAIRING_SUCCEEDED_DETAIL to
                "Pairing succeeded and authorization is established; confirm the device connection next",
            DevicesStringKey.PAIRING_FAILED to "Pairing failed",
            DevicesStringKey.PAIRING_FAILED_RETRY to "Pairing failed. Try again.",
            DevicesStringKey.PAIRING_INVALID_CODE to "The pairing code must contain 6 digits",
            DevicesStringKey.PAIRING_UNSUPPORTED_QR to "QR pairing is not supported on this system",
            DevicesStringKey.PAIRING_CANCELLED to "Pairing cancelled",
            DevicesStringKey.LOCAL_PAIRING_CANCELLED to "Local pairing cancelled",
            DevicesStringKey.PAIRING_EXPIRED to "Pairing expired. Start again.",
            DevicesStringKey.LOCAL_PAIRING_EXPIRED to "Local pairing timed out. Start again.",
            DevicesStringKey.PAIRING_PORT_NOT_FOUND to "No pairing port found",
            DevicesStringKey.RETRY_PAIRING_SCAN to "Scan again",
            DevicesStringKey.PAIRING_CHOOSE_METHOD to "Choose a pairing method",
            DevicesStringKey.PAIRING_CONFIRMATION to "Pair with this device?",
            DevicesStringKey.SESSION_REPLACEMENT_CONFIRMATION to
                "The current ADB Session must disconnect before starting a new pairing attempt. Continue?",
            DevicesStringKey.CANCEL_PAIRING to "Cancel pairing",
            DevicesStringKey.CLOSE_PAIRING to "Close pairing window",
            DevicesStringKey.DEVICE_ROW_CONTENT_DESCRIPTION to "Select discovered device",
            DevicesStringKey.DISMISS_ERROR_CONTENT_DESCRIPTION to "Dismiss connection error",
            DevicesStringKey.PAIRING_OVERLAY_CONTENT_DESCRIPTION to "Device pairing window",
            DevicesStringKey.PAIR_CONTENT_DESCRIPTION to "Submit six-digit pairing code",
            DevicesStringKey.RETRY_CONTENT_DESCRIPTION to "Scan for the pairing port again",
            DevicesStringKey.VERBATIM_ENDPOINT to "{endpoint}",
            DevicesStringKey.VERBATIM_PAIRING_CODE to "{pairingCode}",
            DevicesStringKey.VERBATIM_TECHNICAL_CODE to "{technicalCode}",
        ),
    )

    init {
        val expectedKeys = DevicesStringKey.entries.toSet()
        UiLanguage.entries.forEach { language ->
            require(requiredKeys(language) == expectedKeys) {
                "Devices catalog is incomplete for $language"
            }
        }
        require(requiredKeys(UiLanguage.ZH_CN) == requiredKeys(UiLanguage.EN_US)) {
            "Devices catalogs must expose identical keys"
        }
        DevicesStringKey.entries.forEach { key ->
            require(
                placeholderSchema(UiLanguage.ZH_CN, key) ==
                    placeholderSchema(UiLanguage.EN_US, key),
            ) {
                "Devices placeholder schema mismatch for ${key.semanticKey}"
            }
        }
    }

    fun requiredKeys(language: UiLanguage): Set<DevicesStringKey> =
        catalogs.getValue(language).keys

    fun placeholderSchema(
        language: UiLanguage,
        key: DevicesStringKey,
    ): Map<String, TextArgumentType> {
        require(catalogs.getValue(language).containsKey(key))
        return key.placeholderSchema
    }

    fun text(
        language: UiLanguage,
        key: DevicesStringKey,
    ): String {
        require(key.placeholderSchema.isEmpty()) { "Arguments required for ${key.semanticKey}" }
        return catalogs.getValue(language).getValue(key)
    }

    fun ref(
        key: DevicesStringKey,
        vararg arguments: TypedTextArgument,
    ): LocalizedTextRef = LocalizedTextRef(
        owner = OWNER,
        semanticKey = key.semanticKey,
        arguments = arguments.toList(),
    ).also { ref ->
        require(ref.arguments.associate { it.name to it.type } == key.placeholderSchema) {
            "Devices text argument schema mismatch for ${key.semanticKey}"
        }
    }

    fun endpointRef(endpoint: String): LocalizedTextRef =
        verbatimRef(DevicesStringKey.VERBATIM_ENDPOINT, "endpoint", endpoint)

    fun pairingCodeRef(pairingCode: String): LocalizedTextRef =
        verbatimRef(DevicesStringKey.VERBATIM_PAIRING_CODE, "pairingCode", pairingCode)

    fun technicalCodeRef(technicalCode: String): LocalizedTextRef =
        verbatimRef(DevicesStringKey.VERBATIM_TECHNICAL_CODE, "technicalCode", technicalCode)

    fun resolve(
        language: UiLanguage,
        ref: LocalizedTextRef,
    ): String {
        require(ref.owner == OWNER) { "Text owner mismatch" }
        val key = DevicesStringKey.entries.firstOrNull { it.semanticKey == ref.semanticKey }
            ?: error("Unknown Devices semantic key")
        val arguments = ref.arguments.associateBy(TypedTextArgument::name)
        require(arguments.mapValues { it.value.type } == key.placeholderSchema) {
            "Devices text argument schema mismatch"
        }
        return arguments.values.fold(catalogs.getValue(language).getValue(key)) { resolved, argument ->
            val value = when (argument.type) {
                TextArgumentType.VERBATIM -> SafeVerbatimText.render(
                    raw = argument.value as String,
                    policy = SafeVerbatimPolicy.SingleLine(MAX_VERBATIM_CODE_POINTS),
                ).display
                TextArgumentType.TEXT,
                TextArgumentType.NUMBER,
                -> argument.value.toString()
            }
            resolved.replace("{${argument.name}}", value)
        }
    }

    private fun verbatimRef(
        key: DevicesStringKey,
        name: String,
        raw: String,
    ): LocalizedTextRef = ref(
        key,
        TypedTextArgument(
            name = name,
            value = raw,
            type = TextArgumentType.VERBATIM,
        ),
    )

    private const val MAX_VERBATIM_CODE_POINTS = 160
}
