package com.touchbase.agent.ui.enrollment.steps

import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.ITF,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E
                )
            )
        )
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
            val width = mediaImage.width
            val height = mediaImage.height
            val normalized = normalizeYPlane(mediaImage, width, height)
            val frame = rotate(normalized, width, height, imageProxy.imageInfo.rotationDegrees)

            val source = PlanarYUVLuminanceSource(
                frame.data,
                frame.width,
                frame.height,
                0,
                0,
                frame.width,
                frame.height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            decode(bitmap)
        } catch (_: Exception) {
            // Drop bad frames; CameraX will provide the next frame immediately.
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }

    private fun decode(bitmap: BinaryBitmap) {
        try {
            val result = reader.decodeWithState(bitmap)
            val value = result.text

            val now = System.currentTimeMillis()
            if (value != lastScannedValue || now - lastScanTime > 1500) {
                lastScannedValue = value
                lastScanTime = now
                onBarcodeDetected(value)
            }
        } catch (_: ReaderException) {
            // No barcode in this frame.
        }
    }

    private fun normalizeYPlane(image: Image, width: Int, height: Int): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val output = ByteArray(width * height)

        if (pixelStride == 1 && rowStride == width) {
            buffer.rewindSafely()
            buffer.get(output, 0, output.size)
            return output
        }

        for (row in 0 until height) {
            val rowOffset = row * rowStride
            val outputOffset = row * width
            for (col in 0 until width) {
                output[outputOffset + col] = buffer.get(rowOffset + col * pixelStride)
            }
        }
        return output
    }

    private data class Frame(val data: ByteArray, val width: Int, val height: Int)

    private fun rotate(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): Frame {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> {
                val rotated = ByteArray(data.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[x * height + (height - y - 1)] = data[y * width + x]
                    }
                }
                Frame(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(data.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[(height - y - 1) * width + (width - x - 1)] = data[y * width + x]
                    }
                }
                Frame(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(data.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[(width - x - 1) * height + y] = data[y * width + x]
                    }
                }
                Frame(rotated, height, width)
            }
            else -> Frame(data, width, height)
        }
    }

    private fun ByteBuffer.rewindSafely() {
        if (position() != 0) rewind()
    }
}
