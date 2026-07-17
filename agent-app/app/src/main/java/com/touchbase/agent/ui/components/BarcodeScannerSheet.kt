package com.touchbase.agent.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.touchbase.agent.ui.enrollment.steps.BarcodeAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Reusable barcode/QR/IMEI scanner sheet.
 *
 * [validator] decides whether a scanned raw value is acceptable. When it
 * returns a non-null string the sheet calls [onScan] with it and auto-closes.
 * The default validator accepts any 15-digit run (IMEI on device packaging).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerSheet(
    onDismiss: () -> Unit,
    onScan: (String) -> Unit,
    title: String = "Scan Barcode",
    subtitle: String = "Point camera at the barcode on the box.",
    validator: (raw: String) -> String? = { raw ->
        val digits = raw.filter { it.isDigit() }
        if (digits.length == 15) digits else null
    }
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(36.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreview { raw ->
                        val accepted = validator(raw)
                        if (accepted != null) onScan(accepted)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Camera permission is required to scan barcodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(360.dp)
            ) { Text("Cancel") }
        }
    }
}

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val currentCallback = rememberUpdatedState(onBarcodeDetected)
    val analyzer = remember { BarcodeAnalyzer { currentCallback.value(it) } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val provider = try { cameraProviderFuture.get() } catch (_: Exception) { null }
        if (provider != null) {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (_: Exception) { /* camera in use or unavailable */ }
        }
        onDispose {
            analyzer.stop()
            executor.shutdown()
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (_: InterruptedException) { executor.shutdownNow() }
            try { provider?.unbindAll() } catch (_: Exception) {}
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxWidth())
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
