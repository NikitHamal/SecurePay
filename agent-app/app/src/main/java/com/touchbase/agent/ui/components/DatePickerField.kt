package com.touchbase.agent.ui.components

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "DD/MM/YYYY",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = remember(value) {
        Calendar.getInstance().apply {
            val parts = value.split("/")
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()?.minus(1)
                val y = parts[2].toIntOrNull()
                if (d != null && m != null && y != null) {
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.MONTH, m)
                    set(Calendar.YEAR, y)
                }
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        modifier = modifier.clickable {
            DatePickerDialog(
                context,
                { _, year: Int, month: Int, dayOfMonth: Int ->
                    val formatted = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    onValueChange(formatted)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        singleLine = true
    )
}
