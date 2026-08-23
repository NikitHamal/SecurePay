package com.touchbase.agent.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoCaptureUtils {

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun createTempPhotoUri(context: Context): Pair<File, Uri> {
        val photosDir = File(context.cacheDir, "photos").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
        val photoFile = File(photosDir, "KYC_${timeStamp}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return Pair(photoFile, uri)
    }

    fun processPhotoFileToBase64(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bitmap = decodeSampledBitmapFromFile(file.absolutePath, 1600, 1600) ?: return null
            val rotatedBitmap = rotateBitmapIfRequired(bitmap, file.absolutePath)
            val base64 = compressAndToBase64(rotatedBitmap)
            runCatching { file.delete() }
            base64
        } catch (_: Exception) {
            runCatching { file.delete() }
            null
        }
    }

    fun processUriToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                compressAndToBase64(bitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun compressAndToBase64(bitmap: Bitmap): String {
        val maxDimension = 1600
        val resized = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = if (bitmap.width > bitmap.height) maxDimension else (maxDimension * aspect).toInt()
            val newHeight = if (bitmap.height > bitmap.width) maxDimension else (maxDimension / aspect).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationAngle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationAngle != 0f) {
                val matrix = Matrix().apply { postRotate(rotationAngle) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (_: Exception) {
            bitmap
        }
    }
}
