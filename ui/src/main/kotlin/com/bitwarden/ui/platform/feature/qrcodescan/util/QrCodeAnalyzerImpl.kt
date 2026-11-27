package com.bitwarden.ui.platform.feature.qrcodescan.util

import androidx.camera.core.ImageProxy
import com.bitwarden.annotation.OmitFromCoverage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * A class setup to handle image analysis so that we can use the Zxing library
 * to scan QR codes and convert them to a string.
 */
@OmitFromCoverage
class QrCodeAnalyzerImpl : QrCodeAnalyzer {

    override lateinit var onQrCodeScanned: (String) -> Unit

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val packed = lumaFromYPlane(
                raw = plane.buffer.toByteArray(),
                rowStride = plane.rowStride,
                width = image.width,
                height = image.height,
            ) ?: return
            val text = decodeQrLuminance(packed, image.width, image.height) ?: return
            if (::onQrCodeScanned.isInitialized) {
                onQrCodeScanned(text)
            }
        } catch (_: RuntimeException) {
            // One bad frame must not leave the image open or stop the camera.
        } finally {
            image.close()
        }
    }
}

/**
 * Copies the Y plane into a tightly packed buffer. Returns null when the crop cannot fit.
 */
internal fun lumaFromYPlane(
    raw: ByteArray,
    rowStride: Int,
    width: Int,
    height: Int,
): ByteArray? {
    if (width <= 0 || height <= 0 || rowStride < width) return null
    if (raw.size.toLong() < rowStride.toLong() * height) return null
    if (rowStride == width) return raw
    val packed = ByteArray(width * height)
    for (row in 0 until height) {
        System.arraycopy(raw, row * rowStride, packed, row * width, width)
    }
    return packed
}

/**
 * Reads a QR code from packed luminance bytes. Returns null when no code is present.
 */
internal fun decodeQrLuminance(luma: ByteArray, width: Int, height: Int): String? {
    if (width <= 0 || height <= 0 || luma.size < width * height) return null
    val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
    return try {
        MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                DecodeHintType.ALSO_INVERTED to true,
            ),
        ).text
    } catch (_: NotFoundException) {
        null
    }
}

/**
 * This function helps us prepare the byte buffer to be read.
 */
@OmitFromCoverage
private fun ByteBuffer.toByteArray(): ByteArray =
    ByteArray(rewind().remaining()).also { get(it) }
