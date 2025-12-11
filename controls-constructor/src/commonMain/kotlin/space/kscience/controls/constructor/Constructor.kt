package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.request
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Instant

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
    public val state: ValueState<T>,
) : ConstructorElement

/**
 * A binding for independent state like a timer or model state
 */
public class StateConstructorElement<T>(
    public val state: ValueState<T>,
    public val name: Name? = null
) : ConstructorElement

/**
 * Represents a constructor element that defines a connection between states.
 *
 * This class specifies the states that are read and written as part of the connection.
 *
 * @property reads A collection of [ValueState] instances that represent the states being read by this connection.
 * @property writes A collection of [ValueState] instances that represent the states being written by this connection.
 */
public class ConnectionConstructorElement(
    public val reads: Collection<ValueState<*>>,
    public val writes: Collection<ValueState<*>>,
) : ConstructorElement

/**
 * A class representing a constructor element that is associated with a specific model instance.
 *
 * This element serves as a binding between the construction process and the model it represents.
 * The model instance is provided during the initialization of this element.
 *
 * @property model The model instance associated with this constructor element. It provides access
 * to the required context and state dependencies.
 */
public class ModelConstructorElement(
    public val model: Model,
    public val name: Name? = null
) : ConstructorElement

/**
 * Represents a container for managing system or devices states.
 * This interface enables interaction with context-awareness and coroutine-based operations.
 *
 * The `StateContainer` serves as a central abstraction for maintaining and manipulating
 * states through components described by [ConstructorElement].
 *
 * @property constructorElements A collection of [ConstructorElement] instances
 * representing the elements used to construct and describe the current state configuration.
 */
public interface Constructor : ContextAware, CoroutineScope {
    public val constructorElements: Set<ConstructorElement>
}

/**
 * Interface representing a container for managing state-based elements and interactions within a device context.
 * It extends [ContextAware] and [CoroutineScope], allowing it to work within a coroutine-based environment
 * while maintaining context awareness.
 */
public interface MutableConstructor : Constructor {
    public fun registerElement(constructorElement: ConstructorElement)
    public fun unregisterElement(constructorElement: ConstructorElement)


    /**
     * Bind an action to a [ValueState]. [onChange] block is performed on each state change
     *
     * Optionally provide [writes] - a set of states that this change affects.
     */
    public fun <T> ValueState<T>.onNext(
        writes: Collection<ValueState<*>> = emptySet(),
        reads: Collection<ValueState<*>> = emptySet(),
        onChange: suspend (T) -> Unit,
    ): Job = subscribe().onEach(onChange).launchIn(this@MutableConstructor).also {
        registerElement(ConnectionConstructorElement(reads + this, writes))
    }

    public fun <T> ValueState<T>.onChange(
        writes: Collection<ValueState<*>> = emptySet(),
        reads: Collection<ValueState<*>> = emptySet(),
        onChange: suspend (prev: T, next: T) -> Unit,
    ): Job = subscribe().runningFold(Pair(value, value)) { pair, next ->
        Pair(pair.second, next)
    }.onEach { pair ->
        if (pair.first != pair.second) {
            onChange(pair.first, pair.second)
        }
    }.launchIn(this@MutableConstructor).also {
        registerElement(ConnectionConstructorElement(reads + this, writes))
    }
}


public val Constructor.states: List<ValueState<Any?>>
    get() = constructorElements.filterIsInstance<StateConstructorElement<*>>().map { it.state }

/**
 * Register a [state] in this container. The state is not registered as a device property if [this] is a [DeviceConstructor]
 */
public fun <T, D : ValueState<T>> MutableConstructor.registerState(state: D): D {
    registerElement(StateConstructorElement(state))
    return state
}

/**
 * Create a register a [MutableValueState]
 */
public fun <T> MutableConstructor.stateOf(initialValue: T): MutableValueState<T> = registerState(
    MutableValueState(initialValue)
)


/**
 * Create and register a timer state.
 */
public fun MutableConstructor.timer(tick: Duration): TimerState =
    registerState(TimerState(context.plugins[ClockManager] ?: context.request(ClockManager), tick))

/**
 * Register operations that perform [block] on timer change.
 */
public fun MutableConstructor.onTimer(
    timer: TimerState,
    writes: Collection<ValueState<*>> = emptySet(),
    reads: Collection<ValueState<*>> = emptySet(),
    block: suspend (prev: Instant, next: Instant) -> Unit,
): Job = timer.onChange(writes = writes, reads = reads) { prev, next ->
    if (prev != Instant.DISTANT_PAST && next != Instant.DISTANT_FUTURE) {
        block(prev, next)
    }
}

/**
 * Register a new timer and perform [block] on its change.
 */
public fun MutableConstructor.onTimer(
    tick: Duration,
    writes: Collection<ValueState<*>> = emptySet(),
    reads: Collection<ValueState<*>> = emptySet(),
    block: suspend (prev: Instant, next: Instant) -> Unit,
): Job = onTimer(timer(tick), writes = writes, reads = reads, block = block)

