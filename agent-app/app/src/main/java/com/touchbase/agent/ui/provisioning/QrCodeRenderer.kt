package com.touchbase.agent.ui.provisioning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    val matrix = remember(content) { encodeQr(content) }

    Canvas(
        modifier = modifier
            .size(size)
            .background(lightColor)
    ) {
        if (matrix == null) return@Canvas
        val dim = matrix.width
        val cellSize = size.toPx() / dim.toFloat()
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(x.toFloat() * cellSize, y.toFloat() * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

private fun encodeQr(content: String): com.google.zxing.common.BitMatrix? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
    }.getOrNull()
}
