package com.sheen.adb.core

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class DiagnosticRedactorTest {
    @Test
    fun `redacts network identity pairing code and private key`() {
        val syntheticCode = (0..5).joinToString("")
        val raw = "target=192.0.2.8 [2001:db8::1]:4455 pairingCode=$syntheticCode " +
            "-----BEGIN PRIVATE KEY----- secret -----END PRIVATE KEY----- " +
            "-----BEGIN CERTIFICATE----- cert -----END CERTIFICATE-----"
        val redacted = DiagnosticRedactor.redact(raw)

        assertFalse(redacted.contains("192.0.2.8"))
        assertFalse(redacted.contains("2001:db8::1"))
        assertFalse(redacted.contains(syntheticCode))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("cert "))
        assertTrue(redacted.contains("已脱敏"))
    }

    @Test
    fun `redacts v003 file apk shell and logcat context`() {
        val digest = "a".repeat(64)
        val raw = "remotePath=/storage/emulated/0/synthetic/file.bin; " +
            "safUri=content://synthetic.provider/tree/private; " +
            "packageName=com.example.synthetic; " +
            "apkPath=/data/app/synthetic/base.apk; sha256=$digest; " +
            "shellOutput=SYNTHETIC_SHELL_CONTENT; logcat=SYNTHETIC_LOGCAT_CONTENT"

        val redacted = DiagnosticRedactor.redact(raw)

        assertFalse(redacted.contains("/storage/emulated/0"))
        assertFalse(redacted.contains("content://"))
        assertFalse(redacted.contains("com.example.synthetic"))
        assertFalse(redacted.contains("/data/app"))
        assertFalse(redacted.contains(digest))
        assertFalse(redacted.contains("SYNTHETIC_SHELL_CONTENT"))
        assertFalse(redacted.contains("SYNTHETIC_LOGCAT_CONTENT"))
    }

    @Test
    fun `redacts wireless pairing discovery and application values from diagnostic context`() {
        val serviceInstance = "synthetic-adb-pairing-A7"
        val qrPassword = "syntheticQrPass9"
        val qrPayload = "WIFI:T:ADB;S:$serviceInstance;P:$qrPassword;;"
        val ipv4Endpoint = "192.0.2.44:37123"
        val unbracketedScopedIpv6Endpoint = "fe80::7%synthetic0:37124"
        val bracketedScopedIpv6Endpoint = "[fe80::8%synthetic1]:37125"
        val packageName = "com.example.syntheticdiagnostics"
        val raw = listOf(
            "qrPayload=$qrPayload",
            "qrPassword=$qrPassword",
            "serviceType=_adb-tls-pairing._tcp serviceName=$serviceInstance",
            "endpoint=$ipv4Endpoint",
            "endpoint=$unbracketedScopedIpv6Endpoint",
            "endpoint=$bracketedScopedIpv6Endpoint",
            "packageName=$packageName application=$packageName",
            "exception=IllegalStateException: pairing failed for $qrPayload",
            "message=discovery failed for $serviceInstance at $unbracketedScopedIpv6Endpoint",
            "context=$qrPassword $bracketedScopedIpv6Endpoint $packageName",
        ).joinToString("; ")

        val redacted = DiagnosticRedactor.redact(raw)

        assertFalse(redacted.contains(qrPayload))
        assertFalse(redacted.contains(qrPassword))
        assertFalse(redacted.contains(serviceInstance))
        assertFalse(redacted.contains(ipv4Endpoint))
        assertFalse(redacted.contains(unbracketedScopedIpv6Endpoint))
        assertFalse(redacted.contains(bracketedScopedIpv6Endpoint))
        assertFalse(redacted.contains(packageName))
    }

    @Test
    fun `structured diagnostic fields use an explicit safe whitelist`() {
        val fields = DiagnosticRedactor.safeFields(
            mapOf(
                "stage" to "FILE_TRANSFER",
                "outcome" to "FAILED",
                "technicalCode" to "SESSION_INVALID",
                "count" to 3,
                "remotePath" to "/storage/emulated/0/synthetic/file.bin",
                "safUri" to "content://synthetic.provider/tree/private",
                "packageName" to "com.example.synthetic",
                "shellOutput" to "SYNTHETIC_SHELL_CONTENT",
                "logcat" to "SYNTHETIC_LOGCAT_CONTENT",
                "qrPayload" to "WIFI:T:ADB;S:synthetic-adb-pairing-A7;P:syntheticQrPass9;;",
                "qrPassword" to "syntheticQrPass9",
                "serviceName" to "synthetic-adb-pairing-A7",
                "endpoint" to "fe80::7%synthetic0:37124",
                "application" to "com.example.syntheticdiagnostics",
                "exception" to "IllegalStateException: syntheticQrPass9",
                "message" to "synthetic-adb-pairing-A7 failed",
                "context" to "192.0.2.44:37123",
            ),
        )

        assertEquals(
            fields,
            mapOf(
                "stage" to "FILE_TRANSFER",
                "outcome" to "FAILED",
                "technicalCode" to "SESSION_INVALID",
                "count" to "3",
            ),
        )
    }
}
