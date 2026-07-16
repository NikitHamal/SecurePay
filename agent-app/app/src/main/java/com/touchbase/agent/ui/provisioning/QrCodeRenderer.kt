package com.touchbase.agent.ui.provisioning

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QrCode(
    content: String,
    size: Dp = 300.dp,
    darkColor: Color = Color(0xFF121212),
    lightColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetPx = with(density) { size.roundToPx() }
    val imageBitmap = remember(content, targetPx, darkColor, lightColor) {
        renderQrBitmap(content, targetPx, darkColor, lightColor).asImageBitmap()
    }

    Image(
        bitmap = imageBitmap,
        contentDescription = "Provisioning QR code",
        modifier = modifier
            .size(size)
            .background(lightColor)
    )
}

private fun renderQrBitmap(
    content: String,
    targetPx: Int,
    darkColor: Color,
    lightColor: Color
): android.graphics.Bitmap {
    val safePx = targetPx.coerceAtLeast(64)

    val matrix = runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            safePx,
            safePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    }.getOrNull()

    val width = matrix?.width ?: safePx
    val height = matrix?.height ?: safePx

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val darkArgb = darkColor.toArgb()
    val lightArgb = lightColor.toArgb()

    val pixels = IntArray(width * height)
    if (matrix != null) {
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) darkArgb else lightArgb
            }
        }
    } else {
        for (i in pixels.indices) pixels[i] = lightArgb
    }

    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}

private fun Color.toArgb(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
