package com.touchbase.agent.ui.enrollment.steps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import java.io.ByteArrayOutputStream

private val SignatureInk = Color(0xFF111827)

/**
 * M-KOPA consent screen (Touch Base edition):
 * brand banner → bordered scrollable Device Financing Agreement → affirmation
 * checkbox → "Please Sign Here" full-screen signature pad → AGREE & SUBMIT
 * (the wizard's bottom button, enabled once affirmed + signed).
 */
@Composable
fun ConsentStep(
    state: EnrollmentUiState,
    agreementText: String,
    onConsentChecked: (Boolean) -> Unit,
    onSignatureChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullAgreement by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    val draft = state.draft

    val signatureBitmap = remember(draft.signatureBase64) {
        draft.signatureBase64?.let {
            runCatching {
                val decoded = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            }.getOrNull()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Brand banner — mirrors the M-KOPA consent header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Touch Base",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(360.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "CUSTOMER CONSENT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Bordered agreement box (scrollable) with a "read full screen" affordance.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        ) {
            Text(
                agreementText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            IconButton(
                onClick = { showFullAgreement = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Filled.OpenInFull,
                    contentDescription = "Read full agreement",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Affirmation — M-KOPA wording.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onConsentChecked(!draft.consentTerms) },
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = draft.consentTerms,
                onCheckedChange = onConsentChecked
            )
            Text(
                "I affirm that the privacy policy and the terms and conditions were read over and explained to me in a language I understand best and I agree to the terms and conditions contained herein in relation to the Product",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Signature area: grey "Please Sign Here" button → full-screen pad.
        if (signatureBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF16A34A), RoundedCornerShape(8.dp))
            ) {
                Image(
                    bitmap = signatureBitmap.asImageBitmap(),
                    contentDescription = "Customer signature",
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
                TextButton(
                    onClick = { showSignaturePad = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text("Re-sign", color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Button(
                onClick = { showSignaturePad = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Please Sign Here", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showFullAgreement) {
        Dialog(
            onDismissRequest = { showFullAgreement = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showFullAgreement = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(
                            "Device Financing Agreement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        agreementText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }

    if (showSignaturePad) {
        SignatureSheet(
            onDone = { base64 ->
                onSignatureChange(base64)
                showSignaturePad = false
            },
            onCancel = { showSignaturePad = false }
        )
    }
}

/**
 * Full-screen signature pad (the M-KOPA landscape sheet): big white canvas,
 * gold CLEAR / DONE actions bottom-right.
 */
@Composable
private fun SignatureSheet(
    onDone: (String?) -> Unit,
    onCancel: () -> Unit
) {
    val strokes = remember { mutableStateListOf<Path>() }
    var activeStroke by remember { mutableStateOf<Path?>(null) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "Customer signature",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Ask the customer to sign with a finger on the pad below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .onSizeChanged { padSize = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset: Offset ->
                                        activeStroke = Path().apply { moveTo(offset.x, offset.y) }
                                    },
                                    onDrag = { change, _ ->
                                        val current = activeStroke ?: return@detectDragGestures
                                        val next = Path().apply { addPath(current) }
                                        next.lineTo(change.position.x, change.position.y)
                                        activeStroke = next
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        activeStroke?.let { strokes.add(it) }
                                        activeStroke = null
                                    },
                                    onDragCancel = { activeStroke = null }
                                )
                            }
                    ) {
                        val style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        strokes.forEach { stroke -> drawPath(stroke, color = SignatureInk, style = style) }
                        activeStroke?.let { drawPath(it, color = SignatureInk, style = style) }
                    }
                    if (strokes.isEmpty() && activeStroke == null) {
                        Text(
                            "Sign here",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { strokes.clear() }) {
                        Text("CLEAR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onDone(exportSignature(strokes, padSize)) }) {
                        Text("DONE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun exportSignature(strokes: List<Path>, size: IntSize): String? {
    if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val outWidth = 1000
    val outHeight = (outWidth.toFloat() * size.height / size.width).toInt().coerceIn(160, 900)
    val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val scaleX = outWidth.toFloat() / size.width
    val scaleY = outHeight.toFloat() / size.height
    val matrix = android.graphics.Matrix().apply { setScale(scaleX, scaleY) }
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(17, 24, 39)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { stroke ->
        val androidPath = stroke.asAndroidPath()
        androidPath.transform(matrix)
        canvas.drawPath(androidPath, paint)
    }
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    bitmap.recycle()
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
