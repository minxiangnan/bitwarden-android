package com.x8bit.bitwarden.data.authenticator.importing

import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Reads a QR code from an image the user picked. Returns null when the image has no code.
 */
fun decodeQrImage(bytes: ByteArray): String? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val binary = BinaryBitmap(HybridBinarizer(source))
    return runCatching { MultiFormatReader().decode(binary).text }.getOrNull()
}
