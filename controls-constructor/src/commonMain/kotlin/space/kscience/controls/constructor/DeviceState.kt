package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import kotlin.reflect.KProperty

/**
 * An observable state of a device
 */
public interface DeviceState<out T> {
    public val value: T

    /**
     * Subscribe on changes made to this [DeviceState]. The first value in a subscription is always the current value.
     *
     * Som implementation could
     */
    public fun subscribe(): Flow<T>

    override fun toString(): String

    public companion object
}


public operator fun <T> DeviceState<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * Use each value (including initial one) in a given [scope]
 */
public fun <T> DeviceState<T>.useValue(scope: CoroutineScope, block: suspend (T) -> Unit): Job =
    subscribe().onEach(block).launchIn(scope)

/**
 * A mutable state of a device
 */
public interface MutableDeviceState<T> : DeviceState<T>, FlowCollector<T> {
    override var value: T

    /**
     * Quasi-synchronous emit value
     */
    override suspend fun emit(value: T)
}

public operator fun <T> MutableDeviceState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

/**
 * Device state with a value that depends on other device states
 */
public interface DeviceStateWithDependencies<T> : DeviceState<T> {
    public val dependencies: Collection<DeviceState<*>>
}

public fun <T> DeviceState<T>.withDependencies(
    dependencies: Collection<DeviceState<*>>,
): DeviceStateWithDependencies<T> = object : DeviceStateWithDependencies<T>, DeviceState<T> by this {
    override val dependencies: Collection<DeviceState<*>> = dependencies
}

/**
 * Create a new read-only [DeviceState] that mirrors receiver state by mapping the value with [mapper].
 *
 * This implementation is thread safe and "cold" meaning that it computes values and flows on-demand.
 * The same flow is shared with all subscribers, so it is user's responsibility to ensure that the source state allows multiple subscriptions.
 */
public fun <T, R> DeviceState.Companion.map(
    state: DeviceState<T>,
    mapper: (T) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state)

    override val value: R get() = mapper(state.value)

    override fun subscribe(): Flow<R> = state.subscribe().map(mapper)

    override fun toString(): String = "DeviceState.map(state=${state}, mapper=$mapper)"
}

public fun <T, R> DeviceState<T>.map(mapper: (T) -> R): DeviceStateWithDependencies<R> =
    DeviceState.map(this, mapper)

/**
 * Create a new read-only [DeviceState] that mirrors receiver state by mapping the value with [mapper].
 *
 * This implementation
 */
public fun <T, R> DeviceState.Companion.map(
    scope: CoroutineScope,
    state: DeviceState<T>,
    mapper: (T) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state)

    val valueFlow: SharedFlow<R> = state.subscribe().map(mapper)
        .shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(state.value)

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.map(state=${state}, mapper=$mapper)"
}

public fun <T, R> DeviceState<T>.map(scope: CoroutineScope, mapper: (T) -> R): DeviceStateWithDependencies<R> =
    DeviceState.map(scope, this, mapper)


/**
 * A hot variant of [DeviceState.map]. It allows suspended transformations
 */
public fun <T, R> DeviceState.Companion.transform(
    scope: CoroutineScope,
    state: DeviceState<T>,
    initialValue: R,
    transform: suspend (T) -> R
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies: Collection<DeviceState<*>> = listOf(state)

    private val valueFlow = MutableStateFlow<R>(initialValue)

    val transformJob = scope.launch {
        valueFlow.emit(transform(state.value))
        state.subscribe().collect {
            valueFlow.emit(transform(it))
        }
    }

    override val value: R get() = valueFlow.value

    override fun subscribe(): StateFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.transform(state=${state}, transform=$transform)"
}

public suspend fun <T, R> DeviceState<T>.transform(
    scope: CoroutineScope,
    transform: suspend (T) -> R
): DeviceStateWithDependencies<R> = DeviceState.transform(
    scope = scope,
    state = this,
    initialValue = transform(value),
    transform = transform
)

/**
 * Combine two device states into one read-only [DeviceState]. Only the latest value of each state is used.
 */
public fun <T1, T2, R> DeviceState.Companion.combine(
    scope: CoroutineScope,
    state1: DeviceState<T1>,
    state2: DeviceState<T2>,
    mapper: (T1, T2) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2)

    val valueFlow: SharedFlow<R> = combine(state1.subscribe(), state2.subscribe(), mapper)
        .shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(state1.value, state2.value)

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.combine(state1=$state1, state2=$state2)"
}

