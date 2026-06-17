package com.touchbase.agent.ui.enrollment.steps

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true))
    }

    private var lastScannedValue = ""
    private var lastScanTime = 0L

    @Volatile
    private var isScanning = true

    fun stop() {
        isScanning = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val buffer = mediaImage.planes[0].buffer
            val data = ByteArray(buffer.capacity())
            buffer.get(data)

            val width = mediaImage.width
            val height = mediaImage.height

            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(bitmap)
                val value = result.text

                val now = System.currentTimeMillis()
                if (value != lastScannedValue || now - lastScanTime > 1500) {
                    lastScannedValue = value
                    lastScanTime = now
                    onBarcodeDetected(value)
                }
            } catch (_: ReaderException) {
            }

            reader.reset()
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }
}
