package com.touchbase.agent.ui.enrollment.steps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.ui.enrollment.EnrollmentDraft
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import java.io.ByteArrayOutputStream

@Composable
fun KycStep(
    state: EnrollmentUiState,
    onNameChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPhotoSelected: (String?) -> Unit,
    onIdFrontSelected: (String?) -> Unit,
    onIdBackSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_full_name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.customerName,
                onValueChange = onNameChange,
                placeholder = { Text("Enter full name", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                minLines = 1,
                maxLines = 1,
                isError = state.draft.customerName.isNotEmpty() && !state.isNameValid,
                supportingText = {
                    if (state.draft.customerName.isNotEmpty() && !state.isNameValid) {
                        Text("Enter at least 3 characters", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_national_id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.nationalId,
                onValueChange = onNationalIdChange,
                placeholder = { Text("Enter national ID", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                isError = state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid,
                supportingText = {
                    if (state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid) {
                        Text("Enter 6–20 characters", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_phone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.phoneNumber,
                onValueChange = onPhoneChange,
                placeholder = { Text("Enter phone number", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid,
                supportingText = {
                    if (state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid) {
                        Text("Enter a valid phone number", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("KYC Photo Verification", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)

        KycPhotoSelector(
            label = "Customer Photo (Selfie)",
            base64 = state.draft.customerPhotoBase64,
            onPhotoSelected = onPhotoSelected
        )

        KycPhotoSelector(
            label = "National ID Front Photo",
            base64 = state.draft.nationalIdFrontBase64,
            onPhotoSelected = onIdFrontSelected
        )

        KycPhotoSelector(
            label = "National ID Back Photo",
            base64 = state.draft.nationalIdBackBase64,
            onPhotoSelected = onIdBackSelected
        )
    }
}

@Composable
fun KycPhotoSelector(
    label: String,
    base64: String?,
    onPhotoSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val encoded = compressAndToBase64(bitmap)
            onPhotoSelected(encoded)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val encoded = compressAndToBase64(bitmap)
                        onPhotoSelected(encoded)
                    }
                }
            }
        }
    }

    val previewBitmap = remember(base64) {
        if (base64 != null) {
            runCatching {
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            }.getOrNull()
        } else {
            null
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                )
            } else {
                Column(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Image", style = TextStyle(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (base64 != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Attached",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Photo Attached", style = TextStyle(fontSize = 11.sp), color = Color(0xFF10B981))
                    }
                } else {
                    Text("Please attach photo", style = TextStyle(fontSize = 11.sp), color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Camera", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Gallery", fontSize = 11.sp)
                    }
                    if (base64 != null) {
                        FilledTonalButton(
                            onClick = { onPhotoSelected(null) },
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun compressAndToBase64(bitmap: Bitmap): String {
    val maxDimension = 800
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val resizedBitmap = if (originalWidth > maxDimension || originalHeight > maxDimension) {
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        val newWidth = if (originalWidth > originalHeight) maxDimension else (maxDimension * aspectRatio).toInt()
        val newHeight = if (originalHeight > originalWidth) maxDimension else (maxDimension / aspectRatio).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else {
        bitmap
    }

    val outputStream = ByteArrayOutputStream()
    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

@Preview(showBackground = true)
@Composable
fun KycStepPreview() {
    SecurePayAgentTheme {
        KycStep(
            state = EnrollmentUiState(draft = EnrollmentDraft(customerName = "John Doe", nationalId = "12345678", phoneNumber = "0712345678")),
            onNameChange = {},
            onNationalIdChange = {},
            onPhoneChange = {},
            onPhotoSelected = {},
            onIdFrontSelected = {},
            onIdBackSelected = {}
        )
    }
}
