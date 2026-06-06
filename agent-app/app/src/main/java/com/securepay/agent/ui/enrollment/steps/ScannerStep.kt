package com.securepay.agent.ui.enrollment.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.securepay.agent.R
import com.securepay.agent.ui.enrollment.EnrollmentUiState

/**
 * Step 2 — Device capture. Shows a live CameraX preview to frame the IMEI
 * barcode/label, with a manual entry fallback and a "Simulate Scan" helper for
 * demos or devices without a camera.
 */
@Composable
fun ScannerStep(
    state: EnrollmentUiState,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit,
    onSimulateScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(hasCameraPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(modifier = Modifier.fillMaxWidth())
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.camera_permission_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.action_grant_camera))
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onSimulateScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_simulate_scan))
        }

        OutlinedTextField(
            value = state.draft.imei,
            onValueChange = onImeiChange,
            label = { Text(stringResource(R.string.label_imei)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.draft.imei.isNotEmpty() && !state.isImeiValid,
            supportingText = {
                Text("${state.draft.imei.length}/15 digits")
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.draft.deviceModel,
            onValueChange = onDeviceModelChange,
            label = { Text(stringResource(R.string.label_device_model)) },
            singleLine = true,
            isError = state.draft.deviceModel.isEmpty().not() && !state.isDeviceModelValid,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Binds a CameraX [Preview] use case to the current lifecycle and renders it in
 * a [PreviewView] through interop. The provider is unbound on disposal.
 */
@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        val provider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
        } catch (_: Exception) {
            // Camera may be unavailable (e.g. emulator without webcam); the
            // manual IMEI entry below remains fully functional.
        }

        onDispose {
            provider.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxWidth())
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
