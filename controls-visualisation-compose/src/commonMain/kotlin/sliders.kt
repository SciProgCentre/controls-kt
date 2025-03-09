package space.kscience.controls.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState

@Composable
public fun Slider(
    deviceState: MutableDeviceState<Number>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: SliderColors = SliderDefaults.colors(),
) {
    androidx.compose.material3.Slider(
        value = deviceState.value.toFloat(),
        onValueChange = { deviceState.value = it },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        colors = colors,
    )
}

@Composable
public fun SliderIndicator(
    deviceState: DeviceState<Number>,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    androidx.compose.material3.Slider(
        value = deviceState.value.toFloat(),
        onValueChange = { /*do nothing*/ },
        modifier = modifier,
        enabled = false,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
    )
}