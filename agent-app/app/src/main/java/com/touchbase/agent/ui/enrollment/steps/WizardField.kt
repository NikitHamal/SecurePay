package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M-KOPA "Start Application" field atoms: notched-outline boxes with the
 * floating label on the border, transparent container, ~4dp corners — tinted
 * with the Touch Base gold theme.
 */
@Composable
private fun wizardFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    errorContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

private val WizardFieldShape = RoundedCornerShape(4.dp)

@Composable
fun WizardTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }) else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        supportingText = {
            if (isError && supportingText != null) {
                Text(supportingText, color = MaterialTheme.colorScheme.error)
            }
        },
        trailingIcon = trailing,
        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        colors = wizardFieldColors(),
        shape = WizardFieldShape,
        modifier = modifier.fillMaxWidth()
    )
}

/** Outlined dropdown with the notch label + trailing chevron — M-KOPA style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            isError = isError,
            supportingText = {
                if (isError && supportingText != null) {
                    Text(supportingText, color = MaterialTheme.colorScheme.error)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            colors = wizardFieldColors(),
            shape = WizardFieldShape,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Horizontal green-tinted radio options — M-KOPA style ("Gender: (•) Male ( ) Female"). */
@Composable
fun WizardRadioRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    RadioButton(
                        selected = selected == option,
                        onClick = { onSelect(option) }
                    )
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected == option) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** M-KOPA section header: small tinted icon + bold white title. */
@Composable
fun WizardSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** The "(+233) 537995936 🇬🇭" phone-number field used on the customer info form. */
@Composable
fun WizardPhoneField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("(+233) XX XXX XXXX", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        isError = isError,
        supportingText = {
            if (isError && supportingText != null) {
                Text(supportingText, color = MaterialTheme.colorScheme.error)
            }
        },
        trailingIcon = { Text("🇬🇭", fontSize = 18.sp, modifier = Modifier.padding(end = 4.dp)) },
        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        colors = wizardFieldColors(),
        shape = WizardFieldShape,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Date-of-birth field backed by the Material 3 date picker so the agent never
 * types a date by hand. The stored + wire format stays dd/MM/yyyy (the picker
 * only changes the *input method*); we convert to/from epoch millis with
 * java.time in the device's default zone (Ghana is UTC+0, so no DST surprises).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = dobToMillis(value)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = DOB_YEAR_RANGE
    )

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }
        } else {
            null
        },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = {
            if (supportingText != null) {
                Text(
                    supportingText,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "Pick $label",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        colors = wizardFieldColors(),
        shape = WizardFieldShape,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showPicker = true }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(millisToDob(it)) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Selectable birth years — mirrors the dashboard's native DOB bounds (1930..2012). */
private val DOB_YEAR_RANGE = 1930..2012

/** dd/MM/yyyy -> start-of-day epoch millis, or null when blank / unparseable. */
private fun dobToMillis(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

/** start-of-day epoch millis -> dd/MM/yyyy. */
private fun millisToDob(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
