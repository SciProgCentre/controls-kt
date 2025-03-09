package space.kscience.controls.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle

@Composable
public fun NumberTextField(
    value: Number,
    onValueChange: (Number) -> Unit,
    step: Double = 0.0,
    formatter: (Number) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    var isError by remember { mutableStateOf(false) }

    Row (verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        step.takeIf { it > 0.0 }?.let {
            IconButton({ onValueChange(value.toDouble() - step) }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "decrease value")
            }
        }
        TextField(
            value = formatter(value),
            onValueChange = { stringValue: String ->
                val number = stringValue.toDoubleOrNull()
                number?.let { onValueChange(number) }
                isError = number == null
            },
            isError = isError,
            enabled = enabled,
            textStyle = textStyle,
            label = label,
            supportingText = supportingText,
            singleLine = true,
            shape = shape,
            colors = colors,
            modifier = Modifier.weight(1f)
        )
        step.takeIf { it > 0.0 }?.let {
            IconButton({ onValueChange(value.toDouble() + step) }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "increase value")
            }
        }
    }
}