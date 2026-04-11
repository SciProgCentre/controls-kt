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
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName

@DslMarker
public annotation class Drawable2DBuilder

public typealias ControlsDrawable2DElements = Map<Name, ControlsDrawable2D>

public interface ControlsDrawable2DBuilder : FlowCollector<ControlsDrawable2DElements> {
    public val scope: CoroutineScope
    public val size: Size
}

@Drawable2DBuilder
public class ControlsDrawable2DStore(override val scope: CoroutineScope, override val size: Size) :
    ControlsDrawable2DBuilder {
    public val content: MutableStateFlow<ControlsDrawable2DElements> = MutableStateFlow(emptyMap())

    override suspend fun emit(value: ControlsDrawable2DElements) {
        content.emit(value)
    }
}

/**
 * Emit single drawable element
 */
public suspend fun ControlsDrawable2DBuilder.emit(id: Name, drawable2D: ControlsDrawable2D) {
    emit(mapOf(id to drawable2D))
}


/**
 * Emit single drawable element
 */
public suspend fun ControlsDrawable2DBuilder.emit(id: String, drawable2D: ControlsDrawable2D) {
    emit(mapOf(id.parseAsName() to drawable2D))
}

/**
 * Emit multiple drawable elements
 */
public suspend fun ControlsDrawable2DBuilder.emitAll(drawables: ControlsDrawable2DElements) {
    emit(drawables)
}

/**
 * Fill drawables from a flow of drawable states
 */
public fun ControlsDrawable2DBuilder.updateById(
    id: Name,
    flow: Flow<ControlsDrawable2D>
): Job {
    return flow.onEach {
        emit(id, it)
    }.launchIn(scope)
}

/**
 * Fill drawables from a flow of drawable states
 */
public fun ControlsDrawable2DBuilder.updateById(
    id: String,
    flow: Flow<ControlsDrawable2D>
): Job = updateById(id.parseAsName(), flow)

/**
 * Observe single [ValueState] and change content on its change
 */
public fun <T> ControlsDrawable2DBuilder.observeState(
    state: ValueState<T>,
    id: Name = NameToken("@state", state.hashCode().toHexString()).asName(),
    transform: suspend ControlsDrawable2DBuilder.(T) -> ControlsDrawable2D,
): Job = updateById(id, state.subscribe().map { transform(this, it) })

public fun <T> ControlsDrawable2DBuilder.observeState(
    state: ValueState<T>,
    id: String,
    transform: suspend ControlsDrawable2DBuilder.(T) -> ControlsDrawable2D,
): Job = observeState(state, id.parseAsName(), transform)

/**
 * Observe a single [Device] property
 */
public fun <T, D : Device, P : DevicePropertySpec<D, T>> ControlsDrawable2DStore.observeProperty(
    device: D,
    devicePropertySpec: DevicePropertySpec<D, T>,
    id: String = devicePropertySpec.toString(),
    transform: suspend ControlsDrawable2DBuilder.(T) -> ControlsDrawable2D,
): Job = updateById(id, device.propertyFlow(devicePropertySpec).map { transform(this, it) })

/**
 * Create a canvas for drawing 2D graphics.
 * @param onDraw draw additional elements
 * @param builder create and update 2d elements
 */
@Composable
public fun Controls2DCanvas(
    modifier: Modifier = Modifier,
    onDraw: DrawScope.() -> Unit = {},
    builder: suspend ControlsDrawable2DBuilder.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(Size(100f, 100f)) }

    val store = remember(canvasSize) {
        ControlsDrawable2DStore(coroutineScope, canvasSize).apply {
            coroutineScope.launch {
                builder()
            }
        }
    }

    val drawables by store.content.collectAsState()

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