/**
 * Register operation that performs [block] on next timer tick.
 */
public fun MutableConstructor.onTimer(
    timer: TimerState,
    writes: Collection<ValueState<*>> = emptySet(),
    reads: Collection<ValueState<*>> = emptySet(),
    block: suspend (next: Instant) -> Unit,
): Job = timer.onNext(writes = writes, reads = reads, onChange = block)

/**
 * Register a new timer and perform [block] on next tick
 */
public fun MutableConstructor.onTimer(
    tick: Duration,
    writes: Collection<ValueState<*>> = emptySet(),
    reads: Collection<ValueState<*>> = emptySet(),
    block: suspend (next: Instant) -> Unit,
): Job = timer(tick).onNext(writes = writes, reads = reads, onChange = block)

public fun <T, R> MutableConstructor.mapState(
    origin: ValueState<T>,
    transformation: (T) -> R,
): ValueStateWithDependencies<R> = registerState(ValueState.map(this, origin, transformation))

/**
 * Perform a complex transformation on state change
 */
public fun <T, R> MutableConstructor.flowState(
    origin: ValueState<T>,
    initialValue: R,
    transformation: suspend FlowCollector<R>.(T) -> Unit,
): ValueStateWithDependencies<R> {
    val state = MutableValueState(initialValue)
    origin.subscribe().transform(transformation).onEach { state.value = it }.launchIn(this)
    return registerState(state.withDependencies(setOf(origin)))
}

/**
 * Create a new state by combining two existing ones
 */
public fun <T1, T2, R> MutableConstructor.combineState(
    first: ValueState<T1>,
    second: ValueState<T2>,
    transformation: (T1, T2) -> R,
): ValueState<R> = registerState(ValueState.combine(this, first, second, transformation))


public fun <T1, T2, T3, R> MutableConstructor.combineState(
    first: ValueState<T1>,
    second: ValueState<T2>,
    third: ValueState<T3>,
    transformation: (T1, T2, T3) -> R,
): ValueState<R> = registerState(ValueState.combine(this, first, second, third, transformation))

public fun <T1, T2, T3, T4, R> MutableConstructor.combineState(
    first: ValueState<T1>,
    second: ValueState<T2>,
    third: ValueState<T3>,
    forth: ValueState<T4>,
    transformation: (T1, T2, T3, T4) -> R,
): ValueState<R> = registerState(ValueState.combine(this, first, second, third, forth, transformation))

/**
 * Combines multiple device states into a single state by applying a transformation function.
 *
 * @param T the type of the individual state values.
 * @param R the type of the combined state value.
 * @param states a collection of [ValueState] instances to be combined.
 * @param transformation a function that takes an array of individual state values and maps it to a combined value.
 * @return a new [ValueState] representing the combined state, with the value computed by the transformation function.
 */
public fun <T, R> MutableConstructor.combineState(
    states: Collection<ValueState<T>>,
    transformation: (List<T>) -> R,
): ValueState<R> = registerState(ValueState.combine(this, states, transformation))

/**
 * Combines multiple [ValueState] instances into a new combined [ValueState].
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
public fun <K, T, R> MutableConstructor.combineState(
    states: Map<K, ValueState<T>>,
    transformation: (Map<K, T>) -> R,
): ValueState<R> = registerState(ValueState.combine(this, states, transformation))

/**
 * Create and start binding between [sourceState] and [targetState]. Changes made to [sourceState] are automatically
 * transferred onto [targetState], but not vise versa.
 *
 * On resulting [Job] cancel the binding is unregistered
 */
public fun <T> MutableConstructor.bindState(sourceState: ValueState<T>, targetState: MutableValueState<T>): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = sourceState.value
        sourceState.subscribe().collect {
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
public fun <T, R> MutableConstructor.bindTransformedState(
    sourceState: ValueState<T>,
    targetState: MutableValueState<R>,
    transformation: suspend (T) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceState.value)
        sourceState.subscribe().collect {
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
public fun <T1, T2, R> MutableConstructor.bindCombinedState(
    sourceState1: ValueState<T1>,
    sourceState2: ValueState<T2>,
    targetState: MutableValueState<R>,
    transformation: suspend (T1, T2) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(setOf(sourceState1, sourceState2), setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceState1.value, sourceState2.value)
        combine(sourceState1.subscribe(), sourceState2.subscribe(), transformation).collect {
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
public inline fun <reified T, R> MutableConstructor.bindCombinedState(
    sourceStates: Collection<ValueState<T>>,
    targetState: MutableValueState<R>,
    noinline transformation: suspend (Array<T>) -> R,
): Job {
    val descriptor = ConnectionConstructorElement(sourceStates, setOf(targetState))
    registerElement(descriptor)

    return launch {
        targetState.value = transformation(sourceStates.map { it.value }.toTypedArray())
        combine(sourceStates.map { it.subscribe() }, transformation).collect {
            targetState.value = it
        }
    }.apply {
        invokeOnCompletion {
            unregisterElement(descriptor)
        }
    }
}