package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.time.ValueWithTime
import kotlin.reflect.KProperty
import kotlin.time.Instant

/**
 * An observable value state
 */
public interface ValueState<out T> {
    public val valueWithTime: ValueWithTime<T>

    public val value: T get() = valueWithTime.value

    public val time: Instant get() = valueWithTime.time


    /**
     * Subscribe on changes made to this [ValueState] with time. The first value in a subscription is always the current value.
     */
    public fun subscribeWithTime(): Flow<ValueWithTime<T>>

    /**
     * Subscribe on changes made to this [ValueState]. The first value in a subscription is always the current value.
     */
    public fun subscribe(): Flow<T> = subscribeWithTime().map { it.value }


    override fun toString(): String

    public companion object {
        public const val TYPE: String = "state"
    }
}


public operator fun <T> ValueState<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * Use each value (including initial one) in a given [scope]
 */
public fun <T> ValueState<T>.useValue(scope: CoroutineScope, block: suspend (T) -> Unit): Job =
    subscribe().onEach(block).launchIn(scope)

/**
 * A mutable state of a device
 */
public interface MutableValueState<T> : ValueState<T>, FlowCollector<T> {
    override var value: T

    /**
     * Quasi-synchronous emit value
     */
    override suspend fun emit(value: T)
}

public operator fun <T> MutableValueState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

/**
 * Device state with a value that depends on other device states
 */
public interface ValueStateWithDependencies<T> : ValueState<T> {
    public val dependencies: Collection<ValueState<*>>
}

public fun <T> ValueState<T>.withDependencies(
    dependencies: Collection<ValueState<*>>,
): ValueStateWithDependencies<T> = object : ValueStateWithDependencies<T>, ValueState<T> by this {
    override val dependencies: Collection<ValueState<*>> = dependencies
}

/**
 * Create a new read-only [ValueState] that mirrors receiver state by mapping the value with [mapper].
 *
 * This implementation is thread safe and "cold" meaning that it computes values and flows on-demand.
 * The same flow is shared with all subscribers, so it is user's responsibility to ensure that the source state allows multiple subscriptions.
 */
public fun <T, R> ValueState.Companion.map(
    state: ValueState<T>,
    mapper: (T) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = listOf(state)

    override val value: R get() = mapper(state.value)

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            mapper(state.valueWithTime.value),
            state.valueWithTime.time
        )

    override fun subscribe(): Flow<R> = state.subscribe().map(mapper)

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = state.subscribeWithTime().map {
        ValueWithTime(mapper(it.value), it.time)
    }

    override fun toString(): String = "DeviceState.map(state=${state.value}, value=$value)"
}

public fun <T, R> ValueState<T>.map(mapper: (T) -> R): ValueStateWithDependencies<R> =
    ValueState.map(this, mapper)

/**
 * Create a new read-only [ValueState] that mirrors receiver state by mapping the value with [mapper].
 *
 * This implementation
 */
public fun <T, R> ValueState.Companion.map(
    scope: CoroutineScope,
    state: ValueState<T>,
    mapper: (T) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = listOf(state)

    override val value: R get() = mapper(state.value)

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            mapper(state.valueWithTime.value),
            state.valueWithTime.time
        )

    val valueFlow: StateFlow<R> = state.subscribe().map(mapper)
        .stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): StateFlow<R> = valueFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = state.subscribeWithTime().map {
        ValueWithTime(mapper(it.value), it.time)
    }

    override fun toString(): String = "DeviceState.map(state=${state.value}, value=$value)"
}

public fun <T, R> ValueState<T>.map(scope: CoroutineScope, mapper: (T) -> R): ValueStateWithDependencies<R> =
    ValueState.map(scope, this, mapper)


/**
 * A hot variant of [ValueState.map]. It allows suspended transformations
 */