/**
 * Combines three device states into a single device state by applying a mapping function.
 *
 * @param state1 The first device state to combine.
 * @param state2 The second device state to combine.
 * @param state3 The third device state to combine.
 * @param mapper The mapping function that combines the values of the three states into a resulting value.
 * @return A new device state whose value depends on the combined values of the provided states.
 */
public fun <T1, T2, T3, R> DeviceState.Companion.combine(
    scope: CoroutineScope,
    state1: DeviceState<T1>,
    state2: DeviceState<T2>,
    state3: DeviceState<T3>,
    mapper: (T1, T2, T3) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2, state3)

    val valueFlow: SharedFlow<R> = combine(state1.subscribe(), state2.subscribe(), state3.subscribe(), mapper)
        .shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(state1.value, state2.value, state3.value)

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.combine(state1=$state1, state2=$state2, state3=$state3)"
}

public fun <T1, T2, T3, T4, R> DeviceState.Companion.combine(
    scope: CoroutineScope,
    state1: DeviceState<T1>,
    state2: DeviceState<T2>,
    state3: DeviceState<T3>,
    state4: DeviceState<T4>,
    mapper: (T1, T2, T3, T4) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2, state3, state4)

    val valueFlow: SharedFlow<R> = combine(
        flow = state1.subscribe(),
        flow2 = state2.subscribe(),
        flow3 = state3.subscribe(),
        flow4 = state4.subscribe(),
        transform = mapper
    ).shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(state1.value, state2.value, state3.value, state4.value)

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String =
        "DeviceState.combine(state1=$state1, state2=$state2, state3=$state3, state4=$state4)"
}

/**
 * Combines multiple [DeviceState] instances into a single [DeviceStateWithDependencies].
 * The combined state value is derived by applying the provided [mapper] function to the collection of individual state values.
 *
 * @param T the type of the individual state values.
 * @param R the type of the combined state value.
 * @param states a collection of [DeviceState] instances whose values are to be combined.
 * @param mapper a function that takes a collection of state values and maps it to a combined value.
 * @return a [DeviceStateWithDependencies] representing the combined state, which has dependencies on the input [states].
 */
public fun <T, R> DeviceState.Companion.combine(
    scope: CoroutineScope,
    states: Collection<DeviceState<T>>,
    mapper: (List<T>) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = states

    @Suppress("UNCHECKED_CAST")
    val valueFlow: SharedFlow<R> = combine(states.map { it.subscribe() }) { array: Array<Any?> ->
        mapper(array.asList() as List<T>)
    }.shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(states.map { it.value })

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.combine(states=${states.joinToString()})"
}

/**
 * Combines multiple `DeviceState` instances into a single `DeviceStateWithDependencies`.
 * The combined state is derived by applying the provided `mapper` function to the values of the input states.
 *
 * @param T the type of individual states' values.
 * @param K the type of the keys in the input state map.
 * @param R the type of the resulting combined state value.
 * @param states a map of keys to `DeviceState` instances representing the individual states to be combined.
 * @param mapper a function that takes a map of key-value pairs (where keys are from `states` and values are the current
 *        values of the corresponding `DeviceState` instances) and produces the value for the combined state.
 * @return a `DeviceStateWithDependencies` instance representing the combined state, with its value computed
 *         dynamically based on the input states and the `mapper` function.
 */
public fun <T, K, R> DeviceState.Companion.combine(
    scope: CoroutineScope,
    states: Map<K, DeviceState<T>>,
    mapper: (Map<K, T>) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = states.values

    private val entries = states.entries.toList()

    @Suppress("UNCHECKED_CAST")
    val valueFlow: SharedFlow<R> = combine(entries.map { it.value.subscribe() }) { array: Array<Any?> ->
        // restore mapping
        mapper(entries.indices.associate { entries[it].key to (array[it] as T) })
    }.shareIn(scope, SharingStarted.Eagerly)

    override val value: R get() = mapper(states.mapValues { it.value.value })

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun toString(): String = "DeviceState.associate(states=${states})"
}

/**
 * Transforms a [DeviceState] containing a [Amount] with specific [UnitsOfMeasurement]
 * into a [DeviceState] containing a [Double] representing the underlying numerical value.
 *
 * @return A new [DeviceState] object that provides the numerical value as a [Double].
 */
public fun DeviceState<Amount<out UnitsOfMeasurement>>.values(): DeviceState<Double> =
    DeviceState.map(this) { it.value }
