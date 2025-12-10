package space.kscience.controls.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.propertyFlow

@DslMarker
public annotation class Drawable2DBuilder

@Drawable2DBuilder
public class DeviceDrawable2DStore(public val scope: CoroutineScope, public val size: Size) {
    public val drawableFlow: MutableStateFlow<Map<String, DeviceDrawable2D>> = MutableStateFlow(emptyMap())
}

public fun DeviceDrawable2DStore.emit(id: String, drawable2D: DeviceDrawable2D) {
    drawableFlow.value += (id to drawable2D)
}

public fun DeviceDrawable2DStore.emitAll(drawables: Map<String, DeviceDrawable2D>) {
    drawableFlow.value += drawables
}


/**
 * Fill drawables from a discrete
 */
public fun DeviceDrawable2DStore.observe(id: String, flow: Flow<DeviceDrawable2D>): Job = flow.onEach {
    drawableFlow.value += (id to it)
}.launchIn(scope)

/**
 * Observe single [ValueState]
 */
public fun <T> DeviceDrawable2DStore.observeState(
    state: ValueState<T>,
    id: String = state.toString(),
    transform: suspend DeviceDrawable2DStore.(T) -> DeviceDrawable2D,
): Job = observe(id, state.subscribe().map { transform(this, it) })

/**
 * Observe a single [Device] property
 */
public fun <T, D : Device, P : DevicePropertySpec<D, T>> DeviceDrawable2DStore.observeProperty(
    device: D,
    devicePropertySpec: DevicePropertySpec<D, T>,
    id: String = devicePropertySpec.toString(),
    transform: suspend DeviceDrawable2DStore.(T) -> DeviceDrawable2D,
): Job = observe(id, device.propertyFlow(devicePropertySpec).map { transform(this, it) })

@Composable
public fun Device2DCanvas(
    modifier: Modifier = Modifier,
    onDraw: DrawScope.() -> Unit = {},
    flowBuilder: suspend DeviceDrawable2DStore.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(Size(100f, 100f)) }

    val store = remember(canvasSize) {
        DeviceDrawable2DStore(coroutineScope, canvasSize).apply {
            coroutineScope.launch {
                flowBuilder()
            }
        }
    }

    val drawables by store.drawableFlow.collectAsState()

    key(store) {
        Canvas(modifier.onGloballyPositioned {
            canvasSize = it.size.toSize()
        }) {
            clipRect {
                drawables.values.forEach {
                    with(it) { draw() }
                }
                onDraw()
            }
        }
    }
}