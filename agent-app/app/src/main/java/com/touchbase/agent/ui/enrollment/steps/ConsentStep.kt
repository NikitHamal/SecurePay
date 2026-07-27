package com.touchbase.agent.ui.enrollment.steps

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import java.io.ByteArrayOutputStream

private val SignatureInk = Color(0xFF111827)

/**
 * Customer consent & signature step (the M-KOPA "consent" screen): the agent
 * walks the customer through the deal summary, the customer ticks both
 * consents and signs on the phone with a finger.
 */
@Composable
fun ConsentStep(
    state: EnrollmentUiState,
    onConsentTermsChange: (Boolean) -> Unit,
    onConsentDataChange: (Boolean) -> Unit,
    onSignatureChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft
    val planName = state.selectedPlan?.name ?: "Custom plan"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Deal summary the customer is agreeing to
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Agreement summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${draft.customerName.trim().ifBlank { "The customer" }} is buying " +
                        "${draft.deviceModel.ifBlank { "a device" }} on $planName: " +
                        "${formatAmount(draft.totalLoanAmount)} over ${draft.termDays} days at " +
                        "${formatAmount(draft.dailyRate)}/day with ${formatAmount(draft.downPayment)} deposit. " +
                        "The device locks automatically when a payment is missed, and unlocks as soon as the balance is cleared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ConsentCheckRow(
            checked = draft.consentTerms,
            onCheckedChange = onConsentTermsChange,
            text = "The customer agreed to the financing terms above and understands the device will lock automatically when payments are overdue."
        )
        ConsentCheckRow(
            checked = draft.consentData,
            onCheckedChange = onConsentDataChange,
            text = "The customer consents to the collection of their identity documents, photos, and device location for financing protection (Ghana Data Protection Act, 2012)."
        )

        Text(
            "Customer signature — ask them to sign with a finger",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SignaturePad(
            hasSignature = draft.signatureBase64 != null,
            onSignatureChange = onSignatureChange
        )

        if (draft.signatureBase64 != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = androidx.compose.ui.Modifier.size(16.dp)
                )
                Text(
                    "Signature captured",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ConsentCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Finger-drawing pad that exports strokes as a PNG (base64) for upload. */
@Composable
private fun SignaturePad(
    hasSignature: Boolean,
    onSignatureChange: (String?) -> Unit
) {
    val strokes = remember { mutableStateListOf<Path>() }
    var activeStroke by remember { mutableStateOf<Path?>(null) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    fun export() {
        onSignatureChange(exportSignature(strokes, padSize))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (hasSignature) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
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
                            export()
                        },
                        onDragCancel = {
                            activeStroke = null
                        }
                    )
                }
        ) {
            val style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            strokes.forEach { stroke -> drawPath(stroke, color = SignatureInk, style = style) }
            activeStroke?.let { drawPath(it, color = SignatureInk, style = style) }
        }

        if (strokes.isEmpty() && activeStroke == null) {
            Text(
                "Sign here",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (strokes.isNotEmpty()) {
            TextButton(
                onClick = {
                    strokes.clear()
                    onSignatureChange(null)
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun exportSignature(strokes: List<Path>, size: IntSize): String? {
    if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val outWidth = 1000
    val outHeight = (outWidth.toFloat() * size.height / size.width)
        .toInt().coerceIn(160, 600)
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
