package com.sheen.adb.core

object DiagnosticRedactor {
    private val ipv4 = Regex("(?<![A-Za-z0-9])(?:\\d{1,3}\\.){3}\\d{1,3}(?![A-Za-z0-9])")
    private val bracketedIpv6 = Regex("\\[(?:[0-9A-Fa-f]{0,4}:){2,}[0-9A-Fa-f:.]*(?:%[A-Za-z0-9_.-]+)?]")
    private val scopedIpv6 = Regex(
        "(?<![A-Za-z0-9_.-])(?:[0-9A-Fa-f]{1,4}:){1,7}:?[0-9A-Fa-f]{0,4}%[A-Za-z0-9_.-]+(?::\\d{1,5})?(?![A-Za-z0-9_.-])",
    )
    private val wirelessQrPayload = Regex("WIFI:T:ADB;S:[^;\\r\\n]+;P:[^;\\r\\n]+;;", RegexOption.IGNORE_CASE)
    private val pairingCode = Regex("(?i)(pair(?:ing)?[ _-]?code\\s*[:=]?\\s*)\\d{6}")
    private val privateKeyBlock = Regex(
        "-----BEGIN (?:RSA )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA )?PRIVATE KEY-----",
    )
    private val certificateBlock = Regex(
        "-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----",
    )
    private val sensitiveField = Regex(
        "(?i)\\b(remotePath|path|safUri|uri|packageName|package|apkPath|sha256|digest|" +
            "shellOutput|stdout|stderr|logcat|qrPayload|qrPassword|serviceName|endpoint|application|exception|message|context)" +
            "\\s*[:=]\\s*[^;\\r\\n]*",
    )
    private val contentUri = Regex("content://[^\\s;,]+", RegexOption.IGNORE_CASE)
    private val androidAbsolutePath = Regex(
        "(?<![A-Za-z0-9_])/(?:data|storage|sdcard|mnt|system|vendor|product|apex)(?:/[^\\s;,]*)?",
        RegexOption.IGNORE_CASE,
    )
    private val packageName = Regex(
        "(?<![A-Za-z0-9_])(?:[A-Za-z][A-Za-z0-9_]*\\.){2,}[A-Za-z][A-Za-z0-9_]*(?![A-Za-z0-9_])",
    )
    private val sha256 = Regex("(?<![A-Fa-f0-9])[A-Fa-f0-9]{64}(?![A-Fa-f0-9])")
    private val safeToken = Regex("[A-Z][A-Z0-9_]{0,63}")
    private val safeFieldOrder = listOf("stage", "outcome", "technicalCode", "count")

    fun redact(value: String): String = value
        .replace(privateKeyBlock, "<私钥已脱敏>")
        .replace(certificateBlock, "<证书已脱敏>")
        .replace(pairingCode) { "${it.groupValues[1]}<配对码已脱敏>" }
        .replace(wirelessQrPayload, "<QR已脱敏>")
        .replace(sensitiveField) { "${it.groupValues[1]}=<已脱敏>" }
        .replace(contentUri, "<URI已脱敏>")
        .replace(androidAbsolutePath, "<路径已脱敏>")
        .replace(packageName, "<包名已脱敏>")
        .replace(sha256, "<摘要已脱敏>")
        .replace(bracketedIpv6, "[<IPv6已脱敏>]")
        .replace(scopedIpv6, "<IPv6已脱敏>")
        .replace(ipv4, "<IP已脱敏>")
        .take(2_000)

    fun safeFields(fields: Map<String, Any?>): Map<String, String> = buildMap {
        for (key in safeFieldOrder) {
            val value = fields[key] ?: continue
            val safeValue = when (key) {
                "count" -> value.toString().toLongOrNull()?.takeIf { it >= 0L }?.toString()
                else -> value.toString().takeIf(safeToken::matches)
            } ?: continue
            put(key, safeValue)
        }
    }
}
