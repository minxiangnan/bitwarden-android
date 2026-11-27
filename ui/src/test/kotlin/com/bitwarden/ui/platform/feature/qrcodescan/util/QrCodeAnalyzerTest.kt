package com.bitwarden.ui.platform.feature.qrcodescan.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QrCodeAnalyzerTest {
    @Test
    fun `packed luminance of an otpauth qr decodes back to the same uri`() {
        val matrix = MultiFormatWriter().encode(OTP_AUTH, BarcodeFormat.QR_CODE, 240, 240)
        val luma = ByteArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                luma[y * matrix.width + x] = if (matrix.get(x, y)) 0 else WHITE
            }
        }
        assertEquals(OTP_AUTH, decodeQrLuminance(luma, matrix.width, matrix.height))
    }

    @Test
    fun `row padding is removed before decoding`() {
        val matrix = MultiFormatWriter().encode(OTP_AUTH, BarcodeFormat.QR_CODE, 180, 180)
        val stride = matrix.width + 16
        val raw = ByteArray(stride * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                raw[y * stride + x] = if (matrix.get(x, y)) 0 else WHITE
            }
        }
        val packed = lumaFromYPlane(raw, stride, matrix.width, matrix.height)
        assertEquals(OTP_AUTH, decodeQrLuminance(packed!!, matrix.width, matrix.height))
    }

    @Test
    fun `a crop that does not fit is rejected`() {
        assertNull(lumaFromYPlane(ByteArray(8), rowStride = 2, width = 4, height = 2))
        assertNull(decodeQrLuminance(ByteArray(4), width = 3, height = 3))
    }
}

private const val OTP_AUTH =
    "otpauth://totp/GitHub:ada?secret=GEZDGNBVGY3TQOJQ&issuer=GitHub&digits=6&period=30"

private const val WHITE: Byte = 0xff.toByte()