public fun <T, R> ValueState.Companion.transform(
    scope: CoroutineScope,
    state: ValueState<T>,
    initialValue: R,
    transform: suspend (T) -> R
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies: Collection<ValueState<*>> = listOf(state)

    private val valueFlow = MutableStateFlow<R>(initialValue)

    val transformJob = scope.launch {
        valueFlow.emit(transform(state.value))
        state.subscribe().collect {
            valueFlow.emit(transform(it))
        }
    }

    override val value: R get() = valueFlow.value

    override val valueWithTime: ValueWithTime<R> get() = ValueWithTime(valueFlow.value, state.valueWithTime.time)

    override fun subscribe(): StateFlow<R> = valueFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = state.subscribeWithTime().map {
        ValueWithTime(valueFlow.value, it.time)
    }

    override fun toString(): String = "DeviceState.transform(state=${state.value}, value=$value)"
}

public suspend fun <T, R> ValueState<T>.transform(
    scope: CoroutineScope,
    transform: suspend (T) -> R
): ValueStateWithDependencies<R> = ValueState.transform(
    scope = scope,
    state = this,
    initialValue = transform(value),
    transform = transform
)

/**
 * Combine two device states into one read-only [ValueState]. Only the latest value of each state is used.
 */
public fun <T1, T2, R> ValueState.Companion.combine(
    scope: CoroutineScope,
    state1: ValueState<T1>,
    state2: ValueState<T2>,
    mapper: (T1, T2) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2)

    override val value: R get() = mapper(state1.value, state2.value)

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            value = mapper(
                state1.valueWithTime.value,
                state2.valueWithTime.value
            ),
            time = maxOf(state1.valueWithTime.time, state2.valueWithTime.time)
        )

    val valueFlow: StateFlow<R> = combine(state1.subscribe(), state2.subscribe(), mapper)
        .stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): StateFlow<R> = valueFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = combine(
        flow = state1.subscribeWithTime(),
        flow2 = state2.subscribeWithTime()
    ) { v1, v2 ->
        ValueWithTime(
            value = mapper(v1.value, v2.value),
            time = maxOf(v1.time, v2.time)
        )
    }

    override fun toString(): String =
        "DeviceState.combine(state1=${state1.value}, state2=${state2.value}, value=$value)"
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
public fun <T1, T2, T3, R> ValueState.Companion.combine(
    scope: CoroutineScope,
    state1: ValueState<T1>,
    state2: ValueState<T2>,
    state3: ValueState<T3>,
    mapper: (T1, T2, T3) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2, state3)

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            value = mapper(
                state1.valueWithTime.value,
                state2.valueWithTime.value,
                state3.valueWithTime.value
            ),
            time = maxOf(state1.valueWithTime.time, state2.valueWithTime.time, state3.valueWithTime.time)
        )

    override val value: R get() = mapper(state1.value, state2.value, state3.value)

    val valueFlow: SharedFlow<R> = combine(state1.subscribe(), state2.subscribe(), state3.subscribe(), mapper)
        .stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): SharedFlow<R> = valueFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = combine(
        flow = state1.subscribeWithTime(),
        flow2 = state2.subscribeWithTime(),
        flow3 = state3.subscribeWithTime()
    ) { v1, v2, v3 ->
        ValueWithTime(
            value = mapper(v1.value, v2.value, v3.value),
            time = maxOf(v1.time, v2.time, v3.time)
        )
    }

    override fun toString(): String =
        "DeviceState.combine(state1=${state1.value}, state2=${state2.value}, state3=${state3.value}, value=$value)"
}

public fun <T1, T2, T3, T4, R> ValueState.Companion.combine(
    scope: CoroutineScope,
    state1: ValueState<T1>,
    state2: ValueState<T2>,
    state3: ValueState<T3>,
    state4: ValueState<T4>,
    mapper: (T1, T2, T3, T4) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2, state3, state4)

    override val value: R get() = mapper(state1.value, state2.value, state3.value, state4.value)

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            value = mapper(
                state1.valueWithTime.value,
                state2.valueWithTime.value,
                state3.valueWithTime.value,
                state4.valueWithTime.value
            ),
            time = maxOf(
                state1.valueWithTime.time,
                state2.valueWithTime.time,
                state3.valueWithTime.time,
                state4.valueWithTime.time
            )
        )

    val valueFlow: StateFlow<R> = combine(
        flow = state1.subscribe(),
        flow2 = state2.subscribe(),
        flow3 = state3.subscribe(),
        flow4 = state4.subscribe(),
        transform = mapper
    ).stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): StateFlow<R> = valueFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = combine(
        flow = state1.subscribeWithTime(),
        flow2 = state2.subscribeWithTime(),
        flow3 = state3.subscribeWithTime(),
        flow4 = state4.subscribeWithTime()
    ) { v1, v2, v3, v4 ->
        ValueWithTime(
            value = mapper(v1.value, v2.value, v3.value, v4.value),
            time = maxOf(v1.time, v2.time, v3.time, v4.time)
        )
    }

    override fun toString(): String =
        "DeviceState.combine(state1=${state1.value}, state2=${state2.value}, state3=${state3.value}, state4=${state4.value}, value=$value)"
}

