package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.simulationDispatcher
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.request
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A binding that is used to describe device functionality
 */
public sealed interface ConstructorElement

/**
 * A binding that exposes device property as a read-only state
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

public class ConnectionConstructorElement(
    public val reads: Collection<DeviceState<*>>,
    public val writes: Collection<DeviceState<*>>,
) : ConstructorElement

public class ModelConstructorElement(
    public val model: ModelConstructor,
) : ConstructorElement

/**
 * Interface representing a container for managing state-based elements and interactions within a device context.
 * It extends [ContextAware] and [CoroutineScope], allowing it to work within a coroutine-based environment
 * while maintaining context awareness.
 */
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
        registerElement(ConnectionConstructorElement(reads + this, writes))
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
        registerElement(ConnectionConstructorElement(reads + this, writes))
    }
}

public interface Model : StateContainer

/**
 * Run simulation using context simulation dispatcher
 */
public suspend fun <M : Model> M.runSimulation(
    block: suspend M.() -> Unit
) {
    withContext(context.simulationDispatcher) {
        block()
    }
}

public val StateContainer.states
    get() = constructorElements.filterIsInstance<StateConstructorElement<*>>().map { it.state }

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
    registerState(TimerState(context.plugins[ClockManager] ?: context.request(ClockManager), tick))

/**
 * Register a new timer and perform [block] on its change
 */
public fun StateContainer.onTimer(
    tick: Duration,
    writes: Collection<DeviceState<*>> = emptySet(),
    reads: Collection<DeviceState<*>> = emptySet(),
    block: suspend (prev: Instant, next: Instant) -> Unit,
): Job = timer(tick).onChange(writes = writes, reads = reads, onChange = block)

public enum class DefaultTimer(public val duration: Duration) {
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

/**
 * Perform a complex transformation on state change
 */
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


public fun <T1, T2, T3, R> StateContainer.combineState(
    first: DeviceState<T1>,
    second: DeviceState<T2>,
    third: DeviceState<T3>,
    transformation: (T1, T2, T3) -> R,
): DeviceState<R> = registerState(DeviceState.combine(first, second, third, transformation))

/**
 * Combines multiple device states into a single state by applying a transformation function.
 *
 * @param T the type of the individual state values.
 * @param R the type of the combined state value.
 * @param states a collection of [DeviceState] instances to be combined.
 * @param transformation a function that takes an array of individual state values and maps it to a combined value.
 * @return a new [DeviceState] representing the combined state, with the value computed by the transformation function.
 */
public fun <T, R> StateContainer.combineState(
    states: Collection<DeviceState<T>>,
    transformation: (List<T>) -> R,
): DeviceState<R> = registerState(DeviceState.combine(states, transformation))

/**
 * Combines multiple [DeviceState] instances into a new combined [DeviceState].
 * The combined state is created by applying the specified transformation function to the current values
 * of the input states.
 *
 * @param K the type of keys in the input `states` map.
 * @param T the type of individual states' values.
 * @param R the resulting type of the value after the `transformation` is applied.
 * @param states a map of keys to `DeviceState` instances representing the individual states to be combined.
 * @param transformation a function that takes a map of key-value pairs (where keys are the same as in the `states` map
 *        and values are the current values of the associated `DeviceState` instances) and produces the combined state's value.
 * @return a new `DeviceState` instance representing the combined state, with its value derived dynamically
 *         from the input states and the `transformation` function.
 */
public fun <K, T, R> StateContainer.combineState(
    states: Map<K, DeviceState<T>>,
    transformation: (Map<K, T>) -> R,
): DeviceState<R> = registerState(DeviceState.combine(states, transformation))

/**
 * Create and start binding between [sourceState] and [targetState]. Changes made to [sourceState] are automatically
 * transferred onto [targetState], but not vise versa.
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public fun <T> StateContainer.bindState(sourceState: DeviceState<T>, targetState: MutableDeviceState<T>): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = sourceState.value
        sourceState.valueFlow.collect {
            targetState.value = it
        }
    }.apply {
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
public fun <T, R> StateContainer.bindTransformedState(
    sourceState: DeviceState<T>,
    targetState: MutableDeviceState<R>,
    transformation: suspend (T) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceState.value)
        sourceState.valueFlow.collect {
            targetState.value = transformation(it)
        }
    }.apply {
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
public fun <T1, T2, R> StateContainer.bindCombinedState(
    sourceState1: DeviceState<T1>,
    sourceState2: DeviceState<T2>,
    targetState: MutableDeviceState<R>,
    transformation: suspend (T1, T2) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState1, sourceState2), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceState1.value, sourceState2.value)
        combine(sourceState1.valueFlow, sourceState2.valueFlow, transformation).collect {
            targetState.value = it
        }
    }.apply {
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
public inline fun <reified T, R> StateContainer.bindCombinedState(
    sourceStates: Collection<DeviceState<T>>,
    targetState: MutableDeviceState<R>,
    noinline transformation: suspend (Array<T>) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(sourceStates, setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceStates.map { it.value }.toTypedArray())
        combine(sourceStates.map { it.valueFlow }, transformation).collect {
            targetState.value = it
        }
    }.apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}