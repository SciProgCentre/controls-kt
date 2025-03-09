package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.manager.ClockManager
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.request
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A binding that is used to describe device functionality
 */
public sealed interface ConstructorElement

/**
 * A binding that exposes device property as read-only state
 */
public class PropertyConstructorElement<T>(
    public val device: Device,
    public val propertyName: String,
    public val state: DeviceState<T>,
) : ConstructorElement

/**
 * A binding for independent state like a timer
 */
public class StateConstructorElement<T>(
    public val state: DeviceState<T>,
) : ConstructorElement

public class ConnectionConstrucorElement(
    public val reads: Collection<DeviceState<*>>,
    public val writes: Collection<DeviceState<*>>,
) : ConstructorElement

public class ModelConstructorElement(
    public val model: ModelConstructor,
) : ConstructorElement


public interface StateContainer : ContextAware, CoroutineScope {
    public val constructorElements: Set<ConstructorElement>
    public fun registerElement(constructorElement: ConstructorElement)
    public fun unregisterElement(constructorElement: ConstructorElement)


    /**
     * Bind an action to a [DeviceState]. [onChange] block is performed on each state change
     *
     * Optionally provide [writes] - a set of states that this change affects.
     */
    public fun <T> DeviceState<T>.onNext(
        writes: Collection<DeviceState<*>> = emptySet(),
        reads: Collection<DeviceState<*>> = emptySet(),
        onChange: suspend (T) -> Unit,
    ): Job = valueFlow.onEach(onChange).launchIn(this@StateContainer).also {
        registerElement(ConnectionConstrucorElement(reads + this, writes))
    }

    public fun <T> DeviceState<T>.onChange(
        writes: Collection<DeviceState<*>> = emptySet(),
        reads: Collection<DeviceState<*>> = emptySet(),
        onChange: suspend (prev: T, next: T) -> Unit,
    ): Job = valueFlow.runningFold(Pair(value, value)) { pair, next ->
        Pair(pair.second, next)
    }.onEach { pair ->
        if (pair.first != pair.second) {
            onChange(pair.first, pair.second)
        }
    }.launchIn(this@StateContainer).also {
        registerElement(ConnectionConstrucorElement(reads + this, writes))
    }
}

/**
 * Register a [state] in this container. The state is not registered as a device property if [this] is a [DeviceConstructor]
 */
public fun <T, D : DeviceState<T>> StateContainer.registerState(state: D): D {
    registerElement(StateConstructorElement(state))
    return state
}

/**
 * Create a register a [MutableDeviceState]
 */
public fun <T> StateContainer.stateOf(initialValue: T): MutableDeviceState<T> = registerState(
    MutableDeviceState(initialValue)
)

public fun <T : ModelConstructor> StateContainer.model(model: T): T {
    registerElement(ModelConstructorElement(model))
    return model
}

/**
 * Create and register a timer state.
 */
public fun StateContainer.timer(tick: Duration): TimerState =
    registerState(TimerState(context.request(ClockManager), tick))

/**
 * Register a new timer and perform [block] on its change
 */
public fun StateContainer.onTimer(
    tick: Duration,
    writes: Collection<DeviceState<*>> = emptySet(),
    reads: Collection<DeviceState<*>> = emptySet(),
    block: suspend (prev: Instant, next: Instant) -> Unit,
): Job = timer(tick).onChange(writes = writes, reads = reads, onChange = block)

public enum class DefaultTimer(public val duration: Duration){
    REALTIME(5.milliseconds),
    VERY_FAST(10.milliseconds),
    FAST(20.milliseconds),
    MEDIUM(50.milliseconds),
    SLOW(100.milliseconds),
    VERY_SLOW(500.milliseconds),
}

/**
 * Perform an action on default timer
 */
public fun StateContainer.onTimer(
    defaultTimer: DefaultTimer = DefaultTimer.FAST,
    writes: Collection<DeviceState<*>> = emptySet(),
    reads: Collection<DeviceState<*>> = emptySet(),
    block: suspend (prev: Instant, next: Instant) -> Unit,
): Job = timer(defaultTimer.duration).onChange(writes = writes, reads = reads, onChange = block)
//TODO implement timer pooling

public fun <T, R> StateContainer.mapState(
    origin: DeviceState<T>,
    transformation: (T) -> R,
): DeviceStateWithDependencies<R> = registerState(DeviceState.map(origin, transformation))


public fun <T, R> StateContainer.flowState(
    origin: DeviceState<T>,
    initialValue: R,
    transformation: suspend FlowCollector<R>.(T) -> Unit,
): DeviceStateWithDependencies<R> {
    val state = MutableDeviceState(initialValue)
    origin.valueFlow.transform(transformation).onEach { state.value = it }.launchIn(this)
    return registerState(state.withDependencies(setOf(origin)))
}

/**
 * Create a new state by combining two existing ones
 */
public fun <T1, T2, R> StateContainer.combineState(
    first: DeviceState<T1>,
    second: DeviceState<T2>,
    transformation: (T1, T2) -> R,
): DeviceState<R> = registerState(DeviceState.combine(first, second, transformation))

/**
 * Create and start binding between [sourceState] and [targetState]. Changes made to [sourceState] are automatically
 * transferred onto [targetState], but not vise versa.
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public fun <T> StateContainer.bind(sourceState: DeviceState<T>, targetState: MutableDeviceState<T>): Job {
    val descriptor = ConnectionConstrucorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)
    return sourceState.valueFlow.onEach {
        targetState.value = it
    }.launchIn(this).apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}

/**
 * Create and start binding between [sourceState] and [targetState]. Changes made to [sourceState] are automatically
 * transferred onto [targetState] via [transformation], but not vise versa.
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public fun <T, R> StateContainer.transformTo(
    sourceState: DeviceState<T>,
    targetState: MutableDeviceState<R>,
    transformation: suspend (T) -> R,
): Job {
    val descriptor = ConnectionConstrucorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)
    return sourceState.valueFlow.onEach {
        targetState.value = transformation(it)
    }.launchIn(this).apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}

/**
 * Register [ConstructorElement] that combines values from [sourceState1] and [sourceState2] using [transformation].
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public fun <T1, T2, R> StateContainer.combineTo(
    sourceState1: DeviceState<T1>,
    sourceState2: DeviceState<T2>,
    targetState: MutableDeviceState<R>,
    transformation: suspend (T1, T2) -> R,
): Job {
    val descriptor = ConnectionConstrucorElement(setOf(sourceState1, sourceState2), setOf(targetState))
    registerElement(descriptor)
    return kotlinx.coroutines.flow.combine(sourceState1.valueFlow, sourceState2.valueFlow, transformation).onEach {
        targetState.value = it
    }.launchIn(this).apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}

/**
 * Register [ConstructorElement] that combines values from [sourceStates] using [transformation].
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public inline fun <reified T, R> StateContainer.combineTo(
    sourceStates: Collection<DeviceState<T>>,
    targetState: MutableDeviceState<R>,
    noinline transformation: suspend (Array<T>) -> R,
): Job {
    val descriptor = ConnectionConstrucorElement(sourceStates, setOf(targetState))
    registerElement(descriptor)
    return kotlinx.coroutines.flow.combine(sourceStates.map { it.valueFlow }, transformation).onEach {
        targetState.value = it
    }.launchIn(this).apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}