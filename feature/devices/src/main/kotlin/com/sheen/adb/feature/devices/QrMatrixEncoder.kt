package com.sheen.adb.feature.devices

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets

internal class QrMatrix(
    val size: Int,
    modules: BooleanArray,
) {
    private val modules = modules.copyOf()

    init {
        require(size > 0) { "QR matrix size must be positive." }
        require(modules.size == size * size) { "QR matrix modules must form a square." }
    }

    operator fun get(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size) { "QR matrix coordinate is out of bounds." }
        return modules[y * size + x]
    }

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && size == other.size && modules.contentEquals(other.modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()

    override fun toString(): String = "QrMatrix(size=$size)"
}

internal class QrMatrixEncoder {
    fun encode(payload: String): QrMatrix {
        require(payload.isNotEmpty()) { "QR payload must not be empty." }
        val encoded = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            ),
        )
        check(encoded.width == encoded.height) { "QR encoder returned a non-square matrix." }
        val modules = BooleanArray(encoded.width * encoded.height)
        for (y in 0 until encoded.height) {
            for (x in 0 until encoded.width) {
                modules[y * encoded.width + x] = encoded[x, y]
            }
        }
        return QrMatrix(encoded.width, modules)
    }

    private companion object {
        const val QUIET_ZONE_MODULES = 4
    }
}