/**
 * Combines multiple [ValueState] instances into a single [ValueStateWithDependencies].
 * The combined state value is derived by applying the provided [mapper] function to the collection of individual state values.
 *
 * @param T the type of the individual state values.
 * @param R the type of the combined state value.
 * @param states a collection of [ValueState] instances whose values are to be combined.
 * @param mapper a function that takes a collection of state values and maps it to a combined value.
 * @return a [ValueStateWithDependencies] representing the combined state, which has dependencies on the input [states].
 */
public fun <T, R> ValueState.Companion.combine(
    scope: CoroutineScope,
    states: Collection<ValueState<T>>,
    mapper: (List<T>) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = states

    override val value: R get() = mapper(states.map { it.value })

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            value = mapper(states.map { it.valueWithTime.value }),
            time = states.maxOf { it.valueWithTime.time }
        )

    @Suppress("UNCHECKED_CAST")
    val valueFlow: StateFlow<R> = combine(states.map { it.subscribe() }) { array: Array<Any?> ->
        mapper(array.asList() as List<T>)
    }.stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): StateFlow<R> = valueFlow

    @Suppress("UNCHECKED_CAST")
    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = combine(states.map { it.subscribeWithTime() }) { array: Array<Any?> ->
        mapper(array.asList() as List<T>)
    }.map { ValueWithTime(it, states.maxOf { it.valueWithTime.time }) }

    override fun toString(): String =
        "DeviceState.combine(states=${
            states.joinToString(
                prefix = "[",
                separator = ", ",
                postfix = "]"
            ) { "${it.value}" }
        }, value=$value)"
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
public fun <T, K, R> ValueState.Companion.combine(
    scope: CoroutineScope,
    states: Map<K, ValueState<T>>,
    mapper: (Map<K, T>) -> R,
): ValueStateWithDependencies<R> = object : ValueStateWithDependencies<R> {
    override val dependencies = states.values

    private val entries = states.entries.toList()

    override val value: R get() = mapper(states.mapValues { it.value.value })

    override val valueWithTime: ValueWithTime<R>
        get() = ValueWithTime(
            value = mapper(states.mapValues { it.value.valueWithTime.value }),
            time = states.maxOf { it.value.valueWithTime.time }
        )

    @Suppress("UNCHECKED_CAST")
    val valueFlow: StateFlow<R> = combine(entries.map { it.value.subscribe() }) { array: Array<Any?> ->
        // restore mapping
        mapper(entries.indices.associate { entries[it].key to (array[it] as T) })
    }.stateIn(scope, SharingStarted.WhileSubscribed(), value)

    override fun subscribe(): StateFlow<R> = valueFlow

    @Suppress("UNCHECKED_CAST")
    override fun subscribeWithTime(): Flow<ValueWithTime<R>> = combine(entries.map { it.value.subscribeWithTime() }) { array: Array<Any?> ->
        mapper(entries.indices.associate { entries[it].key to (array[it] as T) })
    }.map { ValueWithTime(it, states.maxOf { it.value.valueWithTime.time }) }

    override fun toString(): String =
        "DeviceState.associate(states=${
            states.entries.joinToString(
                prefix = "[",
                separator = ", ",
                postfix = "]"
            ) { "${it.key}=${it.value.value}" }
        }, value=$value)"
}

/**
 * Transforms a [ValueState] containing a [Amount] with specific [UnitsOfMeasurement]
 * into a [ValueState] containing a [Double] representing the underlying numerical value.
 *
 * @return A new [ValueState] object that provides the numerical value as a [Double].
 */
public fun ValueState<Amount<out UnitsOfMeasurement>>.values(): ValueState<Double> =
    ValueState.map(this) { it.value }
