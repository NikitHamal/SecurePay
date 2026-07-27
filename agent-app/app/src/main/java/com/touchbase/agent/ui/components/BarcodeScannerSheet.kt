package com.touchbase.agent.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.touchbase.agent.ui.enrollment.steps.BarcodeAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Full-screen barcode/QR/IMEI scanner.
 *
 * History: this used to be a ModalBottomSheet whose dialog window could carry
 * focus-blocking flags on top of the caller's sheet — the preview went black
 * or never opened while the keyboard also died in the form behind it. A plain
 * full-screen Dialog (own window, flags explicitly cleared) removes the whole
 * class of device-specific sheet bugs, and the larger viewfinder makes 1D
 * barcodes (IMEI stickers) dramatically easier to hit.
 *
 * [validator] decides whether a scanned raw value is acceptable. When it
 * returns a non-null string the sheet calls [onScan] with it and auto-closes.
 * The default validator accepts any 15-digit run (IMEI on device packaging).
 */
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            dialogWindow?.let { window ->
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                )
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    onBarcodeDetected = { raw ->
                        val accepted = validator(raw)
                        if (accepted != null) onScan(accepted)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Aiming reticle for the barcode zone
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.8f)
                        .height(160.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        "Camera permission is required to scan barcodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
            }

            // Close
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close scanner",
                    tint = Color.White
                )
            }
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

    val currentCallback = rememberUpdatedState(onBarcodeDetected)
    val analyzer = remember { BarcodeAnalyzer { currentCallback.value(it) } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        // Never block the composition thread: attach when the provider resolves.
        val listener = Runnable {
            val provider = try { providerFuture.get() } catch (_: Exception) { null } ?: return@Runnable
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
                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (_: Exception) { /* camera in use or unavailable */ }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        // Tap-to-focus: the box stickers rarely sit in the center AF zone.
        previewView.setOnTouchListener { view, event ->
            val cam = camera ?: return@setOnTouchListener false
            try {
                val factory = SurfaceOrientedMeteringPointFactory(
                    view.width.toFloat(), view.height.toFloat()
                )
                val action = FocusMeteringAction.Builder(
                    factory.createPoint(event.x, event.y),
                    FocusMeteringAction.FLAG_AF
                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                cam.cameraControl.startFocusAndMetering(action)
            } catch (_: Exception) { }
            true
        }

        onDispose {
            analyzer.stop()
            camera = null
            try { torchOn = false } catch (_: Exception) {}
            try {
                val provider = providerFuture.get()
                provider.unbindAll()
            } catch (_: Exception) {}
            executor.shutdown()
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (_: InterruptedException) { executor.shutdownNow() }
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Torch toggle (bottom-end) — barcode stickers under shop lighting.
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = {
                    val next = !torchOn
                    try {
                        camera?.cameraControl?.enableTorch(next)
                        torchOn = next
                    } catch (_: Exception) { }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Torch off" else "Torch on",
                    tint = Color.White
                )
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
