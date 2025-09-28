package space.kscience.controls.demo.constructor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import space.kscience.controls.compose.asComposeState
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import kotlin.time.Duration.Companion.seconds

internal fun StateContainer.debugState(name: String, state: DeviceState<Amount<*>>): Job =
    state.useValue(this) { value ->
        println("(${clock.now()}) $name: ${value.value}")
    }


@Composable
fun <T> StateContainer.displayState(
    name: String,
    state: DeviceState<T>,
    content: @Composable (T) -> Unit = {
        Text(it.toString())
    }
) {
    Row(
        modifier = Modifier.fillMaxWidth().border(2.dp, Color.Blue).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$name:", modifier = Modifier.weight(1f))

        val value by state.asComposeState(coroutineContext, 0.1.seconds)

        content(value)
    }
}

@Composable
fun <U: UnitsOfMeasurement> StateContainer.slider(
    name: String,
    state: MutableDeviceState<NumericAmount<U>>,
    valueRange: ClosedFloatingPointRange<Float>
) {

    val value: NumericAmount<U> by state.asComposeState(coroutineContext, 0.1.seconds)

    Row(
        modifier = Modifier.fillMaxWidth().border(2.dp, Color.Blue).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$name: ")
        Slider(
            value = value.value.toFloat(),
            onValueChange = { state.value = NumericAmount(it) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
    }
}