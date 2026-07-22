package com.sheen.adb.feature.devices

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNotSame
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class QrMatrixEncoderTest {
    @Test
    fun `UTF-8 payload uses the fixed quiet zone and error correction configuration`() {
        val payload = "设备-测试-α"
        val actual = QrMatrixEncoder().encode(payload)
        val expected = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            ),
        )

        assertEquals(actual.size, expected.width)
        assertEquals(expected.width, expected.height)
        for (y in 0 until actual.size) {
            for (x in 0 until actual.size) {
                assertEquals(actual[x, y], expected[x, y], "Module mismatch at ($x,$y)")
            }
        }
        repeat(QUIET_ZONE_MODULES) { offset ->
            assertFalse((0 until actual.size).any { coordinate -> actual[coordinate, offset] })
            assertFalse((0 until actual.size).any { coordinate -> actual[offset, coordinate] })
            assertFalse((0 until actual.size).any { coordinate -> actual[coordinate, actual.size - 1 - offset] })
            assertFalse((0 until actual.size).any { coordinate -> actual[actual.size - 1 - offset, coordinate] })
        }
    }

    @Test
    fun `encoding is deterministic and returns a fresh immutable project matrix`() {
        val encoder = QrMatrixEncoder()
        val first = encoder.encode("deterministic-payload")
        val second = encoder.encode("deterministic-payload")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotSame(first, second, "The encoder must not cache pairing matrices")
        assertTrue(first.size > 0)
        assertTrue((0 until first.size).all { x -> (0 until first.size).all { y -> first[x, y] == second[x, y] } })
    }

    @Test
    fun `encoder boundary exposes no ZXing decode scanner or camera API`() {
        val boundaryClasses = listOf(QrMatrixEncoder::class.java, QrMatrix::class.java)
        val methods = boundaryClasses.flatMap { type -> type.declaredMethods.toList() }
        val forbiddenWords = listOf("decode", "scan", "scanner", "camera", "capture")

        assertTrue(methods.none { method -> forbiddenWords.any { it in method.name.lowercase() } })
        assertTrue(
            methods.none { method ->
                (listOf(method.returnType) + method.parameterTypes).any { it.name.startsWith("com.google.zxing") }
            },
            "ZXing types must remain behind the project-owned matrix boundary",
        )
        assertTrue(
            QrMatrixEncoder::class.java.declaredFields.none { field ->
                field.type == QrMatrix::class.java || field.type.name == "com.google.zxing.common.BitMatrix"
            },
            "The encoder must not retain the last generated matrix",
        )
    }

    private companion object {
        const val QUIET_ZONE_MODULES = 4
    }
}
